package SimpleTradingBot.Schedule;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.LiveController;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Plugins.CoinDesk;
import SimpleTradingBot.Test.Backtest.Feeder.Feeder;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Util.SupportedCoins;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.market.TickerStatistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        log.info( "Setting quote budget with CoinDesk plugin...");
        Static.QUOTE_PER_TRADE = CoinDesk.getQuotePerTrade();
        log.info( "Successfully set quote budget as: " + Static.QUOTE_PER_TRADE);

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

        Set<String> backtested_symbols = BACKTEST_DATA_SYMBOL_MAP.keySet();
        statisticsList.removeIf(ts -> !backtested_symbols.contains(ts.getSymbol()));

        HashMap<String, FilterConstraints> constraints = FilterConstraints.getConstraints( exchangeInfo, statisticsList );
        Static.constraints = constraints;
        log.info("Successfully retrieved symbol constraints" );

        for ( Map.Entry<String, String> entry : BACKTEST_DATA_SYMBOL_MAP.entrySet() ) {

            String symbol = entry.getKey();
            String dataPath = entry.getValue();

            log.info( "Beginning back test of symbol: " + symbol);

            Thread thread = new Thread(() -> {
                try {
                    LiveController controller = new LiveController( symbol );
                    Feeder feeder = getBinanceFeeder();
                    List<String> allLines = Files.readAllLines(Path.of(dataPath));
                    allLines.remove(0);
                    for (String line: allLines ) {
                        feeder.feed(line, controller);
                    }
                    controller.exit();
                }
                catch (IOException e) {
                    log.severe("Back test failed for symbol: " + symbol + " : "
                            + e.toString());
                }
            });
            thread.start();
        };

        /* Print aggregates */
        log.exiting("BinanceTestSchedule", "main");
    }
}
