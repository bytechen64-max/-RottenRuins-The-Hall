package org.bytechen.hall.utils.entity;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import java.util.Collection;

public class EntityBuffUtils {

    /**
     * 叠加效果：如果已存在同类型效果，则等级相加、时长相加；否则新增。
     */
    public static void addOrStackEffect(LivingEntity entity, MobEffect effect, int level, int durationTicks) {
        if (entity == null || effect == null || level <= 0 || durationTicks <= 0) return;

        MobEffectInstance existing = entity.getEffect(effect);
        if (existing != null) {
            // 叠加等级和时长（注意：原版效果等级从0开始，即 level 0 表示 I 级）
            int newLevel = existing.getAmplifier() + level;
            int newDuration = existing.getDuration() + durationTicks;
            // 重新施加（会覆盖旧效果，但不会触发移除事件）
            entity.addEffect(new MobEffectInstance(effect, newDuration, newLevel));
        } else {
            entity.addEffect(new MobEffectInstance(effect, durationTicks, level - 1)); // 等级需减1
        }
    }

    /**
     * 仅叠加等级（时长不变）
     */
    public static void stackEffectLevel(LivingEntity entity, MobEffect effect, int additionalLevel) {
        if (entity == null || effect == null || additionalLevel <= 0) return;
        MobEffectInstance existing = entity.getEffect(effect);
        if (existing != null) {
            int newLevel = existing.getAmplifier() + additionalLevel;
            entity.addEffect(new MobEffectInstance(effect, existing.getDuration(), newLevel));
        } else {
            // 不存在则新建，时长为默认（如 200 tick）
            entity.addEffect(new MobEffectInstance(effect, 200, additionalLevel - 1));
        }
    }

    /**
     * 仅叠加时长（等级不变）
     */
    public static void stackEffectDuration(LivingEntity entity, MobEffect effect, int additionalDuration) {
        if (entity == null || effect == null || additionalDuration <= 0) return;
        MobEffectInstance existing = entity.getEffect(effect);
        if (existing != null) {
            int newDuration = existing.getDuration() + additionalDuration;
            entity.addEffect(new MobEffectInstance(effect, newDuration, existing.getAmplifier()));
        } else {
            entity.addEffect(new MobEffectInstance(effect, additionalDuration, 0)); // 默认等级 I
        }
    }

    // 批量叠加（可变参数）
    public static void addOrStackEffects(LivingEntity entity, MobEffectInstance... effects) {
        if (entity == null || effects == null) return;
        for (MobEffectInstance inst : effects) {
            addOrStackEffect(entity, inst.getEffect(), inst.getAmplifier() + 1, inst.getDuration());
        }
    }
}