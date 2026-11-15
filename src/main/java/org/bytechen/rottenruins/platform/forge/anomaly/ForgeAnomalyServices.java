package org.bytechen.rottenruins.platform.forge.anomaly;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import org.bytechen.rottenruins.api.anomaly.AnomalyAttack;
import org.bytechen.rottenruins.api.anomaly.AnomalyDamageSource;
import org.bytechen.rottenruins.api.anomaly.AnomalyType;
import org.bytechen.rottenruins.content.anomaly.HallAnomalyAttacks;
import org.bytechen.rottenruins.core.anomaly.AnomalyState;
import org.bytechen.rottenruins.gameplay.anomaly.AnomalyRuntime;
import org.bytechen.rottenruins.gameplay.anomaly.AnomalyEngine;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;
import org.bytechen.rottenruins.overworld.registry.capability.anomaly.AnomalyCapability;

/** Stateless registry/capability bridge; no retained players or worlds. */
public final class ForgeAnomalyServices implements AnomalyRuntime.Services {
    private static AnomalyCapability capability(LivingEntity entity) {
        return entity.getCapability(CapabilityRegistry.ANOMALY_CAP).orElse(null);
    }

    public static void enqueue(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        AnomalyCapability cap = capability(event.getEntity());
        if (cap != null && AnomalyRuntime.services().attack(event.getSource()) != null) {
            cap.damageInbox().add(event);
        }
    }

    public static void flush(LivingEntity player) {
        if (player.level().isClientSide()) return;
        AnomalyCapability cap = capability(player);
        if (cap == null) return;
        for (LivingDamageEvent event : cap.damageInbox().drain()) {
            // Read the final event after all priorities have run, including late cancellation.
            if (!event.isCanceled()) AnomalyEngine.onDamage(player, event.getSource(), event.getAmount());
        }
    }

    @Override public AnomalyState<AnomalyType> state(LivingEntity entity) {
        AnomalyCapability cap = capability(entity);
        return cap == null ? null : cap.state();
    }

    @Override public boolean addProgress(LivingEntity entity, AnomalyType type, int amount) {
        AnomalyCapability cap = capability(entity);
        return cap != null && cap.addProgress(entity, type, amount);
    }

    @Override public AnomalyAttack attack(DamageSource source) {
        if (source instanceof AnomalyDamageSource declared) return declared.attack();
        AnomalyAttack direct = find(source.getDirectEntity(), source);
        return direct != null ? direct : find(source.getEntity(), source);
    }

    private AnomalyAttack find(Entity entity, DamageSource source) {
        if (entity == null) return null;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? null : HallAnomalyAttacks.find(id.getNamespace(), id.getPath(),
                source.is(DamageTypeTags.IS_EXPLOSION));
    }
}
