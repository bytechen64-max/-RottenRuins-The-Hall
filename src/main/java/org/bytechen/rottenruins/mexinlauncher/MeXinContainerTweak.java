package org.bytechen.rottenruins.mexinlauncher;

import sun.misc.Unsafe;

import java.lang.module.Configuration;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * 让"一个 jar 同时当启动插件和模组"成立：把自己从 ModLauncher 的层容器里摘掉。
 *
 * <p>两步，缺一不可：</p>
 * <ol>
 *   <li><b>摘 mods 扫描排除表</b>：{@code ModDirTransformerDiscoverer.found} →
 *       {@code allExcluded()} → {@code ModsFolderLocator} 的 filter。摘掉后 FML 会把本 jar 当模组扫到。</li>
 *   <li><b>摘层容器里的自身模块</b>：ModLauncher 已把本 jar 挂在启动插件层，游戏层解析时
 *       会看到同一个 jar 的两个模块（{@code rotten_ruins} + 文件名自动模块名）导出同一个包，
 *       直接 {@code java.lang.module.ResolutionException}。层一旦 build 完不可变，
 *       所以这里用 Unsafe 直接改各层 {@code Configuration} 的
 *       {@code nameToModule} / {@code modules} 两个容器，把自身 ResolvedModule 删掉，
 *       并清掉 {@code allConfigurations} 缓存让后续解析重新计算。</li>
 * </ol>
 * 这里改用 {@code sun.misc.Unsafe} 读写字段，不依赖模块是否 open。</p>
 */
final class MeXinContainerTweak {

    /** 只有本 jar 才有的条目，用来在 mods 目录里认出自己。 */
    private static final String SELF_MARKER = "org/bytechen/rottenruins/mexinlauncher/MeXinLuncher.class";

    private static Unsafe unsafe;
    private static boolean done;

    private MeXinContainerTweak() {
    }

    /** 幂等；两层都试着摘，失败只记日志不抛。 */
    static void apply(Path gameDir) {
        if (done) {
            return;
        }
        if (Boolean.getBoolean("mexin.keepLaunchPlugin")) {
            MeXinLog.info("已通过 -Dmexin.keepLaunchPlugin=true 跳过（本 jar 不会被当模组加载）");
            return;
        }
        Path self = selfJar(gameDir);
        boolean dropped = dropFromDiscoverer(self);
        int layers = dropModuleFromLayers();
        if (dropped || layers > 0) {
            done = true;
        }
        MeXinLog.info("启动插件摘除：扫描排除表=" + dropped + "，层容器=" + layers + " 个配置已处理");
    }

