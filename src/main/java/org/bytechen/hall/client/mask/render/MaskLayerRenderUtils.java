package org.bytechen.hall.client.mask.render;

import org.bytechen.hall.client.cosmic.render.CosmicRenderUtils;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * mask 层几何的复用工具。
 *
 * <h3>为什么要有缓存</h3>
 * <p>一个物品 quad 是「拿 sprite 跑一遍 {@code ItemModelGenerator} + {@code FaceBakery}」
 * 烘出来的（{@link CosmicRenderUtils#bakeItem}），这是纯函数 —— 只跟 sprite 有关，
 * 跟物品、视角、帧都无关。但 cosmic 那条路是<b>每帧每物品重新烘一次</b>的。
 * 单层时那点开销看不出来，可本系统的卖点就是能叠好几层，再乘上「每帧 × 每物品」
 * 就会变成实打实的浪费，所以这里按 sprite 缓存。</p>
 *
 * <h3>缓存失效</h3>
 * <p>键是 sprite <b>实例</b>（{@link IdentityHashMap}）。资源重载会 stitch 出新的
 * sprite 实例，旧条目自然不再被命中 —— 但会留在 map 里，所以由
 * {@code MaskLayerClient} 在 {@code TextureStitchEvent} 时调用 {@link #clear()}。
 * 不依赖 {@code equals}，是因为 {@code TextureAtlasSprite} 压根没重写它。</p>
 */
public final class MaskLayerRenderUtils {

    private static final Map<TextureAtlasSprite, List<BakedQuad>> QUAD_CACHE = new IdentityHashMap<>();

    /** 该 sprite 烘好的 quad 列表（缓存，只烘一次）。 */
    public static List<BakedQuad> quadsFor(TextureAtlasSprite sprite) {
        List<BakedQuad> cached = QUAD_CACHE.get(sprite);
        if (cached != null) return cached;
        List<BakedQuad> baked = CosmicRenderUtils.bakeItem(sprite);
        QUAD_CACHE.put(sprite, baked);
        return baked;
    }

    /** 丢弃所有缓存的 quad。图集重新 stitch 时必须调用。 */
    public static void clear() {
        QUAD_CACHE.clear();
    }

    /**
     * 取物品<b>本体贴图</b>（layer0）在图集里的 sprite —— 泛光层的光源。
     *
     * <p>优先从模型的 quad 上问：{@code getQuads(null, null, random)} 对物品模型
     * 返回的就是本体的那几个 quad，{@code BakedQuad.getSprite()} 拿到的正是
     * layer0。这条路比 {@code getParticleIcon()} 可靠（后者依赖模型有没有声明
     * {@code particle} 贴图，没声明时会返回 missing sprite，症状是整层变成
     * 紫黑格子）；所以只在取 quad 失败时才退回它。</p>
     *
     * @return 本体贴图 sprite，取不到时返回 {@code null}（调用方应退回用遮罩当几何）
     */
    @SuppressWarnings("deprecation")
    public static TextureAtlasSprite baseSpriteOf(@Nullable BakedModel model) {
        if (model == null) return null;
        try {
            List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create(BASE_PROBE_SEED));
            for (BakedQuad quad : quads) {
                TextureAtlasSprite sprite = quad.getSprite();
                if (sprite != null) return sprite;
            }
        } catch (Throwable ignored) {
            // 少数自定义模型会因为拿不到 state 而炸，退回下面那条路
        }
        try {
            return model.getParticleIcon();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 探测本体贴图用的固定种子，避免每帧换随机数导致 quad 顺序不稳定。 */
    private static final long BASE_PROBE_SEED = 42L;

    private MaskLayerRenderUtils() {}
}
