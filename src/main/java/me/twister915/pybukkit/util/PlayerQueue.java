package me.twister915.pybukkit.util;

import me.twister915.pybukkit.PyBukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.subjects.PublishSubject;

import java.util.*;

/**
 * A queue which preserves order, removes on quit, and does not allow duplicate players
 */
public class PlayerQueue extends AbstractQueue<Player> implements AutoQuitRemove<Player> {
    private final Queue<LightweightPlayer> backing;
    private final PublishSubject<Player> autoRemoveObs = PublishSubject.create();
    private final Subscription quitSubscription = AutoQuitRemove.observeEvent(EventPriority.MONITOR, PlayerQuitEvent.class)
            .map(PlayerEvent::getPlayer)
            .filter(this::contains)
            .subscribe(player -> {
                if (remove(player))
                    autoRemoveObs.onNext(player);
            });

    public PlayerQueue() {
        this(new LinkedList<>());
    }

    public PlayerQueue(Queue<LightweightPlayer> backing) {
        this.backing = backing;
    }

    @Override
    public Iterator<Player> iterator() {
        Iterator<LightweightPlayer> iterator = backing.iterator();
        return new Iterator<Player>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Player next() {
                return getPlayerFrom(iterator::next, this::remove);
            }

            @Override
            public void remove() {
                iterator.remove();
            }
        };
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean offer(Player player) {
        //we return true if it already contains the player because the boolean signifies if we're full or not
        return contains(player) || backing.offer(new LightweightPlayer(player));
    }

    @Override
    public Player poll() {
        return getPlayerFrom(backing::poll, () -> {});
    }

    @Override
    public Player peek() {
        return getPlayerFrom(backing::peek, this::remove);
    }

    @SuppressWarnings({"StatementWithEmptyBody", "OptionalGetWithoutIsPresent"})
    private static Player getPlayerFrom(Func0<LightweightPlayer> func, Action0 onInvalid) {
        Player p = null;
        while (p == null) {
            LightweightPlayer call = func.call();
            if (call == null) {
                onInvalid.call();
                continue;
            }

            Optional<Player> playerSafe = call.getPlayerSafe();
            if (playerSafe.isPresent())
                p = playerSafe.get();
            else
                onInvalid.call();
        }
        return p;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        unsubscribe();
    }

    @Override
    public void unsubscribe() {
        quitSubscription.unsubscribe();
        autoRemoveObs.onCompleted();
    }
}
