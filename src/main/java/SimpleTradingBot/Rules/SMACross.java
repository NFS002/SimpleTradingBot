package SimpleTradingBot.Rules;

import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;

import java.math.BigDecimal;


public class SMACross implements IRule {

    private int[] periods;

    private String next;

    @Override
    public String getNext() {
        return next;
    }

    public SMACross( int p0, int p1, int ... periods ) {
        int l = periods.length;
        this.periods = new int[l + 2];
        this.periods[0] = p0;
        this.periods[1] = p1;
        System.arraycopy(periods, 0, this.periods, 2, l);
        this.next = this.getHeader( );
    }

    private String getHeader() {
        StringBuilder builder = new StringBuilder();
        String name = getName();
        for ( int p : this.periods )
            builder.append( name ).append( p ).append(",");
        return builder.toString();
    }

    @Override
    public Rule apply( TimeSeries timeSeries, int index ) {

        int nT = this.periods.length;
        OverIndicatorRule[] rules = new OverIndicatorRule[ nT ];
        StringBuilder builder = new StringBuilder();
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator( timeSeries );

        for (int i = 0; i < nT - 1; i++) {

            int t = this.periods[ i ];
            int t2 = this.periods[ i + 1 ];

            SMAIndicator sma = new SMAIndicator(closePriceIndicator, t );

            SMAIndicator sma1 = new SMAIndicator(closePriceIndicator, t2 );

            rules[i] = new OverIndicatorRule( sma, sma1 );


            BigDecimal smaV = (BigDecimal) sma.getValue( index ).getDelegate();
            builder.append( smaV.toPlainString() ).append(",");

            if ( i == nT - 2 ) {
                BigDecimal smaV1 = (BigDecimal) sma1.getValue(index).getDelegate();
                builder.append( smaV1.toPlainString() ).append(",");
            }
        }

        this.next = builder.toString();
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
