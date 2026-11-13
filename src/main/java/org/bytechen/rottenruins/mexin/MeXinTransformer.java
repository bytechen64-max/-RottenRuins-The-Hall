package org.bytechen.rottenruins.mexin;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MeXin 的字节码注入器 —— 把钩子调用塞进目标类的方法里。
 *
 * <p>四种模式发射的都是<b>直线代码</b>（不新增跳转目标），所以不需要重算
 * StackMapTable，ModLauncher 重新写盘时用 COMPUTE_MAXS 就够。</p>
 *
 * <p>本类运行在 <b>launch plugin 层</b>：不能引用模组自己的类，
 * {@link MeXinCall} 只用内部名字符串引用。</p>
 */
public final class MeXinTransformer implements ITransformer<ClassNode>, Opcodes {

    /** {@link MeXinCall} 的内部名 —— 改类名时这里要同步。 */
    static final String CALL_INTERNAL = "org/bytechen/rottenruins/mexin/MeXinCall";
    static final String CALL_DESC = "L" + CALL_INTERNAL + ";";
    private static final String CALL_INIT_DESC = "(Ljava/lang/Object;)V";
    private static final String CALL_RESULT_NAME = "result";
    private static final String CALL_RESULT_DESC = "()Ljava/lang/Object;";

    /** 单次注入日志上限：整合包里子类可能很多，逐条打会把日志刷爆。 */
    private static final int LOG_LIMIT = 20;

    private final List<MeXinSpec> specs;
    private final Map<String, String> superIndex;
    private final Map<String, Set<String>> subclassesOf;
    /** 包名 → 该包下的类（{@code includmy = true} 时的目标集合）。 */
    private final Map<String, Set<String>> packageIndex;
    /** 已注入过的"目标方法 + 钩子"组合：保证同一个位置只注入一次。 */
    private final Set<String> injectedOnce = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 累计注入次数（只用于统计与日志限流）。 */
    private final java.util.concurrent.atomic.AtomicInteger applied =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * @param specs        扫描到的全部转换请求
     * @param superIndex   类名 → 父类名（含启动时预扫描到的，用于子类判定）
     * @param subclassesOf 目标类 → 已知子类集合（含传递闭包）
     * @param packageIndex 包名 → 该包下的类（同包注入用）
     */
    MeXinTransformer(@NotNull List<MeXinSpec> specs,
                     @NotNull Map<String, String> superIndex,
                     @NotNull Map<String, Set<String>> subclassesOf,
                     @NotNull Map<String, Set<String>> packageIndex) {
        this.specs = List.copyOf(specs);
        this.superIndex = new HashMap<>(superIndex);
        this.subclassesOf = new HashMap<>(subclassesOf);
        this.packageIndex = new HashMap<>(packageIndex);
    }

    /** 测试 / 工具用入口：不带子类与包索引（正常路径由 {@link MeXinLuncher} 提供）。 */
    public static @NotNull MeXinTransformer forTest(@NotNull List<MeXinSpec> specs) {
        return new MeXinTransformer(specs, Map.of(), Map.of(), Map.of());
    }

    /** 测试 / 工具用入口：带包索引。 */
    public static @NotNull MeXinTransformer forTest(@NotNull List<MeXinSpec> specs,
                                                    @NotNull Map<String, Set<String>> packageIndex) {
        return new MeXinTransformer(specs, Map.of(), Map.of(), packageIndex);
    }

    @Override
    public @NotNull Set<Target> targets() {
        Set<Target> targets = new HashSet<>();
        for (MeXinSpec spec : specs) {
            targets.add(Target.targetClass(spec.targetClass()));
            for (String sub : subclassesOf.getOrDefault(spec.targetClass(), Set.of())) {
                targets.add(Target.targetClass(sub));
            }
            if (spec.hasPackageTarget()) {
                for (String inPackage : packageIndex.getOrDefault(spec.packageTarget(), Set.of())) {
                    targets.add(Target.targetClass(inPackage));
                }
            }
        }
        return targets;
    }

