package me.twister915.pybukkit.sys;

import lombok.RequiredArgsConstructor;
import me.twister915.pybukkit.PyBukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.python.core.Py;
import org.reflections.Reflections;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.OnErrorNotImplementedException;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unchecked")
@RequiredArgsConstructor
public final class EventSystem implements SysType {
    private static final Map<String, Class<? extends Event>> EVENT_TYPES_RESOLVED = new HashMap<>();

    private final PyBukkit plugin;
    private final CompositeSubscription subscriptions = new CompositeSubscription();

    static {
        Reflections reflections = new Reflections("org.bukkit.event");
        Set<Class<? extends Event>> classes = reflections.getSubTypesOf(Event.class);

        for (Class<? extends Event> eventType : classes)
            EVENT_TYPES_RESOLVED.put(eventType.getSimpleName(), eventType);
    }

    public <T extends Event> Observable<T> registerEvent(Class<? extends T> type) {
        return registerEvent(EventPriority.NORMAL, type);
    }

    public <T extends Event> Observable<T> registerEvent(EventPriority priority, Class<? extends T> type) {
        return registerEvent(priority, false, type);
    }

    public <T extends Event> Observable<T> registerEvent(EventPriority priority,  boolean ignoreCancelled, Class<? extends T> type) {
        if (type == null)
            throw Py.RuntimeError("You cannot pass None to registerEvent!");
        if (!Event.class.isAssignableFrom(type))
            throw Py.TypeError("You cannot register for an event using the type " + type.getSimpleName() + "!");

        return Observable.create(subscriber -> {
            Observable<? extends T> observable = plugin.observeEvent(priority, ignoreCancelled, type);
            subscriber.add(observable.subscribe(new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    subscriber.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    plugin.getScriptManager().call(e);
                    try {
                        subscriber.onError(e);
                    } catch (OnErrorNotImplementedException e1) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onNext(T t) {
                    try {
                        subscriber.onNext(t);
                    } catch (Exception e) {
                        onError(e);
                    }
                }
            }));
            subscriptions.add(subscriber);
        });
    }

    @Override
    public void disable() {
        subscriptions.unsubscribe();
    }

    @Override
    public boolean isActive() {
        return subscriptions.hasSubscriptions();
    }
}
