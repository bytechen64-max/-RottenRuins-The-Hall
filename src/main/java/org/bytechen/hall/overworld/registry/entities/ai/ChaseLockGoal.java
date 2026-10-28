package org.bytechen.hall.overworld.registry.entities.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;

import java.util.EnumSet;

/**
 * 追击占位 Goal —— <b>只占 {@code Flag.MOVE}，不寻路、不攻击</b>。
 *
 * <h3>为什么需要它</h3>
 * <p>王庭生物的「靠近目标 + 贴身出手」全部由
 * {@link AbstractHallEntity#tickChase()} 统一负责（那里带寻路节流与失败退避）。
 * 但目标丢失之前如果不占住 {@code Flag.MOVE}，优先级更低的
 * {@code RandomStrollGoal}（同样用 {@code Flag.MOVE}）就会随机接管：
 * 它会另开一条"散步"路径，把 {@code tickChase} 设好的追击路径顶掉，
 * 表现就是「追着追着突然开始闲逛」。</p>
 *
 * <h3>为什么不再用原版 {@code MeleeAttackGoal}</h3>
 * <p>{@code MeleeAttackGoal} 在这套体系里是纯重复劳动：</p>
 * <ul>
 *   <li>它自己会 {@code navigation.moveTo} —— 与 {@code tickChase} 各算各的路径，
 *       一只怪同时跑两份 A*；</li>
 *   <li>它靠 {@code mob.doHurtTarget()} 出手，而 {@link AbstractHallEntity}
 *       把 {@code doHurtTarget} 写死返回 {@code false}，伤害其实走的是
 *       {@code doHurtGoal → getAttack} —— 也就是说它的攻击部分<b>一直是空转</b>。</li>
 * </ul>
 * <p>换成本 Goal 之后，路径只剩 {@code tickChase} 一条，出手只剩
 * {@code doHurtGoal} 一条，行为不变而开销减半。</p>
 *
 * <h3>朝向</h3>
 * <p>本 Goal 刻意<b>不</b>占 {@code Flag.LOOK}：朝向交给 {@link FaceTargetGoal}
 * （它也只占 {@code Flag.LOOK}）。两个各占一个标记，互不抢位。</p>
 *
 * <p>用法：放在原来 {@code MeleeAttackGoal} 的位置即可，优先级 1。</p>
 */
public class ChaseLockGoal extends Goal {

    private final Mob mob;

    public ChaseLockGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return hasUsableTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return hasUsableTarget();
    }

    /** 目标没了就把导航停掉，免得残留一条通往尸体的路径。 */
    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    /** 只做一次字段读取，GoalSelector 每 tick 都会问一次，必须够便宜。 */
    private boolean hasUsableTarget() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }
}
