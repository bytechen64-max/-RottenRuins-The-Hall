package org.bytechen.rottenruins.utils.health.analysis;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class MethodAnalysis {

    private static final int MAX_VISITS = 20_000;

    private final MethodNode method;
    private final boolean isStatic;

    private final AbstractInsnNode[] insns;
    private final Map<AbstractInsnNode, Integer> index = new IdentityHashMap<>();
    private final List<int[]> blocks = new ArrayList<>();
    private final int[] blockOf;
    private final Frame[] entryFrames;
    private final boolean[] reachable;

    private final Set<Sym> returns = new LinkedHashSet<>();
    private int visits;
    private boolean overflow;

    private MethodAnalysis(MethodNode method) {
        this.method = method;
        this.isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        this.insns = method.instructions.toArray();
        for (int i = 0; i < insns.length; i++) index.put(insns[i], i);
        buildBlocks();
        this.blockOf = new int[insns.length];
        for (int b = 0; b < blocks.size(); b++) {
            int[] r = blocks.get(b);
            for (int i = r[0]; i < r[1]; i++) blockOf[i] = b;
        }
        this.entryFrames = new Frame[blocks.size()];
        this.reachable = new boolean[blocks.size()];
    }

    public static List<Sym> returnValues(ClassNode owner, MethodNode method) {
        if (method == null || method.instructions == null || method.instructions.size() == 0) return List.of();
        try {
            MethodAnalysis a = new MethodAnalysis(method);
            a.run();
            if (a.returns.isEmpty()) return List.of();
            return new ArrayList<>(a.returns);
        } catch (Throwable t) {
            return List.of();
        }
    }

    private void buildBlocks() {
        int n = insns.length;
        TreeSet<Integer> leaders = new TreeSet<>();
        leaders.add(0);
        for (int i = 0; i < n; i++) {
            AbstractInsnNode in = insns[i];
            if (in instanceof JumpInsnNode j) {
                addLeader(leaders, idx(j.label));
                addLeader(leaders, i + 1);
            } else if (in instanceof TableSwitchInsnNode t) {
                addLeader(leaders, idx(t.dflt));
                for (LabelNode l : t.labels) addLeader(leaders, idx(l));
                addLeader(leaders, i + 1);
            } else if (in instanceof LookupSwitchInsnNode l) {
                addLeader(leaders, idx(l.dflt));
                for (LabelNode lab : l.labels) addLeader(leaders, idx(lab));
                addLeader(leaders, i + 1);
            } else if (isTerminator(in.getOpcode())) {
                addLeader(leaders, i + 1);
            }
        }
        leaders.removeIf(v -> v < 0 || v >= n);
        List<Integer> sorted = new ArrayList<>(leaders);
        for (int k = 0; k < sorted.size(); k++) {
            int start = sorted.get(k);
            int end = (k + 1 < sorted.size()) ? sorted.get(k + 1) : n;
            blocks.add(new int[]{start, end});
        }
        if (blocks.isEmpty()) blocks.add(new int[]{0, n});
    }

    private void addLeader(Set<Integer> set, int i) {
        if (i >= 0 && i < insns.length) set.add(i);
    }

    private int idx(AbstractInsnNode node) {
        Integer i = index.get(node);
        return i == null ? -1 : i;
    }

    private static boolean isTerminator(int op) {
        return (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) || op == Opcodes.ATHROW;
    }

    static boolean isConditionalJump(int op) {
        if (op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE) return true;
        return op == Opcodes.IFNULL || op == Opcodes.IFNONNULL;
    }

    private void run() {
        entryFrames[0] = Frame.initial(method, isStatic);
        Deque<Integer> work = new ArrayDeque<>();
        work.add(0);
        while (!work.isEmpty()) {
            int b = work.poll();
            if (++visits > MAX_VISITS) { overflow = true; return; }
            Frame in = entryFrames[b];
            if (in == null) continue;
            reachable[b] = true;
            Frame out = simulate(b, in);
            if (out == null) continue;
            for (int s : successors(b)) {
                Frame merged = merge(entryFrames[s], out);
                if (merged != null) {
                    entryFrames[s] = merged;
                    if (!work.contains(s)) work.add(s);
                }
            }
        }
    }

    private int[] successors(int b) {
        int[] r = blocks.get(b);
        int last = r[1] - 1;
        while (last >= r[0] && insns[last].getOpcode() < 0) last--;
        if (last < r[0]) return b + 1 < blocks.size() ? new int[]{b + 1} : new int[0];
        AbstractInsnNode in = insns[last];
        int op = in.getOpcode();
        List<Integer> out = new ArrayList<>(4);
        if (in instanceof JumpInsnNode j) {
            out.add(blockOf[idx(j.label)]);
            if (op != Opcodes.GOTO && op != Opcodes.JSR && b + 1 < blocks.size()) out.add(b + 1);
        } else if (in instanceof TableSwitchInsnNode t) {
            out.add(blockOf[idx(t.dflt)]);
            for (LabelNode l : t.labels) out.add(blockOf[idx(l)]);
        } else if (in instanceof LookupSwitchInsnNode l) {
            out.add(blockOf[idx(l.dflt)]);
            for (LabelNode lab : l.labels) out.add(blockOf[idx(lab)]);
        } else if (!isTerminator(op) && b + 1 < blocks.size()) {
            out.add(b + 1);
        }
        return out.stream().filter(v -> v >= 0).mapToInt(Integer::intValue).distinct().toArray();
    }

    private Frame merge(Frame old, Frame add) {
        if (old == null) return add.copy();
        Frame m = old.copy();
        boolean changed = false;
        for (int i = 0; i < m.locals.length; i++) {
            Alt s = union(old.locals[i], add.locals[i]);
            if (!eq(s, m.locals[i])) { m.locals[i] = s; changed = true; }
        }
        int maxSp = Math.max(old.sp, add.sp);
        for (int i = 0; i < maxSp; i++) {
            Alt s = union(old.slot(i), add.slot(i));
            if (!eq(s, m.slot(i))) changed = true;
            m.setSlot(i, s);
        }
        m.sp = Math.max(m.sp, maxSp);
        return changed ? m : null;
    }

    private static Alt union(Alt a, Alt b) {
        if (a == null || b == null) return Alt.UNKNOWN;
        if (a.equals(b)) return a;
        if (a.isUnknownOnly() || b.isUnknownOnly()) return Alt.UNKNOWN;
        List<Sym> merged = new ArrayList<>(a.options());
        for (Sym s : b.options()) if (!merged.contains(s)) merged.add(s);
        return Alt.of(merged);
    }

    private static boolean eq(Alt a, Alt b) {
        return a == null ? b == null : a.equals(b);
    }

    private Frame simulate(int blockIndex, Frame in) {
        int[] r = blocks.get(blockIndex);
        Frame f = in.copy();
        for (int i = r[0]; i < r[1]; i++) {
            AbstractInsnNode node = insns[i];
            int op = node.getOpcode();
            if (op < 0) continue;
            try {
                if (!step(f, node, op)) break;
            } catch (Throwable t) {
                return null;
            }
        }
        return f;
    }

    private boolean step(Frame f, AbstractInsnNode in, int op) {
        switch (op) {
            case Opcodes.ACONST_NULL: f.pushVal(Alt.of(Sym.constant(null, "Ljava/lang/Object;")), 1); return true;
            case Opcodes.ICONST_M1: case Opcodes.ICONST_0: case Opcodes.ICONST_1:
            case Opcodes.ICONST_2: case Opcodes.ICONST_3: case Opcodes.ICONST_4: case Opcodes.ICONST_5:
                f.pushVal(Alt.of(Sym.constant(op - Opcodes.ICONST_0, "I")), 1); return true;
            case Opcodes.LCONST_0: case Opcodes.LCONST_1:
                f.pushVal(Alt.of(Sym.constant((long) (op - Opcodes.LCONST_0), "J")), 2); return true;
            case Opcodes.FCONST_0: case Opcodes.FCONST_1: case Opcodes.FCONST_2:
                f.pushVal(Alt.of(Sym.constant((float) (op - Opcodes.FCONST_0), "F")), 1); return true;
            case Opcodes.DCONST_0: case Opcodes.DCONST_1:
                f.pushVal(Alt.of(Sym.constant((double) (op - Opcodes.DCONST_0), "D")), 2); return true;
            case Opcodes.BIPUSH: case Opcodes.SIPUSH:
                f.pushVal(Alt.of(Sym.constant(((IntInsnNode) in).operand, "I")), 1); return true;
            case Opcodes.LDC: {
                Object c = ((LdcInsnNode) in).cst;
                if (c instanceof Integer) f.pushVal(Alt.of(Sym.constant(c, "I")), 1);
                else if (c instanceof Float) f.pushVal(Alt.of(Sym.constant(c, "F")), 1);
                else if (c instanceof Long) f.pushVal(Alt.of(Sym.constant(c, "J")), 2);
                else if (c instanceof Double) f.pushVal(Alt.of(Sym.constant(c, "D")), 2);
                else if (c instanceof String) f.pushVal(Alt.of(Sym.constant(c, "Ljava/lang/String;")), 1);
                else if (c instanceof Type t) f.pushVal(Alt.of(Sym.constant(c, t.getDescriptor())), 1);
                else f.pushVal(Alt.UNKNOWN, 1);
                return true;
            }

            case Opcodes.ILOAD: case Opcodes.FLOAD: case Opcodes.ALOAD:
                f.pushVal(f.local(((VarInsnNode) in).var), 1); return true;
            case Opcodes.LLOAD: case Opcodes.DLOAD:
                f.pushVal(f.local(((VarInsnNode) in).var), 2); return true;
            case Opcodes.IALOAD: case Opcodes.FALOAD: case Opcodes.AALOAD: case Opcodes.BALOAD:
            case Opcodes.CALOAD: case Opcodes.SALOAD:
                f.popSlots(2); f.pushVal(Alt.UNKNOWN, 1); return true;
            case Opcodes.LALOAD: case Opcodes.DALOAD:
                f.popSlots(2); f.pushVal(Alt.UNKNOWN, 2); return true;
            case Opcodes.ISTORE: case Opcodes.FSTORE: case Opcodes.ASTORE:
            case Opcodes.LSTORE: case Opcodes.DSTORE:
                f.setLocal(((VarInsnNode) in).var, f.popVal()); return true;
            case Opcodes.IASTORE: case Opcodes.FASTORE: case Opcodes.AASTORE: case Opcodes.BASTORE:
            case Opcodes.CASTORE: case Opcodes.SASTORE:
                f.popSlots(3); return true;
            case Opcodes.LASTORE: case Opcodes.DASTORE:
                f.popSlots(4); return true;
            case Opcodes.IINC: {
                IincInsnNode ii = (IincInsnNode) in;
                f.setLocal(ii.var, Alt.binary(Opcodes.IADD, f.local(ii.var),
                        Alt.of(Sym.constant(ii.incr, "I"))));
                return true;
            }

            case Opcodes.POP: f.popSlots(1); return true;
            case Opcodes.POP2: f.popSlots(2); return true;
            case Opcodes.DUP: f.pushSlot(f.slot(f.sp - 1)); return true;
            case Opcodes.DUP_X1: {
                Alt v1 = f.slot(f.sp - 1), v2 = f.slot(f.sp - 2);
                f.sp -= 2; f.pushSlot(v1); f.pushSlot(v2); f.pushSlot(v1); return true;
            }
            case Opcodes.DUP_X2: {
                Alt v1 = f.slot(f.sp - 1), v2 = f.slot(f.sp - 2), v3 = f.slot(f.sp - 3);
                f.sp -= 3; f.pushSlot(v1); f.pushSlot(v3); f.pushSlot(v2); f.pushSlot(v1); return true;
            }
            case Opcodes.DUP2: {
                Alt v1 = f.slot(f.sp - 1), v2 = f.slot(f.sp - 2);
                f.pushSlot(v2); f.pushSlot(v1); return true;
            }
            case Opcodes.DUP2_X1: {
                Alt v1 = f.slot(f.sp - 1), v2 = f.slot(f.sp - 2), v3 = f.slot(f.sp - 3);
                f.sp -= 3; f.pushSlot(v2); f.pushSlot(v1); f.pushSlot(v3); f.pushSlot(v2); f.pushSlot(v1);
                return true;
            }
            case Opcodes.DUP2_X2: {
                Alt v1 = f.slot(f.sp - 1), v2 = f.slot(f.sp - 2);
                Alt v3 = f.slot(f.sp - 3), v4 = f.slot(f.sp - 4);
                f.sp -= 4;
                f.pushSlot(v2); f.pushSlot(v1); f.pushSlot(v4); f.pushSlot(v3); f.pushSlot(v2); f.pushSlot(v1);
                return true;
            }
            case Opcodes.SWAP: {
                Alt v1 = f.slot(f.sp - 1), v2 = f.slot(f.sp - 2);
                f.setSlot(f.sp - 1, v2); f.setSlot(f.sp - 2, v1); return true;
            }
            default:
        }

        if (OpNames.isBinary(op)) {
            Alt b = f.popVal();
            Alt a = f.popVal();
            f.pushVal(Alt.binary(op, a, b), width(OpNames.resultDesc(op)));
            return true;
        }
        if (OpNames.isUnary(op)) {
            f.pushVal(Alt.unary(op, f.popVal()), width(OpNames.resultDesc(op)));
            return true;
        }
        if (OpNames.isConversion(op)) {
            f.pushVal(Alt.cast(OpNames.resultDesc(op), f.popVal()), width(OpNames.resultDesc(op)));
            return true;
        }
        if (isConditionalJump(op)) {
            f.popVal();
            if (op >= Opcodes.IF_ICMPEQ && op <= Opcodes.IF_ACMPNE) f.popVal();
            return true;
        }
        if (op == Opcodes.GOTO) return true;

        switch (op) {
            case Opcodes.LCMP: case Opcodes.FCMPL: case Opcodes.FCMPG:
            case Opcodes.DCMPL: case Opcodes.DCMPG:
                f.popSlots(op == Opcodes.LCMP || op == Opcodes.DCMPL || op == Opcodes.DCMPG ? 4 : 2);
                f.pushVal(Alt.UNKNOWN, 1);
                return true;

            case Opcodes.GETFIELD: {
                FieldInsnNode fi = (FieldInsnNode) in;
                Alt recv = f.popVal();
                f.pushVal(Alt.field(false, fi.owner, fi.name, fi.desc, recv), width(fi.desc));
                return true;
            }
            case Opcodes.PUTFIELD: {
                f.popVal();
                f.popVal();
                return true;
            }
            case Opcodes.GETSTATIC: {
                FieldInsnNode fi = (FieldInsnNode) in;
                f.pushVal(Alt.field(true, fi.owner, fi.name, fi.desc, null), width(fi.desc));
                return true;
            }
            case Opcodes.PUTSTATIC: {
                f.popVal();
                return true;
            }

            case Opcodes.INVOKEVIRTUAL: case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKESTATIC: case Opcodes.INVOKEINTERFACE: {
                MethodInsnNode mi = (MethodInsnNode) in;
                Type[] params = Type.getArgumentTypes(mi.desc);
                List<Alt> args = new ArrayList<>(params.length);
                for (int i = params.length - 1; i >= 0; i--) args.add(0, f.popVal());
                boolean stat = op == Opcodes.INVOKESTATIC;
                Alt recv = stat ? null : f.popVal();
                Type ret = Type.getReturnType(mi.desc);
                if (ret.getSort() == Type.VOID) return true;
                f.pushVal(Alt.call(stat, mi.owner, mi.name, mi.desc, recv, args), ret.getSize());
                return true;
            }
            case Opcodes.INVOKEDYNAMIC: {
                InvokeDynamicInsnNode di = (InvokeDynamicInsnNode) in;
                Type[] params = Type.getArgumentTypes(di.desc);
                for (int i = params.length - 1; i >= 0; i--) f.popVal();
                Type ret = Type.getReturnType(di.desc);
                if (ret.getSort() == Type.VOID) return true;
                f.pushVal(Alt.UNKNOWN, ret.getSize());
                return true;
            }

            case Opcodes.NEW: f.pushVal(Alt.UNKNOWN, 1); return true;
            case Opcodes.NEWARRAY: case Opcodes.ANEWARRAY:
                f.popVal(); f.pushVal(Alt.UNKNOWN, 1); return true;
            case Opcodes.MULTIANEWARRAY: {
                MultiANewArrayInsnNode m = (MultiANewArrayInsnNode) in;
                f.popSlots(m.dims);
                f.pushVal(Alt.UNKNOWN, 1);
                return true;
            }
            case Opcodes.ARRAYLENGTH: f.popVal(); f.pushVal(Alt.UNKNOWN, 1); return true;
            case Opcodes.CHECKCAST:
                f.pushVal(Alt.cast(((TypeInsnNode) in).desc, f.popVal()), 1);
                return true;
            case Opcodes.INSTANCEOF: f.popVal(); f.pushVal(Alt.UNKNOWN, 1); return true;
            case Opcodes.MONITORENTER: case Opcodes.MONITOREXIT: f.popVal(); return true;
            case Opcodes.ATHROW: f.popVal(); return false;

            case Opcodes.IRETURN: case Opcodes.LRETURN: case Opcodes.ARETURN: case Opcodes.DRETURN:
                f.popVal();
                return false;
            case Opcodes.FRETURN:
                for (Sym s : f.popVal().options()) returns.add(s);
                return false;
            case Opcodes.RETURN:
                return false;

            default:
                throw new IllegalStateException("unhandled opcode " + op);
        }
    }

    private static int width(String desc) {
        if (desc == null) return 1;
        char c = desc.charAt(0);
        return (c == 'J' || c == 'D') ? 2 : 1;
    }

    static final class Frame {
        final Alt[] locals;
        final Alt[] stack;
        int sp;

        private Frame(Alt[] locals, Alt[] stack, int sp) {
            this.locals = locals;
            this.stack = stack;
            this.sp = sp;
        }

        static Frame initial(MethodNode m, boolean isStatic) {
            Alt[] lc = new Alt[Math.max(m.maxLocals, 8)];
            int slot = 0;
            if (!isStatic) { lc[0] = Alt.of(Sym.thisSym()); slot = 1; }
            for (Type t : Type.getArgumentTypes(m.desc)) {
                lc[slot] = Alt.of(Sym.param(slot));
                if (t.getSize() == 2) lc[slot + 1] = null;
                slot += t.getSize();
            }
            return new Frame(lc, new Alt[Math.max(m.maxStack, 16)], 0);
        }

        Frame copy() {
            return new Frame(locals.clone(), stack.clone(), sp);
        }

        Alt slot(int i) {
            if (i < 0 || i >= sp) return Alt.UNKNOWN;
            Alt v = stack[i];
            return v == null ? Alt.UNKNOWN : v;
        }

        void setSlot(int i, Alt s) {
            if (i < 0 || i >= stack.length) return;
            stack[i] = s;
            if (i >= sp) sp = i + 1;
        }

        void pushSlot(Alt s) {
            if (sp >= stack.length) return;
            stack[sp++] = s;
        }

        void pushVal(Alt s, int width) {
            pushSlot(s);
            if (width == 2) pushSlot(null);
        }

        Alt popVal() {
            if (sp <= 0) return Alt.UNKNOWN;
            Alt v = stack[--sp];
            if (v == null) {
                if (sp <= 0) return Alt.UNKNOWN;
                v = stack[--sp];
            }
            return v == null ? Alt.UNKNOWN : v;
        }

        void popSlots(int n) {
            sp = Math.max(0, sp - n);
        }

        Alt local(int i) {
            if (i < 0 || i >= locals.length) return Alt.UNKNOWN;
            Alt s = locals[i];
            return s == null ? Alt.UNKNOWN : s;
        }

        void setLocal(int i, Alt s) {
            if (i < 0 || i >= locals.length) return;
            locals[i] = s == null ? Alt.UNKNOWN : s;
        }
    }
}
