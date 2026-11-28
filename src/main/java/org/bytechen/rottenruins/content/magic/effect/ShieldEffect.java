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
 * 护盾术：抵挡固定次数的伤害。
 *
 * <p>剩余次数存在静态表里（效果实例没有自定义数据位），效果一掉就一起清掉。</p>
 */
public class ShieldEffect extends CustomEffectBase {

    private static final Map<UUID, Integer> CHARGES = new ConcurrentHashMap<>();

    public ShieldEffect(ResourceLocation id) {
        super(id, CustomEffectCategory.BENEFICIAL, 0x7BE38B);
    }

    public static void grant(LivingEntity entity, int charges, int duration) {
        CHARGES.put(entity.getUUID(), Math.max(0, charges));
        CustomEffects.addEffect(entity, RegisterCustomEffect.SHIELD, duration, 0);
    }

    public static int charges(LivingEntity entity) {
        return CHARGES.getOrDefault(entity.getUUID(), 0);
    }

    /** 消耗一次护盾；返回 true 表示这次伤害要被完全挡下。 */
    public static boolean absorb(LivingEntity entity) {
        if (!CustomEffects.hasEffect(entity, RegisterCustomEffect.SHIELD)) {
            return false;
        }
        int left = charges(entity);
        if (left <= 0) {
            return false;
        }
        if (left == 1) {
            CHARGES.remove(entity.getUUID());
            CustomEffects.removeEffect(entity, RegisterCustomEffect.SHIELD);
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
