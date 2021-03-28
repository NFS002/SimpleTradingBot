package SimpleTradingBot.Models;

import SimpleTradingBot.Config.Config;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;

import static SimpleTradingBot.Util.Static.toReadableDateTime;
import static SimpleTradingBot.Util.Static.toReadableDuration;
import static SimpleTradingBot.Config.Config.SKIP_SLIPPAGE_TRADES;

public class Cycle {

    public static int id = 0;

    public static BigDecimal netGain = BigDecimal.ZERO;

    public static double n_winning_trades = 0;

    public static double n_losing_trades = 0;

    public static double n_neutral_trades = 0;

    public static double win_loss_pc = 0;

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

    private int n_ticks;

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

    private boolean slippage;

    public static final String CSV_HEADER =
            "id,symbol,openPrice,openTime,buyId,nBuyUpdates,lastBuyStatus,origBuyQty,exBuyQty," +
            "closePrice,closeTime,closeId,nSellUpdates,lastSellStatus,origSellQty,exSellQty," +
            "holdTime,n_ticks,slippage,gain,n_winning_trades,n_losing_trades,n_neutral_trades,n_total_trades,win_loss_pc,netGain\n";

    public Cycle ( Position buyPosition ) {
        this.finalised = false;
        this.buyPosition = buyPosition;
        NewOrder originalBuy = this.buyPosition.getOriginalOrder();
        NewOrderResponse originalBuyResponse = this.buyPosition.getOriginalOrderResponse();
        this.symbol = originalBuyResponse.getSymbol();
        this.buyId = originalBuyResponse.getOrderId();
        this.openTime = originalBuy.getTimestamp();
        this.openPrice = new BigDecimal( originalBuyResponse.getPrice() );
        this.origBuyQty = originalBuyResponse.getOrigQty();
        this.n_ticks = 0;
        this.slippage = false;
    }


    public void setSellPosition( Position sellPosition ) {
        this.sellPosition = sellPosition;
    }

    public void incTicks() {
        this.n_ticks = this.n_ticks + 1;
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

    public boolean hasSlippage() {
        return this.slippage;
    }

    public long getHoldTime() {
        return this.holdTime;
    }

    public void setSlippage() {
        this.slippage = true;
    }

    public void finalise() {

        if ( this.slippage && SKIP_SLIPPAGE_TRADES ) return;

        Order lastUpdate = this.buyPosition.getLastUpdate();
        this.nBuyUpdates = this.buyPosition.getnUpdate();
        this.lastBuyStatus = lastUpdate.getStatus();
        this.exBuyQty = lastUpdate.getExecutedQty();
        if ( this.sellPosition == null ) {
            this.gain = BigDecimal.ZERO;
            this.nSellUpdates = 0;
            this.closePrice = BigDecimal.ZERO;
            this.closeId = 0;
            this.closeTime = 0;
            this.holdTime = 0;
            this.lastSellStatus = null;
        }
        else {
            this.closePrice = new BigDecimal( this.sellPosition.getOriginalOrderResponse().getPrice() );
            this.gain = ( this.closePrice.subtract( this.openPrice ))
                    .divide( this.openPrice, RoundingMode.HALF_UP )
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
            n_winning_trades += 1;
        else if ( loss )
            n_losing_trades += 1;
        else
            n_neutral_trades += 1;

        if ( n_winning_trades + n_losing_trades == 0 )
            win_loss_pc = 50;
        else
            win_loss_pc = (n_winning_trades / (n_winning_trades + n_losing_trades) ) * 100;

        netGain = netGain.add( this.gain, MathContext.DECIMAL64);
        id += 1;
        this.finalised = true;
    }



    private BigDecimal getAveragePrice(OrderSide side ) {
        Position position = side == OrderSide.BUY ? this.buyPosition : this.sellPosition;
        BigDecimal total = BigDecimal.ZERO;
        int size = this.getnBuyUpdates();
        for ( int i = 0; i < size; i++ ) {
            BigDecimal price = new BigDecimal( position.getUpdate( i ).getPrice() );
            total = total.add( price, MathContext.DECIMAL64 );
        }
        return total.divide( BigDecimal.valueOf( size ), MathContext.DECIMAL64 );
    }

    public HashMap<String, Object> toMap() {
        if (!this.isFinalised())
            this.finalise();

        HashMap<String, Object> params = new HashMap<>();
        params.put("symbol", this.symbol);
        params.put("ticks", this.n_ticks);
        params.put("gain", this.gain);
        params.put("net_gain", netGain);
        params.put("exc_quantities", this.exSellQty + "/" + this.exBuyQty);
        params.put("buy_updates", this.nBuyUpdates);
        params.put("sell_updates", this.nSellUpdates);
        params.put("total_cycles", n_winning_trades + n_losing_trades + n_neutral_trades);
        params.put("win_loss_pc", win_loss_pc);

        return params;
    }

    public String toCsv() {
        NewOrder originalBuyOrder = this.getBuyPosition().getOriginalOrder();
        String symbol = originalBuyOrder.getSymbol();
        return id + "," +
                symbol + "," +
                this.openPrice + "," +
                toReadableDateTime(this.openTime) + "," +
                this.buyId + "," +
                this.nBuyUpdates + "," +
                this.lastBuyStatus + "," +
                this.origBuyQty + "," +
                this.exBuyQty + "," +
                this.closePrice + "," +
                toReadableDateTime(this.closeTime) + "," +
                this.closeId + "," +
                this.nSellUpdates + "," +
                this.lastSellStatus + "," +
                this.origSellQty + "," +
                this.exSellQty + "," +
                toReadableDuration(this.holdTime) + "," +
                this.n_ticks + "," +
                this.slippage + "," +
                this.gain + "," +
                n_winning_trades + "," +
                n_losing_trades + "," +
                n_neutral_trades + "," +
                (n_winning_trades + n_losing_trades + n_neutral_trades) + "," +
                win_loss_pc + "," +
                netGain + "\n";
    }
}