package me.twister915.pybukkit.sys;

public interface SysType {
    default void enable() {}
    default void disable() {}
    default boolean isActive() {return false;}
}
