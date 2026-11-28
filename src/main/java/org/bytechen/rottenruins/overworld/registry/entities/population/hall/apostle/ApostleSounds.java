package org.bytechen.rottenruins.overworld.registry.entities.population.hall.apostle;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 使徒技能音效：模组没有技能音效素材，全部复用原版音源，按"起手 → 蓄力 → 爆发 → 持续 → 收尾"分层，
 * 用 volume / pitch 拉距离感。所有方法必须在服务端调用（原版只在服务端把声音广播给附近玩家）；换音色只改下面的常量。
 */
public final class ApostleSounds {

    private static final SoundEvent CAST_ROAR = SoundEvents.ENDER_DRAGON_GROWL;
    /** 前摇蓄力脉冲：每 10 tick 一次，音调随进度升高。 */
    private static final SoundEvent CHARGE_PULSE = SoundEvents.RESPAWN_ANCHOR_CHARGE;
    private static final SoundEvent CHARGE_START = SoundEvents.BEACON_ACTIVATE;
    private static final SoundEvent SHOCKWAVE_BOOM = SoundEvents.GENERIC_EXPLODE;
    private static final SoundEvent SHOCKWAVE_CHARGE = SoundEvents.WARDEN_SONIC_CHARGE;
    private static final SoundEvent HOLE_OPEN = SoundEvents.WARDEN_SONIC_BOOM;
    private static final SoundEvent HOLE_OPEN_BODY = SoundEvents.END_PORTAL_SPAWN;
    /** 注意：原版有些条目是 {@code Holder.Reference<SoundEvent>}（这一个就是），要取 .value()。 */
    private static final SoundEvent HOLE_OPEN_TAIL = SoundEvents.RESPAWN_ANCHOR_DEPLETE.value();
    private static final SoundEvent HOLE_HUM = SoundEvents.RESPAWN_ANCHOR_AMBIENT;
    private static final SoundEvent HOLE_HEARTBEAT = SoundEvents.WARDEN_HEARTBEAT;
    private static final SoundEvent SLASH_CAST = SoundEvents.ILLUSIONER_CAST_SPELL;
    private static final SoundEvent SLASH_CAST_WHOOSH = SoundEvents.ENDER_DRAGON_FLAP;
    private static final SoundEvent SLASH_SWING = SoundEvents.PLAYER_ATTACK_SWEEP;
    private static final SoundEvent SLASH_ACCENT = SoundEvents.TRIDENT_RIPTIDE_2;
    private static final SoundEvent SLASH_FINISH = SoundEvents.TRIDENT_RIPTIDE_3;
    private static final SoundEvent SLASH_FINISH_HIT = SoundEvents.PLAYER_ATTACK_CRIT;

    private ApostleSounds() {}

    public static void blackHoleCastStart(Level level, Vec3 pos) {
        play(level, CAST_ROAR, pos, 1.2f, 0.75f);
        play(level, CHARGE_START, pos, 0.9f, 1.35f);
    }

    /** 前摇蓄力脉冲：音调随 progress（0~1）从 0.85 升到 1.75，爆发前一刻最尖。 */
    public static void blackHoleCharge(Level level, Vec3 pos, float progress) {
        float t = Math.max(0f, Math.min(1f, progress));
        play(level, CHARGE_PULSE, pos, 0.75f + 0.35f * t, 0.85f + 0.90f * t);
    }

    /** 冲击波炸开（黑洞出现前 4 tick）。 */
    public static void blackHoleShockwave(Level level, Vec3 pos) {
        play(level, SHOCKWAVE_BOOM, pos, 1.3f, 0.70f);
        play(level, SHOCKWAVE_CHARGE, pos, 1.0f, 0.80f);
    }

    public static void blackHoleOpen(Level level, Vec3 pos) {
        play(level, HOLE_OPEN, pos, 1.6f, 0.55f);
        play(level, HOLE_OPEN_BODY, pos, 1.0f, 0.50f);
        play(level, HOLE_OPEN_TAIL, pos, 1.0f, 0.45f);
    }

