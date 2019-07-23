package SimpleTradingBot.Util;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Controller.Controller;
import SimpleTradingBot.Services.HeartBeat;
import com.binance.api.client.domain.OrderType;

import java.math.BigDecimal;
import java.util.List;

public class OrderRequest {

    private final String symbol;

    private final BigDecimal openPrice;

    private final OrderType orderType;

    private int weight;

    private long executedAt;

    public OrderRequest( String symbol, BigDecimal openPrice, OrderType orderType, int weight ) {
        this.symbol = symbol;
        this.openPrice = openPrice;
        this.orderType = orderType;
        this.weight = weight;
        this.executedAt = -1;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public long getExecutedAt() {
        return executedAt;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
