package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.Bar;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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

    public TimeKeeper(TickerStatistics statistics) throws STBException {
        this.log = Logger.getLogger("root." + statistics.getSymbol());
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
    private void checkServerTime() throws STBException{
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
    public boolean checkEvent( Bar lastBar, CandlestickEvent candlestickEvent)
        throws STBException{

        log.entering(this.getClass().getSimpleName(), "checkEvent");

        checkInterval(candlestickEvent);

        ZonedDateTime lastBarEndTime = lastBar.getEndTime();

        long nextBarOpenTime = candlestickEvent.getOpenTime();
        Instant instant = Instant.ofEpochMilli(nextBarOpenTime);
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, Config.ZONE_ID);
        log.fine("Candlestick open time: " + zonedDateTime + ". Last bar close time: " + lastBarEndTime );
        long diff = lastBarEndTime.until(zonedDateTime, ChronoUnit.MILLIS);
        boolean check = checkEventTime(diff);
        log.exiting(this.getClass().getSimpleName(), "checkEvent");
        return check;

    }


    /* Checks if a given candlestick event is in time with the preceeding time series (Internal) */
    private boolean checkEventTime(long time_diff) throws STBException {
        log.entering(this.getClass().getSimpleName(), "checkEventTime");
        long tolerance = Config.INTERVAL_TOLERANCE; /* 5000 Milliseconds */
        int warningPeriod = 6;
        if ( this.nErr < Config.MAX_ERR - warningPeriod )
            log.fine("Next candlestick time difference: " + time_diff + ", tolerance: " + tolerance );
        else
            log.info("Next candlestick time difference: " + time_diff + ", tolerance: " + tolerance );
        if ( ( time_diff <= tolerance)  && ( time_diff  > 0 ) ) {
            this.nErr = 0;
            log.fine( "Candlestick in time" );
            log.exiting(this.getClass().getSimpleName(), "checkEventTime");
            return true;
        }
        else {
            if ( this.nErr <= Config.MAX_ERR - warningPeriod )
                log.fine("Candlestick event out of time, sequence: " + this.nErr );
            else
                log.info("Candlestick event out of time, sequence: " + this.nErr );

            log.exiting(this.getClass().getSimpleName(), "checkEventTime");

            if ( ++this.nErr >= Config.MAX_ERR) {
                this.nErr = 0;
                throw new STBException( 120 );
            }
            else {
                return false;
            }
        }
    }

    /* Check the time of this event is in line with our system clock */
    private void checkEventTime( CandlestickEvent candlestickEvent )
            throws STBException {
        log.entering(this.getClass().getSimpleName(), "checkEventTime");
        long eventTime = candlestickEvent.getEventTime();
        long myTime = System.currentTimeMillis();
        log.info("Candlestick event time: " + eventTime);
        log.info("Current system time: " + myTime);
        long diff = Math.abs(eventTime - myTime);
        if (diff > Config.RECV_WINDOW) {
            log.severe("Event time difference of " + lastTimeDifference + " exceeds maximum of " + Config.RECV_WINDOW);
            throw new STBException( 160 );
        } else
            log.info("Event time difference: " + lastTimeDifference);

        log.exiting(this.getClass().getSimpleName(), "checkEventTime");
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
        /* TODO */
        switch (id) {
            case "1m":
                return CandlestickInterval.ONE_MINUTE;
            case "5m":
                return CandlestickInterval.FIVE_MINUTES;
            default:
                return null;
        }
    }

    private static long intervalToMillis( CandlestickInterval interval ) {

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