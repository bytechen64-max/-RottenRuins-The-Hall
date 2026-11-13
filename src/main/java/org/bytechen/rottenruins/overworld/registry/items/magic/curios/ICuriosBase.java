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
 * 魔法饰品基类 —— 戴在 Curios 饰品栏里、带流派的被动装备（法术是放出去的东西，饰品是持续生效的）。
 * <p>流派是「特殊使用」的判定依据：法术流派 == 手上法杖流派，或 == 已装备饰品流派（本类提供的第二个
 * 来源）都算特殊使用；法术只需在 {@code cast} 里读 {@code ctx.specialUse()}，不用自己查饰品栏。
 * <p>槽位由物品标签决定（{@code charm} / {@code ring} / {@code necklace} / {@code belt} / {@code curio} …）：
 * 只实现 {@link ICurioItem} 而不加标签，饰品栏不会接受它；{@link #canEquip} 默认放行，可在代码层限制。
 */
public abstract class ICuriosBase extends Item implements ICurioItem {

    private final MagicType magicType;

    /** 只有"不想自定义属性"的子类才用这个：默认单件不可堆叠。 */
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

    /** 戴着时每 tick 调用；两端都会走，做服务端副作用前自己判 {@code isClientSide()}。 */
    @Override
    public void curioTick(@NotNull SlotContext context, @NotNull ItemStack stack) {
        onWornTick(context, stack);
    }

    /** 子类用的「戴着时每 tick」钩子，默认什么都不做。 */
    protected void onWornTick(@NotNull SlotContext context, @NotNull ItemStack stack) {
    }

    @Override
    public void onEquip(@NotNull SlotContext context, @NotNull ItemStack newStack,
                        @NotNull ItemStack previousStack) {
        onWorn(context, newStack);
    }

    @Override
    public void onUnequip(@NotNull SlotContext context, @NotNull ItemStack newStack,
                          @NotNull ItemStack previousStack) {
        onRemoved(context, newStack);
    }

    /** 子类用的「刚戴上」钩子。 */
    protected void onWorn(@NotNull SlotContext context, @NotNull ItemStack stack) {
    }

    /** 子类用的「被摘下」钩子：常驻效果（光环、记在别处的状态）必须在这里收尾，否则会残留。 */
    protected void onRemoved(@NotNull SlotContext context, @NotNull ItemStack stack) {
    }

    /** 能不能装进这个槽，默认放行 —— 真正的槽位限制交给物品标签。 */
    @Override
    public boolean canEquip(@NotNull SlotContext context, @NotNull ItemStack stack) {
        return true;
    }

    /** 介绍：先一行「类型：X」（颜色用与法术同一套 {@link MagicTooltips}），再交给子类追加说明。 */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(MagicTooltips.typeLine(this.magicType));
        appendCurioHoverText(stack, level, tooltip);
    }

    /** 子类追加自己的介绍（例如「法术强度 +10%」）。 */
    protected void appendCurioHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                        @NotNull List<Component> tooltip) {
    }

    /** 玩家身上是否装备着该流派的饰品；只认 {@link ICuriosBase} 子类，别的模组的饰品不参与判定。 */
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

    /** 同上，但要「具体哪一件」时用；没有匹配时返回 {@link ItemStack#EMPTY}。 */
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
