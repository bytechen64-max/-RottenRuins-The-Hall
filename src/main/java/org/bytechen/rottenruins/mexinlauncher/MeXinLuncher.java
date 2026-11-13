package org.bytechen.rottenruins.mexinlauncher;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * <b>MeXin 的 coremod 入口</b>（{@link ITransformationService} 实现）。
 *
 * <h3>为什么这样能生效</h3>
 * <p>Forge 的 {@code ModDirTransformerDiscoverer} 会扫描 <b>mods 目录</b>，凡是带
 * {@code META-INF/services/cpw.mods.modlauncher.api.ITransformationService} 的 jar
 * 都会被当作 launch plugin 加载 —— 于是不用加任何 JVM 参数就能在类加载时改字节码。
 * 注意：开发环境（{@code runClient}/{@code runGameTestServer}）走 classpath，
 * ModLauncher <b>不会</b>扫描它，所以运行时注入只在正式环境（jar 放进 mods）生效。</p>
 *
 * <h3>启动流程</h3>
 * <ol>
 *   <li>{@link #onLoad}：扫描自身输出（jar 或 classes 目录）里的 {@code @MeXin}，
 *       得到"钩子 → 目标方法"清单；顺便建立父类/接口索引；</li>
 *   <li>对 {@code includextend=true} 的目标算出传递子类集合；</li>
 *   <li>{@link #transformers()} 交给 {@link MeXinTransformer}，它把目标类（含子类）
 *       逐个注册给 ModLauncher，类加载时完成注入。</li>
 * </ol>
 */
public final class MeXinLuncher implements ITransformationService {

    private static final String ANNOTATION_DESC =
            "Lorg/bytechen/rottenruins/mexin/MeXin;";
    /** SRG 方法名形如 {@code m_21051_}。 */
    private static final Pattern SRG_PATTERN = Pattern.compile("^[a-z]_\\d+_$");
    /** {@code includmy = true} 时用的"总包"：本模组的根包（含全部子包）。可用 -Dmexin.rootPackage 覆盖。 */
    private static final String DEFAULT_ROOT_PACKAGE = "org/bytechen/rottenruins";

    private final List<MeXinSpec> specs = new ArrayList<>();
    /** 类名 → 父类名。 */
    private final Map<String, String> superIndex = new HashMap<>();
    /** 类名 → 直接实现的接口。 */
    private final Map<String, Set<String>> interfaceIndex = new HashMap<>();
    /** 目标类 → 传递子类集合。 */
    private final Map<String, Set<String>> subclassesOf = new HashMap<>();
    /** 包名 → 该包下的类（{@code includmy = true} 用）。 */
    private final Map<String, Set<String>> packageIndex = new HashMap<>();
    /** 已登记的钩子（宿主类#方法名#签名），用于跨来源去重。 */
    private final Set<String> seenHooks = new HashSet<>();

    @Override
    public @NotNull String name() {
        return "mexin";
    }

    @Override
    public void initialize(@NotNull IEnvironment environment) {
        // 无需环境信息
    }

    @Override
    public void onLoad(@NotNull IEnvironment environment, @NotNull Set<String> otherServices)
            throws IncompatibleEnvironmentException {
        // 摘除前先把本包（launch plugin 层）的类全部预加载：摘掉模块后，
        // 这个类加载器就再也解析不到本模块里尚未加载的类了。
        MeXinContainerTweak.preloadOwnClasses();
        // 把自己从启动插件容器里摘掉，之后 FML 才会把同一个 jar 当模组加载
        MeXinContainerTweak.apply(environment.getProperty(IEnvironment.Keys.GAMEDIR.get()).orElse(null));
        // 兼容开关：排查冲突时可 -Dmexin.disable=true 让本服务完全静默（不扫描、不注入）
        if (Boolean.getBoolean("mexin.disable")) {
            MeXinLog.info("已通过 -Dmexin.disable=true 关闭，本次不扫描也不注入");
            return;
        }
        try {
            for (Path source : collectSources(environment)) {
                scanSource(source);
            }
            computeSubclasses();
            MeXinLog.info("扫描完成：钩子 " + specs.size() + " 个，类索引 " + superIndex.size() + " 条");
            for (MeXinSpec spec : specs) {
                StringBuilder extra = new StringBuilder();
                if (spec.includeExtend()) {
                    extra.append("（含 ").append(subclassesOf.getOrDefault(spec.targetClass(), Set.of()).size())
                            .append(" 个子类）");
                }
                if (spec.hasPackageTarget()) {
                    extra.append("（总包 ").append(spec.packageTarget()).append(" 下 ")
                            .append(packageIndex.getOrDefault(spec.packageTarget(), Set.of()).size())
                            .append(" 个类）");
                }
                MeXinLog.info("  " + spec + extra);
            }
            warnMissingSrg();
        } catch (Throwable t) {
            // 扫描失败不能让游戏起不来：退化成"不注入任何方法"
            MeXinLog.error("扫描/初始化失败，本次不注入任何方法", t);
        }
    }

    @Override
    public List<ITransformationService.Resource> beginScanning(@NotNull IEnvironment environment) {
        // 模组扫描前再摘一次：onLoad 阶段启动插件层的 Configuration 可能还没进 completedLayers
        MeXinContainerTweak.apply(environment.getProperty(IEnvironment.Keys.GAMEDIR.get()).orElse(null));
        return List.of();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public @NotNull List<ITransformer> transformers() {
        if (specs.isEmpty()) {
            return List.of();
        }
        return List.of(new MeXinTransformer(specs, superIndex, subclassesOf, packageIndex));
    }

    /**
     * 兼容性提醒：原版方法在<b>生产环境</b>是 SRG 名（{@code m_21051_}），
     * 只写 {@code method="hurt"} 在开发环境能中、正式环境不中。
     * 这里提前把这种"只写了可读名"的钩子点出来，省得进游戏才发现没生效。
     */
    private void warnMissingSrg() {
        for (MeXinSpec spec : specs) {
            if (!spec.srgName().isEmpty() || !spec.targetClass().startsWith("net/minecraft/")) {
                continue;
            }
            if (SRG_PATTERN.matcher(spec.devName()).matches()) {
                continue;   // 本身就是 SRG 名（写在 method 里也算）
            }
            MeXinLog.warn("钩子没写 srg，正式环境可能匹配不到：@" + spec.hostClass() + "#"
                    + spec.hookName() + " (method=\"" + spec.devName() + "\")，建议补 srg=\"m_xxxxx_\"");
        }
    }

    // 扫描来源

    /** 自身输出 + mods 目录 + 原版 jar + java.class.path + 开发环境的 build 输出。 */
    private static List<Path> collectSources(@NotNull IEnvironment environment) {
        Set<Path> sources = new LinkedHashSet<>();
        try {
            URL own = MeXinLuncher.class.getProtectionDomain().getCodeSource().getLocation();
            sources.add(Path.of(own.toURI()));
        } catch (Throwable t) {
            MeXinLog.warn("定位自身输出失败：" + t);
        }
        environment.getProperty(IEnvironment.Keys.GAMEDIR.get()).ifPresent(gameDir -> {
            addJars(gameDir.resolve("mods"), sources);
            addVersionJars(gameDir, sources);
        });
        // 兼容性：额外扫描根（jar 或目录），多个用 ; 或 , 分隔。
        // 生产环境原版 jar 不在 classpath 上，需要更深层的原版父类时用它补，
        // 例如 -Dmexin.scan.extra=D:\mc\versions\1.20.1\1.20.1.jar
        String extra = System.getProperty("mexin.scan.extra", "");
        for (String entry : extra.split("[;,]")) {
            if (!entry.isBlank()) {
                sources.add(Path.of(entry.trim()));
            }
        }
        for (String entry : System.getProperty("java.class.path", "")
                .split(java.io.File.pathSeparator)) {
            if (!entry.isBlank()) {
                sources.add(Path.of(entry));
            }
        }
        // 开发环境：游戏进程的 classpath 里没有模组类，按相对 run/ 的路径补上
        // （生产环境模组 jar 就在 mods 目录里，上面已经覆盖）。
        Path cwd = Path.of("").toAbsolutePath();
        for (String candidate : new String[]{
                "../build/classes/java/main", "../build/resources/main",
                "build/classes/java/main", "build/resources/main"}) {
            Path path = cwd.resolve(candidate).normalize();
            if (Files.exists(path)) {
                sources.add(path);
            }
        }
        // 去重：同一个路径可能以不同写法（相对/绝对、软链接）进来，
        // 重复扫描会让同一个钩子被登记两次。
        Set<String> seen = new HashSet<>();
        List<Path> result = new ArrayList<>();
        for (Path source : sources) {
            if (!Files.isDirectory(source) && !source.toString().endsWith(".jar")) {
                continue;
            }
            String key;
            try {
                key = source.toRealPath().toString().toLowerCase(Locale.ROOT);
            } catch (Throwable t) {
                key = source.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            }
            if (seen.add(key)) {
                result.add(source);
            }
        }
        MeXinLog.info("扫描来源 " + result.size() + " 个：");
        for (Path source : result) {
            MeXinLog.info("  " + source);
        }
        return result;
    }

    private static void addJars(Path dir, Set<Path> out) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".jar")).forEach(out::add);
        } catch (Throwable t) {
            MeXinLog.warn("扫描目录失败 " + dir + "：" + t);
        }
    }

    /**
     * 原版 jar（{@code <gamedir>/versions/<id>/<id>.jar}）。
     * <p>生产环境下原版类<b>不在</b> {@code java.class.path} 上，不加这一层，
     * {@code includextend=true} 就只能看到模组自己写的子类，看不到僵尸、骷髅这类原版子类。
     * 只拿它建父类索引，扫错或扫不到都不影响主流程。</p>
     */
    private static void addVersionJars(Path gameDir, Set<Path> out) {
        Path versions = gameDir.resolve("versions");
        if (!Files.isDirectory(versions)) {
            return;
        }
        List<Path> found = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(versions)) {
            dirs.filter(Files::isDirectory).forEach(versionDir -> {
                try (Stream<Path> files = Files.list(versionDir)) {
                    files.filter(p -> p.getFileName().toString().endsWith(".jar"))
                            .filter(p -> !p.getFileName().toString().contains("-natives"))
                            .forEach(found::add);
                } catch (Throwable t) {
                    MeXinLog.warn("扫描版本目录失败 " + versionDir + "：" + t);
                }
            });
        } catch (Throwable t) {
            MeXinLog.warn("扫描版本库失败 " + versions + "：" + t);
        }
        // 装了多个版本时别把每个都扫一遍：只取最新的几个（原版父子关系基本一致）
        int limit = Math.min(found.size(), 3);
        for (int i = 0; i < limit; i++) {
            out.add(found.get(i));
        }
    }

    // 扫描单个来源里的 class

    private void scanSource(@NotNull Path source) {
        try {
            if (Files.isDirectory(source)) {
                try (Stream<Path> walk = Files.walk(source)) {
                    walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                        try (InputStream in = Files.newInputStream(p)) {
                            scanClass(in);
                        } catch (Throwable t) {
                            MeXinLog.warn("读取 " + p + " 失败：" + t);
                        }
                    });
                }
            } else if (source.toString().endsWith(".jar")) {
                try (JarFile jar = new JarFile(source.toFile())) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                            continue;
                        }
                        try (InputStream in = jar.getInputStream(entry)) {
                            scanClass(in);
                        } catch (Throwable t) {
                            MeXinLog.warn("读取 " + source.getFileName() + "!" + entry.getName()
                                    + " 失败：" + t);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            MeXinLog.warn("扫描 " + source + " 失败：" + t);
        }
    }

    /** 读一个 class：记录父类/接口，并抽取方法上的 {@code @MeXin}。 */
    private void scanClass(@NotNull InputStream in) throws Exception {
        new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
            private String className;

            @Override
            public void visit(int version, int access, @NotNull String name, String signature,
                              String superName, String[] interfaces) {
                this.className = name;
                if (superName != null) {
                    superIndex.putIfAbsent(name, superName);
                }
                if (interfaces != null && interfaces.length > 0) {
                    interfaceIndex.put(name, Set.of(interfaces));
                }
            }

            @Override
            public MethodVisitor visitMethod(int access, @NotNull String name,
                                             @NotNull String descriptor, String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private Map<String, Object> values;

                    @Override
                    public AnnotationVisitor visitAnnotation(@NotNull String desc, boolean visible) {
                        if (!ANNOTATION_DESC.equals(desc)) {
                            return null;
                        }
                        values = new HashMap<>();
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String key, Object value) {
                                values.put(key, value);
                            }

                            @Override
                            public void visitEnum(String key, String enumDesc, String enumValue) {
                                values.put(key, enumValue);
                            }
                        };
                    }

                    @Override
                    public void visitEnd() {
                        if (values == null) {
                            return;
                        }
                        // 同一个类可能既在 build/classes 又在打包好的 jar 里被扫到两次，
                        // 钩子按「宿主类#名#签名」去重，避免重复注入。
                        if (!seenHooks.add(className + "#" + name + descriptor)) {
                            return;
                        }
                        MeXinSpec spec = buildSpec(className, access, name, descriptor, values);
                        if (spec != null) {
                            specs.add(spec);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    /** 把注解值组装成 {@link MeXinSpec}；不满足要求时打日志并跳过。 */
    private MeXinSpec buildSpec(String hostClass, int hookAccess, String hookName, String hookDesc,
                                @NotNull Map<String, Object> values) {
        if ((hookAccess & Opcodes.ACC_STATIC) == 0 || (hookAccess & Opcodes.ACC_PUBLIC) == 0) {
            MeXinLog.warn("钩子必须是 public static：" + hostClass + "#" + hookName);
            return null;
        }
        String clazzName = values.get("clazzname") instanceof String s ? s : "";
        Object clazz = values.get("clazz");
        if (clazzName.isBlank() && clazz instanceof Type type
                && !"java/lang/Object".equals(type.getInternalName())) {
            clazzName = type.getInternalName();
        }
        boolean includeMy = Boolean.TRUE.equals(values.get("includmy"));
        // includmy：目标扩到"模组总包"（根包 + 全部子包）
        String packageTarget = includeMy ? rootPackage() : "";
        if (clazzName.isBlank()) {
            if (!includeMy) {
                MeXinLog.warn("没指定目标类（clazz/clazzname/includmy）：" + hostClass + "#" + hookName);
                return null;
            }
            clazzName = hostClass;
        }
        clazzName = clazzName.replace('.', '/');
        String method = values.get("method") instanceof String s ? s : "";
        if (method.isBlank()) {
            MeXinLog.warn("没指定目标方法：" + hostClass + "#" + hookName);
            return null;
        }
        String srg = values.get("srg") instanceof String s ? s : "";
        String desc = values.get("desc") instanceof String s ? s : "";
        MeXinType type = MeXinType.MIX_BEFORE_ENTER;
        if (values.get("type") instanceof String enumName) {
            try {
                type = MeXinType.valueOf(enumName);
            } catch (IllegalArgumentException e) {
                MeXinLog.warn("未知的 MeXinType：" + enumName);
            }
        }
        boolean includeExtend = Boolean.TRUE.equals(values.get("includextend"));
        return new MeXinSpec(clazzName, method, srg, desc, type, includeExtend, packageTarget,
                hostClass, hookName, hookDesc);
    }

    // 子类闭包 + 包索引

    private void computeSubclasses() {
        for (MeXinSpec spec : specs) {
            if (spec.includeExtend()) {
                subclassesOf.put(spec.targetClass(), findSubclasses(spec.targetClass()));
            }
            if (spec.hasPackageTarget()) {
                packageIndex.put(spec.packageTarget(), findPackageClasses(spec.packageTarget()));
            }
        }
    }

    /** 扫描索引里落在该包（含所有子包）的所有类。 */
    private Set<String> findPackageClasses(String packageName) {
        String prefix = packageName + "/";
        Set<String> result = new HashSet<>();
        for (String className : superIndex.keySet()) {
            if (className.startsWith(prefix)) {
                result.add(className);
            }
        }
        for (String className : interfaceIndex.keySet()) {
            if (className.startsWith(prefix)) {
                result.add(className);
            }
        }
        return result;
    }

    /** 模组总包（内部名写法）；允许 {@code -Dmexin.rootPackage=a.b.c} 覆盖。 */
    private static String rootPackage() {
        String configured = System.getProperty("mexin.rootPackage", DEFAULT_ROOT_PACKAGE);
        return configured.replace('.', '/');
    }

    private Set<String> findSubclasses(String target) {
        Set<String> result = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(target);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            for (Map.Entry<String, String> entry : superIndex.entrySet()) {
                if (current.equals(entry.getValue()) && result.add(entry.getKey())) {
                    queue.add(entry.getKey());
                }
            }
            for (Map.Entry<String, Set<String>> entry : interfaceIndex.entrySet()) {
                if (entry.getValue().contains(current) && result.add(entry.getKey())) {
                    queue.add(entry.getKey());
                }
            }
        }
        return result;
    }
}
