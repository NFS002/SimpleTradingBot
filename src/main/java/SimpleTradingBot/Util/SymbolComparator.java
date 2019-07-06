package SimpleTradingBot.Util;
import com.binance.api.client.domain.market.TickerStatistics;

import java.util.Comparator;

public class SymbolComparator implements Comparator<TickerStatistics> {

    @Override
    public int compare(TickerStatistics s1, TickerStatistics s2) {
        return (int) Math.round(Double.parseDouble(s2.getPriceChangePercent()) - Double.parseDouble(s1.getPriceChangePercent()));
    }

    @Override
    public String toString() {
        return "PRICE_CHANGE_PERCENT";
    }
}