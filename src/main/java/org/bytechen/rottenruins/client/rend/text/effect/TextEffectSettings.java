package org.bytechen.rottenruins.client.rend.text.effect;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.config.data.SplendidingConfig;

/**
 * 文本特效的<b>配置读取门面</b>（仿项目里的 {@code VerdictTuning}：配置拿不到/抛异常/值非法
 * 一律回默认值，绝不让渲染路径因为配置炸掉）。
 *
 * <h3>为什么要一个"运行时开关"</h3>
 * <p>{@code /rrsfx off} 需要在<b>不重启</b>的情况下对照测试"有特效 / 没特效"；
 * 而 cloth-config 的字段要存盘才生效。所以这里在配置值之上再加一层内存覆盖
 * （{@link #setRuntimeEnabled(boolean)}），重启后回到配置文件的值。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TextEffectSettings {

    /** 内存里的运行时覆盖；null = 跟随配置。 */
    private static volatile Boolean runtimeOverride = null;

    private TextEffectSettings() {}

    private static SplendidingConfig cfg() {
        try {
            if (ConfigHelper.configHolder == null) return null;
            return ConfigHelper.configHolder.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 总开关（运行时覆盖优先）。 */
    public static boolean enabled() {
        Boolean override = runtimeOverride;
        if (override != null) return override;
        SplendidingConfig config = cfg();
        return config == null || config.textEffectsEnabled;
    }

    /** 供命令/演示屏显示当前状态。 */
    public static boolean runtimeEnabled() {
        return enabled();
    }

    /** `/rrsfx on|off`：只改内存，不写配置。 */
    public static void setRuntimeEnabled(boolean value) {
        runtimeOverride = value;
    }

    /**
     * 三个 mixin 的总旁路（排障用；false = 完全等价于没装本功能）。
     *
     * <p>注意它<b>只看</b> {@link #fontHooksEnabled}：`textEffectsEnabled=false` 时 mixin 仍然要接管
     * —— 否则标记不会被剥离，原版会把 {@code §[} 之后的内容当正文画出来（露出 {@code rrs:grad:…}）。
     * "是否上色/位移"由 {@link #enabled()} 单独控制（解析层会在关闭时只做零宽剥离）。</p>
     */
    public static boolean hooksEnabled() {
        SplendidingConfig config = cfg();
        return config == null || config.textEffectsFontHooksEnabled;
    }

    public static float intensity() {
        SplendidingConfig config = cfg();
        return clamp(config == null ? 1.0F : config.textEffectIntensity, 0F, 2F);
    }

    public static float speed() {
        SplendidingConfig config = cfg();
        return clamp(config == null ? 1.0F : config.textEffectSpeed, 0.05F, 8F);
    }

    public static boolean useGameTime() {
        SplendidingConfig config = cfg();
        return config != null && config.textEffectUseGameTime;
    }

    public static int maxAnimatedGlyphs() {
        SplendidingConfig config = cfg();
        return config == null ? 512 : Math.max(0, config.textEffectMaxAnimatedGlyphs);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
