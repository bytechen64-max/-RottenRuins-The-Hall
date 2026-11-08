package org.bytechen.rottenruins.overworld.registry.effect.customEffect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 自定义效果的<b>实例</b> —— 对应原版 {@code MobEffectInstance}。
 *
 * <p>同一个 {@link CustomEffectBase} 可以挂出无数个实例（不同剩余时长 / 不同层数），
 * 但<b>同一个实体身上同一种效果只有一个实例</b>：再次施加会走
 * {@link #upgrade} 合并，规则与原版一致。</p>
 *
 * <p>字段命名刻意对齐原版：{@code duration}（剩余 tick，{@code -1} = 永久）、
 * {@code amplifier}（层数，0 起）、{@code ambient}（环境效果，边框更淡）、
 * {@code visible}（是否画粒子 / HUD）。</p>
 */
public class CustomEffectInstance {

    /** 永久效果的 duration 值（与原版一致）。 */
    public static final int INFINITE_DURATION = -1;

    private final CustomEffectBase effect;
    private int duration;
    private int amplifier;
    private boolean ambient;
    private boolean visible;
    private boolean noCounter;

    public CustomEffectInstance(@NotNull CustomEffectBase effect) {
        this(effect, effect.getDefaultDuration(), 0);
    }

    public CustomEffectInstance(@NotNull CustomEffectBase effect, int duration) {
        this(effect, duration, 0);
    }

    public CustomEffectInstance(@NotNull CustomEffectBase effect, int duration, int amplifier) {
        this(effect, duration, amplifier, false, true);
    }

    public CustomEffectInstance(@NotNull CustomEffectBase effect, int duration, int amplifier,
                                boolean ambient, boolean visible) {
        this.effect = effect;
        this.duration = duration;
        this.amplifier = Mth.clamp(amplifier, 0, CustomEffectBase.MAX_AMPLIFIER);
        this.ambient = ambient;
        this.visible = visible;
    }

    // ── 访问器 ──

    @NotNull
    public CustomEffectBase getEffect() {
        return effect;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getAmplifier() {
        return amplifier;
    }

    public void setAmplifier(int amplifier) {
        this.amplifier = Mth.clamp(amplifier, 0, CustomEffectBase.MAX_AMPLIFIER);
    }

    public boolean isAmbient() {
        return ambient;
    }

    public void setAmbient(boolean ambient) {
        this.ambient = ambient;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** 是否隐藏"HUD 上的倒计时数字"（原版 {@code showIcon}/{@code noCounter} 的对应物）。 */
    public boolean isNoCounter() {
        return noCounter;
    }

    public void setNoCounter(boolean noCounter) {
        this.noCounter = noCounter;
    }

    /** 永久效果。 */
    public boolean isInfinite() {
        return duration == INFINITE_DURATION;
    }

    /** 这个实例是否已经结束。 */
    public boolean expired() {
        return !isInfinite() && duration <= 0;
    }

    /**
     * 倒计时一 tick。<b>只有服务端权威</b>；客户端为了显示流畅也会单独倒计时，
     * 但客户端的数字到 0 只会被移除，不会触发任何逻辑。
     *
     * @return 倒计时之后是否仍然有效
     */
    public boolean tickDown() {
        if (isInfinite()) {
            return true;
        }
        if (duration > 0) {
            duration--;
        }
        return duration > 0;
    }

    /**
     * 同种效果再次施加时的合并规则（与原版 {@code MobEffectInstance#update} 一致）：
     *
     * <ul>
     *   <li>新时长 &gt; 旧时长、且层数 ≥ 旧层数 → <b>整体替换</b>（新的一次生效）；</li>
     *   <li>否则只取<b>较长的时长</b>与<b>较高的层数</b>，旧实例保留，
     *       但两种参数都只在"不变差"的方向上更新。</li>
     * </ul>
     *
     * @param other 新施加的那个实例
     * @return {@code true} = 发生了实质变化（调用方应该重新应用属性修饰器 + 同步）
     */
    public boolean upgrade(@Nullable CustomEffectInstance other) {
        if (other == null || other.getEffect() != effect) {
            return false;
        }
        boolean changed = false;

        if (other.amplifier > this.amplifier) {
            this.amplifier = other.amplifier;
            changed = true;
        }
        if (other.duration > this.duration || other.isInfinite()) {
            this.duration = other.duration;
            changed = true;
        }
        if (other.ambient != this.ambient) {
            this.ambient = other.ambient;
            changed = true;
        }
        return changed;
    }

    // ══════════════════════════════════════════════════════════════
    // NBT
    // ══════════════════════════════════════════════════════════════

    /** 写进实体 NBT（只存 id 与参数，效果本体靠注册表反查）。 */
    @NotNull
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", effect.getId().toString());
        tag.putInt("Duration", duration);
        tag.putInt("Amplifier", amplifier);
        if (ambient) {
            tag.putBoolean("Ambient", true);
        }
        if (!visible) {
            tag.putBoolean("Visible", false);
        }
        if (noCounter) {
            tag.putBoolean("NoCounter", true);
        }
        return tag;
    }

    /**
     * 从 NBT 还原；effect id 已经不在注册表里（存档来自旧版本 / 效果被删）时返回 {@code null}。
     */
    @Nullable
    public static CustomEffectInstance load(@NotNull CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Id"));
        CustomEffectBase effect = CustomEffectRegistry.get(id);
        if (effect == null) {
            return null;
        }
        int duration = tag.contains("Duration") ? tag.getInt("Duration") : effect.getDefaultDuration();
        int amplifier = tag.getInt("Amplifier");
        CustomEffectInstance instance = new CustomEffectInstance(effect, duration, amplifier,
                tag.getBoolean("Ambient"), !tag.contains("Visible") || tag.getBoolean("Visible"));
        instance.setNoCounter(tag.getBoolean("NoCounter"));
        return instance;
    }

    @Override
    public String toString() {
        return effect.getId() + "[duration=" + duration + ", amplifier=" + amplifier + "]";
    }
}
