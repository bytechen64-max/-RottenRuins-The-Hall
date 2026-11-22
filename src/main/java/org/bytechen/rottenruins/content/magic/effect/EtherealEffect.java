package org.bytechen.rottenruins.content.magic.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectInstance;

/**
 * 虚化：身体半透明 + 淡蓝白描边。
 *
 * <p>半透明由 {@code client} 的 {@code LivingEntityRenderer} mixin 做（渲染类型换成 translucent），
 * 描边复用原版发光标记，所以客户端没装额外着色器也能看见。</p>
 */
public class EtherealEffect extends CustomEffectBase {

    public static final int COLOR = 0x9AD8FF;

    public EtherealEffect(net.minecraft.resources.ResourceLocation id) {
        super(id, CustomEffectCategory.NEUTRAL, COLOR);
    }

    @Override
    public int getDefaultDuration() {
        return 10;
    }

    @Override
    public void onAdded(LivingEntity entity, CustomEffectInstance instance) {
        entity.setGlowingTag(true);
        if (entity.level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(),
                    12, entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.4D, entity.getBbWidth() * 0.4D, 0.02D);
        }
    }

    @Override
    public void onEntityTick(ServerLevel level, LivingEntity entity, CustomEffectInstance instance) {
        entity.setGlowingTag(true);
    }

    @Override
    public void onRemoved(LivingEntity entity, CustomEffectInstance instance) {
        if (entity.level() == null || entity.level().isClientSide) {
            return;
        }
        // 只有我们还活着、且没有别的发光来源时才清标记。
        if (!entity.hasEffect(MobEffects.GLOWING) && !entity.isCurrentlyGlowing()) {
            entity.setGlowingTag(false);
        }
    }
}
