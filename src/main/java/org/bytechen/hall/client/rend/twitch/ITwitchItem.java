package org.bytechen.hall.client.rend.twitch;

import net.minecraft.world.item.ItemDisplayContext;
import org.bytechen.hall.client.cosmic.BakedModelCosmic;

/**
 * Marker interface for items that should display the glitch twitch
 * visual effect during rendering.
 *
 * <p>The twitch effect periodically applies rapid position jitter,
 * non-uniform scale stretch, and rotation to the item model during
 * short bursts (~0.3s every ~3s), creating a "corrupted / glitchy"
 * aesthetic.
 *
 * <h3>Alternative: JSON model loader</h3>
 * Items that use the {@code "hall:cosmic"} model loader get twitch
 * automatically via {@link BakedModelCosmic}.  This interface is for
 * items that want the twitch effect without the cosmic starfield shader.
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * public class MyGlitchItem extends SwordItem implements ITwitchItem {
 *     public MyGlitchItem() { super(Tiers.NETHERITE, 3, -2.4f, new Properties()); }
 * }
 * }</pre>
 *
 * @see ItemTwitchHelper#shouldTwitch
 */
public interface ITwitchItem {

    /**
     * Whether the twitch effect should render in this display context.
     * Default: enabled everywhere except GUI inventory.
     */
    default boolean twitchShouldRender(ItemDisplayContext ctx) {
        return ctx != ItemDisplayContext.GUI;
    }

    /**
     * 物品层面的明确关闭声明。
     *
     * <p>默认 {@code false}（不声明任何意见），所以这是一个纯增量开关，
     * 现有实现（如 {@code InfEnderPearItem}）行为完全不变。</p>
     *
     * <p><b>为什么需要它，而不是只靠模型的 {@code "twitch": false}</b>：
     * {@code MixinItemRendererCosmic} 在延迟渲染路径入队时会调用
     * {@code BakedModelCosmic.setCorruptionMask()}，而那个方法内部会把
     * {@code corruptionEnabled} 重新置为 true；同理崩坏层与抽动层的开关
     * 都可能被包装器覆盖。物品自身的声明是唯一不会被覆盖的那一层，
     * 所以崩坏层与抽动层都以它为准。</p>
     */
    default boolean twitchDisabled() {
        return false;
    }
}
