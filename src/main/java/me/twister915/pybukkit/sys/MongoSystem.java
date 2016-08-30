package me.twister915.pybukkit.sys;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.client.MongoDatabase;
import me.twister915.pybukkit.util.ActionWrapper;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyObject;
import org.python.core.PyString;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class MongoSystem extends PyObject implements SysType {
    private final DB database;
    private final PyObject wrapped;

    public MongoSystem(DB database) {
        this.database = database;
        wrapped = Py.java2py(database);
    }

    @Override
    public PyObject __call__(PyObject[] args, String[] keywords) {
        return wrapped.__call__(args, keywords);
    }

    @Override
    public PyObject __findattr_ex__(String name) {
        return Py.java2py(database.getCollection(name));
    }

    @Override
    public PyObject __finditem__(PyObject key) {
        if (key instanceof PyString)
            return __findattr_ex__(key.asString());
        else
            throw Py.TypeError("You need to pass a string to the mongo object!");
    }
}
