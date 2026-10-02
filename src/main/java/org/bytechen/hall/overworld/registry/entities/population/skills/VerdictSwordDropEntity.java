package org.bytechen.hall.overworld.registry.entities.population.skills;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDamage;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictFeedback;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 裁决领域的<b>落剑</b> —— 一道从天而降、落地后插在地上的剑气。
 *
 * <h3>为什么它需要是一个实体，而不是"几个 {@code SwordAuraEntity} + 偏移"</h3>
 * <p>剑阵的读法来自<b>时间差</b>：多把剑以错开的时间从上落下、依次钉进地里，
 * 才会被读成"阵"而不是"一堆特效同时生成"。而错开的落地时间需要每把剑
 * 各自记一个计时器 —— 那正是实体最擅长的事。</p>
 *
 * <h3>落点与位置的分工（这份契约必须与 {@code SwordAuraRenderer} 一致）</h3>
 * <pre>
 *   实体位置 (x, y, z)   = 剑的<b>最终落点</b>（地面高度）
 *   getFallProgress()    = 0 → 1 的下落进度；渲染侧用
 *                          {@code y + (1 - progress) * getFallFrom()} 重建当前位置
 * </pre>
 * <p>也就是说<b>实体本身不移动</b>，位移完全由渲染侧按同步数据重建。
 * 这么做比"服务端每 tick 挪一次实体"省下一个每 tick 的位置包 ——
 * 而一次剑阵会同时有 6~8 把剑在下落。</p>
 *
 * <h3>落地伤害</h3>
 * <p>只有<b>指明了目标</b>的剑（{@link #setTarget} 非空）才在落地那一刻结算伤害。
 * 这样一来"剑落下来"和"伤害生效"在时间上是同一件事 ——
 * 原本的做法是剑气生成的那一瞬就结算，于是画面上剑还在飞、怪已经掉血了。</p>
 */
public class VerdictSwordDropEntity extends Entity {

    // ── 同步数据 ────────────────────────────────────────────────────
    private static final EntityDataAccessor<Float> DATA_FALL_FROM =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FALL_TICKS =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(VerdictSwordDropEntity.class, EntityDataSerializers.INT);

    // ── 默认值 ──────────────────────────────────────────────────────
    /** 默认下落高度（格）。够高才有"从天而降"的读法，又不至于落到一半领域就结束了。 */
    public static final float DEFAULT_FALL_FROM = 16.0f;
    /** 默认下落耗时（tick）= 0.45 秒。再慢就不是"剑雨"而是"飘落"了。 */
    public static final int DEFAULT_FALL_TICKS = 9;
    /** 默认总存活（tick）：落完之后还要插在地上留一会儿。 */
    public static final int DEFAULT_MAX_AGE = 40;
    /** 默认剑长（格）。 */
    public static final float DEFAULT_HEIGHT = 5.0f;

    /**
     * 落剑的用途。
     *
     * <p>只剩一种了：原本还有一个 {@code USE_STRUCTURE}（展开剑阵时那圈纯装饰的
     * 立柱），随"撤掉展开表现"一起去掉了 —— 现在每一把落剑都必须对应一次真实伤害。
     * 保留这个常量是因为它同时进存档与同步数据，删掉会让旧存档里的字段变成野值。</p>
     */
    public static final int USE_STRIKE = 1;

    private int age;
    private @Nullable UUID targetUUID;
    private float hurt = VerdictDamage.FIELD_HURT;
    private boolean damageDone;
    /** 落地时是否打一圈空间涟漪（每次脉冲只给第一把剑，避免多把剑叠成一堆环）。 */
    private boolean ringOnLand = true;
    private ParticleOptions landParticle;
    /**
     * 投下这把剑的 {@link VerdictFieldEntity} 的 entity id（-1 = 无）。
     *
     * <p>它存在的唯一目的是<b>把施法者追回来</b>：领域只存了 owner 的 UUID，
     * 而伤害归因需要 {@code Player} 引用。没有它的话，领域击杀的怪不记在玩家头上
     * —— 掉落、成就、击杀统计全部落到"环境"。</p>
     */
    private int fieldEntityId = -1;

    public VerdictSwordDropEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    // ══════════════════════════════════════════════════════════════
    //  同步数据
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_FALL_FROM, DEFAULT_FALL_FROM);
        entityData.define(DATA_FALL_TICKS, DEFAULT_FALL_TICKS);
        entityData.define(DATA_MAX_AGE, DEFAULT_MAX_AGE);
        entityData.define(DATA_SCALE, 1.0f);
        entityData.define(DATA_HEIGHT, DEFAULT_HEIGHT);
        entityData.define(DATA_STYLE, USE_STRIKE);
    }

    /** 从落点往上多少格开始下落。 */
    public float getFallFrom()      { return entityData.get(DATA_FALL_FROM); }
    /** 下落耗时（tick）。 */
    public int   getFallTicks()     { return entityData.get(DATA_FALL_TICKS); }
    public int   getMaxAge()        { return entityData.get(DATA_MAX_AGE); }
    public float getSwordScale()    { return entityData.get(DATA_SCALE); }
    public float getSwordHeight()   { return entityData.get(DATA_HEIGHT); }
    public int   getUse()           { return entityData.get(DATA_STYLE); }
    public int   getAge()           { return age; }

    public void setFallFrom(float v)   { entityData.set(DATA_FALL_FROM, Math.max(1.0f, v)); }
    public void setFallTicks(int v)    { entityData.set(DATA_FALL_TICKS, Math.max(1, v)); }
    public void setMaxAge(int v)       { entityData.set(DATA_MAX_AGE, Math.max(1, v)); }
    public void setSwordScale(float v) { entityData.set(DATA_SCALE, Math.max(0.01f, v)); }
    public void setSwordHeight(float v){ entityData.set(DATA_HEIGHT, Math.max(0.5f, v)); }
    public void setUse(int v)          { entityData.set(DATA_STYLE, USE_STRIKE); }

    // 剑宽不在这里：它由渲染器按"宽 = 长 × 固定比例"推出（见 VerdictSwordDropRenderer
    // 的 BLADE_WIDTH_RATIO）。实体只持有语义参数（长度、缩放、姿势），
    // 几何比例归渲染器 —— 否则两处各存一份宽度，改一处就会出现不一致。

    /** 0 → 1 的下落进度（带 partialTick）。1 表示已经落地。 */
    public float getFallProgress(float partialTick) {
        int ft = getFallTicks();
        if (ft <= 0) return 1f;
        return Math.min((age + Math.max(0f, partialTick)) / ft, 1f);
    }

    /** 落地之后在生命周期里的淡出强度（0..1）。 */
    public float getHoldIntensity() {
        int rest = Math.max(1, getMaxAge() - getFallTicks());
        float t = Math.max(0f, (age - getFallTicks()) / (float) rest);
        return 1f - Math.max(0f, t) * Math.max(0f, t);       // 二次淡出，尾巴收得干净
    }

    // ══════════════════════════════════════════════════════════════
    //  目标与伤害
    // ══════════════════════════════════════════════════════════════

    public void setTarget(@Nullable LivingEntity target) {
        this.targetUUID = target == null ? null : target.getUUID();
    }

    public void setHurt(float hurt) { this.hurt = hurt; }

    /** 落地时是否打一圈空间涟漪。 */
    public void setRingOnLand(boolean v) { this.ringOnLand = v; }

    /** 落地时的粒子（null = 不打粒子）。 */
    public void setLandParticle(@Nullable ParticleOptions p) { this.landParticle = p; }

    /**
     * 记下投出这把剑的领域实体（用于把施法者追回来做伤害归因）。
     * <p>必须在 {@code addFreshEntity} <b>之前</b>调用 —— 落地结算不会发生在
     * 生成的同一 tick（{@code fallTicks} 最小为 1），但把顺序写对更稳。</p>
     */
    public void setFieldEntity(@Nullable VerdictFieldEntity field) {
        this.fieldEntityId = field == null ? -1 : field.getId();
    }

    /** 通过领域实体反查施法者；任何一环缺失都返回 null（伤害照样结算，只是没有归因）。 */
    @Nullable
    private Player resolveCaster() {
        if (fieldEntityId < 0) return null;
        return level().getEntity(fieldEntityId) instanceof VerdictFieldEntity field
                ? field.getOwner() : null;
    }

    /** 按 UUID 找回目标（生物与玩家一视同仁）。 */
    @Nullable
    private LivingEntity resolveTarget() {
        if (targetUUID == null) return null;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(64.0))) {
            if (e.getUUID().equals(targetUUID)) return e;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    //  Tick
    // ══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        age++;

        if (level().isClientSide()) {
            // 客户端只负责在下落时留一条细光尘，让"剑从哪来"可读
            if (age < getFallTicks()) {
                Vec3 p = position().add(0, (1.0 - getFallProgress(0f)) * getFallFrom(), 0);
                level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        p.x, p.y, p.z, 0.0, 0.08, 0.0);
            }
            return;
        }

        // ── 落地那一刻：整把剑的"生效点" ──
        int ft = getFallTicks();
        if (!damageDone && age >= ft) {
            damageDone = true;
            onLand();
        }

        if (age >= getMaxAge()) discard();
    }

    /**
     * 落地：结算伤害 + 打反馈。
     *
     * <p>刻意把伤害放在<b>落地</b>而不是生成时：剑还在空中、怪已经掉血，
     * 是玩家最容易察觉到的"特效和伤害对不上"。代价是伤害延迟了
     * {@value #DEFAULT_FALL_TICKS} tick —— 但领域本来就是持续技能，
     * 0.45 秒的延迟换来的是"剑落下 → 怪掉血"这条因果可读。</p>
     */
    private void onLand() {
        Vec3 at = position();

        if (ringOnLand) {
            VerdictFeedback.impactRing(level(), at.add(0, 0.05, 0),
                    2.2f, 2.6f, 0.55f);
        }
        if (landParticle != null) {
            VerdictFeedback.burst(level(), at.add(0, 0.25, 0), landParticle, 8, 0.14);
        }

        // 只有指明了目标的剑才结算伤害（阵列立柱是纯装饰）
        if (targetUUID == null) return;

        LivingEntity target = resolveTarget();
        if (target == null || !target.isAlive()) return;

        // 归因：通过领域实体把施法者追回来（详见 fieldEntityId 的注释）。
        // 拿不到时退化为 magic + null —— 伤害形状完全不变，只是这一下不算玩家的击杀。
        Player caster = resolveCaster();
        var source = caster != null ? caster.damageSources().playerAttack(caster)
                                    : level().damageSources().magic();
        VerdictDamage.strike(target, source, hurt, VerdictDamage.BYPASS_PART, caster);
        VerdictFeedback.landed(target, 0.6f);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override
    public boolean isPickable()   { return false; }
    @Override
    public boolean isAttackable() { return false; }

    /**
     * 剔除用的包围盒必须<b>向上扩到下落起点</b>。
     *
     * <p>光锥从 16 格高处落下来，如果包围盒只有落点那一小格，
     * 玩家在剑落到半空时转头就会看到它消失 —— 那是经典的自定义渲染实体剔除问题。</p>
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBox().expandTowards(0, getFallFrom() + 2.0, 0).inflate(1.5);
    }

    // ══════════════════════════════════════════════════════════════
    //  存档
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        damageDone = tag.getBoolean("Landed");
        if (tag.hasUUID("Target")) targetUUID = tag.getUUID("Target");
        if (tag.contains("FallFrom")) setFallFrom(tag.getFloat("FallFrom"));
        if (tag.contains("FallTicks")) setFallTicks(tag.getInt("FallTicks"));
        if (tag.contains("MaxAge")) setMaxAge(tag.getInt("MaxAge"));
        if (tag.contains("Scale")) setSwordScale(tag.getFloat("Scale"));
        if (tag.contains("Height")) setSwordHeight(tag.getFloat("Height"));
        if (tag.contains("Use")) setUse(tag.getInt("Use"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putBoolean("Landed", damageDone);
        if (targetUUID != null) tag.putUUID("Target", targetUUID);
        tag.putFloat("FallFrom", getFallFrom());
        tag.putInt("FallTicks", getFallTicks());
        tag.putInt("MaxAge", getMaxAge());
        tag.putFloat("Scale", getSwordScale());
        tag.putFloat("Height", getSwordHeight());
        tag.putInt("Use", getUse());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ══════════════════════════════════════════════════════════════
    //  静态工厂
    // ══════════════════════════════════════════════════════════════

    /**
     * 在落点生成一道落剑。
     *
     * @param landing   剑的最终落点（一般取目标脚下的地面高度）
     * @param fallFrom  从落点上方多少格开始下落
     * @param fallTicks 下落耗时（tick）—— 多把剑错开这个值就是"剑雨"的节奏来源
     * @param scale     整体缩放
     * @param height    剑长（格）
     */
    public static VerdictSwordDropEntity spawn(Level level, Vec3 landing,
                                              float fallFrom, int fallTicks,
                                              float scale, float height) {
        VerdictSwordDropEntity e = new VerdictSwordDropEntity(
                EntityTypeRegistry.VERDICT_SWORD_DROP.get(), level);
        e.setPos(landing);
        e.setFallFrom(fallFrom);
        e.setFallTicks(fallTicks);
        e.setUse(USE_STRIKE);
        e.setSwordScale(scale);
        e.setSwordHeight(height);
        // 存活 = 落完 + 一段停留：停留期就是"插在地上的剑"还能被看见的时间。
        // 停留给到 22 tick（1.1 秒）：够看清它插在哪，又不至于占着一堆实体。
        e.setMaxAge(fallTicks + 22);
        level.addFreshEntity(e);
        return e;
    }
}
