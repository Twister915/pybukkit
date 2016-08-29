package me.twister915.pybukkit.util;

import org.python.core.Py;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import javax.annotation.Nullable;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class ActionWrapper extends PyObject {
    private final Object instance;
    private final Method method;
    private final Map<String, Integer> paramsByName;

    public static void injectMethods(Object source, PythonInterpreter interpreter) {
        injectMethods(source.getClass(), source, interpreter);
    }

    public static void injectStaticMethods(Class<?> source, PythonInterpreter interpreter) {
        injectMethods(source, null, interpreter);
    }

    private static void injectMethods(Class<?> source, Object inst, PythonInterpreter interpreter) {
        for (Method method : source.getDeclaredMethods()) {
            PythonFunction annotation = method.getAnnotation(PythonFunction.class);
            if (annotation == null)
                continue;

            String[] names = annotation.pyNames();
            ActionWrapper actionWrapper = new ActionWrapper(inst, method);
            if (names.length == 0)
                interpreter.set(method.getName(), actionWrapper);

            for (String s : names) interpreter.set(s, actionWrapper);
        }
    }

    private static Method getMethod(Object o) {
        Class<?> aClass = o.getClass();

        Method[] declaredMethods = aClass.getDeclaredMethods();
        if (declaredMethods.length == 0)
            throw new IllegalArgumentException("Invalid action supplied!");
        return declaredMethods[0];
    }

    public ActionWrapper(Object action) {
        this(action, getMethod(action));
    }

    public ActionWrapper(Object action, String methodName, Class... args) throws NoSuchMethodException {
        this(action, action.getClass().getMethod(methodName, args));
    }

    public ActionWrapper(Object instance, Method method) {
        this.instance = instance;
        this.method = method;
        method.setAccessible(true);
        Parameter[] parameters = method.getParameters();
        paramsByName = new HashMap<>(parameters.length);
        for (int i = 0; i < parameters.length; i++)
            paramsByName.put(parameters[i].getName().toLowerCase(), i);
    }

    @Override
    public PyObject __call__(PyObject[] args, String keywords[]) {
        Parameter[] parameters = method.getParameters();
        Object[] jArgs = new Object[parameters.length];
        int regParmasLength = args.length - keywords.length, setParams = 0;
        for (int i = 0; i < regParmasLength; i++) {
            jArgs[i] = args[i].__tojava__(parameters[i].getType());
            parameters[i] = null; //mark set
            setParams++;
        }

        Map<String, PyObject> extraKeywords = null;
        if (regParmasLength < args.length) {
            extraKeywords = new HashMap<>();
            for (int i = regParmasLength; i < args.length; i++) {
                String keyword = keywords[i - regParmasLength].toLowerCase();
                Integer p = paramsByName.get(keyword);
                if (p == null) {
                    extraKeywords.put(keyword, args[i]);
                    continue;
                }
                if (jArgs[p] != null)
                    throw Py.SyntaxError("Specified the same variable twice!");

                jArgs[p] = args[i].__tojava__(parameters[p].getType());
                parameters[p] = null;
                setParams++;
            }
        }

        if (setParams != parameters.length || args.length != setParams) {
            if (extraKeywords != null && extraKeywords.size() > 0 && setParams == parameters.length - 1) {
                int mapParam = -1;
                for (int i = 0; i < parameters.length; i++)
                    if (parameters[i] != null) {
                        mapParam = i;
                        break;
                    }

                if (mapParam >= 0 && parameters[mapParam].getType() == Map.class) {
                    Type[] actualTypeArguments = ((ParameterizedType) method.getGenericParameterTypes()[mapParam]).getActualTypeArguments();
                    if (actualTypeArguments[0] == String.class) {
                        Class<?> actualTypeArgument = (Class<?>) actualTypeArguments[1];

                        Object finalArg;
                        if (PyObject.class == actualTypeArgument)
                            finalArg = extraKeywords;

                        else {
                            Map<String, Object> argument = new HashMap<>();
                            for (Map.Entry<String, PyObject> arg : extraKeywords.entrySet())
                                argument.put(arg.getKey(), arg.getValue().__tojava__(actualTypeArgument));
                            finalArg = argument;
                        }
                        jArgs[mapParam] = finalArg;
                        parameters[mapParam] = null;
                        setParams++;
                    }
                } else
                    throw Py.SyntaxError(extraKeywords.size() + " extra params specified...");
            }

            if (setParams != parameters.length) {
                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];
                    if (parameter == null)
                        continue;

                    boolean set = false;
                    Object setTo = null;
                    if (parameter.isAnnotationPresent(Nullable.class)) {
                        set = true;
                        setTo = null;
                    }
                    else if (parameter.isAnnotationPresent(DefaultValue.class)) {
                        Class<?> type = parameter.getType();
                        try {
                            setTo = type.getMethod("valueOf", String.class).invoke(null, parameter.getAnnotation(DefaultValue.class).value());
                            set = true;
                        } catch (Exception e) {}
                    }

                    if (set) {
                        jArgs[i] = setTo;
                        parameters[i] = null;
                        setParams++;
                    }
                }
            }

            if (setParams != parameters.length)
                throw Py.SyntaxError("Specified " + setParams + " arguments where " + parameters.length + " are required!");
        }

        try {
            return Py.java2py(method.invoke(instance, jArgs));
        } catch (IllegalAccessException e) {
            throw Py.JavaError(e);
        } catch (InvocationTargetException e) {
            throw Py.JavaError(e.getTargetException());
        }
    }
}
