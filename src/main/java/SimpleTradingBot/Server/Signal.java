package SimpleTradingBot.Server;

import java.math.BigDecimal;

public class Signal {

    private String symbol;

    private String type;

    private int weight;

    private String source;

    private long created;

    private boolean deleted;

    private String openPrice;

    public Signal() { }

    public Signal( String symbol, String type, int weight, String source, long created, boolean deleted, String openPrice) {
        this.symbol = symbol;
        this.type = type;
        this.source = source;
        this.created = created;
        this.deleted = deleted;
        this.openPrice = openPrice;
        this.weight = weight;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(String openPrice) {
        this.openPrice = openPrice;
    }

    @Override
    public String toString() {

        return this.getClass().getSimpleName() + "[symbol="
                + this.getSymbol()
                + ", source="
                + this.getSource()
                + ", type="
                + this.getType()
                + ", weight="
                + this.getWeight()
                + ", price="
                + this.getOpenPrice()
                + ", created="
                + this.getCreated()
                + "]";

    }
}
