package org.bytechen.rottenruins.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import org.bytechen.rottenruins.utils.ModUtils;

/**
 * Jade（Waila）集成：
 * <ul>
 *   <li>奥秘仪台：显示势能强度、状态与合成进度；</li>
 *   <li>生物：显示它身上的<b>自定义效果</b>（本模组自成一套，原版 Jade 看不到）。</li>
 * </ul>
 *
 * <p>没有编译依赖 Jade 时这个包会被 build.gradle 排除（见 sourceSets 的 exclude）。</p>
 */
@WailaPlugin(HallMod.MODID)
public final class JadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(PotentialCraftingProvider.INSTANCE,
                org.bytechen.rottenruins.overworld.registry.blocks.impl.PotentialCraftingTableBlock.class);
        registration.registerEntityComponent(CustomEffectProvider.INSTANCE, LivingEntity.class);
    }

    public enum PotentialCraftingProvider implements IBlockComponentProvider {
        INSTANCE;

        private final ResourceLocation uid = ModUtils.modLoc("potential_crafting_table");

        @Override
        public ResourceLocation getUid() {
            return uid;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof PotentialCraftingTableBlockEntity station)) {
                return;
            }
            int power = station.currentPower();
            tooltip.add(Component.translatable("gui.rotten_ruins.potential_crafting.power", power, 15)
                    .withStyle(power > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
            if (station.isCrafting()) {
                tooltip.add(Component.translatable("gui.rotten_ruins.jade.crafting",
                        station.progressPermille() / 10,
                        Math.max(1, station.totalTicks() / 20)));
            } else if (power <= 0) {
                tooltip.add(Component.translatable("gui.rotten_ruins.potential_crafting.needs_power")
                        .withStyle(ChatFormatting.RED));
            } else {
                tooltip.add(Component.translatable("gui.rotten_ruins.potential_crafting.idle")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public enum CustomEffectProvider implements IEntityComponentProvider {
        INSTANCE;

        private final ResourceLocation uid = ModUtils.modLoc("custom_effects");

        @Override
        public ResourceLocation getUid() {
            return uid;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof LivingEntity living)) {
                return;
            }
            var effects = org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects
                    .getActiveEffects(living);
            for (var instance : effects) {
                String name = Component.translatable(instance.getEffect().getTranslationKey()).getString();
                String time = org.bytechen.rottenruins.client.gui.CustomEffectHudRenderer
                        .formatDuration(instance.getDuration());
                tooltip.add(Component.literal(name + " " + time)
                        .withStyle(instance.getEffect().isHarmful()
                                ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
        }
    }
}
