package SimpleTradingBot.Rules;

import SimpleTradingBot.Rules.addons.SMACrossConfirm;
import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.math.BigDecimal;

import static SimpleTradingBot.Rules.addons.SMACrossConfirm.confirm;


public class SMACross implements IRule {

    private int confirmPeriod;

    private int[] smaPeriods;

    private String next;

    @Override
    public String getNext() {
        return this.next;
    }

    public SMACross( int p0, int p1, int confirmPeriod ) {
        this.confirmPeriod = confirmPeriod;
        this.smaPeriods = new int[2];
        this.smaPeriods[0] = p0;
        this.smaPeriods[1] = p1;
        this.next = this.getHeader( );
    }

    public SMACross( int p0, int p1 ) {
        this.confirmPeriod = 0;
        this.smaPeriods = new int[2];
        this.smaPeriods[0] = p0;
        this.smaPeriods[1] = p1;
        this.next = this.getHeader();
    }

    private String getHeader() {
        StringBuilder builder = new StringBuilder();
        String name = getName();
        for ( int p : this.smaPeriods)
            builder.append( name ).append("-").append( p ).append(",");
        if ( this.confirmPeriod > 0 ) {
            builder.append(SMACrossConfirm.getName()).append("-")
                    .append(this.confirmPeriod).append(",");
        }
        return builder.toString();
    }

    @Override
    public Rule apply( TimeSeries timeSeries, int index ) {

        StringBuilder builder = new StringBuilder();
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator( timeSeries );
        int t = this.smaPeriods[ 0 ];
        int t1 = this.smaPeriods[ 1 ];

        SMAIndicator sma = new SMAIndicator(closePriceIndicator, t );
        SMAIndicator sma1 = new SMAIndicator(closePriceIndicator, t1 );

        BigDecimal smaV = (BigDecimal) sma.getValue( index ).getDelegate();
        builder.append( smaV.toPlainString() ).append(",");

        BigDecimal smaV1 = (BigDecimal) sma1.getValue( index ).getDelegate();
        builder.append( smaV1.toPlainString() ).append(",");

        Rule rule;
        if ( this.confirmPeriod > 0 ) {
            boolean confirmed = confirm(sma, sma1, this.confirmPeriod, index );
            builder.append(confirmed).append(",");
            rule = ( (i, tR) -> confirmed );
        }
        else {
            rule = ( (i, tR) -> sma.getValue(index).isGreaterThan(sma1.getValue(index)) );
        }
        this.next = builder.toString();
        return rule;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
