package org.bytechen.rottenruins.utils.health.analysis;

import org.objectweb.asm.Opcodes;

public final class OpNames {

    private OpNames() {}

    public static String of(int op) {
        return switch (op) {
            case Opcodes.IADD -> "I+"; case Opcodes.ISUB -> "I-"; case Opcodes.IMUL -> "I*";
            case Opcodes.IDIV -> "I/"; case Opcodes.IREM -> "I%";
            case Opcodes.INEG -> "I-neg"; case Opcodes.ISHL -> "I<<"; case Opcodes.ISHR -> "I>>";
            case Opcodes.IUSHR -> "I>>>"; case Opcodes.IAND -> "I&"; case Opcodes.IOR -> "I|";
            case Opcodes.IXOR -> "I^";
            case Opcodes.LADD -> "J+"; case Opcodes.LSUB -> "J-"; case Opcodes.LMUL -> "J*";
            case Opcodes.LDIV -> "J/"; case Opcodes.LREM -> "J%"; case Opcodes.LNEG -> "J-neg";
            case Opcodes.LSHL -> "J<<"; case Opcodes.LSHR -> "J>>"; case Opcodes.LUSHR -> "J>>>";
            case Opcodes.LAND -> "J&"; case Opcodes.LOR -> "J|"; case Opcodes.LXOR -> "J^";
            case Opcodes.FADD -> "F+"; case Opcodes.FSUB -> "F-"; case Opcodes.FMUL -> "F*";
            case Opcodes.FDIV -> "F/"; case Opcodes.FREM -> "F%"; case Opcodes.FNEG -> "F-neg";
            case Opcodes.DADD -> "D+"; case Opcodes.DSUB -> "D-"; case Opcodes.DMUL -> "D*";
            case Opcodes.DDIV -> "D/"; case Opcodes.DREM -> "D%"; case Opcodes.DNEG -> "D-neg";
            case Opcodes.I2L -> "I2J"; case Opcodes.I2F -> "I2F"; case Opcodes.I2D -> "I2D";
            case Opcodes.L2I -> "J2I"; case Opcodes.L2F -> "J2F"; case Opcodes.L2D -> "J2D";
            case Opcodes.F2I -> "F2I"; case Opcodes.F2L -> "F2J"; case Opcodes.F2D -> "F2D";
            case Opcodes.D2I -> "D2I"; case Opcodes.D2L -> "D2J"; case Opcodes.D2F -> "D2F";
            case Opcodes.I2B -> "I2B"; case Opcodes.I2C -> "I2C"; case Opcodes.I2S -> "I2S";
            case Opcodes.LCMP -> "lcmp"; case Opcodes.FCMPL -> "fcmpg-l"; case Opcodes.FCMPG -> "fcmpg";
            case Opcodes.DCMPL -> "dcmpg-l"; case Opcodes.DCMPG -> "dcmpg";
            case Opcodes.ARRAYLENGTH -> "arraylength";
            default -> "op" + op;
        };
    }

    public static boolean isBinary(int op) {
        return switch (op) {
            case Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM,
                 Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR, Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR,
                 Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM,
                 Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR, Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR,
                 Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM,
                 Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM -> true;
            default -> false;
        };
    }

    public static boolean isUnary(int op) {
        return switch (op) {
            case Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG -> true;
            default -> false;
        };
    }

    public static boolean isConversion(int op) {
        return switch (op) {
            case Opcodes.I2L, Opcodes.I2F, Opcodes.I2D, Opcodes.L2I, Opcodes.L2F, Opcodes.L2D,
                 Opcodes.F2I, Opcodes.F2L, Opcodes.F2D, Opcodes.D2I, Opcodes.D2L, Opcodes.D2F,
                 Opcodes.I2B, Opcodes.I2C, Opcodes.I2S -> true;
            default -> false;
        };
    }

    public static String resultDesc(int op) {
        return switch (op) {
            case Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM, Opcodes.LNEG,
                 Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR, Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR,
                 Opcodes.I2L, Opcodes.F2L, Opcodes.D2L, Opcodes.LCMP -> "J";
            case Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM, Opcodes.FNEG,
                 Opcodes.I2F, Opcodes.L2F, Opcodes.D2F -> "F";
            case Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM, Opcodes.DNEG,
                 Opcodes.I2D, Opcodes.L2D, Opcodes.F2D -> "D";
            default -> "I";
        };
    }
}
