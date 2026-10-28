package org.bytechen.rottenruins.overworld.registry.entities.population.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictFeedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 陨石事件（{@code /meteorevent} 触发）—— 一次三段式的天灾演出。
 *
 * <h3>三段相位</h3>
 * <pre>
 *   0   → 200t   ① 显现   目标正上方 150 格亮起一颗十字星，闪烁、缓慢自转、越来越大
 *   200 → 400t   ② 坠落   十字星变成火球砸下来，拖尾从头顶被拉长、越来越红
 *   400t         ③ 砸中   超大规模爆炸：复用 {@link MeteoriteEntity} 的落地爆炸
 *                        + 三重冲击环 + 粒子 + 音效 + 镜头抖动 / FOV
 *   400 → 460t   余波：留下的火球与烟，然后散
 * </pre>
 *
 * <h3>为什么"砸中"要复用 MeteoriteEntity</h3>
 * <p>项目里已经有一整套陨石落地爆炸（二十面体冲击、AoE、矿石飞溅、滞留消退），
 * 这里在落点上方 2 格生成一颗、给一个向下的速度，它下一 tick 就会自己
 * {@code doImpact()}。重写一套只会得到两份不一致的爆炸。</p>
 *
 * <h3>为什么只需要一个同步字段</h3>
 * <p>整条时间线的所有量（十字星大小、火球高度、拖尾长度、颜色）都是由
 * {@code age} 推出来的纯函数；{@code age} 两端各自累加（与
 * {@code CrimsonVowFieldEntity} 同一个做法），所以除了"种子"之外没有
 * 任何需要每 tick 同步的东西。</p>
 */
public class MeteorEventEntity extends Entity {

    /** 显现时长（tick）= 10 秒。 */
    public static final int SIGN_TICKS = 200;
    /** 坠落时长（tick）= 10 秒。 */
    public static final int FALL_TICKS = 200;
    /** 砸中之后的余波时长（tick）。 */
    public static final int AFTER_TICKS = 500;
    /** 十字星离落点的高度（格）。 */
    public static final float SIGN_HEIGHT = 130.0f;
    /** 十字星在天空中的最大视觉半径（格）。 */
    public static final float SIGN_MAX_SIZE = 16.0f;
    /** 拖尾最大半径（格）。 */
    public static final float TRAIL_MAX_RADIUS = 3.2f;
    /** 落点火球的最大半径（格）。 */
    public static final float BLAST_MAX_RADIUS = 46.0f;

    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(MeteorEventEntity.class, EntityDataSerializers.INT);

    private int age;
    private boolean impacted;
    @Nullable
    private UUID ownerUUID;
    /** 客户端抓"刚砸中"用来触发一次屏幕抖动 / 3bit 屏效。 */
    private boolean clientImpactFired;

