package SimpleTradingBot.Test.Backtest;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.TrailingStop;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.Cycle;
import SimpleTradingBot.Models.PositionState;
import SimpleTradingBot.Models.Position;
import SimpleTradingBot.Services.AccountManager;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.*;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.Bar;
import org.ta4j.core.indicators.ROCIndicator;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.STOP_LOSS_PERCENT;
import static com.binance.api.client.domain.account.NewOrder.marketBuy;
import static com.binance.api.client.domain.account.NewOrder.marketSell;
import static SimpleTradingBot.Models.PositionState.Phase.*;
import static SimpleTradingBot.Models.PositionState.Flags.*;
import static SimpleTradingBot.Test.FakeOrderResponses.*;
import static SimpleTradingBot.Config.Config.MAX_ORDER_UPDATES;

public class TestTrader {

    private final Logger log;

    private final String symbol;

    private final ArrayList<Cycle> cycles;

    private final TrailingStop trailingStop;

    private final PositionState state;

    private PrintWriter rtWriter;

    /* Constructor */
    TestTrader( String symbol, File baseDir ) {
        String loggerName = this.getClass().getSimpleName();
        this.symbol = symbol;
        this.cycles = new ArrayList<>();
        this.log = Logger.getLogger("root." + symbol + "." + loggerName);
        this.trailingStop = new TrailingStop( symbol );
        this.state = new PositionState();
        initRtWriter( baseDir );
    }

    /* Getters */

    public TrailingStop getTrailingStop() {
        return trailingStop;
    }

    public PositionState getState() {
        return state;
    }

    /* Methods */
    boolean shouldOpen( ) {
        this.log.entering(this.getClass().getSimpleName(),"shouldOpen");
        boolean clear = this.state.getPhase() == PositionState.Phase.CLEAR;
        this.log.info( "State: " + this.state );
        this.log.exiting(this.getClass().getSimpleName(),"shouldOpen");
        return clear;
    }

    boolean shouldClose( Bar bar ) {
        log.entering(this.getClass().getSimpleName(), "shouldClose");
        BigDecimal lastPrice = (BigDecimal) bar.getClosePrice().getDelegate();
        BigDecimal stopLoss = this.trailingStop.getStopLoss();
        boolean breached = lastPrice.compareTo( stopLoss ) <= 0;
        this.log.info("Last price: " + lastPrice + ". Stop loss: "  + stopLoss + ". Breached: " + breached );
        this.log.exiting(this.getClass().getSimpleName(), "shouldClose");
        if ( Config.FORCE_CLOSE )
            return true;
        else
            return breached;
    }

    boolean open( BigDecimal close ) throws STBException {
        this.log.entering(this.getClass().getSimpleName(), "open");
        BigDecimal adjustedQty = BigDecimal.TEN; /* Arbitrary Quantity */
        this.log.info( "Quantity : " + adjustedQty);
        NewOrder newOrder = marketBuy( this.symbol, Static.safeDecimal( adjustedQty ) );
        NewOrderResponse response = trySubmit( newOrder, close );
        if ( response == null) {
            this.log.warning( "Order failed");
            return false;
        }
        else {
            BigDecimal initialStop = close.multiply(Config.STOP_LOSS_PERCENT);
            this.trailingStop.setStopLoss(initialStop);
            Position position = new Position(newOrder, response);
            Cycle newCycle = new Cycle( position );
            this.cycles.add( newCycle );
        }
        this.log.info(newOrder.toString());
        this.log.info(response.toString());
        this.log.exiting(this.getClass().getSimpleName(), "open");
        return true;
    }

    void close( Bar lastBar ) {
        this.log.entering(this.getClass().getSimpleName(), "close");
        BigDecimal close = (BigDecimal) lastBar.getClosePrice().getDelegate();
        long currTimeMillis = System.currentTimeMillis();
        String dateTime = Static.toReadableTime( currTimeMillis );
        /* Get executed qty, or just use all our free balance */
        String symbol = this.symbol;
        String qty = this.getSellQty();
        NewOrder sellOrder = marketSell( symbol, qty );
        NewOrderResponse sellOrderResponse = trySubmit( sellOrder, close );
        if ( sellOrderResponse == null ) {
            this.log.warning( "Postponed submission");
        }
        else {
            this.log.info("Closed at: " + close + ", " + dateTime);
            this.trailingStop.reset( );
            Cycle lastCycle = this.cycles.get( this.cycles.size() - 1 );
            Position sellPosition = new Position( sellOrder, sellOrderResponse );
            lastCycle.setSellPosition( sellPosition );
        }
        this.log.info( String.format( "%s", sellOrder ) );
        this.log.info( String.format( "%s", sellOrderResponse ));
        log.exiting(this.getClass().getSimpleName(), "close");
    }

    private String getSellQty( ) {
        Cycle currentCycle = this.cycles.get( this.cycles.size() - 1 );
        Position currentBuyPosition = currentCycle.getBuyPosition();
        Order order = currentBuyPosition.getLastUpdate();
        return order.getExecutedQty();
    }

