package SimpleTradingBot.Config;
import SimpleTradingBot.Rules.IRule;
import SimpleTradingBot.Rules.SMACross;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Util.TestLevel;
import com.binance.api.client.constant.BinanceApiConstants;
import com.binance.api.client.domain.market.CandlestickInterval;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.time.ZoneId;


public class Config {

    final public static int MAX_ORDER_UPDATES = 10;

    final public static int INTERVAL_TOLERANCE = 5000;

    final public static int MAX_ORDER_RETRY = 5;

    final public static int ACCOUNT_MANAGER_INTERVAL = 1;

    final public static long MIN_VOLUME = 10000;

    final public static boolean FORCE_ORDER = false;

    final public static boolean FORCE_CLOSE = false;

    final public static int MAX_ERR = 30;

    final public static int HB_TOLERANCE = 60000;

    final public static ZoneId ZONE_ID = ZoneId.systemDefault();

    final public static long RECV_WINDOW = BinanceApiConstants.DEFAULT_RECEIVING_WINDOW;

    final public static long MAX_DDIFF = 100;

    final public static int MAX_TIME_SYNC = 1000;

    final public static IRule[] TA_RULES = getTaRules();

    final public static int minBars = 1;

    final public static int GBP_PER_TRADE = 25;

    final public static BigDecimal STOP_LOSS_PERCENT = new BigDecimal( 0.95 ); //%

    final public static CandlestickInterval CANDLESTICK_INTERVAL = CandlestickInterval.ONE_MINUTE;

    final public static double minVolume = 10000;

    public static final int MAX_WSS_ERR = 5;

    public static int MAX_SYMBOLS = 1;

    public static final boolean SHOULD_LOG_TA = true;

    public static final boolean SHOULD_LOG_TS = true;

    public static final int INIT_BARS = 498;

    public static final boolean SHOULD_LOG_INIT_TS = false;

    public static final boolean INIT_TS = true;

    final public static String QUOTE_ASSET = "BTC";

    final public static double minPriceChange = 5;

    final public static boolean TRAILING_STOP = true; //%

    final public static double takeProfit = 1000; //%

    final public static TestLevel TEST_LEVEL = TestLevel.FAKEORDER;

    final public static int MAX_BAR_COUNT = 500;

    final public static int COOL_DOWN = 5;

    public static void print() throws Exception {
        File conf = new File(Static.OUT_DIR + "config.txt");
        conf.createNewFile();
        Config c = new Config();
        new FileWriter(conf).append( c.toString() ).flush();
    }

    public static IRule[] getTaRules() {
        return new IRule[] {
                new SMACross(14, 50, 100, 500)
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("budget_per_trade: " + GBP_PER_TRADE + "\n")
                .append("min_bars: " + minBars + "\n")
                .append("stop_loss: " + STOP_LOSS_PERCENT + "\n")
                .append("CANDLESTICK_INTERVAL: " + CANDLESTICK_INTERVAL + "\n")
                .append("min_volumes: " + minVolume + "\n")
                .append("max_symbols: " + MAX_SYMBOLS + "\n")
                .append("logTa: " + SHOULD_LOG_TA + "\n")
                .append("base_asset: " + QUOTE_ASSET + "\n")
                .append("min_price_change: " + minPriceChange + "\n")
                .append("trailing_loss: " + TRAILING_STOP + "\n")
                .append("take_profit: " + takeProfit + "\n")
                .append("test_level: " + TEST_LEVEL + "\n")
                .append("cool_down: " + COOL_DOWN + "\n")
                .append("out_dir: " + Static.OUT_DIR + "\n");
        return sb.toString();
    }
}
