package SimpleTradingBot.Controller;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.Phase;
import SimpleTradingBot.Models.Position;
import SimpleTradingBot.Models.RoundTrip;
import SimpleTradingBot.Util.Handler;
import SimpleTradingBot.Util.OrderRequest;
import org.ta4j.core.num.*;
import SimpleTradingBot.Services.HeartBeat;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.exception.BinanceApiException;
import org.ta4j.core.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.*;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.XMLFormatter;
import java.util.stream.Stream;

import static SimpleTradingBot.Config.Config.*;


public class Controller implements BinanceApiCallback<CandlestickEvent> {

    private OrderRequest orderRequest;

    private TimeSeries timeSeries;

    private Closeable closeable;

    private Trader buyer;

    private TimeKeeper timeKeeper;

    private Logger log;

    private PrintWriter tsWriter;

    private String baseSymbol;

    private String assetSymbol;

    /* Constructor */

    public Controller( OrderRequest orderRequest )
            throws BinanceApiException, IOException, STBException {

        String symbol = orderRequest.getSymbol();
        BaseTimeSeries.SeriesBuilder builder = new BaseTimeSeries.SeriesBuilder();
        builder.withNumTypeOf( PrecisionNum::valueOf )
                .withMaxBarCount( MAX_BAR_COUNT )
                .withName( symbol );


        this.orderRequest = orderRequest;

        this.timeSeries = builder.build();

        this.buyer = new Trader( orderRequest );

        this.timeKeeper = new TimeKeeper( symbol );

        File baseDir = initBaseDir();

        if ( SHOULD_LOG_TS )
            initTsWriter( baseDir );


        initLogger( baseDir );

        initSymbol();

    }

    /* Getters & Setters */

    public Trader getBuyer() {
        return buyer;
    }

    public OrderRequest getOrderRequest() {
        return orderRequest;
    }

    public String getBase() {
        return baseSymbol;
    }

    public TimeSeries getTimeSeries() {
        return this.timeSeries;
    }

    public String getAsset() {
        return assetSymbol;
    }

    public Closeable getCloseable() {
        return closeable;
    }


    /* Local methods */

    @Override
    public void onFailure( Throwable cause ) {
        this.log.entering( this.getClass().getSimpleName(), "onFailure" );
        this.log.log( Level.SEVERE, cause.getMessage(), cause );
        this.log.severe( "Exiting controller");
        exit();
        this.log.exiting( this.getClass().getSimpleName(), "onFailure" );
    }


    private boolean checkExitQueue() {
        this.log.entering( this.getClass().getSimpleName(), "checkExitQueue");
        this.log.info( "Checking exit queue");
        Stream<QueueMessage> messageStream = Static.getExitQueue().stream();
        Optional<QueueMessage> exitMessage = messageStream.filter(m -> m.getType() == QueueMessage.Type.INTERRUPT && m.getSymbol().equals( this.orderRequest.getSymbol()) ).findFirst();
        boolean foundExitMessage = exitMessage.isPresent();
        this.log.exiting( this.getClass().getSimpleName(), "checkExitQueue");
        return foundExitMessage;
    }


    public void exit(  ) {
       this.exit( true );
    }

    public void exit( boolean interrupt ) {
        this.log.entering( this.getClass().getSimpleName(), "exit");
        this.log.warning( "Preparing to exit controller and interrupt thread. ");
        try {
            closeable.close();
        }
        catch ( IOException e ) {
            log.log( Level.SEVERE, "Error closing WSS socket", e);
        }

        this.tsWriter.close();


        StringBuilder msg = new StringBuilder();
        long currentTime = System.currentTimeMillis();
        this.timeKeeper.endStream( currentTime );
        long duration = this.timeKeeper.getStreamTime();
        msg.append("Time Elapsed since open: ").append( duration ).append("s");
        log.warning( msg.toString() );
        this.log.exiting( this.getClass().getSimpleName(), "exit");
        if ( interrupt && !Thread.currentThread().isInterrupted() )
            Thread.currentThread().interrupt();
    }

