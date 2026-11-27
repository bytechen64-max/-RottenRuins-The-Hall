package org.bytechen.rottenruins.utils.health.analysis;

import org.objectweb.asm.Opcodes;

public final class OutExpr {

    public enum K { TARGET, CONST, RUNTIME, OP }

    public final K kind;
    public final int op;
    public final String type;
    public final Object cst;
    public final Sym runtime;
    public final OutExpr a;
    public final OutExpr b;

    private OutExpr(K kind, int op, String type, Object cst, Sym runtime, OutExpr a, OutExpr b) {
        this.kind = kind;
        this.op = op;
        this.type = type;
        this.cst = cst;
        this.runtime = runtime;
        this.a = a;
        this.b = b;
    }

    public static OutExpr target(String type) { return new OutExpr(K.TARGET, 0, type, null, null, null, null); }

    public static OutExpr of(Object value, String type) { return new OutExpr(K.CONST, 0, type, value, null, null, null); }

    public static OutExpr runtime(Sym sym, String type) { return new OutExpr(K.RUNTIME, 0, type, null, sym, null, null); }

    public static OutExpr op(int opcode, String type, OutExpr a, OutExpr b) {
        return new OutExpr(K.OP, opcode, type, null, null, a, b);
    }

    public boolean isConstant() { return kind == K.CONST; }

    public double eval(double target, RuntimeLookup lookup) {
        switch (kind) {
            case TARGET: return target;
            case CONST: return num(cst);
            case RUNTIME: return num(lookup == null ? null : lookup.eval(runtime));
            case OP: {
                double x = a == null ? 0 : a.eval(target, lookup);
                double y = b == null ? 0 : b.eval(target, lookup);
                return apply(op, type, x, y);
            }
            default: return target;
        }
    }

    public static double num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof Boolean b) return b ? 1 : 0;
        if (o instanceof Character c) return c;
        return 0;
    }

    private static double apply(int op, String type, double x, double y) {
        if ("F".equals(type)) {
            float a = (float) x, b = (float) y;
            return switch (op) {
                case Opcodes.FADD -> a + b;
                case Opcodes.FSUB -> a - b;
                case Opcodes.FMUL -> a * b;
                case Opcodes.FDIV -> a / b;
                case Opcodes.FREM -> a % b;
                case Opcodes.FNEG -> -a;
                default -> a;
            };
        }
        if ("D".equals(type)) {
            return switch (op) {
                case Opcodes.DADD -> x + y;
                case Opcodes.DSUB -> x - y;
                case Opcodes.DMUL -> x * y;
                case Opcodes.DDIV -> x / y;
                case Opcodes.DREM -> x % y;
                case Opcodes.DNEG -> -x;
                default -> x;
            };
        }
        if ("J".equals(type)) {
            long a = (long) x, b = (long) y;
            return switch (op) {
                case Opcodes.LADD -> a + b;
                case Opcodes.LSUB -> a - b;
                case Opcodes.LMUL -> a * b;
                case Opcodes.LDIV -> b == 0 ? 0 : a / b;
                case Opcodes.LREM -> b == 0 ? 0 : a % b;
                case Opcodes.LNEG -> -a;
                case Opcodes.LAND -> a & b;
                case Opcodes.LOR -> a | b;
                case Opcodes.LXOR -> a ^ b;
                default -> a;
            };
        }
        int a = (int) x, b = (int) y;
        return switch (op) {
            case Opcodes.IADD -> a + b;
            case Opcodes.ISUB -> a - b;
            case Opcodes.IMUL -> a * b;
            case Opcodes.IDIV -> b == 0 ? 0 : a / b;
            case Opcodes.IREM -> b == 0 ? 0 : a % b;
            case Opcodes.INEG -> -a;
            case Opcodes.IAND -> a & b;
            case Opcodes.IOR -> a | b;
            case Opcodes.IXOR -> a ^ b;
            case Opcodes.ISHL -> a << b;
            case Opcodes.ISHR -> a >> b;
            case Opcodes.IUSHR -> a >>> b;
            default -> a;
        };
    }

    @Override
    public String toString() {
        switch (kind) {
            case TARGET: return "T";
            case CONST: return String.valueOf(cst);
            case RUNTIME: return runtime == null ? "?" : runtime.toString();
            case OP: return "(" + OpNames.of(op) + " " + a + " " + b + ")";
            default: return "?";
        }
    }
}
