package org.bytechen.rottenruins.overworld.registry.effect.customEffect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>自实现效果的基类</b> —— 形状刻意照着原版 {@link MobEffect} 做，用法也一样。
 *
 * <h3>和原版 {@link MobEffect} 的区别</h3>
 * <table border="1">
 *   <tr><th></th><th>原版 MobEffect</th><th>本类</th></tr>
 *   <tr><td>注册</td><td>必须进 {@code ForgeRegistries.MOB_EFFECTS}</td>
 *       <td>{@link CustomEffectRegistry#register} 即可，不占 Forge 注册表</td></tr>
 *   <tr><td>实例</td><td>{@code new MobEffectInstance(effect, duration, amplifier)}</td>
 *       <td>{@link CustomEffectInstance}，参数与语义一致</td></tr>
 *   <tr><td>施加</td><td>{@code entity.addEffect(instance)}</td>
 *       <td>{@code CustomEffects.addEffect(entity, instance)}</td></tr>
 *   <tr><td>数据</td><td>原版 {@code activeEffects} 字段</td>
 *       <td>本模组自己的 capability（{@link CustomEffectHolder}），随实体 NBT 持久化</td></tr>
 *   <tr><td>HUD</td><td>右上角自动显示</td>
 *       <td><b>不</b>进原版列表（那栏只画 {@link MobEffect}），需要自己画</td></tr>
 * </table>
 *
 * <h3>子类要覆写哪些</h3>
 * <ul>
 *   <li>{@link #isDurationEffectTick} + {@link #applyEffectTick}：周期效果（原版同名方法，语义一致）；</li>
 *   <li>{@link #onEntityTick}：需要每 tick 都做的事；</li>
 *   <li>{@link #onAdded} / {@link #onRemoved}：挂上 / 掉的时候各一次；</li>
 *   <li>{@link #applyAttributeModifiers} / {@link #removeAttributeModifiers}：属性型效果。</li>
 * </ul>
 *
 * <p>所有回调都<b>只在服务端</b>触发（客户端只有倒计时与显示数据），
 * 所以里面可以放心改世界。</p>
 */
public abstract class CustomEffectBase {

    /** 默认持续时间（tick）：10 秒。 */
    public static final int DEFAULT_DURATION = 200;

    /** 层数上限（amplifier 上限）：与原版一样 4 级封顶。 */
    public static final int MAX_AMPLIFIER = 4;

    private final ResourceLocation id;
    private final CustomEffectCategory category;
    private final int color;

    protected CustomEffectBase(@NotNull ResourceLocation id,
                               @NotNull CustomEffectCategory category,
                               int color) {
        this.id = id;
        this.category = category;
        this.color = color;
    }

    // ══════════════════════════════════════════════════════════════
    // 基本信息
    // ══════════════════════════════════════════════════════════════

    /** 唯一 id（注册表键，也是 NBT 里存的名字）。 */
    @NotNull
    public final ResourceLocation getId() {
        return id;
    }

    @NotNull
    public final CustomEffectCategory getCategory() {
        return category;
    }

    /** 粒子 / HUD 主色（0xRRGGBB）。 */
    public final int getColor() {
        return color;
    }

    public final boolean isBeneficial() {
        return category.isBeneficial();
    }

    public final boolean isHarmful() {
        return category.isHarmful();
    }

    /** 不指定持续时间时用多久。 */
    public int getDefaultDuration() {
        return DEFAULT_DURATION;
    }

    /**
     * 能不能挂到这个实体上（对应原版"能不能被施加"那条判断）。默认都能。
     *
     * <p>典型用法：只对亡灵生效、只对玩家生效、boss 免疫……</p>
     */
    public boolean canApply(@NotNull LivingEntity entity) {
        return true;
    }

    /**
     * 这个效果是否要同步给客户端。默认 {@code true}。
     *
     * <p>纯服务端逻辑（只做计数 / 只改伤害）返回 {@code false} 可以省同步开销。
     * 注意服务端自己始终拿得到数据，这个开关只影响"发不发包"。</p>
     */
    public boolean isVisible() {
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // 计时与周期回调（全部服务端）
    // ══════════════════════════════════════════════════════════════

    /**
     * 本次 tick 要不要触发 {@link #applyEffectTick}。
     *
     * <p>与原版同名方法语义一致：频率由子类决定，框架只在返回 true 时回调。
     * "每 20 tick 一次"就写 {@code return duration % 20 == 0;}。</p>
     *
     * @param duration  剩余持续时间（tick）
     * @param amplifier 层数（0 起）
     */
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }

    /** 周期效果本体；只在 {@link #isDurationEffectTick} 返回 true 的那一 tick 调用。 */
    public void applyEffectTick(@NotNull ServerLevel level, @NotNull LivingEntity entity,
                                @NotNull CustomEffectInstance instance) {
    }

    /** 每 tick 回调（在倒计时之前调用）。 */
    public void onEntityTick(@NotNull ServerLevel level, @NotNull LivingEntity entity,
                             @NotNull CustomEffectInstance instance) {
    }

    /** 效果刚挂上；被更长时长 / 更高层数刷新时也会调。 */
    public void onAdded(@NotNull LivingEntity entity, @NotNull CustomEffectInstance instance) {
    }

    /** 效果结束（到期、被移除、实体死亡都走这里）。 */
    public void onRemoved(@NotNull LivingEntity entity, @NotNull CustomEffectInstance instance) {
    }

    // ══════════════════════════════════════════════════════════════
    // 属性
    // ══════════════════════════════════════════════════════════════

    /**
     * 挂上属性修饰器。默认什么都不做。
     *
     * <p>摘除时用的是<b>同一个 UUID</b>，所以修饰器建议做成 {@code static final}；
     * 每次 new 一个新 UUID 会导致摘不掉、属性永久残留。</p>
     */
    public void applyAttributeModifiers(@NotNull LivingEntity entity, int amplifier) {
    }

    /** 摘掉 {@link #applyAttributeModifiers} 挂上的属性修饰器。 */
    public void removeAttributeModifiers(@NotNull LivingEntity entity, int amplifier) {
    }

    /** 往实体身上加属性修饰器的小工具。 */
    protected static boolean addModifier(@NotNull LivingEntity entity, @NotNull Attribute attribute,
                                         @NotNull AttributeModifier modifier) {
        var instance = entity.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        if (instance.getModifier(modifier.getId()) != null) {
            instance.removeModifier(modifier.getId());
        }
        instance.addTransientModifier(modifier);
        return true;
    }

    /** 摘掉一个属性修饰器的小工具。 */
    protected static void removeModifier(@NotNull LivingEntity entity, @NotNull Attribute attribute,
                                         @Nullable AttributeModifier modifier) {
        if (modifier == null) {
            return;
        }
        var instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifier.getId());
        }
    }

    /** 层数换算成倍率的常用写法：{@code 1 + amplifier × perLevel}。 */
    public static double scaleOf(int amplifier, double perLevel) {
        return 1.0d + Math.max(0, amplifier) * perLevel;
    }

    /** 分类与原版 {@link MobEffectCategory} 的对应关系（以后真要做 HUD 投影时用）。 */
    @NotNull
    public final MobEffectCategory toVanillaCategory() {
        return switch (category) {
            case BENEFICIAL -> MobEffectCategory.BENEFICIAL;
            case HARMFUL -> MobEffectCategory.HARMFUL;
            case NEUTRAL -> MobEffectCategory.NEUTRAL;
        };
    }

    @Override
    public String toString() {
        return "CustomEffect[" + id + "]";
    }
}
