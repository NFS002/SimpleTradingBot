package SimpleTradingBot.Models;


import SimpleTradingBot.Exception.STBException;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.exception.BinanceApiException;

import java.math.BigDecimal;

import static SimpleTradingBot.Config.Config.MAX_ORDER_UPDATES;

public class Position {

    private final BigDecimal price;

    private final NewOrder originalOrder;

    private NewOrderResponse originalOrderResponse;

    private Order[] updatedOrders;

    private int nUpdate;

    public Position(NewOrder originalOrder, NewOrderResponse originalOrderResponse, BigDecimal price ) {
        this.price = price;
        this.originalOrder = originalOrder;
        this.originalOrderResponse = originalOrderResponse;
        this.nUpdate = 0;
        this.updatedOrders = new Order[ MAX_ORDER_UPDATES ];
    }

    public void setUpdatedOrder( Order updatedOrder ) throws STBException {

        try {
            this.updatedOrders[ nUpdate ] = updatedOrder;
            nUpdate++;
        }
        catch ( IndexOutOfBoundsException e ) {
            throw new STBException( 90 );
        }
    }

    public Order getLastUpdate()  {
        for ( int i = updatedOrders.length - 1; i >= 0; i-- ) {
            Order update = this.updatedOrders[i];
            if ( update != null )
                return update;
        }
        return null;
    }

    public NewOrder getOriginalOrder() {
        return originalOrder;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public NewOrderResponse getOriginalOrderResponse() {
        return originalOrderResponse;
    }

    public void setOriginalOrderResponse( NewOrderResponse originalOrderResponse ) {
        this.originalOrderResponse = originalOrderResponse;
    }

    public Order getUpdatedOrder( int i ) {
        return updatedOrders[ i ];
    }

    public int getnUpdate() {
        return nUpdate;
    }
}
