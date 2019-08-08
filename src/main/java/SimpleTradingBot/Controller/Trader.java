package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Models.PositionState;
import SimpleTradingBot.Models.Position;
import SimpleTradingBot.Services.AccountManager;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.domain.*;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.binance.api.client.domain.account.NewOrder.marketBuy;
import static com.binance.api.client.domain.account.NewOrder.marketSell;


public class Trader {

    private Logger log;

    private TickerStatistics symbol;

    private Position buyOrder;

    private Position sellOrder;

    private TrailingStop trailingStop;

    private BinanceApiAsyncRestClient client;

    private PositionState state;

    FilterConstraints constraints;

    /* For market orders */
    private BigDecimal buyPrice;

    /* For market orders */
    private BigDecimal sellPrice;

    private int nErr;

    /* Constructor */
    Trader(  Controller controller ) {
        this.symbol = controller.getSummary();
        String loggerName = this.getClass().getSimpleName();
        log = Logger.getLogger("root." + symbol.getSymbol() + "." + loggerName);
        this.trailingStop = new TrailingStop( symbol );
        this.client = Static.getFactory().newAsyncRestClient();
        this.state = new PositionState();
        this.constraints = Static.constraints.get( symbol.getSymbol() );
        this.nErr = 0;
    }

    /* Getters */

    public Position getBuyOrder() {
        return buyOrder;
    }

    public TrailingStop getTrailingStop() {
        return trailingStop;
    }

    public Position getSellOrder() {
        return sellOrder;
    }

    public PositionState getState() {
        return state;
    }

    public BigDecimal getBuyPrice() {
        return buyPrice;
    }

    public BigDecimal getSellPrice() {
        return sellPrice;
    }
    /* Methods */

    boolean shouldOpen( ) {
        log.entering(this.getClass().getSimpleName(),"shouldOpen");
        PositionState.Type stateType = this.state.getType();
        boolean updated = this.state.isUpdated() || this.state.isStable();
        boolean clear = (stateType == PositionState.Type.CLEAR ) || ( stateType == PositionState.Type.SELL );
        this.log.info( String.format("State type: %s (%s), updated: %s", stateType, clear, updated));
        log.exiting(this.getClass().getSimpleName(),"shouldOpen");
        return clear && updated;
    }

    boolean shouldClose( Bar bar ) {
        log.entering(this.getClass().getSimpleName(), "shouldClose");
        Num lastPrice = bar.getClosePrice();
        BigDecimal stopLoss = this.trailingStop.getStopLoss();
        PrecisionNum precisionStopLoss = PrecisionNum.valueOf( stopLoss );
        boolean breached = lastPrice.isLessThanOrEqual( precisionStopLoss );
        log.info("Last price: " + lastPrice + ". Stop loss: "  + stopLoss + ". Breached: " + breached );
        log.exiting(this.getClass().getSimpleName(), "shouldClose");
        if ( Config.FORCE_CLOSE )
            return true;
        else
            return breached;
    }

    boolean open( BigDecimal close ) throws InterruptedException, STBException {
        log.entering(this.getClass().getSimpleName(), "open");
        BigDecimal baseQty = AccountManager.getNextQty();
        BigDecimal proposedQty = baseQty.divide( close, RoundingMode.HALF_DOWN);
        BigDecimal adjustedQty = this.constraints.adjustQty( proposedQty );
        this.log.info( "Quantity : " + proposedQty + " -> " + adjustedQty);
        int precision = this.constraints.getBASE_PRECISION() - 2;
        NewOrder newOrder = marketBuy(this.symbol.getSymbol(), Static.safeDecimal(adjustedQty,  precision ));
        NewOrderResponse response = submit( newOrder, close );
        if ( response == null) {
            this.log.warning( "Order suspended");
            return false;
        }
        BigDecimal initialStop = close.multiply(Config.STOP_LOSS_PERCENT);
        this.trailingStop.setStopLoss(initialStop);
        this.log.info(newOrder.toString());
        this.log.info(response.toString());
        this.buyOrder = new Position(newOrder, response);
        this.buyPrice = close;
        log.exiting(this.getClass().getSimpleName(), "open");
        return true;
    }

