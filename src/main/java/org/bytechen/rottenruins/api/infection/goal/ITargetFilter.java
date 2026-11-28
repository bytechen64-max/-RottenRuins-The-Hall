package org.bytechen.rottenruins.api.infection.goal;

import net.minecraft.world.entity.LivingEntity;

/**
 * 索敌自定义过滤器。
 * <p>
 * 在默认规则（排除自身、已死亡、不可攻击、非同类感染生物）之外，
 * 附加额外的可攻击条件判断。返回 {@code true} 允许锁定目标，{@code false} 排除。
 * <p>
 * 典型用法：
 * <pre>{@code
 * // 不攻击创造模式玩家
 * .filter((owner, target) -> !(target instanceof Player player && player.isCreative()))
 *
 * // 只攻击村民和玩家
 * .filter((owner, target) -> target instanceof Villager || target instanceof Player)
 * }</pre>
 */
@FunctionalInterface
public interface ITargetFilter {

    /**
     * 判断是否可以将目标纳入索敌范围。
     * <p>
     * 该方法在默认筛选之后调用——到达此处时目标已被确认：
     * 存活的、可攻击的、非自身的、非排除类型的感染生物。
     *
     * @param owner  索敌主体（实现 {@link org.bytechen.rottenruins.api.infection.IInfectedEntity} 的实体）
     * @param target 候选目标
     * @return true 允许锁定，false 排除
     */
    boolean canTarget(LivingEntity owner, LivingEntity target);
}
