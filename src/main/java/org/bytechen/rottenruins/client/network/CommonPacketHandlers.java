package org.bytechen.rottenruins.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.client.gui.DifficultySelectScreen;
import org.bytechen.rottenruins.client.rend.HitboxCubeRenderer;
import org.bytechen.rottenruins.client.rend.LightningEffectRenderer;
import org.bytechen.rottenruins.client.rend.MeteoriteGroundEffectRenderer;
import org.bytechen.rottenruins.client.rend.gui.GuiShaderEffect;
import org.bytechen.rottenruins.client.rend.gui.GuiShaderManager;
import org.bytechen.rottenruins.overworld.registry.capability.CapabilityAttacher;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;
import org.bytechen.rottenruins.overworld.registry.capability.anomaly.AnomalyCapability;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectHolder;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import java.util.UUID;

/** Entered through the physical-client packet guard, on the game thread. */
public final class CommonPacketHandlers {
    private CommonPacketHandlers() {}
    public static void applyAnomaly(int entityId, CompoundTag anomalyData) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof LivingEntity living)) return;

        AnomalyCapability cap = CapabilityAttacher.getCapability(
                living, CapabilityRegistry.ANOMALY_CAP, null);
        if (cap != null && anomalyData != null) {
            cap.deserializeNBT(anomalyData);
        }
    }

    public static void applyCustomEffect(int entityId, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof LivingEntity living) || data == null) {
            return;
        }
        CustomEffectHolder holder = CustomEffects.getHolder(living);
        if (holder != null) {
            holder.readSnapshot(data);
        }
    }

    public static void openDifficulty() {
        Minecraft.getInstance().setScreen(new DifficultySelectScreen());
    }

    public static void applyEffect(String typeId, UUID instanceId, CompoundTag data) {
        // 根据 typeId 分发给对应的渲染器
        if ("lightning".equals(typeId)) {
            LightningEffectRenderer.handleSyncPacket(instanceId, data);
        } else if ("hitbox_cube".equals(typeId)) {
            HitboxCubeRenderer.handleSyncPacket(instanceId, data);
        } else if ("meteorite_ground".equals(typeId)) {
            MeteoriteGroundEffectRenderer.handleSyncPacket(instanceId, data);
        } else if ("gui_shader".equals(typeId)) {
            // GUI shader overlay effect dispatch
            GuiShaderEffect effect = GuiShaderManager.fromNbt(data);
            if (effect != null) {
                GuiShaderManager.getInstance().addEffect(instanceId, effect);
            }
        }
    }
}
