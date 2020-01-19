package SimpleTradingBot.Models;

/* Why do we need this enum >
 * We encsapulate the type of each trading asset
 * in this enum so the Trader and the Account manager
 * know what actions to take at each maintain, simply
 * by lookng at the value of this enum..
 */

public class PositionState {

    /* Indicates any immediate action the buyer needs to take */
    private Flags flags;

    /* Where we are in the order cycle */
    private Phase phase;


    public PositionState() {
        this.phase = Phase.CLEAR;
        this.flags = Flags.NONE;;
    }

    public void maintain(Phase state, Flags flags ) {
        this.phase = state;
        this.flags = flags;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public void setFlags(Flags flags) {
        this.flags = flags;
    }

    public Phase getPhase() {
        return phase;
    }

    public Flags getFlags() {
        return this.flags;
    }

    public boolean isBuyOrHold()  {
       return this.phase.isBuyOrHold();
    }


    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + this.phase + "|" + this.flags + "]";
    }

    public String toShortString() {
        char t = this.phase.toString().charAt( 0 );
        char f = this.flags.toString().charAt( 0 );
        return "[" + t + "|" + f + "]";

    }

    /* Describes the current stage in
     * the order cycle of the asset. */
    public enum Phase {

        /* Both buy and sell complete (or null), available funds */
        CLEAR,

        /* Buy occured after sell, buy order is not yet complete. */
        BUY,

        /* Buy is (partially) filled */
        HOLD,

        /* Sell occurred after buy, sell order is not yet complete */
        SELL;

        public  boolean isClean() {
            return ( this == HOLD || this == CLEAR );
        }

        public boolean isBuyOrHold()  {
            return ( this == BUY || this == HOLD );
        }

    }

    /* Describes an action that should be taken by the buyer
    at its next maintain, or before opening another order */
    public enum Flags {


        NONE,

        UPDATE,

        CANCEL,

        REVERT,

        RESTART;
    }
}

