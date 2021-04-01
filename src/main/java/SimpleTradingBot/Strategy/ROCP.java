package SimpleTradingBot.Strategy;

import SimpleTradingBot.Util.LimitedList;
import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;

import java.math.BigDecimal;
import static SimpleTradingBot.Strategy.Lib.Common.pDiff;

public class ROCP implements IStrategy {

    private String next;

    private int rocperiod;

    private int rocthreshold;

    private int roccperiod;

    private int roccthreshold;

    private LimitedList<BigDecimal> pDiffs;

    public ROCP( int rocperiod, int rocthreshold, int roccperiod, int roccthreshold ) {
        this.rocthreshold = rocthreshold;
        this.rocperiod = rocperiod;
        this.pDiffs = new LimitedList<>( this.roccperiod );
        this.roccthreshold = roccthreshold;
        this.roccperiod = roccperiod;
        this.next = this.getHeader();
    }

    public ROCP( int rocperiod, int rocthreshold  ) {
       this(rocperiod, rocthreshold, 0, 0);
    }

    @Override
    public String getNext() {
        return this.next;
    }

    @Override
    public Rule apply(TimeSeries timeSeries, int index) {
        StringBuilder builder = new StringBuilder();
        BigDecimal p = (BigDecimal) timeSeries.getBar( index - 1 ).getClosePrice().getDelegate();
        BigDecimal p1 = (BigDecimal) timeSeries.getBar( index ).getClosePrice().getDelegate();
        BigDecimal diff = pDiff( p, p1 );
        this.pDiffs.add( diff ); //TODO this wont work, as we need to move the pDiffs list to controller instead
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator( timeSeries );
        ROCIndicator rocIndicator = new ROCIndicator( closePriceIndicator, this.rocperiod);
        BigDecimal v = (BigDecimal) rocIndicator.getValue( index ).getDelegate();
        builder.append(v.toPlainString());
        Rule rule = new OverIndicatorRule( rocIndicator, this.rocthreshold);

        if ( this.roccperiod > 0 ) {
            ROCIndicator roccIndicator = new ROCIndicator( rocIndicator, this.roccperiod );
            BigDecimal v1 = (BigDecimal) roccIndicator.getValue( index ).getDelegate();
            builder.append(",").append(v1.toPlainString());
            rule = rule.and( new OverIndicatorRule( roccIndicator, this.roccthreshold ));
        }

        this.next = builder.toString();
        return rule;
    }

    private String getHeader() {
        String header = "ROC-" + this.rocperiod;
        if ( this.roccperiod > 0 ) {
            header += ",ROCC-" + this.roccperiod;
        }

        return header;
    }

    @Override
    public String toString() {

        return "ROCP: roc_period=" + this.rocperiod
                + ", roc_threshold=" + this.rocthreshold
                + ", rocc_period=" + this.roccperiod
                + ", rocc_threshold=" + this.roccthreshold;
    }
}
