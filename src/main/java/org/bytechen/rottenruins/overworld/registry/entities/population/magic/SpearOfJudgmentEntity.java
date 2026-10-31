package org.bytechen.rottenruins.overworld.registry.entities.population.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.IMagicBaseItem;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * <b>审判之矛</b> —— 从天而降、笔直钉向指定位置的法术投射物。
 *
 * <h3>它和别的投射物有什么不一样</h3>
 * <table border="1">
 *   <tr><th>要求</th><th>实现</th></tr>
 *   <tr><td><b>无重力</b></td>
 *       <td>{@code setNoGravity(true)}，而且位移是直接 {@code setPos} 推的
 *           —— 一次都不调 {@code move()}，所以既没有重力也没有阻尼</td></tr>
 *   <tr><td><b>从后方 5 格、上方 5 格射出</b></td>
 *       <td>{@link #spawn} 里出生点取"主人脚下 → 沿身体朝向退 {@value #SPAWN_BACK_OFFSET} 格
 *           → 再抬 {@value #SPAWN_UP_OFFSET} 格"</td></tr>
 *   <tr><td><b>有明确的主人</b></td>
 *       <td>生成时 {@code setOwner(caster)} 定死并随存档保存；击杀归因、命中豁免
 *           都按它判，见 {@link #spawn} 的注释与 {@link #isOwner}</td></tr>
 *   <tr><td><b>射向指定区域</b></td>
 *       <td>方向在生成时就按"出生点 → 准星指定的位置"算好并定死，
 *           全程走直线；走完这段距离就结束（{@link #finish}）</td></tr>
 *   <tr><td><b>无视方块阻拦</b></td>
 *       <td>整条 tick 里<b>没有任何方块查询</b>—— 不 raycast、不 {@code clip}、
 *           不 {@code move()}。山体、墙、基岩一律直接穿过去</td></tr>
 *   <tr><td><b>穿透生物</b></td>
 *       <td>命中生物后<b>不销毁</b>，继续飞；同一个目标<b>可以反复吃伤害</b>
 *           （穿体期间每 tick 一次，不去重），每次结算前把目标的<b>无敌帧清零</b>
 *           —— 详见 {@link #hitAlongStep} 与 {@link #damage}</td></tr>
 *   <tr><td><b>造成伤害</b></td>
 *       <td>每 tick 用"这一步扫过的线段"做一次判定，命中走
 *            —— 与别的法术同一条通道：
 *           {@code MagicDamageEvent}、{@code rotten_ruins:spell_power} 倍率、击杀归因与掉落都生效</td></tr>
 * </table>
 *
 * <h3>为什么继承 {@code Projectile} 而不是 {@code Entity}</h3>
 * <p>只为了白拿 owner 的存取与存档（{@code setOwner}/{@code getOwner} + 存 UUID），
 * 击杀归因要靠它。{@code Projectile} 本身不提供任何碰撞或移动行为 ——
 * 那些都在 {@code AbstractHurtingProjectile} 里，本类故意不碰。</p>
 *
 * <h3>渲染</h3>
 * <p>客户端使用沿飞行方向的程序化光矛渲染器。
 * 因为普通实体没有客户端预测，{@code EntityType} 的 {@code updateInterval} 设成了
 * <b>1</b>（每 tick 同步位置），否则 1.6 格/tick 的速度会看出明显的跳格。</p>
 */
public class SpearOfJudgmentEntity extends Projectile implements ItemSupplier {

    // ── NBT ──
    private static final String TAG_DIRECTION = "HallSpearDirection";
    private static final String TAG_REMAINING = "HallSpearRemaining";
    private static final String TAG_DAMAGE_SCALE = "HallSpearDamageScale";
    private static final String TAG_SPELL = "HallSpearSpell";

    /** 飞行速度（格/tick）。1.6 = 32 格/秒：够快，读作"从天上钉下来"。 */
    public static final double SPEED = 1.6d;

    /** 判定用的圆柱半径（格）：以这一步的轨迹线段为轴。 */
    public static final double HIT_RADIUS = 0.8d;

    /**
     * 安全存活上限（tick）。
     * <p>正常情况走完 {@link #remaining} 就自己结束了，这个只是兜底 ——
     * 万一方向/距离因为某种原因没被正确初始化，不至于留一根永久存在的矛。</p>
     */
    public static final int MAX_AGE = 200;

    /** 出生点：在主人<b>背后</b>多少格（沿主人身体朝向的反方向）。 */
    public static final double SPAWN_BACK_OFFSET = 5.0d;

    /** 出生点：在主人<b>上方</b>多少格（相对主人脚下的坐标）。 */
    public static final double SPAWN_UP_OFFSET = 5.0d;

    private static final DustParticleOptions JUDGMENT_DUST =
            new DustParticleOptions(new Vector3f(0.30f, 0.72f, 1.0f), 1.25f);

    /** 单位飞行方向。生成时定死，中途不变。 */
    private Vec3 direction = Vec3.ZERO;

    /** 还要飞多少格；减到 0 就到指定区域了。 */
    private double remaining;

    /**
     * 伤害倍率：由法术在释放时按蓄力完成度算好传进来。
     * <p>本法术当前 {@code minChargeRatio = 1}（必须蓄满才放），所以它恒为 {@code 1.0}；
     * 但如果把那个值调低（允许"提前放"），这里就已经能把"提前放效果减弱"接上了，
     * 不用再改实体。</p>
     */
    private float damageScale = 1.0f;

    /** 释放这次法术的物品堆（命中时用来取伤害基值、附魔、事件上下文）。 */
    private ItemStack spellStack = ItemStack.EMPTY;

    private int age;
    private boolean finished;

    public SpearOfJudgmentEntity(@NotNull EntityType<? extends Projectile> type, @NotNull Level level) {
        super(type, level);
        this.noCulling = true;
        // 无重力：位移全程由 setPos 推进，一次都不调 move()，所以重力与阻尼都无从生效
        this.setNoGravity(true);
    }

    // ══════════════════════════════════════════════════════════════
    // 生成
    // ══════════════════════════════════════════════════════════════

    /**
     * 在主人<b>背后 5 格、上方 5 格</b>生成一根矛，朝 {@code aimPoint} 直线射出去。
     *
     * <h3>主人是怎么定下来的</h3>
     * <p>生成时就 {@code setOwner(caster)} 定死，并且由 {@link Projectile} 自带
     * 的存档把 owner 的 UUID 一起写进 NBT —— 区块卸载/重进世界之后
     * {@code getOwner()} 仍然能靠 UUID 从服务端找回这个玩家。定死主人之后，
     * 下面三件事就必然指向同一个人：</p>
     * <ul>
     *   <li><b>击杀归因</b>：{@code SpellDamageUtil} 把主人写进受害者的
     *       {@code lastHurtByPlayer}，掉落/进度/本模组的击杀回蓝都靠它；</li>
     *   <li><b>命中豁免</b>：矛穿过主人时不结算伤害（按 UUID 比，见 {@link #isOwner}）；</li>
     *   <li><b>取法术数据</b>：伤害基值从这次释放的法术物品堆里读。</li>
     * </ul>
     * <p>{@code caster} 是 {@code @NotNull} 的，所以"生成出来却没有主人"这件事
     * 在本方法里不可能发生。唯一会丢掉主人的时机是<b>读档</b>——主人已离线时
     * {@code getOwner()} 会返回 {@code null}，那种情况下 {@link #damage} 仍然照打，
     * 只是退化成没有归因的伤害（{@code SpellDamageUtil} 会把来源退成 {@code magic()}）。</p>
     *
     * @param caster      主人（施法者），必填
     * @param spellStack  这次释放的法术物品堆
     * @param aimPoint    准星指定的落点，见 {@code MagicHandle#resolveAimPoint}
     * @param damageScale 伤害倍率（蓄力完成度）
     * @return 生成出来的矛；落点离出生点太近（小于 0.5 格）时返回 {@code null}，不生成
     */
    @Nullable
    public static SpearOfJudgmentEntity spawn(@NotNull ServerPlayer caster, @NotNull ItemStack spellStack,
                                              @NotNull Vec3 aimPoint, float damageScale) {
        Level level = caster.level();

        // "背后"用<b>身体朝向（yaw）</b>算，不用视线方向：
        // 玩家把准星压到脚下（俯仰 -90°）时视线的水平分量是 0，"背后"就没有意义了，
        // 而身体朝向永远有定义。
        // Vec3.directionFromRotation(0, yaw) 就是该 yaw 下的水平朝向单位向量。
        Vec3 forward = Vec3.directionFromRotation(0.0f, caster.getYRot());
        Vec3 start = caster.position()
                .subtract(forward.scale(SPAWN_BACK_OFFSET))   // 退到背后
                .add(0.0d, SPAWN_UP_OFFSET, 0.0d);            // 抬到上方

        Vec3 delta = aimPoint.subtract(start);
        double distance = delta.length();
        if (distance < 0.5d) {
            // 落点几乎就在出生点上（例如准星正对身后、而射程又很短）→ 没有可飞的距离
            return null;
        }
        Vec3 dir = delta.scale(1.0d / distance);

        SpearOfJudgmentEntity spear =
                new SpearOfJudgmentEntity(EntityTypeRegistry.SPEAR_OF_JUDGMENT.get(), level);
        spear.setPos(start.x, start.y, start.z);
        spear.setOwner(caster);
        spear.direction = dir;
        spear.remaining = distance;
        spear.damageScale = damageScale;
        spear.spellStack = spellStack.copy();

        // 朝向：billboard 渲染用不到，但位置包会带上 yaw/pitch —— 小地图、雷达、
        // 以及将来换成真模型时都读它。角度算法照抄原版 Projectile#shoot。
        float horizontal = Mth.sqrt((float) (dir.x * dir.x + dir.z * dir.z));
        spear.setYRot((float) (Mth.atan2(dir.x, dir.z) * (180.0d / Math.PI)));
        spear.setXRot((float) (Mth.atan2(dir.y, horizontal) * (180.0d / Math.PI)));

        level.addFreshEntity(spear);

        // 出手音：三叉戟的投掷音，正好是"矛"的味道
        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 0.9f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, start.x, start.y, start.z,
                    22, 0.5d, 0.5d, 0.5d, 0.025d);
        }
        return spear;
    }

    // ══════════════════════════════════════════════════════════════
    // Tick
    // ══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        super.tick();

        // 客户端不做任何逻辑：位置由服务端的位置包同步过来（updateInterval = 1）。
        // 这一点和原版火球那种"两端各自跑一遍同样的物理"的实体不一样 ——
        // 本实体没有客户端预测，也不需要。
        if (level().isClientSide()) {
            return;
        }

        if (finished) {
            discard();
            return;
        }
        if (++age > MAX_AGE) {
            finish();
            return;
        }

        // 这一 tick 走多远：最后一步只走剩下的那点，保证正好停在指定区域
        double stepLength = Math.min(SPEED, remaining);
        Vec3 step = direction.scale(stepLength);

        // 先判定再位移：判定用的是"这一步扫过的线段"，
        // 所以不会因为一 tick 走 1.6 格而漏掉站在中间的目标
        hitAlongStep(step);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);

        remaining -= stepLength;
        spawnTrail();

        if (remaining <= 1.0e-4) {
            finish();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 伤害（穿透）
    // ══════════════════════════════════════════════════════════════

    /**
     * 结算这一步扫过的所有生物。
     *
     * <p>用"当前碰撞箱沿这一步拉伸"的 AABB 当判定体，等价于一根以轨迹为轴的圆柱 ——
     * 这一步走多长就扫多长，不会有"高速穿过导致漏判"的洞。</p>
     *
     * <p><b>同一个目标可以反复吃伤害</b>：这里<b>不做</b>"一个目标只打一次"的去重，
     * 所以矛穿过一个生物的那几 tick 里，每 tick 都会结算一次 —— 体积越大的生物
     * 被穿的时间越长，挨的次数越多（0.6 格宽的普通怪约 2 次，5 格宽的约 5 次）。
     * 每次结算前会把目标的<b>无敌帧清零</b>（见 {@link #damage}），
     * 否则第二次会被原版 {@code LivingEntity.hurt} 的 {@code invulnerableTime > 10}
     * 分支当成"重复伤害"而只补差值、也就是打不出效果。</p>
     */
    private void hitAlongStep(@NotNull Vec3 step) {
        AABB sweep = getBoundingBox().expandTowards(step).inflate(HIT_RADIUS);
        List<LivingEntity> hits = level().getEntitiesOfClass(LivingEntity.class, sweep, this::canHit);
        for (LivingEntity target : hits) {
            damage(target);
        }
    }

    /** 能不能打：活着的生物、不是旁观、不是主人（自己放的矛不会扎到自己）。 */
    private boolean canHit(@NotNull LivingEntity target) {
        return !isOwner(target) && target.isAlive() && !target.isSpectator();
    }

    /**
     * 目标是不是<b>主人本人</b>。
     *
     * <p>按 UUID 比，不按对象引用：{@code getOwner()} 是从 UUID 反查出来的
     * （见 {@code Projectile#getOwner}），而"目标列表里的那个玩家实例"和它
     * 不保证是同一个对象引用。用 {@code ==} 比一旦不相等，主人就会吃到自己的矛。</p>
     */
    private boolean isOwner(@NotNull Entity target) {
        Entity owner = getOwner();
        return owner != null && owner.getUUID().equals(target.getUUID());
    }

    /** 对单个目标结算一次法术伤害。 */
    private void damage(@NotNull LivingEntity target) {
        IMagicBaseItem item = MagicStats.spellOf(spellStack);
        if (item == null) {
            // 物品堆丢了（理论上不会）→ 连基值都读不到，只能放弃这一次
            return;
        }
        // 主人可能查不到（读档后他不在线）——那时仍然照打，只是没有归因：
        // SpellDamageUtil 会把 DamageSource 退化成 magic()，不会造成"无来源伤害"崩溃。
        ServerPlayer caster = ownerPlayer();

        // 无敌帧清零 + 红闪拉满，否则第二下会被原版 LivingEntity.hurt 的
        // `invulnerableTime > 10` 分支吃掉（同数值的第二次只补差值 = 0 伤害），
        // 表现为"穿透过去只掉了一次血"。这是"能重复造成伤害"的前提。
        // 两条路都覆盖：走 BCCore 直接写血时本来不吃无敌帧，但 BCCore 不可用
        // 退回原版 hurt 时那一条就吃。
        SpellDamageUtil.prepareRepeatHit(target);

        // 基值 × 蓄力倍率 → 交给 MagicStats 折叠（属性/附魔 provider 链）。
        // rotten_ruins:spell_power 的倍率不在这里乘：它由 MagicDamageEvent 的监听器乘，
        // 乘在这里会叠加成平方。
        float base = item.baseDamage() * damageScale;
        float amount = MagicStats.spellDamage(caster, spellStack, target, null, base);
        SpellDamageUtil.hurt(target, spellStack, caster, amount);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5d,
                    target.getZ(), 9, 0.3d, 0.35d, 0.3d, 0.08d);
        }
    }

    /** 主人；不是玩家（读档后主人不在线）时返回 {@code null}，调用方需要能接受它。 */
    @Nullable
    private ServerPlayer ownerPlayer() {
        return getOwner() instanceof ServerPlayer player ? player : null;
    }

    // ══════════════════════════════════════════════════════════════
    // 表现
    // ══════════════════════════════════════════════════════════════

    /** Cool light and short-lived motes trace the spear's actual path. */
    private void spawnTrail() {
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 wake = position().subtract(direction.scale(0.9d));
            serverLevel.sendParticles(JUDGMENT_DUST,
                    wake.x, wake.y, wake.z, 4, 0.16d, 0.16d, 0.16d, 0.015d);
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    wake.x, wake.y, wake.z, 2, 0.08d, 0.08d, 0.08d, 0.005d);
        }
    }

    /** 到达指定区域：落点特效 + 音效，然后销毁。 */
    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY(), getZ(), 45, 0.7d, 0.7d, 0.7d, 0.12d);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY(), getZ(), 25, 0.5d, 0.5d, 0.5d, 0.08d);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.9f, 0.9f);
        }
        discard();
    }

    // ══════════════════════════════════════════════════════════════
    // 杂项覆写
    // ══════════════════════════════════════════════════════════════

    /**
     * 兼容 ItemSupplier 的法术物品堆；实体视觉由专用渲染器负责。
     */
    @Override
    @NotNull
    public ItemStack getItem() {
        return new ItemStack(RegisterItem.SPEAR_OF_JUDGMENT.get());
    }

    /** 不可被选中/不被攻击：它是法术效果，不是能捡起来的东西。 */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** 无论多远都要渲染（长距离下坠很常见）。 */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    /** 本实体没有任何需要同步的字段：位置靠位置包，渲染靠固定贴图。 */
    @Override
    protected void defineSynchedData() {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ══════════════════════════════════════════════════════════════
    // 存档
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble(TAG_DIRECTION + "X", direction.x);
        tag.putDouble(TAG_DIRECTION + "Y", direction.y);
        tag.putDouble(TAG_DIRECTION + "Z", direction.z);
        tag.putDouble(TAG_REMAINING, remaining);
        tag.putFloat(TAG_DAMAGE_SCALE, damageScale);
        if (!spellStack.isEmpty()) {
            tag.put(TAG_SPELL, spellStack.save(new CompoundTag()));
        }
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Vec3 dir = new Vec3(tag.getDouble(TAG_DIRECTION + "X"),
                tag.getDouble(TAG_DIRECTION + "Y"),
                tag.getDouble(TAG_DIRECTION + "Z"));
        // 存档里可能是零向量（旧档/异常）→ 退回"垂直向下"，总比原地卡住好
        direction = dir.lengthSqr() < 1.0e-8 ? new Vec3(0.0d, -1.0d, 0.0d) : dir.normalize();
        remaining = Math.max(0.0d, tag.getDouble(TAG_REMAINING));
        if (tag.contains(TAG_DAMAGE_SCALE)) {
            damageScale = tag.getFloat(TAG_DAMAGE_SCALE);
        }
        if (tag.contains(TAG_SPELL)) {
            spellStack = ItemStack.of(tag.getCompound(TAG_SPELL));
        }
    }

    /** 调试用：剩余飞行距离（格）。 */
    public double getRemaining() {
        return remaining;
    }

    /** 调试用：单位飞行方向。 */
    @NotNull
    public Vec3 getFlightDirection() {
        return direction;
    }
}