    public MeteorEventEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    // ══════════════════════════════════════════════════════════════
    //  同步 / 访问器
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_SEED, 0);
    }

    public int getAge() { return age; }
    public int getSeed() { return entityData.get(DATA_SEED); }
    public boolean isImpacted() { return impacted; }
    /**
     * 客户端"刚砸中"的一次性事件。
     *
     * <p>{@code impacted} 两端各自在 tick 里翻真（服务端只是额外多做爆炸），
     * 所以这一个 bool 就够用，不需要同步字段。</p>
     */
    public boolean consumeClientImpact() {
        if (clientImpactFired || !impacted) return false;
        clientImpactFired = true;
        return true;
    }

    /** 十字星的**水平偏移**（格）。 */
    public static final float SIGN_OFFSET = 140.0f;

    /**
     * 十字星出现的位置 —— 落点**斜上方**，方向由种子决定。
     *
     * <p>实机反馈"每次都是笔直砸下来，没有大倾角"。原来十字星固定在落点
     * 正上方 150 格，轨迹就是一条垂直线。现在起点在水平方向偏
     * {@link #SIGN_OFFSET} 格、高度 {@link #SIGN_HEIGHT} 格，进入角约
     * <b>47°</b>，每次事件的方位还不同。方向只依赖种子，两端算出来一致。</p>
     */
    public net.minecraft.world.phys.Vec3 signPos() {
        float ang = (getSeed() % 360) * net.minecraft.util.Mth.DEG_TO_RAD;
        return new net.minecraft.world.phys.Vec3(
                getX() + Math.cos(ang) * SIGN_OFFSET,
                getY() + SIGN_HEIGHT,
                getZ() + Math.sin(ang) * SIGN_OFFSET);
    }

    /** 这一帧头部的位置（沿起点→落点的斜线插值，{@code 1-p⁴} 缓入）。 */
    public net.minecraft.world.phys.Vec3 headPos(float pt) {
        float f = 1.0f - (float) Math.pow(fallProgress(pt), FALL_EXPONENT);
        net.minecraft.world.phys.Vec3 s = signPos();
        net.minecraft.world.phys.Vec3 e = position();
        return new net.minecraft.world.phys.Vec3(
                e.x + (s.x - e.x) * f,
                e.y + (s.y - e.y) * f,
                e.z + (s.z - e.z) * f);
    }

    /**
     * 尾迹可见段的末端：从头部沿斜线往起点方向退 {@link #TRAIL_LENGTH} 格，
     * 且不超过起点。这样尾迹永远跟着头走，而不是钉在天上某一点。
     */
    public net.minecraft.world.phys.Vec3 trailEnd(float pt) {
        net.minecraft.world.phys.Vec3 h = headPos(pt);
        net.minecraft.world.phys.Vec3 s = signPos();
        net.minecraft.world.phys.Vec3 d = s.subtract(h);
        double len = d.length();
        if (len < 1.0e-4) return h;
        float want = TRAIL_LENGTH * (0.55f + 0.45f * fallProgress(pt));
        double take = Math.min(len, want);
        return h.add(d.scale(take / len));
    }

    /** 显现进度 0..1。 */
    public float signProgress(float pt) {
        return Mth.clamp((age + pt) / SIGN_TICKS, 0f, 1f);
    }

    /** 坠落进度 0..1（0 = 刚进大气层，1 = 触地）。 */
    public float fallProgress(float pt) {
        return Mth.clamp((age + pt - SIGN_TICKS) / FALL_TICKS, 0f, 1f);
    }

    /**
     * 坠落曲线的指数：{@code y = H·(1 - p^k)}。
     *
     * <p>历史：{@code 1-p²} 被反馈"太慢"；改成"90% 时间缓降 + 最后 1 秒暴冲"的
     * 两段式之后又被反馈"加速太突兀"—— 因为两段的**加速度在接缝处跳变**，
     * 头会先减速到几乎停住、再突然往下掉。</p>
     *
     * <p>现在用一条**连续**曲线 {@code 1 - p^4}：起始速度 0（还挂在天上），
     * 加速度单调增大，末速 {@code 4·H/T} = <b>3 格/tick（60 格/秒）</b>。
     * 没有接缝，所以不会"顿一下再掉"，但末段依然是砸下来的。</p>
     */
    public static final float FALL_EXPONENT = 4.0f;


    /** 砸中之后经过的 tick 数（未砸中为 0）。 */
    public float afterTicks(float pt) {
        return Math.max(0f, age + pt - (SIGN_TICKS + FALL_TICKS));
    }


    /** 尾迹在头后面保留的长度（格）。 */
    public static final float TRAIL_LENGTH = 58.0f;

    public void setOwner(@Nullable Entity owner) {
        this.ownerUUID = owner == null ? null : owner.getUUID();
    }

    @Nullable
    public Player getOwner() {
        return ownerUUID == null ? null : level().getPlayerByUUID(ownerUUID);
    }

    // ══════════════════════════════════════════════════════════════
    //  Tick
    // ══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        age++;

        // 事件开场：天顶一声"开"，让玩家起鸡皮疙瘩（也提示抬头）
        if (!level().isClientSide() && age == 2) {
            Vec3 at = position();
            // 这两声也要"远处听得到"：十字星在斜上方 190 格开外，
            // 老写法 volume=6 只有 ~96 格的可听半径，很多人根本听不见开场。
            boom(at, SoundEvents.BEACON_ACTIVATE, 0.55f, 420.0);
            boom(signPos(), SoundEvents.END_PORTAL_SPAWN, 0.70f, 520.0);
        }

        int impactAge = SIGN_TICKS + FALL_TICKS;
        if (age >= impactAge && !impacted) {
            impacted = true;
            if (!level().isClientSide()) impact();
        }

        // 坑是**分帧**挖的：一次放一万多个方块更新会卡住服务端，
        // 而"卡一下"正好会毁掉最该有分量的那一下。
        if (!level().isClientSide()) {
            aftermathTick();
            tickCrater();
        }

        // 队列没挖完就不散（否则玩家会看到"坑挖到一半没了"）
        boolean craterDone = crater == null || craterIndex >= crater.size();
        if (age > impactAge + AFTER_TICKS && craterDone) {
            discard();
        }
    }

    /**
     * 放一声"远处也听得到"的爆炸。
     *
     * <p>MC 的音量同时也是**可听半径**：{@code volume} 4 只到 64 格左右，
     * 玩家在 100 格外就完全没声。所以这里按需要的半径反推音量：
     * {@code volume = maxDist / 16}（SoundEngine 里线性衰减的最大距离是
     * {@code max(volume,1) * 16}）。实际增益会被 clamp 到 1，
     * 所以近处的玩家不会被轰爆，远处则是自然的距离衰减。</p>
     *
     * <p>⚠️ 坑：{@code level.playSound(player, ...)} 里的 player 参数是
     * <b>"排除谁"</b>，不是"放给谁"。早先按"逐个玩家"写成了
     * {@code playSound(p, ...)}，等于把所有玩家依次排除了一遍 ——
     * 结果是<b>谁都没听到</b>。要指定对象得自己发
     * {@code ClientboundSoundPacket}；这里用广播更简单。</p>
     */
    private void boom(Vec3 at, net.minecraft.sounds.SoundEvent sound,
                      float pitch, double maxDist) {
        float vol = (float) Mth.clamp(maxDist / 16.0, 1.0, 40.0);
        level().playSound(null, at.x, at.y, at.z, sound,
                SoundSource.PLAYERS, vol, pitch);
    }
    /**
     * 砸中：一次**纯粹的毁灭**。只在服务端调用。
     *
     * <h3>为什么不复用 MeteoriteEntity 的爆炸</h3>
     * <p>那是"普通陨石"的演出（二十面体线框 + 它自己的 AoE 环 + 矿石飞溅），
     * 范围和观感都配不上"天灾"。这一招要的是：一个半径 26 格的碗形坑、
     * 一次全屏 3bit 红黑、四道冲击环 —— 所以爆炸由这里自己做，只是把
     * 粒子 / 音效 / 镜头这些共用零件继续走 {@code VerdictFeedback}。</p>
     */
    private void impact() {
        Vec3 c = position();

        // ① 冲击环：**大且快**。
        //  实机反馈"冲击波太小、扩散太慢"——原来的主环 80 格 / 55 格每秒，
        //  要 1.5 秒才推到头。现在主环 200 格 / 180 格每秒（1.1 秒推满），
        //  再叠一道 320 格慢壳，远处的人也能看到天边压过来的那圈。
        VerdictFeedback.impactRing(level(), c, 200f, 180f, 2.0f);
        VerdictFeedback.impactRing(level(), c, 120f, 120f, 1.8f);
        VerdictFeedback.impactRing(level(), c, 320f, 90f, 1.2f);
        VerdictFeedback.impactRing(level(), c, 44f, 60f, 1.6f);

        // ② 粒子
        VerdictFeedback.burst(level(), c, ParticleTypes.EXPLOSION_EMITTER, 20, 0.0);
        VerdictFeedback.burst(level(), c, ParticleTypes.SOUL_FIRE_FLAME, 1600, 4.5);
        VerdictFeedback.burst(level(), c, ParticleTypes.LAVA, 900, 3.6);
        VerdictFeedback.burst(level(), c, ParticleTypes.END_ROD, 500, 3.0);
        VerdictFeedback.burst(level(), c, ParticleTypes.CRIMSON_SPORE, 800, 3.0);

        // ③ 音效：对每个附近玩家单独算音量，远处也听得到
        boom(c, SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 420.0);
        boom(c, SoundEvents.GENERIC_EXPLODE, 0.40f, 380.0);
        boom(c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.35f, 320.0);
        boom(c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.50f, 480.0);

        // ④ 镜头：施法者满档 + FOV，周围所有人也顶一下
        AABB box = new AABB(c, c).inflate(140.0);
        for (Player p : level().getEntitiesOfClass(Player.class, box)) {
            if (ownerUUID != null && p.getUUID().equals(ownerUUID)) {
                VerdictFeedback.shake(p, 1.5f);
                VerdictFeedback.fovKick(p, 9.0f);
            } else {
                VerdictFeedback.shakeNear(p, c, 1.35f);
                VerdictFeedback.fovKick(p, 5.0f);
            }
        }

        // ⑤ 伤害：半径 40 内所有活物。施法者豁免（指令测试时不该把自己炸死）
        AABB hurtBox = new AABB(c, c).inflate(BLAST_HURT_RADIUS);
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, hurtBox)) {
            if (ownerUUID != null && living.getUUID().equals(ownerUUID)) continue;
            double d = living.position().distanceTo(c);
            float falloff = (float) Mth.clamp(1.0 - d / BLAST_HURT_RADIUS, 0.0, 1.0);
            if (falloff <= 0.01f) continue;
            living.hurt(level().damageSources().explosion(this, getOwner()), BLAST_DAMAGE * falloff);
            Vec3 push = living.position().subtract(c).normalize().scale(2.2 * falloff);
            living.push(push.x, 1.2 * falloff + 0.4, push.z);
        }

        // ⑥ 挖坑（先收集、排序，再由 tickCrater 分帧消化）
        queueCrater();

        // ⑦ 落点补一圈烟
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    c.x, c.y + 1.0, c.z, 400, 30.0, 10.0, 30.0, 0.02);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  碗形坑：收集 → 排序 → 分帧挖
    // ══════════════════════════════════════════════════════════════

    /** 半径 26 格、中心最深 12 格的碗。 */
    public static final float BLAST_RADIUS = 26.0f;
    public static final float BLAST_DEPTH = 12.0f;
    /** 伤害半径（比坑大：冲击波扫到的活物都算）。 */
    public static final float BLAST_HURT_RADIUS = 44.0f;
    public static final float BLAST_DAMAGE = 160.0f;
    /** 每 tick 最多挖多少方块。 */
    public static final int BLOCKS_PER_TICK = 700;

    /**
     * 余波：落地之后不该"就炸一下"。
     *
     * <p>前 6 秒每 10 tick 在坑里随机位置补一次**次级爆炸**（小冲击环 +
     * 熔岩/灵魂火 + 一声闷响 + 一点抖动），6~9 秒每 45 tick 一声低频轰鸣。
     * 这些全部走服务端，不占网络（粒子一次发包）。</p>
     */
    private void aftermathTick() {
        if (!impacted) return;
        int a = age - (SIGN_TICKS + FALL_TICKS);
        if (a <= 0) return;

        // 次级爆炸：读起来像"坑里还在连环炸"
        if (a < 200 && a % 10 == 0) {
            double ang = level().random.nextDouble() * Math.PI * 2.0;
            double dist = 4.0 + level().random.nextDouble() * 20.0;
            Vec3 p = position().add(Math.cos(ang) * dist, 1.5, Math.sin(ang) * dist);
            VerdictFeedback.burst(level(), p, ParticleTypes.EXPLOSION, 6, 0.7);
            VerdictFeedback.burst(level(), p, ParticleTypes.LAVA, 50, 1.4);
            VerdictFeedback.burst(level(), p, ParticleTypes.SOUL_FIRE_FLAME, 60, 1.2);
            VerdictFeedback.impactRing(level(), p, 26f, 46f, 1.1f);
            boom(p, SoundEvents.GENERIC_EXPLODE,
                    0.55f + level().random.nextFloat() * 0.3f, 160.0);
            // 小抖动：让余波一直在"顶"着镜头
            AABB box = new AABB(p, p).inflate(64.0);
            for (Player pl : level().getEntitiesOfClass(Player.class, box)) {
                VerdictFeedback.shakeNear(pl, p, 0.35f);
            }
        }

        // 低频轰鸣：余波里每隔一会儿来一下，像地还在响
        if (a > 20 && a % 45 == 0 && a < 440) {
            boom(position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.35f, 520.0);
        }
    }

    private List<BlockPos> crater;
    private int craterIndex;

    private void queueCrater() {
        int r = Mth.ceil(BLAST_RADIUS);
        BlockPos origin = blockPosition();
        List<BlockPos> list = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double hd = Math.sqrt(dx * dx + dz * dz) / BLAST_RADIUS;
                if (hd > 1.0) continue;
                // 碗形：越靠中心越深
                int depth = (int) (BLAST_DEPTH * (1.0 - hd * hd));
                for (int dy = 1; dy >= -depth; dy--) {
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level().hasChunkAt(p)) continue;
                    if (level().isEmptyBlock(p)) continue;
                    // 硬度 < 0 = 不可破坏（基岩 / 屏障 / 命令方块）
                    if (level().getBlockState(p).getDestroySpeed(level(), p) < 0) continue;
                    list.add(p.immutable());
                }
            }
        }
        // 由内向外啃：读起来是"一圈冲击波在吃地面"
        list.sort(Comparator.comparingDouble(b -> b.distSqr(origin)));
        crater = list;
        craterIndex = 0;
    }

    private void tickCrater() {
        if (crater == null || craterIndex >= crater.size()) return;
        int end = Math.min(craterIndex + BLOCKS_PER_TICK, crater.size());
        for (; craterIndex < end; craterIndex++) {
            level().setBlock(crater.get(craterIndex), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        if (craterIndex >= crater.size()) crater = null;
    }

    // ══════════════════════════════════════════════════════════════
    //  实体样板
    // ══════════════════════════════════════════════════════════════

    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override public boolean isPickable()   { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        impacted = tag.getBoolean("Impacted");
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        if (tag.contains("Seed")) entityData.set(DATA_SEED, tag.getInt("Seed"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putBoolean("Impacted", impacted);
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("Seed", getSeed());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ══════════════════════════════════════════════════════════════
    //  静态工厂
    // ══════════════════════════════════════════════════════════════

    /**
     * 在 {@code ground}（落点，y 已是地表高度）触发一次陨石事件。
     *
     * @param owner 触发者（只用于镜头反馈与存档归属，可以是空）
     */
    public static MeteorEventEntity spawn(Level level, @Nullable Player owner, Vec3 ground) {
        MeteorEventEntity e = new MeteorEventEntity(
                org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry.METEOR_EVENT.get(), level);
        e.setPos(ground.x, ground.y, ground.z);
        e.setOwner(owner);
        e.entityData.set(DATA_SEED, level.random.nextInt(1 << 20));
        level.addFreshEntity(e);
        return e;
    }
}
