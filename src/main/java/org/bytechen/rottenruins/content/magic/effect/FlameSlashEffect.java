package org.bytechen.rottenruins.content.magic.effect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectInstance;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;

/**
 * 焰斩：效果期间主手挥剑会额外扫出一道剑气，每次消耗一次可用次数。
 */
public class FlameSlashEffect extends CustomEffectBase {

    private static final Map<UUID, Integer> CHARGES = new ConcurrentHashMap<>();

    public FlameSlashEffect(ResourceLocation id) {
        super(id, CustomEffectCategory.BENEFICIAL, 0xFF5A1E);
    }

    public static void grant(LivingEntity entity, int charges, int duration) {
        CHARGES.put(entity.getUUID(), Math.max(0, charges));
        CustomEffects.addEffect(entity, RegisterCustomEffect.FLAME_SLASH, duration, 0);
    }

    public static int charges(LivingEntity entity) {
        return CHARGES.getOrDefault(entity.getUUID(), 0);
    }

    /** 消耗一次次数；返回 false 表示没次数了。 */
    public static boolean consume(LivingEntity entity) {
        int left = charges(entity);
        if (left <= 0) {
            return false;
        }
        if (left == 1) {
            CHARGES.remove(entity.getUUID());
            CustomEffects.removeEffect(entity, RegisterCustomEffect.FLAME_SLASH);
        } else {
            CHARGES.put(entity.getUUID(), left - 1);
        }
        return true;
    }

    @Override
    public void onRemoved(LivingEntity entity, CustomEffectInstance instance) {
        CHARGES.remove(entity.getUUID());
    }
}
