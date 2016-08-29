package me.twister915.pybukkit.script;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.Getter;
import me.twister915.pybukkit.PyBukkit;
import me.twister915.pybukkit.sys.*;
import org.bukkit.Bukkit;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import rx.Subscription;
import rx.subscriptions.CompositeSubscription;
import tech.rayline.core.util.RunnableShorthand;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PBContext extends PyObject implements ScriptOwner {
    private boolean unsubscribed = false;
    private final PyBukkit plugin;
    @Getter private final ScriptOwner parent;
    @Getter private final ScriptPath containingPath, selfPath;
    private final Set<Object> originalAttributes = new HashSet<>();
    @Getter private final BiMap<ScriptPath, ScriptOwner> children = HashBiMap.create();
    private final PythonInterpreter interpreter;
    private final ConcurrentMap<String, PyObject> attributes = new ConcurrentHashMap<>();
    private final CompositeSubscription subscription = new CompositeSubscription();

    public PBContext(PyBukkit plugin, ScriptOwner parent, ScriptPath selfPath, PythonInterpreter interpreter) {
        this.plugin = plugin;
        this.parent = parent;
        this.selfPath = selfPath;
        watchPath(selfPath);
        this.containingPath = selfPath.getParent();
        this.interpreter = interpreter;
        //systems
        registerSystem(Bukkit.getServer(), "bukkit");
        registerSystem(new EventSystem(plugin), "event");
        registerSystem(plugin, "plugin");
        registerSystem(new MongoSystem(plugin.getMongoDB()), "mongo");
        registerSystem(plugin.getScriptManager(), "script_manager");
        registerSystem(new CommandSystem(plugin), "command");
        registerSystem(new DataSystem(plugin.getGsonBridge().getGson()),"data");
        registerSystem(new SchedulerSystem(plugin), "scheduler");
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

    public void invokeScript(ScriptPath path) throws Exception {
        ScriptManager scriptManager = plugin.getScriptManager();
        scriptManager.invokeScript(path, interpreter);
        watchPath(path);
    }

    private void watchPath(ScriptPath path) {
        subscription.add(plugin.getScriptManager().getScriptSource().watchUpdates(path).subscribe(update -> {
            unsubscribe();
            reinvoke();
        }));
    }

    private void reinvoke() {
        plugin.getScriptManager().invokeScriptRetry(selfPath, parent);
    }

    void registerSystem(Object system, String... names) {
        PyObject pyObject = Py.java2py(system);

        for (String name : names)
            attributes.put(name, pyObject);

        originalAttributes.add(system);
        if (system instanceof SysType)
            ((SysType) system).enable();
    }

    public void addChild(ScriptPath name, ScriptOwner child) {
        cleanChildren();
        children.put(name, child);
    }

    @Override
    public void unsubscribe() {
        if (isUnsubscribed())
            return;

        originalAttributes.forEach(t -> {
            if (t instanceof SysType)
                ((SysType) t).disable();
        });
        subscription.unsubscribe();
        cleanChildren();
        children.values().forEach(Subscription::unsubscribe);
        interpreter.close();
        plugin.getLogger().info("Killed script " + selfPath);
        unsubscribed = true;
    }

    public boolean isActive() {
        for (Object o : originalAttributes)
            if (o instanceof SysType && ((SysType) o).isActive())
                return true;

        cleanChildren();
        for (ScriptOwner child : children.values())
            if (!child.isUnsubscribed())
                return true;

        return false;
    }

    @Override
    public boolean isUnsubscribed() {
        return unsubscribed;
    }
}
