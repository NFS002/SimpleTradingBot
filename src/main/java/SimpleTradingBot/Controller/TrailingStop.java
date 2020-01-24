package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import org.ta4j.core.Bar;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.util.logging.Logger;

import static SimpleTradingBot.Util.Static.safeDecimal;

public class TrailingStop {

    private BigDecimal stopLoss;

    private Logger log;

    public TrailingStop(TickerStatistics symbol) {
        String loggerName = this.getClass().getSimpleName();
        this.log = Logger.getLogger("root." + symbol.getSymbol() + "." + loggerName);
        this.stopLoss = BigDecimal.ZERO;
    }

    void reset() {
        setStopLoss( BigDecimal.ZERO );
    }

    public void setStopLoss( BigDecimal stopLoss ) {
        this.log.entering( this.getClass().getSimpleName(), "setStopLoss");
        this.stopLoss = stopLoss;
        log.info("Setting stop loss value to: " + stopLoss );
        this.log.exiting( this.getClass().getSimpleName(), "setStopLoss");
    }

    BigDecimal getStopLoss() {
        return this.stopLoss;
    }

    public void update( BigDecimal lastPrice ) throws BinanceApiException {
        log.entering(this.getClass().getSimpleName(),"update");
        this.log.info("Updating stop loss with last price of: " + lastPrice );
        BigDecimal newStopLoss = lastPrice.multiply( Config.STOP_LOSS_PERCENT );
        if ( newStopLoss.compareTo( this.stopLoss ) >= 0 )
            this.setStopLoss( newStopLoss );
        else
            this.log.info( "No change in stop loss required" );
        log.exiting(this.getClass().getSimpleName(), "update");
    }
}
