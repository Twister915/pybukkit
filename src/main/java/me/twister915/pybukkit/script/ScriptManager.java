package me.twister915.pybukkit.script;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import lombok.Getter;
import me.twister915.pybukkit.PyBukkit;
import me.twister915.pybukkit.PyBukkitLoadEvent;
import me.twister915.pybukkit.source.RunnableScript;
import me.twister915.pybukkit.source.ScriptSource;
import me.twister915.pybukkit.util.ActionWrapper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.python.core.Options;
import org.python.core.Py;
import org.python.core.PyFrame;
import org.python.util.PythonInterpreter;
import rx.functions.Action1;
import tech.rayline.core.util.RunnableShorthand;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ScriptManager implements ScriptOwner, Action1<Throwable> {
    static {
        Options.includeJavaStackInExceptions = true;
        Options.respectJavaAccessibility = false;
        Options.showJavaExceptions = true;
        Options.verbose = 2;
        Options.dont_write_bytecode = true;
    }

    private final PyBukkit plugin;
    @Getter private final ScriptSource scriptSource;
    @Getter private final ScriptPath containingPath = ScriptPath.ROOT;
    private final Map<Class<? extends Exception>, ErrorHandler<?>> errorHandlers = new HashMap<>();

    private ScriptOwner index;
    private ScriptPath indexPath;

    public ScriptManager(PyBukkitLoadEvent event) throws Exception {
        this.plugin = event.getPlugin();
        this.scriptSource = event.getScriptSource();
        this.errorHandlers.putAll(event.getErrorHandlers());
    }

    public void invokeScript(PBContext parent, String id) throws Exception {
        invokeScript(parent.getContainingPath().append(id), parent);
    }

    public void invokeIndex() {
        invokeScriptRetry(containingPath.append(plugin.getConfig().getString("__init__", "__init__")), this);
    }

    void invokeScript(ScriptPath path, ScriptOwner owner) throws Exception {
        PythonInterpreter pythonInterpreter = new PythonInterpreter();
        pythonInterpreter.setOut(new PythonConsole("&aOUT"));
        pythonInterpreter.setErr(new PythonConsole("&cERROR"));
        PBContext pbContext = new PBContext(plugin, owner, path, pythonInterpreter);
        pythonInterpreter.set("pyb", pbContext);
        ActionWrapper.injectMethods(new PyBukkitFunctions(plugin, pbContext), pythonInterpreter);
        try {
            invokeScript(path, pythonInterpreter);
            if (!pbContext.isUnsubscribed())
                owner.addChild(path, pbContext);
            else
                pbContext.unsubscribe();
        } catch (Exception e) {
            if (!pbContext.isUnsubscribed())
                pbContext.unsubscribe();
            throw e;
        }
    }

    void invokeScript(ScriptPath path, PythonInterpreter pythonInterpreter) throws Exception {
        if (!scriptSource.exists(path))
            throw new IOException("Could not find script " + path);

        plugin.getLogger().info("Running script " + path + "!");
        RunnableScript runnableScript = scriptSource.loadScript(path);
        try (InputStream stream = runnableScript.getScript()) {
            pythonInterpreter.execfile(stream, path.toString());
        }
    }

    void invokeScriptRetry(ScriptPath path, ScriptOwner owner) {
        doAndRetry("invoke script " + path, 5, TimeUnit.SECONDS, () -> {
            invokeScript(path, owner);
        });
    }

    @SuppressWarnings("unchecked")
    public <T extends Exception> void handle(T exception) {
        Class<? extends Exception> aClass = exception.getClass();
        for (Map.Entry<Class<? extends Exception>, ErrorHandler<?>> handlerEntry : errorHandlers.entrySet()) {
            if (!handlerEntry.getKey().isAssignableFrom(aClass))
                continue;
            ((ErrorHandler<? super T>) handlerEntry.getValue()).handle(exception);
        }
    }

    @Override
    public void call(Throwable throwable) {
        if (!(throwable instanceof Exception))
            return;
        handle((Exception) throwable);
    }

    public interface Action0Ex {
        void call() throws Exception;
    }

    public void doAndRetry(String name, long time, TimeUnit timeUnit, Action0Ex action) {
        try {
            action.call();
        } catch (Exception e) {
            e.printStackTrace();
            plugin.getLogger().warning("Retrying " + name + "in " + time + " " + timeUnit.name().toLowerCase() + "!");
            RunnableShorthand.forPlugin(plugin).with(() -> doAndRetry(name, time, timeUnit, action)).later(timeUnit.toMillis(time) / 50);
        }
    }

    public boolean killScript(ScriptPath path) {
        ScriptOwner scriptOwner = getChild(path);
        if (scriptOwner == null)
            return false;

        scriptOwner.unsubscribe();
        return true;
    }

    public void onDisable() {
        unsubscribe();
    }

    @Override
    public void addChild(ScriptPath path, ScriptOwner other) {
        if (index != null && !index.isUnsubscribed())
            throw new IllegalArgumentException("The index script is the only script ScriptManager can be responsible for!");

        index = other;
        indexPath = path;
    }

    @Override
    public BiMap<ScriptPath, ? extends ScriptOwner> getChildren() {
        if (indexPath == null || index == null)
            return ImmutableBiMap.of();

        return ImmutableBiMap.of(indexPath, index);
    }

    @Override
    public void unsubscribe() {
        if (isUnsubscribed())
            return;
        index.unsubscribe();
    }

    @Override
    public boolean isUnsubscribed() {
        return index.isUnsubscribed();
    }

    public void reload() throws Exception {
        unsubscribe();
        invokeIndex();
    }

    private static final class PythonConsole extends StringWriter {
        private final String prefix;

        private PythonConsole(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void flush() {
            StringBuffer buffer = getBuffer();
            while (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) == '\n')
                buffer.deleteCharAt(buffer.length() - 1);

            String s = buffer.toString().trim();
            if (s.length() == 0)
                return;

            PyFrame frame = Py.getThreadState().frame;
            int getline;
            String name;
            if (frame != null) {
                getline = frame.getline();
                name = frame.f_code.co_filename;
            } else {
                getline = -1;
                name = "?";
            }

            String[] split = s.split("\n");
            for (int i = 0; i < split.length; i++) {
                String message = ChatColor.translateAlternateColorCodes('&', String.format(
                        "&7[&6PyBukkit&7:&6%s &7- %s&7:%d - %d]: &f%s", prefix, name, getline, (i + 1), split[i].trim()
                ));
                Bukkit.getConsoleSender().sendMessage(message);
                Bukkit.broadcast(message, "pybukkit.admin");
            }

            buffer.setLength(0);
        }

        @Override
        public void close() throws IOException {}
    }
}
