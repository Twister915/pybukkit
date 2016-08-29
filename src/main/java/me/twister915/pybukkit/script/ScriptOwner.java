package me.twister915.pybukkit.script;

import com.google.common.collect.BiMap;
import rx.Subscription;

import java.util.Iterator;

public interface ScriptOwner extends Subscription {
    void addChild(ScriptPath path, ScriptOwner other);
    BiMap<ScriptPath, ? extends ScriptOwner> getChildren();

    ScriptPath getContainingPath();
    default ScriptOwner getChild(ScriptPath path) {
        cleanChildren();

        BiMap<ScriptPath, ? extends ScriptOwner> children = getChildren();
        ScriptOwner scriptOwner = children.get(path);
        if (scriptOwner != null)
            return scriptOwner;

        for (ScriptOwner owner : children.values()) {
            ScriptOwner child = owner.getChild(path);
            if (child != null)
                return child;
        }

        return null;
    }

    default void cleanChildren() {
        for (Iterator<? extends ScriptOwner> ownerIterator = getChildren().values().iterator(); ownerIterator.hasNext(); )
            if (ownerIterator.next().isUnsubscribed())
                ownerIterator.remove();
    }
}
