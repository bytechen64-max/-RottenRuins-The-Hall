package org.bytechen.hall.overworld.registry.items;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.client.rend.twitch.ITwitchItem;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.List;

public class InfEnderPearItem extends Item implements ITwitchItem {

    // 冷却时间（单位：Tick，20 Tick = 1秒），这里设置为5秒
    private static final int COOLDOWN_TICKS = 100;
    // 交互距离
    private static final double REACH_DISTANCE = 24.0;

    public InfEnderPearItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 仅在服务端执行逻辑（防止双端不同步），客户端只播放效果
        if (level.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }

        // 1. 射线追踪：检测玩家看向的是实体还是方块
        HitResult hitResult = player.pick(REACH_DISTANCE, 1.0F, false);

        // ----- 情况 A：瞄准了实体（生物或玩家） -> 交换位置 -----
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hitResult;
            Entity target = entityHit.getEntity();

            if (target instanceof LivingEntity livingTarget) {
                // 执行交换
                Vec3 playerPos = player.position();
                Vec3 targetPos = target.position();

                // 注意：在 1.20.1 中，玩家调用 teleportTo 会自动处理网络同步
                player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
                target.teleportTo(playerPos.x, playerPos.y, playerPos.z);

                // 特效：传送门粒子爆发
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            playerPos.x, playerPos.y, playerPos.z,
                            80, 0.5, 0.5, 0.5, 0.1);
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            targetPos.x, targetPos.y, targetPos.z,
                            80, 0.5, 0.5, 0.5, 0.1);
                }

                // 音效
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

                // 如果目标不是玩家（即怪物/动物），给它上 DeBuff 方便你反制
                if (!(target instanceof Player)) {
                    livingTarget.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    livingTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                }

                // 消耗物品（创造模式除外）
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                // 添加冷却
                player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                return InteractionResultHolder.success(stack);
            }
        }

        // ----- 情况 B：瞄准了方块（地面/墙壁） -> 瞬移并释放冲击波 -----
        else if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            // 获取点击到的方块坐标，并传送到该方块的上方 1.5 格处（避免卡进地板）
            Vec3 teleportPos = blockHit.getBlockPos().getCenter().add(0, 1.5, 0);

            // 安全检查：如果该位置被堵死，就取消传送
            if (level.getBlockState(blockHit.getBlockPos().above()).isSolid()) {
                return InteractionResultHolder.fail(stack);
            }

            // 执行传送
            player.teleportTo(teleportPos.x, teleportPos.y, teleportPos.z);

            // 特效：落地冲击波（传送门粒子）
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        teleportPos.x, teleportPos.y, teleportPos.z,
                        120, 3.0, 1.0, 3.0, 0.2);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);

            // 附加趣味机制：末影冲击波（击退周围 4 格内的非玩家怪物并造成伤害）
            AABB aabb = player.getBoundingBox().inflate(4.0);
            List<Entity> nearbyEntities = level.getEntities(player, aabb);
            for (Entity entity : nearbyEntities) {
                if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                    Vec3 delta = entity.position().subtract(player.position());
                    double distance = delta.length();
                    if (distance < 4.0 && distance > 0.5) {
                        // 击退力度随距离衰减
                        double strength = 2.5 / (distance + 0.5);
                        living.setDeltaMovement(delta.normalize().scale(strength));
                        // 造成 3 点魔法伤害（无视护甲）
                        living.hurt(level.damageSources().magic(), 3.0F);
                    }
                }
            }

            // 消耗物品 + 冷却
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            return InteractionResultHolder.success(stack);
        }

        // 如果瞄到天空或远处（未命中任何东西），则无事发生
        return InteractionResultHolder.fail(stack);
    }
}