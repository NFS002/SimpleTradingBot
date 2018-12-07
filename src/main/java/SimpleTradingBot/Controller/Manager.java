package SimpleTradingBot.Controller;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Util.Position;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Util.TAbot;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import org.ta4j.core.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.*;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

//where the magic happens....
public class Manager implements BinanceApiCallback<CandlestickEvent> {

    private TickerStatistics summary;

    private KeepAlive keepAlive;

    private TAbot taBot;

    private TimeSeries timeSeries = new BaseTimeSeries();

    private Trader buyer;

    private final PumpMonitor monitor;

    private TrailingStop stopLossBot;

    private Logger log;

    private PrintWriter tsWriter;

    private PrintWriter taWriter;

    private LocalDateTime startStreamTime;

    private int coolOff = 0; //where we start the cool off period.

    public Manager(TickerStatistics summary) throws BinanceApiException,IOException {
        this.summary = summary;
        this.buyer = new Trader(summary);
        this.taBot = new TAanalyser();
        this.monitor = new PumpMonitor(summary, this.timeSeries);
        this.stopLossBot = new TrailingStop(summary);
        //this.keepAlive = new KeepAlive();
        initLoggers();
        initSeries();
    }

    public void goLive() throws BinanceApiException {
        log.info("Beginning data stream");
        BinanceApiWebSocketClient webSocketClient = Static.getFactory().newWebSocketClient();
        webSocketClient.onCandlestickEvent(summary.getSymbol().toLowerCase(),Config.CANDLESTICK_INTERVAL, this);
        onWebSocketConnect();
    }

    private void initSeries() {
        BinanceApiRestClient client = Static.getFactory().newRestClient();
        List<Candlestick> candlesticks = client.getCandlestickBars(summary.getSymbol(), Config.CANDLESTICK_INTERVAL);
        for (Candlestick candletick:candlesticks)
            addBarToTimeSeries(candletick);
    }

    @Override
    public void onFailure(Throwable cause) {
        StringBuilder msg = new StringBuilder();
        msg.append("WebSocketError: " + cause);
        Duration duration = Duration.between(startStreamTime,LocalDateTime.now());
        msg.append("\nTime Elapsed since open: " + duration.getSeconds() + "s");
        log.severe(msg.toString());
    }

    public void onWebSocketConnect() {
        log.info("Connected" +
                " to WSS data stream");
        startStreamTime = LocalDateTime.now();
    }

