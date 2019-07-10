package SimpleTradingBot.Services;


import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.Controller;
import SimpleTradingBot.Controller.TimeKeeper;
import SimpleTradingBot.Controller.Trader;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.*;
import SimpleTradingBot.Util.Handler;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.TimeSeries;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.OUT_DIR;
import static SimpleTradingBot.Config.Config.MAX_ORDER_UPDATES;

public class HeartBeat {

    private PrintWriter rtWriter;

    private Logger log;

    /* Singleton pattern */
    private static HeartBeat instance;

    private final OrderHistory orderHistory;

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
            this.log.severe( "Cant create necessary rt files. Skipping rt logging" );
            this.log.throwing(this.getClass().getSimpleName(), "HeartBeat", e);
        }
    }

    public static HeartBeat getInstance() {
        if (instance == null)
            instance = new HeartBeat();
        return instance;
    }

    public void register( Controller controller ) {
        this.log.entering( this.getClass().getSimpleName(), "register");
        String symbol = controller.getSummary().getSymbol();
        if ( !hasRegistered(controller) ) {
            this.log.info( "Registering controller: " + symbol);
            this.controllers.add( controller );
        }
        else
            this.log.warning( "Registration failed: " + symbol );
        this.log.exiting( this.getClass().getSimpleName(), "register");
    }

    private boolean hasRegistered(Controller controller) {

        for (Controller assetController : controllers) {
            TickerStatistics registeredSummary = assetController.getSummary();
            TickerStatistics newSummary = controller.getSummary();
            String newSymbol = newSummary.getSymbol();
            String registeredSymbol = registeredSummary.getSymbol();
            if (newSymbol.equals(registeredSymbol))
                return true;
        }

        return false;
    }

    public void deregister( String symbol ) {
        this.log.entering( this.getClass().getSimpleName(), "close" );
        this.log.warning( "Attempting deregister of symbol: " + symbol);

        int index = -1;
        for (int i = 0; i < controllers.size(); i++) {
            TickerStatistics registeredSummary = this.controllers.get(i).getSummary();
            String registeredSymbol = registeredSummary.getSymbol();
            if ( symbol.equals(registeredSymbol) ) {
                this.log.warning( "Deregistering: " + registeredSymbol );
                index = i;
                break;
            }
        }

        if ( index != -1 ) {
            this.controllers.remove(index);
        }

        this.log.exiting( this.getClass().getSimpleName(), "close" );
    }

    private void shutdown() {
        this.log.entering( this.getClass().getName(), "shutdown" );
        Thread thread = Thread.currentThread();
        if ( !thread.isInterrupted() )
            thread.interrupt();
        System.exit(0);
        this.log.exiting( this.getClass().getName(), "shutdown" );
    }

    public void logOrder( String symbol, RoundTrip roundTrip) {

        ArrayList<RoundTrip> roundTrips = this.orderHistory.get(symbol);

        if (roundTrips == null)
            roundTrips = new ArrayList<>();

        String stats = roundTrip.getStats();

        roundTrips.add( roundTrip );
        this.orderHistory.put( symbol, roundTrips );
        appendRt( symbol, stats );
    }

    public void maintenance() {
        this.log.entering( this.getClass().getSimpleName(), "maintenance" );

        Thread thread = Thread.currentThread();
        thread.setUncaughtExceptionHandler( handler );

        try {

            QueueMessage exitMessage = Static.EXIT_QUEUE.poll( 5, TimeUnit.SECONDS );

            if ( exitMessage != null
            && exitMessage.getType() == QueueMessage.Type.INTERRUPT
            && exitMessage.getSymbol().equals("*")) {
                this.log.severe( "Received shutdown message");
                shutdown();
            }

            QueueMessage message = Static.DR_QUEUE.poll( 5, TimeUnit.SECONDS );

            if ( message != null ) {
                QueueMessage.Type type = message.getType();
                String symbol = message.getSymbol();
                switch (type) {
                    case DEREGISTER:
                        deregister( symbol );
                }

                /* If there no more registered controllers, close the thread */
                if ( this.controllers.isEmpty()) {
                    this.log.warning( "Preparing to shutdown" );
                    shutdown();
                }
            }

            this.log.info( "Preparing maintenance");
            maintenance_internal();

        }

        catch ( Throwable e ) {

            this.log.log( Level.SEVERE, e.getMessage(), e );
            this.log.severe("Sending shutdown message" );
            QueueMessage message = new QueueMessage( QueueMessage.Type.INTERRUPT, "*" );
            Static.EXIT_QUEUE.offer( message );
            shutdown();

        }

        finally {
            this.log.info("Exiting maintenance" );
        }

        this.log.exiting( this.getClass().getSimpleName(), "maintenance" );
    }

    private void maintenance_internal()  throws STBException {
        this.log.entering( this.getClass().getSimpleName(), "maintenance_internal");

        for (Controller controller : this.controllers) {
            TickerStatistics summary = controller.getSummary();
            String symbol = summary.getSymbol();
            this.log.info( "Performing maintenance for symbol: " + symbol );

            if ( !checkHearbeat( controller ) )
                continue;

            this.log.info( "Heartbeat passed for symbol: " + symbol + " .Continuing maintenance" );

            PositionState currentState = controller.getState();

            Trader buyer = controller.getBuyer();


            /* There has been no changes to the
             * position state since last maintenance.
             *
             */
            if ( !currentState.isOutdated() ) {
                this.log.info( "No maintenance required" );
                continue;
            }

            Position buyPosition = buyer.getBuyOrder();
            Position sellPosition = buyer.getSellOrder();


            boolean bNull = buyPosition == null;
            boolean sNull = sellPosition == null;


            /* What stage of the cycle should we actually be maintaining... */

            if (bNull) {

                if (sNull) {
                    /* These lines should never be executed
                     * If both positions are null then the state should never be outdated */
                    log.severe( "Cycle: " + "CLEAR");
                    controller.updateState(PositionState.Type.CLEAR, PositionState.Flags.NONE);
                }
                else
                    throw new STBException( 200 );

            }
            else if (sNull || !sellAfterBuy(buyPosition, sellPosition)) {
                /* BUY/HOLD */
                log.info( "Cycle: " + "BUY/HOLD");
                update(controller, OrderSide.BUY);
            }

            else {
                log.info( "Cycle: " + "SELL/CLEAR");
                /* SELL/CLEAR */
                update(controller, OrderSide.SELL);
            }
        }

        log.exiting( this.getClass().getSimpleName(), "maintenance_internal");
    }

    private boolean checkHearbeat( Controller controller ) {
        boolean inTime = true;
        this.log.entering( this.getClass().getSimpleName(), "checkHeartbeat");
        String symbol = controller.getSummary().getSymbol();
        this.log.info( "Checking heartbeat for symbol: " + symbol );
        TimeSeries series = controller.getTimeSeries();
        ZonedDateTime endTime = series.getLastBar().getEndTime();
        ZonedDateTime now = ZonedDateTime.now( Config.ZONE_ID );
        long duration = endTime.until( now, ChronoUnit.MILLIS );
        long intervalToMillis = TimeKeeper.intervalToMillis( Config.CANDLESTICK_INTERVAL );
        if ( duration > ( intervalToMillis + Config.HB_TOLERANCE ) ) {
            this.log.severe( "Hearbeat failed for symbol: " + symbol + " . With idle duration of " + duration + "(s). Forcing exit of controller");
            controller.exit();
            inTime = false;
        }
        this.log.exiting( this.getClass().getSimpleName(), "checkHeartbeat");
        return inTime;
    }

    private PositionState.Flags getFlags(Position position) throws STBException {

        int nUpdates = position.getnUpdate();
        OrderStatus lastStatus = null;
        double execQty = 0;
        double origQty = 0;

        if ( nUpdates == 0) {

            switch (Config.TEST_LEVEL ) {
                case FAKEORDER:
                case NOORDER:
                    return PositionState.Flags.NONE;
                case REAL:
                    return PositionState.Flags.UPDATE;
            }

        }

        else {

            Order lastUpdate = position.getUpdatedOrder(nUpdates);
            execQty = Double.parseDouble(lastUpdate.getExecutedQty());
            origQty = Double.parseDouble(lastUpdate.getOrigQty());
            lastStatus = lastUpdate.getStatus();

            if (isFinished(lastStatus) || execQty == origQty)
                return PositionState.Flags.NONE;
        }

        if (nUpdates < MAX_ORDER_UPDATES) {
            switch (lastStatus) {
                case PARTIALLY_FILLED:
                case NEW:
                    return PositionState.Flags.UPDATE;
                case EXPIRED:
                case REJECTED:
                    if (execQty > 0)
                        return PositionState.Flags.CANCEL;
                    else
                        return PositionState.Flags.RESTART;
                default:
                    log.severe("Unrecognised order status: " + lastStatus.toString().toUpperCase());
                    throw new STBException( 210 );

            }
        }
        else
            return PositionState.Flags.CANCEL;
    }

    private boolean isFinished(OrderStatus lastStatus) {
        return (lastStatus == OrderStatus.FILLED
                || lastStatus == OrderStatus.PENDING_CANCEL
                || lastStatus == OrderStatus.CANCELED);
    }

    private PositionState.Type rotateState(PositionState.Type typedState) {
        if (typedState.isClean())
            return typedState;

        else {

            if (typedState == PositionState.Type.BUY)
                return PositionState.Type.HOLD;

            else
                return PositionState.Type.CLEAR;
        }
    }

    private PositionState.Type reverseState(PositionState.Type typedState) {
        if (typedState.isClean())
            return typedState;

        else {

            if (typedState == PositionState.Type.BUY)
                return PositionState.Type.CLEAR;
            else
                return PositionState.Type.HOLD;
        }
    }

    private void update(Controller controller, OrderSide side ) throws STBException {
        log.entering( this.getClass().getSimpleName(), "update");
        PositionState.Type currStateType = controller.getState().getType();
        PositionState.Type nextStateType = rotateState( currStateType );
        PositionState.Type prevStateType = reverseState( currStateType );
        Trader trader = controller.getBuyer();
        Position position = ( side == OrderSide.BUY ) ? trader.getBuyOrder() : trader.getSellOrder();
        PositionState.Flags flags = getFlags( position );
        log.info( "Updating side: " + side + ". Determined flags: " + flags );

        /* Flags should only be set to NONE in the case of a clean state.
         * However, just because we are in a clean state, it doesnt mean
         * that the flags are NONE
         *
         */
        switch (flags) {
            case UPDATE:
            case CANCEL:
            case RESTART:
                controller.updateState( currStateType, flags );
                break;
            case NONE:
                controller.updateState( nextStateType, flags );

                /* Check if the order needs logging */
                if ( side == OrderSide.SELL && currStateType != nextStateType ) {
                  String symbol = controller.getSummary().getSymbol();
                  RoundTrip rt = new RoundTrip( trader.getBuyPrice(), trader.getSellPrice(),
                          trader.getBuyOrder(), trader.getSellOrder() );
                  logOrder( symbol, rt );
                }

                break;
            case REVERT:
                controller.updateState( prevStateType, flags );
                break;
        }
        log.exiting( this.getClass().getSimpleName(), "update");
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

        this.log.exiting( this.getClass().getSimpleName(), "appendRt");
    }
}
