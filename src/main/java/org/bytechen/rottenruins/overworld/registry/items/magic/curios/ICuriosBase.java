package org.bytechen.rottenruins.overworld.registry.items.magic.curios;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicTooltips;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * <b>魔法饰品基类</b> —— 戴在 Curios 饰品栏里、带流派的被动装备。
 *
 * <p>它和法术的区别：法术是<b>放出去的东西</b>（在魔法槽里、由法杖触发），
 * 饰品是<b>戴在身上的东西</b>（在饰品栏里、持续生效）。两者共用的只有
 * "{@link MagicType 流派}"这一个维度，而流派正是"特殊使用"的判定依据：</p>
 *
 * <pre>
 *   法术流派 == 手上法杖流派      → 特殊使用
 *   法术流派 == 已装备的饰品流派  → 也算特殊使用（本类提供的第二个来源）
 * </pre>
 *
 * <p>判定入口是 {@code MagicStats.isSpecialUse(player, 法杖堆, 法术堆)}，
 * 法术在 {@code cast} 里读 {@code ctx.specialUse()} 就行，不用自己查饰品栏。</p>
 *
 * <h3>写一件具体的饰品</h3>
 * <pre>{@code
 * public class FireCharm extends ICuriosBase {
 *     public FireCharm() {
 *         super(MagicType.FIRE);           // 或 super(new Properties().stacksTo(1), MagicType.FIRE)
 *     }
 *
 *     @Override
 *     protected void onWornTick(SlotContext ctx, ItemStack stack) {
 *         // 每 tick 生效的效果；纯被动加属性的话这个方法可以不写
 *     }
 * }
 * }</pre>
 *
 * <h3>还要做的一步：让它能进饰品栏</h3>
 * <p>Curios 靠<b>物品标签</b>决定一件饰品能放进哪个槽（{@code charm} / {@code ring} /
 * {@code necklace} / {@code belt} / {@code curio} …）。所以具体饰品类写完之后，
 * 还要把它加进对应的物品标签，例如 {@code data/curios/tags/items/charm.json}：</p>
 * <pre>{@code
 * { "replace": false, "values": [ "rotten_ruins:fire_charm" ] }
 * }</pre>
 * <p>只实现 {@link ICurioItem} 而不打标签，饰品栏不会接受它。
 * {@link #canEquip} 默认放行（不限槽位），想在代码层面进一步限制就覆写它 ——
 * 槽位名从 {@code context.identifier()} 拿。</p>
 */
public abstract class ICuriosBase extends Item implements ICurioItem {

    private final MagicType magicType;

    /** 只给"不想自定义属性"的子类用：默认单件不可堆叠。 */
    public ICuriosBase(MagicType type) {
        this(new Properties().stacksTo(1), type);
    }

    public ICuriosBase(Properties properties, MagicType type) {
        super(properties);
        this.magicType = type;
    }

    /** 这件饰品的流派。 */
    @NotNull
    public MagicType getMagicType() {
        return this.magicType;
    }

    // ══════════════════════════════════════════════════════════════
    // Curios 生命周期 → 给子类的钩子
    // ══════════════════════════════════════════════════════════════

    /**
     * 戴着的时候每 tick 调用一次。
     *
     * <p>两端都会调（Curios 在客户端也会走一遍），做服务端副作用前请自己判
     * {@code context.entity().level().isClientSide()}。</p>
     */
    @Override
    public void curioTick(@NotNull SlotContext context, @NotNull ItemStack stack) {
        onWornTick(context, stack);
    }

    /** 子类用的"戴着的时候每 tick"钩子。默认什么都不做。 */
    protected void onWornTick(@NotNull SlotContext context, @NotNull ItemStack stack) {
    }

    /** 刚戴上。 */
    @Override
    public void onEquip(@NotNull SlotContext context, @NotNull ItemStack newStack,
                        @NotNull ItemStack previousStack) {
        onWorn(context, newStack);
    }

    /** 摘下来。 */
    @Override
    public void onUnequip(@NotNull SlotContext context, @NotNull ItemStack newStack,
                          @NotNull ItemStack previousStack) {
        onRemoved(context, newStack);
    }

    /** 子类用的"刚戴上"钩子。 */
    protected void onWorn(@NotNull SlotContext context, @NotNull ItemStack stack) {
    }

    /**
     * 子类用的"被摘下"钩子。
     * <p>常驻效果（比如自己挂的光环、记在别处的状态）必须在这里收尾，
     * 否则摘下之后会残留。</p>
     */
    protected void onRemoved(@NotNull SlotContext context, @NotNull ItemStack stack) {
    }

    /**
     * 能不能装进这个槽。默认放行 —— 真正的槽位限制交给物品标签。
     *
     * <p>覆写示例：只允许 {@code charm} 槽 →
     * {@code return "charm".equals(context.identifier());}</p>
     */
    @Override
    public boolean canEquip(@NotNull SlotContext context, @NotNull ItemStack stack) {
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // 介绍
    // ══════════════════════════════════════════════════════════════

    /**
     * 饰品的介绍：先一行"类型：X"（数值部分是该流派的颜色，
     * 和法术用的是同一套 {@link MagicTooltips}，所以同一流派在两个物品上颜色一致），
     * 再交给子类追加自己的说明。
     */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(MagicTooltips.typeLine(this.magicType));
        appendCurioHoverText(stack, level, tooltip);
    }

    /** 子类追加自己的介绍（例如"法术强度 +10%"）。 */
    protected void appendCurioHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                        @NotNull List<Component> tooltip) {
    }

    // ══════════════════════════════════════════════════════════════
    // 查询：玩家身上有没有该流派的饰品
    // ══════════════════════════════════════════════════════════════

    /**
     * 玩家身上是否装备着<b>该流派</b>的魔法饰品。
     *
     * <p>只认 {@link ICuriosBase} 的子类 —— 别的模组的饰品不该影响本模组的特殊使用判定。</p>
     *
     * @param player 玩家，可为 {@code null}（返回 {@code false}）
     */
    public static boolean isSchoolEquipped(@Nullable Player player, @NotNull MagicType type) {
        if (player == null) {
            return false;
        }
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(handler -> !handler.findCurios(
                        stack -> stack.getItem() instanceof ICuriosBase curio
                                && curio.getMagicType() == type).isEmpty())
                .orElse(false);
    }

    /**
     * 同上，但要"具体哪一件"时用（例如特效要区分是哪件饰品触发的）。
     *
     * @return 第一件匹配的饰品堆；没有时返回 {@link ItemStack#EMPTY}
     */
    @NotNull
    public static ItemStack findSchoolCurio(@Nullable LivingEntity entity, @NotNull MagicType type) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        return CuriosApi.getCuriosInventory(entity)
                .resolve()
                .map(handler -> handler.findCurios(
                                stack -> stack.getItem() instanceof ICuriosBase curio
                                        && curio.getMagicType() == type)
                        .stream()
                        .map(SlotResult::stack)
                        .findFirst()
                        .orElse(ItemStack.EMPTY))
                .orElse(ItemStack.EMPTY);
    }
}
