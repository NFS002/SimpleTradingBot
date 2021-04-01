package SimpleTradingBot.Strategy;

import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;

public interface IStrategy {

    Rule apply( TimeSeries timeSeries, int index );

    String getNext( );

}
