package SimpleTradingBot.Services;


import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.Controller;
import SimpleTradingBot.Controller.TimeKeeper;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.*;
import SimpleTradingBot.Util.Handler;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import org.ta4j.core.TimeSeries;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.*;

public class HeartBeat {

    private PrintWriter rtWriter;

    private Logger log;

    /* Singleton pattern */
    private static HeartBeat instance;

    private final OrderHistory orderHistory;

    private int nErr;

    private ArrayList<Controller> controllers;

    private Handler handler;

    private HeartBeat() {
        this.controllers = new ArrayList<>();
        this.orderHistory = new OrderHistory();
        this.handler = new SimpleTradingBot.Util.Handler( "am");
        this.log = Logger.getLogger( "root.am" );
        this.log.setUseParentHandlers( true );
        try {
            this.rtWriter = new PrintWriter(OUT_DIR + "rt.txt");
        }
        catch ( IOException e ) {
            this.log.log(Level.SEVERE, "Cant create necessary rt files. Skipping rt logging", e );
        }
    }

    public static HeartBeat getInstance() {
        if (instance == null)
            instance = new HeartBeat();
        return instance;
    }

    public boolean containsOrder( String symbol ) {

        if ( this.orderHistory == null )
            return false;

        ArrayList<RoundTrip> roundTrips = this.orderHistory.get( symbol );

        if ( roundTrips == null || roundTrips.isEmpty()  ) {
            return false;
        }

        else {
            long now = System.currentTimeMillis();
            RoundTrip trip = roundTrips.get(0);
            long completedAt = trip.getCompletedAt();
            boolean interrupted = trip.getInterruptedAt() > 0;
            if ( interrupted )
                return false;
            else if ( completedAt < 0 )
                return true;
            long diff = now - completedAt;
            int diffMin = Math.round( diff / 1000 );
            return diffMin <= Config.COOL_DOWN;
        }
    }

    public RoundTrip getLastOrder( String symbol ) throws STBException {
        this.log.entering( this.getClass().getSimpleName(), "getLastOrder");
        ArrayList<RoundTrip> roundTrips = this.orderHistory.get( symbol );
        RoundTrip lastRt;

        if ( roundTrips == null || roundTrips.size() < 1 ) {
            this.log.warning( String.format( "No round trips for symbol %s were found", symbol ));
            throw new STBException( 210 );
        }
        else {
            lastRt = roundTrips.get( 0 );
        }

        this.log.exiting( this.getClass().getSimpleName(), "getLastOrder");
        return lastRt;
    }

    public void putBack( String symbol, RoundTrip roundTrip  ) throws STBException {
        this.log.entering( this.getClass().getSimpleName(), "putBack");
        ArrayList<RoundTrip> roundTrips = this.orderHistory.get( symbol );
        if ( roundTrips == null )
            throw new STBException( 210 );
        roundTrips.set( 0, roundTrip );
        this.log.exiting( this.getClass().getSimpleName(), "putBack");
    }


    private void shutdown() {
        this.log.entering( this.getClass().getName(), "shutdown" );
        Thread thread = Thread.currentThread();
        if ( !thread.isInterrupted() )
            thread.interrupt();
        System.exit(0);
        this.log.exiting( this.getClass().getName(), "shutdown" );
    }

    public void registerNewOrder(String symbol, RoundTrip roundTrip ) {
        this.log.entering( this.getClass().getSimpleName(), "registerNewOrder");
        ArrayList<RoundTrip> roundTrips = this.orderHistory.get(symbol);
        if (roundTrips == null)
            roundTrips = new ArrayList<>();
        roundTrips.add( 0, roundTrip );
        this.orderHistory.put( symbol, roundTrips );
        this.log.info( "Registered new rt for updates: " + symbol );
        this.log.entering( this.getClass().getSimpleName(), "registerNewOrder");
    }

