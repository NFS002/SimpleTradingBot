package SimpleTradingBot.Controller;
import SimpleTradingBot.Config.Config;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;

import java.io.PrintWriter;
import java.util.logging.Logger;

public class TrailingStop {


    private double stopLoss;

    private Logger log;

    private int n = 0;

    public TrailingStop(TickerStatistics symbol) {
        this.log = Logger.getLogger("root." + symbol + ".slb");
        this.stopLoss = 0;
    }

    public void setStopLoss(double stopLoss) {
        this.stopLoss = stopLoss;
        log.info("Setting initial stop loss value to: " + stopLoss);
    }

    public double getStopLoss() {
        return stopLoss;
    }

    public void updateStopLoss(double lastPrice) throws BinanceApiException {
        log.entering(this.getClass().getSimpleName(),"updateStopLoss");
        double newStopLoss = lastPrice * Config.trailingLoss;
        stopLoss = Math.max(stopLoss, newStopLoss);
        log.exiting(this.getClass().getSimpleName(), "updateStopLoss");
        /*boolean shouldClose = false;
        String msg = "\nUpdate :  " + ++n + "\nCurrent stop loss: " + stopLoss + "\nReceived close: " + lastPrice;
        log.info(msg);
        if (stopLoss != 0) {
            double newStopLoss = lastPrice * Config.trailingLoss;
            if (lastPrice <= stopLoss) {
                log.info("Closing position");
                shouldClose = true;
            }
            else if (newStopLoss > stopLoss) {
                this.stopLoss = newStopLoss;
                log.info("Updating stop loss calculated at : " + String.valueOf(this.stopLoss));
            }
            else {
                log.info("Maintaining stop loss");
            }
        }
        else log.info("No open order");
        log.exiting(this.getClass().getSimpleName(),"updateStopLoss");
        return shouldClose;*/
    }
}
