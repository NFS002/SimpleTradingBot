package SimpleTradingBot.Schedule;

import SimpleTradingBot.Controller.LiveController;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Test.Backtest.Feeder.Feeder;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Util.SupportedCoins;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.market.TickerStatistics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.*;
import static SimpleTradingBot.Test.Backtest.Feeder.FeederFactory.getBinanceFeeder;

public class BinanceTestSchedule {

    public static void main(String[] args) throws Exception {

        BACKTEST = true;
        COLLECT_DATA = false;

        Logger log = Logger.getLogger( "root" );
        log.entering("BinanceTestSchedule","main");

        Thread parentThread = Thread.currentThread();
        parentThread.setName( "Parent" );
        BinanceApiRestClient client = Static.getFactory().newRestClient();

        log.info( "Requesting exchange information...");
        ExchangeInfo exchangeInfo = client.getExchangeInfo();
        log.info( "Received exchange information");
        Static.exchangeInfo = exchangeInfo;

        log.info( "Loading supported coins...");
        SupportedCoins.loadSupportedCoins();

        log.info( "Requesting 24hr statistics on all symbols...");
        List<TickerStatistics> statisticsList = client.getAll24HrPriceStatistics();
        int nSymbols = statisticsList.size();
        log.info( "Succesfully received " + nSymbols + " symbols");

        log.info( "Creating symbol constraints...");

        Map<String, String> backtestedSymbolMap = new HashMap<>();
        for (String path : BACKTEST_STREAMS) {
            File rootOut = new File(BASE_OUT_PATH + path);
            if (rootOut.exists() && rootOut.getName().matches("\\d{4}-\\d{2}-\\d{2}-[a-z]\\d")) {
                File[] symbolDirs = rootOut.listFiles(name -> name.getName().matches("[A-Z]{3,10}"));
                for (File symbolDir : symbolDirs) {
                    backtestedSymbolMap.put(symbolDir.getName(), symbolDir.getPath() + "/stream.csv");
                }
            }
            else if (rootOut.exists() && rootOut.getName().matches("[A-Z]{3,10}")) {
                backtestedSymbolMap.put(rootOut.getName(), rootOut.getPath() + "/stream.csv");
            }
            else throw new IllegalArgumentException("Unable to resolve backtest path set in config: " + path);
        }

        Set<String> backtestedSymbols = backtestedSymbolMap.keySet();
        statisticsList.removeIf(ts -> !backtestedSymbols.contains(ts.getSymbol()));

        Static.constraints = FilterConstraints.getConstraints( exchangeInfo, statisticsList );
        log.info("Successfully retrieved symbol constraints" );

        CountDownLatch countDownLatch = new CountDownLatch(backtestedSymbols.size());

        for ( String symbol : backtestedSymbols ) {

            String dataPath = backtestedSymbolMap.get(symbol);

            log.info( "Beginning back test of symbol: " + symbol);

            Thread thread = new Thread(new ThreadGroup("kl"), () -> {
                try {
                    LiveController controller = new LiveController( symbol );
                    Feeder feeder = getBinanceFeeder();
                    List<String> allLines = Files.readAllLines(Path.of(dataPath));
                    allLines.remove(0);
                    for (String line: allLines ) {
                        feeder.feed(line, controller);
                    }
                    controller.exit();
                    countDownLatch.countDown();
                }
                catch (IOException e) {
                    log.severe("Back test failed for symbol: " + symbol + " : "
                            + e.toString());
                }
            });

            thread.start();

        };


        countDownLatch.await();

        /* Print aggregates */
        log.exiting("BinanceTestSchedule", "main");
        System.exit(0);
    }
}
