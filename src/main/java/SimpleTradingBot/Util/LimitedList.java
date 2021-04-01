package SimpleTradingBot.Util;

import java.util.LinkedList;

public class LimitedList<E> extends LinkedList<E> {
    private static final long serialVersionUID = -23456691722L;

    private final int limit;

    public LimitedList(int limit) {
        this.limit = limit;
    }

    @Override
    public boolean add(E o) {
        super.add(o);
        while (size() > this.limit) { super.remove(); }
        return true;
    }

}