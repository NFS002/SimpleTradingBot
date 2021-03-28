package SimpleTradingBot.Strategy;

import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;

import java.math.BigDecimal;


public class MACD implements IStrategy {


    private final int longPeriod;

    private final int shortPeriod;

    private final int signalPeriod;

    private String next;

    public MACD( int shortPeriod, int longPeriod, int signalPeriod ) {
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
        this.signalPeriod = signalPeriod;
        this.next = this.getHeader();
    }

    public MACD( ) {
        this( 12, 26, 9 );
    }

    private String getHeader() {
        StringBuilder builder = new StringBuilder();
        String name = this.getName();
        builder.append( name ).append("-").append( this.shortPeriod )
                .append("-").append( this.longPeriod ).append(",");
        builder.append( name ).append("s-").append( this.signalPeriod ).append(",");
        return builder.toString();
    }

    @Override
    public Rule apply(TimeSeries timeSeries, int index) {
        StringBuilder builder = new StringBuilder();

        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator( timeSeries );
        MACDIndicator macdIndicator = new MACDIndicator( closePriceIndicator, this.shortPeriod, this.longPeriod );
        BigDecimal v0 = (BigDecimal) macdIndicator.getValue( index ).getDelegate();
        builder.append( v0.toPlainString() ).append(",");
        EMAIndicator macdsIndicator = new EMAIndicator( macdIndicator, this.signalPeriod );
        BigDecimal v1 = (BigDecimal) macdsIndicator.getValue( index ).getDelegate();
        builder.append( v1.toPlainString() ).append(",");
        this.next = builder.toString();
        return new OverIndicatorRule( macdIndicator, macdsIndicator );
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
