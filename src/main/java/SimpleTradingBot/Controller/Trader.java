package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Models.Position;
import SimpleTradingBot.Models.RoundTrip;
import SimpleTradingBot.Util.OrderRequest;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.*;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.exception.BinanceApiException;
import org.ta4j.core.Bar;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.binance.api.client.domain.account.NewOrder.marketBuy;
import static com.binance.api.client.domain.account.NewOrder.marketSell;


public class Trader {

    private final OrderRequest orderRequest;

    private int nOrderErr;

    private Logger log;

    private TrailingStop trailingStop;

    private BinanceApiRestClient client;

    FilterConstraints constraints;


    /* Constructor */
    Trader( OrderRequest orderRequest ) {
        this.orderRequest = orderRequest;
        String symbol = orderRequest.getSymbol();
        String loggerName = "root." + symbol + "." + this.getClass().getSimpleName();
        log = Logger.getLogger( loggerName );
        this.trailingStop = new TrailingStop( symbol );
        this.client = Static.getFactory().newRestClient();
        this.constraints = Static.constraints.get( symbol );
        this.nOrderErr = 0;
    }

    /* Methods */

    boolean shouldClose( Bar bar ) {
        log.entering(this.getClass().getSimpleName(), "shouldClose");
        BigDecimal lastPrice = (BigDecimal) bar.getClosePrice().getDelegate();
        BigDecimal stopLoss = this.trailingStop.getStopLoss();
        boolean breached = lastPrice.compareTo( stopLoss ) <= 0;
        String lastPriceStr = Static.safeDecimal( lastPrice, 5 );
        String stopLossStr = Static.safeDecimal( stopLoss, 5 );
        log.info("Last price: " + lastPriceStr + ", stop loss: "  + stopLossStr + ". Breached: " + breached );
        log.exiting(this.getClass().getSimpleName(), "shouldClose");

        if ( Config.FORCE_CLOSE )
            return true;
        else
            return breached;
    }

    RoundTrip open( ) throws STBException {
        log.entering(this.getClass().getSimpleName(), "open");
        BigDecimal openPrice = this.orderRequest.getOpenPrice();
        int orderWeight = this.orderRequest.getWeight() / Config.MAX_WEIGHT;

        BigDecimal maxQty = FilterConstraints.getRemainingQty();
        BigDecimal weightedQty = maxQty.multiply(  BigDecimal.valueOf( orderWeight ) );
        BigDecimal qty = weightedQty.multiply( openPrice, MathContext.DECIMAL64 );
        BigDecimal adjustedQty = this.constraints.adjustQty( qty );
        String symbol = this.orderRequest.getSymbol();
        RoundTrip roundTrip;

        if ( adjustedQty == null )
            throw new STBException( 180 );

        else {
            NewOrder newOrder = marketBuy( symbol, Static.safeDecimal( adjustedQty, 20));
            this.log.info( newOrder.toString() );
            NewOrderResponse response = submit( newOrder );

            if ( response != null ) {

                this.log.info( response.toString() );
                BigDecimal initialStop = openPrice.multiply( Config.STOP_LOSS_PERCENT );
                this.trailingStop.setStopLoss( initialStop );
                Position position = new Position( newOrder, response, openPrice );
                log.exiting(this.getClass().getSimpleName(), "open");
                return new RoundTrip( position );

            }

            else
                throw new STBException( 190 );
        }

    }

     Position close( String symbol, RoundTrip roundTrip, Bar lastBar ) throws STBException {
        log.entering(this.getClass().getSimpleName(), "close");

        BigDecimal closePrice = (BigDecimal) lastBar.getClosePrice().getDelegate();
        String closePriceStr = Static.safeDecimal( closePrice, 5 );

        long currTimeMillis = System.currentTimeMillis();
        String dateTime = Static.toReadableDate( currTimeMillis );

        this.log.info( String.format( "Closing %s at %s, %s.", symbol, closePriceStr, dateTime ));

        /* Get executed qty */
        String qty = this.getSellQty( roundTrip );
        NewOrder sellOrder = marketSell( symbol, qty );
        NewOrderResponse sellOrderResponse = submit( sellOrder );

        /* Or just use all our free balance */

        this.log.info( String.format("Sell order: %s", sellOrder.toString() ) );
        this.log.info( String.format("Sell order response: %s", sellOrderResponse.toString() ) );

        Position sellPosition = new Position( sellOrder, sellOrderResponse, closePrice );
        this.trailingStop.reset();
        this.log.exiting(this.getClass().getSimpleName(), "close");
        return sellPosition;
    }

    private String getSellQty( RoundTrip roundTrip ) {
        Position buyPosition = roundTrip.getBuyPosition();
        switch ( Config.TEST_LEVEL ) {

            case REAL:
                Order lastBuyUpdate = buyPosition.getLastUpdate();
                return lastBuyUpdate.getExecutedQty();

            case FAKEORDER:
            case NOORDER:
            default:
                NewOrder order = buyPosition.getOriginalOrder();
                return order.getQuantity();
        }
    }

    void updateStopLoss( Bar bar )  {
        if ( Config.TRAILING_STOP ) {
            this.trailingStop.update( bar );
        }
    }

    void cancel( long orderId ) {
        log.entering(this.getClass().getSimpleName(), "cancel");
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest( this.orderRequest.getSymbol(), orderId );
        log.info( "Cancelling order: " + cancelOrderRequest );
        CancelOrderResponse cancelOrderResponse = client.cancelOrder( cancelOrderRequest );
        log.info("Cancel order response: " + cancelOrderResponse );
        log.exiting(this.getClass().getSimpleName(), "cancel");
    }

    private NewOrderResponse submit( NewOrder order ) throws STBException {
        log.entering(this.getClass().getSimpleName(), "submit");
        NewOrderResponse response = null;
        try {
            switch ( Config.TEST_LEVEL ) {
                case REAL:
                    response = client.newOrder(order);
                    break;
                case FAKEORDER:
                    client.newOrderTest( order );
                case NOORDER:
                    response = fakeResponse( order );
            }
            this.nOrderErr = 0;

        }
        catch (BinanceApiException e) {
            log.log(Level.WARNING, "Failed submission. (" + ++this.nOrderErr + "/" + Config.MAX_ORDER_RETRY + ") ", e);
            if ( this.nOrderErr >= Config.MAX_ORDER_RETRY)
                throw new STBException( 70 );
        }
        log.exiting(this.getClass().getSimpleName(), "submit");
        return response;

    }


    private NewOrderResponse fakeResponse( NewOrder newOrder ) {
        NewOrderResponse response = new NewOrderResponse();
        String qty = newOrder.getQuantity();
        long now = System.currentTimeMillis();
        response.setExecutedQty( qty );
        response.setPrice( newOrder.getPrice() );
        response.setTransactTime( now );
        response.setOrigQty( qty );
        response.setOrderId( 12345L );
        response.setSide( newOrder.getSide() );
        response.setStatus( OrderStatus.FILLED );
        response.setTimeInForce( newOrder.getTimeInForce() );
        return response;
    }
}
