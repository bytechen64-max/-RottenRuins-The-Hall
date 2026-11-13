package org.bytechen.rottenruins.overworld.registry.items.verdict;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/**
 * 截空 · 凌空斩的位移执行器 —— 在 {@link #DURATION} tick 内每 tick 重申一次水平速度。
 * <p>一次性给速度不行：原版激流另配了 {@code startAutoSpinAttack} 接管移动，缺了它下一 tick 就回到
 * 地面物理（摩擦 0.546，一 tick 就把速度打成 0.85）；而且位置权威在客户端，持续重申才能让意图收敛。
 * 状态存在 {@link Player#getPersistentData()}，由物品的 {@code inventoryTick} 驱动，撞墙即中止。
 */
public final class VerdictDash {

    /** 突进持续（tick）。 */
    public static final int DURATION = 12;

    private static final String ROOT = "HallVerdictDash";
    private static final String K_TICKS = "T";
    private static final String K_DIR_X = "Dx";
    private static final String K_DIR_Z = "Dz";
    private static final String K_SPEED = "S";

    /** 每 tick 的速度倍率（起步快、收尾慢）；算术平均必须是 1.0，否则总位移与反解距离对不上。 */
    private static final float[] SPEED_ENVELOPE = {
            1.34f, 1.24f, 1.15f, 1.07f, 1.00f, 0.94f,
            0.88f, 0.83f, 0.78f, 0.74f, 0.70f, 0.66f
    };

    /** 收招后保留的水平速度比例：归零会像撞上空气墙，留 38% 让尾巴接回原版移动物理。 */
    private static final double RESIDUE = 0.38;

    /** 中止条件：撞到方块（水平碰撞）。 */
    private static boolean stopOnCollision = true;

    private VerdictDash() {}

    /** 开始一次突进：{@code dirX/dirZ} 为水平单位向量，{@code speed} 为每 tick 的水平速度。 */
    public static void start(Player player, double dirX, double dirZ, double speed) {
        CompoundTag t = player.getPersistentData();
        CompoundTag d = new CompoundTag();
        d.putInt(K_TICKS, DURATION);
        d.putDouble(K_DIR_X, dirX);
        d.putDouble(K_DIR_Z, dirZ);
        d.putDouble(K_SPEED, speed);
        t.put(ROOT, d);
    }

    public static boolean isActive(Player player) {
        CompoundTag d = player.getPersistentData().getCompound(ROOT);
        return d.contains(K_TICKS) && d.getInt(K_TICKS) > 0;
    }

    /** 主动中止（例如被攻击、上了坐骑）。 */
    public static void stop(Player player) {
        player.getPersistentData().remove(ROOT);
    }

    /** 每 tick 由物品的 {@code inventoryTick} 调用；只在服务端有效（客户端位置由服务端同步，且没有权威碰撞位）。 */
    public static void tick(Player player) {
        Level level = player.level();
        CompoundTag t = player.getPersistentData();
        if (!t.contains(ROOT)) return;

        CompoundTag d = t.getCompound(ROOT);
        int left = d.getInt(K_TICKS);
        if (left <= 0) {
            t.remove(ROOT);
            return;
        }

        double speed = d.getDouble(K_SPEED);
        double dirX = d.getDouble(K_DIR_X);
        double dirZ = d.getDouble(K_DIR_Z);

        // 撞墙 / 骑乘 / 死亡：立刻结束，别让玩家贴着墙抖；撞墙额外给一次钝响 + 短抖作反馈
        if (player.isPassenger() || !player.isAlive()
                || (stopOnCollision && player.horizontalCollision)) {
            t.remove(ROOT);
            if (player.horizontalCollision && player.isAlive()) {
                spawnWallImpact(player);
            }
            VerdictDebug.log("  dash 中止 left=%d collision=%b passenger=%b",
                    left, player.horizontalCollision, player.isPassenger());
            return;
        }

        // 用 DURATION - left 而不是外部计数器：状态全在这一个 NBT 里，中途重启也不会读到错的相位
        int elapsed = DURATION - left;
        float env = SPEED_ENVELOPE[Math.floorMod(elapsed, SPEED_ENVELOPE.length)];
        double v = speed * env;

        // 重申水平速度；纵向保持原版物理（地面给一点抬升，让连按能升空）
        double lift = player.onGround() ? 0.42 : player.getDeltaMovement().y;
        player.setDeltaMovement(dirX * v, lift, dirZ * v);
        player.hurtMarked = true;
        player.hasImpulse = true;

        d.putInt(K_TICKS, left - 1);
        if (left - 1 <= 0) {
            t.remove(ROOT);
            double residue = speed * RESIDUE;
            double vy = player.onGround() ? 0.0 : player.getDeltaMovement().y;
            player.setDeltaMovement(dirX * residue, vy, dirZ * residue);
            player.hurtMarked = true;
            VerdictDebug.log("  dash 完成（余速 %.3f 保留）", residue);
        }
    }

    /** 撞到方块时的反馈：一声钝响 + 一次短抖，刻意不加粒子（撞墙是失败的突进）。 */
    private static void spawnWallImpact(Player player) {
        Level level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5f, 0.6f);
        VerdictFeedback.shakeNear(player, player.position(), 0.3f);
    }
}
