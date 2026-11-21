package org.bytechen.rottenruins.content.magic.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.utils.EntityChooseUtil;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;

/** 冻雨：维持期间每 10 tick 对半径 7 格内目标造成 5 点冰伤并施加缓慢 II。 */
public class FreezingRain extends CMagicBaseItem {

    private static final double RADIUS = 7.0d;
    private static final float PULSE_DAMAGE = 5.0f;
    private static final int SLOW_TICKS = 40;

    public FreezingRain() {
        super(defaultProperties(), SpellDefinition.builder(UsingType.KEEP, MagicType.WATER)
                .manaCost(100)
                .cooldownTicks(600)
                .keepCostPerSecond(30)
                .keepTriggerInterval(10)
                .damage(PULSE_DAMAGE)
                .build());
    }

    @Override
    public boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                              int heldTicks, int triggerInterval) {
        ServerLevel level = caster.serverLevel();
        Vec3 center = caster.getBoundingBox().getCenter();
        for (LivingEntity victim : EntityChooseUtil.sphereAt(level, caster, center, RADIUS)) {
            if (!victim.isAlive()) {
                continue;
            }
            float damage = MagicStats.spellDamage(caster, stack, victim, null, PULSE_DAMAGE);
            SpellDamageUtil.hurt(victim, SpellDamageUtil.iceSourceOf(victim, caster), stack, caster, damage);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 1));
        }
        for (int i = 0; i < 20; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0d;
            double distance = RADIUS * Math.sqrt(level.random.nextDouble());
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    center.x + Math.cos(angle) * distance,
                    center.y + level.random.nextDouble() * 4.0d,
                    center.z + Math.sin(angle) * distance,
                    1, 0.0d, -0.4d, 0.0d, 0.02d);
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE,
                SoundSource.PLAYERS, 0.35f, 1.6f);
        return true;
    }
}
