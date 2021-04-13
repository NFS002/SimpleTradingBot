package SimpleTradingBot.Util;

import SimpleTradingBot.Config.Config;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.general.*;
import com.binance.api.client.domain.market.TickerStatistics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

import static SimpleTradingBot.Util.Static.getQuoteFromSymbol;
import static com.binance.api.client.domain.general.FilterType.MARKET_LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;

public class SymbolPredicate {

    private final ExchangeInfo exchangeInfo;

    public SymbolPredicate(ExchangeInfo exchangeInfo ) {
        this.exchangeInfo = exchangeInfo;
    }


    public boolean isTradable(TickerStatistics statistics ) {
        String symbol = statistics.getSymbol();

        /* Quote Asset */
        String quote = getQuoteFromSymbol( symbol );
        if ( !quote.equals( Config.QUOTE_ASSET ) )
            return false;

        /* Volume */
        BigDecimal volume = new BigDecimal( statistics.getVolume() );
        if ( volume.compareTo( new BigDecimal( Config.MIN_VOLUME ) ) <= 0)
            return false;

        /* Symbol Status */
        SymbolInfo info = this.exchangeInfo.getSymbolInfo( symbol );
        SymbolStatus symbolStatus = info.getStatus();
        if (symbolStatus != SymbolStatus.TRADING)
            return true;

        /* Min price change percent */
        SymbolFilter priceChangeFilter = info.getSymbolFilter( PRICE_FILTER );
        if ( !this.minPriceChangePercent( priceChangeFilter ) )
            return false;

        if (info.getOrderTypes().stream().noneMatch( o -> o.equals(OrderType.MARKET)))
            return false;

        if ( info.getFilters().stream().noneMatch(f -> f.getFilterType() == MARKET_LOT_SIZE) )
            return false;

        return true;
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
