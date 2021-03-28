package SimpleTradingBot.Strategy.Rule;

import java.math.BigDecimal;

import static java.math.MathContext.DECIMAL64;
import static java.math.BigDecimal.valueOf;

public class Common {

    public static BigDecimal pDiff( BigDecimal x, BigDecimal y ) {
        BigDecimal f = y.subtract( x, DECIMAL64 ).divide( x, DECIMAL64 );
        return f.multiply( valueOf( 100 ), DECIMAL64 );
    }
}
