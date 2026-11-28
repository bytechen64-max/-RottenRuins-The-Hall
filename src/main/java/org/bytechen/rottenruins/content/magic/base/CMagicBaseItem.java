package org.bytechen.rottenruins.content.magic.base;

import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.MagicTypes;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.bytechen.rottenruins.core.magic.model.SpellKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import org.bytechen.rottenruins.api.ITooltipStyle;
import org.bytechen.rottenruins.api.TooltipShaderSpec;
import org.bytechen.rottenruins.gameplay.magic.spell.IMagicBaseItem;
import org.bytechen.rottenruins.gameplay.magic.spell.MagicCastContext;

/**
 * 魔法物品（法术）基类 —— 数据声明 + 默认行为，<b>不含</b>冷却与法力的存储。
 *
 * <h3>相对早期版本修正了什么</h3>
 * <ol>
 *   <li><b>不再把冷却存在物品上。</b> 原版把 {@code cooldown / coolcount / usingtime}
 *       做成 {@code public int} 字段挂在 {@code Item} 实例上。物品是<b>注册表单例</b>，
 *       于是"所有人的同一件物品共享一个冷却"—— 甲放完法术，乙也不能放了。
 *       现在冷却按「玩家 + 物品注册名」存在 {@code SpellInventory} 里。</li>
 *   <li><b>三种释放模式有了明确语义</b>，并且时间轴由 {@code MagicHandle} 统一驱动，
 *       而不是让每个法术自己数 tick。</li>
 *   <li><b>基值与最终值分离。</b> 这里的 getter 返回的是"未修饰的原始值"；
 *       真正生效的数值一律走 {@code org.bytechen.rottenruins.gameplay.magic.stats.MagicStats}，
 *       属性修饰器与附魔在那里折叠。</li>
 *   <li><b>{@code onPlayHandleTick} 不再是 abstract。</b> 原版把接口方法声明成 abstract、
 *       却在注释里写"默认什么都不做"，任何子类都被强制实现一个空方法。</li>
 * </ol>
 *
 * <h3>写一个法术</h3>
 * <p>继承它、调一个构造器、实现 {@link #cast} 就够了：</p>
 * <pre>{@code
 * public class FrostBoltSpell extends CMagicBaseItem {
 *     public FrostBoltSpell() {
 *         super(defaultProperties(), SpellDefinition.builder(UsingType.CHARGE, MagicType.WATER)
 *                 .manaCost(120).cooldownTicks(40).chargeTicks(20).damage(12.0f)
 *                 .build());
 *     }
 *     @Override
 *     public boolean cast(ServerPlayer caster, ItemStack stack, MagicCastContext ctx) { ... }
 * }
 * }</pre>
 *
 * <p>注意 {@code cast} 必须是 {@code public} —— 它是接口方法，不能收窄可见性。</p>
 *
 * <p>如果基值需要动态计算（例如读 NBT、读时间），把这些 getter 覆写掉即可 ——
 * 它们不是 final。但<b>不要</b>在里面对玩家做副作用的操作，它们会被 HUD 频繁调用。</p>
 */
public abstract class CMagicBaseItem extends Item implements IMagicBaseItem, ITooltipStyle {

    private final SpellDefinition definition;

    /** Item settings and version-independent base data are supplied separately. */
    protected CMagicBaseItem(@NotNull Properties properties, @NotNull SpellDefinition definition) {
        super(properties);
        this.definition = java.util.Objects.requireNonNull(definition, "definition");
    }

    /** Static declaration only; use MagicStats for values resolved with player modifiers. */
    public final SpellDefinition definition() {
        return definition;
    }

    /** 法术物品的推荐属性：不可堆叠（一个槽位只放一个）。 */
    @NotNull
    public static Properties defaultProperties() {
        return new Properties().stacksTo(1);
    }

