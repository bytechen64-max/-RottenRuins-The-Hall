package org.bytechen.rottenruins.api.infection.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

/**
 * 击杀计数变更事件。
 * 在实体击杀计数已变更后触发（不可取消），
 * 携带变更前后的值，供其他模组响应计数变化。
 */
public class KillCountChangeEvent extends Event {

    private final LivingEntity entity;
    private final int oldCount;
    private final int newCount;

    public KillCountChangeEvent(LivingEntity entity, int oldCount, int newCount) {
        this.entity = entity;
        this.oldCount = oldCount;
        this.newCount = newCount;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public int getOldCount() {
        return oldCount;
    }

    public int getNewCount() {
        return newCount;
    }
}
