package SimpleTradingBot.Models;


import SimpleTradingBot.Exception.STBException;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.exception.BinanceApiException;

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

    public void restartUpdates() {
        for ( int i = updatedOrders.length - 1; i >= 0; i-- ) {
            if ( this.updatedOrders[i] != null )
                this.updatedOrders[i] = null;
        }
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

    public Order getUpdatedOrder( int i ) {
        return updatedOrders[ i ];
    }

    public int getnUpdate() {
        return nUpdate;
    }
}
