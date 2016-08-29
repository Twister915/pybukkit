package me.twister915.pybukkit.cmd;

import tech.rayline.core.command.RDCommand;

public final class PyBukkitCommand extends RDCommand {
    public PyBukkitCommand() {
        super("pybukkit", new ReloadCommand());
    }

    @Override
    protected boolean isUsingSubCommandsOnly() {
        return true;
    }
}
