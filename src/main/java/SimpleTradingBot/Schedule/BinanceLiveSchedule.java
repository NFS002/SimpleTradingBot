package SimpleTradingBot.Schedule;

import SimpleTradingBot.Controller.LiveController;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Plugins.CoinDesk;
import SimpleTradingBot.Services.AccountManager;
import SimpleTradingBot.Services.HeartBeat;
import SimpleTradingBot.Util.*;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.*;
import com.binance.api.client.domain.market.TickerStatistics;

import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.MAX_TIME_SYNC;
import static SimpleTradingBot.Config.Config.HEART_BEAT_INTERVAL;
import static SimpleTradingBot.Config.Config.AM_INTERVAL;
import static SimpleTradingBot.Config.Config.MAX_SYMBOLS;

public class BinanceLiveSchedule {


    public static void main(String[] args) throws Exception {

        Logger log = Logger.getLogger( "root" );
        log.entering("Schedule","main");

        Thread parentThread = Thread.currentThread();
        parentThread.setName( "Parent" );
        BinanceApiRestClient client = Static.getFactory().newRestClient();

        log.info( "Setting quote budget with CoinDesk plugin...");
        Static.QUOTE_PER_TRADE = CoinDesk.getQuotePerTrade();
        log.info( "Successfully set quote budget" );

        log.info( "Requesting exchange information...");
        ExchangeInfo exchangeInfo = client.getExchangeInfo();
        log.info( "Received exchange information");
        Static.exchangeInfo = exchangeInfo;

        /* Check we are in sync and all services are available */
        log.info( "Checking server time difference..." );
        long serverTime = exchangeInfo.getServerTime();
        long currentTime = System.currentTimeMillis();
        long diff = serverTime - currentTime;
        log.info( "Server time: " + serverTime + ", Client time: " + currentTime);

        if ( diff >= MAX_TIME_SYNC ) {
            log.severe( "Server time difference (" + diff + ") exceeds maximum value of " + MAX_TIME_SYNC );
            throw new STBException( 130 );
        }

        log.info("Server time difference: (" + diff + ")" );

        log.info( "Requesting 24hr statistics on all symbols...");
        List<TickerStatistics> statisticsList = client.getAll24HrPriceStatistics();
        int nSymbols = statisticsList.size();
        log.info( "Succesfully received " + nSymbols + " symbols");

        SymbolPredicate symbolPredicate = new SymbolPredicate( exchangeInfo );
        log.info( "Filtering symbols based on exchange information, under the following predicate: " + symbolPredicate);
        statisticsList.removeIf(symbolPredicate);

        if ( nSymbols > MAX_SYMBOLS ) {
            SymbolComparator comparator = new SymbolComparator();
            log.info( "Sorting symbols under the following comparator: " + comparator );
            statisticsList.sort( new SymbolComparator() );
            log.info("Sorting complete. Selecting first " + MAX_SYMBOLS + " symbols.");
            statisticsList = statisticsList.subList(0, MAX_SYMBOLS);
        }

        log.info( "Creating symbol constraints...");
        Static.constraints = FilterConstraints.getConstraints( exchangeInfo, statisticsList );
        log.info("Successfully retrieved symbol constraints" );

        /* Instantiate the services */
        log.info( "Instantiating services");
        HeartBeat heartBeat = HeartBeat.getInstance( );
        AccountManager accountManager = AccountManager.getInstance();

        for ( TickerStatistics statistics : statisticsList ) {
            String symbol = statistics.getSymbol();
            log.info( "Beginning live stream of symbol" + symbol);
            LiveController controller = new LiveController( statistics );
            controller.liveStream();
            heartBeat.register( controller );
        }

        /* Schedule execution of the services */
        log.info( "Scheduling execution of services..." );
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool( 0 );
        executorService.scheduleWithFixedDelay( heartBeat::maintain, 1, HEART_BEAT_INTERVAL, TimeUnit.MINUTES);
        executorService.scheduleWithFixedDelay( accountManager::maintain, 0, AM_INTERVAL, TimeUnit.MINUTES );

        /* Print aggregates */
        log.exiting("Schedule", "main");
    }
}