    @Override
    public void onResponse(CandlestickEvent candlestick)  {
        try {
            log.entering(this.getClass().getSimpleName(), "onMessa");
            Instant instant = Instant.ofEpochMilli(candlestick.getCloseTime());
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("GMT"));
            if (kLineIsInTime(zonedDateTime)) {
                //keepAlive.update();
                double close = Double.parseDouble(candlestick.getClose());
                log.info("Adding bar to TimeSeries: ("  + timeSeries.getBarCount() + ")\n" + candlestick);
                addBarToTimeSeries(candlestick);
                String duration = Static.timeFormatter.format(zonedDateTime);
                this.tsWriter.append(duration + "\t\t\t\t" + close + "\t\t\t" + getPosition() + "\t\t\t\t\n").flush();
                stopLossBot.updateStopLoss(close);

                if ((getPosition() == Position.OB) && close < stopLossBot.getStopLoss())
                    closeOrder();

                else if (sufficientBars() && coldEnough())
                    processKline(candlestick);


                else
                    log.info("No action taken");

            }
        }
        catch (Throwable e) {
            log.log(Level.SEVERE,e.getMessage(),e);
        }
        finally {
            log.exiting(this.getClass().getSimpleName(), "onMessage");
        }
    }


    private boolean sufficientBars() {
        return timeSeries.getBarCount() > Config.minBars;
    }

    private boolean coldEnough() {
        boolean coldEnough = coolOff == 0;
        if (!coldEnough) --coolOff;
        return coldEnough;
    }

    private boolean kLineIsInTime(ZonedDateTime nextTime) {
        return (timeSeries.getBarCount() == 0 || nextTime.isAfter(timeSeries.getLastBar().getEndTime()));
    }

    private void addBarToTimeSeries(CandlestickEvent candlestick) {
        Instant instant = Instant.ofEpochMilli(candlestick.getCloseTime());
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("GMT"));
        double open = Double.parseDouble(candlestick.getOpen());
        double high = Double.parseDouble(candlestick.getHigh());
        double low = Double.parseDouble(candlestick.getLow());
        double close = Double.parseDouble(candlestick.getClose());
        double volume = Double.parseDouble(candlestick.getVolume());
        timeSeries.addBar(new BaseBar(zonedDateTime, open, high, low, close, volume));
    }

    private void addBarToTimeSeries(Candlestick candlestick) {
        Instant instant = Instant.ofEpochMilli(candlestick.getCloseTime());
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("GMT"));
        double open = Double.parseDouble(candlestick.getOpen());
        double high = Double.parseDouble(candlestick.getHigh());
        double low = Double.parseDouble(candlestick.getLow());
        double close = Double.parseDouble(candlestick.getClose());
        double volume = Double.parseDouble(candlestick.getVolume());
        timeSeries.addBar(new BaseBar(zonedDateTime, open, high, low, close, volume));
    }

    private void processKline(CandlestickEvent candlestick) {
        log.entering(this.getClass().getSimpleName(),"processKline");
        double close = Double.parseDouble(candlestick.getClose());
        log.info("calculating TA");
        boolean ta = taBot.doTA(timeSeries, taWriter);

        if (ta) {
            monitor.logPump();
            switch (Config.testLevel) {
                case REAL:
                case FAKEORDER:     openNewOrder(close);
                                    break;

                case NOORDER:
                default:            break;
            }
        }

        else
            log.info("TA rejected");

        log.exiting(this.getClass().getSimpleName(),"processKline");
    }

    private void closeOrder() {
        if (buyer.close()) {
            this.coolOff = Config.coolDown;
            stopLossBot.setStopLoss(0);
        }
        else {
            log.severe("Unable to close order");
        }
    }

    private void openNewOrder(double price) {
        log.entering(Manager.class.getSimpleName(), "openNewOrder");
        log.info("Opening order");
        boolean success = buyer.open(price);
        if (success)
           stopLossBot.setStopLoss(price * Config.stopLoss);
        else
            log.severe("Error in opening order");
        log.exiting(Manager.class.getSimpleName(), "openNewOrder");
    }

    private void initLoggers() throws IOException {
        File dir = new File(Config.outDir + summary.getSymbol()); // create log files
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create necessary directories");
        log = Logger.getLogger("root." + summary.getSymbol()); //Managers (this) logger
        FileHandler fileHandler = new FileHandler(dir + "/debug.log");
        log.addHandler(fileHandler); // all handler
        this.tsWriter = new PrintWriter(dir + "/ts.tsv" ); //data print tsWriter
        tsWriter.append("TIME\t\t\t\tCLOSE\t\t\t\tPOS\t\t\t\t\n").flush();
        if (Config.logTA) {
            this.taWriter = new PrintWriter(dir + "/ta.tsv");
            taWriter.append("TIME\t\t\t\tSMA14\t\t\t\tSMA50\t\t\t\tSMA100\t\t\t\tSMA500\t\t\t\t\n").flush();
        }
    }

    private Position getPosition(){
        if (buyer.getOpenBuyOrder() != null && this.buyer.getOpenSellOrder() == null ) return Position.OB;
        else if (buyer.getOpenBuyOrder() != null && this.buyer.getOpenSellOrder() != null)  return Position.OBS;
        else if (buyer.getOpenBuyOrder() == null && this.buyer.getOpenSellOrder() != null)  return Position.OS;
        else return Position.NONE;
    }
}