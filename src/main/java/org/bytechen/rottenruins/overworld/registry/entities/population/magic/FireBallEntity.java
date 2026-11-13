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
 * 法术投射物「火球」：继承原版 {@link Fireball} 保留飞行手感与火焰拖尾，本类只管三件事 ——
 * 怎么飞（出生点与瞄准）、直接命中掉多少血（走 {@link SpellDamageUtil}，吃 spell_power 倍率与击杀归因）、
 * 炸多大并炸掉什么（{@link Level.ExplosionInteraction#MOB}，受 mobGriefing 控制）。
 * 注意施法者自己不在爆炸豁免名单里，贴脸打墙会炸到自己（与原版火球一致）。
 */
public class FireBallEntity extends Fireball {

    private static final String TAG_RADIUS = "HallExplosionRadius";
    private static final String TAG_BREAK_BLOCKS = "HallBreaksBlocks";

    /** 没走 {@link #spawn} 时的兜底爆炸半径（格）。 */
    public static final double DEFAULT_EXPLOSION_RADIUS = 2.0d;

    /** 命中生物时点燃的秒数（原版火球本身不点燃生物，这里补上）。 */
    public static final int FIRE_SECONDS_ON_HIT = 5;

    /** 出生点离眼睛的距离（格）：取 1.0 而非 0，避免与施法者碰撞箱重叠、贴脸炸自己。 */
    private static final double SPAWN_OFFSET = 1.0d;

    /** 爆炸半径（格），由法术在生成时按法术强度算好。 */
    private double explosionRadius = DEFAULT_EXPLOSION_RADIUS;

    private boolean breaksBlocks = true;

    /** 幂等标记：一次飞行只炸一次。 */
    private boolean exploded;

    public FireBallEntity(@NotNull EntityType<? extends Fireball> type,
                          @NotNull Level level) {
        super(type, level);
    }

    /**
     * 在施法者的瞄准方向上生成火球：owner 用于击杀归因，spellStack 既用于渲染也用于伤害计算，
     * 半径由法术按自己的法术强度算好传进来。
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

        // 瞄准用原版的 xPower/yPower/zPower（AbstractHurtingProjectile 每 tick 施加并乘 0.95 阻尼），
        // 刻意不调 setDeltaMovement，否则原版那条加速曲线会失效。
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

    public double getExplosionRadius() {
        return explosionRadius;
    }

    public boolean breaksBlocks() {
        return breaksBlocks;
    }

    /** 打不到自己人：原版只排除同载具，刚出手的火球会立刻打中自己，这里把 owner 整个排除。 */
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
                // 只传基值：rotten_ruins:spell_power 由 MagicDamageEvent 的监听器乘，乘在这里会变成平方
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

    /**
     * 爆炸并销毁自己。伤害源显式为 {@code explosion(火球, 施法者)}，保持三条链：死亡信息、
     * 爆炸保护附魔、以及击杀回蓝认得出 owner。
     */
    private void explode() {
        if (exploded || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        exploded = true;

        serverLevel.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(),
                24, 0.55d, 0.55d, 0.55d, 0.1d);

        DamageSource source = serverLevel.damageSources().explosion(this, getOwner());

        // 与恶魂火球一致：MOB 交互 → 地形破坏与是否点火都受 mobGriefing 控制
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
     * 爆炸策略：只决定方块炸不炸。抗爆判定不在这里 —— 原版 {@code Explosion} 会先用
     * {@link ExplosionDamageCalculator#getBlockExplosionResistance} 扣爆压，扣成负数就走不到这里。
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
