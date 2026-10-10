package org.bytechen.hall.overworld.registry.entities.population.apostle;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 使徒技能音效。
 *
 * <h3>为什么用原版音源而不是自带 ogg</h3>
 * 本模组的 {@code assets/hall/sounds/} 里只有一首群系音乐和三首环境音，
 * 没有可用的技能音效素材；临时合成的声音不可能"好听"。所以这里全部复用原版
 * （Mojang 录制、混音都到位的）音源，按"技能阶段"分层组合：
 * 起手 → 蓄力 → 爆发 → 持续 → 收尾，每一层选一个音色互补的音源，
 * 再用 volume / pitch 拉开距离感。
 *
 * <h3>以后要换成自己的音效</h3>
 * <ol>
 *   <li>{@code SoundEventRegistry} 里 {@code registerSound("apostle_xxx")} 注册事件；</li>
 *   <li>{@code assets/hall/sounds/} 放 ogg、由 {@code SoundData} datagen 生成 sounds.json；</li>
 *   <li>只改本类里的常量即可 —— 调用方只认这里的语义化方法名。</li>
 * </ol>
 *
 * <p><b>所有方法都必须在服务端调用</b>：原版会把声音广播给附近玩家，
 * 客户端本地调用则只有自己能听见。</p>
 */
public final class ApostleSounds {

    // ══════════════════════════════════════════════════════════════
    // 音源表（想换音色只改这里）
    // ══════════════════════════════════════════════════════════════

    /** 起手：低沉咆哮，宣告技能开始。 */
    private static final SoundEvent CAST_ROAR = SoundEvents.ENDER_DRAGON_GROWL;
    /** 前摇蓄力脉冲：每 10 tick 一次，音调随进度升高。 */
    private static final SoundEvent CHARGE_PULSE = SoundEvents.RESPAWN_ANCHOR_CHARGE;
    /** 前摇起点的点亮音。 */
    private static final SoundEvent CHARGE_START = SoundEvents.BEACON_ACTIVATE;
    /** 冲击波（纯视觉波前的听觉对应物）。 */
    private static final SoundEvent SHOCKWAVE_BOOM = SoundEvents.GENERIC_EXPLODE;
    private static final SoundEvent SHOCKWAVE_CHARGE = SoundEvents.WARDEN_SONIC_CHARGE;
    /** 黑洞成形：标志性的重击 + 低频铺底。 */
    private static final SoundEvent HOLE_OPEN = SoundEvents.WARDEN_SONIC_BOOM;
    private static final SoundEvent HOLE_OPEN_BODY = SoundEvents.END_PORTAL_SPAWN;
    /** 注意：原版有些条目是 {@code Holder.Reference<SoundEvent>}（这一个就是），要取 .value()。 */
    private static final SoundEvent HOLE_OPEN_TAIL = SoundEvents.RESPAWN_ANCHOR_DEPLETE.value();
    /** 黑洞存在期间的低频嗡鸣 + 心跳。 */
    private static final SoundEvent HOLE_HUM = SoundEvents.RESPAWN_ANCHOR_AMBIENT;
    private static final SoundEvent HOLE_HEARTBEAT = SoundEvents.WARDEN_HEARTBEAT;
    /** 斩击起手：法术吟唱 + 振翅。 */
    private static final SoundEvent SLASH_CAST = SoundEvents.ILLUSIONER_CAST_SPELL;
    private static final SoundEvent SLASH_CAST_WHOOSH = SoundEvents.ENDER_DRAGON_FLAP;
    /** 单刀破空。 */
    private static final SoundEvent SLASH_SWING = SoundEvents.PLAYER_ATTACK_SWEEP;
    /** 每 5 刀一次的重音。 */
    private static final SoundEvent SLASH_ACCENT = SoundEvents.TRIDENT_RIPTIDE_2;
    /** 连斩收尾。 */
    private static final SoundEvent SLASH_FINISH = SoundEvents.TRIDENT_RIPTIDE_3;
    private static final SoundEvent SLASH_FINISH_HIT = SoundEvents.PLAYER_ATTACK_CRIT;

    private ApostleSounds() {}

    // ══════════════════════════════════════════════════════════════
    // 黑洞技能
    // ══════════════════════════════════════════════════════════════

    /** 黑洞技能起手（前摇第一 tick）。 */
    public static void blackHoleCastStart(Level level, Vec3 pos) {
        play(level, CAST_ROAR, pos, 1.2f, 0.75f);
        play(level, CHARGE_START, pos, 0.9f, 1.35f);
    }

