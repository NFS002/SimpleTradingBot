package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import org.ta4j.core.Bar;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.util.logging.Logger;


public class TrailingStop {

    private BigDecimal stopLoss;

    private Logger log;

    public TrailingStop( String symbol) {
        String loggerName = this.getClass().getSimpleName();
        this.log = Logger.getLogger("root." + symbol + "." + loggerName);
        this.stopLoss = BigDecimal.ZERO;
    }

    void reset() {
        setStopLoss( BigDecimal.ZERO );
    }

    public void setStopLoss( BigDecimal stopLoss ) {
        this.log.entering( this.getClass().getSimpleName(), "setStopLoss");
        this.stopLoss = stopLoss;
        log.info("Setting stop loss value to: " + Static.formatNum( PrecisionNum.valueOf( stopLoss ) ) );
        this.log.exiting( this.getClass().getSimpleName(), "setStopLoss");
    }

    BigDecimal getStopLoss() {
        return this.stopLoss;
    }

    public void update( Bar bar ) {
        update( (BigDecimal) bar.getClosePrice().getDelegate() );
    }

    public void update( BigDecimal lastPrice ) throws BinanceApiException {
        log.entering(this.getClass().getSimpleName(),"maintain");
        BigDecimal newStopLoss = lastPrice.multiply( Config.STOP_LOSS_PERCENT );
        if ( newStopLoss.compareTo( this.stopLoss ) >= 0 ) {
            log.info("Updating stop loss: " + Static.safeDecimal( lastPrice, 5));
            this.stopLoss = newStopLoss;
        }
        else
            log.info( "No stop loss update required" );
        log.exiting(this.getClass().getSimpleName(), "maintain");
    }
}
