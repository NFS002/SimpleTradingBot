package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Util.Static;
import SimpleTradingBot.Util.TAbot;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;

public class TAanalyser implements TAbot {
    public boolean doTA(TimeSeries timeSeries, PrintWriter writer) {
        int endIndex = timeSeries.getEndIndex();
        ZonedDateTime zonedtime = timeSeries.getLastBar().getBeginTime();
        String time = Static.timeFormatter.format(zonedtime);
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(timeSeries);

        SMAIndicator sma14 = new SMAIndicator(closePriceIndicator,14);
        Decimal sma14Value = sma14.getValue(endIndex);

        SMAIndicator sma50 = new SMAIndicator(closePriceIndicator,50);
        Decimal sma50Value = sma50.getValue(endIndex);

        SMAIndicator sma100 = new SMAIndicator(closePriceIndicator,100);
        Decimal sma100Value = sma100.getValue(endIndex);

        SMAIndicator sma500 = new SMAIndicator(closePriceIndicator,500);
        Decimal sma500Value = sma500.getValue(endIndex);

        OverIndicatorRule sma14x50 = new OverIndicatorRule(sma50,sma14);
        OverIndicatorRule sma50x100 = new OverIndicatorRule(sma100,sma50);
        OverIndicatorRule sma100x500 = new OverIndicatorRule(sma500,sma100);

        Rule sma14x50x100x500 = sma14x50.and(sma50x100).and(sma100x500);
        if (Config.logTA) {
            logTA(writer, time, sma14Value, sma50Value, sma100Value, sma500Value);
        }
        return sma14x50x100x500.isSatisfied(endIndex);
    }

    private void logTA(PrintWriter writer, String time, Decimal sma14Value, Decimal sma50Value, Decimal sma100Value, Decimal sma500Value) {
        writer.append(time);
        writer.append("\t\t\t\t");
        writer.append(Static.df.format(sma14Value));
        writer.append("\t\t\t\t");
        writer.append(Static.df.format(sma50Value));
        writer.append("\t\t\t\t");
        writer.append(Static.df.format(sma100Value));
        writer.append("\t\t\t\t");
        writer.append(Static.df.format(sma500Value));
        writer.append("\n").flush();
    }

    private static double round(double value) {
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

}
