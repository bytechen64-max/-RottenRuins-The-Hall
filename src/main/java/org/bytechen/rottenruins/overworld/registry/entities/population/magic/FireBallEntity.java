package org.bytechen.rottenruins.overworld.registry.entities.population.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.IMagicBaseItem;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 法术投射物 —— <b>火球</b>。
 *
 * <p>继承原版 {@link Fireball}，所以飞行手感、
 * 火焰拖尾、被打偏（{@code hurt} 会把它推开）都是原版的；本类只负责三件事：
 * <b>怎么飞</b>（出生点与瞄准方向）、<b>命中谁掉多少血</b>、<b>炸多大并炸掉什么</b>。</p>
 *
 * <h3>为什么不用原版 {@code LargeFireball}</h3>
 * <ul>
 *   <li>原版的爆炸威力是个私有字段，且构造后改不了 —— 而我们的半径要按<b>法术强度</b>现算；</li>
 *   <li>原版直接命中的 6 点伤害走的是 {@code entity.hurt(...)}，绕过了本模组的
 *       {@code MagicDamageEvent}／{@code rotten_ruins:spell_power} 倍率／击杀归因那一条链；</li>
 *   <li>原版爆炸的伤害来源由 {@code Explosion} 自己推，我们要显式指定，
 *       才能保证"击杀回蓝"认得出是谁打死的（见下）。</li>
 * </ul>
 *
 * <h3>伤害是怎么分配出去的</h3>
 * <ol>
 *   <li><b>直接命中</b>：{@link #onHitEntity} → {@link SpellDamageUtil#hurt}，
 *       带法术物品、走 {@code MagicDamageEvent}，因此
 *       {@code rotten_ruins:spell_power} 会乘进去、击杀会算在施法者头上、掉落不丢。</li>
 *   <li><b>爆炸范围</b>：交给原版 {@code Explosion} 结算。它的伤害
 *       <b>由半径推出</b>（见下），所以"法术强度 → 半径 → 伤害"是一条链；
 *       想独立控制范围伤害的话，得自己遍历实体调 {@code SpellDamageUtil}，
 *       而不是用原版爆炸。</li>
 * </ol>
 *
 * <h3>破坏方块</h3>
 * <p>用 {@link Level.ExplosionInteraction#MOB}，也就是和原版恶魂火球完全一致：
 * 地形破坏受 {@code mobGriefing} 游戏规则控制，抗爆方块（黑曜石、基岩）照旧炸不动。
 * 另外 {@code breaksBlocks == false} 时本次爆炸<b>任何方块都不破坏</b>（只伤害与击退），
 * 这是留给"安全版火球"的开关。</p>
 *
 * <p><b>注意</b>：施法者自己<b>不在</b>爆炸豁免名单里 —— 贴脸打墙会把自己炸到，
 * 和原版火球一样。想避免就改 {@link #spawn} 的出生距离或者把半径调小。</p>
 */
public class FireBallEntity extends Fireball {

    /** 爆炸半径的 NBT 键。 */
    private static final String TAG_RADIUS = "HallExplosionRadius";
    /** 是否破坏方块的 NBT 键。 */
    private static final String TAG_BREAK_BLOCKS = "HallBreaksBlocks";

    /** 没走 {@link #spawn} 时的兜底爆炸半径（格）。 */
    public static final double DEFAULT_EXPLOSION_RADIUS = 2.0d;

    /**
     * 命中生物时点燃的秒数。
     * <p>原版火球并不直接点燃生物（只靠爆炸的火焰与地形火），这里补上，
     * 让"火球"这个名字名副其实。</p>
     */
    public static final int FIRE_SECONDS_ON_HIT = 5;

    /**
     * 出生点离眼睛的距离（格）。
     * <p>取 1.0 而不是 0：从眼睛位置直接生成会让火球和施法者的碰撞箱重叠，
     * 而且贴脸打墙时爆炸中心就在脸上。</p>
     */
    private static final double SPAWN_OFFSET = 1.0d;

    /** 爆炸半径（格）。由法术在生成时按法术强度算好。 */
    private double explosionRadius = DEFAULT_EXPLOSION_RADIUS;

    /** 本次爆炸是否破坏方块。 */
    private boolean breaksBlocks = true;

    /** 幂等标记：一次飞行只炸一次。 */
    private boolean exploded;

    public FireBallEntity(@NotNull EntityType<? extends Fireball> type,
                          @NotNull Level level) {
        super(type, level);
    }

    // ══════════════════════════════════════════════════════════════
    // 生成
    // ══════════════════════════════════════════════════════════════

    /**
     * 在施法者的瞄准方向上生成一颗火球。
     *
     * @param caster          施法者（同时被设为 owner，击杀归因靠它）
     * @param spellStack      法术物品堆 —— 既用于渲染（原版 {@code ThrownItemRenderer}
     *                        画的就是它），也用于命中时的伤害计算
     * @param explosionRadius 爆炸半径（格），由法术按自己的法术强度算好传进来
     * @param breaksBlocks    这次爆炸是否破坏方块
     */
    @NotNull
    public static FireBallEntity spawn(@NotNull ServerPlayer caster, @NotNull ItemStack spellStack,
                                       double explosionRadius, boolean breaksBlocks) {
        Level level = caster.level();
        Vec3 look = caster.getViewVector(1.0f);

        FireBallEntity ball = new FireBallEntity(EntityTypeRegistry.FIRE_BALL.get(), level);
        Vec3 pos = caster.getEyePosition().add(look.scale(SPAWN_OFFSET));
        ball.setPos(pos.x, pos.y, pos.z);
        ball.setRot(caster.getYRot(), caster.getXRot());
        ball.setOwner(caster);

        // 瞄准方向：原版不是设置速度，而是设置一个"加速度方向"，
        // 每 tick 由 AbstractHurtingProjectile 施加并乘 0.95 阻尼 —— 见它的 tick()。
        // 所以这里刻意不调 setDeltaMovement，让原版那条加速曲线原样生效。
        ball.xPower = look.x * 0.1d;
        ball.yPower = look.y * 0.1d;
        ball.zPower = look.z * 0.1d;

        ball.setItem(spellStack.copy());
        ball.explosionRadius = Math.max(0.1d, explosionRadius);
        ball.breaksBlocks = breaksBlocks;

        level.addFreshEntity(ball);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8f, 0.72f);
        return ball;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && !exploded && tickCount % 2 == 0) {
            server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(),
                    5, 0.18d, 0.18d, 0.18d, 0.015d);
            server.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(),
                    2, 0.12d, 0.12d, 0.12d, 0.005d);
        }
    }

    /** 本次爆炸半径（格）。 */
    public double getExplosionRadius() {
        return explosionRadius;
    }

    /** 本次爆炸是否破坏方块。 */
    public boolean breaksBlocks() {
        return breaksBlocks;
    }

    // ══════════════════════════════════════════════════════════════
    // 命中
    // ══════════════════════════════════════════════════════════════

    /**
     * 打不到自己人。
     *
     * <p>原版只排除"骑在同一载具上"的情况，所以从手上刚飞出去的火球是有可能
     * 立刻打中自己的。这里直接把 owner 整个排除掉 —— 火球会从施法者身上穿过去。</p>
     */
    @Override
    protected boolean canHitEntity(@NotNull Entity target) {
        return target != this.getOwner() && super.canHitEntity(target);
    }

    /** 命中生物：先按本模组的法术通道结算一次直接伤害，再爆炸。 */
    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        // 服务端权威：客户端只负责渲染（原版火球也是这么护的）
        if (level().isClientSide()) {
            return;
        }

        if (result.getEntity() instanceof LivingEntity living && living.isAlive()) {
            ServerPlayer caster = casterOrNull();
            ItemStack spell = getItem();
            IMagicBaseItem item = MagicStats.spellOf(spell);
            if (caster != null && item != null) {
                // 基值取法术物品声明的那一份，再交给 MagicStats 折叠；
                // rotten_ruins:spell_power 的倍率不在这里乘 —— 它由 MagicDamageEvent 的
                // 监听器乘，乘在这里会和它叠加成平方。
                float damage = MagicStats.spellDamage(caster, spell, living, null, item.baseDamage());
                SpellDamageUtil.hurt(living, spell, caster, damage);
                living.setSecondsOnFire(FIRE_SECONDS_ON_HIT);
            }
        }

        explode();
    }

    /** 撞到方块：直接爆炸。 */
    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        if (level().isClientSide()) {
            return;
        }
        explode();
    }

    // ══════════════════════════════════════════════════════════════
    // 爆炸
    // ══════════════════════════════════════════════════════════════

    /**
     * 爆炸并销毁自己。
     *
     * <p>伤害来源显式指定为 {@code explosion(火球, 施法者)}，和原版爆炸完全同构
     * （directEntity = 火球，causingEntity = 施法者），所以：</p>
     * <ul>
     *   <li>死亡信息是"被炸死"而不是"被魔法杀死"；</li>
     *   <li>爆炸保护附魔照常生效（伤害类型还是 {@code explosion}）；</li>
     *   <li>本模组的击杀回蓝认得出施法者（{@code LivingDeathEvent} 的 source 里
     *       {@code getEntity()} 就是 owner）。</li>
     * </ul>
     */
    private void explode() {
        if (exploded || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        exploded = true;

        serverLevel.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(),
                24, 0.55d, 0.55d, 0.55d, 0.1d);

        DamageSource source = serverLevel.damageSources().explosion(this, getOwner());

        // 和原版恶魂火球一致：MOB 交互 → 地形破坏受 mobGriefing 控制；
        // 是否点火也挂在这个规则上（原版 LargeFireball 就是这么传的）。
        boolean mobGriefing = serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);

        serverLevel.explode(this, source, new SpellExplosionCalculator(breaksBlocks),
                getX(), getY(), getZ(), (float) explosionRadius,
                mobGriefing, Level.ExplosionInteraction.MOB);

        this.discard();
    }

    /** 施法者；不是玩家（例如读档后 owner 丢了）时为 {@code null}。 */
    @Nullable
    private ServerPlayer casterOrNull() {
        return getOwner() instanceof ServerPlayer player ? player : null;
    }

    /**
     * 爆炸策略：唯一的作用是按法术的要求决定"方块炸不炸"。
     *
     * <p>抗爆判定<b>不在这里</b> —— 原版 {@code Explosion} 会先按
     * {@link ExplosionDamageCalculator#getBlockExplosionResistance} 把爆压扣掉，
     * 扣成负数就走不到 {@link #shouldBlockExplode}。所以黑曜石/基岩不需要我们操心。</p>
     */
    private static final class SpellExplosionCalculator extends ExplosionDamageCalculator {

        private final boolean breakBlocks;

        private SpellExplosionCalculator(boolean breakBlocks) {
            this.breakBlocks = breakBlocks;
        }

        @Override
        public boolean shouldBlockExplode(@NotNull Explosion explosion, @NotNull BlockGetter level,
                                         @NotNull BlockPos pos, @NotNull BlockState state,
                                         float power) {
            return breakBlocks;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 存档
    // ══════════════════════════════════════════════════════════════

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble(TAG_RADIUS, explosionRadius);
        tag.putBoolean(TAG_BREAK_BLOCKS, breaksBlocks);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_RADIUS)) {
            explosionRadius = Math.max(0.1d, tag.getDouble(TAG_RADIUS));
        }
        if (tag.contains(TAG_BREAK_BLOCKS)) {
            breaksBlocks = tag.getBoolean(TAG_BREAK_BLOCKS);
        }
    }
}
