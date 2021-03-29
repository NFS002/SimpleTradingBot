package SimpleTradingBot.Strategy;

import SimpleTradingBot.Util.LimitedList;
import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;

import java.math.BigDecimal;
import static SimpleTradingBot.Strategy.Rule.Common.pDiff;

public class ROCP implements IStrategy {

    private String next;

    private int rocperiod;

    private int rocthreshold;

    private int roccperiod;

    private int roccthreshold;

    private LimitedList<BigDecimal> pDiffs;

    public ROCP( int rocperiod, int rocthreshold, int roccperiod, int roccthreshold ) {
        this.next = this.getHeader();
        this.rocthreshold = rocthreshold;
        this.rocperiod = rocperiod;
        this.pDiffs = new LimitedList<>( this.roccperiod );
        this.roccthreshold = roccthreshold;
        this.roccperiod = roccperiod;
    }

    public ROCP( int rocperiod, int rocthreshold  ) {
        this.rocthreshold = rocthreshold;
        this.rocperiod = rocperiod;
        this.pDiffs = new LimitedList<>( this.roccperiod );
        this.roccperiod = 0;
        this.roccthreshold = 0;
        this.next = this.getHeader();
    }

    @Override
    public Rule apply(TimeSeries timeSeries, int index) {
        BigDecimal p = (BigDecimal) timeSeries.getBar( index - 1 ).getClosePrice().getDelegate();
        BigDecimal p1 = (BigDecimal) timeSeries.getBar( index ).getClosePrice().getDelegate();
        BigDecimal diff = pDiff( p, p1 );
        this.pDiffs.add( diff ); //TODO this wont work, as we need to move the pDiffs list to controller instead
        // alternatively just use ROC indicator with contionously in slope, but this is much slower
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator( timeSeries );
        ROCIndicator rocIndicator = new ROCIndicator( closePriceIndicator, this.rocperiod);
        BigDecimal v = (BigDecimal) rocIndicator.getValue( index ).getDelegate();
        this.next = v.toPlainString();
        Rule rule = new OverIndicatorRule( rocIndicator, this.rocthreshold);
        if ( this.roccperiod > 0 ) {
            ROCIndicator roccIndicator = new ROCIndicator( rocIndicator, this.roccperiod );
            BigDecimal v1 = (BigDecimal) roccIndicator.getValue( index ).getDelegate();
            this.next += v1.toPlainString();
            rule = rule.and( new OverIndicatorRule( roccIndicator, this.roccthreshold ));
        }
        return rule;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String getNext() {
        return this.next;
    }

    private String getHeader() {
        String header = this.getName() + "-" + this.rocperiod + "-" + this.rocthreshold;
        if ( this.roccperiod > 0 ) {
            header += "ROCC" + "-" + this.roccperiod + "-" + this.roccthreshold;
        }
        return header;
    }

    private void setRocthreshold(int rocthreshold) {
        this.rocthreshold = rocthreshold;
    }

    public ROCP setRocperiod(int rocperiod) {
        this.rocperiod = rocperiod;
        return this;
    }

    public ROCP setRoccperiod(int roccperiod) {
        this.roccperiod = roccperiod;
        return this;
    }

    public ROCP setRoccthreshold(int roccthreshold) {
        this.roccthreshold = roccthreshold;
        return this;
    }
}
