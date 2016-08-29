package me.twister915.pybukkit.script;

public interface ErrorHandler<BT extends Exception> {
    <T extends BT> void handle(T exception);
}
