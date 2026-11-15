package i18nautoupdatemod.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Reflection {
    private final Class<?> clazz;
    private final Object instance;

    private Reflection(Class<?> clazz) {
        this.clazz = clazz;
        this.instance = null;
    }

    private Reflection(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null");
        }
        this.clazz = instance.getClass();
        this.instance = instance;
    }

    public static Reflection clazz(String className) throws ClassNotFoundException {
        return new Reflection(Class.forName(className));
    }

    public static Reflection clazz(Class<?> clazz) {
        return new Reflection(clazz);
    }

    public static Reflection clazz(Object instance) {
        return new Reflection(instance);
    }

    private Reflection getField(String field) throws ReflectiveOperationException {
        Field field0 = clazz.getDeclaredField(field);
        field0.setAccessible(true);
        return new Reflection(field0.get(instance));
    }

    private Reflection invokeMethod(String method) throws ReflectiveOperationException {
        Method method1 = clazz.getDeclaredMethod(method);
        method1.setAccessible(true);
        return new Reflection(method1.invoke(instance));
    }

    /**
     * Get field or invoke method
     *
     * @param fieldOrMethod fieldName or methodName()
     * @return result
     * @throws ReflectiveOperationException
     */
    public Reflection get(String fieldOrMethod) throws ReflectiveOperationException {
        if (fieldOrMethod.endsWith(")")) {
            return invokeMethod(fieldOrMethod.replace("()", ""));
        } else {
            return getField(fieldOrMethod);
        }
    }

    public Object get() {
        return instance;
    }
}