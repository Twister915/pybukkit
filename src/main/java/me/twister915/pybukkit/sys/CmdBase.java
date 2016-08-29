package me.twister915.pybukkit.sys;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.python.core.PyException;
import org.python.core.PyObject;
import tech.rayline.core.command.CommandException;
import tech.rayline.core.command.RDCommand;
import tech.rayline.core.command.UnhandledCommandExceptionException;

import static me.twister915.pybukkit.sys.CommandSystem.constructor;

public abstract class CmdBase extends RDCommand {
    protected PluginCommand pluginCommand;
    final CommandSystem system;

    protected CmdBase(String name, CommandSystem system) {
        super(name);
        this.system = system;
    }

    protected CmdBase(String name, CommandSystem system, RDCommand... subCommands) {
        super(name, subCommands);
        this.system = system;
    }

    protected PluginCommand getOrCreate(String name) {
        PluginCommand pluginCommand = system.plugin.getCommand(name);
        if (pluginCommand != null) throw new IllegalStateException("You cannot register commands in your plugin.yml with Python!");
        try {
            pluginCommand = (PluginCommand) constructor.newInstance(name, system.plugin);
        } catch (Exception var7) {
            throw new IllegalStateException("Could not register " + name);
        }
        system.commandMap.register(system.plugin.getDescription().getName(), pluginCommand);
        system.registered.add(pluginCommand);
        return pluginCommand;
    }

    @Override
    protected void handleCommandException(CommandException ex, String[] args, CommandSender sender) {
        if (ex instanceof UnhandledCommandExceptionException && ((UnhandledCommandExceptionException) ex).getCausingException() instanceof PyException) {
            try {
                Object value = ((PyException) ((UnhandledCommandExceptionException) ex).getCausingException()).value.__tojava__(Throwable.class);
                if (value instanceof CommandException) {
                    super.handleCommandException((CommandException) value, args, sender);
                    return;
                }
            } catch (Exception ignored) {}
        }
        super.handleCommandException(ex, args, sender);
    }
}
