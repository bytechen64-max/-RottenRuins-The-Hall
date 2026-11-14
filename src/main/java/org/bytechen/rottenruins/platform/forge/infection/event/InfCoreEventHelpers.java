package org.bytechen.rottenruins.platform.forge.infection.event;

import org.bytechen.rottenruins.core.infection.InfectionIds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.bytechen.rottenruins.api.infection.IKillCounter;
import org.bytechen.rottenruins.platform.forge.infection.capability.CapabilityProvider;
import org.bytechen.rottenruins.platform.forge.infection.capability.CapabilityRegistry;
import org.bytechen.rottenruins.platform.forge.infection.capability.KillCountCapability;

/**
 * 事件处理逻辑。
 * <p>
 * 负责将 Forge 事件连接到框架的核心系统：
 * <ul>
 *   <li>能力附加 —— 为 {@link IKillCounter} 实体附加击杀计数能力</li>
 *   <li>实体死亡 —— 触发 {@link IKillCounter#onKilledEntity(Entity)}</li>
 *   <li>实体 tick —— 能力更新</li>
 * </ul>
 */
public final class InfCoreEventHelpers {

    private InfCoreEventHelpers() {}

    // ==================== 能力附加 ====================

    /**
     * 为实体附加能力。
     */
    public static void handleAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        Entity entity = event.getObject();

        if (entity instanceof IKillCounter killCounter) {
            KillCountCapability kcCap = new KillCountCapability(killCounter.killCounterEnabled());
            CapabilityProvider<KillCountCapability, KillCountCapability> provider =
                    new CapabilityProvider<>(CapabilityRegistry.KILL_COUNT, kcCap);
            provider.initSync(entity);
            event.addCapability(ResourceLocation.fromNamespaceAndPath(InfectionIds.NAMESPACE, "kill_count"), provider);
            event.addListener(kcCap::invalidate);
        }
    }

    // ==================== 实体死亡 ====================

    /**
     * 处理实体死亡事件。
     * <p>
     * 杀手为 {@link IKillCounter} 时调用 {@code onKilledEntity}，
     * 同时也检查杀手的能力中是否有击杀计数能力。
     */
    public static void handleLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        Entity killer = event.getSource().getEntity();

        if (killer instanceof IKillCounter killCounter) {
            killCounter.onKilledEntity(entity);
        } else if (killer instanceof LivingEntity livingKiller) {
            livingKiller.getCapability(CapabilityRegistry.KILL_COUNT).ifPresent(kc -> {
                kc.addKillCount(livingKiller, 1);
            });
        }
    }

    // ==================== 实体 Tick ====================

    /**
     * 处理实体 tick 事件。
     */
    public static void handleLivingTick(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            entity.getCapability(CapabilityRegistry.KILL_COUNT).ifPresent(kc -> {
                // 框架预留：击杀计数被动生成/治疗消耗可在此实现
                // 具体逻辑由感染模组自行扩展
            });
        }
    }
}
