package SimpleTradingBot.Main;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.Controller;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Services.AccountManager;
import SimpleTradingBot.Services.HeartBeat;
import SimpleTradingBot.Util.*;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.*;
import com.binance.api.client.domain.market.TickerStatistics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.MAX_TIME_SYNC;
import static SimpleTradingBot.Config.Config.ACCOUNT_MANAGER_INTERVAL;
import static SimpleTradingBot.Config.Config.MAX_SYMBOLS;

public class Schedule {

    public static void setBudgets() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet("https://api.coindesk.com/v1/bpi/currentprice.json");
        CloseableHttpResponse response = client.execute(get);
        Scanner s = new Scanner( response.getEntity().getContent() ).useDelimiter("\\A");
        String content =  s.hasNext() ? s.next() : "";;
        JsonObject jsonObject = new JsonParser().parse( content ).getAsJsonObject();
        JsonObject gbp = jsonObject.get("bpi").getAsJsonObject().get("GBP").getAsJsonObject();
        BigDecimal price = gbp.get("rate_float").getAsBigDecimal();
        BigDecimal budget = BigDecimal.valueOf( Config.GBP_PER_TRADE );
        int precision = 10;
        price = price.setScale( precision, RoundingMode.HALF_UP );
        budget = budget.setScale( precision, RoundingMode.HALF_UP );
        Static.QUOTE_PER_TRADE = budget.divide( price, RoundingMode.HALF_UP );
    }

    public static void main(String[] args) throws Exception {

        Static.initRootLoggers();

        Logger log = Logger.getLogger( "root" );
        log.entering("Schedule","main");

        Thread parentThread = Thread.currentThread();
        parentThread.setName( "Parent" );
        BinanceApiRestClient client = Static.getFactory().newRestClient();

        log.info( "Setting quote budget...");
        setBudgets();
        log.info( "Successfully set quote budget" );

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

        log.info( "Removing duplicate assets..." );
        SymbolPredicate.removeDuplicateQuotes( statisticsList );
        nSymbols = statisticsList.size();
        log.info( "Removed duplicates. " + nSymbols + " symbols remaining" );

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

        for ( TickerStatistics statistics : statisticsList ) {
            String symbol = statistics.getSymbol();
            log.info( "Beginning live stream of symbol" + symbol);
            Controller controller = new Controller( statistics );
            controller.liveStream();
        }


        /* Instantiate the services */
        log.info( "Initialising services.." );
        log.info( "Instantiating HeartBeat service...");
        HeartBeat heartBeat = HeartBeat.getInstance();
        AccountManager accountManager = new AccountManager();
        log.info( "HeartBeat service instantiated");
        log.info( "AccountManager service instantiated");

        /* Schedule execution of the services */
        int keepaliveInterval = 29, keepaliveDelay = 29, accountDelay = 1, nServices = 3;
        log.info( "Scheduling execution of services..." );
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool( nServices );
        log.info( "Successfully scheduled KeepAlive service");
        log.info( "Executing HeartBeat at rate of " + ACCOUNT_MANAGER_INTERVAL + " minute(s), with initial delay of " + accountDelay + " minute(s)"  );
        executorService.scheduleAtFixedRate( heartBeat::maintenance, accountDelay, ACCOUNT_MANAGER_INTERVAL, TimeUnit.MINUTES);
        log.info( "Successfully scheduled HeartBeat service");
        executorService.submit( accountManager::maintain );
        log.info( "Successfully scheduled AccountManager service");

        log.info( "Finished selecting symbols. Joining child threads...." );

        parentThread.join();

        log.info( "All child threads joined or terminated. Exiting program");

        /* Print aggregates */
        log.exiting("Schedule", "main");
    }
}
