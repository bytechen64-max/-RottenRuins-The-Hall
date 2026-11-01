package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.linked;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.manager.CorruptionSpreadHelper;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseLinkedEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 召唤柱（联结种站桩「兵营」）。
 *
 * <p>技能：<b>C12 群体召唤</b> —— 周期性从柱体中拉出同化种增援。
 * 三重限制避免无限刷怪：区域内同族数量上限、单次召唤数量上限、长冷却；
 * 人口已满时改为给现有部队上抗性。死亡时留下腐蚀领域（C9）。</p>
 */
public class SummoningPillarEntity extends BaseLinkedEntity {

    // =========================================================================
    // C12 群体召唤参数
    // =========================================================================
    private static final int SUMMON_COOLDOWN = 240;
    private static final int SUMMON_COUNT = 2;
    private static final int LOCAL_POPULATION_CAP = 10;
    private static final double POPULATION_RADIUS = 20.0;
    private static final double SPAWN_SPREAD = 2.5;

    /** 首轮召唤的延迟，避免刚加载就刷 */
    private int summonCooldown = 100;

    /** 腐植柱守卫检查计时 */
    private int guardTimer = 100;

    /** 脚下方块感染 / 扩散计时 */
    private int spreadTimer = 20;

    public SummoningPillarEntity(EntityType<? extends SummoningPillarEntity> entityType, Level level,
                                 Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.0F);
        this.setRunSpeed(0.0F);
        // 站桩单位不需要假死演出
        this.setFakeDeathEnabled(false);
        // C9 辐照领域：作为兵营，被拆掉时留下一片污染
        this.enableDeathField(3.5F, 160, 3.0F);
        this.guardTimer = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // =========================================================================
    // C12 群体召唤
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        // 常驻回血：每 20t 恢复 1 点
        if (this.tickCount % 20 == 0) {
            this.heal(1.0F);
        }
        // 一阶召唤柱：维持身边 2 个腐植柱守卫
        tickHumusGuards();

