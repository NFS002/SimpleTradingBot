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
    private Type type;


    /* A change in flags or maintanace is required */
    private Visibility visibility;



    public PositionState() {
        this.type = Type.CLEAR;
        this.flags = Flags.NONE;;
        this.visibility = Visibility.STABLE;
    }

    public void maintain( Type state, Flags flags ) {
        if ( ( flags != Flags.NONE) && ( flags != this.flags ) )
            this.visibility = Visibility.UPDATED;
        else
            this.visibility = Visibility.STABLE;
        this.type = state;
        this.flags = flags;
    }

    public boolean getMaintained() {
        return this.visibility == Visibility.UPDATED;
    }

    public boolean isOutdated() {
        return this.visibility == Visibility.OUTDATED;
    }

    public void setAsOutdated( ) {
        this.visibility = Visibility.OUTDATED;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public Flags getFlags() {
        return flags;
    }

    public boolean isClean( ) {
        return ( type.isClean() || flags == Flags.NONE );
    }

    public boolean isUpdated( ) {
        return this.visibility == Visibility.UPDATED;
    }


    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + this.type + ", " + this.flags + "," + this.visibility + "]";
    }

    /* Describes the current stage in
     * the order cycle of the asset. */
    public enum Type {

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

    }

    /* Describes the visibility level of the position state,
    * i.e if it is actually representative of the state of the buyer  */
    public enum Visibility {

        OUTDATED,

        UPDATED,

        STABLE
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

