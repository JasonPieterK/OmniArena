package com.omniarena.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * Tiny reflection helper for optional plugin APIs (DecentHolograms, FancyHolograms, Citizens,
 * Multiverse). Using reflection means OmniArena never links against those jars, so it loads fine
 * without them and survives API changes (a changed signature becomes a caught, counted failure).
 */
public final class Reflect {

    private static final Map<Class<?>, Class<?>> BOXES = Map.of(
            boolean.class, Boolean.class, int.class, Integer.class, long.class, Long.class,
            double.class, Double.class, float.class, Float.class, short.class, Short.class,
            byte.class, Byte.class, char.class, Character.class);

    private Reflect() {}

    /** Loads a class through the given loader (a soft-dependency's own loader sees its classes). */
    public static Class<?> load(String className, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(className, true, loader != null ? loader : Reflect.class.getClassLoader());
    }

    /** Invokes a static method chosen by name and argument compatibility. */
    public static Object invokeStatic(Class<?> type, String method, Object... args) {
        return call(type, null, method, args);
    }

    /** Invokes an instance method chosen by name and argument compatibility. */
    public static Object invoke(Object target, String method, Object... args) {
        if (target == null) throw new IllegalStateException("Cannot call " + method + "() on null");
        return call(target.getClass(), target, method, args);
    }

    /** First public method with this name whose parameters accept {@code args}; null when none fits. */
    public static Method find(Class<?> type, String name, Object... args) {
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            if (accepts(m.getParameterTypes(), args)) return m;
        }
        return null;
    }

    private static Object call(Class<?> type, Object target, String name, Object[] args) {
        Method m = find(type, name, args);
        if (m == null) {
            throw new IllegalStateException("No method " + type.getName() + "." + name + "(" + args.length
                    + " args) matching the plugin version installed");
        }
        try {
            if (!Modifier.isPublic(m.getDeclaringClass().getModifiers())) m.setAccessible(true);
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error err) throw err;
            throw new IllegalStateException(c);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean accepts(Class<?>[] params, Object[] args) {
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];
            Object a = args[i];
            if (a == null) {
                if (p.isPrimitive()) return false;
                continue;
            }
            Class<?> boxed = p.isPrimitive() ? BOXES.get(p) : p;
            if (boxed == null || !boxed.isInstance(a)) return false;
        }
        return true;
    }
}
