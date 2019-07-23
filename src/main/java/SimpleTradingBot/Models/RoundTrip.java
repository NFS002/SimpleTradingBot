package SimpleTradingBot.Models;

import SimpleTradingBot.Controller.Controller;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

public class RoundTrip {


    private final Position buyPosition;

    private Position sellPosition;

    private Controller controller;

    private long completedAt;

    private long interruptedAt;

    private Phase phase;

    public RoundTrip( Position buyPosition, Position sellPosition, Controller controller, Phase phase ) {
        this.controller = controller;
        this.buyPosition = buyPosition;
        this.sellPosition = sellPosition;
        this.completedAt = ( sellPosition == null ) ? -1: System.currentTimeMillis();
        this.interruptedAt = -1;
        this.phase = phase;
    }

    public RoundTrip( Position buyPosition, Controller controller ) {
        this( buyPosition, null, controller, Phase.BUY );
    }


    public RoundTrip( Position buyPosition ) {
        this( buyPosition, null,  null, Phase.BUY );
    }

    public void setSellPosition(Position sellPosition ) {
        this.sellPosition = sellPosition;
        this.completedAt = System.currentTimeMillis();
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public long getInterruptedAt() {
        return interruptedAt;
    }

    public void setInterruptedAt( long interruptedAt ) {
        this.interruptedAt = interruptedAt;
    }

    public Controller getController() {
        return controller;
    }

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public Position getBuyPosition() {
        return buyPosition;
    }

    public Position getSellPosition() {
        return sellPosition;
    }

    public long getCompletedAt() {
        return this.completedAt;
    }

    public String getStats() {

        NewOrder originalBuy = this.buyPosition.getOriginalOrder();
        NewOrderResponse originalBuyResponse = this.buyPosition.getOriginalOrderResponse();

        long buyId = originalBuyResponse.getOrderId();
        long openTime = originalBuy.getTimestamp();

        NewOrder originalSell = this.sellPosition.getOriginalOrder();
        NewOrderResponse originalSellResponse = this.sellPosition.getOriginalOrderResponse();

        BigDecimal openPrice = this.buyPosition.getPrice();
        BigDecimal closePrice = this.sellPosition.getPrice();

        long sellId = originalSellResponse.getOrderId();
        long closeTime = originalSell.getTimestamp();

        BigDecimal gain = ( openPrice.subtract( closePrice )).divide( openPrice, RoundingMode.HALF_UP );

        int length = 5;
        String openStr = Static.safeDecimal( openPrice, length );
        String closeStr = Static.safeDecimal( closePrice, length );
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
