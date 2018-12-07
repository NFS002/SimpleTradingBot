package SimpleTradingBot.Controller;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.client.domain.market.TickerStatistics;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Trader {

    private Logger log;

    private TickerStatistics symbol;

    private NewOrder openBuyOrder;

    private NewOrder openSellOrder;

    public Trader(TickerStatistics symbol) {
        this.symbol = symbol;
        log = Logger.getLogger("root." + symbol + ".buyer");
    }



    public NewOrder getOpenBuyOrder() {
        return openBuyOrder;
    }

    public NewOrder getOpenSellOrder() {
        return openSellOrder;
    }

    boolean open(double price) {
        try {
            log.entering(this.getClass().getSimpleName(), "open");
            NewOrder order = new NewOrder(symbol.getSymbol(), OrderSide.BUY, OrderType.MARKET, TimeInForce.FOK, String.valueOf(Config.budgetPerTrade/ price));
            order.stopPrice(String.valueOf(Config.stopLoss * price));
            log.info("Buy order: " + order);
            submit(order);
            this.openSellOrder = null;
            return true;
        }
        catch (BinanceApiException e) {
            log.info("Unable to open position");
            log.log(Level.SEVERE,e.getMessage(), e);
            return false;
        }
        finally {
            log.exiting(this.getClass().getSimpleName(), "open");
        }
    }

    boolean close() {
        try {
            log.info("Closing position");
            NewOrder order = new NewOrder(symbol.getSymbol(), OrderSide.BUY, OrderType.MARKET, TimeInForce.FOK, this.openBuyOrder.getQuantity());
            log.info("Sell order: " + order);
            submit(order);
            log.info("Logging RT");
            logOrderStats();
            this.openBuyOrder = this.openSellOrder = null;
            return true;

        }
        catch (BinanceApiException e) {
            log.severe("Unable to close position");
            log.log(Level.SEVERE,e.getMessage(),e);
            return false;
        }
        finally {
            log.exiting(this.getClass().getSimpleName(), "open");
        }
    }

    private void submit(NewOrder order) throws BinanceApiException {
        BinanceApiRestClient client = Static.getFactory().newRestClient();
        log.entering(this.getClass().getSimpleName(), "submit");
        int maxRetry = 5;
        for (int i = 0;i < maxRetry; i++) {
            NewOrderResponse response = client.newOrder(order);
            log.log(Level.INFO, "Order response: ", response);
            if (orderSuccess(response)) {
                log.log(Level.INFO, "Order submitted: ", order);
                switch (order.getSide()){
                    case BUY:           this.openBuyOrder = order;
                                        return;
                    case SELL:          this.openSellOrder = order;
                                        return;
                }
            }
        }
        throw new BinanceApiException("Unable to submit order ");
    }

    private boolean orderSuccess(NewOrderResponse response) {
        return (response.getStatus() == OrderStatus.FILLED);
    }

    private void logOrderStats() {
        double openPrice = Double.parseDouble(openBuyOrder.getPrice());
        double closePrice = Double.parseDouble(openSellOrder.getPrice());
        double gain = ((closePrice - openPrice)/openPrice) * 100;
        Duration duration = Duration.ofMillis(openSellOrder.getTimestamp() - openBuyOrder.getTimestamp());
        StringBuilder msg = new StringBuilder();
        msg.append("Open price: " + openPrice + "\n");
        msg.append("Close price: " + closePrice +  "\n");
        msg.append("Gain%: " + gain);
        msg.append("Hold time (s): " + duration);
        log.info( "\n" + msg.toString() + "\n");
        String line = symbol + "," + duration.getSeconds() + "," + openPrice + "," + closePrice+ "," + gain + "%" + "\n";
        Static.appendRt(line);
        log.exiting(this.getClass().getSimpleName(),"RT");
    }
}
