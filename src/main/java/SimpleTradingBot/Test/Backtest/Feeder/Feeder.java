package SimpleTradingBot.Test.Backtest.Feeder;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.event.CandlestickEvent;

public interface Feeder {

    CandlestickEvent getCandlestickEvent( String line );

    void readHeader( String header );

    void feed(String line, BinanceApiCallback<CandlestickEvent> callback );

}