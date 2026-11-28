package org.bytechen.rottenruins.overworld.registry.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.overworld.registry.items.DomeriteLongsword;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictTuning;

/** 裁决 —— 天穹裁决命中后自叠的增益：层数只喂光柱半径与领域出剑速率，不加伤害（伤害已在 VerdictDamage 定死）。 */
public class VerdictEffect extends MobEffect {

    /** 用 MobEffect 而非自定义 Capability：自带等级与自然过期、原版自动同步、免死亡/换维度搬运。 */
    public static final VerdictEffect INSTANCE = new VerdictEffect();

    /** HUD 粒子颜色：天蓝，与长剑描边主色一致。 */
    private static final int COLOR = 0x87CEFA;

    private VerdictEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    public static int level(Player player) {
        var inst = player.getEffect(INSTANCE);
        return inst == null ? 0 : inst.getAmplifier() + 1;
    }

    /** 每层增益 = 基础值 × verdictStackScale；配置为 0 即关掉层数加成（层数与 HUD 仍在）。 */
    private static float perLevel(float base) {
        return base * VerdictTuning.stackScale();
    }

    public static float beamRadiusScale(Player player) {
        return 1.0f + level(player) * perLevel(DomeriteLongsword.BEAM_RADIUS_PER_LEVEL);
    }

    public static float fieldRateScale(Player player) {
        return 1.0f + level(player) * perLevel(DomeriteLongsword.FIELD_RATE_PER_LEVEL);
    }
}
