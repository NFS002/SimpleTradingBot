package SimpleTradingBot.Rules;

import SimpleTradingBot.Rules.addons.ContinouslyInSlopeRule;
import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;

import java.math.BigDecimal;

public class OBVSlope implements IRule {

    private final BigDecimal minSlope;

    private final BigDecimal maxSlope;

    private final int minCount;

    private String next;

    public OBVSlope(int minSlope, int maxSlope, int minCount ) {
        this.minSlope = BigDecimal.valueOf( minSlope );
        this.maxSlope = ( maxSlope <= 0 ) ? null : BigDecimal.valueOf( maxSlope );
        this.minCount = minCount;
        this.next = this.getHeader();
    }

    public OBVSlope( ) {
        this( 70, -1, 100 );
    }


    private String getHeader(){
        String name = getName();
        return name + "-" + this.minSlope +
                "-" + this.maxSlope + "-"
                + this.minCount + ",";
    }


    @Override
    public Rule apply(TimeSeries timeSeries, int index) {
        OnBalanceVolumeIndicator obvIndicator = new OnBalanceVolumeIndicator( timeSeries );
        BigDecimal v = (BigDecimal) obvIndicator.getValue( index ).getDelegate();
        this.next = v.toPlainString() + ",";
        return new ContinouslyInSlopeRule( obvIndicator, this.minSlope, this.maxSlope, this.minCount );
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String getNext() {
        return this.next;
    }
}
