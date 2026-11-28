package org.bytechen.rottenruins.client.rend.text.effect;

import org.bytechen.rottenruins.api.text.TextEffectIds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 把带 {@code §[rrs:...]} 标记的原文编译成 {@link EffectPlan}（纯 Java，无 Minecraft 依赖）。
 * 解析与渲染分离：测量 / String 绘制 / Component 逐帧 overlay 共用同一份不可变计划，
 * 各自边解析边渲染迟早会出现"测量宽度 ≠ 绘制宽度"；计数口径必须与 {@code StringDecomposer} 一致
 * （{@code §}+任意字符不计，代理对计 1）。边界：未闭合 / 超长 / 未知命名空间的标记不消费、只吞 {@code §[}，
 * 绝不让文字消失；未知效果名零宽吞掉并计入 {@link EffectPlan#invalidMarkers}；{@code §r} 清空全部效果。
 */
public final class TextEffectParser {

    private TextEffectParser() {}

    /** 原文里是否存在可被我们消费的标记（快路径，不做分配）。
     *  既认打开 {@code §[rrs:...]} 也认关闭 {@code §[/]} / {@code §[/grad]}：折行后可能出现只有
     *  关闭标记的尾部片段；且必须校验"有闭合的 ']'"，否则漏写右括号会让 T1 无限递归。 */
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

    /** @return {@code >= 0} 表示是本命名空间的标记（已消费）；{@code -1} 表示不是我们的标记，
     *  调用方按原版未知码处理。 */
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
