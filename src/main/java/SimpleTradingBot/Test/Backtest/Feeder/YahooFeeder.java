package SimpleTradingBot.Test.Backtest.Feeder;

import SimpleTradingBot.Exception.STBException;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.event.CandlestickEvent;

public class YahooFeeder implements Feeder {


    @Override
    public void readHeader(String header) {

    }

    @Override
    public void feed(String line, BinanceApiCallback<CandlestickEvent> callback) {

    }
}
