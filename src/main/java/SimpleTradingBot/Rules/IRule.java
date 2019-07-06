package SimpleTradingBot.Rules;

import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;

public interface IRule {

    Rule apply( TimeSeries timeSeries, int index, StringBuilder builder );

    String getName( );
}
