package SimpleTradingBot.Models;

import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

public class RoundTrip {

    /* For market orders */
    private final BigDecimal openPrice;

    /* For market orders */
    private final BigDecimal closePrice;

    private final Position buyPosition;

    private final Position sellPosition;

    public RoundTrip(BigDecimal openPrice, BigDecimal closePrice,
                     Position buyPosition, Position sellPosition) {
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.buyPosition = buyPosition;
        this.sellPosition = sellPosition;
    }


    public String getStats() {

        NewOrder originalBuy = this.buyPosition.getOriginalOrder();
        NewOrderResponse originalBuyResponse = this.buyPosition.getOriginalOrderResponse();

        long buyId = originalBuyResponse.getOrderId();
        long openTime = originalBuy.getTimestamp();

        NewOrder originalSell = this.sellPosition.getOriginalOrder();
        NewOrderResponse originalSellResponse = this.sellPosition.getOriginalOrderResponse();

        long sellId = originalSellResponse.getOrderId();
        long closeTime = originalSell.getTimestamp();

        BigDecimal gain = (this.openPrice.subtract( this.closePrice )).divide( openPrice, RoundingMode.HALF_UP );

        int length = 5;
        String openStr = Static.safeDecimal( this.openPrice, length );
        String closeStr = Static.safeDecimal( this.closePrice, length );
        String gainStr = Static.safeDecimal( gain, length );


        String time = Static.toReadableDate( openTime );
        Duration holdTime = Duration.ofMillis( closeTime - openTime );
        int nBuyUpdates = this.buyPosition.getnUpdate();
        int nSellUpdates = this.sellPosition.getnUpdate();


        return  "Buy Order id: " + buyId + "\n"
                + "Sell Order id: " + sellId + "\n"
                + "Buy updates: " + nBuyUpdates + "\n"
                + "Sell updates " + nSellUpdates + "\n"
                + "Open price: " + openStr + "\n"
                + "Close price: " + closeStr +  "\n"
                + "Gain%: " + gainStr + "\n"
                + "Open time: " + time + "\n"
                + "Hold time (s): " + holdTime.getSeconds() + "\n";
    }
}
