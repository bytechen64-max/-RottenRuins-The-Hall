package org.bytechen.rottenruins.api.infection;

import net.minecraft.world.entity.Entity;

/**
 * 击杀计数接口。
 * 感染类实体实现此接口以获得击杀计数能力，
 * 框架会自动附加 {@link org.bytechen.rottenruins.platform.forge.infection.capability.KillCountCapability} 并同步数据。
 */
public interface IKillCounter {

    /**
     * 是否启用击杀计数。默认启用。
     */
    default boolean killCounterEnabled() {
        return true;
    }

    /**
     * 获取当前击杀计数。
     */
    int getKillCount();

    /**
     * 设置击杀计数。
     */
    void setKillCount(int count);

    /**
     * 增加击杀计数。
     */
    void addKillCount(int amount);

    /**
     * 当此实体击杀另一个实体时调用。
     * 默认实现：击杀计数 +1。
     */
    default void onKilledEntity(Entity entity) {
        addKillCount(1);
    }
}
