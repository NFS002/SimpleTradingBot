package SimpleTradingBot.Models;


import SimpleTradingBot.Exception.STBException;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.exception.BinanceApiException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

import static SimpleTradingBot.Config.Config.MAX_ORDER_UPDATES;

public class Position {

    private final NewOrder originalOrder;

    private NewOrderResponse originalOrderResponse;

    private Order[] updatedOrders;

    private int nUpdate;

    public Position(NewOrder originalOrder, NewOrderResponse originalOrderResponse) {
        this.originalOrder = originalOrder;
        this.originalOrderResponse = originalOrderResponse;
        this.nUpdate = 0;
        this.updatedOrders = new Order[ MAX_ORDER_UPDATES ];
    }

    public void setUpdatedOrder( Order updatedOrder ) throws STBException {

        if (this.nUpdate < MAX_ORDER_UPDATES - 1 )
            this.updatedOrders[ this.nUpdate++ ] = updatedOrder;
        else
            throw new STBException( 90 );
    }

    public Order getLastUpdate() throws STBException {
        for ( int i = updatedOrders.length - 1; i >= 0; i-- ) {
            Order update = this.updatedOrders[i];
            if ( update != null )
                return update;
        }

        throw new STBException( 100 );
    }

    public void cancelOrder( CancelOrderResponse response ) {
        Order update = this.copyToUpdate( response );
        this.setUpdatedOrder( update );
    }

    public void cancelFakeOrder( CancelOrderResponse response ) {
        Order update = this.copyToUpdate( response );
        Order lastUpdate = this.getLastUpdate();
        String status = response.getStatus();
        if ( status == null || status.trim().isEmpty() )
            update.setStatus( OrderStatus.CANCELED );
        String exQty = response.getExecutedQty();
        if ( exQty == null || exQty.trim().isEmpty() )
            update.setExecutedQty( lastUpdate.getExecutedQty() );
        this.setUpdatedOrder( update );
    }

    public BigDecimal getAllExecuted() {
        BigDecimal total = BigDecimal.ZERO;
        for ( Order update : this.updatedOrders ) {
            if ( update.getExecutedQty() != null ) {
                BigDecimal ex = new BigDecimal(update.getExecutedQty());
                if ( ex.compareTo( BigDecimal.ZERO ) > 0) {
                    total = total.add(ex, MathContext.DECIMAL64);
                }
            }
        }
        return total;
    }

    public void restartUpdates() {
        for ( int i = updatedOrders.length - 1; i >= 0; i-- ) {
            if ( this.updatedOrders[i] != null )
                this.updatedOrders[i] = null;
        }
    }

    private Order copyToUpdate( CancelOrderResponse response ) {
        Order update = new Order();
        String status = response.getStatus();
        if ( status != null && status.trim().isEmpty() )
            update.setStatus( OrderStatus.valueOf( status ));
        String orderId = response.getOrderId();
        if ( orderId != null && orderId.trim().isEmpty() )
            update.setOrderId( Long.valueOf( orderId ));
        String exQty = response.getExecutedQty();
        if ( exQty != null && !exQty.trim().isEmpty() )
            update.setExecutedQty( exQty );
        String clientOrderId = response.getClientOrderId();
        if ( clientOrderId != null && !clientOrderId.trim().isEmpty() )
            update.setClientOrderId( response.getClientOrderId() );
        String symbol = response.getSymbol();
        if ( symbol != null && !symbol.trim().isEmpty() )
            update.setSymbol( symbol );
        return update;
    }

    public NewOrder getOriginalOrder() {
        return originalOrder;
    }

    public NewOrderResponse getOriginalOrderResponse() {
        return originalOrderResponse;
    }

    public void setOriginalOrderResponse( NewOrderResponse originalOrderResponse ) {
        this.originalOrderResponse = originalOrderResponse;
    }

    public Order getUpdate(int i ) {
        return this.updatedOrders[ i ];
    }

    public int getnUpdate() {
        return this.nUpdate;
    }

}
