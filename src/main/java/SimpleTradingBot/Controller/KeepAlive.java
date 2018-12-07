package SimpleTradingBot.Controller;

import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.exception.BinanceApiException;
import java.time.Duration;
import java.time.LocalDateTime;

class KeepAlive {

    private final String key;

    private LocalDateTime lastUpdate;

    KeepAlive() throws BinanceApiException {
        BinanceApiRestClient binanceApi = Static.getFactory().newRestClient();
        this.key = binanceApi.startUserDataStream();
        lastUpdate = LocalDateTime.now();
    }

    void update() throws BinanceApiException {
        if (LocalDateTime.now().isAfter(lastUpdate.plus(Duration.ofMinutes(30)))) {
            BinanceApiRestClient binanceApi = Static.getFactory().newRestClient();
            binanceApi.keepAliveUserDataStream(key);
        }
    }
}
