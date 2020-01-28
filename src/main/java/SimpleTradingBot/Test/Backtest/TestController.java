package SimpleTradingBot.Test.Backtest;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.LiveController;
import SimpleTradingBot.Controller.LiveTrader;
import SimpleTradingBot.Controller.TAbot;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.PositionState;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Handler;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Test.FakeOrderResponses;
import SimpleTradingBot.Test.TestLevel;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.exception.BinanceApiException;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.PrecisionNum;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.*;

public class TestController implements BinanceApiCallback<CandlestickEvent> {

    private TAbot taBot;

    private Handler handler;

    private TimeSeries timeSeries;

    private TestTrader buyer;

    private Logger log;

    private PrintWriter tsWriter;

    private int coolOff;

    private final String symbol;

    /* Constructor */

    public TestController( String symbol ) throws IOException {

        this.symbol = symbol;
        BaseTimeSeries.SeriesBuilder builder = new BaseTimeSeries.SeriesBuilder();
        builder.withNumTypeOf( PrecisionNum::valueOf )
                .withMaxBarCount( MAX_BAR_COUNT )
                .withName( symbol );

        this.timeSeries = builder.build();
        this.buyer = new TestTrader( symbol );
        this.handler = new Handler( symbol );
        this.taBot = new TAbot( symbol );
        this.coolOff = 0;

        File baseDir = initBaseDir();

        if ( LOG_TS_AT != -1 )
            initTsWriter( baseDir );

        initLogger( );

        if ( TEST_LEVEL == TestLevel.FAKEORDER )
            FakeOrderResponses.register( symbol );

    }

    /* Getters & Setters */



    public PositionState getState() {
        return this.buyer.getState();
    }

    /* Local methods */

    @Override
    public void onFailure( Throwable cause ) {
        this.log.entering( this.getClass().getSimpleName(), "onFailure" );
        log.log( Level.SEVERE, cause.getMessage(), cause );
        RuntimeException e = new RuntimeException( cause );
        this.log.exiting( this.getClass().getSimpleName(), "onFailure" );
        throw e;
    }

    @Override
    public void onResponse( CandlestickEvent candlestick )  {
        try {
            this.log.entering(this.getClass().getSimpleName(), "onResponse");
            this.log.info("Received candlestick response");
            Bar nextBar = candlestickEventToBar( candlestick );
            this.log.info("Adding bar to timeseries: " + candlestick);
            this.addBarToTimeSeries( nextBar );
            this.buyer.update( nextBar );
            this.buyer.findAndSetState( );
            if ( this.shouldClose( nextBar ))
                this.close( nextBar );
            else if ( this.shouldOpen( ) )
                this.open( nextBar );
            else
                this.log.info("No action taken");
            this.buyer.findAndSetState( );
            this.logs( nextBar );
        }

        catch (Throwable e) {
            this.onFailure(e);
        }

        finally {
            this.log.info("End of candlestick response");
            this.log.exiting(this.getClass().getSimpleName(), "onResponse");
        }
    }

    private boolean shouldOpen( ) {
        log.entering( this.getClass().getSimpleName(), "shouldOpen");;
        boolean ta = false;
        boolean shouldOpen = this.buyer.shouldOpen();
        if ( shouldOpen ) {
            boolean cold = coldEnough();
            this.log.info("Cold: " + cold);
            if ( cold ) {
                boolean sufficient = sufficientBars();
                this.log.info( String.format("Sufficient: %s", sufficient) );
                if ( sufficient ) {
                    if ( FORCE_ORDER )
                        ta = true;
                    else
                        ta = this.taBot.isSatisfied(this.timeSeries);
                    this.log.info("TA: " + ta );
                }
            }
        }
        log.exiting(this.getClass().getSimpleName(), "shouldOpen");
        return ta;
    }

    private void logTa() {
        String next = this.taBot.getNext();
        int l = next.trim().length();
        int n = this.taBot.getnFields();

        if ( l > 1 )
            next = "," + next.substring( 0, l - 1 );
        else if ( n > 0 )
            next = new String(new char[n]).replace("\0", ",");

        this.tsWriter.append( next ).append("\n").flush();
    }

