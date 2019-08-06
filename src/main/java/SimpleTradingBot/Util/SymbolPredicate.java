package SimpleTradingBot.Util;

import SimpleTradingBot.Config.Config;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.general.*;
import com.binance.api.client.domain.market.TickerStatistics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
            return false;

        /* Symbol Status */
        SymbolInfo info = this.exchangeInfo.getSymbolInfo( symbol );
        SymbolStatus symbolStatus = info.getStatus();
        if (symbolStatus != SymbolStatus.TRADING)
            return false;

        /* Base Asset */
        String quote = Static.getQuoteFromSymbol( symbol );
        if ( ! quote.equals( Config.QUOTE_ASSET) )
            return false;

        /* Order types */
        List<OrderType> orderTypes = info.getOrderTypes();
        return !orderTypes.contains( OrderType.MARKET );

    }

    public static void removeDuplicateQuotes( List<TickerStatistics> statistics ) {
        List<String> assets = new ArrayList<>();
        int nStatistics = statistics.size();
        for ( int i = 0; i < nStatistics; i++ ) {
            TickerStatistics statistic = statistics.get( i );
            String symbol = statistic.getSymbol();
            String asset = Static.getBaseFromSymbol( symbol );
            if ( assets.contains( asset ) )
                statistics.set( i, null);
            else
                assets.add( asset );
        }
        statistics.removeIf( (s) -> s == null);
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