    void findAndSetState( ){
        this.log.entering( this.getClass().getSimpleName(), "findAndSetState");
        int nCycles = this.cycles.size();
        if ( nCycles > 0) {
            this.log.info(String.format("Finding state, nCycles: %d", nCycles));
            Cycle lastCycle = this.cycles.get(nCycles - 1);
            Position lastBuy = lastCycle.getBuyPosition();
            Position lastSell = lastCycle.getSellPosition();
            PositionState.Phase phase;
            if (lastCycle.getSellPosition() == null) {
                this.findAndSetFlags(lastBuy);
                if (this.state.getFlags() == NONE) {
                    /* Check we are actually holding something, or else move straight to clear */
                    BigDecimal exQty = new BigDecimal(lastBuy.getLastUpdate().getExecutedQty());
                    this.log.info("Executed qty in buy position: " + exQty);
                    if (exQty.compareTo(BigDecimal.ZERO) > 0)
                        phase = HOLD;
                    else {
                        if (!lastCycle.isFinalised()) {
                            lastCycle.finalise();
                            Static.logRt( lastCycle );
                            this.logRt( lastCycle );
                        }
                        phase = CLEAR;
                    }
                }
                else
                    phase = BUY;
            }
            else {
                this.findAndSetFlags(lastSell);
                if (this.state.getFlags() == NONE) {
                    if (!lastCycle.isFinalised()) {
                        lastCycle.finalise();
                        Static.logRt(lastCycle);
                        this.logRt( lastCycle );
                    }
                    phase = CLEAR;
                }
                else
                    phase = SELL;
            }
            this.log.info("Determined phase: " + phase);
            this.state.setPhase(phase);
        }
        else
            this.log.info(String.format(
                    "Cycles are empty (%d), no update or change in state required", nCycles));
        this.log.exiting( this.getClass().getSimpleName(), "findAndSetState");
    }

    private void findAndSetFlags( Position position ) {
        this.log.entering( this.getClass().getSimpleName(), "findAndSetFlags");
        int nUpdates = position.getnUpdate();
        this.log.info( String.format("Finding phase and flags, nUpdates: %d", nUpdates ) );
        PositionState.Flags flags;
        if ( nUpdates == 0 )
            flags = UPDATE;
        else if ( nUpdates == MAX_ORDER_UPDATES - 1 )
            flags = NONE;
        else if ( nUpdates == MAX_ORDER_UPDATES - 2 )
            flags = CANCEL;
        else {
            Order lastOrder = position.getLastUpdate();
            OrderStatus lastStatus = lastOrder.getStatus();
            this.log.info( String.format("Last update status: %s", lastStatus));
            switch ( lastStatus ) {
                case NEW:
                case PARTIALLY_FILLED:
                case PENDING_CANCEL:
                    flags = UPDATE;
                    break;
                case CANCELED:
                case FILLED:
                    flags = NONE;
                    break;
                default:
                    flags = CANCEL;
            }
        }
        this.log.info( "Found and set flags: " + flags );
        this.state.setFlags( flags );
        this.log.exiting( this.getClass().getSimpleName(), "findAndSetFlags");
    }

    void update( Bar bar )  {
        log.entering(this.getClass().getSimpleName(), "update" );
        int nCycles = this.cycles.size();
        if ( nCycles > 0 ) {
            BigDecimal close = (BigDecimal) bar.getClosePrice().getDelegate();
            this.updateStopLoss(close);
            this.incTicks( nCycles );
            if (this.state.getFlags() != NONE) {
                PositionState.Phase phase = this.state.getPhase();
                switch (phase) {
                    case HOLD:
                    case BUY:
                        update( OrderSide.BUY, close);
                        break;
                    case CLEAR:
                    case SELL:
                        update( OrderSide.SELL, close );
                }
            }
            else
                this.log.info("No further update required");
        }
        else
            this.log.info(String.format(
                    "Cycles are empty (%d), no update or change in state required", nCycles));
        log.exiting(this.getClass().getSimpleName(), "update");
    }

    private void incTicks(int nCycles) {
        Cycle lastCycle = this.cycles.get( nCycles - 1 );
        lastCycle.incTicks();
    }

    private void updateStopLoss( BigDecimal close ) {
        this.log.entering(this.getClass().getSimpleName(), "updateStopLoss");
        this.log.info("Updating stop loss");
        if ( Config.TRAILING_STOP && this.state.isBuyOrHold() )
            this.trailingStop.update( close );
        this.log.exiting(this.getClass().getSimpleName(), "updateStopLoss");
    }

