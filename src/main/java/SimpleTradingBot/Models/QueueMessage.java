package SimpleTradingBot.Models;

import java.math.BigDecimal;

public class QueueMessage implements Comparable<QueueMessage> {

    private final String symbol;

    private BigDecimal requestedQty;

    public QueueMessage( String symbol) {
        this.symbol = symbol;
    }

    public void setRequest( BigDecimal requestedQty ) {
        this.requestedQty = requestedQty;
    }

    public BigDecimal getRequested() {
        return this.requestedQty;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public int compareTo( QueueMessage queueMessage ) {
        return (this.getSymbol().equals("*")) ? 1 : 0;
    }

}