package SimpleTradingBot.Strategy.Rule;

import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import static SimpleTradingBot.Strategy.Rule.Common.pDiff;

public class ContinouslyInSlopeRule implements Rule {

    private Indicator<Num> indicator;

    private BigDecimal minSlope;

    private BigDecimal maxSlope;

    private int minCount;

    public ContinouslyInSlopeRule(Indicator<Num> indicator, BigDecimal minSlope, BigDecimal maxSlope, int minCount ) {
        this.indicator = indicator;
        this.minSlope = minSlope;
        this.maxSlope = maxSlope;
        this.minCount = minCount;
    }

    @Override
    public boolean isSatisfied( int index, TradingRecord t ) {
        int min = ( this.minCount <= 0 || this.minCount > index - 2 ) ? index - 2 : this.minCount;
        int count = 0;
        boolean sat = false;
        for ( int i = index; i >= 0; i--) {
            sat = this.isSubSatisfied( i );

            if ( sat ) count += 1;
            else break;

            if ( count >= min )
                break;
        }
        return sat;
    }

    private boolean isSubSatisfied( int index ) {

        int i0 = Math.max( 0, index );
        int i1 = Math.max( 0, index - 1 );
        BigDecimal v0 = (BigDecimal) this.indicator.getValue( i0 ).getDelegate();
        BigDecimal v1 = (BigDecimal) this.indicator.getValue( i1 ).getDelegate();
        BigDecimal pDiff = pDiff( v0, v1 );
        boolean minSlopeSatisfied = pDiff.compareTo( this.minSlope ) >= 0;
        boolean maxSlopeSatisfied = this.maxSlope == null || pDiff.compareTo(this.maxSlope) <= 0;
        return minSlopeSatisfied && maxSlopeSatisfied;
    }
}
