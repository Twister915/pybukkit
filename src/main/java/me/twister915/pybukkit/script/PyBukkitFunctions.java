package me.twister915.pybukkit.script;

import lombok.RequiredArgsConstructor;
import me.twister915.pybukkit.PyBukkit;
import me.twister915.pybukkit.util.DefaultValue;
import me.twister915.pybukkit.util.PythonFunction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import tech.rayline.core.command.ArgumentRequirementException;
import tech.rayline.core.command.CommandException;

@RequiredArgsConstructor
public final class PyBukkitFunctions {
    private final PyBukkit plugin;
    private final PBContext context;

//    @PythonFunction(pyNames = "disable_module")
//    public void disableModule(@Nullable String module) {
//        if (module == null) context.unsubscribe();
//        else context.unsubscribeChild(module);
//    }

    @PythonFunction(pyNames = "run_ext")
    public void runOutOfScope(String otherName) throws Exception {
        plugin.getScriptManager().invokeScript(context, otherName);
    }

    @PythonFunction(pyNames = "run_inside")
    public void runInScope(String otherFunction) throws Exception {
        context.invokeScript(new ScriptPath(otherFunction));
    }

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

    @PythonFunction(pyNames = {"exit_script"})
    public void exitScript() {
        context.unsubscribe();
    }
}
