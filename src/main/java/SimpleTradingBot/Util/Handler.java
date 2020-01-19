package SimpleTradingBot.Util;

import SimpleTradingBot.Models.QueueMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Handler implements Thread.UncaughtExceptionHandler {

    private String symbol;

    public Handler(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public void uncaughtException( Thread thread, Throwable throwable ) {
        Logger log = Logger.getLogger( "root." + symbol );
        log.entering( this.getClass().getSimpleName(), "uncaughtException");
        log.log(Level.SEVERE, "Uncaught Exception. Sending exit message and exiting thread now", throwable);
        Static.requestExit("*");
        if ( !Thread.currentThread().isInterrupted() )
            Thread.currentThread().interrupt();
        System.exit(0);
    }}