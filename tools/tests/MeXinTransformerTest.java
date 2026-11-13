import org.bytechen.rottenruins.mexin.MeXinSelfTest;
import org.bytechen.rottenruins.mexin.MeXinSpec;
import org.bytechen.rottenruins.mexin.MeXinTransformer;
import org.bytechen.rottenruins.mexin.MeXinType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MeXin 注入器的独立验证（不启动游戏、不依赖 launch plugin 服务发现）。
 *
 * <p>做法：把 {@code MeXinSelfTest$Sample} 的字节码读成 ClassNode → 交给
 * {@link MeXinTransformer} 注入 → 写回字节 → 用新的 ClassLoader 加载 → 逐个断言四种模式。</p>
 *
 * <p>运行（改掉 -cp 里的 4 个 jar 路径即可）：
 * <pre>
 * javac -cp "&lt;modlauncher&gt;;&lt;asm&gt;;&lt;asm-tree&gt;;&lt;annotations&gt;;build/classes/java/main" \
 *       -d build/mexin-test tools/tests/MeXinTransformerTest.java
 * java  -cp "&lt;同上&gt;;build/mexin-test" MeXinTransformerTest
 * </pre>
 * </p>
 */
public final class MeXinTransformerTest {

    private static final String SAMPLE = "org/bytechen/rottenruins/mexin/MeXinSelfTest$Sample";
    private static final String HOST = "org/bytechen/rottenruins/mexin/MeXinSelfTest";

    private static int failures;

