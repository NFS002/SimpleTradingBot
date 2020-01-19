package SimpleTradingBot.Services;


import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.Controller;
import SimpleTradingBot.Controller.TimeKeeper;
import SimpleTradingBot.Controller.Trader;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.*;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.TimeSeries;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Util.Static.OUT_DIR;
import static SimpleTradingBot.Config.Config.MAX_ORDER_UPDATES;
import static SimpleTradingBot.Util.Static.requestExit;

public class HeartBeat {

    private Logger log;

    private ArrayList<Controller> controllers;

    /* Singleton pattern */
    private static HeartBeat instance;

    private HeartBeat(  ) {
        this.controllers = new ArrayList<>();
        this.log = Logger.getLogger( "root.hb" );
        this.log.setUseParentHandlers( true );
    }

    public static HeartBeat getInstance(  ) {
        if (instance == null)
            instance = new HeartBeat( );
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

        if ( index != -1 )
            this.controllers.remove(index);

        this.log.exiting( this.getClass().getSimpleName(), "close" );
    }

    public void maintain() {
        this.log.entering( this.getClass().getSimpleName(), "maintain" );

        try {
            QueueMessage message = Static.checkForDeregister();

            if ( message != null )
                deregister( message.getSymbol() );


            /* If there no more registered controllers, close the thread */
            if ( this.controllers.isEmpty()) {
                this.log.severe( "Controllers are empty, preparing to shutdown" );
                requestExit( "*" );
                this.shutdown();
            }

            else if ( this.shouldExit() ) {
                this.log.severe( "Received exit message, preparing to shutdown" );
                this.shutdown();
            }

            else {
                this.log.info( "Preparing maintenance");
                this._maintenance();
            }
        }
        catch ( Throwable e ) {
            this.log.log( Level.SEVERE, e.getMessage(), e );
            this.log.severe("Sending shutdown message, and preparing to shutdown" );
            requestExit("*");
            this.shutdown();
        }

        finally {
            this.log.exiting( this.getClass().getSimpleName(), "maintain" );
        }
    }

    private void shutdown()  {
        try {
            Thread.sleep(3000 );
        }
        catch ( InterruptedException e ) {
            log.log( Level.SEVERE, "Shutdown failed", e);
        }
        System.exit(0);
    }

    private void _maintenance()  throws STBException {
        this.log.entering( this.getClass().getSimpleName(), "_maintenance");

        for (Controller controller : this.controllers) {
            TickerStatistics summary = controller.getSummary();
            String symbol = summary.getSymbol();
            this.log.info("Performing maintenance for symbol: " + symbol);

            if (controller.isPaused())
                this.log.info("Controller paused (" + symbol + "), skipping maintenance");

            else if (this.checkHearbeat(controller))
                this.log.info("Heartbeat passed for symbol: " + symbol + " .Continuing maintenance");

            else {
                this.log.severe("Forcing exit of controller: " + symbol );
                controller.exit();
            }
        }
        log.exiting( this.getClass().getSimpleName(), "_maintenance");
    }

    private boolean checkHearbeat( Controller controller ) {
        this.log.entering( this.getClass().getSimpleName(), "checkHeartbeat");
        String symbol = controller.getSummary().getSymbol();
        this.log.info( "Checking heartbeat for symbol: " + symbol );
        TimeSeries series = controller.getTimeSeries();
        ZonedDateTime endTime = series.getLastBar().getEndTime();
        ZonedDateTime now = ZonedDateTime.now( Config.ZONE_ID );
        long duration = endTime.until( now, ChronoUnit.MILLIS );
        long interval = TimeKeeper.intervalToMillis( Config.CANDLESTICK_INTERVAL );
        boolean inTime = duration < interval + Config.HB_TOLERANCE;
        if ( !inTime )
            this.log.severe("Hearbeat failed for symbol: " + symbol + " . With idle duration of " + duration + "(s)");
        this.log.exiting( this.getClass().getSimpleName(), "checkHeartbeat");
        return inTime;
    }


    private boolean shouldExit() {
        this.log.entering( this.getClass().getSimpleName(), "shouldExit");
        this.log.info( "Checking exit queue");
        Optional<QueueMessage> exitMessage = Static.checkForExit( "*" );
        if ( exitMessage.isPresent() ) {
            this.log.severe( "Received shutdown message" );
            return true;
        }
        this.log.exiting( this.getClass().getSimpleName(), "shouldExit");
        return false;
    }

}
