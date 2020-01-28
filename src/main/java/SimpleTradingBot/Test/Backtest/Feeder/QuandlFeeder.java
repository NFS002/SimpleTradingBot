package SimpleTradingBot.Test.Backtest.Feeder;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.event.CandlestickEvent;

public class QuandlFeeder implements Feeder {

    @Override
    public CandlestickEvent getCandlestickEvent(String line) {
        return null;
    }

    @Override
    public void readHeader(String header) {

    }

    @Override
    public void feed(String line, BinanceApiCallback<CandlestickEvent> callback) {

    }
}
