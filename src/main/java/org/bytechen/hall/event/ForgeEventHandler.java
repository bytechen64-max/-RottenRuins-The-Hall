package org.bytechen.hall.event;

import net.minecraft.world.damagesource.DamageTypes;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.command.AnomalyCommand;
import org.bytechen.hall.command.BlackHoleCommand;
import org.bytechen.hall.command.CollapseCommand;
import org.bytechen.hall.command.SpreadCommand;
import org.bytechen.hall.command.ThreatCommand;
import org.bytechen.hall.overworld.registry.items.CrimsonVowAttack;
import org.bytechen.hall.overworld.registry.items.VoidSwordGuard;
import org.bytechen.hall.overworld.registry.items.WeaponBlock;
import org.bytechen.hall.api.IBlockingWeapon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEventHandler {

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        ForgeEventHelpers.handleAttachCapabilities(event);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() != null) ForgeEventHelpers.handleLivingHurt(event);
        // 天穹裁决：突进期间的减伤（见 DomeriteLongswordBehavior）
        DomeriteLongswordBehavior.verdictDashMitigation(event);
        // IBlockingWeapon 武器的正面格挡减伤（见 WeaponBlock）
        float blocked = WeaponBlock.mitigate(event.getEntity(), event.getSource(), event.getAmount());
        if (blocked != event.getAmount()) {
            event.setAmount(blocked);
        }
    }

    /**
     * 绯红誓约的攻击：在原版玩家伤害之上叠加 VitalProbe 改血（常量 20 + 目标最大生命值 20%）
     * 与失心粒子。
     *
     * <p><b>刻意不取消事件</b>：取消会让 {@code Player.attack} 直接 return，
     * 连原版的受击红闪/击退/受击音与"出刀自动落剑"一起没了。
     * 详见 {@code CrimsonVowAttack} 的类注释。</p>
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        // 单纯的叠加：不 setCanceled
        CrimsonVowAttack.onAttackLanded(player, event.getTarget());
    }

    /**
     * 虚空剑「蹲下左键」：把目标生命值强制改成 -1。
     *
     * <p>与绯红誓约那条<b>刻意相反</b>：这里同样不取消事件（取消会让受击红闪/击退/
     * 落剑一起消失），但目标血量已经被 {@link VoidSwordGuard} 钉死了，
     * 原版结算只是再确认一次死亡。</p>
     */
    @SubscribeEvent
    public static void onAttackEntityVoidSword(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        VoidSwordGuard.executeInstantKill(player, target);
    }

    /**
     * 虚空剑「蹲下左键」的第二道：<b>伤害被别的模组取消时</b>补回"强制选中"。
     *
     * <p>优先级 {@code LOWEST}：Forge 同一优先级按注册顺序派发，取 LOWEST 能保证
     * 别的模组已经在更早的优先级里把事件取消掉，所以这里读到的
     * {@code isCanceled()} 是最终结果。价格是每次受击多一次 boolean 判断，
     * 无论有没有取消都不做任何事。</p>
     *
     * <p>注意<b>不动 {@code setAmount}</b>：金额层面的减伤（{@code WeaponBlock}
     * 那一类）是另一回事，让它们照常工作；这里只处理"整段被取消"。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurtVoidSword(LivingHurtEvent event) {
        if (event.getEntity() == null) return;
        VoidSwordGuard.forceSelectionOnHurt(event.getEntity(), event.getSource(), event.isCanceled());
    }

    /**
     * 每 tick：既有的实体 tick 处理 + {@link IBlockingWeapon} 的格挡回调派发。
     *
     * <p>格挡的 tick 驱动挂在这里统一转发，所以实现 {@code IBlockingWeapon} 的武器
     * <b>不需要自己再挂 tick 事件</b> —— 接口只承诺回调，调度由框架负责。</p>
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() == null) return;
        ForgeEventHelpers.handleLivingTick(event.getEntity());
        IBlockingWeapon.tickBlocking(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) ForgeEventHelpers.handleLivingDeath(event);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AnomalyCommand.register(event);
        SpreadCommand.register(event);
        CollapseCommand.register(event);
        BlackHoleCommand.register(event);
        ThreatCommand.register(event);
    }
}