        // 腐化群系：直接感染脚下方块，并向周围扩散；区域登记后腐化生物会自然生成
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.spreadTimer > 0) {
                this.spreadTimer--;
            } else {
                this.spreadTimer = 20;
                CorruptionSpreadHelper.spreadArea(serverLevel, this.blockPosition().below(), 4, 8);
                CorruptionSpreadHelper.spreadArea(serverLevel, this.blockPosition(), 6, 4);
                CorruptionSkillFx.pillar(this.level(), this.position(), 1.2,
                        CorruptionSkillUtil.CORRUPTION_RED, 8);
            }
        }

        if (summonCooldown > 0) {
            summonCooldown--;
            // 冷却末尾的蓄势表现：柱子开始发光、粒子变密
            if (summonCooldown < 40 && this.tickCount % 5 == 0) {
                CorruptionSkillUtil.chargeIn(this, 1.8, 3, 1.0F - summonCooldown / 40.0F);
            }
            return;
        }

        // 只有在附近有敌人时才召唤，避免在无战斗状态下无限刷怪
        if (this.getTarget() == null || !this.getTarget().isAlive()) return;

        int population = countLocalPopulation();
        if (population >= LOCAL_POPULATION_CAP) {
            // 人口已满：改为给现有部队上增益，仍然有战术价值
            CorruptionSkillUtil.buffAllies(this, POPULATION_RADIUS, MobEffects.DAMAGE_RESISTANCE, 200, 0);
            summonCooldown = SUMMON_COOLDOWN / 2;
            return;
        }

        performSummon(Math.min(SUMMON_COUNT, LOCAL_POPULATION_CAP - population));
        summonCooldown = SUMMON_COOLDOWN;
    }

    /** 一阶召唤柱：在自身周围维持 2 个腐植柱守卫（死亡后补召）。 */
    private void tickHumusGuards() {
        if (guardTimer > 0) {
            guardTimer--;
            return;
        }
        guardTimer = 100;

        int existing = 0;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(16.0))) {
            if (e instanceof org.bytechen.rottenruins.overworld.registry.entities.population.corruption.linked.HumusColumnEntity) {
                existing++;
            }
        }
        if (existing >= 2) return;

        for (int i = existing; i < 2; i++) {
            var humus = CorruptionEntityTypes.HUMUS_COLUMN.get().create(this.level());
            if (humus == null) continue;

            double angle = (Math.PI * 2 / 2) * i + this.random.nextDouble() * 0.8;
            double r = 2.2 + this.random.nextDouble() * 1.2;
            humus.moveTo(this.getX() + Math.cos(angle) * r, this.getY(),
                    this.getZ() + Math.sin(angle) * r,
                    this.random.nextFloat() * 360.0F, 0.0F);
            humus.setPersistenceRequired();
            if (this.getTarget() != null) humus.setTarget(this.getTarget());
            this.level().addFreshEntity(humus);

            CorruptionSkillFx.pillar(this.level(), humus.position(), 2.0,
                    CorruptionSkillUtil.CORRUPTION_RED, 16);
        }

        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.1F, 0.1F);
    }

    /** 统计半径内的同族数量（含自己）。 */
    private int countLocalPopulation() {
        AABB area = this.getBoundingBox().inflate(POPULATION_RADIUS);
        int allies = 0;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (CorruptionSkillUtil.isCorruptionAlly(e)) allies++;
        }
        return 1 + allies;
    }

    /**
     * 召唤增援。
     * <p>召唤表按"廉价同化种"排列（僵尸 40% / 鸡 30% / 狼 20% / 骷髅 10%），
     * 不召唤重构种与腐化种的高威胁单位。</p>
     */
    private void performSummon(int count) {
        CorruptionSkillFx.pillar(this.level(), this.position(), 3.0,
                CorruptionSkillUtil.CORRUPTION_RED, 24);
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 3.0,
                CorruptionSkillUtil.CORRUPTION_RED, 28);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.EVOKER_CAST_SPELL, 1.2F, 0.8F, 0.1F);

        for (int i = 0; i < count; i++) {
            EntityType<? extends PathfinderMob> type = pickSummonType();
            if (type == null) continue;

            var spawn = type.create(this.level());
            if (spawn == null) continue;

            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = 1.5 + this.random.nextDouble() * SPAWN_SPREAD;
            double sx = this.getX() + Math.cos(angle) * r;
            double sz = this.getZ() + Math.sin(angle) * r;

            spawn.moveTo(sx, this.getY(), sz, this.random.nextFloat() * 360.0F, 0);
            if (this.getTarget() != null) {
                spawn.setTarget(this.getTarget());
            }

            CorruptionSkillFx.pillar(this.level(), new Vec3(sx, this.getY(), sz), 2.0,
                    CorruptionSkillUtil.CORRUPTION_RED, 14);
            this.level().addFreshEntity(spawn);
        }
    }

    /** 挑选召唤物：优先从更"廉价"的同化种里选。 */
    @Nullable
    private EntityType<? extends PathfinderMob> pickSummonType() {
        int roll = this.random.nextInt(100);

        if (roll < 40) {
            return CorruptionEntityTypes.CORRUPTION_ZOMBIE.get();
        } else if (roll < 70) {
            return CorruptionEntityTypes.CORRUPTION_CHICKEN.get();
        } else if (roll < 90) {
            return CorruptionEntityTypes.CORRUPTION_WOLF.get();
        } else {
            return CorruptionEntityTypes.CORRUPTION_SKELETON.get();
        }
    }

    /** 禁止被其他实体推动。 */
    @Override
    public void push(Entity entity) {
    }

    /** 站桩单位不应移动。 */
    @Override
    public void travel(Vec3 travelVector) {
        super.travel(Vec3.ZERO);
    }
}
