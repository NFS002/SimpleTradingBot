package SimpleTradingBot.Models;

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

    private final BigDecimal gain;

    private final Position buyPosition;

    private final Position sellPosition;

    private final long buyId;

    private final long sellId;

    private final int nBuyUpdates;

    private final int nSellUpdates;

    private final long openTime;

    private final long closeTime;

    private final Duration holdTime;

    public RoundTrip(BigDecimal openPrice, BigDecimal closePrice,
                     Position buyPosition, Position sellPosition) {

        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.buyPosition = buyPosition;
        this.sellPosition = sellPosition;

        NewOrder originalBuy = this.buyPosition.getOriginalOrder();
        NewOrderResponse originalBuyResponse = this.buyPosition.getOriginalOrderResponse();

        this.buyId = originalBuyResponse.getOrderId();
        this.openTime = originalBuy.getTimestamp();

        NewOrder originalSell = this.sellPosition.getOriginalOrder();
        NewOrderResponse originalSellResponse = this.sellPosition.getOriginalOrderResponse();

        this.sellId = originalSellResponse.getOrderId();
        this.closeTime = originalSell.getTimestamp();

        this.gain = (this.closePrice.subtract( this.openPrice ))
                .divide( closePrice, RoundingMode.HALF_UP )
                .multiply( BigDecimal.valueOf( 100 ) );

        this.holdTime = Duration.ofMillis( closeTime - openTime );
        this.nBuyUpdates = this.buyPosition.getnUpdate();
        this.nSellUpdates = this.sellPosition.getnUpdate();
    }

    public BigDecimal getGain() {
        return gain;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public Position getBuyPosition() {
        return buyPosition;
    }

    public Position getSellPosition() {
        return sellPosition;
    }

    public long getBuyId() {
        return buyId;
    }

    public long getSellId() {
        return sellId;
    }

    public int getnBuyUpdates() {
        return nBuyUpdates;
    }

    public int getnSellUpdates() {
        return nSellUpdates;
    }

    public long getOpenTime() {
        return openTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public Duration getHoldTime() {
        return holdTime;
    }
}
