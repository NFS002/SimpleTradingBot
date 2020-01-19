package SimpleTradingBot.Util;

import SimpleTradingBot.Config.Config;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.general.*;
import com.binance.api.client.domain.market.TickerStatistics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import static SimpleTradingBot.Util.Static.getQuoteFromSymbol;

public class SymbolPredicate implements java.util.function.Predicate<TickerStatistics> {

    private final ExchangeInfo exchangeInfo;

    public SymbolPredicate(ExchangeInfo exchangeInfo ) {
        this.exchangeInfo = exchangeInfo;
    }

    @Override
    public boolean test( TickerStatistics statistics ) {
        String symbol = statistics.getSymbol();

        /* Volume */
        BigDecimal volume = new BigDecimal( statistics.getVolume() );
        if ( volume.compareTo( new BigDecimal( Config.MIN_VOLUME ) ) <= 0)
            return true;

        /* Symbol Status */
        SymbolInfo info = this.exchangeInfo.getSymbolInfo( symbol );
        SymbolStatus symbolStatus = info.getStatus();
        if (symbolStatus != SymbolStatus.TRADING)
            return true;

        /* Min price change percent */
        SymbolFilter f = info.getSymbolFilter( FilterType.PRICE_FILTER );
        if ( !this.minPriceChangePercent( f ) )
            return true;

        /* Quote Asset */
        String quote = getQuoteFromSymbol( symbol );
        if ( !quote.equals( Config.QUOTE_ASSET ) )
            return true;

        /* Order types */
        List<OrderType> orderTypes = info.getOrderTypes();
        return !orderTypes.contains(OrderType.MARKET);
    }

    private boolean minPriceChangePercent(SymbolFilter f ) {
        BigDecimal minPrice = new BigDecimal( f.getMinPrice() );
        BigDecimal maxPrice = new BigDecimal( f.getMaxPrice() );
        if ( minPrice.compareTo(BigDecimal.ZERO) <= 0
            || maxPrice.compareTo(BigDecimal.ZERO) <= 0)
            return false;
        BigDecimal range = maxPrice.subtract( minPrice, MathContext.DECIMAL64 );
        BigDecimal tickSize = new BigDecimal( f.getTickSize() );
        BigDecimal tickSizePercent = tickSize.divide( range, MathContext.DECIMAL64 ).multiply( new BigDecimal( "100" ), MathContext.DECIMAL64);
        return tickSizePercent.compareTo( Config.MAX_PRICE_CHANGE_PERCENT) < 0;
    }

    @Override
    public String toString() {
        return "Min volume: " + Config.MIN_VOLUME
                + ", "
                + SymbolStatus.TRADING
                + ", "
                + OrderType.MARKET;
    }
}
