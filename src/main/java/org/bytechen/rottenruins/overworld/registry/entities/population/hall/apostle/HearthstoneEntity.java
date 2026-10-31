package org.bytechen.rottenruins.overworld.registry.entities.population.hall.apostle;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import org.bytechen.infcore.core.difficulty.DifficultyHelper;
import org.bytechen.rottenruins.event.impl.HearthstoneAuraEvents;
import org.bytechen.rottenruins.overworld.difficulty.DifficultyScaleProfile;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * <b>王庭炉石</b> —— 王庭阵营的支援型 boss：自己几乎不打人，靠治疗 / 减伤 / 强化把一片骸物
 * 变成打不死的墙，并用「王庭坍缩」把领域里的非骸物压住。
 *
 * <h3>基础数值</h3>
 * <table border="1">
 *   <tr><td>生命</td><td>{@value #MAX_HEALTH}</td><td>护甲</td><td>{@value #ARMOR}</td></tr>
 *   <tr><td>攻击力</td><td>{@value #ATTACK_DAMAGE}（同时是治疗的换算基准）</td>
 *       <td>移动速度</td><td>{@value #MOVEMENT_SPEED}</td></tr>
 * </table>
 * <p>继承 {@link BaseApostleEntity}：使徒分类不进行普通近战，「攻击力」这个属性在这里
 * 只有一个用途 —— 技能的换算基准（例如治疗量 = 攻击力 × 难度系数）。</p>
 *
 * <h3>难度系数（难度3 = HARD，难度4 = INCOMPREHENSIBLE）</h3>
 * <table border="1">
 *   <tr><th>项</th><th>难度1-2</th><th>难度3</th><th>难度4</th></tr>
 *   <tr><td>远程/魔法减伤（自身被动）</td><td>10%</td><td>25%</td><td>40%</td></tr>
 *   <tr><td>※减伤光环（给骸物）</td><td>20%</td><td>30%</td><td>40%</td></tr>
 *   <tr><td>治疗量（攻击力的占比）</td><td>80%</td><td>100%</td><td>130%</td></tr>
 *   <tr><td>强化倍率</td><td>×0.5</td><td>×2.0</td><td>×2.0</td></tr>
 *   <tr><td>削弱倍率</td><td>×1.0</td><td>×2.5</td><td>×3.0</td></tr>
 *   <tr><td>幻影继承（难度4 才有幻影）</td><td>70%</td><td>90%</td><td>110%</td></tr>
 * </table>
 * <p><b>强化/削弱按"字面倍率"实现</b>（这是需求方明确选的）：强化登记的是<b>输出倍率</b>，
 * 所以难度1-2 的 {@code ×0.5} 会让骸物打出更低的伤害；削弱则是
 * {@code 1 − 倍率} 钳到 0，也就是难度1-2 起敌人就被扣成"完全打不出伤害"。
 * 这两条都只是 {@link Scaling} 里的两个数字，要调回"增强"语义改成 1.5 / 0.5 即可。</p>
 *
 * <h3>过载</h3>
 * <p>每次释放<b>坍缩系</b>技能有 {@code 24%} 概率进入过载，持续 <b>30 秒</b>：</p>
 * <ul>
 *   <li>期间<b>无法再释放任何坍缩系技能</b>（治疗/强化/削弱照常）；</li>
 *   <li>给骸物的<b>※减伤光环失效</b>；</li>
 *   <li>改为<b>分摊</b>：它自己受到的伤害按人数分给附近的骸物（见 {@link #hurt}）。</li>
 * </ul>
 *
 * <h3>「同类」的定义</h3>
 * <p>本类里所有"同类 / 骸物"都指<b>王庭阵营</b>（{@link RottenFaction#isHall}），
 * 包含玩家以外的任何王庭生物 —— 这是需求方确认过的口径。</p>
 */
public class HearthstoneEntity extends BaseApostleEntity {

    // ══════════════════════════════════════════════════════════════
    // 基础数值
    // ══════════════════════════════════════════════════════════════

    private static final double MAX_HEALTH = 103.0D;
    private static final double ARMOR = 7.0D;
    private static final double ATTACK_DAMAGE = 11.0D;
    private static final double MOVEMENT_SPEED = 0.23D;
    private static final double FOLLOW_RANGE = 48.0D;
    private static final double KNOCKBACK_RESISTANCE = 0.6D;

    // ══════════════════════════════════════════════════════════════
    // 光环 / 过载 / 绑定
    // ══════════════════════════════════════════════════════════════

    /** 光环半径（格）：治疗、减伤、强化、削弱共用。 */
    private static final double AURA_RADIUS = 12.0D;

    /** 每次释放坍缩系技能触发过载的概率。 */
    private static final float OVERLOAD_CHANCE = 0.24F;

    /** 过载持续（tick）：30 秒。 */
    private static final int OVERLOAD_TICKS = 30 * 20;

    /** 绑定搜索半径（格）。 */
    private static final double BIND_RADIUS = 32.0D;

    /** 绑定检查间隔（tick）。 */
    private static final int BIND_CHECK_INTERVAL = 40;

    /** 跟随绑定目标时，超过这个距离才开始主动靠近。 */
    private static final double FOLLOW_START_DISTANCE = 6.0D;

    // ══════════════════════════════════════════════════════════════
    // 同步数据（给将来的渲染/UI 用；逻辑判定一律走服务端字段）
    // ══════════════════════════════════════════════════════════════

    private static final EntityDataAccessor<Integer> DATA_OVERLOAD_UNTIL =
            SynchedEntityData.defineId(HearthstoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLLAPSE_VARIANT =
            SynchedEntityData.defineId(HearthstoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLLAPSE_UNTIL =
            SynchedEntityData.defineId(HearthstoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PHANTOM =
            SynchedEntityData.defineId(HearthstoneEntity.class, EntityDataSerializers.BOOLEAN);

    // ══════════════════════════════════════════════════════════════
    // 技能与坍缩系变体
    // ══════════════════════════════════════════════════════════════

    /** 技能表。坍缩系全部来自同一个实现，只是参数不同。 */
    public enum Skill {
        HEAL(200),
        AMPLIFY(400),
        WEAKEN(400),
        /** 王庭坍缩：标准领域。 */
        COLLAPSE(360),
        /** 坍缩治疗（难度4）：同领域，但把伤害全部变成治疗。 */
        COLLAPSE_HEAL(400),
        /** 坍缩子弹（难度4）：范围很小、跟着目标走。 */
        COLLAPSE_BULLET(300),
        /** 坍缩奇点（难度4）：范围更大。 */
        COLLAPSE_SINGULARITY(480),
        /** 坍缩崩坏（难度4）：更大更强，但自己持续掉 2% 血。 */
        COLLAPSE_BREAKDOWN(600),
        /** 幻影（难度4）：召唤 2~3 个继承本体数值的分身。 */
        PHANTOM(600),
        /** 空间移动：无视距离贴到目标身边。 */
        BLINK(200);

        private final int cooldownTicks;

        Skill(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
        }

        public int cooldownTicks() {
            return cooldownTicks;
        }

        public boolean isCollapse() {
            return this == COLLAPSE || this == COLLAPSE_HEAL || this == COLLAPSE_BULLET
                    || this == COLLAPSE_SINGULARITY || this == COLLAPSE_BREAKDOWN;
        }
    }

    /** 坍缩领域的参数。所有"坍缩X"变体都是它的一组取值。 */
    private record CollapseSpec(double radius, float damageScale, boolean healOnly,
                                float selfHealthCostPerSecond, int durationTicks,
                                boolean trackTarget, ParticleKind particle) {
    }

    /** 领域表现用的粒子风格（纯客户端表现，判定与它无关）。 */
    private enum ParticleKind {
        STANDARD, HEAL, BULLET, SINGULARITY, SMOKE
    }

    private static final CollapseSpec COLLAPSE_STANDARD =
            new CollapseSpec(8.0D, 1.0F, false, 0.0F, 100, false, ParticleKind.STANDARD);
    private static final CollapseSpec COLLAPSE_HEAL_SPEC =
            new CollapseSpec(8.0D, 1.0F, true, 0.0F, 120, false, ParticleKind.HEAL);
    private static final CollapseSpec COLLAPSE_BULLET_SPEC =
            new CollapseSpec(3.0D, 0.8F, false, 0.0F, 100, true, ParticleKind.BULLET);
    private static final CollapseSpec COLLAPSE_SINGULARITY_SPEC =
            new CollapseSpec(14.0D, 1.2F, false, 0.0F, 140, false, ParticleKind.SINGULARITY);
    private static final CollapseSpec COLLAPSE_BREAKDOWN_SPEC =
            new CollapseSpec(11.0D, 1.6F, false, 0.02F, 160, false, ParticleKind.SMOKE);

    // ══════════════════════════════════════════════════════════════
    // 难度系数
    // ══════════════════════════════════════════════════════════════

    /**
     * 一档难度的全部系数。
     *
     * <p>把数字集中在一个枚举里，而不是散在十几个 {@code if (hard) ...}` 里 ——
     * 调平衡的时候只看这一张表就够了。</p>
     */
    public enum Scaling {
        EASY(0.10F, 0.20F, 0.80F, 0.5F, 1.0F, 0.70F),
        NORMAL(0.10F, 0.20F, 0.80F, 0.5F, 1.0F, 0.70F),
        HARD(0.25F, 0.30F, 1.00F, 2.0F, 2.5F, 0.90F),
        INCOMPREHENSIBLE(0.40F, 0.40F, 1.30F, 2.0F, 3.0F, 1.10F);

        /** 自身对远程/魔法伤害的减伤比例。 */
        private final float rangedMagicReduction;
        /** ※减伤光环给骸物的减伤比例。 */
        private final float auraReduction;
        /** 治疗量 = 攻击力 × 它。 */
        private final float healOfAttack;
        /** 强化登记的「输出倍率」（按需求方的字面口径）。 */
        private final float amplifyMultiplier;
        /** 削弱的强度：敌方输出 × max(0, 1 − 它)。 */
        private final float weakenAmount;
        /** 幻影继承本体的数值比例。 */
        private final float phantomScale;

        Scaling(float rangedMagicReduction, float auraReduction, float healOfAttack,
                float amplifyMultiplier, float weakenAmount, float phantomScale) {
            this.rangedMagicReduction = rangedMagicReduction;
            this.auraReduction = auraReduction;
            this.healOfAttack = healOfAttack;
            this.amplifyMultiplier = amplifyMultiplier;
            this.weakenAmount = weakenAmount;
            this.phantomScale = phantomScale;
        }

        public float rangedMagicReduction() {
            return rangedMagicReduction;
        }

        public float auraReduction() {
            return auraReduction;
        }

        public float healOfAttack() {
            return healOfAttack;
        }

        public float amplifyMultiplier() {
            return amplifyMultiplier;
        }

        /** 敌方输出的剩余倍率：{@code max(0, 1 − weakenAmount)}。 */
        public float enemyOutgoingMultiplier() {
            return Math.max(0.0F, 1.0F - weakenAmount);
        }

        public float phantomScale() {
            return phantomScale;
        }

        /** 难度4（INCOMPREHENSIBLE）才有全部坍缩变体与幻影。 */
        public boolean hasUltimateSkills() {
            return this == INCOMPREHENSIBLE;
        }

        public static Scaling of(@Nullable DifficultyScaleProfile.Tier tier) {
            if (tier == null) {
                return NORMAL;
            }
            return switch (tier) {
                case EASY -> EASY;
                case NORMAL -> NORMAL;
                case HARD -> HARD;
                case INCOMPREHENSIBLE -> INCOMPREHENSIBLE;
            };
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 运行时状态（服务端权威）
    // ══════════════════════════════════════════════════════════════

    private final Map<Skill, Integer> cooldowns = new EnumMap<>(Skill.class);

    /** 过载到什么时候（世界时间）。0 = 没过载。 */
    private long overloadUntil;

    /** 当前领域结束时刻与参数；{@code null} = 没有领域。 */
    private long collapseUntil;
    @Nullable
    private CollapseSpec collapseSpec;
    private double collapseX;
    private double collapseY;
    private double collapseZ;
    private int collapsePulseTicks;

    /** 绑定目标（另一个王庭炉石）。 */
    @Nullable
    private UUID boundTo;

    private boolean phantom;
    private int bindCheckTicks;

    public HearthstoneEntity(@NotNull EntityType<? extends Monster> type, @NotNull Level level) {
        super(type, level);
        this.xpReward = 120;
    }

    public HearthstoneEntity(@NotNull EntityType<? extends Monster> type, @NotNull Level level,
                             @NotNull Consumer<AbstractRottenEntity> resources) {
        this(type, level);
        resources.accept(this);
    }

    /** 实体资源（geo / 贴图 / 动画）。模型资源由美术提供，路径按惯例 {@code <名字>.geo.json} 等。 */
    public static Consumer<AbstractRottenEntity> resources() {
        return EntityTypeRegistry.defaultResources("hearthstone");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractRottenEntity.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    // ══════════════════════════════════════════════════════════════
    // 同步数据
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OVERLOAD_UNTIL, 0);
        this.entityData.define(DATA_COLLAPSE_VARIANT, -1);
        this.entityData.define(DATA_COLLAPSE_UNTIL, 0);
        this.entityData.define(DATA_PHANTOM, false);
    }

    public boolean isPhantom() {
        return this.entityData.get(DATA_PHANTOM);
    }

    private void setPhantom(boolean value) {
        this.phantom = value;
        this.entityData.set(DATA_PHANTOM, value);
    }

    /** 客户端可读：过载剩余 tick（0 = 没过载）。供渲染/UI 用。 */
    public int getOverloadRemainingTicks() {
        return Math.max(0, this.entityData.get(DATA_OVERLOAD_UNTIL));
    }

    /** 客户端可读：当前领域变体序号（-1 = 没有领域）。 */
    public int getCollapseVariant() {
        return this.entityData.get(DATA_COLLAPSE_VARIANT);
    }

    // ══════════════════════════════════════════════════════════════
    // Goals
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(FOLLOW_RANGE)
                .mustSee(false)
                .filter(this)
                .build());
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    // ══════════════════════════════════════════════════════════════
    // 难度
    // ══════════════════════════════════════════════════════════════

    /** 当前世界的难度系数。每 tick 现算（难度可以被命令中途改，不缓存到字段里）。 */
    @NotNull
    public Scaling scaling() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return Scaling.NORMAL;
        }
        return Scaling.of(DifficultyScaleProfile.tierOf(
                DifficultyHelper.getDifficulty(serverLevel)));
    }

    // ══════════════════════════════════════════════════════════════
    // 主循环
    // ══════════════════════════════════════════════════════════════

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            return;
        }

        long now = this.level().getGameTime();
        tickCooldowns();
        tickOverloadDisplay(now);
        tickAura();
        tickCollapse(now);
        tickBinding(now);

        // 幻影只是复制体：不再召唤幻影、不绑定、不共享伤害
        if (!isPhantom()) {
            tryCastNextSkill(now);
        }
    }

    private void tickCooldowns() {
        for (Map.Entry<Skill, Integer> e : this.cooldowns.entrySet()) {
            if (e.getValue() > 0) {
                e.setValue(e.getValue() - 1);
            }
        }
    }

    private void tickOverloadDisplay(long now) {
        if (overloadUntil > 0 && now >= overloadUntil) {
            overloadUntil = 0;
            this.entityData.set(DATA_OVERLOAD_UNTIL, 0);
        } else if (overloadUntil > now) {
            this.entityData.set(DATA_OVERLOAD_UNTIL, (int) Math.min(Integer.MAX_VALUE, overloadUntil - now));
        }
    }

    /** 是否处于过载（服务端权威；客户端用 {@link #getOverloadRemainingTicks()}）。 */
    public boolean isOverloaded() {
        return !this.level().isClientSide() && this.level().getGameTime() < overloadUntil;
    }

    private boolean isSkillReady(Skill skill, long now) {
        return this.cooldowns.getOrDefault(skill, 0) <= 0;
    }

    private void putOnCooldown(Skill skill) {
        this.cooldowns.put(skill, skill.cooldownTicks());
    }

    // ══════════════════════════════════════════════════════════════
    // 被动①：远程 / 魔法减伤 + 过载分摊
    // ══════════════════════════════════════════════════════════════

    /**
     * 受击入口：先按难度吃远程/魔法减伤，再过载时分摊给骸物。
     *
     * <p>顺序刻意是「先减伤、后分摊」：分摊出去的是<b>已经减免过</b>的数值，
     * 否则一次远程暴击会被拆成好几份 40% 减伤后的伤害，比设计值肉太多。</p>
     */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (!this.level().isClientSide() && amount > 0.0F) {
            if (isRangedOrMagic(source)) {
                amount *= (1.0F - scaling().rangedMagicReduction());
            }
            if (isOverloaded()) {
                amount = shareDamageAmongAllies(source, amount);
            }
        }
        return super.hurt(source, amount);
    }

    /** 远程（投射物）或魔法伤害。 */
    private static boolean isRangedOrMagic(@NotNull DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO);
    }

    /**
     * 过载分摊：把这笔伤害按人头拆给附近的骸物，自己只留一份。
     *
     * <p>分摊对象刻意排除<b>其它炉石与幻影</b>：炉石之间互相分摊会形成链式调用
     * （A 分给 B → B 再分给 A），在一个 boss 群里能把伤害放大到不可预期。</p>
     *
     * @return 炉石自己还该承受的那一部分
     */
    private float shareDamageAmongAllies(@NotNull DamageSource source, float amount) {
        List<LivingEntity> allies = new ArrayList<>();
        for (LivingEntity candidate : alliesInRange(AURA_RADIUS)) {
            if (!(candidate instanceof HearthstoneEntity)) {
                allies.add(candidate);
            }
        }
        if (allies.isEmpty()) {
            return amount;
        }
        float share = amount / (allies.size() + 1);
        for (LivingEntity ally : allies) {
            // 用间接魔法来源：不带 owner，避免"谁打的"归因错乱
            ally.hurt(this.damageSources().indirectMagic(this, this), share);
        }
        return share;
    }

    // ══════════════════════════════════════════════════════════════
    // 被动②：※减伤光环（过载时失效）
    // ══════════════════════════════════════════════════════════════

    private void tickAura() {
        // 过载 → 光环失效（登记自然过期就够了，这里显式不登记）
        if (isOverloaded()) {
            return;
        }
        float reduction = scaling().auraReduction();
        for (LivingEntity ally : alliesInRange(AURA_RADIUS)) {
            HearthstoneAuraEvents.setIncomingReduction(ally, reduction);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 被动③：与同类绑定 —— 同步速度并跟随
    // ══════════════════════════════════════════════════════════════

    private void tickBinding(long now) {
        if (isPhantom()) {
            return;
        }
        if (this.bindCheckTicks > 0) {
            this.bindCheckTicks--;
        } else {
            this.bindCheckTicks = BIND_CHECK_INTERVAL;
            refreshBinding();
        }

        HearthstoneEntity partner = boundPartner();
        if (partner == null) {
            return;
        }
        // 速度与绑定目标一致：直接抄它的 MOVEMENT_SPEED 基值
        AttributeInstance mine = this.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance theirs = partner.getAttribute(Attributes.MOVEMENT_SPEED);
        if (mine != null && theirs != null && Math.abs(mine.getBaseValue() - theirs.getBaseValue()) > 1.0E-4) {
            mine.setBaseValue(theirs.getBaseValue());
        }
        // 跟随：没有攻击目标时朝绑定目标走
        if (this.getTarget() == null && this.distanceToSqr(partner) > FOLLOW_START_DISTANCE * FOLLOW_START_DISTANCE) {
            this.getNavigation().moveTo(partner, 1.0D);
        }
    }

    /** 每 {@value #BIND_CHECK_INTERVAL} tick 检查一次：绑定目标还在不在，不在就重新找一个。 */
    private void refreshBinding() {
        HearthstoneEntity partner = boundPartner();
        if (partner != null) {
            return;
        }
        this.boundTo = null;

        HearthstoneEntity best = null;
        double bestDistance = BIND_RADIUS * BIND_RADIUS;
        for (HearthstoneEntity other : this.level().getEntitiesOfClass(HearthstoneEntity.class,
                this.getBoundingBox().inflate(BIND_RADIUS))) {
            if (other == this || other.isPhantom() || !other.isAlive()) {
                continue;
            }
            double d = this.distanceToSqr(other);
            if (d < bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        if (best != null) {
            this.boundTo = best.getUUID();
        }
    }

    @Nullable
    private HearthstoneEntity boundPartner() {
        if (this.boundTo == null) {
            return null;
        }
        for (HearthstoneEntity other : this.level().getEntitiesOfClass(HearthstoneEntity.class,
                this.getBoundingBox().inflate(BIND_RADIUS * 2.0D))) {
            if (other.getUUID().equals(this.boundTo) && other.isAlive()) {
                return other;
            }
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // 技能调度
    // ══════════════════════════════════════════════════════════════

    private void tryCastNextSkill(long now) {
        if (this.isDeadOrDying() || this.isNoAi()) {
            return;
        }
        LivingEntity target = this.getTarget();
        boolean hasEnemy = target != null && target.isAlive();

        // 优先级：先保命（治疗）→ 强化 → 削弱 → 空间移动 → 坍缩系（过载时跳过）→ 幻影
        if (isSkillReady(Skill.HEAL, now) && hasWoundedAlly()) {
            castHeal();
            return;
        }
        if (isSkillReady(Skill.AMPLIFY, now) && hasAlly()) {
            castAmplify(now);
            return;
        }
        if (isSkillReady(Skill.WEAKEN, now) && hasEnemyNearby()) {
            castWeaken(now);
            return;
        }
        if (isSkillReady(Skill.BLINK, now) && hasEnemy && this.distanceToSqr(target) > 64.0D) {
            castBlink(target);
            return;
        }
        if (!isOverloaded()) {
            if (isSkillReady(Skill.COLLAPSE_BREAKDOWN, now) && hasEnemyNearby()
                    && scaling().hasUltimateSkills()) {
                castCollapse(Skill.COLLAPSE_BREAKDOWN, COLLAPSE_BREAKDOWN_SPEC, now);
                return;
            }
            if (isSkillReady(Skill.COLLAPSE_SINGULARITY, now) && hasEnemyNearby()
                    && scaling().hasUltimateSkills()) {
                castCollapse(Skill.COLLAPSE_SINGULARITY, COLLAPSE_SINGULARITY_SPEC, now);
                return;
            }
            if (isSkillReady(Skill.COLLAPSE_HEAL, now) && scaling().hasUltimateSkills() && hasWoundedAlly()) {
                castCollapse(Skill.COLLAPSE_HEAL, COLLAPSE_HEAL_SPEC, now);
                return;
            }
            if (isSkillReady(Skill.COLLAPSE_BULLET, now) && hasEnemy && scaling().hasUltimateSkills()) {
                castCollapse(Skill.COLLAPSE_BULLET, COLLAPSE_BULLET_SPEC, now);
                return;
            }
            if (isSkillReady(Skill.COLLAPSE, now) && hasEnemyNearby()) {
                castCollapse(Skill.COLLAPSE, COLLAPSE_STANDARD, now);
                return;
            }
        }
        if (isSkillReady(Skill.PHANTOM, now) && scaling().hasUltimateSkills() && hasEnemyNearby()) {
            castPhantoms();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 技能实现
    // ══════════════════════════════════════════════════════════════

    /** 治疗：对范围内骸物恢复「攻击力 × 难度系数」。 */
    private void castHeal() {
        float heal = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * scaling().healOfAttack();
        int healed = 0;
        for (LivingEntity ally : alliesInRange(AURA_RADIUS)) {
            if (!ally.isAlive() || ally.getHealth() >= ally.getMaxHealth()) {
                continue;
            }
            ally.heal(heal);
            healed++;
        }
        putOnCooldown(Skill.HEAL);
        if (healed > 0) {
            playSoundAt(SoundEvents.SCULK_CATALYST_BLOOM, 0.9F, 1.2F);
            burst(ParticleTypes.SCULK_SOUL, AURA_RADIUS, 18);
        }
    }

    /** 强化：给范围内骸物上 buff，并登记输出倍率。 */
    private void castAmplify(long now) {
        Scaling s = scaling();
        int duration = 200;
        int affected = 0;
        for (LivingEntity ally : alliesInRange(AURA_RADIUS)) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 1));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0));
            HearthstoneAuraEvents.setOutgoingMultiplier(ally, s.amplifyMultiplier());
            affected++;
        }
        putOnCooldown(Skill.AMPLIFY);
        if (affected > 0) {
            playSoundAt(SoundEvents.SCULK_BLOCK_CHARGE, 0.8F, 1.4F);
        }
    }

    /** 削弱：给范围内非骸物上 debuff，并把输出压到 {@code max(0, 1 − 强度)}。 */
    private void castWeaken(long now) {
        Scaling s = scaling();
        int duration = 200;
        int affected = 0;
        for (LivingEntity enemy : enemiesInRange(AURA_RADIUS)) {
            enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1));
            HearthstoneAuraEvents.setOutgoingMultiplier(enemy, s.enemyOutgoingMultiplier());
            affected++;
        }
        putOnCooldown(Skill.WEAKEN);
        if (affected > 0) {
            playSoundAt(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7F, 0.8F);
        }
    }

    /** 空间移动：无视距离贴到目标身边。 */
    private void castBlink(@NotNull LivingEntity target) {
        Vec3 behind = target.position().subtract(target.getLookAngle().scale(2.0D));
        this.teleportTo(behind.x, target.getY(), behind.z);
        this.getNavigation().stop();
        putOnCooldown(Skill.BLINK);
        playSoundAt(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.7F);
    }

    /** 幻影：召唤 2~3 个继承本体数值的分身。 */
    private void castPhantoms() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        float scale = scaling().phantomScale();
        int count = 2 + this.random.nextInt(2);   // 2~3
        for (int i = 0; i < count; i++) {
            HearthstoneEntity clone = new HearthstoneEntity(
                    EntityTypeRegistry.HEARTHSTONE.get(), serverLevel, resources());
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double offset = 2.0D + this.random.nextDouble() * 2.0D;
            clone.moveTo(this.getX() + Math.cos(angle) * offset, this.getY(),
                    this.getZ() + Math.sin(angle) * offset, this.getYRot(), 0.0F);
            clone.setPhantom(true);
            clone.scaleAttributes(scale);
            clone.setTarget(this.getTarget());
            serverLevel.addFreshEntity(clone);
        }
        putOnCooldown(Skill.PHANTOM);
        playSoundAt(SoundEvents.WARDEN_ROAR, 0.6F, 1.3F);
    }

    /** 按比例缩放关键属性（幻影用）。 */
    private void scaleAttributes(float scale) {
        setBaseValueIfPresent(Attributes.MAX_HEALTH, MAX_HEALTH * scale);
        setBaseValueIfPresent(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE * scale);
        setBaseValueIfPresent(Attributes.ARMOR, ARMOR * scale);
        this.setHealth(this.getMaxHealth());
    }

    private void setBaseValueIfPresent(@NotNull net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                       double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 王庭坍缩（所有坍缩变体共用一个实现）
    // ══════════════════════════════════════════════════════════════

    /**
     * 释放一个坍缩领域。
     *
     * <p>「王庭坍缩」与它的四个难度4变体在判定上<b>完全一样</b>，区别只有
     * {@link CollapseSpec} 里的几个参数（半径、倍率、是否只治疗、是否跟人、自损）。
     * 所以这里只写一份，变体靠参数区分 —— 否则五个几乎相同的 {@code cast} 方法
     * 迟早会调平衡时改漏其中一个。</p>
     */
    private void castCollapse(@NotNull Skill skill, @NotNull CollapseSpec spec, long now) {
        this.collapseSpec = spec;
        this.collapseUntil = now + spec.durationTicks();
        this.collapsePulseTicks = 0;

        Vec3 center = this.position();
        LivingEntity target = this.getTarget();
        if (spec.trackTarget() && target != null) {
            center = target.position();
        }
        this.collapseX = center.x;
        this.collapseY = center.y;
        this.collapseZ = center.z;

        putOnCooldown(skill);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                    this.collapseX, this.collapseY + 1.0D, this.collapseZ, 1, 0, 0, 0, 0);
        }
        playSoundAt(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 0.9F);

        // 过载判定：每次释放坍缩系都有概率触发
        if (skill.isCollapse() && !isPhantom() && this.random.nextFloat() < OVERLOAD_CHANCE) {
            triggerOverload(now);
        }
    }

    private void triggerOverload(long now) {
        this.overloadUntil = now + OVERLOAD_TICKS;
        this.entityData.set(DATA_OVERLOAD_UNTIL, OVERLOAD_TICKS);
        playSoundAt(SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0F, 0.6F);
    }

    /** 领域每 tick 结算：按 10 tick 一拍造成伤害/治疗，同时维护控制效果。 */
    private void tickCollapse(long now) {
        if (this.collapseSpec == null) {
            return;
        }
        if (now >= this.collapseUntil || this.isDeadOrDying()) {
            this.collapseSpec = null;
            this.entityData.set(DATA_COLLAPSE_VARIANT, -1);
            return;
        }
        CollapseSpec spec = this.collapseSpec;

        // 子弹变体：跟着目标走
        LivingEntity target = this.getTarget();
        if (spec.trackTarget() && target != null && target.isAlive()) {
            this.collapseX = target.getX();
            this.collapseY = target.getY();
            this.collapseZ = target.getZ();
        }

        // 自损（坍缩崩坏）：每秒扣 2% 最大生命，且不能被自己的减伤吃掉
        if (spec.selfHealthCostPerSecond() > 0.0F && now % 20 == 0) {
            float cost = this.getMaxHealth() * spec.selfHealthCostPerSecond();
            this.setHealth(Math.max(1.0F, this.getHealth() - cost));
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        this.getX(), this.getY() + 1.0D, this.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
            }
        }

        spawnCollapseParticles(spec);

        if (++this.collapsePulseTicks < 10) {
            return;
        }
        this.collapsePulseTicks = 0;

        Vec3 center = new Vec3(this.collapseX, this.collapseY, this.collapseZ);
        AABB box = new AABB(center, center).inflate(spec.radius());

        // ① 对骸物：持续回血 + buff
        for (LivingEntity ally : this.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> isHallFaction(e) && e.isAlive())) {
            if (ally.getBoundingBox().distanceToSqr(center) > spec.radius() * spec.radius()) {
                continue;
            }
            ally.heal((float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.4F);
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0));
        }

        // ② 对非骸物：坍缩治疗变体只治疗、不伤害；其它变体造成真伤/虚空/窒息
        if (spec.healOnly()) {
            return;
        }
        for (LivingEntity enemy : this.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> !isHallFaction(e) && e.isAlive() && !e.isSpectator())) {
            if (enemy.getBoundingBox().distanceToSqr(center) > spec.radius() * spec.radius()) {
                continue;
            }
            float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * 3.0F * spec.damageScale();
            applyCollapseDamage(enemy, damage);
            // "无法移动、无法攻击"：重度缓慢 + 重度虚弱
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6));
            enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 6));
            enemy.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 4));
        }
    }

    /**
     * 坍缩伤害：真伤 / 虚空 / 窒息三选一（轮流），无视护甲。
     *
     * <p>用 {@code BCCoreCompat} 直接写血那套太重，这里走原版的
     * {@code BYPASSES_ARMOR} 系来源 —— 三种类型本来就无视护甲，
     * 而且保留无敌帧与击杀归因，行为更符合原版预期。</p>
     */
    private void applyCollapseDamage(@NotNull LivingEntity enemy, float damage) {
        DamageSource source = switch (this.random.nextInt(3)) {
            case 0 -> this.damageSources().magic();
            case 1 -> this.damageSources().indirectMagic(this, this);
            default -> this.damageSources().sonicBoom(this);
        };
        enemy.invulnerableTime = 0;
        enemy.hurt(source, damage);
    }

    private void spawnCollapseParticles(@NotNull CollapseSpec spec) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double y = this.collapseY + 0.2D;
        switch (spec.particle()) {
            case HEAL -> serverLevel.sendParticles(ParticleTypes.HEART,
                    this.collapseX, y, this.collapseZ, 4, spec.radius() * 0.4D, 0.4D, spec.radius() * 0.4D, 0.02);
            case BULLET -> serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.collapseX, y, this.collapseZ, 6, 0.3D, 0.3D, 0.3D, 0.02);
            case SINGULARITY -> {
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        this.collapseX, y, this.collapseZ, 12, spec.radius() * 0.5D, 0.6D, spec.radius() * 0.5D, 0.05);
                serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        this.collapseX, y, this.collapseZ, 8, spec.radius() * 0.3D, 0.5D, spec.radius() * 0.3D, 0.02);
            }
            case SMOKE -> serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.collapseX, y, this.collapseZ, 10, spec.radius() * 0.6D, 0.8D, spec.radius() * 0.6D, 0.01);
            default -> serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                    this.collapseX, y, this.collapseZ, 6, spec.radius() * 0.5D, 0.5D, spec.radius() * 0.5D, 0.02);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════════

    /** 某个生物是不是王庭阵营（"同类 / 骸物"的唯一口径）。 */
    public static boolean isHallFaction(@Nullable Entity entity) {
        return entity instanceof IInfectedEntity infected
                && RottenFaction.isHall(infected.getInfectionType());
    }

    /** 范围内活着的骸物（不含自己）。 */
    @NotNull
    private List<LivingEntity> alliesInRange(double radius) {
        return this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                e -> e != this && e.isAlive() && !e.isSpectator()
                        && isHallFaction(e)
                        && e.getBoundingBox().distanceToSqr(this.getBoundingBox().getCenter())
                        <= radius * radius);
    }

    /** 范围内活着的非骸物（玩家与别的阵营生物）。 */
    @NotNull
    private List<LivingEntity> enemiesInRange(double radius) {
        return this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                e -> e != this && e.isAlive() && !e.isSpectator()
                        && !isHallFaction(e)
                        && e.getBoundingBox().distanceToSqr(this.getBoundingBox().getCenter())
                        <= radius * radius);
    }

    private boolean hasAlly() {
        return !alliesInRange(AURA_RADIUS).isEmpty();
    }

    private boolean hasWoundedAlly() {
        for (LivingEntity ally : alliesInRange(AURA_RADIUS)) {
            if (ally.getHealth() < ally.getMaxHealth()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEnemyNearby() {
        return !enemiesInRange(AURA_RADIUS).isEmpty();
    }

    private void playSoundAt(@NotNull net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound,
                SoundSource.HOSTILE, volume, pitch);
    }

    private void burst(@NotNull net.minecraft.core.particles.SimpleParticleType particle,
                       double radius, int count) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particle, this.getX(), this.getY() + 1.0D, this.getZ(),
                    count, radius * 0.4D, 0.6D, radius * 0.4D, 0.02);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 存档
    // ══════════════════════════════════════════════════════════════

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("HearthstonePhantom", this.phantom);
        tag.putLong("HearthstoneOverloadUntil", this.overloadUntil);
        if (this.boundTo != null) {
            tag.putUUID("HearthstoneBoundTo", this.boundTo);
        }
        for (Map.Entry<Skill, Integer> e : this.cooldowns.entrySet()) {
            tag.putInt("HearthstoneCd_" + e.getKey().name(), e.getValue());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPhantom(tag.getBoolean("HearthstonePhantom"));
        this.overloadUntil = tag.getLong("HearthstoneOverloadUntil");
        this.boundTo = tag.hasUUID("HearthstoneBoundTo") ? tag.getUUID("HearthstoneBoundTo") : null;
        for (Skill skill : Skill.values()) {
            String key = "HearthstoneCd_" + skill.name();
            if (tag.contains(key)) {
                this.cooldowns.put(skill, tag.getInt(key));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 动画
    // ══════════════════════════════════════════════════════════════

    /**
     * 动画控制器交给基类。
     *
     * <p>模型还没做，先不注册任何自定义控制器 —— 基类的 {@code defaultControllersEnabled}
     * 会处理移动/待机；等美术给了动画文件后，在这里补一段 {@code AnimationController}
     * 播施法动作即可。</p>
     */
    @Override
    public void registerControllers(
            software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar registrar) {
        super.registerControllers(registrar);
    }
}
