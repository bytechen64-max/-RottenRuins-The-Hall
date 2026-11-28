package org.bytechen.rottenruins.overworld.registry.effect.customEffect.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.event.impl.ArcaneMarkHandler;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectInstance;
import org.bytechen.rottenruins.utils.ModUtils;
import org.jetbrains.annotations.NotNull;

/**
 * <b>奥数印记</b> —— 奥数立方的「三棱锥」形态命中时挂上的效果。
 *
 * <p>它自己<b>不做任何周期伤害</b>：全部逻辑是一条"被动触发"——
 * <b>带印记的目标每次受伤，都会把原版效果注册表里所有负面效果逐个塞上去</b>。
 * 分工是：</p>
 * <ul>
 *   <li>{@code LivingHurtEvent}（{@code ArcaneMarkHandler#onLivingHurt}）决定
 *       <b>什么时候开始</b>一轮；</li>
 *   <li>本类的 {@link #onEntityTick} 负责<b>每 tick 出队一个</b>效果
 *       （{@code ArcaneMarkHandler#drainOne}）—— 节流到 1 个/tick，
 *       避免同一瞬间塞几十个效果造成的卡顿与刷屏；</li>
 *   <li>{@link #onRemoved} 负责丢掉没给完的残留队列。</li>
 * </ul>
 *
 * <p>这类"监听别的事件"的效果不能只靠 {@link CustomEffectBase} 的周期回调，
 * 因为 {@code applyEffectTick} 只在"自己的节奏"上跑；要响应"受伤"必须挂事件。
 * 但"每 tick 一个"正好可以用 {@link #onEntityTick}（它只对带印记的实体每 tick 回调，
 * 没有印记的实体零开销）。</p>
 */
public class ArcaneMarkEffect extends CustomEffectBase {

    public ArcaneMarkEffect(@NotNull ResourceLocation id) {
        super(id, CustomEffectCategory.HARMFUL, 0xB14BFF);
    }

    /** 默认 10 秒（奥数立方的三棱锥形态要求的时长）。 */
    @Override
    public int getDefaultDuration() {
        return 200;
    }

    /** 每 tick 出队一个负面效果（硬性限制：每 tick 最多 1 个）。 */
    @Override
    public void onEntityTick(@NotNull ServerLevel level, @NotNull LivingEntity entity,
                             @NotNull CustomEffectInstance instance) {
        ArcaneMarkHandler.drainOne(entity);
    }

    /** 印记结束 → 还没给完的效果一并丢掉（不会在印记消失后继续叠）。 */
    @Override
    public void onRemoved(@NotNull LivingEntity entity, @NotNull CustomEffectInstance instance) {
        ArcaneMarkHandler.clear(entity);
        ModUtils.LOGGER.info("[ArcaneMark] 印记到期（{}），后续不再施加负面效果",
                entity.getType().toShortString());
    }
}
