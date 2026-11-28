package org.bytechen.rottenruins.event.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.advancement.RotAdvancementTriggers;
import org.bytechen.rottenruins.advancement.SourceMilestoneTrigger;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.worldgen.HighPotentialChunks;

/**
 * 成就 / 进度相关事件。
 *
 * <p>本类只负责「把游戏内事件翻译成自定义判据的触发」。判据本体在
 * {@code org.bytechen.rottenruins.advancement}，进度数据由 datagen
 * （{@code AdvancementProviderData}）生成。以后新增判据时，按同一模式在这里补监听方法即可。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AdvancementEventHandler {

    /**
     * 玩家登录（服务端）。
     *
     * <p>触发时机是安全的：{@code ServerPlayer} 构造期就会创建 {@code PlayerAdvancements} 并
     * 注册进度监听器，而 Forge 的 {@code PlayerLoggedInEvent} 要等到 {@code placeNewPlayer}
     * 末尾（{@code firePlayerLoggedIn}）才发，此时监听器已经就绪，所以这里可以立即触发。</p>
     *
     * <p>重复登录不会重复发放：进度完成后原版的 award 是 no-op；旧存档里的玩家也会在
     * 下一次登录时补发，行为符合「第一次进入世界」的语义。</p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            RotAdvancementTriggers.triggerFirstJoin(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if(event.phase==TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount%20==0) {
            checkHighPotential(player);
        }
    }

    public static void checkHighPotential(ServerPlayer player) {
        // 场强计本身带读数 NBT，判定物品类型而非完整 ItemStack，避免读数使成就失效。
        boolean carriesMeter=player.getOffhandItem().is(RegisterItem.SIMPLE_FIELD_METER.get())
                || player.getInventory().items.stream().anyMatch(stack->stack.is(RegisterItem.SIMPLE_FIELD_METER.get()));
        if(carriesMeter && HighPotentialChunks.isHighPotentialChunk(player.serverLevel(),player.blockPosition())) {
            RotAdvancementTriggers.triggerSourceMilestone(player,SourceMilestoneTrigger.Milestone.HIGH_POTENTIAL);
        }
    }
}
