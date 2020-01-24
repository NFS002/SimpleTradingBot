package SimpleTradingBot.Controller;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.test.TestLevel;
import SimpleTradingBot.test.FakeOrderResponses;
import org.ta4j.core.num.*;
import SimpleTradingBot.Models.PositionState;
import SimpleTradingBot.Util.Handler;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import org.ta4j.core.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.*;


public class Controller implements BinanceApiCallback<CandlestickEvent> {

    private TickerStatistics summary;

    private TAbot taBot;

    private boolean paused;

    private Handler handler;

    private TimeSeries timeSeries;

    private Closeable closeable;

    private Trader buyer;

    private TimeKeeper timeKeeper;

    private Logger log;

    private PrintWriter tsWriter;

    private int coolOff;

    private String baseSymbol;

    private String assetSymbol;

    private int nWssErr;

    /* Constructor */

    public Controller( TickerStatistics summary )
            throws BinanceApiException, IOException, STBException {

        String symbol = summary.getSymbol();
        BaseTimeSeries.SeriesBuilder builder = new BaseTimeSeries.SeriesBuilder();
        builder.withNumTypeOf( PrecisionNum::valueOf )
                .withMaxBarCount( MAX_BAR_COUNT )
                .withName( symbol );

        this.timeSeries = builder.build();
        this.summary = summary;
        this.buyer = new Trader( this );
        this.handler = new Handler( symbol );
        this.timeKeeper = new TimeKeeper( summary );
        this.taBot = new TAbot( summary );
        this.coolOff = 0;
        this.nWssErr = 0;
        this.paused = false;

        File baseDir = initBaseDir();

        if ( LOG_TS_AT != -1 )
            initTsWriter( baseDir );

        initLogger( );

        if ( INIT_TS )
            initSeries();

        initSymbol();

        if ( TEST_LEVEL == TestLevel.FAKEORDER )
            FakeOrderResponses.register( this.summary.getSymbol() );

    }

    /* Getters & Setters */

    public Trader getBuyer() {
        return buyer;
    }

    public TimeKeeper getTimeKeeper() {
        return timeKeeper;
    }

    public PositionState getState() {
        return this.buyer.getState();
    }

    public boolean isPaused() {
        return this.paused;
    }

    public void pause() {
        this.paused = true;
    }

    public TimeSeries getTimeSeries() {
        return this.timeSeries;
    }

    public TickerStatistics getSummary() {
        return summary;
    }

    /* Local methods */

    @Override
    public void onFailure( Throwable cause ) {
        this.log.entering( this.getClass().getSimpleName(), "onFailure" );
        log.log( Level.SEVERE, cause.getMessage(), cause );
        if ( cause instanceof STBException ) {
            STBException exception = ( STBException ) cause;
            switch ( exception.getStatusCode() ) {

                case 60:

                /* Ignore */ this.log.info( "Error ignored");

                break;

                case 120:   //"MAX_ERR";

                case 130:   //"MAX_TIME_SYNC";

                case 140:   //"INCORRECT_INTERVAL";

                case 150:   //"MAX_DDIFF";

                case 160:   //"RECV_WINDOW";

                try {
                    resetAll();
                }

                catch ( Exception e ) {
                    log.log( Level.SEVERE, " Error performing reset ", e);
                    exit();
                }

                break;

                case 70:    //"MAX_ORDER_RETRY";

                case 90:    //"MAX_ORDER_UPDATES";

                case 100:   //"NO_ORDER_UPDATES";

                case 110:   //"SERVER_TIME_DIFFERENCE";

                case 200:   //"UNKNOWN_STATE";

                case 210:   //"UNKOWN_STATUS";

                default:

                exit();

                break;

            }
        }

        else if ( cause.getMessage() != null && cause.getMessage().startsWith("sent ping but didn't receive pong") ) {
            long now = System.currentTimeMillis();
            this.timeKeeper.endStream( now );
            long time = this.timeKeeper.getStreamTime();
            this.nWssErr = ( time < 600 ) ? this.nWssErr + 1 : 0;
            this.log.warning( String.format("Wss err after time %d(s) (%d/%d)", time, this.nWssErr, Config.MAX_WSS_ERR ));
            if ( this.nWssErr < Config.MAX_WSS_ERR ) {
                /* Wait a minute then reset */
                try {
                    closeWss( );
                    Thread.sleep(60000 * 5);
                    liveStream();
                }

                catch (Exception ex) {
                    log.log(Level.SEVERE, "Error performing reset ", ex);
                    exit();
                }
            }

            else
                exit();
        }

        else
            exit();

        this.log.exiting( this.getClass().getSimpleName(), "onFailure" );
    }

