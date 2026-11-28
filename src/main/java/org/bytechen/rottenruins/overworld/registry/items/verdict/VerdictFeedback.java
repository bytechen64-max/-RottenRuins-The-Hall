package org.bytechen.rottenruins.overworld.registry.items.verdict;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.ShockwaveEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天穹裁决 —— 三个技能共享的反馈总线（顿帧 / 粒子 / 相机抖动 / FOV）。
 * <p>会话表放在两端都会加载的这个类里，客户端相机直接读同一张表，不需要新增网络包。
 */
public final class VerdictFeedback {

    /** 抖动强度（0..1）。键是<b>触发者</b>的 UUID —— 只有他的相机该被摇。 */
    private static final Map<UUID, Float> SHAKE = new ConcurrentHashMap<>();
    /** FOV 追加量（度）。正数 = 视野变大 = 速度感。 */
    private static final Map<UUID, Float> FOV_KICK = new ConcurrentHashMap<>();

    /** 单次抖动的强度上限。再高就会晕，而不是"更有力"。 */
    private static final float MAX_SHAKE = 1.0f;
    /** FOV 追加的上限（度）。原版疾跑冲刺是 +10 左右，这里刻意压在它以下。 */
    private static final float MAX_FOV_KICK = 9.0f;

    private VerdictFeedback() {}

    /** 请求一次相机抖动。取 {@code max} 而不是累加：连续命中不该把镜头摇成筛子；方向与可见性判定在 {@code VerdictCameraEffects}。 */
    public static void shake(@Nullable Player player, float strength) {
        if (player == null) return;
        float s = Mth.clamp(strength, 0f, MAX_SHAKE);
        if (s <= 0.001f) return;
        SHAKE.merge(player.getUUID(), s, Math::max);
    }

    /** 请求一次 FOV 冲击（视野瞬间撑大再收回，建议 3~6 度）。 */
    public static void fovKick(@Nullable Player player, float degrees) {
        if (player == null) return;
        float d = Mth.clamp(degrees, -MAX_FOV_KICK, MAX_FOV_KICK);
        if (Math.abs(d) <= 0.01f) return;
        FOV_KICK.merge(player.getUUID(), d, (a, b) -> Math.abs(b) > Math.abs(a) ? b : a);
    }

    /** 在某个世界坐标处触发一次带距离衰减的抖动。衰减 {@code 8 / max(8, d)} 钳到 [0.22, 1]（下限不为 0，脚下的裁决也要有存在感）。 */
    public static void shakeNear(@Nullable Player player, Vec3 at, float full) {
        if (player == null || at == null) return;
        double d = player.position().distanceTo(at);
        float falloff = (float) Mth.clamp(8.0 / Math.max(8.0, d), 0.22, 1.0);
        shake(player, full * falloff);
    }

    /** 客户端每帧读取并消耗一部分抖动强度。衰减放在读取方：相机事件每帧触发一次，无需另设计时器。 */
    public static float consumeShake(UUID playerId, float factor) {
        Float cur = SHAKE.get(playerId);
        if (cur == null) return 0f;
        float v = cur;
        float next = v * Mth.clamp(factor, 0f, 1f);
        if (next <= 0.004f) SHAKE.remove(playerId);
        else SHAKE.put(playerId, next);
        return v;
    }

    public static float consumeFovKick(UUID playerId, float factor) {
        Float cur = FOV_KICK.get(playerId);
        if (cur == null) return 0f;
        float v = cur;
        float next = v * Mth.clamp(factor, 0f, 1f);
        if (Math.abs(next) <= 0.02f) FOV_KICK.remove(playerId);
        else FOV_KICK.put(playerId, next);
        return v;
    }

    public static void clearCamera(@Nullable Player player) {
        if (player == null) return;
        SHAKE.remove(player.getUUID());
        FOV_KICK.remove(player.getUUID());
    }

    /**
     * 在目标身上打出"被裁决砸中"的那一下。{@code invulnerableTime = 0}：裁决系自己会连续命中
     * （领域每 1.2 秒一剑、剑气沿路径取样多次），给无敌帧会被自己吞掉，表现为"打中了但没掉血"。
     */
    public static boolean landed(@Nullable LivingEntity target, float weight) {
        if (target == null || !target.isAlive()) return false;

        boolean fresh = target.hurtTime <= 0;
        target.hurtTime = Math.max(target.hurtTime, target.hurtDuration);
        target.invulnerableTime = 0;
        return fresh;
    }

    /** 命中数 → 反馈权重。刻意开方而非线性：线性映射会让群怪场景的音量与抖动失控。 */
    public static float weightOf(int hits) {
        if (hits <= 0) return 0f;
        return Mth.clamp(Mth.sqrt(hits) / Mth.sqrt(4f), 0f, 1f);
    }

    public static float hitPitch(int hits) {
        return Mth.clamp(1.0f + 0.10f * Math.max(0, hits - 1), 1.0f, 1.7f);
    }

    /** 在指定位置打一次粒子爆发。<b>绝对不要</b>写成逐粒子的循环发包（一次爆发就是几十个包）。 */
    public static void burst(Level level, Vec3 pos,
                             ParticleOptions particle, int count, double speed) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(particle, pos.x, pos.y, pos.z, count,
                0.35, 0.35, 0.35, speed);
    }

    public static void playSound(Level level, Vec3 pos, SoundEvent sound,
                                 float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** 落地冲击环。复用已在项目里验证过的冲击波实体；{@code speed} 单位格/秒，3.0 是默认档。 */
    public static void impactRing(Level level, Vec3 pos, float maxRadius,
                                  float speed, float alpha) {
        ShockwaveEntity.spawn(level, pos, speed, maxRadius, 24, Mth.clamp(alpha, 0f, 4f));
    }
}
