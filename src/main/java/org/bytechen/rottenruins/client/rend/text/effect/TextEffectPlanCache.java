package org.bytechen.rottenruins.client.rend.text.effect;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link EffectPlan} 的小型 LRU 缓存（key = 原文 + 起始下标）。
 *
 * <h3>为什么值得缓存</h3>
 * <p>同一段带标记的文本会在很多地方被反复消费：tooltip 每帧重建、{@code Font.width} 测量、
 * 绘制时再解析一遍、聊天行复用缓存 FCS……解析本身是 O(n) 且会分配 span 列表，
 * 不缓存的话每次绘制都要重来。热点文本（物品名/lore/界面标题）几乎 100% 命中。</p>
 *
 * <h3>为什么 key 里带 start</h3>
 * <p>{@code StringDecomposer} 的 core 重载带 {@code start} 偏移（折行/裁剪会从中间开始），
 * 同一字符串的不同起点会得到不同的可视序号，不能混用。</p>
 *
 * <p>只在客户端渲染线程 + 可能的集成服务端线程上调用；用同步块保护，开销可忽略。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TextEffectPlanCache {

    private static final int MAX_ENTRIES = 256;

    private record Key(String text, int start) {}

    private static final Map<Key, EffectPlan> CACHE =
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, EffectPlan> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    private TextEffectPlanCache() {}

    public static EffectPlan get(String text, int start) {
        Key key = new Key(text, start);
        synchronized (CACHE) {
            EffectPlan cached = CACHE.get(key);
            if (cached != null) {
                TextEffectStats.CACHE_HITS.incrementAndGet();
                return cached;
            }
        }
        TextEffectStats.PARSE_CALLS.incrementAndGet();
        EffectPlan plan = TextEffectParser.parse(text, start);
        synchronized (CACHE) {
            CACHE.put(key, plan);
        }
        return plan;
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
}
