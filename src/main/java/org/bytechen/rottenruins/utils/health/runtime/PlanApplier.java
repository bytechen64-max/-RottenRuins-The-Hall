package org.bytechen.rottenruins.utils.health.runtime;

import org.bytechen.rottenruins.utils.health.analysis.Names;
import org.bytechen.rottenruins.utils.health.analysis.Sym;
import org.bytechen.rottenruins.utils.health.analysis.WritePlan;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlanApplier {

    private final Names names;

    public PlanApplier(Names names) {
        this.names = names;
    }

    public boolean apply(WritePlan plan, Object self, float target, AccessStrength strength) {
        if (plan == null || self == null) return false;
        ValueEvaluator ev = new ValueEvaluator(self);
        double stored = plan.value == null ? target : plan.value.eval(target, ev);
        ClassLoader loader = self.getClass().getClassLoader();
        switch (plan.loc) {
            case FIELD: {
                Object recv = plan.receiver == null ? self : ev.eval(plan.receiver);
                if (recv == null) return false;
                Class<?> owner = MemberResolver.load(plan.owner, loader);
                if (owner == null) owner = recv.getClass();
                return FieldAccess.write(owner, plan.name, plan.desc, recv, stored, strength);
            }
            case STATIC_FIELD: {
                Class<?> owner = MemberResolver.load(plan.owner, loader);
                return FieldAccess.write(owner, plan.name, plan.desc, null, stored, strength);
            }
            case MAP_ENTRY: {
                Object map = ev.eval(plan.mapExpr);
                Object key = ev.eval(plan.keyExpr);
                if (map == null) return false;
                return putMap(map, key, plan.boxedDesc, stored);
            }
            case ENTITY_DATA: {
                Object data = ev.eval(plan.dataExpr);
                Object accessor = ev.eval(plan.accessorExpr);
                if (data == null || accessor == null) return false;
                Method set = MemberResolver.findMethod(data.getClass(), names.entityDataSetName, names.entityDataSetDesc);
                if (set == null) return false;
                try {
                    set.invoke(data, accessor, FieldAccess.box(plan.boxedDesc == null ? "F" : plan.boxedDesc, stored));
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }
            default:
                return false;
        }
    }

    public int applyWithMirror(WritePlan plan, Object self, float target, AccessStrength strength, float oldHealth) {
        if (!apply(plan, self, target, strength)) return -1;
        if (plan.loc != WritePlan.Loc.MAP_ENTRY) return 0;
        try {
            ValueEvaluator ev = new ValueEvaluator(self);
            double stored = plan.value == null ? target : plan.value.eval(target, ev);
            Object primary = ev.eval(plan.mapExpr);
            Object key = ev.eval(plan.keyExpr);
            Class<?> owner = ownerClassOf(plan.mapExpr, self.getClass().getClassLoader());
            if (owner == null || key == null) return 0;
            return mirror(owner, primary, key, oldHealth, stored, plan.boxedDesc);
        } catch (Throwable t) {
            return 0;
        }
    }

    public int mirror(Class<?> owner, Object primary, Object key, double oldValue, double newValue, String boxedDesc) {
        int updated = 0;
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (primary != null) seen.add(primary);
        for (NamedMap candidate : candidateMaps(owner)) {
            Object mapObj = candidate.map();
            if (mapObj == null || !seen.add(mapObj)) continue;
            if (!(mapObj instanceof Map<?, ?> map)) continue;
            Object current;
            try {
                current = map.get(key);
            } catch (Throwable t) {
                continue;
            }
            if (!(current instanceof Number num)) continue;
            boolean sameValue = Math.abs(num.doubleValue() - oldValue) <= 0.05;
            if (!sameValue && !looksLikeHealth(candidate.name())) continue;
            if (setEntryValue(mapObj, key, FieldAccess.box(boxedDesc == null ? "F" : boxedDesc, newValue))) {
                updated++;
            }
        }
        return updated;
    }

    private static boolean looksLikeHealth(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("health") || n.contains("hp") || n.contains("life")
                || n.contains("good") || n.contains("last");
    }

    public record NamedMap(String name, Object map) {}

    public int mapSweep(Object self, java.util.Collection<Sym> returns, String declaringClass,
                        ClassLoader loader, double before, double target) {
        if (self == null) return 0;
        java.util.Set<Class<?>> owners = ownerClasses(returns, declaringClass, loader);
        int written = 0;
        for (Class<?> owner : owners) {
            for (NamedMap candidate : candidateMaps(owner)) {
                Object mapObj = candidate.map();
                if (!(mapObj instanceof Map<?, ?> map)) continue;
                Object current;
                try {
                    current = map.get(self);
                } catch (Throwable t) {
                    continue;
                }
                if (!(current instanceof Number num)) continue;
                double d = num.doubleValue();
                if (Math.abs(d - before) > 0.05 && Math.abs(d - target) > 0.05) continue;
                Object boxed = current instanceof Double ? (Object) target : (Object) (float) target;
                if (setEntryValue(mapObj, self, boxed) && entryMatches(mapObj, self, target)) written++;
            }
        }
        return written;
    }

    public static java.util.Set<Class<?>> ownerClasses(java.util.Collection<Sym> returns,
                                                       String declaringClass, ClassLoader loader) {
        java.util.Set<Class<?>> owners = new java.util.LinkedHashSet<>();
        if (returns != null) {
            for (Sym s : returns) collectOwners(s, owners, loader, 0);
        }
        if (declaringClass != null) {
            Class<?> d = MemberResolver.load(declaringClass, loader);
            if (d != null) owners.add(d);
        }
        return owners;
    }

    private static void collectOwners(Sym s, java.util.Set<Class<?>> out, ClassLoader loader, int depth) {
        if (s == null || depth > 8) return;
        if ((s.kind == Sym.K.FIELD || s.kind == Sym.K.CALL) && s.owner != null && worthSweeping(s.owner)) {
            Class<?> c = MemberResolver.load(s.owner, loader);
            if (c != null) out.add(c);
        }
        collectOwners(s.a, out, loader, depth + 1);
        collectOwners(s.b, out, loader, depth + 1);
        for (Sym arg : s.args) collectOwners(arg, out, loader, depth + 1);
    }

    private static boolean worthSweeping(String internalName) {
        return !(internalName.startsWith("java/") || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/") || internalName.startsWith("sun/")
                || internalName.startsWith("com/mojang/") || internalName.startsWith("it/unimi/")
                || internalName.startsWith("net/minecraft/") || internalName.startsWith("net/minecraftforge/"));
    }

    public static List<NamedMap> candidateMaps(Class<?> owner) {
        List<NamedMap> out = new ArrayList<>();
        collectMaps(owner, out);
        try {
            for (Class<?> nested : owner.getDeclaredClasses()) collectMaps(nested, out);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void collectMaps(Class<?> type, List<NamedMap> out) {
        if (type == null) return;
        try {
            for (Field f : type.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(null);
                    if (v != null) out.add(new NamedMap(type.getSimpleName() + "." + f.getName(), v));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 0) continue;
                if (!Map.class.isAssignableFrom(m.getReturnType())) continue;
                String lower = m.getName().toLowerCase(Locale.ROOT);
                if (!lower.startsWith("get") && !lower.contains("map") && !lower.contains("health")) continue;
                try {
                    m.setAccessible(true);
                    Object v = m.invoke(null);
                    if (v != null) out.add(new NamedMap(type.getSimpleName() + "." + m.getName() + "()", v));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Class<?> ownerClassOf(Sym mapExpr, ClassLoader loader) {
        if (mapExpr == null) return null;
        if (mapExpr.kind == Sym.K.FIELD || mapExpr.kind == Sym.K.CALL) {
            return MemberResolver.load(mapExpr.owner, loader);
        }
        return null;
    }

    public static boolean putMap(Object map, Object key, String boxedDesc, double value) {
        String desc = boxedDesc == null ? "F" : boxedDesc;
        Object boxed = FieldAccess.box(desc, value);
        if (setEntryValue(map, key, boxed) && entryMatches(map, key, value)) return true;
        return putFallback(map, key, desc, value);
    }

    public static boolean entryMatches(Object map, Object key, double expected) {
        if (!(map instanceof Map<?, ?> m) || key == null) return false;
        for (int i = 0; i < 5; i++) {
            try {
                Object current = m.get(key);
                return current instanceof Number n && Math.abs(n.doubleValue() - expected) <= 0.05;
            } catch (Throwable t) {
                Thread.onSpinWait();
            }
        }
        return false;
    }

    public String describeMapState(WritePlan plan, Object self) {
        if (plan == null || plan.loc != WritePlan.Loc.MAP_ENTRY) return "";
        try {
            ValueEvaluator ev = new ValueEvaluator(self);
            Object map = ev.eval(plan.mapExpr);
            Object key = ev.eval(plan.keyExpr);
            StringBuilder sb = new StringBuilder();
            sb.append("map=").append(map == null ? "null" : map.getClass().getName());
            sb.append(" key=").append(key);
            if (map instanceof Map<?, ?> m) {
                sb.append(" size=").append(m.size());
                sb.append(" get(key)=").append(m.get(key));
                try {
                    int hits = 0;
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() == key) hits++;
                    }
                    sb.append(" 身份命中=").append(hits);
                } catch (java.util.ConcurrentModificationException cme) {
                    sb.append(" 迭代被并发修改打断(CME)");
                } catch (Throwable t) {
                    sb.append(" 迭代异常=").append(t);
                }
                String failure = lastFailure();
                if (failure != null) sb.append(" 写入失败原因=").append(failure);
                try {
                    if (UnsafeSupport.available()) {
                        java.lang.reflect.Field tableField = findFieldRaw(map.getClass(), "table");
                        Object tableObj = tableField == null ? null
                                : UnsafeSupport.getObject(map, UnsafeSupport.objectFieldOffset(tableField));
                        if (tableObj instanceof Object[] table) {
                            int want = weakHash(key) & (table.length - 1);
                            sb.append(" 表长=").append(table.length).append(" 应在桶=").append(want);
                            long nextOff = -1;
                            for (int i = 0; i < table.length; i++) {
                                Object cur = table[i];
                                while (cur != null) {
                                    if (nextOff < 0) {
                                        java.lang.reflect.Field nf = findFieldRaw(cur.getClass(), "next");
                                        if (nf == null) { cur = null; break; }
                                        nextOff = UnsafeSupport.objectFieldOffset(nf);
                                    }
                                    Object k = cur instanceof java.lang.ref.Reference<?> r ? r.get() : null;
                                    if (k == key) sb.append(" 实际在桶=").append(i);
                                    cur = UnsafeSupport.getObject(cur, nextOff);
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    sb.append(" 桶位诊断失败=").append(t);
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "无法求值 map/key: " + t;
        }
    }

    public String describePlanWrite(WritePlan plan, Object self, double target, double observed) {
        StringBuilder sb = new StringBuilder();
        sb.append("期望=").append(target).append(" 实读=").append(observed);
        ValueEvaluator ev = new ValueEvaluator(self);
        if (plan.loc == WritePlan.Loc.MAP_ENTRY) {
            Object map = ev.eval(plan.mapExpr);
            Object key = ev.eval(plan.keyExpr);
            sb.append(" | 表类=").append(map == null ? "null" : map.getClass().getName());
            if (map instanceof Map<?, ?> m) {
                sb.append(" 表大小=").append(m.size()).append(" 写前表内=").append(m.get(key));
                boolean wrote = putMap(map, key, plan.boxedDesc, target);
                sb.append(" 再写一次=").append(wrote).append(" 写后表内=").append(m.get(key));
            }
        } else if (plan.loc == WritePlan.Loc.ENTITY_DATA) {
            Object data = ev.eval(plan.dataExpr);
            Object accessor = ev.eval(plan.accessorExpr);
            sb.append(" | SynchedEntityData=").append(data).append(" accessor=").append(accessor);
            if (data != null && accessor != null) {
                java.lang.reflect.Method get = MemberResolver.findMethod(data.getClass(),
                        names.entityDataGetName, names.entityDataGetDesc);
                try {
                    sb.append(" 表内=").append(get == null ? "?" : get.invoke(data, accessor));
                } catch (Throwable t) {
                    sb.append(" 读取异常=").append(t);
                }
            }
        } else if (plan.loc == WritePlan.Loc.FIELD || plan.loc == WritePlan.Loc.STATIC_FIELD) {
            Object recv = plan.loc == WritePlan.Loc.STATIC_FIELD ? null : ev.eval(plan.receiver);
            sb.append(" | 字段=").append(plan.owner == null ? "?" : plan.owner.replace('/', '.'))
                    .append('.').append(plan.name).append(':').append(plan.desc);
            sb.append(" 接收者=").append(recv == null ? "null(求值失败)" : recv.getClass().getName());
            Class<?> owner = MemberResolver.load(plan.owner, self == null ? PlanApplier.class.getClassLoader()
                    : self.getClass().getClassLoader());
            java.lang.reflect.Field f = owner == null ? null
                    : MemberResolver.findField(owner, plan.name, plan.desc);
            sb.append(" 字段对象=").append(f == null ? "找不到" : f.getType().getSimpleName()
                    + (java.lang.reflect.Modifier.isFinal(f.getModifiers()) ? "(final)" : ""));
            if (f != null) {
                try {
                    f.setAccessible(true);
                    sb.append(" 当前值=").append(f.get(recv));
                } catch (Throwable t) {
                    sb.append(" 读不了(setAccessible 被拒)=").append(t.getClass().getSimpleName());
                }
            }
            if (recv != null) {
                boolean wrote = writeFieldRaw(owner, plan.name, plan.desc, recv, plan.boxedDesc, target);
                sb.append(" 再写一次=").append(wrote);
                if (f != null) {
                    try {
                        f.setAccessible(true);
                        sb.append(" 写后值=").append(f.get(recv));
                    } catch (Throwable ignored) {
                    }
                }
            }
        } else {
            sb.append(" | loc=").append(plan.loc).append(' ').append(plan.describe());
        }
        return sb.toString();
    }

    private static boolean writeFieldRaw(Class<?> owner, String name, String desc, Object recv,
                                         String boxedDesc, double value) {
        if (owner == null || recv == null) return false;
        try {
            java.lang.reflect.Field f = MemberResolver.findField(owner, name, desc);
            if (f == null) return false;
            long off = UnsafeSupport.objectFieldOffset(f);
            switch (desc == null ? "F" : desc) {
                case "F" -> UnsafeSupport.putFloat(recv, off, (float) value);
                case "D" -> UnsafeSupport.putDouble(recv, off, value);
                case "I" -> UnsafeSupport.putInt(recv, off, (int) value);
                case "J" -> UnsafeSupport.putLong(recv, off, (long) value);
                default -> {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean setEntryValue(Object map, Object key, Object value) {
        if (!(map instanceof Map<?, ?> m) || key == null) return false;
        LAST_FAILURE.set(null);

        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    Object k = entry.getKey();
                    if (k == key || (k != null && k.equals(key))) {
                        ((Map.Entry<Object, Object>) entry).setValue(value);
                        if (getSees(map, key, value)) return true;
                        LAST_FAILURE.set("entrySet 写入后 get 读不到，改走内部桶表");
                        break;
                    }
                }
                break;
            } catch (java.util.ConcurrentModificationException cme) {
                Thread.onSpinWait();
            } catch (Throwable t) {
                LAST_FAILURE.set("迭代异常 " + t);
                break;
            }
        }

        if (writeViaInternalTable(map, key, value)) return true;
        return false;
    }

    private static final ThreadLocal<String> LAST_FAILURE = new ThreadLocal<>();

    public static String lastFailure() {
        return LAST_FAILURE.get();
    }

    private static java.lang.reflect.Field findFieldRaw(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static int weakHash(Object k) {
        int h = k.hashCode();
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    private static boolean writeViaInternalTable(Object map, Object key, Object value) {
        if (!UnsafeSupport.available()) {
            LAST_FAILURE.set("Unsafe 不可用");
            return false;
        }
        try {
            java.lang.reflect.Field tableField = findFieldRaw(map.getClass(), "table");
            if (tableField == null) {
                LAST_FAILURE.set("找不到 table 字段（" + map.getClass().getName() + "）");
                return false;
            }
            Object tableObj = UnsafeSupport.getObject(map, UnsafeSupport.objectFieldOffset(tableField));
            if (!(tableObj instanceof Object[] table)) {
                LAST_FAILURE.set("table 不是数组: " + tableObj);
                return false;
            }
            if (table.length == 0) {
                LAST_FAILURE.set("table 长度为 0");
                return false;
            }

            long valueOff = -1;
            long nextOff = -1;
            long hashOff = -1;
            boolean relinkAllowed = false;
            String vfName = "value";
            long keyOff = -1;
            for (Object head : table) {
                Object entry = head;
                while (entry != null) {
                    if (valueOff < 0) {
                        java.lang.reflect.Field vf = findFieldRaw(entry.getClass(), "value");
                        if (vf == null) vf = findFieldRaw(entry.getClass(), "val");
                        java.lang.reflect.Field nf = findFieldRaw(entry.getClass(), "next");
                        java.lang.reflect.Field hf = findFieldRaw(entry.getClass(), "hash");
                        if (vf == null || nf == null) {
                            LAST_FAILURE.set("找不到 Entry.value(val)/next（" + entry.getClass().getName() + "）");
                            return false;
                        }
                        valueOff = UnsafeSupport.objectFieldOffset(vf);
                        nextOff = UnsafeSupport.objectFieldOffset(nf);
                        hashOff = hf == null ? -1 : UnsafeSupport.objectFieldOffset(hf);
                        java.lang.reflect.Field kf = findFieldRaw(entry.getClass(), "key");
                        keyOff = kf == null ? -1 : UnsafeSupport.objectFieldOffset(kf);
                        vfName = vf.getName();
                        relinkAllowed = "value".equals(vfName);
                    }
                    Object k;
                    if (keyOff >= 0) {
                        k = UnsafeSupport.getObject(entry, keyOff);
                    } else if (entry instanceof java.lang.ref.Reference<?> ref) {
                        k = ref.get();
                    } else {
                        k = null;
                    }
                    Object next = UnsafeSupport.getObject(entry, nextOff);

                    if (k == key) {
                        UnsafeSupport.putObject(entry, valueOff, value);
                        if (getSees(map, key, value)) {
                            LAST_FAILURE.set(null);
                            return true;
                        }
                        if (!relinkAllowed) {
                            LAST_FAILURE.set("写进 " + vfName + " 之后 get 仍读不到（并发结构不修桶位）");
                            return false;
                        }
                        return relinkAndRetry(map, table, entry, key, value, valueOff, nextOff, hashOff);
                    }
                    entry = next;
                }
            }
            LAST_FAILURE.set("桶表里没有这个 key（表长 " + table.length + "）");
        } catch (Throwable t) {
            LAST_FAILURE.set("走内部表失败: " + t);
        }
        return false;
    }

    private static boolean relinkAndRetry(Object map, Object[] table, Object entry, Object key, Object value,
                                          long valueOff, long nextOff, long hashOff) {
        if (hashOff < 0) {
            LAST_FAILURE.set("拿不到 Entry.hash 偏移，无法修桶位");
            return false;
        }
        int want = weakHash(key) & (table.length - 1);
        int found = -1;
        Object foundPrev = null;
        scan:
        for (int i = 0; i < table.length; i++) {
            Object prev = null;
            Object cur = table[i];
            while (cur != null) {
                if (cur == entry) { found = i; foundPrev = prev; break scan; }
                prev = cur;
                cur = UnsafeSupport.getObject(cur, nextOff);
            }
        }
        if (found < 0) {
            LAST_FAILURE.set("条目不在任何桶链上");
            return false;
        }
        if (found == want) {
            LAST_FAILURE.set("条目已在正确桶(" + want + ") 里，get 却读不到");
            return false;
        }

        int oldHash = UnsafeSupport.getInt(entry, hashOff);
        Object entryNext = UnsafeSupport.getObject(entry, nextOff);

        if (foundPrev == null) table[found] = entryNext;
        else UnsafeSupport.putObject(foundPrev, nextOff, entryNext);
        int wantHash = weakHash(key);
        if (oldHash != wantHash) UnsafeSupport.putInt(entry, hashOff, wantHash);
        UnsafeSupport.putObject(entry, nextOff, table[want]);
        table[want] = entry;

        UnsafeSupport.putObject(entry, valueOff, value);
        if (getSees(map, key, value)) {
            LAST_FAILURE.set(null);
            return true;
        }

        Object movedNext = UnsafeSupport.getObject(entry, nextOff);
        if (table[want] == entry) table[want] = movedNext;
        UnsafeSupport.putObject(entry, nextOff, entryNext);
        if (foundPrev == null) table[found] = entry;
        else UnsafeSupport.putObject(foundPrev, nextOff, entry);
        if (oldHash != wantHash) UnsafeSupport.putInt(entry, hashOff, oldHash);
        LAST_FAILURE.set("桶位不符（实际 " + found + " / 应为 " + want + "），搬过去也没用，已还原");
        return false;
    }

    private static boolean getSees(Object map, Object key, Object value) {
        if (!(map instanceof Map<?, ?> m)) return false;
        for (int i = 0; i < 5; i++) {
            try {
                Object got = m.get(key);
                if (value instanceof Number a && got instanceof Number b) {
                    return Math.abs(a.doubleValue() - b.doubleValue()) <= 0.05;
                }
                return value != null && value.equals(got);
            } catch (Throwable t) {
                Thread.onSpinWait();
            }
        }
        return false;
    }

    private static Method findDeclaredMethod(Class<?> type, String name, Class<?>... params) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static java.lang.reflect.Field findDeclaredField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean putFallback(Object map, Object key, String desc, double value) {
        if (map instanceof Map<?, ?> m) {
            try {
                ((Map<Object, Object>) m).put(key, FieldAccess.box(desc, value));
            } catch (Throwable ignored) {
            }
            if (entryMatches(map, key, value)) return true;
        }
        for (Class<?> c = map.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!"put".equals(m.getName())) continue;
                if (m.getParameterCount() != 2) continue;
                Class<?> p2 = m.getParameterTypes()[1];
                if (p2 != float.class && p2 != double.class && p2 != int.class && p2 != long.class) continue;
                try {
                    m.setAccessible(true);
                    if (p2 == float.class) m.invoke(map, key, (float) value);
                    else if (p2 == double.class) m.invoke(map, key, value);
                    else if (p2 == int.class) m.invoke(map, key, (int) value);
                    else m.invoke(map, key, (long) value);
                    if (entryMatches(map, key, value)) return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    public Object snapshot(WritePlan plan, Object self) {
        if (plan == null || self == null) return null;
        ValueEvaluator ev = new ValueEvaluator(self);
        ClassLoader loader = self.getClass().getClassLoader();
        try {
            switch (plan.loc) {
                case FIELD: {
                    Object recv = plan.receiver == null ? self : ev.eval(plan.receiver);
                    if (recv == null) return null;
                    Class<?> owner = MemberResolver.load(plan.owner, loader);
                    if (owner == null) owner = recv.getClass();
                    return FieldAccess.read(owner, plan.name, plan.desc, recv);
                }
                case STATIC_FIELD:
                    return FieldAccess.read(MemberResolver.load(plan.owner, loader), plan.name, plan.desc, null);
                case MAP_ENTRY: {
                    Object map = ev.eval(plan.mapExpr);
                    Object key = ev.eval(plan.keyExpr);
                    if (map instanceof Map<?, ?> m) return m.get(key);
                    return null;
                }
                case ENTITY_DATA: {
                    Object data = ev.eval(plan.dataExpr);
                    Object accessor = ev.eval(plan.accessorExpr);
                    if (data == null || accessor == null) return null;
                    Method get = MemberResolver.findMethod(data.getClass(), names.entityDataGetName,
                            names.entityDataGetDesc);
                    return get == null ? null : get.invoke(data, accessor);
                }
                default:
                    return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public void restore(WritePlan plan, Object self, Object snapshot) {
        if (plan == null || self == null || snapshot == null) return;
        double value = snapshot instanceof Number n ? n.doubleValue() : 0;
        ValueEvaluator ev = new ValueEvaluator(self);
        ClassLoader loader = self.getClass().getClassLoader();
        try {
            switch (plan.loc) {
                case FIELD: {
                    Object recv = plan.receiver == null ? self : ev.eval(plan.receiver);
                    if (recv == null) return;
                    Class<?> owner = MemberResolver.load(plan.owner, loader);
                    if (owner == null) owner = recv.getClass();
                    FieldAccess.write(owner, plan.name, plan.desc, recv, value, AccessStrength.UNSAFE);
                    return;
                }
                case STATIC_FIELD:
                    FieldAccess.write(MemberResolver.load(plan.owner, loader), plan.name, plan.desc,
                            null, value, AccessStrength.UNSAFE);
                    return;
                case MAP_ENTRY: {
                    Object map = ev.eval(plan.mapExpr);
                    Object key = ev.eval(plan.keyExpr);
                    if (map != null) putMap(map, key, plan.boxedDesc, value);
                    return;
                }
                case ENTITY_DATA: {
                    Object data = ev.eval(plan.dataExpr);
                    Object accessor = ev.eval(plan.accessorExpr);
                    if (data == null || accessor == null) return;
                    Method set = MemberResolver.findMethod(data.getClass(), names.entityDataSetName,
                            names.entityDataSetDesc);
                    if (set != null) {
                        set.invoke(data, accessor,
                                FieldAccess.box(plan.boxedDesc == null ? "F" : plan.boxedDesc, value));
                    }
                    return;
                }
                default:
            }
        } catch (Throwable ignored) {
        }
    }
}
