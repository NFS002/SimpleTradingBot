package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.Bar;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/* Keeps track of all chronological events,
 * making sure they are in the expected order
 */
public class TimeKeeper {

    /* For checking server time to make sure we are in sync */
    private final BinanceApiRestClient client;

    private long lastTimeDifference;

    private final Logger log;

    private int nErr;

    private int nServerChecks;

    private long startStreamTime;

    private long endStreamTime;

    public TimeKeeper( String symbol ) throws STBException {
        this.log = Logger.getLogger("root." + symbol + ".tk");
        this.client = Static.getFactory().newRestClient();
        this.nServerChecks = 0;
        this.startStreamTime = this.endStreamTime = 0;
        this.nErr = 0;
        updateServerTime();
    }

    public void startStream(long currentTime) {
        this.startStreamTime = currentTime;
    }

    public void endStream(long endStreamTime) {
        this.endStreamTime = endStreamTime;
    }

    public long getStreamTime() {
        if (this.startStreamTime == 0 || this.endStreamTime == 0)
            return 0;
        return (this.endStreamTime - this.startStreamTime) / 1000;
    }

    /* Check we are in sync with the binance server system time */
    public void updateServerTime() throws STBException {
        long serverTime = client.getServerTime();
        long clientTime = System.currentTimeMillis();
        if (this.nServerChecks++ == 0) {
            this.lastTimeDifference = serverTime - clientTime;
        } else {
            long diff = serverTime - clientTime;
            long ddiff = Math.abs(this.lastTimeDifference - diff);
            if (ddiff > Config.MAX_DDIFF) {
                log.severe("Change in time difference: " + ddiff + " exceeds maximum of " + Config.MAX_DDIFF );
                throw new STBException( 150 );
            }
        }
        checkServerTime();
    }

    /* Check we are in sync with the binance server system time (Internal) */
    private void checkServerTime() throws STBException {
        log.entering(this.getClass().getSimpleName(), "checkServerTime");
        if ( this.lastTimeDifference > Config.RECV_WINDOW ) {
            log.severe("Server time difference of " + this.lastTimeDifference + " exceeds maximum of " + Config.RECV_WINDOW + ". Checks made: " + nServerChecks);
            throw new STBException( 110 );
        }
        else {
            log.info("Server time difference: " + lastTimeDifference);
        }
        log.exiting(this.getClass().getSimpleName(), "checkServerTime");
    }

    /* Checks if a given candlestick event is in time with the preceeding time series */
    public boolean checkEvent( Bar lastBar, CandlestickEvent candlestickEvent) throws STBException{

        this.log.entering(this.getClass().getSimpleName(), "checkEvent");

        checkInterval(candlestickEvent);

        ZonedDateTime lastBarEndTime = lastBar.getEndTime();

        long candlestickTimeMillis = candlestickEvent.getOpenTime();
        Instant instant = Instant.ofEpochMilli(candlestickTimeMillis);
        ZonedDateTime candlestickTime = ZonedDateTime.ofInstant(instant, Config.ZONE_ID);

        long diff = lastBarEndTime.until(candlestickTime, ChronoUnit.MILLIS);

        this.log.fine("Candlestick open time: " + candlestickTime + ". Last bar close time: " + lastBarEndTime + ". Difference (ms): " + diff);

        boolean check = checkEventTime(diff);

        log.exiting(this.getClass().getSimpleName(), "checkEvent");

        return check;

    }


    /* Checks if a given candlestick event is in time with the preceeding time series (Internal) */
    private boolean checkEventTime(long time_diff) throws STBException {
        log.entering(this.getClass().getSimpleName(), "checkEventTime");
        int warningPeriod = 3;
        Level level = this.nErr < Config.MAX_ERR - warningPeriod ? Level.FINE :  Level.WARNING;
        long intervalMillis = intervalToMillis( Config.CANDLESTICK_INTERVAL );

        log.log(level, "Next candlestick time difference: " + time_diff +
                ", tolerance: " + Config.INTERVAL_TOLERANCE +
                ", sequence: " + this.nErr   );

        if ( (time_diff >= intervalMillis - Config.INTERVAL_TOLERANCE) && (time_diff <= intervalMillis + Config.INTERVAL_TOLERANCE) ) {
            log.exiting(this.getClass().getSimpleName(), "checkEventTime");
            this.nErr = 0;
            return true;
        }
        else {
            this.log.exiting(this.getClass().getSimpleName(), "checkEventTime");

            if ( ++this.nErr >= Config.MAX_ERR) {
                this.nErr = 0;
                throw new STBException( 120 );
            }

            return false;
        }
    }

    private void checkInterval(CandlestickEvent candlestickEvent)
        throws STBException {
        log.entering(this.getClass().getSimpleName(), "checkInterval");

        String intervalId = candlestickEvent.getIntervalId();
        CandlestickInterval interval = getIntervalFromId(intervalId);
        log.fine("Candlestick interval: " + intervalId);
        if (interval != Config.CANDLESTICK_INTERVAL) {
            log.severe("Incorrect candlestick interval: " + interval);
            throw new STBException( 140 );
        }
        log.exiting(this.getClass().getSimpleName(), "checkInterval");
    }

    private static CandlestickInterval getIntervalFromId( String id ) {
        switch (id) {
            case "1m":
                return CandlestickInterval.ONE_MINUTE;
            case "5m":
                return CandlestickInterval.FIVE_MINUTES;
            default:
                return null;
        }
    }

    public static Duration intervalToDuration( CandlestickInterval interval ) {
        long millis = intervalToMillis(interval);
        return Duration.ofMillis(millis);
    }

    public static long intervalToMillis( CandlestickInterval interval ) {
        switch ( interval ) {
            case ONE_MINUTE:
                return (60 * 1000);
            case FIVE_MINUTES:
                return (5 * 60 * 1000);
            case HOURLY:
                return (60 * 60 * 1000);
            default:
                return 0;
        }
    }
}