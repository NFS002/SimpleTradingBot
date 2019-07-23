package SimpleTradingBot.Server;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Services.HeartBeat;
import SimpleTradingBot.Util.Static;
import org.rapidoid.commons.Str;
import org.rapidoid.http.*;
import org.rapidoid.setup.App;
import org.rapidoid.setup.On;

import javax.naming.AuthenticationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Server {

    private HashMap<String, ArrayList<Signal>> signalCache;

    private Logger log;

    private int nErr;



    public Server( int port ) {
        On.address("0.0.0.0").port( port);
        this.nErr = 0;
        String loggerName = this.getClass().getSimpleName();
        this.signalCache = new HashMap<>();
        this.log = Logger.getLogger( "root." + loggerName );
    }


    public Server( ) {
        this( Config.DEFAULT_PORT );
    }


    private void handle( Signal signal ) throws Exception {
        String className = this.getClass().getName();
        String methodName = "handle";
        this.log.entering( className, methodName, signal );
        this.log.info( "Handling signal: " + signal );

        String symbol = signal.getSymbol();
        String source = signal.getSource();
        int suggestedWeight = signal.getWeight();
        long createdAt = signal.getCreated();
        long now = System.currentTimeMillis();
        long diff = now - createdAt;

        HeartBeat heartBeat = HeartBeat.getInstance();

        if ( diff > Config.SIGNAL_TTL || diff < 0 ) {
            this.log.warning(  "Signal expired: " + diff);
            return;
        }

        else if ( symbol == null || symbol.isEmpty() ) {
            this.log.warning( "No symbol specified");
            return;
        }

        else if ( !Static.hasConstraint( symbol ) ) {
            this.log.warning( "No matching constraint found for " + symbol );
            return;
        }

        else if ( heartBeat.containsOrder( symbol ) ) {
            this.log.warning( "Current order found for " + symbol );
            return;
        }

        else if ( !Static.hasSource( source ) )  {
            this.log.warning( "Unrecognised source: " + source);
            return;
        }

        else if ( suggestedWeight == 0) {
            this.log.warning( "Suggested weight is " + suggestedWeight);
            return;
        }

        try {


            ArrayList<Signal> signals = this.signalCache.get( symbol );

            if ( signals == null)
                signals = new ArrayList<>();


            this.log.info( "Signal accepted, adding to cache");

            signals.add(0, signal );

            int weight = this.eval( signals );

            this.log.info(String.format( "Evaluated weight for %s: (%d/%d)",
                    symbol, suggestedWeight, Config.ORDER_WEIGHT_THRESH));

            if ( weight >= Config.ORDER_WEIGHT_THRESH ) {
                signal.setWeight( weight );
                Static.placeOrder( signal );
                this.log.info( "Placed order for symbol: " + symbol + ", clearing cache" );
                signals.clear();

            }

            else {
                this.log.info("Rejected evaluation for " + symbol + ", but signal was cached");
            }


            this.signalCache.put( symbol, signals );
            this.nErr = 0;
        }


        catch ( Throwable e ) {

            this.log.log( Level.SEVERE, String.format("Error placing order when handling signal request (%d/%d)", ++nErr, Config.MAX_QUEUE_RETRY), e);
            if (this.nErr >= Config.MAX_QUEUE_RETRY ) {
                this.log.severe( "Sending shutdown message");
                Static.requestExit("*");
                throw e;
            }
        }

        this.log.exiting( className, methodName, signal );
    }

    private synchronized int eval( ArrayList<Signal> signals ) {

        this.log.entering( this.getClass().getSimpleName(), "signals");

        int len = signals.size();
        String symbol = signals.get(0).getSymbol();
        this.log.info( "Evaluating " + len + " signals for " + symbol + ". (Before cleaning)");
        long now = System.currentTimeMillis();
        signals.removeIf( s -> now - s.getCreated() > Config.SIGNAL_TTL );
        len = signals.size();
        this.log.info( "Evaluating " + len + " signals for " + symbol + ". (After cleaning)");

        int nKnownSources = Config.KNOWN_SOURCES.length;
        int nKnownTypes = Config.KNOWN_TYPES.length;
        int suggestedWeights = signals.stream().mapToInt( Signal::getWeight ).sum();

        Object[] sources = signals.stream().map( Signal::getSource ).distinct().toArray();
        int distinctSources = sources.length;
        this.log.info( "Found " + distinctSources + " distinct sources");
        int s = (distinctSources / nKnownSources) * 10;

        Object[] types = signals.stream().map( Signal::getType ).distinct().toArray();
        int distinctTypes = types.length;
        this.log.info( "Found " + distinctTypes + " distinct sources");
        int k = ( distinctTypes/ nKnownTypes ) * 10;


        this.log.exiting( this.getClass().getSimpleName(), "signals");


        return s * k * suggestedWeights;
    }

    private void throwForAuth( Req req ) throws AuthenticationException {
        String value = req.headers().get( Config.AUTH_HEADER_KEY );

        if ( value == null || !value.equals( Config.AUTH_HEADER_VALUE )) {
            throw new AuthenticationException();
        }
    }

    private Object response(Resp resp, int code, Object message ) {
        return resp.code( code ).contentType( MediaType.TEXT_PLAIN_UTF8 ).result(  message + "\n" );
    }

    public void serve( int port ) {
        String className = this.getClass().getName();
        String methodName = "serve";
        this.log.entering( className, methodName );

        On.port( port );

        ReqRespHandler handler = (Req req, Resp resp) -> {

            this.log.info( "Handling new request");

            QueueMessage m = Static.checkExit( "*" );

            if ( m != null ) {
                this.log.severe( "Received exit message: " + m);
                this.shutdown();
                return response( resp, 200, "thannks but no thanks!" );
            }

            else {

                try {

                    if ( Config.SHOULD_AUTH ) {
                        this.throwForAuth(req);
                    }

                    Signal signal = req.data(Signal.class);

                    this.log.info( "Serialised new signal: " + signal );

                    handle(signal);

                    return response( resp, 200, "thanks!" );
                }

                catch ( AuthenticationException e ) {
                    this.log.log( Level.WARNING,"Request not authenticated", e);
                    return this.response( resp, 403, e.getClass().getCanonicalName() );
                }

                catch ( Exception ex ) {
                    this.log.log( Level.SEVERE, "Error while handling request", ex);
                    this.log.severe( "Sending shutdown message");
                    Static.requestExit( "*");
                    this.shutdown();
                    return response( resp, 500, "Server error" );
                }
            }
        };

        On.req( r -> response( r.response(), 404, "404!" ) );
        On.post("/signal").serve( handler );
        this.log.info( "Started server listening at port " + port);
        this.log.exiting( className, methodName );
    }

    public void shutdown() {
        String className = this.getClass().getName();
        String methodName = "shutdown";
        this.log.entering( className, methodName );
        this.log.warning( "Shutting down server" );
        App.shutdown();
        interrupt();
        this.log.exiting( className, methodName );
    }

    private void interrupt( ) {
        Thread thread = Thread.currentThread();
        if ( thread.isInterrupted() )
            thread.interrupt();
        System.exit( 1 );
    }

}
