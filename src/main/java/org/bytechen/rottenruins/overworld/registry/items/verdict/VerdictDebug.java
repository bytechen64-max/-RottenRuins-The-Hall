package org.bytechen.rottenruins.overworld.registry.items.verdict;

import org.bytechen.rottenruins.HallMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 天穹裁决的诊断日志：把技能按键链路每一步的实际取值落盘到 run/verdict-debug.log，tick 相位差随机器变化、光看代码猜不出。 */
public final class VerdictDebug {

    /**
     * 总开关。默认 <b>false</b>：发布版不应该在工作目录里写诊断日志。
     * 运行期由 {@code ConfigHelper.applyDebugSwitches()} 按 {@code SplendidingConfig.debug} 赋值，
     * 即排查时改配置即可，不需要重新编译。
     */
    public static boolean ENABLED = false;

    private static final Path LOG = Path.of("verdict-debug.log");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private VerdictDebug() {}

    /** 记一行；刻意不抛异常也不重试，写盘失败只走主日志，诊断代码不该带崩玩法。 */
    public static void log(String format, Object... args) {
        if (!ENABLED) return;
        String msg = args.length == 0 ? format : String.format(format, args);
        HallMod.LOGGER.debug("[Verdict] {}", msg);
        try {
            String line = "[" + LocalTime.now().format(TIME) + "] " + msg + System.lineSeparator();
            Files.writeString(LOG, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
