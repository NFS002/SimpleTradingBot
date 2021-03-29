package SimpleTradingBot.Strategy;

import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import java.math.BigDecimal;

public class RSI implements IStrategy {

    private final int overBought;

    private final int overSold;

    private final int period;

    private String next;

    public RSI(int overBought, int overSold, int period) {
        this.overBought = overBought;
        this.overSold = overSold;
        this.period = period;
        this.next = this.getHeader();
    }

    public RSI() {
        this( 70, 30, 14);
    }

    private String getHeader() {
        StringBuilder builder = new StringBuilder();
        String name = this.getName();
        builder.append(name).append("-").append( this.period ).append( "-")
                .append(this.overBought).append("-").append(this.overSold).append(",");
        return builder.toString();
    }

    @Override
    public Rule apply(TimeSeries timeSeries, int index) {
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator( timeSeries );
        RSIIndicator  rsiIndicator = new RSIIndicator( closePriceIndicator, this.period );
        BigDecimal v = (BigDecimal) rsiIndicator.getValue( index ).getDelegate();
        this.next = v.toPlainString();
        return new UnderIndicatorRule( rsiIndicator, this.overSold );
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
