package SimpleTradingBot.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Strategy.*;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Test.TestLevel;
import com.binance.api.client.constant.BinanceApiConstants;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.market.CandlestickInterval;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;


public class Config {

    final public static int MAX_ORDER_UPDATES = 10;

    final public static int INTERVAL_TOLERANCE = 5000;

    final public static int MAX_ORDER_RETRY = 5;

    final public static int HEART_BEAT_INTERVAL = 1;

    final public static int AM_INTERVAL = 30;

    final public static long MIN_VOLUME = 10000;

    final public static boolean FORCE_ORDER = false;

    final public static boolean FORCE_CLOSE = false;

    final public static int EXIT_AFTER = -1;

    final public static int MAX_ERR = 50;

    final public static int HB_TOLERANCE = 60000;

    final public static ZoneId ZONE_ID = ZoneId.systemDefault();

    final public static long RECV_WINDOW = BinanceApiConstants.DEFAULT_RECEIVING_WINDOW;

    final public static long MAX_DDIFF = 100;

    final public static int MAX_TIME_SYNC = 1000;

    public static IStrategy[] TA_STRATEGIES = new IStrategy[] {
            new SMACross(14, 50, 3 ),
            new ROCP( 1, 0 )
    };

    final public static int GBP_PER_TRADE = 25;

    final public static BigDecimal STOP_LOSS_PERCENT = BigDecimal.valueOf(0.95);

    final public static CandlestickInterval CANDLESTICK_INTERVAL = CandlestickInterval.ONE_MINUTE;

    final public static double minVolume = 10000;

    public static final int MAX_WSS_ERR = 5;

    public static final int MAX_SYMBOLS = 5;

    public static final boolean SHOULD_LOG_TA = true;

    public static final int LOG_TS_AT = 0;

    final public static int START_AT = 500;

    public static final boolean INIT_TS = true;

    final public static String QUOTE_ASSET = "BTC";

    final public static BigDecimal MAX_PRICE_CHANGE_PERCENT = new BigDecimal("0.001");

    final public static boolean TRAILING_STOP = true; //%

    final public static double takeProfit = 1000; //%

    final public static TestLevel TEST_LEVEL = TestLevel.MOCK;

    final public static HashMap<String, OrderStatus[]> MOCK_ORDER_UPDATE_PATTERN = new HashMap<>() {{
        put( "*", new OrderStatus[]{ OrderStatus.FILLED });
    }};

    public static boolean COLLECT_DATA = true;

    public static boolean BACKTEST = false;

    final public static Map<String, String> BACKTEST_DATA_SYMBOL_MAP = new HashMap<>() {{
       put("STEEMBTC", "data/STEEMBTC/2021-03-22.csv");
    }};

    final public static long MOCK_ORDER_ID = 12345L;

    final public static int MAX_BAR_COUNT = 600;

    final public static int COOL_DOWN = 15;

    final public static boolean SKIP_SLIPPAGE_TRADES = true;

    final public static boolean WEB_NOTIFICATIONS = true;

    final public static String BINANCE_API_KEY = System.getenv("BINANCE_API_KEY");

    final public static String BINANCE_SECRET_KEY = System.getenv("BINANCE_SECRET_KEY");

    final public static String ROLLBAR_API_KEY = System.getenv("ROLLBAR_API_KEY");


    static {

        if (BINANCE_API_KEY == null) {
            throw new IllegalArgumentException("BINANCE_API_KEY is null");
        }

        if (BINANCE_SECRET_KEY == null) {
            throw new IllegalArgumentException("BINANCE_SECRET_KEY is null");
        }
    }


    public static void print() {
        try {
            File conf = new File(Static.ROOT_OUT + "config.txt");
            conf.createNewFile();
            Config c = new Config();
            FileWriter w = new FileWriter(conf);
            w.append(c.toString());
            w.close();
        }
        catch ( IOException e ) {
            throw new STBException( 30 );
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("budget_per_trade: " + GBP_PER_TRADE + "\n")
                .append("min_bars: " + START_AT + "\n")
                .append("stop_loss: " + STOP_LOSS_PERCENT + "\n")
                .append("CANDLESTICK_INTERVAL: " + CANDLESTICK_INTERVAL + "\n")
                .append("min_volumes: " + minVolume + "\n")
                .append("max_symbols: " + MAX_SYMBOLS + "\n")
                .append("logTa: " + SHOULD_LOG_TA + "\n")
                .append("base_asset: " + QUOTE_ASSET + "\n")
                .append("min_price_change: " + MAX_PRICE_CHANGE_PERCENT + "\n")
                .append("trailing_loss: " + TRAILING_STOP + "\n")
                .append("take_profit: " + takeProfit + "\n")
                .append("test_level: " + TEST_LEVEL + "\n")
                .append("cool_down: " + COOL_DOWN + "\n")
                .append("out_dir: " + Static.ROOT_OUT + "\n");
        return sb.toString();
    }
}