    // ── 摘除前预加载本包所有类 ──────────────────────────────────────────
    // 摘掉自身模块后，插件层的 ModuleClassLoader 就解析不到本模块里“还没加载”的类了
    // （扫描器、注入器、内部类都属于这类），所以先统统 Class.forName 一遍。
    static void preloadOwnClasses() {
        Path self = selfJar(gameDirFromCommandLine());
        if (self == null) {
            MeXinLog.warn("预加载跳过：定位不到自身 jar");
            return;
        }
        int loaded = 0;
        int failed = 0;
        try (JarFile jar = new JarFile(self.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("org/bytechen/rottenruins/mexinlauncher/") || !name.endsWith(".class")) {
                    continue;
                }
                String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                try {
                    Class.forName(className, false, MeXinContainerTweak.class.getClassLoader());
                    loaded++;
                } catch (Throwable ignored) {
                    failed++;
                }
            }
        } catch (Throwable t) {
            MeXinLog.error("预加载本包类失败", t);
            return;
        }
        MeXinLog.info("预加载 mexinlauncher 类：" + loaded + " 个" + (failed > 0 ? "（失败 " + failed + "）" : ""));
    }

    // ── 第一步：从 FML 的排除表里摘掉 ──────────────────────────────────

    private static boolean dropFromDiscoverer(Path self) {
        if (self == null) {
            MeXinLog.warn("定位不到自身 jar，无法从启动插件清单里摘除");
            return false;
        }
        try {
            Class<?> discoverer = Class.forName("net.minecraftforge.fml.loading.ModDirTransformerDiscoverer");
            Field foundField = discoverer.getDeclaredField("found");
            Object found = unsafe().getObject(unsafe().staticFieldBase(foundField),
                    unsafe().staticFieldOffset(foundField));
            if (!(found instanceof List<?> list)) {
                MeXinLog.warn("ModDirTransformerDiscoverer.found 不是 List，跳过第一步");
                return false;
            }
            int before = list.size();
            boolean removed = list.removeIf(entry -> isSelf(entry, self));
            MeXinLog.info("启动插件清单：" + before + " → " + list.size() + "，自身 jar = " + self
                    + (removed ? "（已摘除）" : "（没有自身条目）"));
            return removed;
        } catch (Throwable t) {
            MeXinLog.error("从启动插件清单里摘除失败", t);
            return false;
        }
    }

    // ── 第二步：从各层 Configuration 的容器里删掉自身模块 ────────────────

    private static int dropModuleFromLayers() {
        String selfModule = MeXinContainerTweak.class.getModule() == null
                ? null : MeXinContainerTweak.class.getModule().getName();
        if (selfModule == null || selfModule.isEmpty()) {
            MeXinLog.warn("自身模块没有名字（未命名模块？），跳过第二步");
            return 0;
        }
        int patched = 0;
        try {
            Unsafe u = unsafe();
            // 我们所在的那一层 + 它的所有父层（BFS，去重）：子层解析时读的就是这些 Configuration
            ModuleLayer selfLayer = MeXinContainerTweak.class.getModule().getLayer();
            if (selfLayer == null) {
                MeXinLog.warn("自身模块不在任何 ModuleLayer 里，跳过第二步");
                return 0;
            }
            java.util.Deque<ModuleLayer> queue = new java.util.ArrayDeque<>();
            Set<ModuleLayer> seen = new HashSet<>();
            queue.add(selfLayer);
            while (!queue.isEmpty()) {
                ModuleLayer layer = queue.remove();
                if (!seen.add(layer)) {
                    continue;
                }
                if (dropModule(u, layer.configuration(), selfModule)) {
                    patched++;
                }
                queue.addAll(layer.parents());
            }
            if (patched > 0) {
                MeXinLog.info("层容器：已从 " + patched + " 个 Configuration 里删掉自身模块 " + selfModule);
            }
        } catch (Throwable t) {
            MeXinLog.error("从层容器里删自身模块失败", t);
        }
        return patched;
    }

    /** 用 Unsafe 改 {@code Configuration.nameToModule} / {@code modules}，并清 {@code allConfigurations} 缓存。 */
    private static boolean dropModule(Unsafe u, Configuration config, String selfModule) {
        try {
            Field nameToModuleField = Configuration.class.getDeclaredField("nameToModule");
            Field modulesField = Configuration.class.getDeclaredField("modules");
            long nameOffset = u.objectFieldOffset(nameToModuleField);
            long modulesOffset = u.objectFieldOffset(modulesField);

            Object rawByName = u.getObject(config, nameOffset);
            Object rawModules = u.getObject(config, modulesOffset);
            if (!(rawByName instanceof Map<?, ?> byName) || !(rawModules instanceof Set<?> modules)) {
                return false;
            }
            if (!byName.containsKey(selfModule)) {
                return false;
            }
            Map<String, Object> newByName = new HashMap<>();
            for (Map.Entry<?, ?> e : byName.entrySet()) {
                newByName.put((String) e.getKey(), e.getValue());
            }
            Object resolved = newByName.remove(selfModule);
            Set<Object> newModules = new HashSet<>(modules);
            newModules.remove(resolved);

            u.putObject(config, nameOffset, newByName);
            u.putObject(config, modulesOffset, newModules);
            // allConfigurations 是 parents 的缓存；不清掉的话子层解析还会看到旧数据
            try {
                Field allField = Configuration.class.getDeclaredField("allConfigurations");
                u.putObject(config, u.objectFieldOffset(allField), null);
            } catch (Throwable ignored) {
                // 字段名变了就忽略
            }
            return true;
        } catch (Throwable t) {
            MeXinLog.error("改 Configuration 失败", t);
            return false;
        }
    }

    // ── 自身 jar 与工具 ────────────────────────────────────────────────

    private static Path selfJar(Path gameDir) {
        try {
            var url = MeXinContainerTweak.class.getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                String text = url.toString();
                int start = text.indexOf("union:");
                String path = text;
                if (start >= 0) {
                    path = text.substring(start + "union:".length());
                    int cut = path.length();
                    int percent = path.indexOf('%');
                    if (percent >= 0) {
                        cut = Math.min(cut, percent);
                    }
                    int bang = path.indexOf("!/");
                    if (bang >= 0) {
                        cut = Math.min(cut, bang);
                    }
                    path = path.substring(0, cut);
                    if (path.length() > 2 && path.charAt(0) == '/' && path.charAt(2) == ':') {
                        path = path.substring(1);
                    }
                }
                Path candidate = Path.of(path);
                if (Files.isRegularFile(candidate) && hasMarker(candidate)) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
            // 落到下面的 mods 扫描
        }
        Path dir = gameDir != null ? gameDir : gameDirFromCommandLine();
        if (dir == null) {
            return null;
        }
        Path mods = dir.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return null;
        }
        try (var list = Files.list(mods)) {
            for (Path jar : list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .toList()) {
                if (hasMarker(jar)) {
                    return jar;
                }
            }
        } catch (Throwable t) {
            MeXinLog.error("扫描 mods 目录失败：" + mods, t);
        }
        return null;
    }

    private static Path gameDirFromCommandLine() {
        try {
            String command = System.getProperty("sun.java.command", "");
            var matcher = java.util.regex.Pattern
                    .compile("--gameDir(?:=|\\s+)(\"([^\"]*)\"|([^\\s\"]+))").matcher(command);
            if (!matcher.find()) {
                return null;
            }
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            Path path = Path.of(value.replace("\"", ""));
            return Files.isDirectory(path) ? path : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasMarker(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getJarEntry(SELF_MARKER) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSelf(Object entry, Path self) {
        try {
            if (entry == null) {
                return false;
            }
            var paths = entry.getClass().getMethod("paths").invoke(entry);
            if (!(paths instanceof Path[] array) || array.length == 0) {
                return false;
            }
            Path candidate = array[0].toAbsolutePath().normalize();
            return candidate.equals(self.toAbsolutePath().normalize())
                    || (candidate.getFileName() != null && candidate.getFileName().equals(self.getFileName()));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Unsafe unsafe() throws Exception {
        if (unsafe == null) {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        }
        return unsafe;
    }
}
