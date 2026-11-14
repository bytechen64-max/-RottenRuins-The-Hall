package org.bytechen.rottenruins.overworld.registry.entities.population.death;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;

import java.util.function.Consumer;

/**
 * <b>死亡系</b>生物公共基类 —— 死亡猎手（{@link DeathHunterEntity}）及以后所有
 * "死亡"主题生物的共同父类。
 *
 * <h3>和 {@link AbstractRottenEntity} 的区别</h3>
 * <p>死亡系不是"血肉"生物，所以基类里那两套以血肉为前提的表现都要关掉：</p>
 * <ol>
 *   <li><b>受伤不溅血</b>：覆写 {@link #onHurtBlood} 为空实现。
 *       基类所有受击出血都收敛在这一个入口（见 {@code AbstractRottenEntity#hurt}），
 *       因此这里什么都不做，死亡系生物挨打时就<b>不会</b>喷 {@code blood} 粒子，
 *       也不会触发 {@code BloodBurstPacket}。</li>
 *   <li><b>死亡不爆体</b>：构造器里关掉假死（爆体）状态机 ——
 *       {@code setFakeDeathEnabled(false)}，同时把爆体渲染模式设为
 *       {@link BurstRenderMode#NONE}、关掉爆体缩放。于是 {@code die()} 会直接走
 *       正常的 {@code super.die()}：正常死亡、正常掉落战利品，<b>不会</b>出现
 *       "撑大 + 鬼畜抽搐 + 血肉爆炸"。</li>
 * </ol>
 * <p>另外基类的第二处出血点 —— 感染击杀时在受害者原位喷的血花
 * （{@link #onInfectionBlood}）—— 也一并关掉：死亡系没有血肉，
 * 不应该在它击杀的目标身上出血。</p>
 *
 * <h3>阵营：独立的 DEATH</h3>
 * <p>死亡系<b>不属于王庭（HALL），也不属于腐化（CORRUPTION）</b>：
 * {@link #faction()} 固定返回 {@link RottenFaction#DEATH}，
 * 感染类型为 {@code rotten_ruins:death}。因此：</p>
 * <ul>
 *   <li>与王庭 / 腐化互为"不同感染类型"：双方开启跨阵营索敌后天然敌对；</li>
 *   <li>同类死亡系之间仍然互不攻击；</li>
 *   <li>不参与王庭难度倍率（{@code HallDifficultyAttributes} 只作用于 HALL）、
 *       王庭光环 / 王庭威胁点数等只属于王庭的系统；</li>
 *   <li>没有 {@code death_inf} / {@code death_evo} 进化规则 ——
 *       击杀结算时 {@code EvolutionManager.applyEvolution} 查不到规则直接返回
 *       {@code false}，所以死亡系<b>不会感染击杀目标</b>。</li>
 * </ul>
 *
 * <h3>继承关系说明</h3>
 * <p>本类仍然继承 {@link AbstractRottenEntity}：寻路 / 追击钩子 / 动画控制器注册等
 * 公共设施全部照常可用，只是把"出血 + 爆体"这两条血肉表现从死亡系里摘掉，
 * 并把阵营固定为 {@link RottenFaction#DEATH}。
 * 与王庭 / 腐化不同，死亡系<b>不读玩家的王庭威胁点数</b>：那套闸门是王庭生物的
 * 专属设定，死亡系只要看见目标（创造 / 旁观除外）就会主动索敌，
 * 具体见 {@link #canTarget} / {@link #canAttackPlayer}。</p>
 *
 * <h3>资源注册</h3>
 * <p>和项目内其它生物一致：模型 / 贴图 / 动画走
 * {@code EntityTypeRegistry.defaultResources(name)}，在注册实体的工厂里通过
 * {@link #AbstractDeathEntity(EntityType, Level, Consumer)} 传入。
 * </p>
 */
public abstract class AbstractDeathEntity extends AbstractRottenEntity {

