package SimpleTradingBot.Main;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.Manager;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Util.SymbolComparator;
import SimpleTradingBot.Util.SymbolPredicate;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Main {


    private final static Logger log = Logger.getLogger("root");

    public static void main(String[] args) {
        try {
            log.entering("SimpleTradingBot.Main","main");
            Config.init();
            Static.setLoggers();
            log.info( "Initializing Binance API client");
            BinanceApiRestClient binanceApi = Static.getFactory().newRestClient();
            binanceApi.ping();
            log.info("Requesting all symbols");
            List<TickerStatistics> statistics = binanceApi.getAll24HrPriceStatistics();
            log.info("Received: " + statistics.size() + " symbols");
            statistics.removeIf(new SymbolPredicate());
            statistics.sort(new SymbolComparator());
            List<TickerStatistics> list = statistics.subList(0, Config.maxSymbols);
            selectSymbols(list);
            Thread.currentThread().join();
        }
        catch (Throwable e) {
            log.log(Level.SEVERE,e.getMessage(),e);
        }
        finally {
            log.exiting("SimpleTradingBot.Main","main");
        }
    }

    private static void selectSymbols(List<TickerStatistics> list) throws Exception {
        for (TickerStatistics symbol:list) {
            try {
                if (Config.accept(symbol)) {
                    log.info("Preparing to stream " + symbol.getSymbol() + ". Initial price change: " + symbol.getPriceChangePercent());
                    new Manager(symbol).goLive();
                }
            }
            catch (BinanceApiException e) {
                log.log(Level.SEVERE,e.getMessage(),e);
            }
        }
    }
}

