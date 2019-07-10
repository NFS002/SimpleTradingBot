package SimpleTradingBot.Main;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.Controller;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Main {


    private final static Logger log = Logger.getLogger("root");

    public static void main(String[] args) {

    }

    private static void selectSymbols( List<TickerStatistics> list ) throws Exception {

    }
}

