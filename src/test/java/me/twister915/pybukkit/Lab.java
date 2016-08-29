package me.twister915.pybukkit;

import me.twister915.pybukkit.util.ActionWrapper;
import org.junit.Test;
import org.python.util.PythonInterpreter;
import rx.functions.Action1;
import rx.functions.Func1;

import static org.junit.Assert.assertEquals;

public class Lab {
    @Test
    public void testMethodPassing() {
        PythonInterpreter pythonInterpreter = new PythonInterpreter();
        pythonInterpreter.set("test_lol", new ActionWrapper(new Action1<Func1<Integer, String>>() {
            @Override
            public void call(Func1<Integer, String> integerStringFunc1) {
                assertEquals(integerStringFunc1.call(500), "1000");
            }
        }));
        pythonInterpreter.exec("test_lol(lambda x: str(x * 2))");
    }
}
