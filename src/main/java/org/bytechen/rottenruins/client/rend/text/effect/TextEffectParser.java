package org.bytechen.rottenruins.client.rend.text.effect;

import org.bytechen.rottenruins.api.text.TextEffectIds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 把带 {@code §[rrs:...]} 标记的原文编译成 {@link EffectPlan}（纯 Java，无 Minecraft 依赖）。
 *
 * <h3>为什么解析要独立于渲染</h3>
 * <p>同一段原文会被不同路径消费：测量（{@code StringSplitter}）、String 绘制、Component 的
 * 逐帧 overlay。如果各自边解析边渲染，迟早出现"测量时算出来的宽度和绘制时不一样"。
 * 这里解析一次、产出一份不可变计划，三条路径只做"查表"。</p>
 *
 * <h3>计数口径必须与 {@code StringDecomposer} 完全一致</h3>
 * <p>解析器从左到右数<b>可视字符</b>：遇到 {@code §}+任意字符 → 原版会吞掉两个字符且不渲染，
 * 不计；代理对计 1；其余字符计 1。这个口径是 T1（把 gap 交回原版）与 L1（overlay 原版 FCS）
 * 能对齐"第 i 个字"的唯一依据。</p>
 *
 * <h3>边界处理（对应提案 §4.7）</h3>
 * <ul>
 *   <li>未闭合/超长/未知命名空间的标记：不消费，按原版"未知 § 码"处理（只吞 {@code §[}），
 *       正文照常可见 —— 绝不让文字消失。</li>
 *   <li>未知效果名：标记零宽吞掉、计入 {@link EffectPlan#invalidMarkers}，正文不受影响。</li>
 *   <li>{@code §r} 会清空全部自定义效果（与原版"完全重置"语义对齐）。</li>
 * </ul>
 */
public final class TextEffectParser {

    private TextEffectParser() {}

    /**
     * 原文里是否存在<b>可被我们消费</b>的标记（快路径判断，不做分配）。
     *
     * <p>既认打开标记 {@code §[rrs:...]}，也认关闭标记 {@code §[/]} / {@code §[/grad]}：
     * 折行后可能出现"只有关闭标记、没有打开标记"的尾部片段（{@code Font.split} 按 raw 下标
     * 切块），如果只认打开标记，这种片段会漏到原版手里，最终把 {@code "/]"} 当正文画出来。</p>
     *
     * <p>必须校验"确实有闭合的 ']'"：否则 {@code "§[rrs:grad"（漏写右括号）} 这种文本会让
     * T1 把 gap 又交回原版、T1 再次被触发 —— 无限递归。找不到闭合标记时返回 false，
     * 原版按"未知 § 码"处理（只吞 {@code §[}），与提案 §4.7 的降级行为一致。</p>
     */
    public static boolean hasMarkers(String text, int from) {
        if (text == null) return false;
        int cursor = Math.max(0, from);
        while (true) {
            int index = text.indexOf("\u00a7[", cursor);
            if (index < 0) return false;
            int close = findMarkerEnd(text, index + 2);
            if (close > 0) {
                String body = text.substring(index + 2, close);
                String namespace = TextEffectIds.NAMESPACE + ":";
                if (body.startsWith(namespace) || body.equals("/") || body.startsWith("/")) {
                    return true;
                }
            }
            cursor = index + 2;
        }
    }

    /** 把 {@code text} 从 {@code start} 开始的部分编译成计划。 */
    public static EffectPlan parse(String text, int start) {
        int length = text.length();
        int from = Math.max(0, Math.min(start, length));

        List<EffectPlan.Part> parts = new ArrayList<>();
        List<EffectSpan> spans = new ArrayList<>();
        Deque<EffectSpan> open = new ArrayDeque<>();

        int visible = 0;
        boolean hasRtl = false;
        int invalid = 0;

        int gapStart = from;
        int gapOrdinal = 0;
        int i = from;

        while (i < length) {
            char c = text.charAt(i);

            if (c == '\u00a7') {
                if (i + 1 >= length) break; // 孤立 §：原版直接 break，不渲染任何东西

                char c1 = text.charAt(i + 1);

                if (c1 == '[') {
                    int close = findMarkerEnd(text, i + 2);
                    if (close > 0) {
                        String body = text.substring(i + 2, close);
                        int handled = handleMarker(body, visible, spans, open);
                        if (handled >= 0) {
                            // 是我们的标记：把前面的文本 gap 收尾，然后跳过整个标记
                            invalid += handled;
                            if (i > gapStart) {
                                parts.add(EffectPlan.Part.text(gapStart, i, gapOrdinal));
                            }
                            i = close + 1;
                            gapStart = i;
                            gapOrdinal = visible;
                            continue;
                        }
                        // 未知命名空间（例如别的 mod 的 §[xxx]）：让原版吞 §[，正文继续
                    }
                    // 未闭合 / 非本命名空间：等同原版"未知 § 码"，吞两个字符
                    i += 2;
                    continue;
                }

                if (c1 == 'r' || c1 == 'R') {
                    closeAll(spans, open, visible);
                }
                i += 2;
                continue;
            }

            int codePoint = text.codePointAt(i);
            if (isRtl(codePoint)) hasRtl = true;
            visible++;
            i += Character.charCount(codePoint);
        }

        if (length > gapStart) {
            parts.add(EffectPlan.Part.text(gapStart, length, gapOrdinal));
        }
        closeAll(spans, open, visible);

        return new EffectPlan(parts, spans, visible, hasRtl, invalid);
    }

    // ── 标记处理 ─────────────────────────────────────────────────

    /**
     * @return {@code >= 0} 表示这是本命名空间的标记（已消费）；{@code -1} 表示不是我们的标记
     *         （调用方按原版未知码处理）。
     */
    private static int handleMarker(String body, int visible,
                                    List<EffectSpan> spans, Deque<EffectSpan> open) {
        String trimmed = body.trim();

        // 关闭：§[/] 或 §[/grad]
        if (trimmed.equals("/")) {
            popOne(spans, open, visible);
            return 0;
        }
        if (trimmed.startsWith("/")) {
            closeByName(spans, open, visible, trimmed.substring(1).trim().toLowerCase(java.util.Locale.ROOT));
            return 0;
        }

        // 打开：§[rrs:name:params...]
        String prefix = TextEffectIds.NAMESPACE + ":";
        if (!trimmed.startsWith(prefix)) {
            return -1; // 别的命名空间，不归我们管
        }

        String rest = trimmed.substring(prefix.length());
        if (rest.isEmpty()) return 1; // 畸形，零宽吞掉

        String[] tokens = rest.split(":", -1);
        String name = tokens[0].trim().toLowerCase(java.util.Locale.ROOT);

        if (name.equals(TextEffectIds.RESET)) {
            closeAll(spans, open, visible);
            return 0;
        }

        List<String> params = new ArrayList<>(tokens.length - 1);
        for (int i = 1; i < tokens.length; i++) {
            params.add(tokens[i]);
        }

        EffectSpec spec = EffectSpec.parse(name, params);
        if (spec == null) {
            return 1; // 未知效果：零宽无效，正文继续
        }
        EffectSpan span = new EffectSpan(spec, visible);
        spans.add(span);
        open.push(span);
        return 0;
    }

    private static void popOne(List<EffectSpan> spans, Deque<EffectSpan> open, int visible) {
        EffectSpan span = open.poll();
        if (span != null) span.visibleEnd = visible;
    }

    private static void closeByName(List<EffectSpan> spans, Deque<EffectSpan> open,
                                    int visible, String name) {
        if (open.isEmpty()) return;
        // 从最内层往外找；找到就把它（以及它之上的内层）一起关掉
        for (java.util.Iterator<EffectSpan> it = open.iterator(); it.hasNext(); ) {
            EffectSpan span = it.next();
            if (span.spec.kind.name().toLowerCase(java.util.Locale.ROOT).equals(name)) {
                while (true) {
                    EffectSpan head = open.poll();
                    if (head == null) break;
                    head.visibleEnd = visible;
                    if (head == span) break;
                }
                return;
            }
        }
    }

    private static void closeAll(List<EffectSpan> spans, Deque<EffectSpan> open, int visible) {
        while (!open.isEmpty()) {
            EffectSpan span = open.poll();
            if (span != null) span.visibleEnd = visible;
        }
    }

    // ── 底层扫描工具 ──────────────────────────────────────────────

    /** 从 {@code from} 开始找 ']'，限制在 {@link TextEffectIds#MAX_MARKER_LENGTH} 内。 */
    private static int findMarkerEnd(String text, int from) {
        int limit = Math.min(text.length(), from + TextEffectIds.MAX_MARKER_LENGTH);
        for (int i = from; i < limit; i++) {
            char c = text.charAt(i);
            if (c == ']') return i;
            if (c == '\n' || c == '\u00a7') return -1; // 跨行进 § 视作畸形，避免误吞一整行
        }
        return -1;
    }

    private static boolean isRtl(int codePoint) {
        byte directionality = Character.getDirectionality(codePoint);
        return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
    }
}
