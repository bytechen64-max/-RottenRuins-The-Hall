package org.bytechen.rottenruins.content.magic;


import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.gameplay.magic.spell.IMagicBaseItem;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.content.magic.base.StaffBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 法杖的流派、储存魔能和提示。施法时序与扣费仍由 MagicHandle 负责。 */
public class WandItem extends StaffBase implements org.bytechen.rottenruins.gameplay.magic.spell.ManaReserve {

    private static final String MANA_TAG = "WandMana";
    public static final int NORMAL_CAPACITY = 500;
    public static final int COMMON_CAPACITY = NORMAL_CAPACITY * 5;
    public static final int PLATE_RECHARGE = NORMAL_CAPACITY;
    private final int capacity;

    /**
     * @param properties 物品属性；法杖建议 {@code stacksTo(1)}
     * @param magicType  这根杖的流派
     */
    public WandItem(@NotNull Item.Properties properties, @NotNull MagicType magicType, int capacity) {
        super(properties, magicType);
        this.capacity = capacity;
    }

    public int capacity() { return capacity; }

    @Override public boolean providesSchoolBonus() { return capacity > 0; }

    @Override public void onReserveEmpty(net.minecraft.server.level.ServerPlayer player,
                                          InteractionHand hand, ItemStack stack) {
        empty(player, hand, stack, org.bytechen.rottenruins.gameplay.magic.MagicRuntime.services().emptyWand());
    }

    @Override public void onCastComplete(net.minecraft.server.level.ServerPlayer player,
                                          InteractionHand hand, ItemStack stack) {
        if (capacity > 0) stack.hurtAndBreak(1, player, holder -> holder.broadcastBreakEvent(hand));
    }


    public int mana(@NotNull ItemStack stack) {
        if (capacity == 0) return 0;
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(MANA_TAG)
                ? capacity : Math.max(0, Math.min(capacity, tag.getInt(MANA_TAG)));
    }

    public void setMana(@NotNull ItemStack stack, int amount) {
        stack.getOrCreateTag().putInt(MANA_TAG, Math.max(0, Math.min(capacity, amount)));
    }

    public boolean usesReserve(@NotNull IMagicBaseItem spell) {
        return capacity > 0 && (capacity == COMMON_CAPACITY
                || spell.hasMagicType(getMagicType()));
    }

    @Override
    public boolean accepts(@NotNull ItemStack staff, @NotNull IMagicBaseItem spell) {
        // Empty wands still cast; MagicHandle charges the player's mana instead.
        return true;
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack stack) { return true; }

    @Override
    public int getEnchantmentValue() { return 15; }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> lines, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        if (capacity > 0) {
            lines.add(Component.translatable("tooltip.rotten_ruins.wand_mana", mana(stack), capacity)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** Preserve enchantments, name and wear when the reserve reaches zero. */
    public static void empty(@NotNull Player player, @NotNull InteractionHand hand,
                             @NotNull ItemStack original, @NotNull Item emptyItem) {
        ItemStack blank = new ItemStack(emptyItem);
        if (original.hasTag()) {
            CompoundTag tag = original.getTag().copy();
            tag.remove(MANA_TAG);
            blank.setTag(tag);
        }
        blank.setDamageValue(original.getDamageValue());
        player.setItemInHand(hand, blank);
    }
}
