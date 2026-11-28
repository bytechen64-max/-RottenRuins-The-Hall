package org.bytechen.rottenruins.content.magic.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.gameplay.magic.spell.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.BlackHoleEntity;
import org.jetbrains.annotations.NotNull;

/** 黑洞术：使徒黑洞的 2 倍范围，持续 20 秒，核心每 tick 15 点伤害。 */
public class BlackHoleSpell extends CMagicBaseItem {

    private static final int LIFETIME = 400;
    private static final float RADIUS = 2.4f;
    private static final float GRAVITY_RADIUS = 96.0f;
    /** 伤害半径 = 视界半径。改血引擎只在 killRadius 内结算，传 0 就完全不打人。 */
    private static final float DAMAGE_RADIUS = RADIUS;
    private static final float DAMAGE_PER_TICK = 15.0f;
    private static final double SPAWN_DISTANCE = 8.0d;

    public BlackHoleSpell() {
        super(defaultProperties(), SpellDefinition.builder(UsingType.IMMEDIATELY, MagicType.VOID)
                .manaCost(700)
                .cooldownTicks(3600)
                .damage(DAMAGE_PER_TICK)
                .build());
    }

    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        ServerLevel level = caster.serverLevel();
        Vec3 pos = caster.getEyePosition().add(caster.getLookAngle().scale(SPAWN_DISTANCE));
        BlackHoleEntity.spawnSkill(level, pos, LIFETIME, RADIUS, GRAVITY_RADIUS, DAMAGE_RADIUS,
                        DAMAGE_PER_TICK, LIFETIME)
                .setAnomalyAttacker(caster.getUUID());

        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 60, 1.2d, 1.2d, 1.2d, 0.4d);
        level.playSound(null, caster.blockPosition(), SoundEvents.END_PORTAL_SPAWN,
                SoundSource.PLAYERS, 0.7f, 0.6f);
        return true;
    }
}
