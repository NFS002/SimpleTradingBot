package SimpleTradingBot.Models;

public enum Phase {

    /* Buy occured after sell, buy order is not yet complete. */
    BUY,

    /* Buy is (partially) filled */
    HOLD,

    /* Sell occurred after buy, sell order is not yet complete */
    SELL,


    CLEAR;

    public Phase next() {
        switch ( this ) {
            case BUY: return HOLD;
            case SELL: return CLEAR;
            default: return this;
        }
    }

    public boolean isWorking() {
        return this == BUY || this == SELL;
    }

    public boolean isBuyOrHold() {
        return ( this == BUY) || ( this == HOLD );
    }
}