    public void maintenance() {
        this.log.entering( this.getClass().getSimpleName(), "maintenance" );

        Thread thread = Thread.currentThread();
        thread.setUncaughtExceptionHandler( handler );

        try {

            QueueMessage exitMessage = Static.getExitQueue().poll( 5, TimeUnit.SECONDS );

            if ( exitMessage != null
            && exitMessage.getType() == QueueMessage.Type.INTERRUPT
            && exitMessage.getSymbol().equals("*")) {
                this.log.severe( "Received shutdown message");
                shutdown();
            }


            /* If there no more registered controllers, close the thread */
            if ( Static.constraints.isEmpty()) {
                this.log.info("Preparing maintenance");
                maintenance_internal();
            }


            this.log.info("Exiting maintenance" );

        }

        catch ( Throwable e ) {

            this.log.log(Level.SEVERE, e.getMessage(), e);
            this.log.severe("Sending shutdown message");
            Static.requestExit("*");
            shutdown();
        }

        finally {
            this.log.exiting( this.getClass().getSimpleName(), "maintenance" );
        }
    }

    private void maintenance_internal()  throws STBException {
        this.log.entering( this.getClass().getSimpleName(), "maintenance_internal");

        for ( Map.Entry<String, ArrayList<RoundTrip>> entry : this.orderHistory.entrySet() ) {

            String symbol = entry.getKey();

            log.fine( "Maintaining order history for symbol: " + symbol);
            ArrayList<RoundTrip> roundTrips = entry.getValue();

            if ( roundTrips == null || roundTrips.isEmpty() ) {
                log.warning( "No order history for symbol: " + symbol);
                continue;
            }

            RoundTrip firstRt = roundTrips.get( 0 );
            Controller controller = firstRt.getController();
            long now = System.currentTimeMillis();
            long completedAt = firstRt.getCompletedAt();
            boolean interrupted = firstRt.getInterruptedAt() > 0;
            long diff = now - completedAt;
            int diffMin = Math.round( diff/ 1000 );

            if ( interrupted ) {
                log.warning( String.format( "Order was interrupted (%s), skipping update", symbol) );
            }

            else if ( completedAt <= 0 ) {


                Phase phase = firstRt.getPhase();

                this.log.fine("Updating " + phase + " for symbol: " + symbol);

                if ( phase.isBuyOrHold() ) {

                    boolean alive = checkHearbeat( controller );

                    if ( alive ) {

                        if ( phase == Phase.BUY) {
                            Position buyPosition = firstRt.getBuyPosition();
                            update( firstRt, buyPosition, symbol );
                        }
                        else
                            this.log.info( String.format( "Order (%s) not in a working phase (%s), skipping update", symbol, phase) );
                    }

                    else  {
                        log.severe( "Hearbeat failed for " +  symbol + ", order now interrupted.");
                        controller.exit( false );
                        long currentTime = System.currentTimeMillis();
                        firstRt.setInterruptedAt( currentTime );
                    }
                }

                else if ( phase == Phase.SELL ) {
                    Position sellPosition = firstRt.getSellPosition();
                    update( firstRt, sellPosition, symbol );

                    if ( firstRt.getPhase() == Phase.HOLD ) {
                        String stats = firstRt.getStats();
                        appendRt( symbol, stats );
                    }
                }
            }

            else if ( diffMin < Config.COOL_DOWN ) {
                log.fine( symbol + " is cooling down, skipping update");
            }

            else {
                log.fine( "Order history (" + symbol + ") is complete, skipping update");
            }

        }

        log.exiting( this.getClass().getSimpleName(), "maintenance_internal");
    }

    private boolean checkHearbeat( Controller controller ) {
        boolean inTime = true;
        this.log.entering( this.getClass().getSimpleName(), "checkHeartbeat");
        String symbol = controller.getOrderRequest().getSymbol();
        this.log.info( "Checking heartbeat for symbol: " + symbol );
        TimeSeries series = controller.getTimeSeries();
        ZonedDateTime endTime = series.getLastBar().getEndTime();
        ZonedDateTime now = ZonedDateTime.now( Config.ZONE_ID );
        long duration = endTime.until( now, ChronoUnit.MILLIS );
        long intervalToMillis = TimeKeeper.intervalToMillis( Config.CANDLESTICK_INTERVAL );
        if ( duration > ( intervalToMillis + Config.INTERVAL_TOLERANCE ) ) {
            this.log.severe( "Hearbeat failed for symbol: " + symbol + ", with idle duration of " + duration + "(s). Forcing exit of controller");
            inTime = false;
        }
        else  {
            log.info( "Hearbeat passed for symbol: " + symbol );
        }
        this.log.exiting( this.getClass().getSimpleName(), "checkHeartbeat");
        return inTime;
    }


