package SimpleTradingBot.Test.Backtest.Feeder;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.event.CandlestickEvent;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class QuandlFeeder implements Feeder {

    final private SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");

    final private int DATE = 0;

    final private int OPEN = 1;

    final private int HIGH = 2;

    final private int LOW = 3;

    final private int CLOSE = 4;

    final private int VOLUME = 5;

    final private String SYMBOL;

    public QuandlFeeder(String symbol) {
        this.SYMBOL = symbol;
    }

    @Override
    public void readHeader(String header) {
        if ( header.equals("code,message"))
            throw new IllegalArgumentException("Invalid header");
    }

    @Override
    public void feed(String line, BinanceApiCallback<CandlestickEvent> callback) {
        try {
            String[] split = line.split(",");
            CandlestickEvent event = new CandlestickEvent();
            Date date = SDF.parse(split[DATE]);
            event.setSymbol(SYMBOL);
            event.setOpenTime(date.getTime());
            event.setOpen(split[OPEN]);
            event.setHigh(split[HIGH]);
            event.setLow(split[LOW]);
            event.setClose(split[CLOSE]);
            event.setVolume(split[VOLUME]);
            callback.onResponse( event );
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Invalid file format: " + line);
        }
    }
}
