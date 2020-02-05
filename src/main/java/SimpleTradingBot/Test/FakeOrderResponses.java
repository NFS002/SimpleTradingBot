package SimpleTradingBot.Test;

import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Random;

import static com.binance.api.client.domain.OrderStatus.*;

/* Fake order responses and updates for testing order cycles */
public class FakeOrderResponses {

    private static final long ORDER_ID = 12345L;

    private static final HashMap<String, OrderStatus[]> updateMap;

    private static final HashMap<String, Integer> stageMap;

    static {
        stageMap = new HashMap<>();
        updateMap = new HashMap<>();
    }

    public static void register( String symbol ) {
        int n = updateMap.size();
        stageMap.put( symbol, 0 );
        updateMap.put( symbol, getUpdatePlan( n ) );
    }

    private static OrderStatus[] getUpdatePlan( int n ) {

        switch ( n ) {

            default:

                return new OrderStatus[]{ FILLED };

        }

    }

    public static Order getNextUpdate( NewOrderResponse response ) {
        String symbol = response.getSymbol();
        if ( !stageMap.containsKey( symbol) )
            throw new IllegalArgumentException();
        int stage = stageMap.get( symbol );
        OrderStatus[] statuses = updateMap.get( symbol );
        int len = statuses.length;
        OrderStatus status = statuses[ stage ];
        stage += 1;
        if ( stage > len - 1  )
            stageMap.put( symbol, 0 );
        else
            stageMap.put( symbol, stage  );
        Order update = fakeUpdate( response );
        update.setStatus( status );
        if ( status == EXPIRED || status == REJECTED )
            update.setExecutedQty(BigDecimal.ZERO.toString() );
        return update;
    }

    public static NewOrderResponse fakeFilledResponse(NewOrder newOrder, String price ) {
        NewOrderResponse response = new NewOrderResponse();
        long now = System.currentTimeMillis();
        response.setSymbol( newOrder.getSymbol() );
        response.setExecutedQty( newOrder.getQuantity() );
        response.setPrice( newOrder.getPrice() );
        response.setTransactTime( now );
        response.setPrice( price );
        response.setOrigQty( newOrder.getQuantity() );
        response.setOrderId( ORDER_ID );
        response.setSide( newOrder.getSide() );
        response.setStatus( OrderStatus.FILLED );
        response.setTimeInForce( newOrder.getTimeInForce() );
        return response;
    }

    public static NewOrderResponse fakePartialResponse(NewOrder newOrder, String price ) {
        NewOrderResponse response = fakeFilledResponse( newOrder, price );
        response.setStatus( OrderStatus.PARTIALLY_FILLED );
        return response;
    }

    public static NewOrderResponse fakeNewResponse(NewOrder newOrder, String price ) {
        NewOrderResponse response = fakeFilledResponse( newOrder, price );
        response.setStatus( OrderStatus.NEW );
        return response;
    }

    public static NewOrderResponse fakeCancelledResponse(NewOrder newOrder, String price ) {
        NewOrderResponse response = fakeFilledResponse( newOrder, price );
        response.setStatus( OrderStatus.CANCELED );
        return response;
    }

    public static CancelOrderResponse fakeCancelledResponse( CancelOrderRequest request ) {
        CancelOrderResponse response = new CancelOrderResponse();
        response.setOrderId( String.valueOf(request.getOrderId()) );
        response.setSymbol( request.getSymbol() );
        response.setClientOrderId( request.getOrigClientOrderId() );
        return response;
    }

    public static NewOrderResponse fakeExpiredResponse(NewOrder newOrder, String price ) {
        NewOrderResponse response = fakeFilledResponse( newOrder, price );
        response.setStatus( OrderStatus.EXPIRED );
        return response;
    }

    public static NewOrderResponse fakeRejectedResponse(NewOrder newOrder, String price ) {
        NewOrderResponse response = fakeFilledResponse( newOrder, price );
        response.setStatus( OrderStatus.REJECTED );
        return response;
    }

    public static NewOrderResponse fakePendingResponse(NewOrder newOrder, String price ) {
        NewOrderResponse response = fakeFilledResponse( newOrder, price );
        response.setStatus( OrderStatus.PENDING_CANCEL );
        return response;
    }

    private static Order fakeUpdate( NewOrderResponse newOrder  ) {
        Order update = new Order();
        update.setOrderId( newOrder.getOrderId() );
        update.setSide( newOrder.getSide() );
        update.setOrigQty( newOrder.getOrigQty() );
        update.setSymbol( newOrder.getSymbol() );
        update.setTimeInForce( TimeInForce.FOK );
        update.setExecutedQty( String.valueOf( Double.parseDouble(update.getOrigQty())/2 ) );
        return update;
    }

    public static NewOrderResponse fakeRandomResponse(NewOrder newOrder, String price ) {
        Random rand = new Random();
        int r = rand.nextInt( 7);
        switch ( r ) {
            case 0: return fakeCancelledResponse( newOrder, price );
            case 1: return fakeExpiredResponse( newOrder, price );
            case 2: return fakeFilledResponse( newOrder, price );
            case 3: return fakeNewResponse( newOrder, price );
            case 4: return fakePartialResponse( newOrder, price );
            case 5: return fakePendingResponse( newOrder, price );
            default: return fakeRejectedResponse( newOrder, price );
        }
    }
}