    /**
     * 前摇蓄力脉冲。
     *
     * @param progress 前摇进度 0~1；音调随它从 0.85 升到 1.75，
     *                 听感上是"越充越高"，在爆发前一刻最尖
     */
    public static void blackHoleCharge(Level level, Vec3 pos, float progress) {
        float t = Math.max(0f, Math.min(1f, progress));
        play(level, CHARGE_PULSE, pos, 0.75f + 0.35f * t, 0.85f + 0.90f * t);
    }

    /** 冲击波炸开（黑洞出现前 4 tick）。 */
    public static void blackHoleShockwave(Level level, Vec3 pos) {
        play(level, SHOCKWAVE_BOOM, pos, 1.3f, 0.70f);
        play(level, SHOCKWAVE_CHARGE, pos, 1.0f, 0.80f);
    }

    /** 黑洞成形。 */
    public static void blackHoleOpen(Level level, Vec3 pos) {
        play(level, HOLE_OPEN, pos, 1.6f, 0.55f);
        play(level, HOLE_OPEN_BODY, pos, 1.0f, 0.50f);
        play(level, HOLE_OPEN_TAIL, pos, 1.0f, 0.45f);
    }

    /** 黑洞存在期间的低频嗡鸣（每 20 tick 一次）。 */
    public static void blackHoleHum(Level level, Vec3 pos, int lifeTick) {
        play(level, HOLE_HUM, pos, 1.0f, 0.45f + 0.05f * (lifeTick % 3));
        if (lifeTick % 40 == 0) {
            play(level, HOLE_HEARTBEAT, pos, 0.35f, 1.40f);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 斩击技能
    // ══════════════════════════════════════════════════════════════

    /** 斩击起手（连斩第一刀之前）。 */
    public static void slashCastStart(Level level, Vec3 pos) {
        play(level, SLASH_CAST, pos, 1.2f, 1.50f);
        play(level, SLASH_CAST_WHOOSH, pos, 1.0f, 1.70f);
    }

    /**
     * 单刀破空。
     *
     * @param index 第几刀（0 起）。每 5 刀加一层重音，20 刀听下来是
     *              "破空×4 + 重击" 的呼吸节奏，而不是 20 个一模一样的音
     */
    public static void slashHit(Level level, Vec3 pos, int index) {
        float jitter = (level.random.nextFloat() - 0.5f) * 0.24f;   // ±0.12 音高抖动
        play(level, SLASH_SWING, pos, 0.95f, 1.15f + jitter);
        if (index % 5 == 4) {
            play(level, SLASH_ACCENT, pos, 0.85f, 1.25f + jitter * 0.5f);
        }
    }

    /** 连斩收尾。 */
    public static void slashFinish(Level level, Vec3 pos) {
        play(level, SLASH_FINISH, pos, 1.1f, 1.05f);
        play(level, SLASH_FINISH_HIT, pos, 1.0f, 0.95f);
    }

    // ══════════════════════════════════════════════════════════════
    // 尾杀：坍缩
    // ══════════════════════════════════════════════════════════════

    /** 尾杀起手：本体开始坍缩。 */
    public static void finisherStart(Level level, Vec3 pos) {
        play(level, CAST_ROAR, pos, 1.7f, 0.55f);
        play(level, HOLE_OPEN_BODY, pos, 1.1f, 0.42f);
        play(level, CHARGE_PULSE, pos, 1.0f, 0.60f);
    }

    /**
     * 尾杀第一阶段的脉冲（每 10 tick 一次，<b>不要</b>每 2 tick 一发）。
     * <p>冲击波本身每 2 tick 生成一个（5 秒 50 发），但音效按 2 tick 一发就是
     * 每秒 10 个 {@code playSound} × 50 次——听感糊成一片，发包量也没必要。
     * 所以音频只留"每 10 tick 一记、音调随进度升高"的骨架，视觉自己去密。</p>
     *
     * @param progress 阶段进度 0~1
     */
    public static void finisherStormPulse(Level level, Vec3 pos, float progress) {
        float t = Math.max(0f, Math.min(1f, progress));
        play(level, SHOCKWAVE_CHARGE, pos, 0.85f + 0.35f * t, 0.70f + 0.80f * t);
        if ((int) (t * 10f) % 2 == 1) {
            play(level, SHOCKWAVE_BOOM, pos, 0.9f, 0.65f + 0.25f * t);
        }
    }

    /** 巨型黑洞成形：尾杀最重的一记。 */
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

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    /**
     * 统一播放入口。
     * <p>{@code except=null} ⇒ 广播给范围内所有玩家；{@code SoundSource.HOSTILE}
     * ⇒ 跟随"敌对生物"音量滑块，玩家能自己调小。</p>
     */
    private static void play(Level level, SoundEvent event, Vec3 pos, float volume, float pitch) {
        if (level == null || level.isClientSide() || event == null || pos == null) return;
        level.playSound(null, pos.x, pos.y, pos.z, event, SoundSource.HOSTILE, volume, pitch);
    }
}