    // ══════════════════════════════════════════════════════════════
    // 基值（可覆写，但请保持"无副作用"）
    // ══════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public MagicType getMagicType() {
        return definition.magicTypes().primary();
    }

    @Override
    @NotNull
    public MagicTypes getMagicTypes() {
        return definition.magicTypes();
    }

    @Override
    @NotNull
    public UsingType getUsingType() {
        return definition.usingType();
    }

    @Override
    @NotNull
    public SpellKind spellKind() {
        return definition.kind();
    }

    @Override
    public int baseManaCost() {
        return definition.manaCost();
    }

    @Override
    public int baseKeepCostPerSecond() {
        return definition.keepCostPerSecond();
    }

    @Override
    public int baseCooldownTicks() {
        return definition.cooldownTicks();
    }

    @Override
    public int baseChargeTicks() {
        return definition.chargeTicks();
    }

    @Override
    public float baseDamage() {
        return definition.damage();
    }

    @Override
    public float baseMinChargeRatio() {
        return definition.minChargeRatio();
    }

    @Override
    public int baseKeepTriggerInterval() {
        return definition.keepTriggerInterval();
    }

    // ══════════════════════════════════════════════════════════════
    // 便利判定
    // ══════════════════════════════════════════════════════════════

    /** 按下即放。 */
    public final boolean isInstantMode() {
        return definition.usingType() == UsingType.IMMEDIATELY;
    }

    /** 需要蓄力到 {@link #baseChargeTicks()} 才放。 */
    public final boolean isChargeMode() {
        return definition.usingType() == UsingType.CHARGE;
    }

    /** 按住持续维持。 */
    public final boolean isKeepMode() {
        return definition.usingType() == UsingType.KEEP;
    }

    /**
     * 法术的注册名，用作冷却的键（同名物品共享冷却）。
     *
     * <p>未注册（{@code minecraft:air}）时返回 {@code null}，调用方需要自己处理
     * —— 理论上不会发生，法术物品一定注册过。</p>
     */
    @Nullable
    public ResourceLocation getMagicId() {
        return BuiltInRegistries.ITEM.getKey(this);
    }

    /** 从堆里取法术接口；不是法术返回 {@code null}。 */
    @Nullable
    public static IMagicBaseItem of(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IMagicBaseItem item
                ? item
                : null;
    }

    // ══════════════════════════════════════════════════════════════
    // 介绍（tooltip）
    // ══════════════════════════════════════════════════════════════

    /**
     * 法术的常显介绍：<b>法力消耗 → 法术类型 → 冷却 → 持续消耗（{@code KEEP}）→ 蓄力时间（{@code CHARGE}）</b>。
     *
     * <p>简介与详细说明不在这里 —— 它们按需求折叠进 Shift 细节，由客户端
     * {@code SpellTooltipSections} 追加（这里读不到 Shift 键，{@code content} 层也不能碰客户端类）。</p>
     *
     * <p>显示的是<b>基值</b>（{@link #baseManaCost()} 等），不是带上玩家属性修饰器之后的实际值
     * —— 介绍是在没有玩家的上下文里画的。</p>
     */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(MagicTooltips.manaLine(this.baseManaCost()));
        tooltip.add(MagicTooltips.typeLine(getMagicTypes()));
        tooltip.add(MagicTooltips.cooldownLine(this.baseCooldownTicks()));
        // KEEP 才有维持耗蓝、CHARGE 才有蓄力时间，其它模式显示出来只是噪音
        if (definition.usingType() == UsingType.KEEP && this.baseKeepCostPerSecond() > 0) {
            tooltip.add(MagicTooltips.keepCostLine(this.baseKeepCostPerSecond()));
        }
        if (definition.usingType() == UsingType.CHARGE && this.baseChargeTicks() > 0) {
            tooltip.add(MagicTooltips.chargeLine(this.baseChargeTicks()));
        }
        appendSpellHoverText(stack, level, tooltip);
    }

    /** 子类追加自己的介绍（例如"命中额外造成 3 点真伤"）。 */
    protected void appendSpellHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                        @NotNull List<Component> tooltip) {
    }

    /**
     * 默认的 {@link #cast} 实现：什么都不做，返回 {@code false}。
     *
     * <p>之所以给出默认实现而不是做成 abstract：{@code KEEP} 型法术可能只靠
     * {@link #onKeepTick} 干活，{@code cast} 本身确实是空的。强迫它写一个空方法没有意义。</p>
     */
    @Override
    public boolean cast(@NotNull net.minecraft.server.level.ServerPlayer caster,
                        @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        return false;
    }

    // ── tooltip 底板 ─────────────────────────────────────────────

    /** 法术物品都有自己的底板配色，所以启用；配色按流派现算。 */
    @Override
    public boolean tooltipStyleEnabled() {
        return true;
    }

    @Override
    public int tooltipBackgroundTop() {
        return 0xF0000000 | MagicTooltips.dim(primaryRgb(), 0.16F);
    }

    @Override
    public int tooltipBackgroundBottom() {
        return 0xF0000000 | MagicTooltips.dim(primaryRgb(), 0.10F);
    }

    @Override
    public int tooltipBorderTop() {
        return 0x50000000 | MagicTooltips.lighten(primaryRgb(), 0.20F);
    }

    @Override
    public int tooltipBorderBottom() {
        return 0x30000000 | MagicTooltips.dim(primaryRgb(), 0.85F);
    }

    /**
     * 细胞网络底板：底板压到很暗的流派色，连线是中等亮度的流派色，脉动点才用亮色。
     *
     * <p>亮度这样分配是为了文字对比度 —— 连线铺满整个面板，如果直接用亮色，紫/金字体
     * 会被盖过去；亮点只占几像素，亮一点反而好看。</p>
     */
    @Override
    public TooltipShaderSpec tooltipShader() {
        int rgb = primaryRgb();
        return new TooltipShaderSpec(TooltipShaderSpec.NETWORK, 0.75F,
                0xF0000000 | MagicTooltips.dim(rgb, 0.10F),
                MagicTooltips.dim(MagicTooltips.lighten(rgb, 0.25F), 0.62F),
                MagicTooltips.lighten(rgb, 0.62F));
    }

    /** 展示用的主流派色；{@code MagicTypes} 保证至少一个流派。 */
    private int primaryRgb() {
        return MagicTooltips.accentRgb(getMagicTypes().primary());
    }
}
