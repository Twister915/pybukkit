package me.twister915.pybukkit.util;

import rx.Subscription;

import java.util.*;
import java.util.Map.Entry;

@SuppressWarnings({"unchecked", "unused"})
public class PriorityMap<K, V> {
    private final Map<K, PriorityQueue<PrioritizedEntry<V>>> backingMap;
    private final Comparator<PrioritizedEntry> comparator = (v1, v2) -> v2.getPriority() - v1.getPriority();
    private final Subscription autoRemoveSub;

    public PriorityMap(Map<K, PriorityQueue<PrioritizedEntry<V>>> backingMap) {
        this.backingMap = backingMap;
        if (backingMap instanceof AutoQuitRemove)
            autoRemoveSub = ((AutoQuitRemove<Entry<K, PriorityQueue<PrioritizedEntry<V>>>>) backingMap).
                    observeQuitRemoved().subscribe(entry -> {
                onRemove(entry.getKey());
            });
        else
            autoRemoveSub = null;
    }

    public int size() {
        return backingMap.size();
    }

    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    public boolean containsKey(K key) {
        return backingMap.containsKey(key);
    }

    public boolean containsValue(V value) {
        return values().contains(value);
    }

    public boolean containsEntry(K key, int priority) {
        PriorityQueue<PrioritizedEntry<V>> queue = getQueue(key, false);
        if (queue == null)
            return false;
        for (PrioritizedEntry<V> aQueue : queue)
            if (aQueue.getPriority() == priority)
                return true;
        return false;
    }

    public boolean containsEntry(K key, V value) {
        PriorityQueue<PrioritizedEntry<V>> queue = getQueue(key, false);
        if (queue == null)
            return false;
        for (PrioritizedEntry<V> vPrioritizedEntry : queue)
            if (vPrioritizedEntry.getElem().equals(value))
                return true;
        return false;
    }

    private PriorityQueue<PrioritizedEntry<V>> getQueue(K key, boolean put) {
        PriorityQueue<PrioritizedEntry<V>> vs = backingMap.get(key);
        if (vs == null && put) {
            vs = comparator == null ? new PriorityQueue<>() : new PriorityQueue<>(comparator);
            backingMap.put(key, vs);
        }
        return vs;
    }

    public V get(K key) {
        PriorityQueue<PrioritizedEntry<V>> vs = getQueue(key, false);
        return vs == null ? null : unbox(vs.peek());
    }

    public V get(K key, int priority) {
        PriorityQueue<PrioritizedEntry<V>> queue = getQueue(key, false);
        if (queue == null)
            return null;
        for (PrioritizedEntry<V> vPrioritizedEntry : queue)
            if (vPrioritizedEntry.getPriority() == priority)
                return unbox(vPrioritizedEntry);
        return null;
    }

    public V put(K key, V value, int priority) {
        remove(key, priority, false); //do not allow clean because we're going to at least have one when we're done here (replacement)
        getQueue(key, true).add(new PrioritizedEntry<>(value, priority));
        return null;
    }

    public V remove(K key) {
        PriorityQueue<PrioritizedEntry<V>> remove = backingMap.remove(key);
        if (remove != null && key != null) {
            onRemove(key);
        }
        return null;
    }

    protected void onRemove(K key) {}

    public void clear(K key) {
        remove(key);
    }

    public boolean remove(K key, V value) {
        PriorityQueue<PrioritizedEntry<V>> queue = getQueue(key, false);
        if (queue == null)
            return false;

        boolean didAnything = false;
        Iterator<PrioritizedEntry<V>> iterator = queue.iterator();
        while (iterator.hasNext())
            if (iterator.next().equals(value)) {
                iterator.remove();
                didAnything = true;
            }
        cleanPotentialEmpty(key);
        return didAnything;
    }

    public boolean remove(K k, int priority) {
        return remove(k, priority, true);
    }

    private boolean remove(K k, int priority, boolean allowClean) {
        PriorityQueue<PrioritizedEntry<V>> queue = getQueue(k, false);
        if (queue == null) return false;
        Iterator<PrioritizedEntry<V>> iterator = queue.iterator();
        while (iterator.hasNext())
            if (iterator.next().getPriority() == priority) {
                iterator.remove();
                if (allowClean)
                    cleanPotentialEmpty(k);
                return true;
            }
        return false;
    }

    private void cleanPotentialEmpty(K key) {
        PriorityQueue<PrioritizedEntry<V>> queue = getQueue(key, false);
        if (queue != null && queue.size() == 0)
            remove(key);
    }

    public boolean remove(int priority) {
        boolean didAnything = false;
        for (K k : keySet())
            didAnything = remove(k, priority) || didAnything;

        return didAnything;
    }

    public void clear() {
        for (Entry<K, V> kvEntry : entrySet())
            onRemove(kvEntry.getKey());

        backingMap.clear();
    }

    public Set<K> keySet() {
        return backingMap.keySet();
    }

    public Collection<V> values() {
        return new AbstractCollection<V>() {
            public Iterator<V> iterator() {
                Iterator<PriorityQueue<PrioritizedEntry<V>>> iterator = backingMap.values().iterator();
                return new Iterator<V>() {
                    private PriorityQueue<PrioritizedEntry<V>> current;

                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    public V next() {
                        current = iterator.next();
                        return unbox(current.peek());
                    }

                    public void remove() {
                        current.remove();
                    }
                };
            }

            public int size() {
                return backingMap.size();
            }
        };
    }

    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Entry<K, V>>() {
            public Iterator<Entry<K, V>> iterator() {
                Iterator<K> k = keySet().iterator();
                Iterator<V> v = values().iterator();
                return new Iterator<Entry<K, V>>() {
                    public boolean hasNext() {
                        return k.hasNext() && v.hasNext();
                    }

                    public Entry<K, V> next() {
                        return new AbstractMap.SimpleImmutableEntry<>(k.next(), v.next());
                    }

                    public void remove() {
                        k.remove();
                    }
                };
            }

            public int size() {
                return backingMap.size();
            }
        };
    }

    protected void finalize() throws Throwable {
        super.finalize();
        if (autoRemoveSub != null && !autoRemoveSub.isUnsubscribed())
            autoRemoveSub.unsubscribe();
    }

    private static <E> E unbox(PrioritizedEntry<E> entry) {
        return entry == null ? null : entry.getElem();
    }
}
