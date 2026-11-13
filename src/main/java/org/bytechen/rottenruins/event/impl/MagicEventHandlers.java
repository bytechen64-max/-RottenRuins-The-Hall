package org.bytechen.rottenruins.event.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.network.MagicSync;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.capability.TwistedPoint;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle;

/** 魔法系统的驱动：每 tick 的回蓝/冷却/同步与死亡相关事件；单独一个订阅者，改动不碰既有的事件逻辑。 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicEventHandlers {

    private MagicEventHandlers() {
    }

    /** 每 tick：服务端回蓝 + 冷却 + 推送；客户端只做冷却倒计时。 */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        PlayerMagicPool pool = MagicHandle.getPool(player);

        // 客户端只推进冷却倒计时（本地预测，供 HUD 画环）；tickCooldowns 内部已判侧，不会驱逐物品也不会标脏。
        if (player.level().isClientSide()) {
            if (pool != null) {
                pool.tickCooldowns();
            }
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // 回蓝：+1/秒（速率可被修饰）
        TwistedPoint mana = MagicHandle.getMana(serverPlayer);
        if (mana != null) {
            mana.tickRegen();
        }

        // 冷却：同名物品共享的那张表
        if (pool != null) {
            pool.tickCooldowns();
        }

        // 轮盘会话：CHARGE 蓄力 / KEEP 维持（轮盘不走原版 useItem 生命周期）
        MagicHandle.tickWheel(serverPlayer);

        // 同步：内部按间隔节流，脏才发
        MagicSync.tick(serverPlayer);
    }

    /** 击杀回蓝：用 getSource().getEntity() 而非 getDirectEntity() —— 法术伤害是 indirect_magic，击杀者归因写在 getEntity() 上。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }

        Player killer = resolvePlayerKiller(event, victim);
        if (killer == null) {
            return;
        }
        TwistedPoint mana = MagicHandle.getMana(killer);
        if (mana == null) {
            return;
        }
        mana.addKillRefund(victim);
    }

    /** 来源里没有玩家时退回 lastHurtByMob（持续法术收尾时来源可能已不是玩家）；用它是因 1.20.1 的 getLastHurtByPlayer() 不是 public。 */
    private static Player resolvePlayerKiller(LivingDeathEvent event, LivingEntity victim) {
        if (event.getSource().getEntity() instanceof Player player) {
            return player;
        }
        if (event.getSource().getDirectEntity() instanceof Player player) {
            return player;
        }
        LivingEntity lastHurtBy = victim.getLastHurtByMob();
        return lastHurtBy instanceof Player player ? player : null;
    }

    // 下面几个时刻要无条件推一次：客户端刚拿到新实体，本地是全新空状态，而服务端脏标记是干净的，不 force 就永远同步不过去。

    /** 登录：清掉可能残留的释放状态，并全量推一次。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        MagicHandle.clearState(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            MagicSync.pushAll(player);
        }
    }

    /** 重生：客户端换了个新实体，重新全量推。 */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MagicHandle.clearState(player);
            MagicSync.pushAll(player);
        }
    }

    /** 换维度：客户端会重建实体，重新全量推。 */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MagicHandle.clearState(player);
            MagicSync.pushAll(player);
        }
    }

    /** 死亡重生时把法术槽与法力搬到新实体：能力因 AttachCapabilitiesEvent 被重建成初始状态，不搬一次就会丢法术、法力回满。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        PlayerMagicPool oldPool = MagicHandle.getPool(event.getOriginal());
        PlayerMagicPool newPool = MagicHandle.getPool(event.getEntity());
        if (oldPool != null && newPool != null) {
            newPool.deserializeNBT(oldPool.serializeNBT());
        }

        TwistedPoint oldMana = MagicHandle.getMana(event.getOriginal());
        TwistedPoint newMana = MagicHandle.getMana(event.getEntity());
        if (oldMana != null && newMana != null) {
            newMana.setMana(oldMana.getMana());
        }
    }

    /** 登出：清掉释放状态，避免状态表按 UUID 无限增长。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MagicHandle.clearState(event.getEntity());
    }

    /** 服务器停止：全清。 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MagicHandle.clearAll();
    }
}
