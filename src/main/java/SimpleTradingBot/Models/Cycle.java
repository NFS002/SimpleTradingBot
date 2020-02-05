package SimpleTradingBot.Models;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static SimpleTradingBot.Util.Static.toReadableTime;
import static SimpleTradingBot.Util.Static.toReadableDuration;

public class Cycle {

    public static int id = 0;

    public static BigDecimal netGain = BigDecimal.ZERO;

    public static int nwt = 0;

    public static int nlt = 0;

    public static int nnt = 0;

    public static int wl = 0;

    public static double sharpeRatio = 0;

    private final String symbol;

    private final Position buyPosition;

    private final long buyId;

    private final long openTime;

    private Position sellPosition;

    private long closeId;

    private int nBuyUpdates;

    private int nSellUpdates;

    private BigDecimal gain;

    private long closeTime;

    private long holdTime;

    private OrderStatus lastBuyStatus;

    private OrderStatus lastSellStatus;

    private final String origBuyQty;

    private String exBuyQty;

    private String origSellQty;

    private String exSellQty;

    private boolean finalised;

    /* For market orders */
    private BigDecimal openPrice;

    private BigDecimal closePrice;

    public static final String CSV_HEADER =
            "id,symbol,openPrice,openTime,buyId,nBuyUpdates,lastBuyStatus,origBuyQty,exBuyQty," +
            "closePrice,closeTime,closeId,nSellUpdates,lastSellStatus,origSellQty,exSellQty," +
            "holdTime,gain,nwt,nlt,nnt,ntt,wl,gain,netGain\n";

    public Cycle ( Position buyPosition, BigDecimal openPrice ) {
        this.finalised = false;
        this.buyPosition = buyPosition;
        NewOrder originalBuy = this.buyPosition.getOriginalOrder();
        NewOrderResponse originalBuyResponse = this.buyPosition.getOriginalOrderResponse();
        this.symbol = originalBuyResponse.getSymbol();
        this.buyId = originalBuyResponse.getOrderId();
        this.openTime = originalBuy.getTimestamp();
        this.openPrice = openPrice;
        this.origBuyQty = originalBuyResponse.getOrigQty();
    }


    public void setSellPosition( Position sellPosition, BigDecimal closePrice ) {
        this.sellPosition = sellPosition;
        this.closePrice = closePrice;
    }

    public String getOrigBuyQty() {
        return this.origBuyQty;
    }

    public String getExBuyQty() {
        return this.exBuyQty;
    }

    public String getOrigSellQty() {
        return this.origSellQty;
    }

    public String getExSellQty() {
        return this.exSellQty;
    }

    public String getSymbol() {
        return this.symbol;
    }

    public boolean isFinalised() {
        return this.finalised;
    }

    public OrderStatus getLastBuyStatus() {
        return this.lastBuyStatus;
    }

    public OrderStatus getLastSellStatus() {
        return this.lastSellStatus;
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

    public long getHoldTime() {
        return this.holdTime;
    }


    public void finalise() {
        Order lastUpdate = this.buyPosition.getLastUpdate();
        this.nBuyUpdates = this.buyPosition.getnUpdate();
        this.lastBuyStatus = lastUpdate.getStatus();
        this.exBuyQty = lastUpdate.getExecutedQty();
        this.gain = BigDecimal.ZERO;
        this.nSellUpdates = 0;
        this.closeId = 0;
        this.closeTime = 0;
        this.closePrice = BigDecimal.ZERO;
        this.holdTime = 0;
        this.lastSellStatus = null;
        if ( this.sellPosition != null ) {
            BigDecimal openPrice = new BigDecimal( this.buyPosition.getOriginalOrderResponse().getPrice() );
            BigDecimal closePrice = new BigDecimal( this.sellPosition.getOriginalOrderResponse().getPrice() );
            this.gain = ( closePrice.subtract( openPrice ))
                    .divide( openPrice, RoundingMode.HALF_UP )
                    .multiply( BigDecimal.valueOf( 100 ) );
            this.nSellUpdates = this.sellPosition.getnUpdate();
            NewOrder originalSell = this.sellPosition.getOriginalOrder();
            NewOrderResponse originalSellResponse = this.sellPosition.getOriginalOrderResponse();
            Order lastSellUpdate = this.sellPosition.getLastUpdate();
            this.lastSellStatus = lastSellUpdate.getStatus();
            this.origSellQty = originalSellResponse.getOrigQty();
            this.exSellQty = lastSellUpdate.getExecutedQty();
            this.closeId = originalSellResponse.getOrderId();
            this.closeTime = originalSell.getTimestamp();
            this.holdTime = this.closeTime - this.openTime;
        }
        int com = this.gain.compareTo( BigDecimal.ZERO );
        boolean won = com > 0;
        boolean loss = com < 0;
        if ( won )
            nwt += 1;
        else if ( loss )
            nlt += 1;
        else
            nnt += 1;
        if ( nlt == 0 )
            wl = Integer.MAX_VALUE;
        else
            wl = nwt / nlt;
        netGain = netGain.add( this.gain, MathContext.DECIMAL64);
        id += 1;
        this.finalised = true;
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
        return id + "," +
                symbol + "," +
                this.openPrice + "," +
                toReadableTime(this.openTime) + "," +
                this.buyId + "," +
                this.nBuyUpdates + "," +
                this.lastBuyStatus + "," +
                this.origBuyQty + "," +
                this.exBuyQty + "," +
                this.closePrice + "," +
                toReadableTime(this.closeTime) + "," +
                this.closeId + "," +
                this.nSellUpdates + "," +
                this.lastSellStatus + "," +
                this.origSellQty + "," +
                this.exSellQty + "," +
                toReadableDuration(this.holdTime) + "," +
                nwt + "," + nlt + "," + nnt + "," +
                (nwt + nlt + nlt) + wl + "," +
                this.gain + "," + netGain + "\n";
    }
}