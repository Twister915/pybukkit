package me.twister915.pybukkit.sys;

import me.twister915.pybukkit.PyBukkit;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.python.core.PyFunction;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class CommandSystem implements SysType {
    final PyBukkit plugin;
    final Set<PluginCommand> registered = new HashSet<>();
    final CommandMap commandMap;

    public CommandSystem(PyBukkit plugin) {
        try {
            PluginManager annotation = Bukkit.getPluginManager();
            Field commandMapField = annotation.getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap)commandMapField.get(annotation);
        } catch (Exception var6) {
            throw new IllegalStateException("Could not grab CommandMap");
        }

        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void disable() {
        try {
            Field field = commandMap.getClass().getDeclaredField("knownCommands");
            field.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) field.get(commandMap);
            for (Iterator<Command> commandItr = knownCommands.values().iterator(); commandItr.hasNext(); ) {
                Command next = commandItr.next();
                if (registered.contains(next)) {
                    commandItr.remove();
                    plugin.getLogger().info("Unregistered /" + next.getName());
                }
            }
            registered.clear();
        } catch (Exception e) {
            throw new RuntimeException("Could not unregister commands!", e);
        }
    }

    public CmdFunction registerCommand(PyFunction function, Map<String, Object> meta) {
        return new CmdFunction(this, function, meta);
    }

    static final Constructor constructor;
    static {
        try {
            constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Could not create PluginCommand constructor!");
        }
    }
}
