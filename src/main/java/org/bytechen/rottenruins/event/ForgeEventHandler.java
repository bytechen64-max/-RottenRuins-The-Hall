package org.bytechen.rottenruins.event;

import net.minecraft.world.damagesource.DamageTypes;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.command.AnomalyCommand;
import org.bytechen.rottenruins.command.BlackHoleCommand;
import org.bytechen.rottenruins.command.CollapseCommand;
import org.bytechen.rottenruins.command.CrimsonVowCommand;
import org.bytechen.rottenruins.command.CustomEffectCommand;
import org.bytechen.rottenruins.command.HighPotentialCommand;
import org.bytechen.rottenruins.command.MeteorEventCommand;
import org.bytechen.rottenruins.command.SpreadCommand;
import org.bytechen.rottenruins.command.ThreatCommand;
import org.bytechen.rottenruins.overworld.registry.items.CrimsonVowAttack;
import org.bytechen.rottenruins.overworld.registry.items.SilentDaylightAttack;
import org.bytechen.rottenruins.overworld.registry.items.VoidSwordGuard;
import org.bytechen.rottenruins.overworld.registry.items.WeaponBlock;
import org.bytechen.rottenruins.api.IBlockingWeapon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
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

    /** 绯红誓约攻击：叠加改血（常量 20 + 目标最大生命 20%）与失心粒子；刻意不取消事件，否则受击红闪/击退/落剑都会消失。 */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        CrimsonVowAttack.onAttackLanded(player, event.getTarget());
        // 静谧白昼：主手拿着它时，无论这一下有没有造成伤害，都追加三次斩击
        if (player instanceof ServerPlayer serverPlayer
                && event.getTarget() instanceof LivingEntity livingTarget) {
            SilentDaylightAttack.onAttackLanded(serverPlayer, livingTarget);
        }
    }

    /** 虚空剑「蹲下左键」：目标血量强制改成 -1，不取消事件以保留受击红闪/击退/落剑。 */
    @SubscribeEvent
    public static void onAttackEntityVoidSword(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        VoidSwordGuard.executeInstantKill(player, target);
    }

    /** 虚空剑第二道：伤害被别的模组取消时补回"强制选中"；取 LOWEST 保证读到的 isCanceled() 是最终结果，且不动 setAmount。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurtVoidSword(LivingHurtEvent event) {
        if (event.getEntity() == null) return;
        VoidSwordGuard.forceSelectionOnHurt(event.getEntity(), event.getSource(), event.isCanceled());
    }

    /** 每 tick：实体 tick 处理 + IBlockingWeapon 格挡回调派发（实现者无需自己再挂 tick 事件）。 */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() == null) return;
        ForgeEventHelpers.handleLivingTick(event.getEntity());
        IBlockingWeapon.tickBlocking(event.getEntity());
        // 自定义效果：双端都会进，服务端跑逻辑、客户端只倒计时（见 CustomEffects#tick）
        org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects
                .tick(event.getEntity());
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
        CustomEffectCommand.register(event);
        HighPotentialCommand.register(event);
        CrimsonVowCommand.register(event);
        MeteorEventCommand.register(event);
    }
}
