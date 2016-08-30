package me.twister915.pybukkit.source;

import rx.Observable;
import rx.Single;

import java.io.File;
import java.io.IOException;

public interface ScriptSource {
    default void enable() {}
    File getRoot() throws IOException;
    Observable<Void> watchUpdate();
}
