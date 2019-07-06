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

    /* A change in flags */
    private boolean maintained;

    /* The flags/state is outdated, we need to maintain it */
    private boolean outdated;

    private boolean logged;

    public PositionState() {
        this.type = Type.CLEAR;
        this.flags = Flags.NONE;;
        this.maintained = this.outdated = false;
    }

    public void maintain( Type state, Flags flags ) {
        this.maintained = ( flags != Flags.NONE) && ( flags != this.flags );
        this.outdated = false;
        this.type = state;
        this.flags = flags;
    }

    public boolean getMaintained() {
        return maintained;
    }

    public boolean isOutdated() {
        return outdated;
    }

    public void setAsOutdated( ) {
        this.outdated = true;
        this.maintained = false;
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
        return !this.maintained && !this.outdated;
    }

    public boolean isLogged() {
        return logged;
    }

    public void setLogged( ) {
        this.logged = true;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[ maintained=" + maintained + ", outdated=" + outdated + ", phase=" + type + ", flags=" + flags + "]";
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

        STABLE,
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

