package org.bytechen.hall.event.impl;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
import org.bytechen.hall.api.anomaly.AnomalyType;

/**
 * 异常触发事件 —— 当某个异常进度叠满（达到 maxProgress）时在 FORGE 总线上抛出。
 * <p>
 * 可被取消；取消后进度仍会清零并给予适应 Buff，但不会执行额外的触发逻辑。
 * 监听此事件来实现不同异常类型的触发效果（伤害、粒子、debuff 等）。
 */
public class AnomalyTriggerEvent extends Event {
    private final LivingEntity entity;
    private final AnomalyType type;

    public AnomalyTriggerEvent(LivingEntity entity, AnomalyType type) {
        this.entity = entity;
        this.type = type;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public AnomalyType getAnomalyType() {
        return type;
    }
}
