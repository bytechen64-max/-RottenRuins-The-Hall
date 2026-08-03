package org.bytechen.hall.overworld.registry.capability.anomaly;

import org.bytechen.hall.event.impl.AnomalyTriggerEvent;
import org.bytechen.hall.api.anomaly.AnomalyType;
import org.bytechen.hall.overworld.registry.capability.base.AbstractCapability;
import org.bytechen.hall.overworld.registry.capability.base.IModCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

import java.util.EnumMap;
import java.util.Map;

/**
 * 异常能力 —— 管理一个生物的多种异常进度。
 * <p>
 * 每种异常类型有独立的进度条（0 ~ maxProgress），叠满触发 {@link AnomalyTriggerEvent}，
 * 进度清零并给予对应适应 Buff。适应 Buff 存在时无法叠加对应异常进度。
 * <p>
 * 仅对玩家（LivingEntity）有效。
 */
public class AnomalyCapability extends AbstractCapability<AnomalyCapability> implements IModCapability {

    private final Map<AnomalyType, Integer> progressMap = new EnumMap<>(AnomalyType.class);

    // —— 进度读写 ——

    /** 获取指定异常的当前进度（0 ~ maxProgress） */
    public int getProgress(AnomalyType type) {
        return progressMap.getOrDefault(type, 0);
    }

    /** 设置指定异常的进度（会被钳制到 [0, maxProgress]） */
    public void setProgress(AnomalyType type, int value) {
        int clamped = Math.max(0, Math.min(type.getMaxProgress(), value));
        if (clamped == 0) {
            progressMap.remove(type);
        } else {
            progressMap.put(type, clamped);
        }
    }

    /** 是否有任意一种异常进度 > 0 */
    public boolean hasAnyAnomaly() {
        return progressMap.values().stream().anyMatch(p -> p > 0);
    }

    /** 指定异常进度是否 > 0 */
    public boolean hasAnomaly(AnomalyType type) {
        return getProgress(type) > 0;
    }

    /** 所有异常进度清零 */
    public void clearAll() {
        progressMap.clear();
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

        int current = getProgress(type);
        int max = type.getMaxProgress();
        int next = Math.min(max, current + amount);
        progressMap.put(type, next);

        // 叠满 → 触发
        if (next >= max) {
            trigger(entity, type);
        }
        return true;
    }

    // —— 适应检查 ——

    /** 实体是否拥有对应异常的适应 Buff */
    public boolean hasAdaptation(LivingEntity entity, AnomalyType type) {
        return entity.hasEffect(type.getAdaptationEffect());
    }

    // —— 触发逻辑 ——

    /**
     * 异常叠满触发：抛事件 → 清零进度 → 给予适应 Buff。
     * 调用者应确保进度已达到 maxProgress。
     */
    public void trigger(LivingEntity entity, AnomalyType type) {
        // 先清零进度，避免触发伤害被 LivingHurtEvent 放大
        progressMap.remove(type);

        // 抛事件（事件监听者实现具体触发效果）
        AnomalyTriggerEvent event = new AnomalyTriggerEvent(entity, type);
        MinecraftForge.EVENT_BUS.post(event);

        // 给予适应 Buff
        entity.addEffect(new MobEffectInstance(
                type.getAdaptationEffect(),
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
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<AnomalyType, Integer> entry : progressMap.entrySet()) {
            int p = entry.getValue();
            if (p > 0) {
                tag.putInt(entry.getKey().getId(), p);
            }
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        progressMap.clear();
        for (AnomalyType type : AnomalyType.values()) {
            String key = type.getId();
            if (nbt.contains(key)) {
                int p = nbt.getInt(key);
                if (p > 0) {
                    progressMap.put(type, Math.min(p, type.getMaxProgress()));
                }
            }
        }
    }
}
