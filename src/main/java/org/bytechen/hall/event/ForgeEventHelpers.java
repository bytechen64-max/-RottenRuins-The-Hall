package org.bytechen.hall.event;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.event.impl.HeavyBombDeathEffect;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.network.s2c.AnomalySyncPacket;
import org.bytechen.hall.overworld.registry.CapabilityRegistry;
import org.bytechen.hall.overworld.registry.capability.anomaly.AnomalyCapability;
import org.bytechen.hall.overworld.registry.capability.base.CapabilityProvider;
import org.bytechen.hall.overworld.registry.capability.threat.ThreatCapability;
import org.bytechen.hall.overworld.registry.capability.threat.ThreatHelper;
import org.bytechen.hall.overworld.registry.entities.population.ecological.HeavyBombEntity;
import org.bytechen.hall.overworld.registry.items.DomeriteAxe;
import org.bytechen.hall.overworld.registry.items.DomeriteHoe;
import org.bytechen.hall.overworld.registry.items.DomeritePickaxe;
import org.bytechen.hall.overworld.registry.items.DomeriteShovel;
import org.bytechen.hall.overworld.registry.items.DomeriteSword;
import org.bytechen.hall.utils.DomeriteStatsHelper;
import org.bytechen.infcore.api.IInfectedEntity;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Map;

public class ForgeEventHelpers {

    private static final ResourceLocation ANOMALY_KEY =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "anomaly");

    private static final ResourceLocation THREAT_KEY =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "threat");

    private static final int SYNC_INTERVAL = 20; // 每秒同步一次（20 tick）

    // ==================== 能力附加 ====================

    public static void handleAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            AnomalyCapability cap = new AnomalyCapability();
            event.addCapability(ANOMALY_KEY,
                    new CapabilityProvider<>(CapabilityRegistry.ANOMALY_CAP, cap));
        }

        // 威胁点数：所有生物（含玩家）都挂，初始 0
        event.addCapability(THREAT_KEY,
                new CapabilityProvider<>(CapabilityRegistry.THREAT_CAP, new ThreatCapability()));
    }

    // ==================== Tick 逻辑 ====================

    public static void handleLivingTick(Entity entity) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;

        // 每个玩家按自身 tick 周期同步异常数据到客户端
        if (player.tickCount % SYNC_INTERVAL == 0) {
            syncAnomalyToClient(player);
        }

        // 每 10 tick 更新手持穹顶云铁工具的 Y 坐标，用于动态属性
        if (player.tickCount % 10 == 0) {
            updateDomeriteToolY(player);
        }
    }

    /** 将玩家当前 Y 坐标写入手持 domerite 工具的 NBT，并强制刷新属性 */
    private static void updateDomeriteToolY(ServerPlayer player) {
        double y = player.getY();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (isDomeriteTool(stack)) {
                double oldY = DomeriteStatsHelper.getY(stack);
                // Y 变化达到 10 格时才写入 NBT 并刷新属性，避免 tooltip 频繁变化
                if (Math.abs(y - oldY) >= 10.0) {
                    DomeriteStatsHelper.setY(stack, y);
                    refreshToolAttributes(player, slot, stack);
                }
            }
        }
    }

    /** 移除旧装备属性修饰符后重新添加，强制 AttributeInstance 重算 */
    private static void refreshToolAttributes(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> mods = stack.getAttributeModifiers(slot);
        for (Map.Entry<Attribute, AttributeModifier> entry : mods.entries()) {
            AttributeInstance instance = player.getAttribute(entry.getKey());
            if (instance != null) {
                AttributeModifier mod = entry.getValue();
                // 用 UUID 查找旧修饰符（不用 hasModifier，它比对对象引用）
                if (instance.getModifier(mod.getId()) != null) {
                    instance.removeModifier(mod.getId());
                }
                instance.addPermanentModifier(mod);
            }
        }
    }

    private static boolean isDomeriteTool(ItemStack stack) {
        return stack.getItem() instanceof DomeriteSword
                || stack.getItem() instanceof DomeritePickaxe
                || stack.getItem() instanceof DomeriteAxe
                || stack.getItem() instanceof DomeriteShovel
                || stack.getItem() instanceof DomeriteHoe;
    }

    /** 将玩家的异常数据同步到客户端 */
    public static void syncAnomalyToClient(ServerPlayer player) {
        AnomalyCapability cap = player.getCapability(CapabilityRegistry.ANOMALY_CAP).orElse(null);
        if (cap == null) return;
        NetworkHelper.sendToPlayer(player,
                new AnomalySyncPacket(player.getId(), cap.serializeNBT()));
    }

    /** 立即同步指定实体的异常数据到所有追踪的客户端 */
    public static void syncAnomalyToTracking(Entity entity) {
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof LivingEntity living)) return;
        AnomalyCapability cap = living.getCapability(CapabilityRegistry.ANOMALY_CAP).orElse(null);
        if (cap == null) return;
        NetworkHelper.sendToClient(entity,
                new AnomalySyncPacket(entity.getId(), cap.serializeNBT()));
    }

    // ==================== 伤害 / 死亡 ====================

    public static void handleLivingHurt(LivingHurtEvent event) {
        // hall 感染生物之间无友伤
        if (!isHallInfected(event.getEntity())) return;
        DamageSource source = event.getSource();
        if (isHallInfected(source.getDirectEntity()) || isHallInfected(source.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isHallInfected(Entity entity) {
        if (entity instanceof IInfectedEntity infected) {
            ResourceLocation type = infected.getInfectionType();
            return type != null
                    && HallMod.MODID.equals(type.getNamespace())
                    && "hall".equals(type.getPath());
        }
        return false;
    }

    public static void handleLivingDeath(LivingDeathEvent event) {
        if ((event.getEntity() instanceof HeavyBombEntity bomb)) HeavyBombDeathEffect.trigger(bomb);
        handleThreatOnDeath(event);
    }

    // ==================== 威胁点数 ====================

    /**
     * 死亡事件里的威胁点数结算。
     * <ul>
     *   <li><b>拾取</b>：击杀者为非王庭生物、被击杀者是王庭生物时，
     *       击杀者增加「被击杀者最大生命值 / 5」点威胁；</li>
     *   <li><b>清零</b>：死亡的是玩家 → 威胁点数重置为 0。</li>
     * </ul>
     * 两者互不冲突：玩家（非王庭生物）不可能击杀出王庭生物这一侧的分支。
     */
    private static void handleThreatOnDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Entity killer = event.getSource().getEntity();

        if (killer != null
                && !ThreatHelper.isHallCreature(killer)
                && ThreatHelper.isHallCreature(victim)) {
            ThreatHelper.addThreat(killer, ThreatHelper.threatFromKill(victim));
        }

        if (victim instanceof Player) {
            ThreatHelper.resetThreat(victim);
        }
    }
}
