package SimpleTradingBot.Rules;

import SimpleTradingBot.Util.Static;
import org.ta4j.core.num.*;
import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;

import java.math.BigDecimal;


public class SMACross implements IRule {

    private int[] periods;

    public SMACross(int p0, int p1, int ... periods) {

        int l = periods.length;
        this.periods = new int[l + 2];
        System.arraycopy(periods, 0, this.periods, 2, l);
        this.periods[0] = p0;
        this.periods[1] = p1;
    }

    @Override
    public Rule apply( TimeSeries timeSeries, int index, StringBuilder builder) {

        int nT = this.periods.length;
        OverIndicatorRule[] rules = new OverIndicatorRule[ nT ];

        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator( timeSeries );

        for (int i = 0; i < nT - 1; i++) {

            int t = this.periods[ i ];
            int t2 = this.periods[ i + 1 ];

            SMAIndicator sma = new SMAIndicator(closePriceIndicator, t );

            SMAIndicator sma1 = new SMAIndicator(closePriceIndicator, t2 );

            rules[i] = new OverIndicatorRule( sma, sma1 );


            String smaV = Static.formatNum( sma.getValue( index ) );
            builder.append( t ).append(": ").append(smaV).append("\t");

            if ( i == nT - 2 ) {
                String smaV1 = Static.formatNum(sma1.getValue(index));
                builder.append(t2).append(": ").append(smaV1).append("\t");
            }

        }

        Rule masterRule = rules[0];

        for (int i = 1; i < nT -1; i++ ) {
            masterRule = masterRule.and(rules[i]);
        }

        return masterRule;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
