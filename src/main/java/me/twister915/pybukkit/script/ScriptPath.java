package me.twister915.pybukkit.script;

import com.google.common.base.Joiner;

import java.util.Arrays;

public final class ScriptPath {
    public static final ScriptPath ROOT = new ScriptPath(new String[]{});

    private final String[] path;
    private final int hashCode;

    public ScriptPath(String path) {
        if (path.endsWith("/"))
            path += "__init__";
        if (path.contains("/"))
            this.path = path.split("/");
        else this.path = new String[]{path};
        hashCode = Arrays.hashCode(this.path);
    }

    private ScriptPath(String[] path) {
        this.path = path;
        hashCode = Arrays.hashCode(path);
    }

    public int getLength() {
        return path.length;
    }

    public String[] getPath() {
        return Arrays.copyOf(path, path.length);
    }

    public ScriptPath getParent() {
        return getParent(1);
    }

    public ScriptPath getParent(int level) {
        if (path.length < level)
            throw new IllegalArgumentException("The deepest level possible is " + (path.length - 1));
        String[] parentParts = new String[path.length - level];
        System.arraycopy(path, 0, parentParts, 0, parentParts.length);
        return new ScriptPath(parentParts);
    }

    public ScriptPath append(ScriptPath childPath) {
        String[] path = childPath.path;
        String[] newParts = new String[this.path.length + path.length];
        System.arraycopy(this.path, 0, newParts, 0, this.path.length);
        System.arraycopy(path, 0, newParts, this.path.length, path.length);
        return new ScriptPath(newParts);
    }

    public ScriptPath append(String childPath) {
        return append(new ScriptPath(childPath));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ScriptPath && (obj == this || Arrays.deepEquals(((ScriptPath) obj).path, path));
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return join('/');
    }

    public String join(char join) {
        return join(String.valueOf(join)); //this is what joiner does anyway
    }

    public String join(String join) {
        return Joiner.on(join).join(path);
    }

    public String getTail() {
        return path[path.length - 1];
    }
}
