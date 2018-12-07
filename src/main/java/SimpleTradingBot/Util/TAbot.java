package SimpleTradingBot.Util;

import org.ta4j.core.TimeSeries;

import java.io.PrintWriter;
import java.time.ZonedDateTime;

public interface TAbot {

    boolean doTA(TimeSeries timeSeries, PrintWriter writer);
}
