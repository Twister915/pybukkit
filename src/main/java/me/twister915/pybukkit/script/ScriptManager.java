package me.twister915.pybukkit.script;

import lombok.Getter;
import me.twister915.pybukkit.PyBukkit;
import me.twister915.pybukkit.PyBukkitLoadEvent;
import me.twister915.pybukkit.source.ScriptSource;
import me.twister915.pybukkit.util.ActionWrapper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.python.core.Options;
import org.python.core.Py;
import org.python.core.PyFrame;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;
import rx.functions.Action1;
import tech.rayline.core.util.RunnableShorthand;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ScriptManager implements Action1<Throwable> {
    static {
        Options.includeJavaStackInExceptions = true;
        Options.respectJavaAccessibility = false;
        Options.showJavaExceptions = true;
        Options.dont_write_bytecode = true;
    }

    private final PyBukkit plugin;
    @Getter private final ScriptSource scriptSource;
    private final Map<Class<? extends Exception>, ErrorHandler<?>> errorHandlers = new HashMap<>();
    private PythonInterpreter pythonInterpreter;
    @Getter private PBContext currentContext = null;


    public ScriptManager(PyBukkitLoadEvent event) throws Exception {
        this.plugin = event.getPlugin();
        this.scriptSource = event.getScriptSource();
        this.errorHandlers.putAll(event.getErrorHandlers());
        getScriptSource().enable();
    }

    private void invokeIndex0() throws IOException {
        pythonInterpreter = new PythonInterpreter(null, new PySystemState());
        pythonInterpreter.setOut(new PythonConsole("&aOUT"));
        pythonInterpreter.setErr(new PythonConsole("&cERROR"));
        pythonInterpreter.getSystemState().path.append(Py.java2py(getScriptSource().getRoot().getAbsolutePath()));
        currentContext = new PBContext(plugin);
        pythonInterpreter.getSystemState().getBuiltins().__setitem__("pyb", currentContext);
        ActionWrapper.injectMethods(new PyBukkitFunctions(plugin, currentContext), pythonInterpreter);
        File init = new File(getScriptSource().getRoot(), "__init__.py");
        try (FileInputStream stream = new FileInputStream(init)) {
            pythonInterpreter.execfile(stream, init.getName());
            currentContext.callAction("load");
        }
        getScriptSource().watchUpdate().debounce(5, TimeUnit.SECONDS).take(1).observeOn(plugin.getSyncScheduler()).subscribe(update -> {
            doAndRetry("reload", 5, TimeUnit.SECONDS, this::reload);
        });
    }

    public void invokeIndex() {
        doAndRetry("invoke script", 5, TimeUnit.SECONDS, this::invokeIndex0);
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
            unsubscribe();
            e.printStackTrace();
            plugin.getLogger().warning("Retrying " + name + " in " + time + " " + timeUnit.name().toLowerCase() + "!");
            RunnableShorthand.forPlugin(plugin).with(() -> doAndRetry(name, time, timeUnit, action)).later(timeUnit.toMillis(time) / 50);
        }
    }

    public void unsubscribe() {
        if (currentContext == null)
            return;

        currentContext.unsubscribe();
        currentContext = null;

        pythonInterpreter = null;
    }

    public void reload() throws Exception {
        unsubscribe();
        invokeIndex0();
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

            s = ChatColor.stripColor(s);
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