    boolean close( Bar lastBar ) throws InterruptedException, STBException {
        log.entering(this.getClass().getSimpleName(), "close");

        Num closePrice = lastBar.getClosePrice();
        BigDecimal close = new BigDecimal( closePrice.toString() );

        long currTimeMillis = System.currentTimeMillis();
        String dateTime = Static.toReadableDate( currTimeMillis );
        /* Get executed qty */
        String symbol = this.symbol.getSymbol();
        String qty = getSellQty();
        NewOrder sellOrder = marketSell( symbol, qty );
        NewOrderResponse sellOrderResponse = submit( sellOrder, close );
        if ( sellOrderResponse == null ) {
            this.log.warning( "Postponed submission");
            return false;
        }
        this.sellPrice = close;
        /* Or just use all our free balance
        Account myAccount = client.getAccount();
        AssetBalance balance = myAccount.getAssetBalance( assetSymbol );
        String free = balance.getFree(); */

        this.log.info( sellOrder.toString() );
        this.log.info( sellOrderResponse.toString() );
        this.log.info("Closed at: " + closePrice + ", " + dateTime );
        this.sellOrder = new Position( sellOrder, sellOrderResponse );
        this.trailingStop.reset( );
        log.exiting(this.getClass().getSimpleName(), "close");
        return true;
    }

    private String getSellQty( ) {
        int nUpdates = buyOrder.getnUpdate();
        switch ( Config.TEST_LEVEL ) {
            case REAL:
                Order buyOrder = this.buyOrder.getUpdatedOrder( nUpdates );
                return buyOrder.getExecutedQty();
            case FAKEORDER:
                default:
                NewOrder order = this.buyOrder.getOriginalOrder();
                return order.getQuantity();
        }
    }

    void update( Bar bar ) throws InterruptedException, STBException {
        log.entering(this.getClass().getSimpleName(), "update" );
        BigDecimal close = (BigDecimal) bar.getClosePrice().getDelegate();

        if ( Config.TRAILING_STOP && this.state.isBuyOrHold() )
            this.trailingStop.update( bar );

        if ( this.state.isUpdated() ) {
            PositionState.Type type = this.state.getType();
            log.info( "Updating position: " + type );

            switch ( type ) {
                case HOLD:
                case BUY:
                    update( OrderSide.BUY, close );
                    break;
                case CLEAR:
                case SELL:
                    update( OrderSide.SELL, close );
                    break;
                default:
                    log.severe( "Skipping update of open position: " + type);
            }
        }
        else
            log.info("Position not maintained, skipping update.");

        log.exiting(this.getClass().getSimpleName(), "update");
    }

    private void update( OrderSide side, BigDecimal close ) throws InterruptedException, STBException {

        log.entering(this.getClass().getSimpleName(), "update: " + side);
        log.info("Updating side: " + side);

        Position position = ( side == OrderSide.SELL ) ? this.sellOrder : this.buyOrder;
        NewOrderResponse originalResponse = position.getOriginalOrderResponse();
        OrderStatus oldStatus = originalResponse.getStatus();
        long orderId = originalResponse.getOrderId();
        long timestamp = originalResponse.getTransactTime();
        String time = Static.toReadableDate(timestamp);
        PositionState.Flags flags = this.state.getFlags();
        int nUpdates = position.getnUpdate();

        if ( nUpdates > 0 ) {
            Order recentUpdate = position.getUpdatedOrder(nUpdates);

            long updateTime = recentUpdate.getTime();
            String updatedTime = Static.toReadableDate( updateTime );
            OrderStatus updatedStatus = recentUpdate.getStatus();

            log.info("Updating position: " + orderId + "," +
                    " side: " + side + ", placed at: " + time + "," +
                    " update at " + updatedTime + ", original status: " + oldStatus + ", " +
                    " updated status: " + updatedStatus + ", flags: " + flags);
        }

        else {

            log.info("Updating position: " + orderId + "," +
                    "nUpdates: " + nUpdates + "," +
                    " side: " + side + ", placed at: " + time + "," +
                    " original status: " + oldStatus + ", "
                    + " flags: " + flags);
        }


        switch ( flags ) {
            case RESTART:
            case REVERT:
                if (restart( side, close ))
                    this.state.setAsOutdated();
                break;
            case CANCEL:
                cancel( orderId );
                this.state.setAsOutdated();
            case UPDATE:
                update_internal( orderId );
                this.state.setAsOutdated();
                break;
            case NONE:
                break;
                default:
                    this.log.severe("Unknown flags: " + flags);

        }
        log.exiting( this.getClass().getSimpleName(), "update: " + side );
    }

    private void update_internal( long orderId ) {
        log.entering(this.getClass().getSimpleName(), "update_internal");
        OrderStatusRequest statusRequest = new OrderStatusRequest( this.symbol.getSymbol(), orderId );
        log.info( "Getting update: " + statusRequest) ;
        client.getOrderStatus( statusRequest, this::onOrderUpdate );
        log.exiting(this.getClass().getSimpleName(), "update_internal");
    }

