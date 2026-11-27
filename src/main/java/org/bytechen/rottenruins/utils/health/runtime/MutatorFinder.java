package org.bytechen.rottenruins.utils.health.runtime;

import org.bytechen.rottenruins.utils.health.analysis.ClassResolver;
import org.bytechen.rottenruins.utils.health.analysis.Sym;
import org.bytechen.rottenruins.utils.health.analysis.WritePlan;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MutatorFinder {

    public record Store(String owner, String field) {
        public String key() { return owner + "#" + field; }

        public String display() { return owner.replace('/', '.') + "." + field; }
    }

    public record Candidate(Class<?> owner, String name, String desc,
                            Class<?>[] paramTypes, Class<?> returnType,
                            boolean numericReturn, boolean hasSlot,
                            Method method, MethodHandle handle) {

        public Class<?> valueType() { return paramTypes[paramTypes.length - 1]; }

        public String key() { return owner.getName() + '#' + name + desc; }

        public String display() {
            StringBuilder sb = new StringBuilder(owner.getName()).append('.').append(name).append('(');
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(paramTypes[i].getSimpleName());
            }
            sb.append(") -> ").append(returnType == null ? "?" : returnType.getSimpleName());
            if (method == null) sb.append("  [句柄]");
            return sb.toString();
        }

        public Object invokeStatic(Object[] args) throws Throwable {
            if (handle != null) return handle.invokeWithArguments(args);
            method.setAccessible(true);
            return method.invoke(null, args);
        }
    }

    private static final Set<String> READER_NAMES = Set.of(
            "get", "load", "read", "fetch", "peek", "valueof", "parse", "decode");

    private static final String[] WRITER_HINTS = {
            "store", "set", "put", "write", "apply", "assign", "update", "save", "reset",
            "damage", "hurt", "heal", "consume", "drain"};

    private MutatorFinder() {}

    public static List<Store> storesIn(WritePlan plan) {
        Set<Store> out = new LinkedHashSet<>();
        if (plan == null) return List.of();
        walk(plan.receiver, out, 0);
        walk(plan.mapExpr, out, 0);
        walk(plan.keyExpr, out, 0);
        walk(plan.dataExpr, out, 0);
        walk(plan.accessorExpr, out, 0);
        if (plan.loc == WritePlan.Loc.STATIC_FIELD && plan.owner != null && worthBorrowing(plan.owner)) {
            out.add(new Store(plan.owner, plan.name));
        }
        return new ArrayList<>(out);
    }

    private static void walk(Sym s, Set<Store> out, int depth) {
        if (s == null || depth > 12) return;
        if (s.kind == Sym.K.FIELD && s.statik && s.owner != null && s.name != null
                && worthBorrowing(s.owner)) {
            out.add(new Store(s.owner, s.name));
        }
        walk(s.a, out, depth + 1);
        walk(s.b, out, depth + 1);
        if (s.args != null) for (Sym a : s.args) walk(a, out, depth + 1);
    }

    public static boolean worthBorrowing(String internalName) {
        return internalName != null
                && !(internalName.startsWith("java/") || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/") || internalName.startsWith("sun/")
                || internalName.startsWith("com/mojang/") || internalName.startsWith("it/unimi/")
                || internalName.startsWith("net/minecraft/") || internalName.startsWith("net/minecraftforge/"));
    }

    public static List<Candidate> find(Class<?> storageClass, String storeField, Class<?> entityType,
                                       ClassResolver source) {
        List<Candidate> all = find(storageClass, entityType, source);
        List<Candidate> out = new ArrayList<>();
        String ownerInternal = storageClass == null ? null : storageClass.getName().replace('.', '/');
        ClassNode cn = (source == null || ownerInternal == null) ? null : source.find(ownerInternal);
        for (Candidate c : all) {
            MethodNode mn = cn == null ? null : methodNode(cn, c.name(), c.desc());
            if (mn != null && storeField != null && readsStore(mn, ownerInternal, storeField)) out.add(c);
        }
        return out.isEmpty() ? all : out;
    }

    public static List<Candidate> find(Class<?> searchIn, Class<?> entityType, ClassResolver source) {
        List<Candidate> out = new ArrayList<>();
        if (searchIn == null || entityType == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        collectReflective(searchIn, entityType, out, seen);
        collectFromBytecode(searchIn, entityType, source, out, seen, true);
        sortCandidates(out);
        return out;
    }

    public static List<Candidate> findViaBytecode(Class<?> searchIn, Class<?> entityType, ClassResolver source) {
        List<Candidate> out = new ArrayList<>();
        if (searchIn == null || entityType == null || source == null) return out;
        collectFromBytecode(searchIn, entityType, source, out, new LinkedHashSet<>(), false);
        sortCandidates(out);
        return out;
    }

    private static void collectReflective(Class<?> searchIn, Class<?> entityType,
                                          List<Candidate> out, Set<String> seen) {
        Method[] methods;
        try {
            methods = searchIn.getDeclaredMethods();
        } catch (Throwable t) {
            return;
        }
        for (Method m : methods) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            if (m.isSynthetic() || m.isBridge()) continue;
            Class<?>[] ps = m.getParameterTypes();
            Class<?> ret = m.getReturnType();
            if (!shapeOk(ps, entityType) || !nameOk(m.getName(), ret)) continue;
            String desc = Type.getMethodDescriptor(m);
            if (!seen.add(searchIn.getName() + '#' + m.getName() + desc)) continue;
            out.add(new Candidate(searchIn, m.getName(), desc, ps, ret,
                    isNumeric(ret), ps.length == 3, m, null));
        }
    }

    private static void collectFromBytecode(Class<?> searchIn, Class<?> entityType, ClassResolver source,
                                            List<Candidate> out, Set<String> seen, boolean allowMethodLookup) {
        if (source == null) return;
        ClassNode cn = source.find(searchIn.getName().replace('.', '/'));
        if (cn == null || cn.methods == null) return;
        ClassLoader loader = searchIn.getClassLoader();
        for (MethodNode mn : cn.methods) {
            if (mn.name == null || mn.desc == null) continue;
            if ("<init>".equals(mn.name) || "<clinit>".equals(mn.name)) continue;
            if ((mn.access & Opcodes.ACC_STATIC) == 0) continue;
            if ((mn.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) continue;

            Class<?>[] ps = MemberResolver.paramTypes(mn.desc, loader);
            Class<?> ret = MemberResolver.typeOf(Type.getReturnType(mn.desc).getDescriptor(), loader);
            if (ret == null || anyNull(ps)) continue;
            if (!shapeOk(ps, entityType) || !nameOk(mn.name, ret)) continue;

            String key = searchIn.getName() + '#' + mn.name + mn.desc;
            if (seen.contains(key)) continue;

            Method method = allowMethodLookup ? declaredMethod(searchIn, mn.name, ps, ret) : null;
            MethodHandle handle = method != null ? null : resolveHandle(searchIn, mn.name, ps, ret);
            if (method == null && handle == null) continue;
            seen.add(key);
            out.add(new Candidate(searchIn, mn.name, mn.desc, ps, ret,
                    isNumeric(ret), ps.length == 3, method, handle));
        }
    }

    private static boolean shapeOk(Class<?>[] ps, Class<?> entityType) {
        int n = ps.length;
        if (n != 2 && n != 3) return false;
        if (ps[n - 1] == null || !isNumeric(ps[n - 1])) return false;
        if (n == 3 && !(int.class == ps[1] || Integer.class == ps[1]
                || long.class == ps[1] || Long.class == ps[1])) return false;
        if (ps[0] == null) return false;
        if (ps[0].isAssignableFrom(entityType)) return true;
        return !ps[0].isPrimitive() && !ps[0].getName().startsWith("java.");
    }

    private static boolean nameOk(String name, Class<?> returnType) {
        if (looksReader(name)) return false;
        return writerScore(name) > 0 || returnType == void.class;
    }

    private static void sortCandidates(List<Candidate> out) {
        out.sort((a, b) -> {
            int s = Integer.compare(writerScore(b.name()), writerScore(a.name()));
            if (s != 0) return s;
            s = Boolean.compare(b.numericReturn(), a.numericReturn());
            if (s != 0) return s;
            return Integer.compare(valueRank(a.valueType()), valueRank(b.valueType()));
        });
    }

    private static int valueRank(Class<?> t) {
        if (t == float.class || t == Float.class) return 0;
        if (t == double.class || t == Double.class) return 1;
        if (t == int.class || t == Integer.class) return 2;
        if (t == long.class || t == Long.class) return 3;
        return 4;
    }

    private static boolean anyNull(Class<?>[] classes) {
        for (Class<?> c : classes) {
            if (c == null) return true;
        }
        return false;
    }

    private static Method declaredMethod(Class<?> owner, String name, Class<?>[] ps, Class<?> ret) {
        try {
            Method m = owner.getDeclaredMethod(name, ps);
            return ret == null || m.getReturnType() == ret ? m : m;
        } catch (Throwable t) {
            return null;
        }
    }

    public static MethodHandle resolveHandle(Class<?> owner, String name, Class<?>[] ps, Class<?> ret) {
        if (owner == null || name == null || ret == null || anyNull(ps)) return null;
        MethodType mt;
        try {
            mt = MethodType.methodType(ret, ps);
        } catch (Throwable t) {
            return null;
        }
        try {
            return MethodHandles.lookup().findStatic(owner, name, mt);
        } catch (Throwable ignored) {
        }
        try {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findStatic(owner, name, mt);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> tokens(String name) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == '$' || Character.isDigit(c)) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            if (Character.isUpperCase(c) && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(Character.toLowerCase(c));
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static boolean looksReader(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (READER_NAMES.contains(lower)) return true;
        for (String tok : tokens(name)) {
            if (READER_NAMES.contains(tok)) return true;
        }
        return false;
    }

    private static int writerScore(String name) {
        int score = 0;
        for (String tok : tokens(name)) {
            for (int i = 0; i < WRITER_HINTS.length; i++) {
                if (tok.equals(WRITER_HINTS[i])) { score += WRITER_HINTS.length - i; break; }
            }
        }
        if (score > 0) return score;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String hint : WRITER_HINTS) {
            if (lower.contains(hint)) return 1;
        }
        return 0;
    }

    public static List<Class<?>> referencedClasses(Class<?> from, ClassResolver source, ClassLoader loader) {
        LinkedHashSet<Class<?>> out = new LinkedHashSet<>();
        if (from == null) return new ArrayList<>(out);
        for (Class<?> c = from; c != null && c != Object.class; c = c.getSuperclass()) {
            String internal = c.getName().replace('.', '/');
            if (!worthBorrowing(internal) && c != from) break;
            ClassNode cn = source == null ? null : source.find(internal);
            if (cn == null || cn.methods == null) continue;
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                for (AbstractInsnNode insn : mn.instructions) {
                    String owner = ownerOf(insn);
                    if (owner == null || !worthBorrowing(owner)) continue;
                    Class<?> loaded = MemberResolver.load(owner, loader);
                    if (loaded == null && source instanceof ClassByteSource cbs) {
                        loaded = MemberResolver.load(owner, cbs.loader());
                    }
                    if (loaded == null) continue;
                    if (!hasStaticWriteEntry(loaded)) continue;
                    out.add(loaded);
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static boolean hasStaticWriteEntry(Class<?> c) {
        Method[] ms;
        try {
            ms = c.getDeclaredMethods();
        } catch (Throwable t) {
            return false;
        }
        for (Method m : ms) {
            if (!Modifier.isStatic(m.getModifiers()) || m.isSynthetic() || m.isBridge()) continue;
            Class<?>[] ps = m.getParameterTypes();
            int n = ps.length;
            if ((n == 2 || n == 3) && isNumeric(ps[n - 1])) return true;
        }
        return false;
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

    private static MethodNode methodNode(ClassNode cn, String name, String desc) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name) && mn.desc.equals(desc)) return mn;
        }
        return null;
    }

    private static boolean readsStore(MethodNode mn, String owner, String field) {
        for (AbstractInsnNode insn : mn.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.owner.equals(owner) && fi.name.equals(field)) {
                if (fi.getOpcode() == Opcodes.GETSTATIC || fi.getOpcode() == Opcodes.GETFIELD) return true;
            }
        }
        return false;
    }

    private static boolean writesStore(MethodNode mn, String owner, String field) {
        boolean put = false;
        boolean mapMutator = false;
        for (AbstractInsnNode insn : mn.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.owner.equals(owner) && fi.name.equals(field)) {
                if (fi.getOpcode() == Opcodes.PUTSTATIC || fi.getOpcode() == Opcodes.PUTFIELD) put = true;
            }
            if (insn instanceof MethodInsnNode mi) {
                String n = mi.name.toLowerCase(Locale.ROOT);
                if (n.equals("put") || n.equals("putifabsent") || n.equals("putall")
                        || n.startsWith("compute") || n.equals("merge") || n.equals("replace")
                        || n.equals("replaceall") || n.equals("remove") || n.equals("clear")) {
                    mapMutator = true;
                }
            }
        }
        return put || mapMutator;
    }

    public static boolean isNumeric(Class<?> c) {
        return c == float.class || c == double.class || c == int.class || c == long.class
                || c == Float.class || c == Double.class || c == Integer.class || c == Long.class;
    }
}
