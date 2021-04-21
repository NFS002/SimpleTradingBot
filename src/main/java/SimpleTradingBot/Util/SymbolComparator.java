package SimpleTradingBot.Util;
import com.binance.api.client.domain.market.TickerStatistics;

import java.util.Comparator;

public class SymbolComparator implements Comparator<TickerStatistics> {

    @Override
    public int compare(TickerStatistics s1, TickerStatistics s2) {
        int dpChange = (int) Math.round(Double.parseDouble(s2.getPriceChangePercent()) - Double.parseDouble(s1.getPriceChangePercent()));
        int dVolume = (int) Math.round(Double.parseDouble(s2.getVolume()) - Double.parseDouble(s1.getVolume()));
        return dpChange != 0 ? dpChange : dVolume;
    }
}