    private boolean shouldClose( Bar lastBar ) {
        this.log.entering( this.getClass().getSimpleName(), "shouldClose");
        PositionState state = this.buyer.getState();
        PositionState.Phase stateType = state.getPhase();
        this.log.info( String.format("State: %s", this.getState()));

        if (  stateType == PositionState.Phase.HOLD ) {
            boolean buyerClear = buyer.shouldClose(lastBar);
            this.log.exiting(this.getClass().getSimpleName(), "shouldClose");
            return buyerClear;
        }

        else {
            this.log.exiting(this.getClass().getSimpleName(), "shouldClose");
            return false;
        }

    }

    private void close( Bar lastBar ) {
        this.log.entering( this.getClass().getSimpleName(), "close");
        this.log.info("Preparing to close order" );
        this.buyer.close( lastBar );
        log.exiting( this.getClass().getSimpleName(), "close");
    }

    private void open( Bar lastBar ) throws STBException {
        this.log.entering( this.getClass().getSimpleName(), "open");
        BigDecimal cp = new BigDecimal( lastBar.getClosePrice().toString() );
        String closeStr = Static.safeDecimal( cp, 20 );
        BigDecimal close = new BigDecimal( closeStr );
        this.log.info("Opening new order at " + closeStr );
        if (this.buyer.open( close ))
            restartCoolOff();
        log.exiting(LiveController.class.getSimpleName(), "open");
    }

    private void addBarToTimeSeries( Bar bar ) {
        log.entering(this.getClass().getName(), "addBarToTimeSeries");
        this.timeSeries.addBar( bar );
        log.exiting(this.getClass().getName(), "addBarToTimeSeries");
    }

    private void logs(Bar bar ) {
        this.log.entering( this.getClass().getName(), "logTs");
        if ( LOG_TS_AT != -1 && this.timeSeries.getBarCount() >= LOG_TS_AT ) {
            ZonedDateTime dateTime = bar.getBeginTime();
            String readableDateTime = dateTime.toLocalTime().format( Static.timeFormatter );
            BigDecimal close = (BigDecimal) bar.getClosePrice().getDelegate();
            PositionState state = getState();
            BigDecimal stopLoss = this.buyer.getTrailingStop().getStopLoss();

            this.tsWriter
                    .append(readableDateTime).append(",")
                    .append(close.toPlainString()).append(",")
                    .append(stopLoss.toPlainString()).append(",")
                    .append(state.toShortString())
                    .flush();
            this.logTa();
        }
        else
            this.log.info("Skipping TS logging");
        this.log.exiting(this.getClass().getName(), "logTs");
    }

    private Bar candlestickEventToBar( CandlestickEvent candlestickEvent )  {
        log.entering( this.getClass().getSimpleName(), "candlestickEventToBar" );
        Instant instant = Instant.ofEpochMilli(candlestickEvent.getCloseTime());
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, Config.ZONE_ID);
        String open = candlestickEvent.getOpen();
        String high = candlestickEvent.getHigh();
        String low = candlestickEvent.getLow();
        String close = candlestickEvent.getClose();
        String volume = candlestickEvent.getVolume();
        log.exiting( this.getClass().getSimpleName(), "candlestickEventToBar" );
        return new BaseBar(zonedDateTime, open, high, low, close, volume, PrecisionNum::valueOf );
    }

    private boolean sufficientBars() {
        return this.timeSeries.getBarCount() >= START_AT;
    }


    private boolean coldEnough( ) {
        this.coolOff = (this.coolOff > 0) ? this.coolOff - 1 : this.coolOff;
        return this.coolOff == 0;
    }


    private void restartCoolOff() {
        this.coolOff = Config.COOL_DOWN;
    }

    private File initBaseDir() throws STBException {
        File dir = new File(Static.OUT_DIR + this.symbol);
        if (!dir.exists() && !dir.mkdirs())
            throw new STBException( 60 );
        return dir;
    }

    private void initTsWriter( File baseDir ) throws IOException {
        this.tsWriter = new PrintWriter( baseDir + "/ts.csv" );
        String taHeader = this.taBot.getNext();
        String myHeader = "TIME,CLOSE,STOP,POS,";
        int n = myHeader.length();
        int l = taHeader.length();
        if ( l > 1 )
            taHeader = taHeader.substring(0, l - 1);
        else
            myHeader = myHeader.substring( 0, n - 1 );
        String header = myHeader + taHeader;
        this.tsWriter.append( header ).append("\n").flush();
    }

    private void initLogger( ) {
        this.log = Logger.getLogger("root." + this.symbol );
        log.setLevel( Level.ALL );
        log.setUseParentHandlers( true );
    }
}