package org.bytechen.rottenruins.utils.health.analysis;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Sym {

    public enum K {

        THIS,

        CONST,

        PARAM,

        FIELD,

        CALL,

        OP,

        CAST,

        UNKNOWN
    }

    public static final Sym UNKNOWN = new Sym(K.UNKNOWN, null, null, null, null, 0, 0, false, null, null, null);

    private static final Sym THIS = new Sym(K.THIS, null, null, null, null, 0, 0, false, null, null, null);

    public final K kind;
    public final String owner;
    public final String name;
    public final String desc;
    public final Object cst;
    public final int op;
    public final int index;
    public final boolean statik;
    public final Sym a;
    public final Sym b;
    public final List<Sym> args;

    private Sym(K kind, String owner, String name, String desc, Object cst, int op, int index,
                boolean statik, Sym a, Sym b, List<Sym> args) {
        this.kind = kind;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.cst = cst;
        this.op = op;
        this.index = index;
        this.statik = statik;
        this.a = a;
        this.b = b;
        this.args = args == null ? Collections.emptyList() : args;
    }

    public static Sym thisSym() { return THIS; }

    public static Sym constant(Object value, String typeDesc) {
        return new Sym(K.CONST, null, null, typeDesc, value, 0, 0, false, null, null, null);
    }

    public static Sym param(int slot) {
        return new Sym(K.PARAM, null, null, null, null, 0, slot, false, null, null, null);
    }

    public static Sym field(boolean statik, String owner, String name, String desc, Sym receiver) {
        return new Sym(K.FIELD, owner, name, desc, null, 0, 0, statik, receiver, null, null);
    }

    public static Sym call(boolean statik, String owner, String name, String desc, Sym receiver, List<Sym> args) {
        return new Sym(K.CALL, owner, name, desc, null, 0, 0, statik, receiver, null, args);
    }

    public static Sym op(int opcode, Sym left, Sym right) {
        return new Sym(K.OP, null, null, null, null, opcode, 0, false, left, right, null);
    }

    public static Sym cast(String targetDesc, Sym value) {
        return new Sym(K.CAST, null, null, targetDesc, null, 0, 0, false, value, null, null);
    }

    public boolean isField() { return kind == K.FIELD; }

    public boolean isCallTo(String ownerInternalName, String methodName) {
        return kind == K.CALL && Objects.equals(owner, ownerInternalName) && Objects.equals(name, methodName);
    }

    public boolean isCallToDesc(String ownerInternalName, String methodName, String methodDesc) {
        return isCallTo(ownerInternalName, methodName) && Objects.equals(desc, methodDesc);
    }

    public boolean contains(Sym needle) {
        if (this.equals(needle)) return true;
        if (a != null && a.contains(needle)) return true;
        if (b != null && b.contains(needle)) return true;
        for (Sym s : args) if (s.contains(needle)) return true;
        return false;
    }

    public void collect(java.util.List<Sym> out) {
        if (!out.contains(this)) out.add(this);
        if (a != null) a.collect(out);
        if (b != null) b.collect(out);
        for (Sym s : args) s.collect(out);
    }

    public boolean isSelfContained() {
        switch (kind) {
            case THIS:
            case CONST:
                return true;
            case PARAM:
                return false;
            case FIELD:
                return (statik || (a != null && a.isSelfContained()));
            case CALL:
                if (!statik && (a == null || !a.isSelfContained())) return false;
                for (Sym s : args) if (!s.isSelfContained()) return false;
                return true;
            case OP:
                return (a == null || a.isSelfContained()) && (b == null || b.isSelfContained());
            case CAST:
                return a != null && a.isSelfContained();
            default:
                return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sym s)) return false;
        return op == s.op && index == s.index && statik == s.statik
                && kind == s.kind
                && Objects.equals(owner, s.owner) && Objects.equals(name, s.name)
                && Objects.equals(desc, s.desc) && Objects.equals(cst, s.cst)
                && Objects.equals(a, s.a) && Objects.equals(b, s.b)
                && Objects.equals(args, s.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, owner, name, desc, cst, op, index, statik, a, b, args);
    }

    @Override
    public String toString() {
        switch (kind) {
            case THIS: return "this";
            case CONST: return "const(" + cst + (desc == null ? "" : ":" + desc) + ")";
            case PARAM: return "arg" + index;
            case FIELD: return (statik ? owner + "." + name : render(a) + "." + name) + ":" + desc;
            case CALL: return (statik ? owner : render(a)) + "." + name + desc
                    + "(" + joinArgs() + ")";
            case OP: return OpNames.of(op) + "(" + render(a) + ", " + render(b) + ")";
            case CAST: return "(" + desc + ")" + render(a);
            default: return "?";
        }
    }

    private String joinArgs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(args.get(i));
        }
        return sb.toString();
    }

    private static String render(Sym s) {
        return s == null ? "null" : s.toString();
    }
}
