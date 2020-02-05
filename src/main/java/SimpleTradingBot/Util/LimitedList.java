package SimpleTradingBot.Util;

import java.util.LinkedList;
import java.util.List;

public class LimitedList<E> extends LinkedList<E> {
    private static final long serialVersionUID = -23456691722L;
    private final int limit;

    public LimitedList(int limit) {
        this.limit = limit;
    }

    @Override
    public boolean add(E object) {
        if (this.size() > limit)
            shiftDownOne();
        return super.add(object);
    }

    private void shiftDownOne() {
        int s = super.size();
        List<E> sublist = new LinkedList<>(super.subList(1,s));
        super.clear();
        super.addAll( sublist );
    }

}