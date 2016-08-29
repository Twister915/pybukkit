package me.twister915.pybukkit;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.twister915.pybukkit.script.ErrorHandler;
import me.twister915.pybukkit.source.ScriptSource;
import me.twister915.pybukkit.source.LocalScriptSource;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public final class PyBukkitLoadEvent extends Event implements Cancellable {
    public static final HandlerList handlers = new HandlerList();
    private final PyBukkit plugin;
    private ScriptSource scriptSource;
    private boolean cancelled;
    @Getter(AccessLevel.PACKAGE) private final ScriptSource defaultSource;
    private final Map<Class<? extends Exception>, ErrorHandler<?>> errorHandlers = new HashMap<>();

    PyBukkitLoadEvent(PyBukkit plugin) throws IOException {
        this(plugin, new LocalScriptSource(plugin));
    }

    PyBukkitLoadEvent(PyBukkit plugin, ScriptSource defaultSource) {
        this.defaultSource = defaultSource;
        this.plugin = plugin;
        scriptSource = defaultSource;
    }

    public ScriptSource getScriptSource() throws Exception {
        if (isCancelled())
            throw new IllegalStateException("The plugin load was cancelled!");

        if (scriptSource == null)
            return defaultSource;

        return scriptSource;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