    /** 存在期间的低频嗡鸣（每 20 tick 一次），每 40 tick 补一记心跳。 */
    public static void blackHoleHum(Level level, Vec3 pos, int lifeTick) {
        play(level, HOLE_HUM, pos, 1.0f, 0.45f + 0.05f * (lifeTick % 3));
        if (lifeTick % 40 == 0) {
            play(level, HOLE_HEARTBEAT, pos, 0.35f, 1.40f);
        }
    }

    public static void slashCastStart(Level level, Vec3 pos) {
        play(level, SLASH_CAST, pos, 1.2f, 1.50f);
        play(level, SLASH_CAST_WHOOSH, pos, 1.0f, 1.70f);
    }

    /** 单刀破空；每 5 刀加一层重音，20 刀听下来是"破空×4 + 重击"的呼吸节奏。 */
    public static void slashHit(Level level, Vec3 pos, int index) {
        float jitter = (level.random.nextFloat() - 0.5f) * 0.24f;   // ±0.12 音高抖动
        play(level, SLASH_SWING, pos, 0.95f, 1.15f + jitter);
        if (index % 5 == 4) {
            play(level, SLASH_ACCENT, pos, 0.85f, 1.25f + jitter * 0.5f);
        }
    }

    public static void slashFinish(Level level, Vec3 pos) {
        play(level, SLASH_FINISH, pos, 1.1f, 1.05f);
        play(level, SLASH_FINISH_HIT, pos, 1.0f, 0.95f);
    }

    public static void finisherStart(Level level, Vec3 pos) {
        play(level, CAST_ROAR, pos, 1.7f, 0.55f);
        play(level, HOLE_OPEN_BODY, pos, 1.1f, 0.42f);
        play(level, CHARGE_PULSE, pos, 1.0f, 0.60f);
    }

    /** 尾杀第一阶段的脉冲（每 10 tick 一次）：冲击波本身每 2 tick 生成一个，但音效按 2 tick 一发会糊成一片、
     *  发包量也没必要，所以音频只留"音调随进度升高"的骨架，视觉自己去密。 */
    public static void finisherStormPulse(Level level, Vec3 pos, float progress) {
        float t = Math.max(0f, Math.min(1f, progress));
        play(level, SHOCKWAVE_CHARGE, pos, 0.85f + 0.35f * t, 0.70f + 0.80f * t);
        if ((int) (t * 10f) % 2 == 1) {
            play(level, SHOCKWAVE_BOOM, pos, 0.9f, 0.65f + 0.25f * t);
        }
    }

    public static void finisherCollapse(Level level, Vec3 pos) {
        play(level, SHOCKWAVE_BOOM, pos, 1.8f, 0.45f);
        play(level, HOLE_OPEN, pos, 1.8f, 0.45f);
        play(level, HOLE_OPEN_BODY, pos, 1.4f, 0.38f);
        play(level, HOLE_OPEN_TAIL, pos, 1.2f, 0.35f);
    }

    /** 尾杀结束（15 秒走完）。 */
    public static void finisherEnd(Level level, Vec3 pos) {
        play(level, HOLE_OPEN_TAIL, pos, 1.0f, 0.55f);
        play(level, SLASH_FINISH_HIT, pos, 0.8f, 0.60f);
    }

    /** 统一播放入口：{@code except=null} ⇒ 广播给范围内所有玩家；{@code SoundSource.HOSTILE} ⇒ 跟随"敌对生物"音量滑块。 */
    private static void play(Level level, SoundEvent event, Vec3 pos, float volume, float pitch) {
        if (level == null || level.isClientSide() || event == null || pos == null) return;
        level.playSound(null, pos.x, pos.y, pos.z, event, SoundSource.HOSTILE, volume, pitch);
    }
}
