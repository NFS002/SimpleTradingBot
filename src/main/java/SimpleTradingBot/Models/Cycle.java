package SimpleTradingBot.Models;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;

public class Cycle {

    private final Position buyPosition;

    private final long buyId;

    private final long openTime;

    private Position sellPosition;

    private long closeId;

    private int nBuyUpdates;

    private int nSellUpdates;

    private BigDecimal gain;

    private long closeTime;

    private Duration holdTime;

    /* For market orders */
    private BigDecimal openPrice;

    private BigDecimal closePrice;

    public Cycle ( Position buyPosition, BigDecimal openPrice ) {
        this.buyPosition = buyPosition;

        NewOrder originalBuy = this.buyPosition.getOriginalOrder();
        NewOrderResponse originalBuyResponse = this.buyPosition.getOriginalOrderResponse();

        this.buyId = originalBuyResponse.getOrderId();
        this.openTime = originalBuy.getTimestamp();
        this.openPrice = openPrice;
    }

    public void setSellPosition( Position sellPosition, BigDecimal closePrice ) {
        this.sellPosition = sellPosition;

        NewOrder originalSell = this.sellPosition.getOriginalOrder();
        NewOrderResponse originalSellResponse = this.sellPosition.getOriginalOrderResponse();

        this.closeId = originalSellResponse.getOrderId();
        this.closeTime = originalSell.getTimestamp();
        this.holdTime = Duration.ofMillis( this.closeTime - this.openTime );
        this.closePrice = closePrice;
        this.setStats();
    }

    public BigDecimal getOpenPrice() {
        return this.openPrice;
    }

    public BigDecimal getClosePrice() {
        return this.closePrice;
    }

    public BigDecimal getGain() {
        return this.gain;
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

    public long getCloseId() {
        return closeId;
    }

    public int getnBuyUpdates() {
        return this.nBuyUpdates;
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

    private void setStats() {
        BigDecimal openPrice = new BigDecimal( this.buyPosition.getOriginalOrderResponse().getPrice() );
        BigDecimal closePrice = new BigDecimal( this.sellPosition.getOriginalOrderResponse().getPrice() );
        this.gain = ( closePrice.subtract( openPrice ))
                .divide( closePrice, RoundingMode.HALF_UP )
                .multiply( BigDecimal.valueOf( 100 ) );

        this.holdTime = Duration.ofMillis( closeTime - openTime );
        this.nBuyUpdates = this.buyPosition.getnUpdate();
        this.nSellUpdates = this.sellPosition.getnUpdate();
    }


    private BigDecimal getAveragePrice(OrderSide side ) {
        Position position = side == OrderSide.BUY ? this.buyPosition : this.sellPosition;
        BigDecimal total = BigDecimal.ZERO;
        int size = this.getnBuyUpdates();
        for ( int i = 0; i < size; i++ ) {
            BigDecimal price = new BigDecimal( position.getUpdatedOrder( i ).getPrice() );
            total = total.add( price, MathContext.DECIMAL64 );
        }
        return total.divide( BigDecimal.valueOf( size ), MathContext.DECIMAL64 );
    }

    public String toCsv() {
        NewOrder originalBuyOrder = this.getBuyPosition().getOriginalOrder();
        String symbol = originalBuyOrder.getSymbol();
        return symbol + "," +
                this.openPrice + "," +
                this.openTime + "," +
                this.buyId + "," +
                this.nBuyUpdates + "," +
                this.closePrice + "," +
                this.closeTime + "," +
                this.closeId + "," +
                this.holdTime + "," +
                this.gain;
    }
}