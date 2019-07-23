package SimpleTradingBot.Main;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Util.*;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.*;
import com.binance.api.client.domain.market.TickerStatistics;

import java.util.List;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.MAX_TIME_SYNC;

public class Schedule {

    /* * TODO:
     * 1. Plugins, Coin gossip
     * 2. Num/BigDecimal/DecimalFormat
     * 3. Rate limits
     * 4. Commission constraints
     * 5. CandleStick Interval toId/toMillils
     * 6. Phase Visibility
     *
     */
    public static void main(String[] args) throws Exception {

        Static.init();

        Logger log = Logger.getLogger( "root" );
        log.entering("Schedule","main");

        Thread parentThread = Thread.currentThread();
        parentThread.setName( "Parent" );
        BinanceApiRestClient client = Static.getFactory().newRestClient();

        /* Check we are in sync and all services are available */
        log.info( "Pinging server... ");
        client.ping();
        log.info("Ping successful" );
        log.info( "Checking server time difference..." );
        long serverTime = client.getServerTime();
        long currentTime = System.currentTimeMillis();
        long diff = serverTime - currentTime;
        log.info( "Server time: " + serverTime + ", Client time: " + currentTime);

        if ( diff >= MAX_TIME_SYNC ) {
            log.severe( "Server time difference (" + diff + ") exceeds maximum value of " + MAX_TIME_SYNC );
            throw new STBException( 130 );
        }

        log.info("Server time difference: (" + diff + ")" );
        /* Initialise, configure, and setup */
        log.info( "Initialising configuration settings...");
        Config.print();
        log.info( "Configuration complete" );


        log.info( "Requesting exchange information...");
        ExchangeInfo exchangeInfo = client.getExchangeInfo();
        log.info( "Received exchange information");
        Static.exchangeInfo = exchangeInfo;
        log.info( "Setting rate limits...");

        log.info( "Requesting 24hr statistics on all symbols...");
        List<TickerStatistics> statisticsList = client.getAll24HrPriceStatistics();
        int nSymbols = statisticsList.size();
        log.info( "Succesfully received " + nSymbols + " symbols");

        SymbolPredicate symbolPredicate = new SymbolPredicate( exchangeInfo );
        log.info( "Filtering symbols based on exchange information, under the following predicate: " + symbolPredicate);
        statisticsList.removeIf(symbolPredicate);
        log.info( "Filtering successful. " + statisticsList.size() + " symbols remaining");

        /* log.info( "Removing duplicate assets..." );
        SymbolPredicate.removeDuplicateQuotes( statisticsList );
        nSymbols = statisticsList.size();
        log.info( "Removed duplicates. " + nSymbols + " symbols remaining" ); */

        /*if ( nSymbols > MAX_SYMBOLS ) {

            SymbolComparator comparator = new SymbolComparator();
            log.info( "Sorting symbols under the following comparator: " + comparator );
            statisticsList.sort( new SymbolComparator() );
            log.info("Sorting complete. Selecting first " + MAX_SYMBOLS + " symbols.");
            statisticsList = statisticsList.subList(0, MAX_SYMBOLS);
        } */

        log.info( "Creating symbol constraints...");
        Static.constraints = FilterConstraints.getConstraints( exchangeInfo, statisticsList );
        log.info("Successfully retrieved symbol constraints" );

       /* for ( TickerStatistics statistics : statisticsList ) {
            String symbol = statistics.getSymbol();
            log.info( "Beginning live stream of symbol" + symbol);
            Controller controller = new Controller( statistics );
            controller.liveStream();
        } */


        /*Instantiate the services
        log.info( "Initialising services.." );
        log.info( "Instantiating HeartBeat service...");
        HeartBeat heartBeat = HeartBeat.getInstance();
        AccountManager accountManager = new AccountManager();
        log.info( "HeartBeat service instantiated");
        log.info( "AccountManager service instantiated"); */

        /* Schedule execution of the services
        int keepaliveInterval = 29, keepaliveDelay = 29, accountDelay = 1, nServices = 3;
        log.info( "Scheduling execution of services..." );
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool( nServices );
        log.info( "Successfully scheduled KeepAlive service");
        log.info( "Executing HeartBeat at rate of " + ACCOUNT_MANAGER_INTERVAL + " minute(s), with initial delay of " + accountDelay + " minute(s)"  );
        executorService.scheduleAtFixedRate( heartBeat::maintenance, accountDelay, ACCOUNT_MANAGER_INTERVAL, TimeUnit.MINUTES);
        log.info( "Successfully scheduled HeartBeat service");
        executorService.submit( accountManager::maintain );
        log.info( "Successfully scheduled AccountManager service");

        /*log.info( "Finished selecting symbols. Joining child threads...." );

        parentThread.join();

        log.info( "All child threads joined or terminated. Exiting program");

        /* Print aggregates
        log.exiting("Schedule", "main"); */



        /* Start the server */

    }
}
