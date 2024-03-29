package SimpleTradingBot.Strategy;

import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;

import java.math.BigDecimal;

public class ROCP implements IStrategy {

    private String next;

    private final int rocperiod;

    private final int rocthreshold;

    private final int roccperiod;

    private final int roccthreshold;

    public ROCP( int rocperiod, int rocthreshold  ) {
        this(rocperiod, rocthreshold, 0, 0);
    }

    public ROCP( int rocperiod, int rocthreshold, int roccperiod, int roccthreshold ) {
        this.rocthreshold = rocthreshold;
        this.rocperiod = rocperiod;
        this.roccthreshold = roccthreshold;
        this.roccperiod = roccperiod;
        this.next = this.getHeader();
    }

    @Override
    public String getNext() {
        return this.next;
    }

    @Override
    public Rule apply(TimeSeries timeSeries, int index) {

        if (index > 0) {
            StringBuilder builder = new StringBuilder();
            ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(timeSeries);
            ROCIndicator rocIndicator = new ROCIndicator(closePriceIndicator, this.rocperiod);
            BigDecimal v = (BigDecimal) rocIndicator.getValue(index).getDelegate();
            builder.append(v.toPlainString());
            Rule rule = new OverIndicatorRule(rocIndicator, this.rocthreshold);

            if (this.roccperiod > 0) {
                ROCIndicator roccIndicator = new ROCIndicator(rocIndicator, this.roccperiod);
                BigDecimal v1 = (BigDecimal) roccIndicator.getValue(index).getDelegate();
                builder.append(",").append(v1.toPlainString());
                rule = rule.and(new OverIndicatorRule(roccIndicator, this.roccthreshold));
            }

            this.next = builder.toString();
            return rule;
        }

        return (i, tr) -> false;
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
