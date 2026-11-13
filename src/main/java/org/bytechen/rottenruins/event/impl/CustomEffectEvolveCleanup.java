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
 * 进化 / 转化时清理旧身体的效果（修：带奥数印记的生物死后，新生成的同类身上还挂着印记）。
 * 根因：infcore 的 {@code applyEvolution} "保留 NBT"档整份 {@code saveWithoutId} → {@code load}，
 * ForgeCaps 与 active_effects 一起进到新身体；不改 infcore 是因为 {@code copyFullNbt} 被别的模组依赖。
 * 规则（保守）：自定义效果一律不继承；仅当旧身体带奥数印记时才清新身体的原版 HARMFUL 效果。
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

    /** 清掉所有原版 HARMFUL 效果，返回移除个数。先收集再删：{@code getActiveEffects()}
     *  返回的是内部集合视图，边遍历边 {@code removeEffect} 会抛 ConcurrentModificationException。 */
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
