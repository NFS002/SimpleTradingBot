package SimpleTradingBot.Rules.addons;

import org.ta4j.core.indicators.SMAIndicator;

import java.math.BigDecimal;
import java.math.MathContext;

public class SMACrossConfirm {

    public static boolean confirm(SMAIndicator shortIndicator, SMAIndicator longIndicator, int period, int index ) {
        if ( index > period && period > 0 ) {
            BigDecimal threshold = BigDecimal.ZERO;
            for (  int i = index - period; i < index ; i++ ) {
                BigDecimal smaV = (BigDecimal) shortIndicator.getValue( i ).getDelegate();
                BigDecimal smaV1 = (BigDecimal) longIndicator.getValue( i ).getDelegate();
                boolean cross = smaV.compareTo( smaV1 ) > 0;

                BigDecimal diff = smaV.subtract( smaV1, MathContext.DECIMAL64 );
                boolean thresh = diff.compareTo( threshold ) >= 0; //TODO - differenet oprions

                if ( !( cross && thresh ) )
                    return false;
                else
                    threshold = diff;
            }
        }
        return true;
    }


   public static String getName() {
        return SMACrossConfirm.class.getSimpleName();
    }
}