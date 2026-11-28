package org.bytechen.rottenruins.utils.health.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NodeContainer {

    public final String name;

    private final Object container;
    private final Object[] table;
    private final Class<?> nodeType;
    private final long keyOff;
    private final long valueOff;
    private final long nextOff;

    private final String valueFieldName;

    private NodeContainer(String name, Object container, Object[] table, Class<?> nodeType,
                          long keyOff, long valueOff, long nextOff, String valueFieldName) {
        this.name = name;
        this.container = container;
        this.table = table;
        this.nodeType = nodeType;
        this.keyOff = keyOff;
        this.valueOff = valueOff;
        this.nextOff = nextOff;
        this.valueFieldName = valueFieldName;
    }

    public static NodeContainer of(String name, Object container) {
        if (container == null) return null;
        if (container instanceof java.util.Map) return null;
        try {
            Field tableField = findTableField(container.getClass());
            if (tableField == null) return null;
            Object raw = UnsafeSupport.getObject(container, UnsafeSupport.objectFieldOffset(tableField));
            if (!(raw instanceof Object[] table) || table.length == 0) return null;

            Class<?> nodeType = table.getClass().getComponentType();
            Object sample = null;
            if (nodeType == null || nodeType == Object.class) {
                for (Object o : table) { if (o != null) { sample = o; break; } }
                if (sample == null) return null;
                nodeType = sample.getClass();
            } else {
                for (Object o : table) { if (o != null) { sample = o; break; } }
                if (sample == null) sample = null;
            }

            Field keyField = findField(nodeType, "key");
            Field valueField = findField(nodeType, "value");
            if (valueField == null) valueField = findField(nodeType, "val");
            if (keyField == null || valueField == null) return null;
            Field nextField = findField(nodeType, "next");

            long keyOff = UnsafeSupport.objectFieldOffset(keyField);
            long valueOff = UnsafeSupport.objectFieldOffset(valueField);
            long nextOff = nextField == null ? -1 : UnsafeSupport.objectFieldOffset(nextField);
            return new NodeContainer(name, container, table, nodeType, keyOff, valueOff, nextOff,
                    valueField.getName());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findTableField(Class<?> type) {
        Field named = findField(type, "table");
        if (named != null && isNodeArray(named.getType())) return named;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (isNodeArray(f.getType())) return f;
            }
        }
        return null;
    }

    private static boolean isNodeArray(Class<?> t) {
        if (t == null || !t.isArray()) return false;
        Class<?> comp = t.getComponentType();
        if (comp == null || comp.isPrimitive() || comp == Object.class) return false;
        if (findField(comp, "key") == null) return false;
        return findField(comp, "value") != null || findField(comp, "val") != null;
    }

    public Class<?> nodeType() { return nodeType; }

    public int tableLength() { return table.length; }

    public List<Object> values() {
        List<Object> out = new ArrayList<>();
        for (Object node : nodes()) out.add(UnsafeSupport.getObject(node, valueOff));
        return out;
    }

    public List<Object> nodes() {
        List<Object> out = new ArrayList<>();
        for (Object head : table) {
            Object node = head;
            int guard = 0;
            while (node != null && guard++ < 100_000) {
                out.add(node);
                if (nextOff < 0) break;
                node = UnsafeSupport.getObject(node, nextOff);
            }
        }
        return out;
    }

    public Object findByKey(Object key) {
        if (key == null) return null;
        for (Object head : table) {
            Object node = head;
            int guard = 0;
            while (node != null && guard++ < 100_000) {
                Object k = UnsafeSupport.getObject(node, keyOff);
                if (k == key || Objects.equals(k, key)) return node;
                if (nextOff < 0) break;
                node = UnsafeSupport.getObject(node, nextOff);
            }
        }
        return null;
    }

    public Object valueOf(Object node) {
        return node == null ? null : UnsafeSupport.getObject(node, valueOff);
    }

    public boolean replaceValue(Object node, Object value) {
        if (node == null) return false;
        UnsafeSupport.putObject(node, valueOff, value);
        return true;
    }

    @Override
    public String toString() {
        return name + "(" + container.getClass().getName() + ", 桶=" + table.length + ")";
    }

    public static List<NodeContainer> scanInstance(Object target) {
        List<NodeContainer> out = new ArrayList<>();
        if (target == null) return out;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                    Class<?> t = f.getType();
                    if (t.isPrimitive() || t == String.class || t.isArray()) continue;
                    if (t.getName().startsWith("java.") || t.getName().startsWith("javax.")) continue;
                    Object v;
                    try {
                        v = UnsafeSupport.getObject(target, UnsafeSupport.objectFieldOffset(f));
                    } catch (Throwable ignored) {
                        continue;
                    }
                    NodeContainer nc = of(c.getSimpleName() + "." + f.getName(), v);
                    if (nc != null) out.add(nc);
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    public static List<NodeContainer> scanStatic(Class<?> owner) {
        List<NodeContainer> out = new ArrayList<>();
        collectStatic(owner, out);
        try {
            for (Class<?> nested : owner.getDeclaredClasses()) collectStatic(nested, out);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void collectStatic(Class<?> type, List<NodeContainer> out) {
        if (type == null) return;
        try {
            for (Field f : type.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (t.isPrimitive() || t == String.class || t.isArray()) continue;
                if (t.getName().startsWith("java.") || t.getName().startsWith("javax.")) continue;
                Object v;
                try {
                    v = UnsafeSupport.getObject(UnsafeSupport.staticFieldBase(f),
                            UnsafeSupport.staticFieldOffset(f));
                } catch (Throwable ignored) {
                    continue;
                }
                NodeContainer nc = of(type.getSimpleName() + "." + f.getName(), v);
                if (nc != null) out.add(nc);
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 0) continue;
                Class<?> rt = m.getReturnType();
                if (rt.isPrimitive() || rt == String.class || rt.isArray()) continue;
                if (rt.getName().startsWith("java.") || rt.getName().startsWith("javax.")) continue;
                String lower = m.getName().toLowerCase(java.util.Locale.ROOT);
                if (!lower.startsWith("get") && !lower.contains("map")
                        && !lower.contains("health") && !lower.contains("store")) continue;
                Object v;
                try {
                    m.setAccessible(true);
                    v = m.invoke(null);
                } catch (Throwable ignored) {
                    continue;
                }
                NodeContainer nc = of(type.getSimpleName() + "." + m.getName() + "()", v);
                if (nc != null) out.add(nc);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
