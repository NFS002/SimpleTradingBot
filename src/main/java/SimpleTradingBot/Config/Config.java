package SimpleTradingBot.Config;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Util.TestLevel;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerStatistics;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;


public class Config {

    final public static int budgetPerTrade = 25;

    final public static int minBars = 1;

    final public static double stopLoss = 0.95; //%

    final public static String binanceAPIkey = "<your api key here>";

    final public static String binanceSecretKey = "<your secret key here>";

    final public static CandlestickInterval CANDLESTICK_INTERVAL = CandlestickInterval.ONE_MINUTE;

    final public static double minVolume = 10000;

    public static final int maxSymbols = 5;

    public static final boolean logTA = true;

    final public static String[] baseAssets = {"BTC","ETH"};

    final public static double minPriceChange = 5;

    final public static double trailingLoss = stopLoss; //%

    final public static double takeProfit = 1000; //%

    final public static TestLevel testLevel = TestLevel.NOORDER;

    final public static int coolDown = 40; //whatever period you are trading in

    final public static HashMap<String,Double> qtyMap = new HashMap<>(); //not in use

    final public static String outDir = "out-" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "/";



    public static void init() throws Exception {
        File out = new File(outDir);
        if (out.exists()) out.delete();
        out.mkdir();
        File conf = new File(outDir + "config.txt");
        conf.createNewFile();
        new FileWriter(conf).append(new Config().toString()).flush();
    }

    public static boolean accept(TickerStatistics summary) {
        return (Double.parseDouble(summary.getVolume()) > Config.minVolume
                && Double.parseDouble(summary.getPriceChangePercent()) > Config.minPriceChange);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("budget_per_trade: " + budgetPerTrade + "\n")
                .append("min_bars: " + minBars + "\n")
                .append("stop_loss: " + stopLoss + "\n")
                .append("CANDLESTICK_INTERVAL: " + CANDLESTICK_INTERVAL + "\n")
                .append("min_volumes: " + minVolume + "\n")
                .append("max_symbols: " + maxSymbols + "\n")
                .append("logTa: " + logTA + "\n")
                .append("base_assets: " + Arrays.toString(baseAssets) + "\n")
                .append("min_price_change: " + minPriceChange + "\n")
                .append("trailing_loss: " + trailingLoss + "\n")
                .append("take_profit: " + takeProfit + "\n")
                .append("test_level: " + testLevel + "\n")
                .append("cool_down: " + coolDown + "\n")
                .append("out_dir: " + outDir + "\n")
                .append("Quantity map: " + qtyMap + "\n");
        return sb.toString();
    }
}
