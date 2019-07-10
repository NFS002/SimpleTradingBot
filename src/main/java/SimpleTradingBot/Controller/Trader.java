package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Models.PositionState;
import SimpleTradingBot.Models.Position;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.*;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
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

    private BinanceApiRestClient client;

    private PositionState state;

    private final Controller controller;

    FilterConstraints constraints;

    /* For market orders */
    private BigDecimal buyPrice;

    /* For market orders */
    private BigDecimal sellPrice;

    /* Constructor */
    Trader(  Controller controller ) {
        this.controller = controller;
        this.symbol = controller.getSummary();
        String loggerName = this.getClass().getSimpleName();
        log = Logger.getLogger("root." + symbol.getSymbol() + "." + loggerName);
        this.trailingStop = new TrailingStop( symbol );
        this.client = Static.getFactory().newRestClient();
        this.state = new PositionState();
        this.constraints = Static.constraints.get( symbol.getSymbol() );
    }

    /* Getters */

    public Position getBuyOrder() {
        return buyOrder;
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
        boolean updated = this.state.isUpdated();
        boolean clear = (stateType == PositionState.Type.CLEAR ) || ( stateType == PositionState.Type.SELL );
        log.info("State: " + stateType + ". Updated: " + updated);
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

    boolean open( BigDecimal close ) throws STBException {
        log.entering(this.getClass().getSimpleName(), "open");
        BigDecimal baseQty = this.constraints.getNext_qty();
        BigDecimal qty = baseQty.divide( close, RoundingMode.HALF_UP );
        qty = this.constraints.adjustQty( qty );
        if ( qty == null ) {
            this.log.info( "Adjusted quantity is below minimum, shutting down" );
            this.controller.exit();
        }
        else {
            NewOrder newOrder = marketBuy(this.symbol.getSymbol(), Static.safeDecimal(qty, 20));
            NewOrderResponse response = submit(newOrder, close);
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
        }

        log.exiting(this.getClass().getSimpleName(), "open");
        return true;
    }

    boolean close( Bar lastBar ) throws STBException {
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
            case NOORDER:
                default:
                NewOrder order = this.buyOrder.getOriginalOrder();
                return order.getQuantity();
        }
    }

    void update( Bar bar ) throws STBException {
        log.entering(this.getClass().getSimpleName(), "update" );
        BigDecimal close = new BigDecimal( bar.getClosePrice().doubleValue() );

        if ( Config.TRAILING_STOP )
            this.trailingStop.update( bar );

        if ( this.state.getMaintained() ) {
            PositionState.Type type = this.state.getType();
            log.info( "Updating position " + type );

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

    private void update( OrderSide side, BigDecimal close ) throws STBException {

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
        log.exiting(this.getClass().getSimpleName(), "update: " + side);
    }

    private void update_internal( long orderId )
        throws STBException {
        log.entering(this.getClass().getSimpleName(), "update_internal");
        OrderStatusRequest statusRequest = new OrderStatusRequest( this.symbol.getSymbol(), orderId );
        log.info( "Getting update: " + statusRequest) ;
        Order order = client.getOrderStatus( statusRequest );
        OrderSide side = order.getSide();
        log.info( "Got update: " + order );
        Position position = ( side == OrderSide.BUY ) ? this.buyOrder : this.sellOrder;
        position.setUpdatedOrder( order );
        log.exiting(this.getClass().getSimpleName(), "update_internal");
    }

    private boolean restart( OrderSide side, BigDecimal close )
        throws STBException{
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
        CancelOrderResponse cancelOrderResponse = client.cancelOrder( cancelOrderRequest );
        log.info("Cancel order response: " + cancelOrderResponse );
        log.exiting(this.getClass().getSimpleName(), "cancel");
    }

    private NewOrderResponse submit( NewOrder order, BigDecimal close ) throws STBException {
        log.entering(this.getClass().getSimpleName(), "submit");
        NewOrderResponse response = null;
        for (int i = 0; i < Config.MAX_ORDER_RETRY; i++) {
            try {
                switch ( Config.TEST_LEVEL ) {
                    case REAL:
                        response = client.newOrder(order);
                        break;
                    case FAKEORDER:
                        client.newOrderTest( order );
                    case NOORDER:
                        response = fakeResponse( order, close.toPlainString() );
                }
                log.exiting(this.getClass().getSimpleName(), "submit");
                break;
            }
            catch (BinanceApiException e) {
                log.log(Level.WARNING, "Failed submission. Attempt: " + i, e);
                String message = e.getMessage();
                if ( message.contains("LOT_SIZE"))
                    return null;
            }
        }
        if ( response == null ) {
            throw new STBException( 70 );
        }
        else
            return response;
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
}
