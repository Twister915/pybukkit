package me.twister915.pybukkit.script;

import lombok.RequiredArgsConstructor;
import me.twister915.pybukkit.PyBukkit;
import me.twister915.pybukkit.sys.EventSystem;
import me.twister915.pybukkit.util.DefaultValue;
import me.twister915.pybukkit.util.PythonFunction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.python.core.Py;
import org.python.core.PyFunction;
import org.python.core.PyObject;
import org.python.core.PyString;
import rx.Single;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import tech.rayline.core.command.ArgumentRequirementException;
import tech.rayline.core.command.CommandException;

@RequiredArgsConstructor
public final class PyBukkitFunctions {
    private final PyBukkit plugin;
    private final PBContext context;

    @PythonFunction(pyNames = {"player", "get_player"})
    public Player getPlayer(String name, @DefaultValue("false") boolean commandException) throws CommandException {
        Player player = Bukkit.getPlayer(name);
        if (player == null && commandException)
            throw new ArgumentRequirementException("Invalid player specified!");

        return player;
    }

    @PythonFunction(pyNames = {"col", "color"})
    public String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @PythonFunction(pyNames = {"event_handler"})
    public PyObject eventHandlerDec(Class<? extends Event> type, @DefaultValue("NORMAL") String priority, @DefaultValue("false") Boolean ignoreCancelled) {
        return new PyObject() {
            @Override
            public PyObject __call__(PyObject func) {
                context.getSystem(EventSystem.class)
                        .registerEvent(EventPriority.valueOf(priority), ignoreCancelled, type)
                        .subscribe(event -> func.__call__(Py.java2py(event)));
                return func;
            }
        };
    }

    @PythonFunction(pyNames = "action_handler")
    public PyObject actionHandler(String name, @DefaultValue("NORMAL") String priority) {
        return actionDec(name, priority);
    }

    @PythonFunction(pyNames = "load")
    public PyObject loadDec(@DefaultValue("NORMAL") String priority) {
        return actionDec("load", priority);
    }

    @PythonFunction(pyNames = "unload")
    public PyObject unloadDec(@DefaultValue("NORMAL") String priority) {
        return actionDec("unload", priority);
    }

    @PythonFunction(pyNames = "async_return")
    public PyObject asyncRetDec(PyFunction f) {
        return new PyObject() {
            @Override
            public PyObject __call__(PyObject[] args, String[] keywords) {
                Func0<PyObject> action0 = () -> f.__call__(args, keywords);
                if (Bukkit.isPrimaryThread())
                    return Py.java2py(Single.<PyObject>create(sub -> {
                        if (!Bukkit.isPrimaryThread() || context.isUnsubscribing())
                            sub.onSuccess(action0.call());
                        else
                            context.getAsync().schedule(() -> {
                                sub.onSuccess(action0.call());
                            });
                    }));
                else
                    return action0.call();
            }
        };
    }

    @PythonFunction(pyNames = "async")
    public PyObject asyncDec(PyFunction f) {
        return new PyObject() {
            @Override
            public PyObject __call__(PyObject[] args, String[] keywords) {
                Action0 action0 = () -> f.__call__(args, keywords);
                if (!Bukkit.isPrimaryThread() || context.isUnsubscribing())
                    action0.call();
                else
                    context.getAsync().schedule(action0);

                return Py.None;
            }
        };
    }

    private PyObject actionDec(String name, String priority) {
        return new PyObject() {
            @Override
            public PyObject __call__(PyObject arg0) {
                context.registerAction(name, (Action0) arg0.__tojava__(Action0.class), EventPriority.valueOf(priority.toUpperCase()));
                return arg0;
            }
        };
    }
}
