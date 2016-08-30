package me.twister915.pybukkit.script;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import lombok.Getter;
import me.twister915.pybukkit.PyBukkit;
import me.twister915.pybukkit.sys.*;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.python.core.Py;
import org.python.core.PyObject;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.subscriptions.CompositeSubscription;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PBContext extends PyObject implements Subscription {
    private boolean unsubscribed = false;
    @Getter private boolean unsubscribing = false;
    private final Map<Class, Object> originalAttributes = new HashMap<>();
    private final ConcurrentMap<String, PyObject> attributes = new ConcurrentHashMap<>();
    private final CompositeSubscription subscription = new CompositeSubscription();
    private final Multimap<String, ActionHandler> actionHandlers = Multimaps.newMultimap(new HashMap<>(), TreeSet::new);
    @Getter private Scheduler.Worker sync, async;

    public PBContext(PyBukkit plugin) {
        sync = plugin.getSyncScheduler().createWorker();
        async = plugin.getAsyncScheduler().createWorker();
        //systems
        registerSystem(Bukkit.getServer(), "Bukkit");
        registerSystem(Bukkit.getServer().getOnlinePlayers(), "players");
        registerSystem(new EventSystem(plugin), "event");
        registerSystem(plugin, "plugin");
        registerSystem(new MongoSystem(plugin.getMongoDB()), "mongo");
        registerSystem(plugin.getScriptManager(), "script_manager");
        registerSystem(new CommandSystem(plugin), "command");
        registerSystem(new DataSystem(plugin.getGsonBridge().getGson()),"data");
        registerSystem(new SchedulerSystem(plugin), "scheduler");
    }

    public void registerAction(String name, Action0 action0, EventPriority priority) {
        actionHandlers.put(name.trim().toLowerCase(), new ActionHandler(priority.ordinal(), action0));
    }

    public void callAction(String name) {
        for (ActionHandler action0 : actionHandlers.get(name)) {
            try {
                action0.getHandler().call();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public <T> T getSystem(Class<T> type) {
        return (T) originalAttributes.get(type);
    }

    @Override
    public PyObject __findattr_ex__(String name) {
        if (isUnsubscribed())
            throw Py.SystemError("The system is currently closed!");

        return attributes.get(name);
    }

    @Override
    public void __setattr__(String name, PyObject value) {
        throw Py.TypeError("Could not set attribute on system hooks!");
    }
    void registerSystem(Object system, String... names) {
        PyObject pyObject = Py.java2py(system);

        for (String name : names)
            attributes.put(name, pyObject);

        originalAttributes.put(system.getClass(), system);
        if (system instanceof SysType)
            ((SysType) system).enable();
    }

    @Override
    public void unsubscribe() {
        if (isUnsubscribed())
            return;

        unsubscribing = true;
        originalAttributes.values().forEach(t -> {
            if (t instanceof SysType)
                ((SysType) t).disable();
        });
        callAction("unload");
        subscription.unsubscribe();
        unsubscribed = true;
    }

    public boolean isActive() {
        for (Object o : originalAttributes.values())
            if (o instanceof SysType && ((SysType) o).isActive())
                return true;

        return false;
    }

    @Override
    public boolean isUnsubscribed() {
        return unsubscribed;
    }
}
