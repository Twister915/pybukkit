package me.twister915.pybukkit.cmd;

import me.twister915.pybukkit.PyBukkit;
import org.bukkit.command.CommandSender;
import tech.rayline.core.command.CommandException;
import tech.rayline.core.command.RDCommand;
import tech.rayline.core.command.UnhandledCommandExceptionException;

public final class ReloadCommand extends RDCommand {
    public ReloadCommand() {
        super("reload");
    }

    @Override
    protected void handleCommandUnspecific(CommandSender sender, String[] args) throws CommandException {
        try {
            ((PyBukkit) getPlugin()).getScriptManager().reload();
        } catch (Exception e) {
            throw new UnhandledCommandExceptionException(e);
        }
    }
}
