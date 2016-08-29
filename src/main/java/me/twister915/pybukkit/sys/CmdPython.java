package me.twister915.pybukkit.sys;

import org.python.core.PyObject;
import org.python.core.PySequence;
import org.python.core.PyString;
import tech.rayline.core.command.RDCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class CmdPython extends CmdBase {
    protected CmdPython(CommandSystem system,String name) {
        super(name, system);
    }

    protected CmdPython(CommandSystem system, String name, RDCommand... subCommands) {
        super(name, system, subCommands);
        pluginCommand = getOrCreate(getName());
    }

    public void register(Map<String, PyObject> meta) {
        PyObject desc = meta.get("desc");
        if (desc != null) pluginCommand.setDescription(desc.asString());

        PyObject usage = meta.get("usage");
        if (usage != null) pluginCommand.setUsage(usage.asString());

        List<String> aliases = new ArrayList<>();
        PyObject pyAliases = meta.get("aliases");
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
}
