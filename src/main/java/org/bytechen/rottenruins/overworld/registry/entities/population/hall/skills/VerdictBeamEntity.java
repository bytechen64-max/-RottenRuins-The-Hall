package org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.items.verdict.HeightFactor;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictDamage;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictFeedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 天穹裁决的垂直光柱：实体位置 = 光柱底面中心，沿 +Y 延伸 {@link #getLength()} 格、半径 {@link #getRadius()} 格。
 * 三波伤害由实体自持（不另设调度器），第二波才能与下扫球同帧；实体被卸载则未结算的波次一起消失。
 */
public class VerdictBeamEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LENGTH =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CASTER =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.INT);
    /** 扩散终点半径（格）；起点是 {@link #getRadius()}。 */
    private static final EntityDataAccessor<Float> DATA_TARGET_RADIUS =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GROW_TICKS =
            SynchedEntityData.defineId(VerdictBeamEntity.class, EntityDataSerializers.INT);

    /** 默认半径（格）。地表值，会被高度系数与裁决层数放大。 */
    public static final float DEFAULT_RADIUS = 2.6f;
    /** 默认长度（格）：抬头 90° 的满长度。 */
    public static final float DEFAULT_LENGTH = 44.0f;
    /**
     * 扩散终点半径（格）：落柱后一路涨到它再停住。20 格远大于领域（6~11 格），
     * 且让柱子把玩家整个罩住 —— 背面剔除因此恒走"柱内"分支（{@code uBackCull = 0}）。
     */
    public static final float DEFAULT_TARGET_RADIUS = 20.0f;
    /** 扩散完成所需 tick = 1.5 秒。太快读不出"扩散"，太慢就成了慢慢胀。 */
    public static final int DEFAULT_GROW_TICKS = 30;

    /** 默认存活 tick = 12 秒：扩散 + 通天都需要够长的窗口才看得见。 */
    public static final int DEFAULT_MAX_AGE = 240;

    /** 淡入 / 淡出时长（tick）：3 → 26，刻意不对称 —— 瞬间降下、缓慢退去。 */
    public static final float FADE_IN_TICKS = 3f;
    public static final float FADE_OUT_TICKS = 26f;

    /** 三波打击的触发 tick：分别落在扩散的起始 / 中段 / 完成。 */
    public static final int WAVE1_TICK = 0;
    public static final int WAVE2_TICK = 10;
    public static final int WAVE3_TICK = DEFAULT_GROW_TICKS;

    /** 后两波的横向收紧系数（相对<b>当时</b>的扩散半径）：避免"边缘蹭到"也按满半径结算。 */
    private static final double WAVE2_RADIUS_SCALE = 0.78;
    private static final double WAVE3_RADIUS_SCALE = 0.62;

    /** 单波最多处理的目标数（性能闸门）：超出部分直接不打，让伤害在人特别多时自然饱和。 */
    private static final int MAX_TARGETS_PER_WAVE = 20;

    private int age;
    private boolean wave1Done;
    private boolean wave2Done;
    private boolean wave3Done;

    public VerdictBeamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;                      // 光柱很长，包围盒剔除会误杀
    }

    @Override
    public void defineSynchedData() {
        entityData.define(DATA_RADIUS, DEFAULT_RADIUS);
        entityData.define(DATA_LENGTH, DEFAULT_LENGTH);
        entityData.define(DATA_MAX_AGE, DEFAULT_MAX_AGE);
        entityData.define(DATA_CASTER, -1);
        entityData.define(DATA_TARGET_RADIUS, DEFAULT_TARGET_RADIUS);
        entityData.define(DATA_GROW_TICKS, DEFAULT_GROW_TICKS);
    }

    /** <b>起始</b>半径（格）。当前半径要用 {@link #currentRadius}。 */
    public float getRadius()  { return entityData.get(DATA_RADIUS); }
    public float getLength()  { return entityData.get(DATA_LENGTH); }
    public int   getMaxAge()  { return entityData.get(DATA_MAX_AGE); }
    public int   getAge()     { return age; }
    public float getTargetRadius() { return entityData.get(DATA_TARGET_RADIUS); }
    public int   getGrowTicks()    { return entityData.get(DATA_GROW_TICKS); }

    public void setRadius(float v) { entityData.set(DATA_RADIUS, Math.max(0.05f, v)); }
    public void setLength(float v) { entityData.set(DATA_LENGTH, Math.max(0.5f, v)); }
    public void setMaxAge(int v)   { entityData.set(DATA_MAX_AGE, Math.max(1, v)); }
    public void setTargetRadius(float v) { entityData.set(DATA_TARGET_RADIUS, Math.max(0.5f, v)); }
    public void setGrowTicks(int v) { entityData.set(DATA_GROW_TICKS, Math.max(1, v)); }

    /** 扩散进度 0 → 1，走 {@code sqrt}（前快后慢）：线性会读成"匀速膨胀的气球"。 */
    public float growProgress(float partialTick) {
        int gt = getGrowTicks();
        if (gt <= 0) return 1f;
        float t = Mth.clamp((age + Math.max(0f, partialTick)) / gt, 0f, 1f);
        return Mth.sqrt(t);
    }

    /** 当前半径 = 起始 +（终点 − 起始）× 扩散进度。 */
    public float currentRadius(float partialTick) {
        float g = growProgress(partialTick);
        return getRadius() + (getTargetRadius() - getRadius()) * g;
    }

    /** 当前<b>视觉</b>高度；纵向单独封顶在 {@link #MAX_HEIGHT_SCALE}，再放大会越过远裁剪面。 */
    public float currentLength(float partialTick) {
        return getLength() * currentHeightScale(partialTick);
    }

    /** 扩散期间的纵向放大系数。恒为 {@value #MAX_HEIGHT_SCALE}：起始高度已 440 格，再涨会超出可见范围。 */
    public float currentHeightScale(float partialTick) {
        return MAX_HEIGHT_SCALE;
    }

    public static final float MAX_HEIGHT_SCALE = 1.0f;

    /** 施法者。存 entity id 而不是 UUID：伤害结算只需要在当前世界里拿到那个人。 */
    public void setCaster(@Nullable Player player) {
        entityData.set(DATA_CASTER, player == null ? -1 : player.getId());
    }

    @Nullable
    public Player getCaster() {
        int id = entityData.get(DATA_CASTER);
        if (id < 0) return null;
        return level().getEntity(id) instanceof Player p ? p : null;
    }

    /** 0 → 1 的存活进度，带 partialTick 插值。 */
    public float lifeProgress(float partialTick) {
        int max = getMaxAge();
        if (max <= 0) return 1f;
        return Math.min((age + Math.max(0f, partialTick)) / max, 1f);
    }

    /** 当前强度 0~1：淡入极快、淡出很慢（{@value #FADE_IN_TICKS} / {@value #FADE_OUT_TICKS} tick），反过来会显得软。 */
    public float intensity(float partialTick) {
        float at = age + Math.max(0f, partialTick);
        float in = Math.min(at / FADE_IN_TICKS, 1f);
        int max = getMaxAge();
        float out = Math.min(Math.max(0f, (max - at) / FADE_OUT_TICKS), 1f);
        return in * out;
    }

    @Override
    public void tick() {
        age++;
        if (level().isClientSide()) return;

        // 三波伤害都在服务端结算
        if (!wave1Done && age >= WAVE1_TICK) {
            wave1Done = true;
            strikeWave(1.0);
        }
        if (!wave2Done && age >= WAVE2_TICK) {
            wave2Done = true;
            strikeWave(WAVE2_RADIUS_SCALE);
        }
        // 第三波落在"扩散完成"那一刻：那时柱子最宽，该结算的范围也最大
        if (!wave3Done && age >= WAVE3_TICK) {
            wave3Done = true;
            strikeWave(WAVE3_RADIUS_SCALE);
        }

        if (age >= getMaxAge()) discard();
    }

    /** 结算一波裁决：半径取"当时"的扩散半径；判定高度另有上限（{@link HeightFactor#BEAM_HIT_MAX_HEIGHT}）。 */
    private void strikeWave(double radiusScale) {
        Player caster = getCaster();
        float radius = currentRadius(0f) * (float) radiusScale;
        float hitHeight = HeightFactor.beamHitHeight(getLength());
        AABB box = new AABB(
                getX() - radius, getY(), getZ() - radius,
                getX() + radius, getY() + hitHeight, getZ() + radius);

        List<LivingEntity> hits = new ArrayList<>(
                level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e.isAlive() && !e.isSpectator()
                                && (caster == null || !e.getUUID().equals(caster.getUUID()))));

        // 性能闸门：极端场景能捞到几十个实体，与其让一次技能造成几百点伤害，不如让它在这里饱和。
        if (hits.size() > MAX_TARGETS_PER_WAVE) {
            hits = hits.subList(0, MAX_TARGETS_PER_WAVE);
        }

        // 伤害按波次取：第一波最重，后两波是"扩散到更大范围之后的追加判定"。
        boolean first = radiusScale >= 1.0;
        float hurt = first ? VerdictDamage.BEAM_HURT_WAVE1 : VerdictDamage.BEAM_HURT_WAVE2;
        float bypass = first ? VerdictDamage.BEAM_BYPASS_WAVE1 : VerdictDamage.BEAM_BYPASS_WAVE2;

        int landed = 0;
        var source = caster != null ? caster.damageSources().playerAttack(caster)
                                    : level().damageSources().magic();
        for (LivingEntity target : hits) {
            if (!target.isAlive()) continue;
            if (VerdictDamage.strike(target, source, hurt, bypass, caster)) {
                VerdictFeedback.landed(target, VerdictFeedback.weightOf(hits.size()));
                landed++;
            }
        }

        if (landed > 0) {
            // 命中越多音越高：玩家不用看屏幕就知道这一柱扫到了几个人
            VerdictFeedback.playSound(level(), position(),
                    SoundEvents.PLAYER_ATTACK_CRIT, 0.55f, VerdictFeedback.hitPitch(landed));
        }

        // 落地冲击只给第一波（"落柱"的那一下），后两波是扩散的余韵
        if (first) {
            Vec3 base = position();
            VerdictFeedback.impactRing(level(), base.add(0, 0.05, 0),
                    Math.max(3.0f, getRadius() * 1.35f), 4.2f, 0.9f);
            VerdictFeedback.burst(level(), base.add(0, 0.4, 0),
                    ParticleTypes.END_ROD, 26, 0.22);
            VerdictFeedback.burst(level(), base.add(0, 0.15, 0),
                    ParticleTypes.CLOUD, 14, 0.05);

            // 相机：落柱那一刻的一次短促后坐（离得越远越轻）
            if (caster != null) {
                float w = VerdictFeedback.weightOf(Math.max(1, landed));
                VerdictFeedback.shakeNear(caster, base, 0.55f + 0.35f * w);
                VerdictFeedback.fovKick(caster, -2.2f);      // 负号 = 视野收紧 = 被压住
            }
        } else if (caster != null && landed > 0) {
            // 后两波：一次轻微抖动作为"柱子又推宽了一圈"的触觉回执；冲击环半径跟当时的扩散半径走。
            VerdictFeedback.shakeNear(caster, position(), 0.28f);
            VerdictFeedback.impactRing(level(), position().add(0, 0.05, 0),
                    Math.max(2.0f, radius * 1.15f), 6.5f, 0.5f);
        }
    }

    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override public boolean isPickable()   { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        wave1Done = tag.getBoolean("Wave1");
        wave2Done = tag.getBoolean("Wave2");
        wave3Done = tag.getBoolean("Wave3");
        if (tag.contains("Radius")) setRadius(tag.getFloat("Radius"));
        if (tag.contains("Length")) setLength(tag.getFloat("Length"));
        if (tag.contains("MaxAge")) setMaxAge(tag.getInt("MaxAge"));
        if (tag.contains("TargetRadius")) setTargetRadius(tag.getFloat("TargetRadius"));
        if (tag.contains("GrowTicks")) setGrowTicks(tag.getInt("GrowTicks"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putBoolean("Wave1", wave1Done);
        tag.putBoolean("Wave2", wave2Done);
        tag.putBoolean("Wave3", wave3Done);
        tag.putFloat("Radius", getRadius());
        tag.putFloat("Length", getLength());
        tag.putInt("MaxAge", getMaxAge());
        tag.putFloat("TargetRadius", getTargetRadius());
        tag.putInt("GrowTicks", getGrowTicks());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /** 在指定底面中心生成光柱：第一波在 {@link #WAVE1_TICK} 结算，即"出现"与"打到"同一帧。 */
    public static VerdictBeamEntity spawn(Level level, Vec3 baseCenter,
                                          float radius, float targetRadius, float length,
                                          int growTicks, int maxAge, @Nullable Player caster) {
        VerdictBeamEntity e = new VerdictBeamEntity(EntityTypeRegistry.VERDICT_BEAM.get(), level);
        e.setPos(baseCenter);
        e.setRadius(radius);
        e.setTargetRadius(Math.max(radius, targetRadius));   // 终点不得小于起点
        e.setLength(length);
        e.setGrowTicks(growTicks);
        e.setMaxAge(maxAge);
        e.setCaster(caster);
        level.addFreshEntity(e);
        return e;
    }

    /** 默认参数的快捷工厂。 */
    public static VerdictBeamEntity spawn(Level level, Vec3 baseCenter,
                                          float radius, float length, @Nullable Player caster) {
        return spawn(level, baseCenter, radius, DEFAULT_TARGET_RADIUS, length,
                DEFAULT_GROW_TICKS, DEFAULT_MAX_AGE, caster);
    }
}
