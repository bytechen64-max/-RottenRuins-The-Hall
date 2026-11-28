package org.bytechen.rottenruins.content.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.RegisterAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

/**
 * 铭刻之钟：戴在饰品栏里就给 {@value #COOLDOWN_REDUCTION} 点冷却缩减。
 *
 * <p>走 Curios 自带的属性修饰器通道（{@link #getAttributeModifiers}），
 * 所以戴上 / 摘下 / 换槽都由 Curios 负责结算，不用自己记状态。</p>
 */
public class InscribedBell extends Item implements ICurioItem {

    /** 冷却缩减点数（属性单位是"百分比点数"，所以 10 = 10%）。 */
    public static final double COOLDOWN_REDUCTION = 10.0D;

    /** 修饰器名字：Curios 会用槽位 + 这件物品算出 UUID，名字只用于调试与去重。 */
    private static final String MODIFIER_NAME = "rotten_ruins.inscribed_bell";

    public InscribedBell() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public @NotNull Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            @NotNull SlotContext slotContext, @NotNull UUID uuid, @NotNull ItemStack stack) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(RegisterAttributes.COOLDOWN_REDUCTION.get(),
                new AttributeModifier(uuid, MODIFIER_NAME, COOLDOWN_REDUCTION,
                        AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip." + org.bytechen.rottenruins.HallMod.MODID
                                + ".inscribed_bell.cooldown",
                        Component.literal("+" + (int) COOLDOWN_REDUCTION).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item." + org.bytechen.rottenruins.HallMod.MODID
                + ".inscribed_bell.desc").withStyle(ChatFormatting.GRAY));
    }
}
