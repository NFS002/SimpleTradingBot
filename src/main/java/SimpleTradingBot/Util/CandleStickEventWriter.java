package SimpleTradingBot.Util;

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

    private long idx = 0;

    public CandleStickEventWriter(String rootDataPath) throws IOException {
        String dateString = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String fullDataPath = getNextAvailableFullPath(rootDataPath, dateString);
        this.writer = new PrintWriter(fullDataPath);
        this.writeHeader();
        this.writer.flush();
    }

    private String getNextAvailableFullPath(String rootDataPath, String dateString) {
        int versionNumber = 1;
        String suggestedFullPath = this.getSuggestedFullPath(rootDataPath, dateString, versionNumber);
        while (new File(suggestedFullPath).exists()) {
            suggestedFullPath = this.getSuggestedFullPath(rootDataPath, dateString, ++versionNumber);
        };
        return suggestedFullPath;
    }

    private String getSuggestedFullPath(String rootDataPath, String dateString, int versionNumber) {
        return String.format(
                "%s%s-(%s).csv",
                rootDataPath, dateString, versionNumber
        );
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
        long time = candlestickEvent.getEventTime();
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
        String open = candlestick.getOpen();
        String close = candlestick.getClose();
        String low = candlestick.getLow();
        String high = candlestick.getHigh();
        String volume = candlestick.getVolume();
        String row = String.format("%s,%s,%s,%s,%s,%s,%s,%s", this.idx, symbol, interval, open, close, low, high, volume);
        this.idx += 1;
        return row;
    }
}
