package me.twister915.pybukkit.sys;

import lombok.Data;
import rx.functions.Action0;

@Data public final class ActionHandler implements Comparable<ActionHandler> {
    private final int priority;
    private final Action0 handler;

    @Override
    public int compareTo(ActionHandler o) {
        int i = priority - o.priority;
        if (i == 0)
            return 1;
        return i;
    }
}