    @Override
    public ClassNode transform(@NotNull ClassNode node, @NotNull ITransformerVotingContext context) {
        if (node.superName != null) {
            superIndex.putIfAbsent(node.name, node.superName);
        }
        // 兼容性：任何异常都不能把类改坏、更不能让游戏起不来 —— 出错就原样放行
        try {
            applyAll(node);
        } catch (Throwable t) {
            MeXinLog.error("注入 " + node.name + " 时出错，该类保持原样", t);
        }
        return node;
    }

    private void applyAll(@NotNull ClassNode node) {
        for (MeXinSpec spec : specs) {
            if (!matchesClass(node, spec)) {
                continue;
            }
            for (MethodNode method : new ArrayList<>(node.methods)) {
                if (method.name.startsWith("<")) {
                    continue;   // <init> / <clinit>
                }
                // 桥接方法与编译器生成的方法被跳过：给它们注入会和真实实现重复触发
                if ((method.access & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0) {
                    continue;
                }
                if (!spec.matchesMethodName(method.name)) {
                    continue;
                }
                if (!spec.targetDesc().isEmpty() && !method.desc.equals(spec.targetDesc())) {
                    continue;
                }
                inject(node, method, spec);
            }
        }
    }

    // 匹配

    private boolean matchesClass(@NotNull ClassNode node, @NotNull MeXinSpec spec) {
        if (node.name.equals(spec.targetClass())) {
            return true;
        }
        if (spec.inPackage(node.name)) {
            return true;
        }
        return spec.includeExtend()
                && subclassesOf.getOrDefault(spec.targetClass(), Set.of()).contains(node.name);
    }

    // 注入

    private void inject(@NotNull ClassNode node, @NotNull MethodNode method, @NotNull MeXinSpec spec) {
        if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0 || method.instructions == null) {
            MeXinLog.warn("目标方法是 abstract/native，跳过：" + spec);
            return;
        }
        // 兼容性：同一个目标方法同一个钩子只注入一次（防止重复扫描 / 多个 spec 撞车造成双调用）
        String key = node.name + "#" + method.name + method.desc + "@" + spec.hostClass() + "#"
                + spec.hookName();
        if (!injectedOnce.add(key)) {
            return;
        }
        String expected = expectedHookDesc(spec, method);
        if (!hookSignatureMatches(node, method, spec, expected)) {
            MeXinLog.warn("钩子签名不匹配，跳过 " + spec + "\n  期望 " + expected
                    + "\n  实际 " + spec.hookDesc());
            return;
        }
        // 兼容性：MIX_BEFORE_RETURN 在"没有 return 的方法"上什么都没得改 → 只记一次警告
        if (spec.type() == MeXinType.MIX_BEFORE_RETURN && !hasReturn(method)) {
            MeXinLog.warn("目标方法没有 return 指令（无限循环？），跳过 " + spec);
            return;
        }
        switch (spec.type()) {
            case MIX_BEFORE_ENTER -> injectBeforeEnter(method, spec);
            case MIX_BEFORE_RETURN -> injectBeforeReturn(method, spec);
            case BODY -> injectBody(method, spec);
            case EXCHANGE -> injectExchange(method, spec);
        }
        int count = applied.incrementAndGet();
        // 兼容性：日志限流（整合包里子类可能很多，逐条打会把日志刷爆）
        if (count <= LOG_LIMIT) {
            MeXinLog.info(node.name + "#" + method.name + method.desc + " <- "
                    + spec.hostClass().replace('/', '.') + "#" + spec.hookName()
                    + " [" + spec.type() + "]");
        } else if (count == LOG_LIMIT + 1) {
            MeXinLog.info("（注入日志超过 " + LOG_LIMIT + " 条，后续只统计不打印）");
        }
    }