    public static void main(String[] args) throws Exception {
        List<MeXinSpec> specs = List.of(
                new MeXinSpec(SAMPLE, "compute", "", "(I)I", MeXinType.MIX_BEFORE_ENTER, false, "",
                        HOST, "onEnter", "(L" + SAMPLE + ";I)V"),
                new MeXinSpec(SAMPLE, "compute", "", "(I)I", MeXinType.MIX_BEFORE_RETURN, false, "",
                        HOST, "onReturn", "(L" + SAMPLE + ";I)V"),
                new MeXinSpec(SAMPLE, "compute", "", "(I)I", MeXinType.BODY, false, "",
                        HOST, "onBody", "(L" + SAMPLE + ";ILorg/bytechen/rottenruins/mexin/MeXinCall;)V"),
                new MeXinSpec(SAMPLE, "overriddenResult", "", "()I", MeXinType.BODY, false, "",
                        HOST, "onBodyOverride", "(L" + SAMPLE + ";Lorg/bytechen/rottenruins/mexin/MeXinCall;)V"),
                new MeXinSpec(SAMPLE, "replaced", "", "()I", MeXinType.EXCHANGE, false, "",
                        HOST, "onExchange", "(L" + SAMPLE + ";)I"),
                new MeXinSpec(SAMPLE, "wide", "", "(IJDLjava/lang/String;)D", MeXinType.BODY, false, "",
                        HOST, "onWide", "(L" + SAMPLE + ";IJDLjava/lang/String;"
                                + "Lorg/bytechen/rottenruins/mexin/MeXinCall;)V"));

        byte[] original = readClass(SAMPLE);
        byte[] transformed = transform(original, specs);

        // 用"父加载器 = 当前 classpath"的新加载器加载改过的 Sample
        Path out = Path.of("build", "mexin-test-classes");
        writeClass(out, SAMPLE, transformed);
        try (ChildFirstLoader loader = new ChildFirstLoader(
                new java.net.URL[]{out.toUri().toURL()},
                MeXinTransformerTest.class.getClassLoader())) {
            Class<?> sampleClass = Class.forName(SAMPLE.replace('/', '.'), true, loader);
            Object sample = sampleClass.getDeclaredConstructor().newInstance();

            MeXinSelfTest.reset();
            Method compute = sampleClass.getMethod("compute", int.class);
            Object computed = compute.invoke(sample, 5);
            check("MIX_BEFORE_ENTER 调用次数", MeXinSelfTest.enterCalls == 1, MeXinSelfTest.enterCalls);
            check("MIX_BEFORE_ENTER 看到入参", MeXinSelfTest.lastEnterArg == 5, MeXinSelfTest.lastEnterArg);
            check("BODY 调用次数", MeXinSelfTest.bodyCalls == 1, MeXinSelfTest.bodyCalls);
            check("MIX_BEFORE_RETURN 调用次数", MeXinSelfTest.returnCalls == 1, MeXinSelfTest.returnCalls);
            check("BODY 改返回值（10+5=15）", Integer.valueOf(15).equals(computed), computed);

            Object overridden = sampleClass.getMethod("overriddenResult").invoke(sample);
            check("BODY 改常量返回值（1→42）", Integer.valueOf(42).equals(overridden), overridden);

            Object replaced = sampleClass.getMethod("replaced").invoke(sample);
            check("EXCHANGE 替换方法体（返回 7）", Integer.valueOf(7).equals(replaced), replaced);
            check("EXCHANGE 钩子调用次数", MeXinSelfTest.exchangeCalls == 1, MeXinSelfTest.exchangeCalls);

            // 宽签名：long/double 各占两个槽位，且返回值是 double（装箱/拆箱都走一遍）
            Object wideResult = sampleClass
                    .getMethod("wide", int.class, long.class, double.class, String.class)
                    .invoke(sample, 1, 2L, 0.5d, "abcd");
            double expectedWide = (1 + 2L + 0.5d + 4) * 2;
            check("BODY 宽签名调用次数", MeXinSelfTest.wideCalls == 1, MeXinSelfTest.wideCalls);
            check("BODY 宽签名看到 long 参数", MeXinSelfTest.lastWideLong == 2L, MeXinSelfTest.lastWideLong);
            check("BODY 宽签名改 double 返回值", Double.valueOf(expectedWide).equals(wideResult), wideResult);
        }

        // 兼容性：再来一遍，这次写盘时<b>不</b>重算 maxStack/maxLocals，
        // 完全靠注入器自己维护的值 —— 相当于验证"换个不帮忙算 maxs 的环境也不会 VerifyError"。
        // 这一轮钩子宿主也要跟着隔离加载（否则两个加载器各有一份 Sample，会触发约束冲突）。
        Path rawOut = Path.of("build", "mexin-test-classes-raw");
        writeClass(rawOut, SAMPLE, transformRaw(original, specs));
        writeClass(rawOut, HOST, readClass(HOST));
        try (IsolatedLoader loader = new IsolatedLoader(
                new java.net.URL[]{rawOut.toUri().toURL()},
                MeXinTransformerTest.class.getClassLoader())) {
            Class<?> sampleClass = Class.forName(SAMPLE.replace('/', '.'), true, loader);
            Object sample = sampleClass.getDeclaredConstructor().newInstance();
            Object computed = sampleClass.getMethod("compute", int.class).invoke(sample, 5);
            Object wide = sampleClass
                    .getMethod("wide", int.class, long.class, double.class, String.class)
                    .invoke(sample, 1, 2L, 0.5d, "abcd");
            check("自带 maxs 的字节码通过校验（compute）", Integer.valueOf(15).equals(computed), computed);
            check("自带 maxs 的字节码通过校验（wide）",
                    Double.valueOf((1 + 2L + 0.5d + 4) * 2).equals(wide), wide);
        }

        checkIncludmy();

        System.out.println(failures == 0 ? "MeXin 注入自测：全部通过 ✔" : "MeXin 注入自测：失败 " + failures + " 项 ✘");
        if (failures > 0) {
            System.exit(1);
        }
    }

