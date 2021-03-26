package SimpleTradingBot.Test.Backtest.Feeder;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.event.CandlestickEvent;

public class BinanceFeeder implements Feeder {



    public BinanceFeeder() {
    }

    @Override
    public void feed( String line, BinanceApiCallback<CandlestickEvent> callback) {
        CandlestickEvent event = this.parseCandleStick(line);
        callback.onResponse(event);
    }

    private CandlestickEvent parseCandleStick(String line) {
        String[] splitLine = line.split(",");
        CandlestickEvent event = new CandlestickEvent();
        event.setEventTime(Long.parseLong(splitLine[0]));
        event.setSymbol(splitLine[1]);
        event.setIntervalId(splitLine[2]);
        event.setOpen(splitLine[3]);
        event.setClose(splitLine[4]);
        event.setLow(splitLine[5]);
        event.setHigh(splitLine[6]);
        event.setVolume(splitLine[7]);
        return event;
    }
}