    @Override
    public void onResponse( CandlestickEvent candlestick )  {
        try {
            this.log.entering(this.getClass().getSimpleName(), "onResponse");
            log.fine("Received candlestick response" );
            Bar lastBar = timeSeries.getLastBar();
            Thread thread = Thread.currentThread();
            String symbol = this.orderRequest.getSymbol();
            thread.setName( "thread." + symbol );
            thread.setUncaughtExceptionHandler( new Handler( symbol ));
            if ( timeKeeper.checkEvent( lastBar, candlestick )) {
                log.info("Received candlestick response in time");

                if (checkExitQueue()) {
                    this.log.severe("Received shutdown message");
                    exit(true);
                }

                else {

                    Bar nextBar = candlestickEventToBar(candlestick);
                    log.info("Adding bar to timeseries: " + candlestick );
                    addBarToTimeSeries(nextBar);

                    this.buyer.updateStopLoss( nextBar );

                    if ( shouldClose(nextBar) ) {

                        try {

                            HeartBeat heartBeat = HeartBeat.getInstance();

                            RoundTrip lastOrder = heartBeat.getLastOrder( symbol );

                            this.cancelIf( symbol, lastOrder );

                            this.close( symbol, lastOrder, nextBar);

                            heartBeat.putBack( symbol, lastOrder );


                        }

                        catch ( STBException e ) {
                            this.log.log( Level.SEVERE, "Error closing order: " + symbol + " was not closed", e);
                        }

                        finally {
                            exit( true );
                        }

                    }

                    else {
                        log.info("No action taken");
                    }
                }
            }
        }

        catch (Throwable e) {
            onFailure(e);
        }

        finally {
            log.exiting(this.getClass().getSimpleName(), "onResponse");
        }
    }


    private boolean shouldClose( Bar lastBar ) {
        log.entering( this.getClass().getSimpleName(), "shouldClose");
        boolean breached = this.buyer.shouldClose( lastBar );
        log.exiting(this.getClass().getSimpleName(), "shouldClose");
        return breached;
    }

    private void close( String symbol, RoundTrip lastOrder, Bar lastBar ) throws STBException {
        log.entering( this.getClass().getSimpleName(), "close");
        this.log.info("Preparing to close order on symbol: " + symbol );
        Position sellPosition = this.buyer.close( symbol, lastOrder, lastBar );
        lastOrder.setSellPosition( sellPosition );
        lastOrder.setPhase( Phase.SELL );
        log.exiting( this.getClass().getSimpleName(), "close");
    }

    private void cancelIf(String symbol, RoundTrip lastOrder ) {

        Phase state = lastOrder.getPhase();
        String message = String.format("Symbol %s is in a %s state whilst closing order", symbol, state);

        if (state.isWorking()) {
            this.log.warning(message);
            Position buyPosition = lastOrder.getBuyPosition();
            long orderId = buyPosition.getOriginalOrderResponse().getOrderId();
            this.buyer.cancel( orderId );
        }

        else
            this.log.info( message );

    }

    private void addBarToTimeSeries( Bar bar ) {
        log.entering(this.getClass().getName(), "addBarToTimeSeries");
        if ( SHOULD_LOG_INIT_TS || SHOULD_LOG_TS && this.timeSeries.getBarCount() >= INIT_BARS )
            logTS( bar );
        this.timeSeries.addBar( bar );
        log.exiting(this.getClass().getName(), "addBarToTimeSeries");
    }

    public RoundTrip execute() throws STBException {
        this.log.entering( this.getClass().getSimpleName(), "execute");
        String symbol = this.orderRequest.getSymbol();
        this.log.info( "Preparing to execute order request: " + this.orderRequest);
        long currentTime = System.currentTimeMillis();
        String dateTime = Static.toReadableDate( currentTime );
        BinanceApiWebSocketClient webSocketClient = Static.getFactory().newWebSocketClient();
        RoundTrip roundTrip = this.buyer.open( );
        roundTrip.setController( this );
        this.timeKeeper.startStream( currentTime );
        this.closeable = webSocketClient.onCandlestickEvent( symbol, Config.CANDLESTICK_INTERVAL, this);
        log.info("Connected to WSS data stream at " + dateTime + ", " + symbol);
        this.log.exiting( this.getClass().getSimpleName(), "execute");
        return roundTrip;
    }

    private void logTS( Bar bar ) {
        ZonedDateTime dateTime = bar.getBeginTime();
        String readableDateTime = dateTime.toLocalTime().format( Static.timeFormatter );
        Num close = bar.getClosePrice();
        this.tsWriter.append(readableDateTime).append("\t\t\t\t").append(close.toString())
                .append("\t\t\t\n").flush();
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


    private void initSymbol() {
        this.assetSymbol = this.buyer.constraints.getBASE_ASSET();
        this.baseSymbol = this.buyer.constraints.getQUOTE_ASSET();
    }

    private File initBaseDir() throws STBException {
        File dir = new File(Config.OUT_DIR + orderRequest.getSymbol());
        if (!dir.exists() && !dir.mkdirs())
            throw new STBException( 60 );
        return dir;
    }

    private void initTsWriter( File baseDir ) throws IOException {
        this.tsWriter = new PrintWriter( baseDir + "/ts.txt" );
        this.tsWriter.append("TIME\t\t\t\tCLOSE\t\t\t\tPOS\t\t\t\t\n").flush();
    }

    private void initLogger( File baseDir ) throws IOException {
        this.log = Logger.getLogger("root." + orderRequest.getSymbol());
        FileHandler fileHandler = new FileHandler(baseDir + "/debug.log");
        XMLFormatter formatter = new XMLFormatter();
        fileHandler.setFormatter( formatter );
        this.log.addHandler(fileHandler);
    }

}