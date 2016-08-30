package me.twister915.pybukkit.util;

import me.twister915.pybukkit.PyBukkit;
import me.twister915.pybukkit.sys.EventSystem;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import rx.Observable;

public interface AutoQuitRemove<V> {
    /**
     * Posts {@code V} instance when it is removed because of a {@link org.bukkit.event.player.PlayerQuitEvent}.
     * @return An observable which will emit items of type {@code V}
     */
    default Observable<V> observeQuitRemoved() {
        return Observable.empty();
    }

    void unsubscribe();

    static <T extends Event> Observable<T> observeEvent(EventPriority priority, Class<T> type) {
        return PyBukkit.getInstance().getScriptManager().getCurrentContext().getSystem(EventSystem.class).registerEvent(priority, type);
    }
}
