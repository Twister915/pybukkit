package me.twister915.pybukkit.sys;

import lombok.RequiredArgsConstructor;
import me.twister915.pybukkit.PyBukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.python.core.Py;
import org.reflections.Reflections;
import rx.Observable;
import rx.Subscription;
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

//    public Subscription registerEvent(EventPriority priority, boolean ignoreCancelled, Class<? extends Event> type, Action1 action) {
//        return registerEvent(priority, ignoreCancelled, type).subscribe(action);
//    }

    public <T extends Event> Observable<T> registerEvent(Class<? extends T> type) {
        if (type == null)
            throw Py.RuntimeError("You cannot pass None to registerEvent!");
        if (!Event.class.isAssignableFrom(type))
            throw Py.TypeError("You cannot register for an event using the type " + type.getSimpleName() + "!");

        return Observable.create(subscriber -> {
            subscriber.add(plugin.observeEvent(EventPriority.NORMAL, true, type).doOnError(plugin.getScriptManager()).subscribe(subscriber));
            subscriptions.add(subscriber);
        });
    }
//
//    public <T extends Event> Observable<T> registerEvent(EventPriority priority, Class<? extends T>... types) {
//        return registerEvent(priority, false, types);
//    }

//    public <T extends Event> Observable<T> registerEvent(Class<? extends T>... types) {
//        return registerEvent(EventPriority.NORMAL, types);
//    }
//
//    public <T extends Event> Observable<T> registerEvent(String reqType, String... types) {
//        boolean foundEventPriority = false;
//        EventPriority eventPriority;
//        try {
//            eventPriority = EventPriority.valueOf(reqType.toUpperCase());
//            foundEventPriority = true;
//        } catch (Exception e) {
//            eventPriority = EventPriority.NORMAL;
//        }
//        int offset = foundEventPriority ? 1 : 0;
//        Class[] classes = new Class[(1 - offset) + types.length];
//        for (int i = 0; i < types.length + offset; i++) {
//            String val;
//            if (i == -offset) val = reqType; //some magic there- if it's 0 then it stays 0, otherwise -1
//            else val = types[i - (1 - offset)];
//
//            Class<? extends Event> type = EVENT_TYPES_RESOLVED.get(val);
//            if (type == null)
//                throw Py.NameError(val + " is not a valid event name!");
//
//            classes[i] = type;
//        }
//
//        return registerEvent(eventPriority, classes);
//    }

    @Override
    public void disable() {
        subscriptions.unsubscribe();
    }

    @Override
    public boolean isActive() {
        return subscriptions.hasSubscriptions();
    }
}