    private void request_deregister() {
        this.log.entering( this.getClass( ).getSimpleName( ), "request_deregister");
        this.log.severe( "Requesting deregister from HB" );
        if ( !Static.requestDeregister( this.summary.getSymbol()) )
            this.log.severe( "Deregister request failed");
        this.log.exiting( this.getClass().getSimpleName( ), "request_deregister");
    }

    private boolean shouldExit() {
        this.log.entering( this.getClass().getSimpleName(), "checkExitQueue");
        this.log.info( "Checking exit queue");
        Optional<QueueMessage> exitMessage = Static.checkForExit( this.summary.getSymbol() );
        if ( exitMessage.isPresent() ) {
            this.log.severe( "Received shutdown message" );
            return true;
        }
        else if ( EXIT_AFTER != -1 )
            return this.buyer.getCycles().size() > EXIT_AFTER;
        this.log.exiting( this.getClass().getSimpleName(), "checkExitQueue");
        return false;
    }

    private void closeWss( ) throws Exception {
        this.log.entering( this.getClass().getSimpleName(), "closeWss");
        this.log.warning( "Closing wss..." );
        this.closeable.close();
        this.pause();

        if ( this.tsWriter != null )
            this.tsWriter.append( "\n" ).flush();

        this.log.exiting( this.getClass().getSimpleName(), "closeWss");
    }

    private void resetAll() throws Exception  {
        this.log.entering( this.getClass().getSimpleName(), "resetAll");
        log.warning( "Performing reset..." );
        String symbol = summary.getSymbol();
        BaseTimeSeries.SeriesBuilder builder = new BaseTimeSeries.SeriesBuilder();
        builder.withNumTypeOf( PrecisionNum::valueOf )
                .withMaxBarCount( MAX_BAR_COUNT )
                .withName( symbol );
        this.timeSeries = builder.build();
        this.buyer = new Trader( this );
        long now = System.currentTimeMillis();
        this.timeKeeper.endStream( now );
        long time = this.timeKeeper.getStreamTime();
        closeable.close();

        String resetMessage = "\n**********RESET (" + time + ")s **********\n";

        if ( this.tsWriter != null )
            this.tsWriter.append( resetMessage ).flush();


        if ( INIT_TS )
            initSeries();

        liveStream();

        this.log.exiting( this.getClass().getSimpleName(), "resetAll");
    }

    public void exit( ) {
        this.log.entering( this.getClass().getSimpleName(), "exit");
        this.log.severe( "Preparing to exit controller and interrupt thread. ");

        request_deregister();

        try {
            closeable.close();
        }
        catch ( IOException e ) {
            log.log( Level.SEVERE, "Error closing WSS socket", e);
        }

        this.tsWriter.close();
        log.severe( "Removing filter constraint");

        Static.removeConstraint( this.summary.getSymbol() );

        StringBuilder msg = new StringBuilder();
        long currentTime = System.currentTimeMillis();
        this.timeKeeper.endStream( currentTime );
        long duration = this.timeKeeper.getStreamTime();
        msg.append("Time Elapsed since open: ").append( duration ).append("s");
        log.severe( msg.toString() );
        this.log.exiting( this.getClass().getSimpleName(), "exit");
    }

