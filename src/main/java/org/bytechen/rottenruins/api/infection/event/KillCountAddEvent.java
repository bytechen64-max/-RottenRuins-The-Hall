package org.bytechen.rottenruins.api.infection.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

/**
 * 击杀计数增加事件。
 * 在实体击杀计数即将增加时触发，可取消以阻止计数增加。
 * 若未取消，可通过 {@link #setAmount(int)} 修改增加量。
 */
public class KillCountAddEvent extends Event {

    private final LivingEntity entity;
    private int amount;

    public KillCountAddEvent(LivingEntity entity, int amount) {
        this.entity = entity;
        this.amount = amount;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }
}
