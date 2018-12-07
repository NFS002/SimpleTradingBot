package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.TimeSeries;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;

public class PumpMonitor {

    private final TickerStatistics statistics;

    private PrintWriter writer;

    private final TimeSeries series;



    PumpMonitor(TickerStatistics statistics, TimeSeries series) throws IOException {
        this.statistics = statistics;
        this.series = series;
        initLog();
    }

    private void initLog() throws IOException {
        File file = new File(Config.outDir + "pump.tsv");
        writer = new PrintWriter(new FileWriter(file));
        writer.append("Time\t\t\tSymbol\t\t\tPC(5)\t\t\tPC(10)\t\t\tPC(15)\t\t\tPC(25)\t\t\tPC(35)\t\t\tPC(50)\t\t\t\n");
        writer.flush();
    }

    public void logPump() {
        int endIndex = series.getEndIndex();
        double current = series.getBar(endIndex).getClosePrice().doubleValue();
        ZonedDateTime zonedtime = series.getLastBar().getBeginTime();
        String time = Static.timeFormatter.format(zonedtime);

        double diff5 = current- (series.getBar(endIndex - 5).getClosePrice()).doubleValue();
        double pc5 = (diff5/current) * 100;

        double diff10 = current- (series.getBar(endIndex - 10).getClosePrice()).doubleValue();
        double pc10 = (diff10/current) * 100;

        double diff15 = current- (series.getBar(endIndex - 15).getClosePrice()).doubleValue();
        double pc15 = (diff15/current) * 100;

        double diff25 = current- (series.getBar(endIndex - 25).getClosePrice()).doubleValue();
        double pc25 = (diff25/current) * 100;

        double diff35 = current- (series.getBar(endIndex - 35).getClosePrice()).doubleValue();
        double pc35 = (diff35/current) * 100;

        double diff50 = current- (series.getBar(endIndex - 50).getClosePrice()).doubleValue();
        double pc50 = (diff50/current) * 100;

        StringBuilder builder = new StringBuilder();
        builder.append(time)
                .append("\t\t\t")
                .append(Static.df.format(pc5))
                .append("\t\t\t")
                .append(Static.df.format(pc10))
                .append("\t\t\t")
                .append(Static.df.format(pc15))
                .append("\t\t\t")
                .append(Static.df.format(pc25))
                .append("\t\t\t")
                .append(Static.df.format(pc35))
                .append("\t\t\t")
                .append(Static.df.format(pc50))
                .append("\n");

        String msg = builder.toString();
        sendEmail(msg);
        writer.append(msg).flush();;
    }

    private void sendEmail(String msg) {

    }
}
