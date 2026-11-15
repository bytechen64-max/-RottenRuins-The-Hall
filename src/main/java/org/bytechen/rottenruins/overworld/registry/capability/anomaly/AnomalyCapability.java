package org.bytechen.rottenruins.overworld.registry.capability.anomaly;

import org.bytechen.rottenruins.event.impl.AnomalyTriggerEvent;
import org.bytechen.rottenruins.api.anomaly.AnomalyType;
import org.bytechen.rottenruins.core.anomaly.AnomalyState;
import org.bytechen.rottenruins.platform.forge.anomaly.AnomalyDamageInbox;
import org.bytechen.rottenruins.overworld.registry.capability.base.AbstractCapability;
import org.bytechen.rottenruins.overworld.registry.capability.base.IModCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

/**
 * 异常能力 —— 管理一个生物的多种异常进度。
 * <p>
 * 每种异常类型有独立的进度条（0 ~ maxProgress），叠满触发 {@link AnomalyTriggerEvent}，
 * 进度清零并给予对应适应 Buff。适应 Buff 存在时无法叠加对应异常进度。
 * <p>
 * 仅对玩家（LivingEntity）有效。
 */
public class AnomalyCapability extends AbstractCapability<AnomalyCapability> implements IModCapability {

    private final AnomalyState<AnomalyType> state =
            new AnomalyState<>(AnomalyType.class, AnomalyType::getMaxProgress);

    public AnomalyState<AnomalyType> state() { return state; }
    private final AnomalyDamageInbox damageInbox = new AnomalyDamageInbox();
    public AnomalyDamageInbox damageInbox() { return damageInbox; }

    // —— 进度读写 ——

    /** 获取指定异常的当前进度（0 ~ maxProgress） */
    public int getProgress(AnomalyType type) {
        return state.get(type);
    }

    /** 设置指定异常的进度（会被钳制到 [0, maxProgress]） */
    public void setProgress(AnomalyType type, int value) {
        state.set(type, value);
    }

    /** 是否有任意一种异常进度 > 0 */
    public boolean hasAnyAnomaly() {
        return state.hasAny();
    }

    /** 指定异常进度是否 > 0 */
    public boolean hasAnomaly(AnomalyType type) {
        return getProgress(type) > 0;
    }

    /** 所有异常进度清零 */
    public void clearAll() {
        state.clear();
    }

    /**
     * 尝试为实体增加异常进度。
     *
     * @param entity 目标生物
     * @param type   异常类型
     * @param amount 增加量（正数）
     * @return 是否成功增加（被适应 Buff 阻挡时返回 false）
     */
    public boolean addProgress(LivingEntity entity, AnomalyType type, int amount) {
        if (amount <= 0) return false;
        if (hasAdaptation(entity, type)) return false;

        // 叠满 → 触发
        if (state.add(type, amount)) {
            trigger(entity, type);
        }
        return true;
    }

    // —— 适应检查 ——

    /** 实体是否拥有对应异常的适应 Buff */
    public boolean hasAdaptation(LivingEntity entity, AnomalyType type) {
        return entity.hasEffect(org.bytechen.rottenruins.platform.forge.anomaly.AnomalyEffects.adaptation(type));
    }

    // —— 触发逻辑 ——

    /**
     * 异常叠满触发：清零进度 → 抛事件 → 给予适应 Buff。
     * 调用者应确保进度已达到 maxProgress。
     */
    public void trigger(LivingEntity entity, AnomalyType type) {
        // 先清零进度，避免触发伤害被 LivingHurtEvent 放大
        state.set(type, 0);

        // 抛事件（事件监听者实现具体触发效果）
        AnomalyTriggerEvent event = new AnomalyTriggerEvent(entity, type);
        MinecraftForge.EVENT_BUS.post(event);

        // 给予适应 Buff
        entity.addEffect(new MobEffectInstance(
                org.bytechen.rottenruins.platform.forge.anomaly.AnomalyEffects.adaptation(type),
                type.getAdaptationDurationTicks(),
                0,  // amplifier
                false,  // ambient
                true,   // visible
                true    // showIcon
        ));
    }

    // —— NBT 持久化 ——

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = syncData();
        CompoundTag idle = new CompoundTag();
        for (AnomalyType type : AnomalyType.values()) {
            int p = state.get(type);
            if (p > 0) {
                idle.putInt(type.getId(), state.idleTicks(type));
            }
        }
        tag.put("IdleTicks", idle);
        return tag;
    }

    /** Wire snapshot deliberately excludes persistence clocks and attacker cooldowns. */
    public CompoundTag syncData() {
        CompoundTag tag = new CompoundTag();
        for (AnomalyType type : AnomalyType.values()) {
            if (state.get(type) > 0) tag.putInt(type.getId(), state.get(type));
        }
        return tag;
    }

    public void applySyncData(CompoundTag tag) {
        state.clear();
        for (AnomalyType type : AnomalyType.values()) state.set(type, tag.getInt(type.getId()));
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        state.clear();
        CompoundTag idle = nbt.getCompound("IdleTicks");
        for (AnomalyType type : AnomalyType.values()) {
            String key = type.getId();
            if (nbt.contains(key)) {
                int p = nbt.getInt(key);
                if (p > 0) {
                    state.restore(type, p, idle.getInt(key));
                }
            }
        }
    }
}