    private void onOrderUpdate( Order order ) throws STBException {
        this.log.entering( this.getClass().getSimpleName(), "onOrderUpdate" );
        OrderSide side = order.getSide();
        log.info( "Got update: " + order );
        Position position = ( side == OrderSide.BUY ) ? this.buyOrder : this.sellOrder;
        position.setUpdatedOrder( order );
        this.log.exiting( this.getClass().getSimpleName(), "onOrderUpdate" );
    }

    private boolean restart( OrderSide side, BigDecimal close )
        throws InterruptedException, STBException{
        log.entering(this.getClass().getSimpleName(), "restart" );
        Position position = ( side == OrderSide.BUY ) ? this.buyOrder : this.sellOrder;
        NewOrder newOrder = position.getOriginalOrder();
        log.info("Restarting order: " + newOrder);
        NewOrderResponse response = submit( newOrder, close  );

        if ( response == null ) {
            this.log.info( "Postponing restart");
            return false;
        }

        this.log.info( "Restarted order: " + response );
        position.setOriginalOrderResponse( response );
        log.exiting(this.getClass().getSimpleName(), "restart" );
        return true;
    }


    void cancel( OrderSide side ) {
        NewOrderResponse buyResponse = this.buyOrder.getOriginalOrderResponse();
        NewOrderResponse sellResponse = this.sellOrder.getOriginalOrderResponse();
        long orderId = ( side == OrderSide.BUY ) ? buyResponse.getOrderId() : sellResponse.getOrderId();
        cancel( orderId );
    }

    private void cancel( long orderId ) {
        log.entering(this.getClass().getSimpleName(), "cancel");
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest( this.symbol.getSymbol(), orderId );
        log.info( "Cancelling order: " + cancelOrderRequest );
        client.cancelOrder( cancelOrderRequest,  response -> this.log.info("Cancel order response: " + response ) );
        log.exiting(this.getClass().getSimpleName(), "cancel");
    }

    private synchronized NewOrderResponse submit( NewOrder order, BigDecimal close )
            throws InterruptedException, STBException {
        log.entering(this.getClass().getSimpleName(), "submit");
        final TransferQueue<NewOrderResponse> queue = new LinkedTransferQueue<>();
        try {
            switch ( Config.TEST_LEVEL ) {
                case REAL:
                    client.newOrder(order, r -> {
                        try {
                            queue.transfer( r );
                        }
                        catch ( InterruptedException e ) {
                            boolean success = queue.tryTransfer( r );

                            if (!success ) {
                                this.log.log(Level.SEVERE, "Submission failed", e);
                            }

                        }
                    } );
                    break;
                case FAKEORDER:
                default:
                    client.newOrderTest( order, r  ->  queue.offer( fakeResponse( order, close.toString() )) );
                    break;
            }
            NewOrderResponse response = queue.poll( 40, TimeUnit.SECONDS );
            this.nErr = 0;
            return response;
        }

        catch ( BinanceApiException e) {

            log.log(Level.WARNING, "Failed submission, attempt: " + this.nErr, e);

            if (++this.nErr >= Config.MAX_ORDER_RETRY)
                throw new STBException(70);
            else
                return null;

        }

        finally {
            log.exiting(this.getClass().getSimpleName(), "submit");
        }
    }

    public void updateState( PositionState.Type state, PositionState.Flags flags ) {
        this.log.entering( this.getClass().getSimpleName(), "updateState");
        PositionState currentState = getState();
        PositionState.Type type = currentState.getType();
        PositionState.Flags currentFlags = currentState.getFlags();
        this.log.info( "Updating state to: " + state + ", " + flags + ", from current state: " + type + ", " + currentFlags);
        this.state.maintain( state, flags );
        this.log.exiting( this.getClass().getSimpleName(), "updateState");
    }

    private NewOrderResponse fakeResponse( NewOrder newOrder, String price ) {
        NewOrderResponse response = new NewOrderResponse();
        String qty = newOrder.getQuantity();
        long now = System.currentTimeMillis();
        response.setExecutedQty( qty );
        response.setPrice( newOrder.getPrice() );
        response.setTransactTime( now );
        response.setPrice( price );
        response.setOrigQty( qty );
        response.setOrderId( 12345L );
        response.setSide( newOrder.getSide() );
        response.setStatus( OrderStatus.FILLED );
        response.setTimeInForce( newOrder.getTimeInForce() );
        return response;
    }

    private void on(Order order) {
        try {
            onOrderUpdate(order);
        } catch (STBException e) {
            throw e;
        }
    }
}
