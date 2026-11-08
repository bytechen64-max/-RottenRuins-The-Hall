package org.bytechen.rottenruins.event.impl;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.infcore.api.event.EntityEvolveEvent;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;
import org.bytechen.rottenruins.utils.ModUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>进化 / 转化时清理旧身体的效果</b> —— 修的是
 * 「带奥数印记的生物死亡后，新生成的同类生物身上还有印记」。
 *
 * <h3>根因：一次 NBT 整份拷贝</h3>
 * <p>感染的进阶与转化由 infcore 的
 * {@code EvolutionManager#applyEvolution} 完成，其中"保留 NBT"那一档是这样做的：</p>
 * <pre>{@code
 * CompoundTag tag = new CompoundTag();
 * from.saveWithoutId(tag);   // ← ForgeCaps（含本模组的 RottenRuinsCustomEffects）与 active_effects 都在里面
 * to.load(tag);              // ← 整份灌进新身体
 * }</pre>
 * <p>于是<b>旧身体上的所有状态都会跟着进新身体</b>：奥数印记（还带着剩余时长）、
 * 以及印记刷上去的那一整排原版负面效果。表现出来就是
 * <b>"这只怪死了，但再刷出来的一模一样的怪身上还挂着印记"</b>。</p>
 *
 * <h3>为什么修在这里，而不是改 infcore</h3>
 * <ul>
 *   <li>{@code copyFullNbt} 是框架的通用行为，别的模组可能<b>依赖</b>"保留 NBT"来延续自己的状态
 *       （血量以外的数据、自定义名字之外的东西）；直接把它删掉会波及别人；</li>
 *   <li>而"<b>效果属于哪一具身体</b>"是本模组的业务约定 —— 由本模组自己声明最合适：
 *       自定义效果是我们自己的，原版负面效果里最典型的那一大堆也是被我们的印记刷上去的。</li>
 * </ul>
 *
 * <h3>清理规则（两条，都很保守）</h3>
 * <ol>
 *   <li><b>自定义效果一律不带进新身体</b>：{@link CustomEffects#removeAllEffects}。
 *       进化出来的是"一具新身体"，印记 / 腐蚀 / 迟滞 / 衰竭都属于旧身体；</li>
 *   <li><b>旧身体带奥数印记时，新身体的原版负面效果一起清掉</b>：印记的机制就是
 *       "把注册表里所有 HARMFUL 效果刷一遍"，所以这种情形下新身体身上的负面效果
 *       基本就是这个印记刷出来的。没有印记时不碰原版效果 —— 别的模组给生物挂的
 *       自有 debuff 一律尊重。</li>
 * </ol>
 *
 * <p>事件在 {@code applyEvolution} 里、新实体 {@code addFreshEntity} 之后抛出，
 * 所以这里拿到的是<b>已经在世上的</b>新实体，清除后的空快照也会正常同步给客户端。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CustomEffectEvolveCleanup {

    private CustomEffectEvolveCleanup() {
    }

    @SubscribeEvent
    public static void onEntityEvolve(EntityEvolveEvent event) {
        LivingEntity result = event.getResultEntity();
        if (result == null || result.level().isClientSide() || !result.isAlive()) {
            return;
        }

        // 必须在清除之前判定：印记是"旧身体把负面效果刷了一遍"的证据
        boolean inheritedMark = CustomEffects.hasEffect(result, RegisterCustomEffect.ARCANE_MARK);
        boolean hadCustomEffects = !CustomEffects.getActiveEffects(result).isEmpty();
        if (hadCustomEffects) {
            CustomEffects.removeAllEffects(result);
        }

        int harmfulRemoved = inheritedMark ? removeHarmfulEffects(result) : 0;

        if (hadCustomEffects || harmfulRemoved > 0) {
            ModUtils.LOGGER.info(
                    "[CustomEffect] 转化出的新身体不继承旧状态：{} → {}，清掉自定义效果={} 原版负面效果={} 个",
                    describe(event.getOriginal()), describe(result),
                    hadCustomEffects, harmfulRemoved);
        }
    }

    /**
     * 清掉目标身上所有原版 {@link MobEffectCategory#HARMFUL} 效果。
     *
     * <p>先收集再删除：{@code getActiveEffects()} 返回的是实体内部集合的视图，
     * 边遍历边 {@code removeEffect} 会抛 {@code ConcurrentModificationException}。</p>
     *
     * @return 实际移除的个数
     */
    private static int removeHarmfulEffects(LivingEntity entity) {
        List<MobEffect> harmful = new ArrayList<>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                harmful.add(instance.getEffect());
            }
        }
        for (MobEffect effect : harmful) {
            entity.removeEffect(effect);
        }
        return harmful.size();
    }

    private static String describe(LivingEntity entity) {
        if (entity == null) {
            return "<null>";
        }
        return entity.getType().toShortString();
    }
}
