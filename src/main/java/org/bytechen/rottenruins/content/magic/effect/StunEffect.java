package org.bytechen.rottenruins.content.magic.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectInstance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 晕眩：生物 noai 不动，玩家锁住视角与位移，效果结束还原。分类用 NEUTRAL，避免被奥数印记当成随机负面效果抽中。 */
public final class StunEffect extends CustomEffectBase {

    private static final Map<UUID, StunLock> LOCKS = new ConcurrentHashMap<>();

    public StunEffect(ResourceLocation id) {
        super(id, CustomEffectCategory.NEUTRAL, 0xE8D44D);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void onAdded(LivingEntity entity, CustomEffectInstance instance) {
        if (entity.level().isClientSide) return;
        boolean wasNoAi = entity instanceof Mob mob && mob.isNoAi();
        LOCKS.put(entity.getUUID(), new StunLock(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot(), wasNoAi));
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof Mob mob) mob.setNoAi(true);
    }

    @Override
    public void onEntityTick(ServerLevel level, LivingEntity entity, CustomEffectInstance instance) {
        StunLock lock = LOCKS.get(entity.getUUID());
        if (lock == null) return;
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof ServerPlayer player) {
            if (player.isDeadOrDying() || player.isSpectator() || player.isPassenger()) return;
            player.connection.teleport(lock.x, lock.y, lock.z, lock.yaw, lock.pitch);
            player.setYRot(lock.yaw);
            player.setXRot(lock.pitch);
            player.setYHeadRot(lock.yaw);
            player.setYBodyRot(lock.yaw);
            return;
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setSpeed(0.0F);
        }
        entity.setYRot(lock.yaw);
        entity.setYHeadRot(lock.yaw);
        entity.setYBodyRot(lock.yaw);
        entity.setXRot(lock.pitch);
    }

    @Override
    public void onRemoved(LivingEntity entity, CustomEffectInstance instance) {
        StunLock lock = LOCKS.remove(entity.getUUID());
        if (entity.level().isClientSide) return;
        if (entity instanceof Mob mob && lock != null) mob.setNoAi(lock.wasNoAi);
    }

    public static boolean isStunned(LivingEntity entity) {
        return LOCKS.containsKey(entity.getUUID());
    }

    private record StunLock(double x, double y, double z, float yaw, float pitch, boolean wasNoAi) {
    }
}
