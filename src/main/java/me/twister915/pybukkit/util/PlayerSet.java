package me.twister915.pybukkit.util;

import me.twister915.pybukkit.PyBukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;

import java.util.*;

/**
 * Represents a safe, and performance sensitive class for holding sets of players. This set is mutable, but makes no considerations for concurrency. This is in no way tested for thread safety.
 *
 * Players are removed on the {@code EventPriority.MONITOR} level for the {@link PlayerQuitEvent} automatically.
 *
 * The backing set is a {@link LinkedHashMap} of {@link UUID} to {@link LightweightPlayer}. Any two players with identical UUIDs are considered the same player (likely also the case in Bukkit), and will likely break this set. This behavior is not supported.
 * {@link #contains(Object)} and {@link #containsAll(Collection)} accept {@link UUID} or any descendant of {@link OfflinePlayer} as valid arguments. The final check is against the result of {@link OfflinePlayer#getUniqueId()} or the value of the UUID itself.
 *
 * If the object is finalized by the garbage collector, the subscription to the {@link PlayerQuitEvent} is unsubscribed.
  */
public class PlayerSet extends AbstractSet<Player> implements AutoQuitRemove<Player> {
    private final Map<UUID, LightweightPlayer> internalSet = new LinkedHashMap<>();
    private final PublishSubject<Player> autoQuitRemove = PublishSubject.create();
    private final Subscription playerQuitSubscription = AutoQuitRemove.observeEvent(EventPriority.MONITOR, PlayerQuitEvent.class)
            .map(PlayerEvent::getPlayer)
            .filter(this::contains)
            .subscribe((o) -> {
                autoQuitRemove.onNext(o);
                remove(o);
            });

    public PlayerSet() {
    }

    public PlayerSet(Collection<Player> players) {
        addAll(players);
    }

    @Override
    public int size() {
        return internalSet.size();
    }

    @Override
    public boolean contains(Object o) {
        UUID uuid;
        if (o instanceof OfflinePlayer)
            uuid = ((OfflinePlayer) o).getUniqueId();
        else if (o instanceof UUID)
            uuid = (UUID) o;
        else
            return false;

        return internalSet.containsKey(uuid);
    }

    @Override
    public Iterator<Player> iterator() {
        Iterator<LightweightPlayer> iterator = internalSet.values().iterator();
        return new Iterator<Player>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Player next() {
                return iterator.next().getPlayer();
            }

            @Override
            public void remove() {
                iterator.remove();
            }
        };
    }

    @Override
    public boolean add(Player player) {
        if (player == null)
            throw new NullPointerException("Cannot add a null player to the PlayerSet!");
        if (!player.isOnline())
            throw new IllegalArgumentException("Player must be online to store!");

        return !contains(player) && internalSet.put(player.getUniqueId(), new LightweightPlayer(player)) == null;
    }

    @Override
    public boolean remove(Object o) {
        if (o == null)
            throw new NullPointerException("Cannot remove null from a PlayerSet!");

        if (o instanceof OfflinePlayer)
            return internalSet.remove(((OfflinePlayer) o).getUniqueId()) != null;

        return o instanceof UUID && internalSet.remove(o) != null;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        unsubscribe();
    }

    @Override
    public Observable<Player> observeQuitRemoved() {
        return autoQuitRemove;
    }

    @Override
    public void unsubscribe() {
        playerQuitSubscription.unsubscribe();
        autoQuitRemove.onCompleted();
    }
}
