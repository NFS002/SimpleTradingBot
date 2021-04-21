package SimpleTradingBot.Schedule;

import SimpleTradingBot.Controller.LiveController;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Services.AccountManager;
import SimpleTradingBot.Services.HeartBeat;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Util.SupportedCoins;
import SimpleTradingBot.Util.SymbolComparator;
import SimpleTradingBot.Util.SymbolPredicate;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.market.TickerStatistics;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static SimpleTradingBot.Config.Config.*;

public class TestSchedule {

    public static void main(String[] args) throws Exception {
        LiveController controller = new LiveController( "IOTAUSDT" );
        controller.liveStream();
    }
}
