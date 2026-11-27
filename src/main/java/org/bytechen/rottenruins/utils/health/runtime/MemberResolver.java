package org.bytechen.rottenruins.utils.health.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MemberResolver {

    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> CLASSES = new ConcurrentHashMap<>();

    private MemberResolver() {}

    public static Class<?> load(String internalName, ClassLoader loader) {
        if (internalName == null) return null;
        String key = internalName;
        Class<?> cached = CLASSES.get(key);
        if (cached != null) return cached;
        String binary = internalName.replace('/', '.');
        Class<?> c = null;
        ClassLoader cl = loader != null ? loader : MemberResolver.class.getClassLoader();
        try {
            c = Class.forName(binary, false, cl);
        } catch (Throwable t) {
            try {
                c = Class.forName(binary, false, MemberResolver.class.getClassLoader());
            } catch (Throwable ignored) {
            }
        }
        if (c != null) CLASSES.put(key, c);
        return c;
    }

    public static Field findField(Class<?> owner, String name, String desc) {
        if (owner == null || name == null) return null;
        String key = owner.getName() + '#' + name + ':' + desc;
        Field cached = FIELDS.get(key);
        if (cached != null) return cached;
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.getName().equals(name)) continue;
                if (desc != null && !desc.equals(org.objectweb.asm.Type.getDescriptor(f.getType()))) continue;
                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                }
                FIELDS.put(key, f);
                return f;
            }
        }
        return null;
    }

    public static Method findMethod(Class<?> owner, String name, String desc) {
        if (owner == null || name == null || desc == null) return null;
        String key = owner.getName() + '#' + name + desc;
        Method cached = METHODS.get(key);
        if (cached != null) return cached;
        Class<?>[] params = paramTypes(desc, owner.getClassLoader());
        Class<?> ret = typeOf(org.objectweb.asm.Type.getReturnType(desc).getDescriptor(), owner.getClassLoader());
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != params.length) continue;
                if (!sameTypes(m.getParameterTypes(), params)) continue;
                if (ret != null && !ret.equals(m.getReturnType()) && !m.getReturnType().equals(ret)) {
                    if (!compatible(m.getReturnType(), ret)) continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                METHODS.put(key, m);
                return m;
            }
        }
        try {
            Method m = owner.getMethod(name, params);
            METHODS.put(key, m);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean sameTypes(Class<?>[] a, Class<?>[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) {
                if (!compatible(a[i], b[i]) && !compatible(b[i], a[i])) return false;
            }
        }
        return true;
    }

    private static boolean compatible(Class<?> declared, Class<?> wanted) {
        if (declared.equals(wanted)) return true;
        if (declared.isPrimitive() != wanted.isPrimitive()) {
            Class<?> boxed = box(declared);
            return boxed != null && boxed.equals(wanted);
        }
        return declared.isAssignableFrom(wanted) || wanted.isAssignableFrom(declared);
    }

    private static Class<?> box(Class<?> p) {
        if (p == null || !p.isPrimitive()) return p;
        if (p == float.class) return Float.class;
        if (p == double.class) return Double.class;
        if (p == int.class) return Integer.class;
        if (p == long.class) return Long.class;
        if (p == short.class) return Short.class;
        if (p == byte.class) return Byte.class;
        if (p == char.class) return Character.class;
        if (p == boolean.class) return Boolean.class;
        return p;
    }

    public static Class<?>[] paramTypes(String methodDesc, ClassLoader cl) {
        org.objectweb.asm.Type[] types = org.objectweb.asm.Type.getArgumentTypes(methodDesc);
        Class<?>[] out = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) out[i] = typeOf(types[i].getDescriptor(), cl);
        return out;
    }

    public static Class<?> typeOf(String desc, ClassLoader cl) {
        if (desc == null || desc.isEmpty()) return null;
        char c = desc.charAt(0);
        switch (c) {
            case 'V': return void.class;
            case 'Z': return boolean.class;
            case 'B': return byte.class;
            case 'C': return char.class;
            case 'S': return short.class;
            case 'I': return int.class;
            case 'J': return long.class;
            case 'F': return float.class;
            case 'D': return double.class;
            case 'L': return load(desc.substring(1, desc.length() - 1), cl);
            case '[':
                try {
                    return Class.forName(desc.replace('/', '.'), false,
                            cl != null ? cl : MemberResolver.class.getClassLoader());
                } catch (Throwable t) {
                    return Object.class;
                }
            default: return null;
        }
    }
}
