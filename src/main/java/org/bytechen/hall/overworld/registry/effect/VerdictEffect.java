package org.bytechen.hall.overworld.registry.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;
import org.bytechen.hall.overworld.registry.items.DomeriteLongsword;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictTuning;

/**
 * 裁决 —— 天穹裁决在任意裁决系命中后给自己叠的增益。
 *
 * <h3>它为什么不加伤害</h3>
 * <p>这把剑的伤害已经在 {@code VerdictDamage} 里定死（25 hurt + 5 setHealth = 30），
 * 所以层数<b>一律不碰伤害</b>，只喂"覆盖"：</p>
 * <ul>
 *   <li>每层给光柱半径 {@link DomeriteLongsword#BEAM_RADIUS_PER_LEVEL}；</li>
 *   <li>每层给领域出剑提速 {@link DomeriteLongsword#FIELD_RATE_PER_LEVEL}。</li>
 * </ul>
 *
 * <p>这样两条曲线各管一段、不互相放大：</p>
 * <pre>
 *   Y 坐标   → 你在哪      → 光柱/突进/领域的<b>基础</b>覆盖
 *   裁决层数 → 你打得多顺  → 覆盖的<b>临时</b>增幅（5 秒不命中就掉光）
 * </pre>
 *
 * <p>如果层数也给伤害，就会出现"命中 → 更强 → 更容易命中"的正反馈，
 * 在群怪场景里会滚成雪球 —— 这是次要伤害组件最常见的失控方式。</p>
 *
 * <h3>为什么用 {@code MobEffect} 而不是自定义 Capability</h3>
 * <ul>
 *   <li>外层语义正好吻合：只需要"一个有等级、会自然过期、能显示在 HUD 上的计时器"；</li>
 *   <li>免掉网络同步（{@code MobEffectInstance} 原版就同步）、免掉死亡/换维度搬运；</li>
 *   <li>玩家能直接在右上角看到自己攒了几层，机制可读。</li>
 * </ul>
 */
public class VerdictEffect extends MobEffect {

    /** 单例。注册走 {@code RegisterEffect.VERDICT}。 */
    public static final VerdictEffect INSTANCE = new VerdictEffect();

    /** HUD 粒子颜色：天蓝，与长剑描边主色一致。 */
    private static final int COLOR = 0x87CEFA;

    private VerdictEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    /** 当前层数（0 = 无增益）。 */
    public static int level(Player player) {
        var inst = player.getEffect(INSTANCE);
        return inst == null ? 0 : inst.getAmplifier() + 1;
    }

    /**
     * 每层的实际增益 = 基础值 × 配置倍率。
     * <p>{@code verdictStackScale} 设 0 就等于彻底关掉层数机制
     * （层数仍会叠、HUD 仍会显示，但不再提供任何加成）。</p>
     */
    private static float perLevel(float base) {
        return base * VerdictTuning.stackScale();
    }

    /** 光柱半径倍率（1.0 + 层数 × 每层加成）。 */
    public static float beamRadiusScale(Player player) {
        return 1.0f + level(player) * perLevel(DomeriteLongsword.BEAM_RADIUS_PER_LEVEL);
    }

    /** 领域出剑提速倍率（越大越快）。 */
    public static float fieldRateScale(Player player) {
        return 1.0f + level(player) * perLevel(DomeriteLongsword.FIELD_RATE_PER_LEVEL);
    }
}
