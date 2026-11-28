package org.bytechen.rottenruins.utils.logging;

import org.bytechen.rottenruins.utils.logging.InfectionLog;

import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高频事件日志节流器。
 * <p>
 * 方块扩散、实体进化这类事件在感染爆发时每秒可能触发成百上千次，逐条打印 DEBUG 日志会把
 * 开发环境控制台和 {@code logs/debug.log} 刷爆（实测某次会话里约 74% 的 debug.log 都是扩散明细）。
 * 本类把这类日志分成两级：
 * <ul>
 *   <li><b>明细</b>：默认只写到 TRACE 级别；启动参数加上 {@code -Dinfcore.verbose=true} 时改为
 *       DEBUG 输出，方便在 IDE 控制台里直接观察。</li>
 *   <li><b>摘要</b>：按固定时间窗口聚合，每个窗口最多输出一条“本窗口内共发生 N 次”的 DEBUG 日志，
 *       既不会刷屏，又能一眼看出扩散 / 进化系统是否在工作。</li>
 * </ul>
 * 线程安全，可直接作为 {@code static final} 字段使用：
 *
 * <pre>{@code
 * private static final ThrottledLogger SPREAD_LOG =
 *         new ThrottledLogger(InfectionLog.LOGGER, "BlockSpreadManager");
 *
 * // 逐条明细（默认进 TRACE），窗口到期时自动汇总成一条 DEBUG
 * SPREAD_LOG.record("'{}' spread to '{}' at {} (type={})", source, target, pos, type);
 * }</pre>
 */
public final class ThrottledLogger {

    /**
     * 默认聚合窗口：每个窗口最多输出一条摘要日志。
     * <p>
     * 可用启动参数 {@code -Dinfcore.log.interval=<毫秒>} 覆盖；设为 {@code 0} 表示连摘要也不输出，
     * 高频日志就只剩 TRACE 明细。
     */
    public static final long DEFAULT_INTERVAL_MS = Long.getLong("infcore.log.interval", 30_000L);

    private static final long MIN_INTERVAL_MS = 1_000L;

    /** 明细日志被关闭时显示的提示。 */
    private static final String DETAIL_HINT =
            "per-event details go to TRACE level (-Dinfcore.verbose=true shows them here)";

    /** 已创建的全部节流器，供服务器停止时统一汇总。 */
    private static final List<ThrottledLogger> INSTANCES = new CopyOnWriteArrayList<>();

    private final Logger logger;
    private final String tag;
    private final String prefix;
    private final long intervalMs;

    private final AtomicLong eventCount = new AtomicLong();
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

    public ThrottledLogger(Logger logger, String tag) {
        this(logger, tag, DEFAULT_INTERVAL_MS);
    }

    public ThrottledLogger(Logger logger, String tag, long intervalMs) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.tag = Objects.requireNonNull(tag, "tag");
        this.prefix = this.tag + ": ";
        // intervalMs <= 0：不输出任何聚合摘要，只保留 TRACE / VERBOSE 明细
        this.intervalMs = intervalMs <= 0 ? 0L : Math.max(MIN_INTERVAL_MS, intervalMs);
        INSTANCES.add(this);
    }

    /**
     * 汇总所有节流器中当前窗口内尚未输出的事件数。
     * <p>
     * 服务器停止时调用，避免窗口没走完就关服导致最后一批事件完全没有日志。
     */
    public static void flushAll() {
        for (ThrottledLogger throttled : INSTANCES) {
            throttled.flush();
        }
    }

    /**
     * 记录一次事件。
     * <p>
     * 明细默认只在 TRACE 级别输出（{@code -Dinfcore.verbose=true} 时为 DEBUG）；
     * 聚合摘要按窗口间隔最多输出一条 DEBUG。
     *
     * @param detailFormat 明细格式，占位符与 SLF4J 的 <code>{}</code> 一致
     * @param detailArgs   明细参数
     */
    public void record(String detailFormat, Object... detailArgs) {
        eventCount.incrementAndGet();

        if (InfectionLog.VERBOSE) {
            if (logger.isDebugEnabled()) {
                logger.debug(prefix + render(detailFormat, detailArgs));
            }
        } else if (logger.isTraceEnabled()) {
            logger.trace(prefix + render(detailFormat, detailArgs));
        }

        long now = System.currentTimeMillis();
        long start = windowStart.get();
        if (intervalMs <= 0 || now - start < intervalMs) return;
        if (!windowStart.compareAndSet(start, now)) return;

        long events = eventCount.getAndSet(0);
        if (events <= 0) return;
        logger.debug("{}: {} event(s) since the last summary ({}s window) | {}",
                tag, events, intervalMs / 1000L, DETAIL_HINT);
    }

    /**
     * 立即汇总当前窗口内尚未输出的事件数并重新计时。
     * 供服务器停止等场景调用，避免最后一批事件被丢弃。
     */
    public void flush() {
        long events = eventCount.getAndSet(0);
        windowStart.set(System.currentTimeMillis());
        if (intervalMs <= 0 || events <= 0) return;
        logger.debug("{}: {} event(s) in the final window | {}", tag, events, DETAIL_HINT);
    }

    /**
     * 按 SLF4J 的 <code>{}</code> 占位符渲染消息。
     * <p>
     * 这里自行渲染而不把可变参数数组继续交给 {@link Logger}，是因为 Java 重载解析会把
     * {@code Object[]} 绑定到 {@code debug(String, Object)}（把整个数组当成一个参数），
     * 导致日志参数错位。
     */
    private static String render(String format, Object[] args) {
        if (format == null) return "null";
        if (args == null || args.length == 0) return format;

        StringBuilder sb = new StringBuilder(format.length() + args.length * 16);
        int argIndex = 0;
        int from = 0;
        while (argIndex < args.length) {
            int at = format.indexOf("{}", from);
            if (at < 0) break;
            sb.append(format, from, at).append(args[argIndex++]);
            from = at + 2;
        }
        return sb.append(format, from, format.length()).toString();
    }
}
