package org.bytechen.hall.overworld.registry.items.verdict;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天穹裁决 —— <b>三个技能共享的反馈总线</b>。
 *
 * <h3>为什么需要这一层</h3>
 * <p>三个技能原本各自 {@code playSound}，除此之外什么都没有：命中的那一下
 * 与没命中的那一下，在画面和声音上<b>完全一样</b>。结果是玩家读不出自己打没打中，
 * 也读不出这一招有多重 —— 手感问题里最常见、也最容易被漏掉的一类。</p>
 *
 * <p>把反馈收成一个入口之后，三个技能才有一致的"裁决"识别度：
 * 同一个音色、同一种顿帧、同一种相机回应，音调随命中数升高。</p>
 *
 * <h3>三个反馈通道</h3>
 * <ol>
 *   <li><b>顿帧</b>（{@link #landed}）—— 目标侧 {@code hurtTime} 拉满 + 伤害节拍清零。
 *       这是"打实了"最廉价也最有效的一招：目标闪红、动作被压一帧，
 *       视觉重量的来源其实在这里，而不在特效上。</li>
 *   <li><b>粒子爆发</b>（{@link #burst}）—— 一次 {@code sendParticles} 展开 count 个，
 *       绝不写"每粒子一个包"的循环（那是这个项目的既有约定）。</li>
 *   <li><b>相机</b>（{@link #shake} / {@link #fovKick}）—— 客户端读表，见
 *       {@code VerdictCameraEffects}。服务端只写"意图与强度"，方向/可见性判定在客户端做。</li>
 * </ol>
 *
 * <h3>为什么会话表要放在这里（服务端类）</h3>
 * <p>{@code shake} 是从服务端的技能结算里调用的，但它要影响的是客户端相机。
 * 把表放在这个两端都会加载的类里，客户端侧的处理器直接读同一张表即可 ——
 * 不需要新增网络包，单人游戏下天然生效。</p>
 */
public final class VerdictFeedback {

    // ══════════════════════════════════════════════════════════════
    //  相机反馈：服务端写，客户端读
    // ══════════════════════════════════════════════════════════════

    /** 抖动强度（0..1）。键是<b>触发者</b>的 UUID —— 只有他的相机该被摇。 */
    private static final Map<UUID, Float> SHAKE = new ConcurrentHashMap<>();
    /** FOV 追加量（度）。正数 = 视野变大 = 速度感。 */
    private static final Map<UUID, Float> FOV_KICK = new ConcurrentHashMap<>();

    /** 单次抖动的强度上限。再高就会晕，而不是"更有力"。 */
    private static final float MAX_SHAKE = 1.0f;
    /** FOV 追加的上限（度）。原版疾跑冲刺是 +10 左右，这里刻意压在它以下。 */
    private static final float MAX_FOV_KICK = 9.0f;

    private VerdictFeedback() {}

    /**
     * 请求一次相机抖动。
     *
     * <p>取 {@code max} 而不是累加：连续命中的两剑不该把镜头摇成筛子。
     * 强度的<b>方向与可见性</b>判定在 {@code VerdictCameraEffects} 里做 ——
     * 这一层只表达"这一下有多重"。</p>
     *
     * @param player   抖谁（一般是施法者本人）
     * @param strength 0..1，1 是满强度
     */
    public static void shake(@Nullable Player player, float strength) {
        if (player == null) return;
        float s = Mth.clamp(strength, 0f, MAX_SHAKE);
        if (s <= 0.001f) return;
        SHAKE.merge(player.getUUID(), s, Math::max);
    }

    /**
     * 请求一次 FOV 冲击（视野瞬间撑大再收回）。
     *
     * <p>这是"速度感"最有效的一招，比任何拖尾都直接 —— 突进之所以轻，
     * 一半原因就是镜头在整个过程中纹丝不动。</p>
     *
     * @param player 受益者
     * @param degrees 追加的视野度数（建议 3~6 度）
     */
    public static void fovKick(@Nullable Player player, float degrees) {
        if (player == null) return;
        float d = Mth.clamp(degrees, -MAX_FOV_KICK, MAX_FOV_KICK);
        if (Math.abs(d) <= 0.01f) return;
        FOV_KICK.merge(player.getUUID(), d, (a, b) -> Math.abs(b) > Math.abs(a) ? b : a);
    }

    /**
     * 在某个世界坐标处触发一次带距离衰减的抖动。
     *
     * <p>衰减用 {@code 8 / max(8, d)} 再钳到 [0.22, 1]：8 格以内基本满强度，
     * 超过后迅速衰减。这样"远处队友放大招"不会把你的镜头晃到没法瞄准，
     * 但近处的一击你躲不掉。下限 0.22 而不是 0，是因为<b>你脚下的裁决</b>
     * 即便坐标离你稍远也该有存在感。</p>
     *
     * <p>为什么放在这一层而不是客户端相机那个类里：本方法要被服务端的技能结算调用，
     * 而服务端类不该 import 一个 {@code Dist.CLIENT} 的类。这里只用玩家自己的坐标，
     * 两端都安全。</p>
     */
    public static void shakeNear(@Nullable Player player, Vec3 at, float full) {
        if (player == null || at == null) return;
        double d = player.position().distanceTo(at);
        float falloff = (float) Mth.clamp(8.0 / Math.max(8.0, d), 0.22, 1.0);
        shake(player, full * falloff);
    }

    /**
     * 客户端每帧读取并消耗一部分抖动强度。
     *
     * <p>衰减放在<b>读取方</b>，而不是另开一个 tick 计时器：相机事件本身
     * 就是每帧触发一次的，用它当驱动器不需要任何额外的生命周期管理
     * （退出世界/切换维度都不会留下残留状态）。</p>
     *
     * @param factor 本帧保留的比例（0.82 左右，与帧率无关的近似一阶衰减）
     */
    public static float consumeShake(UUID playerId, float factor) {
        Float cur = SHAKE.get(playerId);
        if (cur == null) return 0f;
        float v = cur;
        float next = v * Mth.clamp(factor, 0f, 1f);
        if (next <= 0.004f) SHAKE.remove(playerId);
        else SHAKE.put(playerId, next);
        return v;
    }

    /** 客户端每帧读取并消耗 FOV 追加量。 */
    public static float consumeFovKick(UUID playerId, float factor) {
        Float cur = FOV_KICK.get(playerId);
        if (cur == null) return 0f;
        float v = cur;
        float next = v * Mth.clamp(factor, 0f, 1f);
        if (Math.abs(next) <= 0.02f) FOV_KICK.remove(playerId);
        else FOV_KICK.put(playerId, next);
        return v;
    }

    /** 彻底清掉某个玩家的相机残留（死亡/换维度/登出时用）。 */
    public static void clearCamera(@Nullable Player player) {
        if (player == null) return;
        SHAKE.remove(player.getUUID());
        FOV_KICK.remove(player.getUUID());
    }

    // ══════════════════════════════════════════════════════════════
    //  命中：顿帧 + 音效
    // ══════════════════════════════════════════════════════════════

    /**
     * 在目标身上打出"被裁决砸中"的那一下。
     *
     * <h3>为什么写 {@code invulnerableTime = 0} 而不是给个无敌帧</h3>
     * <p>直觉上"顿帧"应该配无敌帧，但那会让裁决系自己的连续命中互相吞掉
     * （领域每 1.2 秒一剑、剑气沿路径取样多次），表现为"打中了但没掉血" ——
     * 这正好与"手感更强"相反。所以这里<b>只取视觉</b>：{@code hurtTime} 拉满
     * 让目标闪红、模型抖动一下，同时把伤害节拍清零，让下一次命中立刻生效。</p>
     *
     * @param target 被打中的生物
     * @param weight 命中权重（0..1），来自 {@link #weightOf}
     * @return 是否真的产生了反馈（false = 目标已经在这个受击窗口里，别重复播）
     */
    public static boolean landed(@Nullable LivingEntity target, float weight) {
        if (target == null || !target.isAlive()) return false;

        boolean fresh = target.hurtTime <= 0;
        target.hurtTime = Math.max(target.hurtTime, target.hurtDuration);
        // 让同一招的后续命中不被这一下挡住（见方法注释）
        target.invulnerableTime = 0;
        return fresh;
    }

    /**
     * 命中数 → 反馈权重。
     *
     * <p>曲线刻意是"开方而不是线性"：1 个目标就已经应该是满档的重击感，
     * 5 个目标只是"更响一点点"。线性映射会让群怪场景的音量和抖动失控。</p>
     *
     * @param hits 本次技能命中的目标数
     */
    public static float weightOf(int hits) {
        if (hits <= 0) return 0f;
        return Mth.clamp(Mth.sqrt(hits) / Mth.sqrt(4f), 0f, 1f);
    }

    /**
     * 命中音调：命中越多、音越高。
     *
     * <p>升调是让玩家"听出自己这一下打了几个人"的唯一手段 —— 不用看屏幕。</p>
     */
    public static float hitPitch(int hits) {
        return Mth.clamp(1.0f + 0.10f * Math.max(0, hits - 1), 1.0f, 1.7f);
    }

    // ══════════════════════════════════════════════════════════════
    //  世界反馈：粒子与冲击波
    // ══════════════════════════════════════════════════════════════

    /**
     * 在指定位置打一次粒子爆发。
     *
     * <p>一次 {@code sendParticles} 展开 {@code count} 个粒子是原版的常规做法，
     * 客户端会本地展开成多个 —— <b>绝对不要</b>写成逐粒子的循环发包
     * （那是每粒子一个包，一次爆发就是几十个包）。</p>
     *
     * @param speed 粒子初速的随机幅度；0 表示原地不动
     */
    public static void burst(Level level, Vec3 pos,
                             ParticleOptions particle, int count, double speed) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(particle, pos.x, pos.y, pos.z, count,
                0.35, 0.35, 0.35, speed);
    }

    /** 在指定位置按给定半径播一次音效。 */
    public static void playSound(Level level, Vec3 pos, SoundEvent sound,
                                 float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * 落地冲击环：一个从中心向外扩散的冲击波实体。
     *
     * <p>冲击波是本项目里已经验证过的资产（带光影兼容的延迟回放路径），
     * 复用它比任何新特效都便宜，而且"裁决落地 → 地面被压出一圈"这条语义
     * 本来就和它的形状吻合。</p>
     *
     * @param maxRadius 最终半径（格）
     * @param speed     扩散速度（格/秒），3.0 是 {@code ShockwaveEntity} 的默认值档
     */
    public static void impactRing(Level level, Vec3 pos, float maxRadius,
                                  float speed, float alpha) {
        ShockwaveEntity.spawn(level, pos, speed, maxRadius, 24, Mth.clamp(alpha, 0f, 4f));
    }
}
