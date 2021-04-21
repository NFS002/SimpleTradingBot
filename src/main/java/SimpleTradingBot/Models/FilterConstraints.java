package SimpleTradingBot.Models;

import SimpleTradingBot.Exception.STBException;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerStatistics;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class FilterConstraints {

    private final BigDecimal MIN_QTY;

    private final BigDecimal MAX_QTY;

    private final BigDecimal STEP;

    private final BigDecimal MIN_NOTIONAL;

    private final String BASE_ASSET;

    private final String QUOTE_ASSET;

    private final int BASE_PRECISION;

    private final String SYMBOL;

    FilterConstraints( BigDecimal MIN_QTY, BigDecimal MAX_QTY, BigDecimal STEP, BigDecimal MIN_NOTIONAL, String BASE_ASSET, String QUOTE_ASSET, String SYMBOL,
                       int BASE_PRECISION ) {
        this.MIN_QTY = MIN_QTY;
        this.MAX_QTY = MAX_QTY;
        this.STEP = STEP;
        this.MIN_NOTIONAL = MIN_NOTIONAL;
        this.BASE_ASSET = BASE_ASSET;
        this.QUOTE_ASSET = QUOTE_ASSET;
        this.SYMBOL = SYMBOL;
        this.BASE_PRECISION = BASE_PRECISION;

    }

    public BigDecimal getMIN_QTY() {
        return MIN_QTY;
    }

    public BigDecimal getMAX_QTY() {
        return MAX_QTY;
    }

    public BigDecimal getSTEP() {
        return STEP;
    }

    public BigDecimal getMIN_NOTIONAL() {
        return MIN_NOTIONAL;
    }

    public int getBasePrecision() {
        return BASE_PRECISION;
    }

    public String getBASE_ASSET() {
        return BASE_ASSET;
    }

    public String getQUOTE_ASSET() {
        return QUOTE_ASSET;
    }

    public String getSYMBOL() {
        return SYMBOL;
    }

    public BigDecimal adjustQty( BigDecimal proposedQty ) {
        if ( proposedQty.compareTo( this.MIN_QTY  ) < 1 )
            return this.MIN_QTY;
        else if ( proposedQty.compareTo( this.MAX_QTY ) >= 0 )
            return this.MAX_QTY;
        else if (this.STEP.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal adjusted = this.MIN_QTY;
            while (adjusted.compareTo(proposedQty) < 1)
                adjusted = adjusted.add(this.STEP);
            return adjusted.subtract(this.STEP);
        }
        else {
            return proposedQty;
        }
    }

    public static HashMap<String, FilterConstraints> getConstraints(ExchangeInfo exchangeInfo, List<TickerStatistics> statistics )
        throws STBException {
        HashMap<String, FilterConstraints> constraintsMap = new HashMap<>();
        for ( TickerStatistics statistic : statistics ) {
            String symbol = statistic.getSymbol();
            SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo( symbol );
            String quote = symbolInfo.getQuoteAsset();
            String base = symbolInfo.getBaseAsset();
            int basePrecision = symbolInfo.getBaseAssetPrecision();
            Optional<SymbolFilter> mktLotFilterOpt = symbolInfo.getFilters().stream().filter(f -> (f.getFilterType() == FilterType.LOT_SIZE) ).findFirst();
            mktLotFilterOpt.orElseThrow( () -> new STBException( 40 ));
            SymbolFilter mktLotFilter = mktLotFilterOpt.get();

            BigDecimal minQty = new BigDecimal( mktLotFilter.getMinQty() );
            BigDecimal maxQty = new BigDecimal( mktLotFilter.getMaxQty() );
            BigDecimal qtyStep = new BigDecimal( mktLotFilter.getStepSize() );

            SymbolFilter minNotFilter = symbolInfo.getSymbolFilter( FilterType.MIN_NOTIONAL );

            BigDecimal minNotional = new BigDecimal( minNotFilter.getMinNotional() );

            FilterConstraints filterConstraints = new FilterConstraints( minQty, maxQty, qtyStep, minNotional, base, quote, symbol, basePrecision );
            constraintsMap.put( symbol, filterConstraints);
        }

        return constraintsMap;
    }
}
