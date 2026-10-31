package org.bytechen.rottenruins.client.rend.text.effect;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.HallMod;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文本特效的运行计数与"只报一次"日志（给 {@code /rrsfx stats} 与排障用）。
 *
 * <h3>为什么不是每帧打日志</h3>
 * <p>解析/绘制路径里的异常如果每次绘制都打一行，"失效时的日志"反而会刷屏，
 * 而且日志本身会拖帧。项目里着色器/uniform 的失败都遵循"每个失败点只报一次"的约定
 * （见 {@code TooltipShaders.warnUniformOnce}），这里保持一致。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TextEffectStats {

    public static final AtomicLong PARSE_CALLS = new AtomicLong();
    public static final AtomicLong CACHE_HITS = new AtomicLong();
    public static final AtomicLong WRAPPED_TEXTS = new AtomicLong();
    public static final AtomicLong FAILURES = new AtomicLong();

    private static final AtomicBoolean FIRST_FAILURE_LOGGED = new AtomicBoolean(false);

    private TextEffectStats() {}

    /** 解析层/overlay 层捕获到异常时调用；只记第一次的完整堆栈。 */
    public static void failure(Throwable throwable) {
        FAILURES.incrementAndGet();
        if (FIRST_FAILURE_LOGGED.compareAndSet(false, true)) {
            HallMod.LOGGER.warn("[TextFX] 文本特效渲染失败，已退回原版路径（后续同类错误不再打印）：{}",
                    throwable.toString(), throwable);
        }
    }

    public static void reset() {
        PARSE_CALLS.set(0);
        CACHE_HITS.set(0);
        WRAPPED_TEXTS.set(0);
        FAILURES.set(0);
    }

    /** 一行可直接发到聊天栏的统计文本。 */
    public static String describe() {
        return "§7[§dTextFX§7] 解析 §f" + PARSE_CALLS.get()
                + "§7 / 缓存命中 §f" + CACHE_HITS.get()
                + "§7 / overlay §f" + WRAPPED_TEXTS.get()
                + "§7 / 失败 §f" + FAILURES.get();
    }
}