    private static boolean hasReturn(@NotNull MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op >= IRETURN && op <= RETURN) {
                return true;
            }
        }
        return false;
    }

    /** 按模式推导钩子的期望描述符（{@code this} 槽按 {@code targetClass} 算）。 */
    private static String expectedHookDesc(@NotNull MeXinSpec spec, @NotNull MethodNode method) {
        StringBuilder sb = new StringBuilder("(");
        if ((method.access & ACC_STATIC) == 0) {
            sb.append('L').append(spec.targetClass()).append(';');
        }
        for (Type arg : Type.getArgumentTypes(method.desc)) {
            sb.append(arg.getDescriptor());
        }
        if (spec.type() == MeXinType.BODY) {
            sb.append(CALL_DESC);
        }
        sb.append(')');
        sb.append(spec.type() == MeXinType.EXCHANGE ? Type.getReturnType(method.desc).getDescriptor() : "V");
        return sb.toString();
    }

    /**
     * 校验钩子签名。和"逐字符比对期望描述符"等价，但放宽了 {@code this} 槽：
     * 同包注入时要往多个不同的类里插，钩子第一个参数只能写成它们的公共父类型
     * （最省事是 {@code Object}），所以这里按<b>可赋值性</b>判断，而不是要求正好等于目标类。
     */
    private boolean hookSignatureMatches(@NotNull ClassNode node, @NotNull MethodNode method,
                                         @NotNull MeXinSpec spec, @NotNull String expected) {
        if (expected.equals(spec.hookDesc())) {
            return true;
        }
        Type[] declared = Type.getArgumentTypes(spec.hookDesc());
        Type[] targets = Type.getArgumentTypes(method.desc);
        boolean isStatic = (method.access & ACC_STATIC) != 0;
        int index = 0;
        if (!isStatic) {
            if (declared.length == 0) {
                return false;
            }
            if (!thisParamAccepts(node.name, declared[0])) {
                return false;
            }
            index = 1;
        }
        for (Type target : targets) {
            if (index >= declared.length || !declared[index].equals(target)) {
                return false;
            }
            index++;
        }
        if (spec.type() == MeXinType.BODY) {
            if (index >= declared.length
                    || !CALL_DESC.equals(declared[index].getDescriptor())) {
                return false;
            }
            index++;
        }
        if (index != declared.length) {
            return false;
        }
        Type wantReturn = spec.type() == MeXinType.EXCHANGE
                ? Type.getReturnType(method.desc) : Type.VOID_TYPE;
        return wantReturn.equals(Type.getReturnType(spec.hookDesc()));
    }

    /** 钩子第一参数能不能接住 {@code className} 的实例（{@code Object} 恒可，其余按父类索引判断）。 */
    private boolean thisParamAccepts(@NotNull String className, @NotNull Type declared) {
        if (declared.getSort() != Type.OBJECT) {
            return false;
        }
        String want = declared.getInternalName();
        if ("java/lang/Object".equals(want) || want.equals(className)) {
            return true;
        }
        Set<String> seen = new HashSet<>();
        String current = className;
        while (seen.add(current)) {
            String parent = superIndex.get(current);
            if (parent == null) {
                return false;
            }
            if (parent.equals(want)) {
                return true;
            }
            current = parent;
        }
        return false;
    }

    private void injectBeforeEnter(@NotNull MethodNode method, @NotNull MeXinSpec spec) {
        InsnList code = new InsnList();
        pushInstanceAndArgs(code, method, spec);
        code.add(callHook(spec));
        method.instructions.insert(code);
        ensureStack(method, 2);
    }

    private void injectBeforeReturn(@NotNull MethodNode method, @NotNull MeXinSpec spec) {
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op >= IRETURN && op <= RETURN) {
                returns.add(insn);
            }
        }
        for (AbstractInsnNode ret : returns) {
            InsnList code = new InsnList();
            pushInstanceAndArgs(code, method, spec);
            code.add(callHook(spec));
            method.instructions.insertBefore(ret, code);
        }
        ensureStack(method, 2);
    }

    /** 原方法体之后、返回之前：可以读改返回值。 */
    private void injectBody(@NotNull MethodNode method, @NotNull MeXinSpec spec) {
        Type returnType = Type.getReturnType(method.desc);
        List<AbstractInsnNode> returnNodes = new ArrayList<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op >= IRETURN && op <= RETURN) {
                returnNodes.add(insn);
            }
        }
        for (AbstractInsnNode returnNode : returnNodes) {
            int returnOp = returnNode.getOpcode();
            boolean isVoid = returnOp == RETURN;
            InsnList code = new InsnList();
            int callLocal = method.maxLocals++;
            int valueLocal = method.maxLocals;
            method.maxLocals += (returnOp == LRETURN || returnOp == DRETURN) ? 2 : 1;

            if (!isVoid) {
                code.add(new VarInsnNode(storeOp(returnOp), valueLocal));
            }
            code.add(new TypeInsnNode(NEW, CALL_INTERNAL));
            code.add(new InsnNode(DUP));
            if (isVoid) {
                code.add(new InsnNode(ACONST_NULL));
            } else {
                code.add(new VarInsnNode(loadOp(returnOp), valueLocal));
                box(code, returnType);
            }
            code.add(new MethodInsnNode(INVOKESPECIAL, CALL_INTERNAL, "<init>", CALL_INIT_DESC, false));
            code.add(new VarInsnNode(ASTORE, callLocal));

            pushInstanceAndArgs(code, method, spec);
            code.add(new VarInsnNode(ALOAD, callLocal));
            code.add(callHook(spec));

            if (!isVoid) {
                code.add(new VarInsnNode(ALOAD, callLocal));
                code.add(new MethodInsnNode(INVOKEVIRTUAL, CALL_INTERNAL, CALL_RESULT_NAME,
                        CALL_RESULT_DESC, false));
                unbox(code, returnType);
            }
            method.instructions.insertBefore(returnNode, code);
        }
        ensureStack(method, 4);
    }

    /** 整段替换：直接返回钩子的返回值。 */
    private void injectExchange(@NotNull MethodNode method, @NotNull MeXinSpec spec) {
        Type ret = Type.getReturnType(method.desc);
        InsnList code = new InsnList();
        pushInstanceAndArgs(code, method, spec);
        code.add(callHook(spec));
        code.add(new InsnNode(ret.getOpcode(IRETURN)));
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.instructions.add(code);
        method.maxLocals = 0;
        method.maxStack = 0;
        ensureStack(method, 4);
        // 原局部变量表已失效，且注入代码用不到局部变量 → maxLocals 重新按参数算
        int locals = (method.access & ACC_STATIC) == 0 ? 1 : 0;
        for (Type arg : Type.getArgumentTypes(method.desc)) {
            locals += arg.getSize();
        }
        method.maxLocals = Math.max(locals, method.maxLocals);
    }

    // 小工具

    private static MethodInsnNode callHook(@NotNull MeXinSpec spec) {
        return new MethodInsnNode(INVOKESTATIC, spec.hostClass(), spec.hookName(), spec.hookDesc(), false);
    }

    /** 把 {@code this}（非静态方法）与全部参数压栈。 */
    private static void pushInstanceAndArgs(@NotNull InsnList code, @NotNull MethodNode method,
                                            @NotNull MeXinSpec spec) {
        boolean isStatic = (method.access & ACC_STATIC) != 0;
        int local = isStatic ? 0 : 1;
        if (!isStatic) {
            code.add(new VarInsnNode(ALOAD, 0));
        }
        for (Type arg : Type.getArgumentTypes(method.desc)) {
            code.add(new VarInsnNode(arg.getOpcode(ILOAD), local));
            local += arg.getSize();
        }
    }

    /**
     * 注入序列真正需要的栈深：{@code this} + 全部参数 + {@code extra} 个额外槽位。
     * <p>只加一个固定常数是不够的 —— 参数很多的宽方法（或者带 long/double 的方法）
     * 会让 {@code maxStack} 算小，写盘后被校验器判成 {@code VerifyError}。</p>
     */
    private static void ensureStack(@NotNull MethodNode method, int extra) {
        int needed = (method.access & ACC_STATIC) == 0 ? 1 : 0;
        for (Type arg : Type.getArgumentTypes(method.desc)) {
            needed += arg.getSize();
        }
        method.maxStack = Math.max(method.maxStack + extra, needed + extra);
        method.maxLocals = Math.max(method.maxLocals, 8);
    }

    /** xRETURN 的 opcode → 对应的 xSTORE / xLOAD（T 类型共用一个）。 */
    private static int storeOp(int returnOpcode) {
        return returnOpcode - (IRETURN - ISTORE);
    }

    private static int loadOp(int returnOpcode) {
        return returnOpcode - (IRETURN - ILOAD);
    }

    private static void box(@NotNull InsnList code, @NotNull Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> callValueOf(code, "java/lang/Boolean", "(Z)Ljava/lang/Boolean;");
            case Type.BYTE -> callValueOf(code, "java/lang/Byte", "(B)Ljava/lang/Byte;");
            case Type.CHAR -> callValueOf(code, "java/lang/Character", "(C)Ljava/lang/Character;");
            case Type.SHORT -> callValueOf(code, "java/lang/Short", "(S)Ljava/lang/Short;");
            case Type.INT -> callValueOf(code, "java/lang/Integer", "(I)Ljava/lang/Integer;");
            case Type.FLOAT -> callValueOf(code, "java/lang/Float", "(F)Ljava/lang/Float;");
            case Type.LONG -> callValueOf(code, "java/lang/Long", "(J)Ljava/lang/Long;");
            case Type.DOUBLE -> callValueOf(code, "java/lang/Double", "(D)Ljava/lang/Double;");
            default -> {
                // 引用类型：已经可以直接当 Object
            }
        }
    }

    private static void callValueOf(@NotNull InsnList code, @NotNull String owner, @NotNull String desc) {
        code.add(new MethodInsnNode(INVOKESTATIC, owner, "valueOf", desc, false));
    }

    private static void unbox(@NotNull InsnList code, @NotNull Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> callUnbox(code, "java/lang/Boolean", "booleanValue", "()Z");
            case Type.BYTE -> callUnbox(code, "java/lang/Byte", "byteValue", "()B");
            case Type.CHAR -> callUnbox(code, "java/lang/Character", "charValue", "()C");
            case Type.SHORT -> callUnbox(code, "java/lang/Short", "shortValue", "()S");
            case Type.INT -> callUnbox(code, "java/lang/Integer", "intValue", "()I");
            case Type.FLOAT -> callUnbox(code, "java/lang/Float", "floatValue", "()F");
            case Type.LONG -> callUnbox(code, "java/lang/Long", "longValue", "()J");
            case Type.DOUBLE -> callUnbox(code, "java/lang/Double", "doubleValue", "()D");
            case Type.ARRAY -> code.add(new TypeInsnNode(CHECKCAST, type.getDescriptor()));
            case Type.OBJECT -> code.add(new TypeInsnNode(CHECKCAST, type.getInternalName()));
            default -> {
            }
        }
    }

    private static void callUnbox(@NotNull InsnList code, @NotNull String owner,
                                  @NotNull String name, @NotNull String desc) {
        code.add(new TypeInsnNode(CHECKCAST, owner));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, owner, name, desc, false));
    }

    @Override
    public @NotNull TransformerVoteResult castVote(@NotNull ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @NotNull
    List<MeXinSpec> specs() {
        return specs;
    }
}
