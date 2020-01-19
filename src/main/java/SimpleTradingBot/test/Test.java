package SimpleTradingBot.test;

import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MInteger;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.PrecisionNum;

import java.time.ZonedDateTime;

public class Test {

    private static final int LOOKBACK = 3;
    private static final double EXTRA_VALUE = 10;
    private static Core core = new Core();
    private static MInteger outBeginIndex = new MInteger();
    private static MInteger outLength = new MInteger();

    private static MInteger outBeginIndex2 = new MInteger();
    private static MInteger outLength2 = new MInteger();

    private static double[] values;
    private static double[] values2;

    private static double[] result;
    private static double[] result2;

    public static void main(String[] args) {
        TimeSeries series = new BaseTimeSeries();
        for ( int i = 5; i > 0; i-- ) {
            ZonedDateTime dateTime = ZonedDateTime.now();
            double open = i;
            double high = i;
            double low = i;
            double close = i;
            double volume  = i;
            series.addBar(new BaseBar(dateTime, open, high, low, close, volume, PrecisionNum::valueOf ));
        }
        ZonedDateTime dateTime = ZonedDateTime.now();
        series.addBar(new BaseBar(dateTime, 10, 10, 10, 10, 10, PrecisionNum::valueOf ));
        dateTime = ZonedDateTime.now();
        series.addBar(new BaseBar(dateTime, 5, 5, 5, 5, 5, PrecisionNum::valueOf ));


        ClosePriceIndicator indicator = new ClosePriceIndicator( series );
        EMAIndicator emaIndicator = new EMAIndicator(indicator, 3);
        for ( int i = 0; i < series.getBarData().size(); i++ ) {
            System.out.print( indicator.getValue(i) + "->" );
        }
        System.out.println();
        for ( int i = 0; i < series.getBarData().size(); i++ ) {
            System.out.print( emaIndicator.getValue(i) + "->" );
        }
        System.out.println();
    }
}