    /**
     * {@code includmy = true}：目标扩成<b>总包</b>（根包 + 全部子包）。
     * <p>合成类验证：总包根下的 {@code Foo}、子包里的 {@code Bar}、更深的 {@code sub/deep/Baz}
     * 都注入；别的包（{@code other/Outside}）不动。</p>
     */
    private static void checkIncludmy() {
        String root = "demo/root";
        String hookOwner = root + "/hooks/Hooks";
        String callDesc = "Lorg/bytechen/rottenruins/mexin/MeXinCall;";
        MeXinSpec spec = new MeXinSpec(hookOwner, "value", "", "()I", MeXinType.BODY, false, root,
                hookOwner, "onValue", "(Ljava/lang/Object;" + callDesc + ")V");

        Map<String, Set<String>> packageIndex = new LinkedHashMap<>();
        packageIndex.put(root, new LinkedHashSet<>(List.of(
                root + "/Foo", root + "/sub/Bar", root + "/sub/deep/Baz", hookOwner)));
        MeXinTransformer transformer = MeXinTransformer.forTest(List.of(spec), packageIndex);

        boolean foo = injectsHook(synthetic(root + "/Foo"), transformer);
        boolean bar = injectsHook(synthetic(root + "/sub/Bar"), transformer);
        boolean baz = injectsHook(synthetic(root + "/sub/deep/Baz"), transformer);
        boolean hooks = injectsHook(synthetic(hookOwner), transformer);
        boolean outside = injectsHook(synthetic("demo/other/Outside"), transformer);
        check("includmy：总包根下的 Foo 被注入", foo, foo);
        check("includmy：子包里的 Bar 被注入", bar, bar);
        check("includmy：更深的子包 Baz 被注入", baz, baz);
        check("includmy：钩子自己所在类也被注入", hooks, hooks);
        check("includmy：总包外的 Outside 不受影响", !outside, outside);
    }

    /** 造一个只有 {@code public int value() { return 1; }} 的类（没有注解、没有依赖）。 */
    private static org.objectweb.asm.tree.ClassNode synthetic(String internalName) {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        node.version = 52;
        node.access = org.objectweb.asm.Opcodes.ACC_PUBLIC;
        node.name = internalName;
        node.superName = "java/lang/Object";
        org.objectweb.asm.tree.MethodNode method = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC, "value", "()I", null, null);
        method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_1));
        method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.IRETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        node.methods.add(method);
        return node;
    }

    /** 跑一遍注入，看 {@code value()} 里有没有对钩子的 INVOKESTATIC。 */
    private static boolean injectsHook(org.objectweb.asm.tree.ClassNode node,
                                       MeXinTransformer transformer) {
        transformer.transform(node, null);
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null; insn = insn.getNext()) {
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && "onValue".equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 先查自己的 URL（改过的 Sample），其余走父加载器（钩子所在的 MeXinSelfTest）。 */
    private static final class ChildFirstLoader extends java.net.URLClassLoader {
        private ChildFirstLoader(java.net.URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.bytechen.rottenruins.mexin.MeXinSelfTest$")) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
            return super.loadClass(name, resolve);
        }
    }

    /** 连钩子宿主一起隔离：{@code MeXinSelfTest} 与 {@code Sample} 都在自己的 URL 里。 */
    private static final class IsolatedLoader extends java.net.URLClassLoader {
        private IsolatedLoader(java.net.URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.bytechen.rottenruins.mexin.MeXinSelfTest")) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
            return super.loadClass(name, resolve);
        }
    }

    private static byte[] transform(byte[] original, List<MeXinSpec> specs) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        MeXinTransformer transformer = MeXinTransformer.forTest(specs);
        ClassNode result = transformer.transform(node, null);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        result.accept(writer);
        return writer.toByteArray();
    }

    /** 和上面一样，但写盘时保留注入器自己算的 maxStack / maxLocals（不重算）。 */
    private static byte[] transformRaw(byte[] original, List<MeXinSpec> specs) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        MeXinTransformer transformer = MeXinTransformer.forTest(specs);
        ClassNode result = transformer.transform(node, null);
        ClassWriter writer = new ClassWriter(0);
        result.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] readClass(String internalName) throws Exception {
        try (InputStream in = MeXinTransformerTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                throw new IllegalStateException("找不到类资源：" + internalName);
            }
            return in.readAllBytes();
        }
    }

    private static void writeClass(Path root, String internalName, byte[] bytes) throws Exception {
        Path file = root.resolve(internalName + ".class");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.write(file, bytes);
    }

    private static void check(String name, boolean ok, Object actual) {
        if (ok) {
            System.out.println("  [OK]   " + name);
        } else {
            failures++;
            System.out.println("  [FAIL] " + name + "（实际 " + actual + "）");
        }
    }
}
