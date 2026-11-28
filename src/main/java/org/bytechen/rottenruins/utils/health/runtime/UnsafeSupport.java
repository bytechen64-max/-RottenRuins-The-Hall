package org.bytechen.rottenruins.utils.health.runtime;

import java.lang.reflect.Field;

public final class UnsafeSupport {

    private static final Object UNSAFE;

    static {
        Object u = null;
        try {
            Class<?> c = Class.forName("sun.misc.Unsafe");
            Field f = c.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = f.get(null);
        } catch (Throwable ignored) {
        }
        UNSAFE = u;
    }

    private UnsafeSupport() {}

    public static boolean available() { return UNSAFE != null; }

    public static long objectFieldOffset(Field field) {
        try {
            return (long) UNSAFE.getClass().getMethod("objectFieldOffset", Field.class).invoke(UNSAFE, field);
        } catch (Throwable t) {
            throw new IllegalStateException("objectFieldOffset failed", t);
        }
    }

    public static long staticFieldOffset(Field field) {
        try {
            return (long) UNSAFE.getClass().getMethod("staticFieldOffset", Field.class).invoke(UNSAFE, field);
        } catch (Throwable t) {
            throw new IllegalStateException("staticFieldOffset failed", t);
        }
    }

    public static Object staticFieldBase(Field field) {
        try {
            return UNSAFE.getClass().getMethod("staticFieldBase", Field.class).invoke(UNSAFE, field);
        } catch (Throwable t) {
            throw new IllegalStateException("staticFieldBase failed", t);
        }
    }

    public static void putFloat(Object base, long offset, float v) { call("putFloat", Object.class, long.class, float.class, base, offset, v); }
    public static void putDouble(Object base, long offset, double v) { call("putDouble", Object.class, long.class, double.class, base, offset, v); }
    public static void putInt(Object base, long offset, int v) { call("putInt", Object.class, long.class, int.class, base, offset, v); }
    public static void putLong(Object base, long offset, long v) { call("putLong", Object.class, long.class, long.class, base, offset, v); }
    public static void putShort(Object base, long offset, short v) { call("putShort", Object.class, long.class, short.class, base, offset, v); }
    public static void putByte(Object base, long offset, byte v) { call("putByte", Object.class, long.class, byte.class, base, offset, v); }

    public static float getFloat(Object base, long offset) { return (float) call2("getFloat", Object.class, long.class, base, offset); }
    public static double getDouble(Object base, long offset) { return (double) call2("getDouble", Object.class, long.class, base, offset); }
    public static int getInt(Object base, long offset) { return (int) call2("getInt", Object.class, long.class, base, offset); }
    public static long getLong(Object base, long offset) { return (long) call2("getLong", Object.class, long.class, base, offset); }

    public static Object getObject(Object base, long offset) {
        return call2("getObject", Object.class, long.class, base, offset);
    }

    public static void putObject(Object base, long offset, Object value) {
        call("putObject", Object.class, long.class, Object.class, base, offset, value);
    }

    private static void call(String name, Class<?> p1, Class<?> p2, Class<?> p3, Object base, long off, Object v) {
        try {
            UNSAFE.getClass().getMethod(name, p1, p2, p3).invoke(UNSAFE, base, off, v);
        } catch (Throwable t) {
            throw new IllegalStateException("Unsafe." + name + " failed", t);
        }
    }

    private static Object call2(String name, Class<?> p1, Class<?> p2, Object base, long off) {
        try {
            return UNSAFE.getClass().getMethod(name, p1, p2).invoke(UNSAFE, base, off);
        } catch (Throwable t) {
            throw new IllegalStateException("Unsafe." + name + " failed", t);
        }
    }
}
