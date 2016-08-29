package me.twister915.pybukkit.source;

import me.twister915.pybukkit.script.ScriptPath;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.util.FileUtil;
import org.python.core.util.StringUtil;
import org.python.core.util.importer;
import org.python.util.PythonInterpreter;

import java.io.IOException;
import java.io.InputStream;

public final class ScriptSourceImporter extends PyObject {
    private final ScriptSource source;

    public ScriptSourceImporter(ScriptSource source) {
        this.source = source;
    }

    @Override
    public PyObject __findattr_ex__(String name) {
        try {
            if (!source.exists(new ScriptPath(name)))
                return Py.None;
            RunnableScript script = source.loadScript(new ScriptPath(name));
        } catch (IOException e) {
            throw Py.IOError(e);
        }
        return super.__findattr_ex__(name);
    }

    //    @Override
//    public String get_data(String s) {
//        try {
//            return StringUtil.fromBytes(FileUtil.readBytes(source.loadScript(new ScriptPath(s))));
//        } catch (IOException e) {
//            throw Py.IOError(e);
//        }
//    }
//
//    @Override
//    protected String getSeparator() {
//        return "/";
//    }
//
//    @Override
//    protected String makePackagePath(String s) {
//        return null;
//    }
//
//    @Override
//    protected String makeFilename(String s) {
//        return s + ".py";
//    }
//
//    @Override
//    protected String makeFilePath(String s) {
//        return s;
//    }
//
//    @Override
//    protected String makeEntry(String s) {
//        return s;
//    }
//
//    @Override
//    protected importer.Bundle makeBundle(String s, String s2) {
//        InputStream stream;
//        try {
//            stream = source.loadScript(new ScriptPath(s));
//        } catch (IOException e) {
//            throw Py.IOError(e);
//        }
//        return new importer.Bundle(stream) {
//            @Override
//            public void close() {
//                try {
//                    stream.close();
//                } catch (IOException e) {
//                    throw Py.IOError(e);
//                }
//            }
//        };
//    }
//
//    @Override
//    protected long getSourceMtime(String s) {
//        return 0;
//    }
}
