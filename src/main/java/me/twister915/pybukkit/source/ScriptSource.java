package me.twister915.pybukkit.source;

import me.twister915.pybukkit.script.ScriptPath;
import org.python.util.PythonInterpreter;
import rx.Single;

import java.io.IOException;
import java.io.InputStream;

public interface ScriptSource {
    RunnableScript loadScript(ScriptPath path) throws IOException;
    Single<Void> watchUpdates(ScriptPath path);
    boolean exists(ScriptPath path) throws IOException;
}
