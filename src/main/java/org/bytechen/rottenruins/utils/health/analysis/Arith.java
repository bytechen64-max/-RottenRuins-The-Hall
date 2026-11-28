package org.bytechen.rottenruins.utils.health.analysis;

import org.objectweb.asm.Opcodes;

public final class Arith {

    private Arith() {}

    public static Object apply(int op, Object a, Object b) {
        switch (op) {
            case Opcodes.FADD: case Opcodes.FSUB: case Opcodes.FMUL: case Opcodes.FDIV: case Opcodes.FREM: {
                float x = f(a), y = f(b);
                return switch (op) {
                    case Opcodes.FADD -> x + y;
                    case Opcodes.FSUB -> x - y;
                    case Opcodes.FMUL -> x * y;
                    case Opcodes.FDIV -> x / y;
                    default -> x % y;
                };
            }
            case Opcodes.DADD: case Opcodes.DSUB: case Opcodes.DMUL: case Opcodes.DDIV: case Opcodes.DREM: {
                double x = d(a), y = d(b);
                return switch (op) {
                    case Opcodes.DADD -> x + y;
                    case Opcodes.DSUB -> x - y;
                    case Opcodes.DMUL -> x * y;
                    case Opcodes.DDIV -> x / y;
                    default -> x % y;
                };
            }
            case Opcodes.LADD: case Opcodes.LSUB: case Opcodes.LMUL: case Opcodes.LDIV:
            case Opcodes.LREM: case Opcodes.LAND: case Opcodes.LOR: case Opcodes.LXOR: {
                long x = l(a), y = l(b);
                return switch (op) {
                    case Opcodes.LADD -> x + y;
                    case Opcodes.LSUB -> x - y;
                    case Opcodes.LMUL -> x * y;
                    case Opcodes.LDIV -> y == 0 ? 0L : x / y;
                    case Opcodes.LREM -> y == 0 ? 0L : x % y;
                    case Opcodes.LAND -> x & y;
                    case Opcodes.LOR -> x | y;
                    default -> x ^ y;
                };
            }
            case Opcodes.FNEG: return -f(a);
            case Opcodes.DNEG: return -d(a);
            case Opcodes.INEG: return -i(a);
            case Opcodes.LNEG: return -l(a);
            default: {
                int x = i(a), y = i(b);
                return switch (op) {
                    case Opcodes.IADD -> x + y;
                    case Opcodes.ISUB -> x - y;
                    case Opcodes.IMUL -> x * y;
                    case Opcodes.IDIV -> y == 0 ? 0 : x / y;
                    case Opcodes.IREM -> y == 0 ? 0 : x % y;
                    case Opcodes.IAND -> x & y;
                    case Opcodes.IOR -> x | y;
                    case Opcodes.IXOR -> x ^ y;
                    case Opcodes.ISHL -> x << y;
                    case Opcodes.ISHR -> x >> y;
                    case Opcodes.IUSHR -> x >>> y;
                    default -> x;
                };
            }
        }
    }

    public static float f(Object o) {
        if (o instanceof Number n) return n.floatValue();
        if (o instanceof Boolean b) return b ? 1f : 0f;
        return 0f;
    }

    public static double d(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof Boolean b) return b ? 1d : 0d;
        return 0d;
    }

    public static int i(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof Boolean b) return b ? 1 : 0;
        return 0;
    }

    public static long l(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof Boolean b) return b ? 1L : 0L;
        return 0L;
    }
}
