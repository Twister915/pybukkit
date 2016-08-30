package me.twister915.pybukkit.util;

import lombok.Data;

@Data public final class PrioritizedEntry<E> {
    private final E elem;
    private final int priority;
}
