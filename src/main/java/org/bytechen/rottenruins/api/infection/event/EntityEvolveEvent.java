package org.bytechen.rottenruins.api.infection.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

/**
 * 实体进化/感染事件。
 * 当实体通过 {@link org.bytechen.rottenruins.gameplay.infection.evolution.EvolutionManager#applyEvolution} 成功转变时触发。
 * 在 Forge 事件总线上触发，可被其他模组监听以响应进化行为。
 */
public class EntityEvolveEvent extends Event {

    private final ServerLevel level;
    private final LivingEntity original;
    private final LivingEntity resultEntity;

    public EntityEvolveEvent(ServerLevel level, LivingEntity original, LivingEntity resultEntity) {
        this.level = level;
        this.original = original;
        this.resultEntity = resultEntity;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public LivingEntity getOriginal() {
        return original;
    }

    public LivingEntity getResultEntity() {
        return resultEntity;
    }
}
