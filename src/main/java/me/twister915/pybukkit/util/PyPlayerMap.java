package me.twister915.pybukkit.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.python.core.Py;
import org.python.core.PyIterator;
import org.python.core.PyObject;
import org.python.core.PyString;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;

import java.util.*;

/**
 * Represents a safe, performant, map of {@code Player -> E}. This shares many mechanics with the {@link PlayerSet}. If there is a failure to explain anything in this javadoc, please refer to that javadoc as well. Some mechanics are different, however.
 *
 * There are two backing maps, both of which are straight {@link HashMap}s. One {@link Map} is used to associate {@code UUID -> LightweightPlayer} and another is used to associate a {@link LightweightPlayer} with an instance of {@code E}
 *
 * Players are automatically removed in the same way as {@link PlayerSet}s. Using the {@link PlayerQuitEvent} on priority {@code EventPriority.MONITOR}. Players who are in the map are removed.
 *
 * You may asked to be notified when a player is removed automatically (via the quit event) by using the {@link AutoQuitRemove#observeQuitRemoved()} method.
 * @param <E> The type players are mapped to
 */
public class PyPlayerMap<E extends PyObject> extends PyObject  {
    private final InternalPlayerMap<E> internal;

    public PyPlayerMap() {
        internal = new InternalPlayerMap<>();
    }

    public PyPlayerMap(Map<Player, E> data) {
        this();
        internal.putAll(data);
    }

    @Override
    public PyObject __finditem__(PyObject key) {
        return internal.get(key.__tojava__(Player.class));
    }

    @Override
    public int __len__() {
        return internal.size();
    }

    @Override
    public boolean __contains__(PyObject o) {
        return internal.containsValue(o) || internal.containsKey(o.__tojava__(Player.class));
    }

    @Override
    public void __setitem__(PyObject key, PyObject value) {
        internal.put((Player) key.__tojava__(Player.class), (E) value);
    }

    @Override
    public void __delitem__(PyObject key) {
        internal.remove(key.__tojava__(Player.class));
    }

    @Override
    public boolean __nonzero__() {
        return internal.size() != 0;
    }

    @Override
    public PyObject __ne__(PyObject other) {
        return __eq__(other).__invert__();
    }

    @Override
    public PyObject __eq__(PyObject other) {
        if (!(other instanceof PyPlayerMap))
            return Py.False;

        return internal.equals(((PyPlayerMap) other).internal) ? Py.True : Py.False;
    }

    @Override
    public PyObject __iter__() {
        Iterator<Player> iterator = internal.keySet().iterator();
        return new PyIterator() {
            @Override
            public PyObject __iternext__() {
                return !iterator.hasNext() ? null : Py.java2py(iterator.next());
            }
        };
    }

    @Override
    public PyString __repr__() {
        return PyString.fromInterned(internal.toString().intern());
    }

    private static class InternalPlayerMap<E extends PyObject> extends AbstractMap<Player, E> implements AutoQuitRemove<Map.Entry<Player, E>> {
        private final HashMap<UUID, LightweightPlayer> uuidToPlayers = new HashMap<>();
        private final HashMap<String, LightweightPlayer> namesToPlayers = new HashMap<>();
        private final HashMap<LightweightPlayer, E> elements = new HashMap<>();
        private final PublishSubject<Entry<Player, E>> autoRemoveObs = PublishSubject.create();
        private final Subscription quitSubscription = AutoQuitRemove.observeEvent(EventPriority.MONITOR, PlayerQuitEvent.class)
                .map(PlayerEvent::getPlayer)
                .filter(this::containsKey)
                .subscribe(player -> {
                    E remove = remove(player);
                    autoRemoveObs.onNext(new AbstractMap.SimpleEntry<>(player, remove));
                });

        private LightweightPlayer getBestForObject(Object o) {
            if (o instanceof OfflinePlayer)
                return uuidToPlayers.get(((OfflinePlayer) o).getUniqueId());
            if (o instanceof UUID)
                return uuidToPlayers.get(o);
            if (o instanceof String)
                return namesToPlayers.get(o);
            return null;
        }

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public boolean containsKey(Object key) {
            return uuidToPlayers.containsValue(getBestForObject(key));
        }

        @Override
        public boolean containsValue(Object value) {
            return elements.containsValue(value);
        }

        @Override
        public E get(Object key) {
            return elements.get(getBestForObject(key));
        }

        @SuppressWarnings("unchecked")
        @Override
        public E put(Player key, E v) {
            if (!key.isOnline())
                throw new IllegalArgumentException("Player must be online to store!");
            LightweightPlayer lightweightPlayer = new LightweightPlayer(key);
            uuidToPlayers.put(key.getUniqueId(), lightweightPlayer);
            namesToPlayers.put(key.getName(), lightweightPlayer);
            return elements.put(lightweightPlayer, v);
        }

        @Override
        public E remove(Object key) {
            UUID uuid;
            if (key instanceof OfflinePlayer)
                uuid = ((OfflinePlayer) key).getUniqueId();
            else if (key instanceof UUID)
                uuid = (UUID) key;
            else throw new IllegalArgumentException("Please pass a player to the remove method!");
            LightweightPlayer remove = uuidToPlayers.remove(uuid);
            if (remove != null) {
                namesToPlayers.remove(uuid );
                return elements.remove(remove);
            }
            return null;
        }

        @Override
        public void clear() {
            elements.clear();
            uuidToPlayers.clear();
        }

        @Override
        public Set<Player> keySet() {
            final Set<LightweightPlayer> uuids = elements.keySet();
            return new AbstractSet<Player>() {
                @Override
                public Iterator<Player> iterator() {
                    Iterator<LightweightPlayer> iterator = uuids.iterator();
                    return new Iterator<Player>() {
                        private Player current;

                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public Player next() {
                            return (current = iterator.next().getPlayer());
                        }

                        @Override
                        public void remove() {
                            uuidToPlayers.remove(current.getUniqueId());
                            iterator.remove();
                        }
                    };
                }

                @Override
                public int size() {
                    return uuids.size();
                }
            };
        }

        @Override
        public Collection<E> values() {
            return elements.values();
        }

        @Override
        public Set<Entry<Player, E>> entrySet() {
            final Set<Entry<LightweightPlayer, E>> entries = elements.entrySet();
            return new AbstractSet<Entry<Player, E>>() {
                @Override
                public Iterator<Entry<Player, E>> iterator() {
                    Iterator<Entry<LightweightPlayer, E>> iterator = entries.iterator();
                    return new Iterator<Entry<Player, E>>() {
                        Player currPlayer;

                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public Entry<Player, E> next() {
                            Entry<LightweightPlayer, E> next = iterator.next();
                            currPlayer = next.getKey().getPlayer();
                            return new AbstractMap.SimpleEntry<Player, E>(currPlayer, next.getValue()) {
                                @Override
                                public E setValue(E value) {
                                    return next.setValue(value);
                                }
                            };
                        }

                        @Override
                        public void remove() {
                            iterator.remove();
                            uuidToPlayers.remove(currPlayer.getUniqueId());
                        }
                    };
                }

                @Override
                public int size() {
                    return entries.size();
                }
            };
        }

        @Override
        public Observable<Entry<Player, E>> observeQuitRemoved() {
            return autoRemoveObs;
        }

        @Override
        public void unsubscribe() {
            quitSubscription.unsubscribe();
            autoRemoveObs.onCompleted();
        }

    }


}
