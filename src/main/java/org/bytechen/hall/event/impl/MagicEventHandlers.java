package org.bytechen.hall.event.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.network.MagicSync;
import org.bytechen.hall.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.hall.overworld.registry.capability.TwistedPoint;
import org.bytechen.hall.overworld.registry.items.magic.bases.MagicHandle;

/**
 * 魔法系统的<b>驱动</b>：把"每 tick 该做的事""死亡时该做的事""客户端该收到什么"接上。
 *
 * <p>单独一个订阅者，而不是塞进既有的 {@code ForgeEventHelpers} —— 魔法是后加的一套系统，
 * 把它的驱动隔离在这里，改动不会碰到已经在跑的难度/威胁/异常逻辑。</p>
 *
 * <h3>服务端</h3>
 * <ol>
 *   <li>回蓝与冷却每 tick 推进一次；</li>
 *   <li>击杀回蓝（击杀者是玩家时，按被击杀生物最大生命值折算）；</li>
 *   <li>把法力与槽位按脏标记节流推给客户端（见 {@code MagicSync}）。</li>
 * </ol>
 *
 * <h3>客户端</h3>
 * <p>只推进冷却倒计时用于显示。<b>不</b>回蓝、<b>不</b>驱逐物品、<b>不</b>标脏 ——
 * 客户端那份法力与槽位永远只是服务端推过来的镜像，一切判定以服务端为准。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicEventHandlers {

    private MagicEventHandlers() {
    }

    /**
     * 每 tick：服务端回蓝 + 冷却 + 推送；客户端只做冷却倒计时。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        PlayerMagicPool pool = MagicHandle.getPool(player);

        // ── 客户端：只推进冷却倒计时（本地预测，供 HUD 画环）──
        // tickCooldowns() 内部已经判断了侧，客户端不会驱逐物品也不会标脏。
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

        // 同步：内部按间隔节流，脏才发
        MagicSync.tick(serverPlayer);
    }

    /**
     * 击杀回蓝：击杀者是玩家 → 该玩家按被击杀生物的最大生命值回蓝。
     *
     * <p>用 {@code getSource().getEntity()} 而不是 {@code getDirectEntity()}：
     * 法术伤害是 {@code indirect_magic}，归因写在 {@code getEntity()} 上；
     * 而且 {@code SpellDamageUtil} 已经把击杀者写进了目标的 {@code lastHurtByMob}，
     * 所以用 {@code getEntity()} 才能和掉落归因保持一致。</p>
     */
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

    /**
     * 找出"该记在谁头上"的玩家击杀者。
     *
     * <p>先看伤害来源；来源里没有玩家时退回目标记录的 {@code lastHurtByMob} ——
     * 例如玩家先打了一刀，最后被自己的持续法术收掉，那种情况下
     * {@code LivingDeathEvent} 的来源可能已经不是玩家了。</p>
     *
     * <p>用 {@code getLastHurtByMob()} + {@code instanceof Player} 而不是
     * {@code getLastHurtByPlayer()}：后者在 1.20.1 里不是 public 方法。</p>
     */
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

    // ══════════════════════════════════════════════════════════════
    // 同步时机
    // ══════════════════════════════════════════════════════════════
    // 下面这几个都要**无条件**推一次：那些时刻客户端刚拿到一个新实体，
    // 本地那份是全新的空状态，而服务端的脏标记是干净的（什么都没改），
    // 不 force 就永远同步不过去。

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

    /**
     * 死亡重生时把法术槽与法力搬到新玩家实体上。
     *
     * <p>Forge 在重生时会重建 {@code ServerPlayer}，能力会因为
     * {@code AttachCapabilitiesEvent} 再触发一次而被重新创建成<b>初始状态</b>。
     * 不在这里手动搬一次，玩家一死就会丢掉所有已装配的法术、法力也会被重置回满 ——
     * 对"槽位里存着法术"这种设定来说这是不能接受的。</p>
     *
     * <p>刻意<b>不区分</b> {@code isWasDeath()}：死亡也照搬。
     * 如果以后要做"死亡清空法术槽"，在这里加一个 {@code if (event.isWasDeath()) return;} 即可。</p>
     */
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