    private void update( OrderSide side, BigDecimal close ) throws STBException {
        this.log.entering(this.getClass().getSimpleName(), "update",side);
        int nCycles = this.cycles.size();
        Cycle lastCycle = this.cycles.get( nCycles - 1 );
        Position position = ( side == OrderSide.BUY ) ? lastCycle.getBuyPosition() : lastCycle.getSellPosition();
        NewOrderResponse originalResponse = position.getOriginalOrderResponse();
        PositionState.Flags flags = this.state.getFlags();
        long orderId = originalResponse.getOrderId();
        this.logPreUpdate( side, position, orderId, originalResponse );
        switch ( flags ) {
            case REVERT:
                this.revertCycle( nCycles - 1 );
                break;
            case CANCEL:
                cancel( position );
                break;
            case RESTART:
                restart( position, close );
                break;
            case UPDATE:
                _update( position, originalResponse, close );
                break;
        }
        this.log.exiting( this.getClass().getSimpleName(), "update", side );
    }

    private void revertCycle( int i ) {
        this.log.entering( this.getClass().getSimpleName(), "revertCycle");
        this.log.info("Reverting cycle: " + i);
        this.cycles.set( i, null);
        this.state.setFlags( NONE );
        this.state.setPhase( CLEAR );
        this.log.entering( this.getClass().getSimpleName(), "revertCycle");

    }

    private void logPreUpdate( OrderSide side, Position position, long orderId, NewOrderResponse originalResponse ) {
        this.log.entering( this.getClass().getSimpleName(), "logPreUpdate");
        OrderStatus oldStatus = originalResponse.getStatus();
        long timestamp = originalResponse.getTransactTime();
        String time = Static.toReadableTime(timestamp);
        PositionState.Flags flags = this.state.getFlags();
        int nUpdates = position.getnUpdate();
        this.log.info("Updating position: " + orderId
                + ", nUpdates: " + nUpdates
                + ", side: " + side
                + ", placed at: " + time
                + ", original status: " + oldStatus
                + ", flags: " + flags);

        if ( nUpdates > 0 ) {

            Order recentUpdate = position.getLastUpdate();
            long updateTime = recentUpdate.getTime();
            String updatedTime = Static.toReadableTime( updateTime );
            OrderStatus updatedStatus = recentUpdate.getStatus();

            this.log.info("Last update at: " + updatedTime
                    + ", status: " + updatedStatus);
        }
        this.log.exiting( this.getClass().getSimpleName(), "logPreUpdate");
    }

    private void _update(Position position, NewOrderResponse response, BigDecimal close ) {
        this.log.entering(this.getClass().getSimpleName(), "_update");
        long orderId = position.getOriginalOrderResponse().getOrderId();
        OrderStatusRequest statusRequest = new OrderStatusRequest( response.getSymbol(), orderId );
        this.log.info( "Getting update: " + statusRequest );
        Order order = getNextUpdate( response, close );
        this.log.info( "Got update: " + order );
        position.setUpdatedOrder( order );
        log.exiting(this.getClass().getSimpleName(), "_update");
    }

    private void restart( Position position, BigDecimal close ) {
        this.log.entering(this.getClass().getSimpleName(), "restart" );
        NewOrder newOrder = position.getOriginalOrder();
        this.log.info("Restarting order: " + newOrder);
        NewOrderResponse response = trySubmit( newOrder, close  );
        this.log.info( "Got response: " + response );
        position.setOriginalOrderResponse( response );
        position.restartUpdates();
        this.log.exiting(this.getClass().getSimpleName(), "restart" );
    }

    private void cancel( Position position ) {
        this.log.entering(this.getClass().getSimpleName(), "cancel");
        long orderId = position.getOriginalOrderResponse().getOrderId();
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest( this.symbol, orderId );
        this.log.info( "Cancelling order: " + cancelOrderRequest );
        CancelOrderResponse response = fakeCancelledResponse(cancelOrderRequest);
        position.cancelFakeOrder( response );
        this.log.info("Cancel order response: " + response );
        log.exiting(this.getClass().getSimpleName(), "cancel");
    }

    private synchronized NewOrderResponse trySubmit(NewOrder order, BigDecimal close )  {
        log.entering(this.getClass().getSimpleName(), "submit");
        return fakeNewResponse( order, close.toPlainString() );
    }

    private void initRtWriter( File baseDir ) {
        ROCIndicator indicator;
        try {
            rtWriter = new PrintWriter( baseDir + "/rt.csv");
            rtWriter.append( Cycle.CSV_HEADER ).flush();
        }
        catch ( IOException e ) {
            log.warning( "Cant create necessary rt files. Skipping rt logging" );
        }
    }

    public void logIfSlippage( BigDecimal fChange ) {
        if ( fChange.compareTo( STOP_LOSS_PERCENT ) < 0 ) {
            int nCycles = this.cycles.size();
            if ( nCycles > 0 && this.state.isBuyOrHold() ) {
                Cycle lastCycle = this.cycles.get( nCycles - 1 );
                lastCycle.setSlippage();
            }
        }
    }


    private void logRt( Cycle cycle ) {
        if (this.rtWriter != null) {
            this.log.info("Logging rt for symbol: " + cycle.getSymbol());
            this.rtWriter.append(cycle.toCsv()).flush();
        }
    }
}
