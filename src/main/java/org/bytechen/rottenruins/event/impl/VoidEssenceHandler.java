package org.bytechen.rottenruins.event.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;

/**
 * 虚空精粹的获取方式：末地 y&lt;0 处用玻璃瓶采集。
 * 无论成功与否都会消耗一个玻璃瓶。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoidEssenceHandler {

    private static final float SUCCESS_CHANCE = 0.10F;
    private static final int COOLDOWN_TICKS = 10;

    private VoidEssenceHandler() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        Level level = player.level();
        if (!stack.is(Items.GLASS_BOTTLE)
                || level.dimension() != Level.END
                || player.getY() >= 0.0D) {
            return;
        }
        // 拦下原版玻璃瓶逻辑，避免背包里的瓶子和手上升级各走一条路。
        event.setCanceled(true);
        if (player.getCooldowns().isOnCooldown(Items.GLASS_BOTTLE)) {
            return;
        }
        player.getCooldowns().addCooldown(Items.GLASS_BOTTLE, COOLDOWN_TICKS);
        stack.shrink(1);
        if (level.random.nextFloat() < SUCCESS_CHANCE) {
            ItemStack essence = new ItemStack(RegisterItem.VOID_ESSENCE.get());
            if (!player.getInventory().add(essence)) {
                player.drop(essence, false);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 0.8F);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }
}