    private void update(RoundTrip roundTrip, Position position, String symbol )  {
        log.entering( this.getClass().getSimpleName(), "update");

        int nUpdates = position.getnUpdate();
        OrderStatus lastStatus = OrderStatus.NEW;
        long orderId = position.getOriginalOrderResponse().getOrderId();
        log.info( String.format( "Order updates (%d/%d)", nUpdates, MAX_ORDER_UPDATES) );
        Phase phase = roundTrip.getPhase();
        Phase nextPhase = phase.next();

        if ( nUpdates > 0 ) {

            Order lastUpdate = position.getLastUpdate();
            lastStatus = lastUpdate.getStatus();
        }



        log.info( String.format("Last status for symbol %s: %s, phase: %s", symbol, lastStatus, phase));

        switch ( lastStatus ) {
            case CANCELED:
            case REJECTED:
            case EXPIRED:
            case FILLED:
            default:
            this.log.info( "Updating " + phase + " to " + nextPhase );
            roundTrip.setPhase( nextPhase );
            break;

            case NEW:
            case PENDING_CANCEL:
            case PARTIALLY_FILLED:
            try {

                if ( nUpdates < MAX_ORDER_UPDATES ) {
                    Order nextOrderUpdate = update_internal( symbol, orderId );
                    position.setUpdatedOrder( nextOrderUpdate );
                    log.info( "Successfully fetched update: " + nextOrderUpdate.getStatus() );

                    if (nUpdates + 1 == MAX_ORDER_UPDATES) {
                        this.log.info( "Maximum updates, moving phase to " + nextPhase );
                        roundTrip.setPhase( nextPhase );
                    }
                }

                else {
                    this.log.info( "Maximum updates, moving phase to " + nextPhase );
                    roundTrip.setPhase( nextPhase );
                }

            }

            catch ( STBException | BinanceApiException e ) {
                log.log( Level.WARNING, String.format( "Error getting update (%d/%d) for symbol: %s", nErr, MAX_ORDER_RETRY, symbol), e);
                if ( ++nErr >= MAX_QUEUE_RETRY ) {
                    this.log.severe( "Maximum error, moving phase to " + nextPhase );
                    roundTrip.setPhase( nextPhase );
                    this.nErr = 0;
                }
            }

        }

        log.exiting( this.getClass().getSimpleName(), "update");

    }

    private Order update_internal( String symbol, long orderId ) {
        BinanceApiRestClient client = Static.getFactory().newRestClient();
        OrderStatusRequest statusRequest = new OrderStatusRequest( symbol, orderId);
        return client.getOrderStatus( statusRequest );
    }

    private boolean sellAfterBuy(Position buyPosition, Position sellPosition) {

        NewOrder originalBuyOrder = buyPosition.getOriginalOrder();
        NewOrder originalSellOrder = sellPosition.getOriginalOrder();
        double buyTimestamp = originalBuyOrder.getTimestamp();
        double sellTimestamp = originalSellOrder.getTimestamp();
        return sellTimestamp > buyTimestamp;
    }

    private void appendRt( String symbol, String rt ) {
        this.log.entering( this.getClass().getSimpleName(), "appendRt");
        if ( rtWriter != null ) {
            this.log.info( "Logging rt for symbol: " + symbol);
            this.rtWriter.append(symbol).append(":\n");
            this.rtWriter.append(rt).append("\n");
            this.rtWriter.flush();
        }
        else
            this.log.warning( "Writer was not initialised, skipping rt ");

        this.log.exiting( this.getClass().getSimpleName(), "appendRt");
    }
}
