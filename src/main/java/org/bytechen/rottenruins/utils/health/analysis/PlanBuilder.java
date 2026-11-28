package org.bytechen.rottenruins.utils.health.analysis;

import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PlanBuilder {

    private static final int MAX_INLINE = 6;

    private static final int MAX_INLINE_INSNS = 400;

    private final Names names;
    private final ClassResolver resolver;

    private final Set<String> inlining = new HashSet<>();

    public PlanBuilder(Names names) {
        this(names, null);
    }

    public PlanBuilder(Names names, ClassResolver resolver) {
        this.names = names;
        this.resolver = resolver;
    }

    public List<WritePlan> plans(Sym ret) {
        List<WritePlan> out = new ArrayList<>();
        if (ret == null) return out;
        inlining.clear();
        enumerate(ret, OutExpr.target("F"), null, 0, out);
        Set<String> seen = new HashSet<>();
        List<WritePlan> unique = new ArrayList<>();
        for (WritePlan p : out) {
            if (seen.add(p.describe())) unique.add(p);
        }
        unique.sort(Comparator.comparingInt(PlanBuilder::score).reversed());
        return unique;
    }

    private void enumerate(Sym s, OutExpr want, String boxedHint, int depth, List<WritePlan> out) {
        if (s == null || want == null || depth > 24) return;
        switch (s.kind) {
            case FIELD: {
                if (s.statik) {
                    out.add(WritePlan.staticField(s.owner, s.name, s.desc, want, "静态字段"));
                } else if (s.a != null && s.a.isSelfContained()) {
                    out.add(WritePlan.field(s.owner, s.name, s.desc, s.a, want, "实例字段"));
                }
                return;
            }
            case CAST: {
                String hint = boxedHint != null ? boxedHint : referenceTypeOf(s.desc);
                enumerate(s.a, want, hint, depth + 1, out);
                return;
            }
            case CALL: {
                String boxedOwner = Names.boxedTypeOf(s.owner);
                if (boxedOwner != null && s.args.isEmpty() && isUnboxMethod(s.name)) {
                    enumerate(s.a, want, boxedOwner, depth + 1, out);
                    return;
                }
                if (names.isEntityDataGet(s) && s.a != null && s.a.isSelfContained() && s.args.size() == 1) {
                    Sym accessor = s.args.get(0);
                    if (accessor.isSelfContained()) {
                        out.add(WritePlan.entityData(s.a, accessor,
                                boxedHint != null ? boxedHint : "F", want, "SynchedEntityData 同步血量"));
                    }
                    return;
                }
                if (names.isMapLookup(s) && s.a != null && s.a.isSelfContained() && !s.args.isEmpty()) {
                    Sym key = s.args.get(0);
                    if (key.isSelfContained()) {
                        out.add(WritePlan.mapEntry(s.a, key, boxedHint != null ? boxedHint : "F", want, "Map 条目"));
                    }
                    return;
                }
                inlineCall(s, want, boxedHint, depth, out);
                return;
            }
            case OP: {
                if (OpNames.isUnary(s.op) && s.a != null) {
                    String fam = family(s.op);
                    enumerate(s.a, OutExpr.op(neg(fam), fam, want, null), boxedHint, depth + 1, out);
                    return;
                }
                if (!OpNames.isBinary(s.op) || s.a == null || s.b == null) return;
                String fam = family(s.op);
                if (runtimeEvaluable(s.b)) {
                    OutExpr w = solveLeft(s.op, fam, want, s.b);
                    if (w != null) enumerate(s.a, w, boxedHint, depth + 1, out);
                }
                if (runtimeEvaluable(s.a)) {
                    OutExpr w = solveRight(s.op, fam, want, s.a);
                    if (w != null) enumerate(s.b, w, boxedHint, depth + 1, out);
                }
                return;
            }
            default:
        }
    }

    private void inlineCall(Sym call, OutExpr want, String boxedHint, int depth, List<WritePlan> out) {
        if (resolver == null || inlining.size() >= MAX_INLINE) return;
        if (call.owner == null || call.name == null || call.desc == null) return;
        char ret = call.desc.charAt(call.desc.indexOf(')') + 1);
        if ("V".equals(String.valueOf(ret))) return;

        org.objectweb.asm.tree.ClassNode owner = resolver.find(call.owner);
        if (owner == null) return;
        org.objectweb.asm.tree.MethodNode callee = null;
        for (org.objectweb.asm.tree.MethodNode m : owner.methods) {
            if (call.name.equals(m.name) && call.desc.equals(m.desc)) { callee = m; break; }
        }
        if (callee == null) return;
        if (callee.instructions == null || callee.instructions.size() > MAX_INLINE_INSNS) return;

        String key = call.owner + '.' + call.name + call.desc;
        if (!inlining.add(key)) return;
        try {
            for (Sym ret0 : MethodAnalysis.returnValues(owner, callee)) {
                Sym substituted = substitute(ret0, call, callee);
                enumerate(substituted, want, boxedHint, depth + 1, out);
            }
        } finally {
            inlining.remove(key);
        }
    }

    static Sym substitute(Sym s, Sym call, org.objectweb.asm.tree.MethodNode callee) {
        if (s == null) return null;
        switch (s.kind) {
            case THIS:
                return call.a != null ? call.a : s;
            case PARAM: {
                int index = argIndexForSlot(callee, s.index);
                if (index >= 0 && index < call.args.size()) return call.args.get(index);
                return Sym.UNKNOWN;
            }
            case CONST:
            case UNKNOWN:
                return s;
            case FIELD:
                return Sym.field(s.statik, s.owner, s.name, s.desc, substitute(s.a, call, callee));
            case CAST:
                return Sym.cast(s.desc, substitute(s.a, call, callee));
            case OP:
                return Sym.op(s.op, substitute(s.a, call, callee), substitute(s.b, call, callee));
            case CALL: {
                List<Sym> args = new ArrayList<>(s.args.size());
                for (Sym a : s.args) args.add(substitute(a, call, callee));
                return Sym.call(s.statik, s.owner, s.name, s.desc, substitute(s.a, call, callee), args);
            }
            default:
                return s;
        }
    }

    private static int argIndexForSlot(org.objectweb.asm.tree.MethodNode callee, int slot) {
        boolean statik = (callee.access & Opcodes.ACC_STATIC) != 0;
        int cur = statik ? 0 : 1;
        org.objectweb.asm.Type[] types = org.objectweb.asm.Type.getArgumentTypes(callee.desc);
        for (int i = 0; i < types.length; i++) {
            if (cur == slot) return i;
            cur += types[i].getSize();
        }
        return -1;
    }

    static OutExpr solveLeft(int op, String fam, OutExpr t, Sym right) {
        OutExpr r = OutExpr.runtime(right, fam);
        return switch (op) {
            case Opcodes.FADD, Opcodes.DADD, Opcodes.IADD, Opcodes.LADD -> OutExpr.op(sub(fam), fam, t, r);
            case Opcodes.FSUB, Opcodes.DSUB, Opcodes.ISUB, Opcodes.LSUB -> OutExpr.op(add(fam), fam, t, r);
            case Opcodes.FMUL, Opcodes.DMUL, Opcodes.IMUL, Opcodes.LMUL -> OutExpr.op(div(fam), fam, t, r);
            case Opcodes.FDIV, Opcodes.DDIV, Opcodes.IDIV, Opcodes.LDIV -> OutExpr.op(mul(fam), fam, t, r);
            default -> null;
        };
    }

    static OutExpr solveRight(int op, String fam, OutExpr t, Sym left) {
        OutExpr l = OutExpr.runtime(left, fam);
        return switch (op) {
            case Opcodes.FADD, Opcodes.DADD, Opcodes.IADD, Opcodes.LADD -> OutExpr.op(sub(fam), fam, t, l);
            case Opcodes.FSUB, Opcodes.DSUB, Opcodes.ISUB, Opcodes.LSUB -> OutExpr.op(sub(fam), fam, l, t);
            case Opcodes.FMUL, Opcodes.DMUL, Opcodes.IMUL, Opcodes.LMUL -> OutExpr.op(div(fam), fam, t, l);
            case Opcodes.FDIV, Opcodes.DDIV, Opcodes.IDIV, Opcodes.LDIV -> OutExpr.op(div(fam), fam, l, t);
            default -> null;
        };
    }

    static String family(int op) {
        return switch (op) {
            case Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM, Opcodes.FNEG -> "F";
            case Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM, Opcodes.DNEG -> "D";
            case Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM, Opcodes.LNEG -> "J";
            default -> "I";
        };
    }

    private static int neg(String f) { return switch (f) { case "F" -> Opcodes.FNEG; case "D" -> Opcodes.DNEG; case "J" -> Opcodes.LNEG; default -> Opcodes.INEG; }; }

    private static int add(String f) { return switch (f) { case "F" -> Opcodes.FADD; case "D" -> Opcodes.DADD; case "J" -> Opcodes.LADD; default -> Opcodes.IADD; }; }
    private static int sub(String f) { return switch (f) { case "F" -> Opcodes.FSUB; case "D" -> Opcodes.DSUB; case "J" -> Opcodes.LSUB; default -> Opcodes.ISUB; }; }
    private static int mul(String f) { return switch (f) { case "F" -> Opcodes.FMUL; case "D" -> Opcodes.DMUL; case "J" -> Opcodes.LMUL; default -> Opcodes.IMUL; }; }
    private static int div(String f) { return switch (f) { case "F" -> Opcodes.FDIV; case "D" -> Opcodes.DDIV; case "J" -> Opcodes.LDIV; default -> Opcodes.IDIV; }; }

    public static boolean runtimeEvaluable(Sym s) {
        return s != null && s.isSelfContained() && !hasUnknown(s);
    }

    private static boolean hasUnknown(Sym s) {
        if (s == null) return true;
        if (s.kind == Sym.K.UNKNOWN) return true;
        if (s.a != null && hasUnknown(s.a)) return true;
        if (s.b != null && hasUnknown(s.b)) return true;
        for (Sym arg : s.args) if (hasUnknown(arg)) return true;
        return false;
    }

    private static boolean isUnboxMethod(String name) {
        return "floatValue".equals(name) || "doubleValue".equals(name)
                || "intValue".equals(name) || "longValue".equals(name)
                || "shortValue".equals(name) || "byteValue".equals(name);
    }

    private static String referenceTypeOf(String desc) {
        if (desc == null) return null;
        if (desc.startsWith("L") && desc.endsWith(";")) {
            return Names.boxedTypeOf(desc.substring(1, desc.length() - 1));
        }
        return null;
    }

    static int score(WritePlan p) {
        int s = 0;
        String n = p.name == null ? "" : p.name.toLowerCase(Locale.ROOT);
        if (n.contains("health") || n.contains("hp") || n.contains("life")
                || n.contains("blood") || n.contains("vital") || n.contains("hurt")) s += 60;
        if (n.contains("max") || n.contains("total") || n.contains("cap")
                || n.contains("default") || n.contains("base") || n.contains("limit")) s -= 80;
        switch (p.loc) {
            case FIELD -> s += 20;
            case MAP_ENTRY -> s += 25;
            case ENTITY_DATA -> s += 45;
            case STATIC_FIELD -> s += 5;
        }
        String d = p.storageDesc();
        if ("F".equals(d)) s += 10;
        else if ("D".equals(d)) s += 6;
        return s;
    }
}
