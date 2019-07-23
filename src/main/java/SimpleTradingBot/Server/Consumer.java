package SimpleTradingBot.Server;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.Controller;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.RoundTrip;
import SimpleTradingBot.Services.HeartBeat;
import SimpleTradingBot.Util.OrderRequest;
import SimpleTradingBot.Util.Static;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

class Consumer {

    private static Logger log;

    private static int nErr;

    static {
        log = Logger.getLogger("root.Consumer");
        nErr = 0;
    }

    public static void consume() throws Throwable {
        log.entering( "Consumer", "consume");
        int weight = 0;
        OrderRequest request = null;
        String symbol = null;
        try {
            int len = Static.ORDER_QUEUE.size();
            log.fine( "Order queue size: " + len );
            if ( len > 0 ) {
                request = Static.ORDER_QUEUE.poll(2, TimeUnit.MINUTES);
                    if (request != null) {
                        log.info( request.toString() );
                        symbol = request.getSymbol();
                        weight = request.getWeight();
                        if (weight >= Config.MIN_ACCEPTED_WEIGHT ) {
                            HeartBeat heartBeat = HeartBeat.getInstance();
                            if (heartBeat.containsOrder(symbol))
                                log.warning("Order already found for symbol: " + symbol);
                            else if (Static.constraints.get(symbol) == null)
                                log.warning("Symbol not accepted: " + symbol);
                            else {
                                log.info("Instantiating controller and opening order");
                                Controller controller = new Controller(request);
                                RoundTrip roundTrip = controller.execute();
                                heartBeat.registerNewOrder( symbol, roundTrip );
                                log.info("Successfully executed new controller");
                            }
                        }
                        else
                            log.warning( String.format(
                                    "Request weight is below minimum (%d/%d)", weight, Config.MIN_ACCEPTED_WEIGHT));
                    }
                else {
                    throw new STBException( 280 );
                }
            }
            else {
                log.info("Queue is empty" );
            }
        }

        catch ( STBException stbEx ) {
            log( stbEx );
            int statusCode = stbEx.getStatusCode();
            switch ( statusCode ) {

                case 180: // Below min qty
                Static.removeConstraint( symbol ); break;

                case 190: // Order suspended
                if ( weight > 2 ) {
                    log.warning( "Reducing weight and requeuing order");
                    request.setWeight( --weight );
                    Static.ORDER_QUEUE.offer(request);
                } break;

                case 280: // Unable to poll from queue
                shutdownIf( stbEx ); break;

                case 70: // MAX_ORDER_RETRY
                shutdownNow( stbEx );


            }
        }

        catch ( InterruptedException e ) {
            log( e );
            shutdownIf(  e );
        }
    }

    private static void log(Throwable e ) throws Throwable {
        log.entering( "Consumer", "log");
        log.log( Level.WARNING, "Consumer failed (" + nErr + "/" + Config.MAX_QUEUE_RETRY + ")", e);
        log.exiting( "Consumer", "log" );
    }

    private static void shutdownIf( Throwable e ) throws Throwable {
        log.entering( "Consumer", "shutdownIf");

        if ( ++nErr >= Config.MAX_QUEUE_RETRY ) {

            shutdownNow( e );
        }

        log.exiting( "Consumer", "shutdownIf" );
    }

    private static void shutdownNow( Throwable e ) throws Throwable {
        log.entering( "Consumer", "shutdownNow");
        log.severe( "Requesting shut down");
        Static.requestExit( "*" );
        log.exiting( "Consumer", "shutdownNow");
        throw e;
    }

    private static void rejectSignal( String message ) {
        log.entering( "Consumer", "rejectSignal");
        log.warning( message );
        log.exiting( "Consumer", "rejectSignal");
    }

}