    /**
     * 只做基类初始化，不注册资源 —— 供子类需要完全自定义资源时使用。
     */
    protected AbstractDeathEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        disableFleshEffects();
    }

    /**
     * 标准构造器：基类初始化 + 应用资源（模型 / 贴图 / 动画）。
     *
     * @param resources 资源应用器，通常是 {@code EntityTypeRegistry.defaultResources("xxx")}
     */
    protected AbstractDeathEntity(EntityType<? extends Monster> entityType, Level level,
                                  Consumer<AbstractRottenEntity> resources) {
        this(entityType, level);
        resources.accept(this);
    }

    /**
     * 摘掉血肉生物的两套表现（受伤溅血 / 死亡爆体）。
     *
     * <p>放在构造器里而不是运行期切换：这两项是"这个物种天生没有血肉"的静态属性，
     * 服务端与客户端都会执行同一构造器，不存在只在一端生效的问题。</p>
     *
     * <p><b>不要</b>在本方法之后再调用 {@code setFakeDeathEnabled(true)} ——
     * 那会把死亡爆体重新打开，违背"死亡系不爆体"的约定。</p>
     */
    private void disableFleshEffects() {
        // 死亡爆体：关闭假死状态机，死亡走正常的 super.die() + 正常战利品。
        this.setFakeDeathEnabled(false);
        this.setBurstRenderMode(BurstRenderMode.NONE);
        this.setBurstScaleEnabled(false);
        // 没有 die 动画；即使以后补了，死亡爆体也已经关掉，不会拿来播。
        this.setHasDieAnim(false);
    }

    // ==================== 阵营：独立 DEATH ====================

    /**
     * 死亡系是独立阵营，不是王庭 / 腐化的分支。
     *
     * <p>阵营 id 决定 {@code IInfectedEntity.getInfectionType()} 的返回值
     * （{@code rotten_ruins:death}），从而让跨阵营索敌把死亡系与王庭 / 腐化视为敌对；
     * 同时死亡系没有对应的 {@code death_inf} 进化规则，击杀不会感染目标。</p>
     */
    @Override
    protected RottenFaction faction() {
        return RottenFaction.DEATH;
    }

    /**
     * 死亡系不受玩家王庭威胁点数限制：创造 / 旁观以外的玩家一律可被索敌。
     *
     * <p>威胁点数是王庭生物的专属机制（{@code ThreatHelper} 只统计 HALL 阵营），
     * 死亡系作为独立阵营不参与 —— 口径与腐化系
     * {@code BaseCorruptionEntity#canTarget} 一致。</p>
     */
    @Override
    public boolean canTarget(LivingEntity owner, LivingEntity target) {
        if (target instanceof Player player && isIgnoredPlayer(player)) {
            return false;
        }
        return true;
    }

    /** 同上：死亡系不看王庭威胁点数，只排除创造 / 旁观玩家。 */
    @Override
    protected boolean canAttackPlayer(Player player) {
        return !isIgnoredPlayer(player);
    }

    /**
     * 受伤溅血：死亡系<b>不喷血</b>。
     *
     * <p>基类在 {@code hurt()} 里结算真实伤害后调用本方法，入口唯一，
     * 所以这里留空就是完整的"死亡系受伤不出血"。子类若想加自己的受击表现
     * （例如黑雾、骨头碎屑），覆写本方法即可，但不要再转调基类的出血实现。</p>
     */
    @Override
    protected void onHurtBlood(DamageSource source, float damage) {
        // 死亡系没有血肉：刻意留空，不掉血花。
    }

    /**
     * 感染击杀血花：死亡系没有血肉，所以击杀并感染目标时也不在受害者原位喷血。
     *
     * <p>这是基类的第二处出血点（与 {@link #onHurtBlood} 并列）。</p>
     */
    @Override
    protected void onInfectionBlood(ServerLevel level, Vec3 center, AABB box, float damage) {
        // 死亡系没有血肉：击杀转化不喷血。
    }
}
