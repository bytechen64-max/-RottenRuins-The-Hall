package org.bytechen.hall.overworld.registry.items.verdict;

import org.bytechen.hall.HallMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 天穹裁决的诊断日志。
 *
 * <h3>为什么需要它</h3>
 * <p>这把剑的三个技能全部挂在<b>原版物品使用状态</b>上
 * （{@code use()} / {@code onUseTick()} / {@code releaseUsing()} / {@code getUseDuration()}），
 * 而这条链路的中间量（"按住几 tick 才被判定为短按"、
 * "服务端在收到释放包时 {@code useItemRemaining} 是多少"）
 * <b>看不见也猜不准</b> —— 它取决于客户端与集成服务端的 tick 相位差，
 * 而那个差值随机器性能变化，在开发机上和在别人机器上可以完全不同。</p>
 *
 * <p>"技能 2 放不出来"这类问题，光看代码是查不出来的：
 * 分流条件、配置开关、冷却锁、服务端是否真收到释放包，
 * 四种原因的症状一模一样（右键没反应）。
 * 所以这里把每一步的实际取值落盘，让问题从"猜"变成"读"。</p>
 *
 * <h3>输出位置</h3>
 * <p>写到 {@code run/verdict-debug.log}（不是 {@code logs/latest.log}）。理由：
 * 其一，latest.log 被原版和其余模组的信息淹没，翻起来很累；
 * 其二，这个文件<b>每行都带一次 releaseUsing 的判定结果</b>，单独一个文件才好对比。</p>
 *
 * <p>同时也往 {@link HallMod#LOGGER} 写一条 DEBUG —— 开发时习惯看主日志的话也在。</p>
 *
 * <h3>怎么关掉</h3>
 * <p>把 {@link #ENABLED} 置 false 并重新编译。默认开启是刻意的：
 * 这个功能在"已经能跑"之后就不再需要了，但在调参期它比任何猜测都值钱。</p>
 */
public final class VerdictDebug {

    /** 总开关。调参结束后置 false 即可，不影响任何玩法逻辑。 */
    public static final boolean ENABLED = true;

    private static final Path LOG = Path.of("verdict-debug.log");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private VerdictDebug() {}

    /**
     * 记一行。
     *
     * <p>刻意<b>不</b>抛异常、也<b>不</b>在失败时重试：诊断代码绝不该
     * 因为写文件失败而把玩法带崩。写不进去就只走主日志。</p>
     */
    public static void log(String format, Object... args) {
        if (!ENABLED) return;
        String msg = args.length == 0 ? format : String.format(format, args);
        HallMod.LOGGER.debug("[Verdict] {}", msg);
        try {
            String line = "[" + LocalTime.now().format(TIME) + "] " + msg + System.lineSeparator();
            Files.writeString(LOG, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // 诊断输出失败不影响玩法
        }
    }
}
