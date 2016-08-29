package me.twister915.pybukkit.sys;

import org.bukkit.command.CommandSender;
import org.python.core.*;
import tech.rayline.core.command.CommandException;
import tech.rayline.core.command.PermissionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CmdFunction extends CmdBase {
    private final PyFunction function;
    private String permission;

    protected CmdFunction(CommandSystem system, PyFunction function, Map<String, Object> meta) {
        super(getName(function, meta), system);
        if (getName() == null)
            throw new IllegalArgumentException("Invalid state. Must specify a name using {name : 'name'}");

        this.function = function;

        pluginCommand = getOrCreate(getName());

        PyObject desc = get("description", function, meta);
        if (desc != null) pluginCommand.setDescription(desc.asString());

        PyObject usage = get("usage", function, meta);
        if (usage != null) pluginCommand.setUsage(usage.asString());

        PyObject permission = get("permission", function, meta);
        if (permission != null) this.permission = permission.asString();

        List<String> aliases = new ArrayList<>();
        PyObject pyAliases = get("aliases", function, meta);
        if (pyAliases != null && pyAliases instanceof PySequence)
            for (PyObject pyObject : pyAliases.asIterable())
                if (pyObject instanceof PyString)
                    aliases.add(pyObject.asString());
        pluginCommand.setAliases(aliases);

        pluginCommand.setExecutor(this);
        pluginCommand.setTabCompleter(this);

        setPlugin(system.plugin);
        system.plugin.getLogger().info("Registered /" + getName());
    }

    @Override
    protected void checkPermission(CommandSender sender) throws PermissionException {
        if (permission != null && !sender.hasPermission(permission))
            throw new PermissionException("No permission!");
        super.checkPermission(sender);
    }

    private static String getName(PyFunction function, Map<String, Object> meta) {
        if (meta.containsKey("name")) {
            Object name = meta.get("name");
            if (name instanceof PyString)
                return ((PyString) name).asString();
            if (name instanceof String)
                return (String) name;
            return name.toString();
        }
        PyObject name = function.__findattr__("name");
        if (name != null) {
            String s = name.asStringOrNull();
            if (s != null)
                return s;
        }
        return function.__name__;
    }

    private static PyObject get(String key, PyFunction function, Map<String, Object> meta) {
        if (meta.containsKey(key))
            return Py.java2py(meta.get(key));

        try {
            return Py.java2py(function.__getattr__(key));
        } catch (PyException e) {
            return null;
        }
    }


    @Override
    protected void handleCommandUnspecific(CommandSender sender, String[] args) throws CommandException {
        function.__call__(Py.javas2pys(sender, args));
    }
}
