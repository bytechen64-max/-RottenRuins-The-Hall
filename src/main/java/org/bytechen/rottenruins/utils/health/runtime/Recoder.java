package org.bytechen.rottenruins.utils.health.runtime;

import org.bytechen.rottenruins.utils.health.analysis.ClassResolver;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public final class Recoder {

    public interface Container {
        Object get(Object key);

        boolean put(Object key, Object value);
    }

    public interface Health {
        float read();
    }

    public record Result(boolean success, float before, float after, float target, String recipe) {
        public String describe() {
            String head = success ? "成功" : "部分成功";
            return head + " [RECODE " + recipe + "] " + before + " → " + after;
        }
    }

    private interface Slot {
        Class<?> type();

        String name();

        Object get(Object owner) throws Throwable;
    }

    public static final class Recipe {
        final Class<?> valueType;
        final List<Slot> slots;
        final int targetSlot;
        final Method decoder;
        final Method encoder;
        final Method factory;
        final int[] paramSlot;

        Recipe(Class<?> valueType, List<Slot> slots, int targetSlot, Method decoder, Method encoder,
               Method factory, int[] paramSlot) {
            this.valueType = valueType;
            this.slots = slots;
            this.targetSlot = targetSlot;
            this.decoder = decoder;
            this.encoder = encoder;
            this.factory = factory;
            this.paramSlot = paramSlot;
        }

        public String describe() {
            return factory.getName() + "(" + encoder.getDeclaringClass().getSimpleName() + "."
                    + encoder.getName() + ", " + slots.get(targetSlot).name() + ")";
        }
    }

    private static final float EPS = 0.005f;
    private static final int MAX_CLASSES = 24;

    private Recoder() {}

    public static Container containerOf(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Map<?, ?>) {
            return new Container() {
                @Override public Object get(Object key) {
                    try {
                        return ((java.util.Map<?, ?>) raw).get(key);
                    } catch (Throwable t) {
                        return null;
                    }
                }

                @Override public boolean put(Object key, Object value) {
                    if (!PlanApplier.setEntryValue(raw, key, value)) return false;
                    Object back = get(key);
                    return back != null && back.equals(value);
                }
            };
        }
        NodeContainer nc = NodeContainer.of("(recoder)", raw);
        if (nc == null) return null;
        return new Container() {
            @Override public Object get(Object key) {
                try {
                    return nc.valueOf(nc.findByKey(key));
                } catch (Throwable t) {
                    return null;
                }
            }

            @Override public boolean put(Object key, Object value) {
                try {
                    Object node = nc.findByKey(key);
                    if (node == null) return false;
                    return nc.replaceValue(node, value);
                } catch (Throwable t) {
                    return false;
                }
            }
        };
    }

    public static Recipe find(Object sample, Class<?> storageClass, ClassResolver source,
                              float currentReading, Consumer<String> log) {
        if (sample == null || storageClass == null || source == null || Float.isNaN(currentReading)) return null;
        Class<?> valueType = sample.getClass();
        List<Slot> slots = slotsOf(valueType);
        if (slots.isEmpty()) return null;

        List<Class<?>> classes = candidateClasses(storageClass, valueType, source,
                storageClass.getClassLoader());
        if (log != null) {
            log.accept("值类型=" + valueType.getName() + " 字段=" + slots.stream().map(Slot::name).toList()
                    + " 候选类=" + classes.stream().map(Class::getSimpleName).toList());
        }

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            for (Method d : staticMethods(classes)) {
                if (d.getParameterCount() != 1) continue;
                if (d.getReturnType() != float.class && d.getReturnType() != double.class
                        && d.getReturnType() != int.class && d.getReturnType() != long.class) continue;
                if (!wrap(d.getParameterTypes()[0]).isAssignableFrom(wrap(slot.type()))) continue;
                float got = decode(d, slot, sample);
                if (Float.isNaN(got) || Math.abs(got - currentReading) > EPS) continue;

                for (Method e : staticMethods(classes)) {
                    if (e.getParameterCount() != 1) continue;
                    if (!isNumeric(e.getParameterTypes()[0])) continue;
                    if (!wrap(slot.type()).isAssignableFrom(wrap(e.getReturnType()))) continue;
                    if (!roundTrips(d, e, slot, currentReading, log)) continue;

                    for (Method f : staticMethods(classes)) {
                        if (f.getReturnType() != valueType) continue;
                        int[] map = mapParamsToSlots(f, slots);
                        if (map == null) continue;
                        Recipe r = new Recipe(valueType, slots, i, d, e, f, map);
                        if (log != null) log.accept("配方命中：" + r.describe());
                        return r;
                    }
                }
            }
        }
        if (log != null) log.accept("没能凑出配方（解码器/编码器/工厂三者缺一）");
        return null;
    }

    private static boolean roundTrips(Method d, Method e, Slot slot, float probe, Consumer<String> log) {
        try {
            e.setAccessible(true);
            Object encoded = e.invoke(null, coerce(probe, e.getParameterTypes()[0]));
            if (encoded == null) return false;
            if (!wrap(slot.type()).isAssignableFrom(encoded.getClass())) return false;
            float back = decode(d, slot, null, encoded);
            if (Float.isNaN(back) || Math.abs(back - probe) > 0.01f) {
                if (log != null) log.accept("编码器 " + e.getName() + " 回环失败: " + probe + " → " + back);
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static float decode(Method d, Slot slot, Object owner) {
        return decode(d, slot, owner, null);
    }

    private static float decode(Method d, Slot slot, Object owner, Object raw) {
        try {
            Object arg = owner != null ? slot.get(owner) : raw;
            if (arg == null) return Float.NaN;
            d.setAccessible(true);
            Object out = d.invoke(null, coerceRaw(arg, d.getParameterTypes()[0]));
            return out instanceof Number n ? n.floatValue() : Float.NaN;
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    private static int[] mapParamsToSlots(Method factory, List<Slot> slots) {
        Class<?>[] ps = factory.getParameterTypes();
        if (ps.length == 0 || ps.length > slots.size()) return null;
        int[] map = new int[ps.length];
        boolean[] used = new boolean[slots.size()];
        for (int i = 0; i < ps.length; i++) {
            int found = -1;
            if (i < slots.size() && !used[i] && wrap(ps[i]).isAssignableFrom(wrap(slots.get(i).type()))) {
                found = i;
            } else {
                for (int s = 0; s < slots.size(); s++) {
                    if (used[s]) continue;
                    if (wrap(ps[i]).isAssignableFrom(wrap(slots.get(s).type()))) { found = s; break; }
                }
            }
            if (found < 0) return null;
            used[found] = true;
            map[i] = found;
        }
        return map;
    }

    public static Result write(Container container, Object key, Recipe r, float target, Health health,
                               Consumer<String> log) {
        if (container == null || r == null || health == null) return null;
        float before = health.read();
        if (Float.isNaN(before)) return null;

        try {
            Object template = container.get(key);
            if (template == null || !r.valueType.isInstance(template)) return null;

            Object encoded;
            try {
                r.encoder.setAccessible(true);
                encoded = r.encoder.invoke(null, coerce(target, r.encoder.getParameterTypes()[0]));
            } catch (Throwable t) {
                if (log != null) log.accept("编码 " + target + " 失败：" + t);
                return null;
            }
            if (encoded == null) return null;

            float preview = decode(r.decoder, r.slots.get(r.targetSlot), null, encoded);
            if (Float.isNaN(preview) || Math.abs(preview - target) > 0.01f) {
                if (log != null) {
                    log.accept("目标 " + target + " 表示不出来（编码器有损：解回来是 " + preview + "），已跳过、未改数据");
                }
                return null;
            }

            Object rebuilt = build(r, template, r.targetSlot, encoded);
            if (!container.put(key, rebuilt)) {
                if (log != null) log.accept("重建值写不进容器");
                return null;
            }

            float after = health.read();
            if (!Float.isNaN(after) && Math.abs(after - target) <= EPS) {
                if (log != null) log.accept("重建命中：" + before + " → " + after + "（目标 " + target + "）");
                return new Result(true, before, after, target, r.describe());
            }

            restore(container, key, r, template, health, log, after);
            return null;
        } catch (Throwable t) {
            if (log != null) log.accept("重建失败：" + t);
            return null;
        }
    }

    private static void restore(Container container, Object key, Recipe r, Object template, Health health,
                                Consumer<String> log, float observed) {
        try {
            Object back = build(r, template, -1, null);
            container.put(key, back);
            float now = health.read();
            if (log != null) {
                log.accept("重建后读数是 " + observed + "（目标不符），已还原为 " + now);
            }
        } catch (Throwable t) {
            if (log != null) log.accept("还原失败：" + t);
        }
    }

    private static Object build(Recipe r, Object template, int slotIndex, Object newRaw) throws Throwable {
        Object[] args = new Object[r.paramSlot.length];
        for (int i = 0; i < args.length; i++) {
            int s = r.paramSlot[i];
            args[i] = (s == slotIndex) ? newRaw : r.slots.get(s).get(template);
        }
        r.factory.setAccessible(true);
        return r.factory.invoke(null, args);
    }

    private static List<Slot> slotsOf(Class<?> valueType) {
        List<Slot> out = new ArrayList<>();
        try {
            if (valueType.isRecord()) {
                for (RecordComponent rc : valueType.getRecordComponents()) {
                    Method acc = rc.getAccessor();
                    acc.setAccessible(true);
                    out.add(new Slot() {
                        @Override public Class<?> type() { return rc.getType(); }

                        @Override public String name() { return rc.getName(); }

                        @Override public Object get(Object owner) throws Throwable { return acc.invoke(owner); }
                    });
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (java.lang.reflect.Field f : valueType.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                f.setAccessible(true);
                out.add(new Slot() {
                    @Override public Class<?> type() { return f.getType(); }

                    @Override public String name() { return f.getName(); }

                    @Override public Object get(Object owner) throws Throwable { return f.get(owner); }
                });
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static List<Class<?>> candidateClasses(Class<?> storageClass, Class<?> valueType,
                                                   ClassResolver source, ClassLoader loader) {
        LinkedHashSet<Class<?>> out = new LinkedHashSet<>();
        addClass(out, storageClass);
        addClass(out, valueType);
        try {
            addClass(out, valueType.getDeclaringClass());
        } catch (Throwable ignored) {
        }
        ClassNode cn = source.find(storageClass.getName().replace('.', '/'));
        if (cn != null && cn.methods != null) {
            outer:
            for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                for (AbstractInsnNode insn : mn.instructions) {
                    String owner = ownerOf(insn);
                    if (owner == null || !MutatorFinder.worthBorrowing(owner)) continue;
                    addClass(out, MemberResolver.load(owner, loader));
                    if (out.size() >= MAX_CLASSES) break outer;
                }
            }
        }
        out.addAll(BIT_CODEC_CLASSES);
        return new ArrayList<>(out);
    }

    private static final List<Class<?>> BIT_CODEC_CLASSES = List.of(
            Float.class, Double.class, Integer.class, Long.class, Short.class, Byte.class);

    private static void addClass(LinkedHashSet<Class<?>> out, Class<?> c) {
        if (c != null && !c.isPrimitive() && !c.isArray()) out.add(c);
    }

    private static String ownerOf(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode mi) return mi.owner;
        if (insn instanceof FieldInsnNode fi) return fi.owner;
        if (insn instanceof TypeInsnNode ti) return ti.desc;
        if (insn instanceof LdcInsnNode ld && ld.cst instanceof Type t && t.getSort() == Type.OBJECT) {
            return t.getInternalName();
        }
        return null;
    }

    private static List<Method> staticMethods(List<Class<?>> classes) {
        List<Method> out = new ArrayList<>();
        for (Class<?> c : classes) {
            try {
                for (Method m : c.getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && !m.isSynthetic()) out.add(m);
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == boolean.class) return Boolean.class;
        if (c == char.class) return Character.class;
        return c;
    }

    private static boolean isNumeric(Class<?> c) {
        return c == float.class || c == double.class || c == int.class || c == long.class
                || Number.class.isAssignableFrom(wrap(c));
    }

    private static Object coerce(float v, Class<?> target) {
        if (target == double.class || target == Double.class) return (double) v;
        if (target == int.class || target == Integer.class) return (int) v;
        if (target == long.class || target == Long.class) return (long) v;
        return v;
    }

    private static Object coerceRaw(Object v, Class<?> target) {
        if (v instanceof Number n) return coerce(n.floatValue(), target);
        return v;
    }
}
