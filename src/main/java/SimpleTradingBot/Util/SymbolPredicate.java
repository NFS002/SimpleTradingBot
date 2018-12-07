package SimpleTradingBot.Util;
import SimpleTradingBot.Config.Config;
import com.binance.api.client.domain.market.TickerStatistics;

import java.util.Arrays;

public class SymbolPredicate implements java.util.function.Predicate<TickerStatistics> {
    @Override
    public boolean test(TickerStatistics binanceSymbol) {
        String symbol = binanceSymbol.getSymbol();
        String base = symbol.substring(symbol.length() - 3);
        return !Arrays.asList(Config.baseAssets).contains(base);
    }
}
