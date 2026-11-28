package org.bytechen.rottenruins.utils.health.runtime;

import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FieldAccess {

    private static final Map<String, VarHandle> HANDLES = new ConcurrentHashMap<>();
    private static final Map<String, Long> OFFSETS = new ConcurrentHashMap<>();

    private FieldAccess() {}

    public static Object read(Class<?> owner, String name, String desc, Object target) {
        Field f = MemberResolver.findField(owner, name, desc);
        if (f == null) return null;
        try {
            return f.get(target);
        } catch (Throwable t) {
            if (UnsafeSupport.available()) {
                try {
                    long off = offsetOf(f, target == null);
                    Object base = target == null ? UnsafeSupport.staticFieldBase(f) : target;
                    switch (desc == null ? "F" : desc) {
                        case "F": return UnsafeSupport.getFloat(base, off);
                        case "D": return UnsafeSupport.getDouble(base, off);
                        case "I": return UnsafeSupport.getInt(base, off);
                        case "J": return UnsafeSupport.getLong(base, off);
                        default: return UnsafeSupport.getInt(base, off);
                    }
                } catch (Throwable ignored) {
                }
            }
            return null;
        }
    }

    public static boolean write(Class<?> owner, String name, String desc, Object target,
                                double value, AccessStrength strength) {
        Field f = MemberResolver.findField(owner, name, desc);
        if (f == null) return false;
        String type = desc == null ? Type.getDescriptor(f.getType()) : desc;
        boolean statik = target == null || java.lang.reflect.Modifier.isStatic(f.getModifiers());
        switch (strength) {
            case REFLECTION:
                return writeReflection(f, target, type, value);
            case VARHANDLE:
                return writeVarHandle(f, owner, name, type, target, statik, value);
            case UNSAFE:
                return writeUnsafe(f, target, type, value, statik);
            default:
                return false;
        }
    }

    private static boolean writeReflection(Field f, Object target, String type, double value) {
        try {
            f.setAccessible(true);
            switch (type) {
                case "F" -> f.setFloat(target, (float) value);
                case "D" -> f.setDouble(target, value);
                case "I" -> f.setInt(target, (int) value);
                case "J" -> f.setLong(target, (long) value);
                case "S" -> f.setShort(target, (short) value);
                case "B" -> f.setByte(target, (byte) value);
                case "C" -> f.setChar(target, (char) ((int) value));
                case "Z" -> f.setBoolean(target, value != 0);
                default -> f.set(target, (float) value);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean writeVarHandle(Field f, Class<?> owner, String name, String type,
                                          Object target, boolean statik, double value) {
        try {
            String key = owner.getName() + '#' + name + ':' + type + (statik ? ":static" : "");
            VarHandle vh = HANDLES.get(key);
            if (vh == null) {
                Class<?> fieldType = MemberResolver.typeOf(type, owner.getClassLoader());
                MethodHandles.Lookup lk = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
                vh = statik ? lk.findStaticVarHandle(owner, name, fieldType)
                            : lk.findVarHandle(owner, name, fieldType);
                HANDLES.put(key, vh);
            }
            Object boxed = box(type, value);
            vh.set(statik ? null : target, boxed);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean writeUnsafe(Field f, Object target, String type, double value, boolean statik) {
        if (!UnsafeSupport.available()) return false;
        try {
            long off = offsetOf(f, statik);
            Object base = statik ? UnsafeSupport.staticFieldBase(f) : target;
            if (base == null) return false;
            switch (type) {
                case "F" -> UnsafeSupport.putFloat(base, off, (float) value);
                case "D" -> UnsafeSupport.putDouble(base, off, value);
                case "I" -> UnsafeSupport.putInt(base, off, (int) value);
                case "J" -> UnsafeSupport.putLong(base, off, (long) value);
                case "S" -> UnsafeSupport.putShort(base, off, (short) value);
                case "B", "Z" -> UnsafeSupport.putByte(base, off, (byte) ((int) value));
                case "C" -> UnsafeSupport.putInt(base, off, (int) value);
                default -> UnsafeSupport.putFloat(base, off, (float) value);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static long offsetOf(Field f, boolean statik) {
        String key = f.getDeclaringClass().getName() + '#' + f.getName() + (statik ? ":static" : "");
        Long cached = OFFSETS.get(key);
        if (cached != null) return cached;
        long off = statik ? UnsafeSupport.staticFieldOffset(f) : UnsafeSupport.objectFieldOffset(f);
        OFFSETS.put(key, off);
        return off;
    }

    public static Object box(String type, double value) {
        return switch (type) {
            case "F" -> (float) value;
            case "D" -> value;
            case "I" -> (int) value;
            case "J" -> (long) value;
            case "S" -> (short) value;
            case "B" -> (byte) value;
            case "C" -> (char) ((int) value);
            case "Z" -> value != 0;
            default -> (float) value;
        };
    }

    public static AccessStrength escalate(AccessStrength s) {
        return switch (s) {
            case REFLECTION -> AccessStrength.VARHANDLE;
            case VARHANDLE -> AccessStrength.UNSAFE;
            case UNSAFE -> null;
        };
    }
}
