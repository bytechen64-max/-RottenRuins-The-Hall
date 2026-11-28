package org.bytechen.rottenruins.content.magic.effect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectBase;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectCategory;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectInstance;

/**
 * 圣翼：效果期间可以像鞘翅一样滑翔（由 {@code PlayerElytraMixin} 放开鞘翅判定），
 * 空中按一次跳跃向前冲刺，冷却是 1 秒，可以反复用。
 */
public class HolyWingsEffect extends CustomEffectBase {

    private static final double DASH_SPEED = 1.7d;
    private static final double DASH_LIFT = 0.18d;
    private static final int DASH_COOLDOWN = 20;

    /** [0] = 上次冲刺的 tick，[1] = 上一 tick 是否按着跳跃。 */
    private static final Map<UUID, int[]> JUMP = new ConcurrentHashMap<>();

    public HolyWingsEffect(ResourceLocation id) {
        super(id, CustomEffectCategory.BENEFICIAL, 0xFFE9A6);
    }

    @Override
    public void onEntityTick(ServerLevel level, LivingEntity entity, CustomEffectInstance instance) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int[] state = JUMP.computeIfAbsent(player.getUUID(), key -> new int[]{-100, 0});
        boolean jumping = player.jumping;
        // 只要离地就算——不要求当前已经在滑翔，否则落地收翼之后就再也冲不了第二次。
        boolean airborne = !player.onGround() && !player.isPassenger();
        if (jumping && state[1] == 0 && airborne && player.tickCount - state[0] >= DASH_COOLDOWN) {
            player.setSharedFlag(7, true);
            Vec3 dash = player.getLookAngle().scale(DASH_SPEED).add(0.0d, DASH_LIFT, 0.0d);
            player.setDeltaMovement(dash);
            player.hurtMarked = true;
            player.hasImpulse = true;
            player.fallDistance = 0.0f;
            state[0] = player.tickCount;
        }
        state[1] = jumping ? 1 : 0;
    }

    @Override
    public void onRemoved(LivingEntity entity, CustomEffectInstance instance) {
        JUMP.remove(entity.getUUID());
    }
}
