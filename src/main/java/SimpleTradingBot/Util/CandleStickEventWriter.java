package SimpleTradingBot.Util;

import SimpleTradingBot.Config.Config;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.Candlestick;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CandleStickEventWriter {

    public static final String CSV_HEADER = "time,symbol,interval,open,close,low,high,volume";

    private final PrintWriter writer;

    public CandleStickEventWriter(File rootPath) throws IOException {
        String fullDataPath = rootPath + "/stream.csv";
        this.writer = new PrintWriter(fullDataPath);
        this.writeHeader();
        this.writer.flush();
    }

    public void writeHeader() {
        writer.println(CSV_HEADER);
    }

    public void writeCandlestickEvent(CandlestickEvent event) {
        String eventString = this.candlestickEventToCsvString(event);
        this.writer.println(eventString);
        this.writer.flush();
    }

    public void writeCandlestick(Candlestick candlestick) {
        String eventString = this.candlestickToCsvString(candlestick);
        this.writer.println(eventString);
        this.writer.flush();
    }

    public void close() {
        this.writer.close();
    }

    private String candlestickEventToCsvString(CandlestickEvent candlestickEvent) {
        long time = candlestickEvent.getOpenTime();
        String symbol = candlestickEvent.getSymbol();
        String interval = candlestickEvent.getIntervalId();
        String open = candlestickEvent.getOpen();
        String close = candlestickEvent.getClose();
        String low = candlestickEvent.getLow();
        String high = candlestickEvent.getHigh();
        String volume = candlestickEvent.getVolume();
        return String.format("%s,%s,%s,%s,%s,%s,%s,%s", time, symbol, interval, open, close, low, high, volume);
    }

    private String candlestickToCsvString(Candlestick candlestick) {
        String symbol = "";
        String interval = "";
        long time = candlestick.getOpenTime();
        String open = candlestick.getOpen();
        String close = candlestick.getClose();
        String low = candlestick.getLow();
        String high = candlestick.getHigh();
        String volume = candlestick.getVolume();
        return String.format("%s,%s,%s,%s,%s,%s,%s,%s", time, symbol, interval, open, close, low, high, volume);
    }
}
