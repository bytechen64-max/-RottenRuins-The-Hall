package org.bytechen.rottenruins.client.rend.text.effect;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 效果动画的<b>时间源</b>（秒）。
 *
 * <h3>为什么默认墙钟而不是游戏时间</h3>
 * <p>工具提示/界面在单人游戏里打开时世界是暂停的：用 {@code gameTime} 会让"悬停时才会显示"
 * 的 tooltip 渐变完全冻住（项目里 {@code FlowingNameColors} 就是这种口径，见它的时间源注释）。
 * 文本特效是"界面装饰"，暂停时继续流动观感更好。想暂停即停可以在配置里切到游戏时间。</p>
 *
 * <h3>为什么要取模</h3>
 * <p>{@code float} 在数值很大时丢精度（1000 秒以后相位会一顿一顿）。项目里创造栏色带
 * 用"毫秒对 1 小时取模"的办法，这里照抄：每小时回绕一次，没人会盯着看。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TextEffectClock {

    /** 回绕周期（毫秒，1 小时）。 */
    private static final long WRAP_MS = 3_600_000L;

    private TextEffectClock() {}

    /** 当前时间（秒），每帧可多次调用（纯读，无状态）。 */
    public static float seconds() {
        if (TextEffectSettings.useGameTime()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                return (mc.level.getGameTime() + mc.getFrameTime()) / 20F;
            }
        }
        long millis = (System.currentTimeMillis() % WRAP_MS + WRAP_MS) % WRAP_MS;
        return millis / 1000F;
    }
}