    @Override
    public void onResponse( CandlestickEvent candlestick )  {
        try {
            this.log.entering(this.getClass().getSimpleName(), "onResponse");
            this.log.fine("Received candlestick response");
            Bar lastBar = this.timeSeries.getLastBar();
            if ( this.timeKeeper.checkEvent( lastBar, candlestick )) {

                this.log.info( "Received candlestick response in time");
                Bar nextBar = candlestickEventToBar( candlestick );

                if ( shouldExit() )
                    exit();

                else {
                    this.log.info("Adding bar to timeseries: " + candlestick);

                    /* Add bar */
                    this.addBarToTimeSeries( nextBar );

                    /* Fetch update */
                    this.buyer.update( nextBar );

                    /* Update local state */
                    this.buyer.findAndSetState( );

                    /* Do stuff */
                    if ( this.shouldClose( nextBar ))
                        this.close( nextBar );

                    else if ( this.shouldOpen( ) )
                        this.open( nextBar );

                    else
                        this.log.info("No action taken");

                    /* Update local state */
                    this.buyer.findAndSetState( );

                    /* Log */
                    this.logs( nextBar );
                }
            }
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
        log.exiting(Controller.class.getSimpleName(), "open");
    }

    private void addBarToTimeSeries( Bar bar ) {
        log.entering(this.getClass().getName(), "addBarToTimeSeries");
        this.timeSeries.addBar( bar );
        if ( this.paused )
            this.paused = false;
        log.exiting(this.getClass().getName(), "addBarToTimeSeries");
    }

    public void liveStream() throws BinanceApiException {
        this.log.entering( this.getClass().getSimpleName(), "liveStream" );
        log.info("Attempting data stream....");
        long currentTime = System.currentTimeMillis();
        String dateTime = Static.toReadableTime( currentTime );
        this.timeKeeper.startStream( currentTime );
        BinanceApiWebSocketClient webSocketClient = Static.getFactory().newWebSocketClient();
        this.closeable = webSocketClient.onCandlestickEvent( this.summary.getSymbol().toLowerCase(),Config.CANDLESTICK_INTERVAL, this);
        log.info("Connected to WSS data stream at " + dateTime + ", " + summary.getSymbol());
        this.log.exiting( this.getClass().getSimpleName(), "liveStream" );
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

    private Bar candlestickToBar( Candlestick candlestick )  {
        log.entering( this.getClass().getSimpleName(), "candlestickToBar" );
        Instant instant = Instant.ofEpochMilli(candlestick.getCloseTime());
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, Config.ZONE_ID);
        String open = candlestick.getOpen();
        String high = candlestick.getHigh();
        String low = candlestick.getLow();
        String close = candlestick.getClose();
        String volume = candlestick.getVolume();
        log.exiting( this.getClass().getSimpleName(), "candlestickToBar" );
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

    private void initSeries() {
        log.entering( this.getClass().getSimpleName(), "initSeries");
        BinanceApiRestClient client = Static.getFactory().newRestClient();
        List<Candlestick> candlesticks = client.getCandlestickBars(summary.getSymbol(), Config.CANDLESTICK_INTERVAL);
        this.log.info("Initialising timeseries with " + candlesticks.size() + " bars");
        for (Candlestick candletick:candlesticks) {
            Bar bar = candlestickToBar( candletick );
            addBarToTimeSeries( bar  );
            if ( sufficientBars() )
                this.taBot.isSatisfied(this.timeSeries);
            this.logs( bar );
        }

        log.exiting( this.getClass().getSimpleName(), "initSeries");
    }

    private void initSymbol() {
        this.assetSymbol = this.buyer.constraints.getBASE_ASSET();
        this.baseSymbol = this.buyer.constraints.getQUOTE_ASSET();
    }

    private File initBaseDir() throws STBException {
        File dir = new File(Static.OUT_DIR + summary.getSymbol());
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
        this.log = Logger.getLogger("root." + this.summary.getSymbol() );
        log.setLevel( Level.ALL );
        log.setUseParentHandlers( true );
    }

}