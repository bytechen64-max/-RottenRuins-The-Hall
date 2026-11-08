package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.compat.BCCoreCompat;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.bytechen.rottenruins.utils.ModUtils;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import static net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;

/**
 * <b>寂寒白日 · 左键三连斩</b> —— 挥刀一次，在目标身上连斩三下。
 *
 * <h3>触发与节奏</h3>
 * <ul>
 *   <li>由 {@code AttackEntityEvent} 驱动（见 {@code ForgeEventHandler#onAttackEntity}），
 *       所以<b>无论原版这一刀有没有造成伤害</b>（被取消、打无敌帧、目标免伤）都会触发
 *       —— 这是需求里明确要求的"无论造成伤害没有"。</li>
 *   <li>原版那一刀本身<b>保留</b>：不取消事件，受击红闪/击退/落剑动作都照常。</li>
 *   <li>三斩的间隔是 {@value #SLASH_INTERVAL} tick（0.3 秒），第一斩与"挥刀"同一 tick。</li>
 * </ul>
 *
 * <h3>每一斩做什么</h3>
 * <ol>
 *   <li><b>视觉</b>：{@link SwordAuraEntity#spawnFrostSlash} —— 与使徒技能斩击同一套构件
 *       （外层放大 + 内芯 outline），配色换成<b>冰白外层 / 深蓝内芯</b>。位置取
 *       <b>目标碰撞箱中心</b>，朝向由实体 id 派生，所以每一下的方向都不同
 *       （"方向随机但要斩到目标"）。尺寸是<b>窄而长</b>的一线刀光：
 *       {@value #SLASH_HEIGHT} 格长、{@value #SLASH_RADIUS} 格半径。</li>
 *   <li><b>范围伤害</b>：斩击不是只打被砍的那一个 —— 以目标中心为球心、
 *       {@value #SLASH_RANGE} 格为半径，范围内<b>所有</b>生物一起吃这一斩
 *       （施法者自己除外）。因为刀光本身有 10 格长，只打单体反而和视觉对不上。</li>
 *   <li><b>伤害</b>：两条路分开结算(原因见 {@link #writePercentOfMax})：
 *       <ul>
 *         <li><b>改血</b> = 目标最大生命值的 {@value #PERCENT_HEALTH}%（不低于 1 点），
 *             直接调 {@code BCCoreCompat.damage} —— 不走任何伤害事件、不吃护甲、
 *             不吃无敌帧；</li>
 *         <li><b>冰伤</b> = {@value #ICE_DAMAGE} 点，走 {@link SpellDamageUtil} 的包装层，
 *             护甲与别的模组的减伤照常生效。</li>
 *       </ul>
 *       两条路都会补击杀归因，改血致死时 {@code BCCoreCompat} 会补一次真实的
 *       {@code die(source)}，所以<b>掉落物照常</b>。</li>
 *   <li><b>冻结</b>：每斩把目标的冻结计时顶满，读作"被冰刃割开"。</li>
 * </ol>
 *
 * <h3>为什么需要调度器</h3>
 * <p>三斩要跨 12 tick，而武器本身没有"每 tick 回调"。这里用一个极小的
 * {@link TickEvent.ServerTickEvent} 队列：每次挥刀往队里塞三条 {@link PendingSlash}，
 * 到期就结算。队列里存的是 <b>UUID</b> 而不是实体引用 —— 目标在中途被卸载/换维度时，
 * 按 UUID 查不到就自然丢弃，不会把手里的引用拖住。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SilentDaylightAttack {

    /** 每一斩之间的间隔（tick）：0.3 秒。 */
    public static final int SLASH_INTERVAL = 6;

    /** 一次挥刀斩几下。 */
    public static final int SLASH_COUNT = 3;

    /** 每一斩的<b>基准</b>冰伤（走包装层，吃护甲 / 事件）；实际值按攻击力等比放大。 */
    public static final float ICE_DAMAGE = 18.0F;

    /**
     * 基准攻击力：寂寒白日自身的 8 点 + 玩家基础 1 点 = {@code 9}。
     *
     * <p>用它把玩家的 {@code ATTACK_DAMAGE} 属性归一化成倍率：
     * 拿本剑时倍率正好 1.0（冰伤 = {@link #ICE_DAMAGE}），
     * 换成更好的武器 / 吃到力量buff / 别的模组加攻击力时按同一比例上升。</p>
     */
    public static final double REFERENCE_ATTACK_DAMAGE = 9.0D;

    /**
     * 攻击力倍率的上限。
     *
     * <p>防呆：{@code ATTACK_DAMAGE} 是玩家属性，能被各种模组叠到几十上百，
     * 不封顶的话三斩的冰伤会直接失控。10 倍已经相当于 180 点冰伤一斩。</p>
     */
    public static final double MAX_ATTACK_SCALE = 10.0D;

    /**
     * 当前玩家攻击力对应的<b>冰伤</b>。
     *
     * <p>= {@link #ICE_DAMAGE} × (玩家攻击力 / {@link #REFERENCE_ATTACK_DAMAGE})，
     * 倍率钳在 {@code [0, }{@link #MAX_ATTACK_SCALE}{@code ]}。
     * 攻力读不到（属性缺失 / NaN）时退回基准值，不会变成 0 伤害。</p>
     */
    public static float iceDamageOf(@Nullable net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return ICE_DAMAGE;
        }
        double attack;
        try {
            attack = player.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        } catch (Throwable t) {
            return ICE_DAMAGE;
        }
        if (!Double.isFinite(attack) || attack <= 0.0D) {
            return ICE_DAMAGE;
        }
        double scale = Math.min(MAX_ATTACK_SCALE, attack / REFERENCE_ATTACK_DAMAGE);
        return (float) (ICE_DAMAGE * scale);
    }

    /**
     * 每一斩的<b>改血</b>百分比：目标最大生命值的 5%（下限 1 点）。
     *
     * <p>与 {@link #ICE_DAMAGE} 分开结算，两条路各有各的意义：</p>
     * <ul>
     *   <li><b>改血</b>走 {@code BCCoreCompat}（直接写血量）—— 无视护甲、抗性、无敌帧，
     *       这是"改血武器"该有的穿透力；</li>
     *   <li><b>冰伤</b>走 {@code SpellDamageUtil} 那层包装 —— 会经过 Forge 的伤害事件，
     *       别的模组的减伤/免疫照常生效。</li>
     * </ul>
     */
    public static final float PERCENT_HEALTH = 5.0F;

    /** 普通攻击（原版那一刀）额外改血的百分比。 */
    public static final float NORMAL_ATTACK_PERCENT = 5.0F;

    /** 斩击刀光的总长度（格）—— 比使徒的 8 更长，拉成一道长刀。 */
    public static final float SLASH_HEIGHT = 14.0F;

    /**
     * 斩击刀光的底面半径（格）—— 收得比使徒的 1.0 细得多，是<b>一条线</b>而不是一道光带。
     *
     * <p>{@code 0.19} 是"再拉长一档、同时再细一档"的结果：外轮廓层（渲染时放大
     * {@code SwordAuraEntity.DEFAULT_OUTER_SCALE} = 1.45 倍）实际半宽约
     * {@code 0.19 × 1.45 ≈ 0.28} 格，中段最宽处不到 0.6 格；配 14 格长度，
     * 长宽比约 50:1 —— 一道极细极长的寒芒。</p>
     *
     * <p>这个值<b>只影响视觉</b>：判定半径是独立的 {@link #SLASH_RANGE}（{@value #SLASH_RANGE} 格球），
     * 所以把刀光收细不会让刀变"短手"。</p>
     */
    public static final float SLASH_RADIUS = 0.19F;

    /**
     * 沿刀光轴撒的雪花取样数（两侧各这么多个，含中心共 {@code 2×n-1} 个位置）。
     *
     * <p>取 5 ⇒ 9 个位置摊在 {@value #SLASH_HEIGHT} 格上，间距约 1.55 格：看得出是一条线，
     * 又不会在混战里糊成一团。每格只发 1 个粒子，三斩合计约 27 个雪花 —— 可忽略的开销。</p>
     */
    public static final int SLASH_SPARK_STEPS = 5;

    /**
     * 一斩的伤害半径（格）。
     *
     * <p>以被砍目标的碰撞箱中心为球心。取 {@value #SLASH_RANGE} 而不是"刀光多长就打多远"：
     * 刀光是绕自身中心随机倾斜的一道线，几何上没有一个稳定的"扫过体积"，
     * 硬按线去算判定会变成"有时候打到 5 格外的怪、有时候贴脸打不到"。
     * 球形判定读起来稳定，且与"这一斩落在目标身上"的观感一致。</p>
     */
    public static final double SLASH_RANGE = 4.5D;

    /**
     * 待结算的一斩。
     *
     * @param ticksLeft 还有多少 tick 到期；{@code 0} 表示本次服务端 tick 结束时就结算
     *                  （挥刀发生在 {@code ServerTickEvent.END} 之前的包处理里，
     *                  所以第一斩与挥刀同一 tick 落地）
     * @param index     第几斩（0 起），只用于日志
     */
    private record PendingSlash(UUID playerId, UUID targetId, int ticksLeft, int index) {
    }

    private static final List<PendingSlash> PENDING = new ArrayList<>();

    /** "改血引擎不可用"的警告只打一次 —— 否则每一斩都会刷一屏日志。 */
    private static boolean warnedNoEngine;

    private SilentDaylightAttack() {
    }

    // ══════════════════════════════════════════════════════════════
    // 入口：由 AttackEntityEvent 调用
    // ══════════════════════════════════════════════════════════════

    /**
     * 玩家挥刀（{@code AttackEntityEvent}）。只有手上真的是寂寒白日时才生效。
     *
     * <p>这里做两件事：马上结算"普通攻击的 5% 最大生命值改血"，并把三斩排进队列。</p>
     */
    public static void onAttackLanded(ServerPlayer player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SilentDaylight)) {
            return;
        }
        if (target == null || !target.isAlive()) {
            return;
        }

        // 普通攻击：额外 5% 最大生命值的改血
        writePercentOfMax(player, target, NORMAL_ATTACK_PERCENT,
                player.damageSources().playerAttack(player));

        // 三连斩：0 / 6 / 12 tick 之后各结算一次
        for (int i = 0; i < SLASH_COUNT; i++) {
            PENDING.add(new PendingSlash(player.getUUID(), target.getUUID(),
                    i * SLASH_INTERVAL, i));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 调度
    // ══════════════════════════════════════════════════════════════

    /**
     * 每服务端 tick 推进一次队列。
     *
     * <p>用<b>倒计时</b>而不是世界时间：{@code PendingSlash} 里存的两个 UUID 可能指向
     * 不同维度的实体，拿"某一个维度的时间"做比较会引入"哪个维度的时间"这种无意义的分支。
     * 倒计时则只依赖服务端 tick 数，任何维度、任何存档都一致。</p>
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        ListIterator<PendingSlash> iterator = PENDING.listIterator();
        while (iterator.hasNext()) {
            PendingSlash pending = iterator.next();
            if (pending.ticksLeft() <= 0) {
                iterator.remove();
                resolve(event.getServer(), pending);
            } else {
                // record 不可变：就地改成"少一 tick"
                iterator.set(new PendingSlash(pending.playerId(), pending.targetId(),
                        pending.ticksLeft() - 1, pending.index()));
            }
        }
    }

    /** 结算一斩：找到玩家与目标，出视觉效果并结算伤害。 */
    private static void resolve(net.minecraft.server.MinecraftServer server, PendingSlash pending) {
        ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // 在玩家所在维度里找目标（不能拿 overworld 去查，跨维度会找不到）
        if (!(level.getEntity(pending.targetId()) instanceof LivingEntity target)) {
            return;
        }
        if (!target.isAlive()) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SilentDaylight)) {
            // 中途换手了 → 剩下的斩击作废
            return;
        }

        spawnSlashVisual(level, target.getBoundingBox().getCenter());
        applySlashDamage(level, player, held, target.getBoundingBox().getCenter());
    }

    /**
     * 使徒式斩击的视觉：放在目标碰撞箱中心，配色换成蓝白，并拉成<b>窄而长</b>的一线。
     *
     * <p>几何直接复用 {@link SwordAuraEntity#STYLE_FROST} —— 和使徒斩击是同一套构件
     * （多层渐变 + 弧形弯曲 + 流动高光，见 {@code SwordAuraRenderer}）。
     * 朝向仍然由 {@link SwordAuraEntity} 按 entity id 派生的 pitch/yaw 决定，
     * 所以三斩各自歪向不同角度，但锥体中心永远压在目标碰撞箱中心上
     * —— 这就是需求里的"方向随机但要斩到目标的区域"。</p>
     *
     * <p>粒子分三组，各管一件事：</p>
     * <ul>
     *   <li><b>沿刀光轴的一串雪花</b>：刀光本体只是半透明几何，这串粒子把"这一刀划过了哪条线"
     *       在地图上标出来（也是唯一能看出弧线形状的静态痕迹）；</li>
     *   <li><b>两端的末地烛光</b>：刀锋的落点，让 14 格长的刀光有明确的头尾；</li>
     *   <li><b>中心电火花</b>：命中点的寒气爆散。</li>
     * </ul>
     *
     * @param center 斩击中心（目标碰撞箱中心）
     */
    private static void spawnSlashVisual(ServerLevel level, Vec3 center) {
        SwordAuraEntity slash = SwordAuraEntity.spawnFrostSlash(level, center,
                1.0F,               // scale
                SLASH_HEIGHT,       // height：14 格长，拉成一道长刀
                SLASH_RADIUS,       // radius：0.19 格，收成一条极细的寒芒
                16,                 // maxAge：0.8 秒，够"从来袭方向溶解"演完
                0.30F);             // shrinkSpeed：几乎不缩，收场交给方向性溶解

        // 刀光轴线 = 渲染器里那两步旋转作用在本地 +Y 上的结果（先绕 Z 转 pitch，再绕 Y 转 yaw）
        Vec3 axis = slashAxisOf(slash);
        int steps = SLASH_SPARK_STEPS;
        double half = SLASH_HEIGHT * 0.5D;
        for (int i = -steps; i <= steps; i++) {
            // 沿轴均匀取样；跳过正中那格，那里交给下面的电火花
            if (i == 0) {
                continue;
            }
            double along = half * i / steps;
            Vec3 p = center.add(axis.scale(along));
            level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z,
                    1, 0.04D, 0.04D, 0.04D, 0.0D);
        }
        // 两个刀尖：一小簇末地烛光，标出刀的落点
        for (int end = -1; end <= 1; end += 2) {
            Vec3 p = center.add(axis.scale(end * half));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z,
                    5, 0.12D, 0.12D, 0.12D, 0.01D);
        }
        // 命中点的寒气爆散
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y, center.z, 18, 0.7D, 0.7D, 0.7D, 0.16D);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.7F);
    }

    /**
     * 刀光的轴向单位向量。
     *
     * <p>必须和 {@code SwordAuraRenderer} 的旋转顺序一致：先把本地 +Y 绕 Z 转
     * {@code pitch} 度，得到 {@code (-sinθ, cosθ, 0)}；再绕 Y 转 {@code yaw} 度，
     * 得到 {@code (-sinθ·cosφ, cosθ, sinθ·sinφ)}。两处一旦不同步，
     * 粒子就会"不在这把刀上"，所以注释里把公式写死。</p>
     */
    private static Vec3 slashAxisOf(SwordAuraEntity slash) {
        double pitch = Math.toRadians(slash.getPitch());
        double yaw = Math.toRadians(slash.getYaw());
        return new Vec3(
                -Math.sin(pitch) * Math.cos(yaw),
                Math.cos(pitch),
                Math.sin(pitch) * Math.sin(yaw));
    }

    /**
     * 一斩的范围结算：以 {@code center} 为球心、{@link #SLASH_RANGE} 格为半径，
     * 范围内所有生物各吃一份伤害并顶满冻结计时。
     *
     * <p>判定用"目标碰撞箱到球心的最近距离"，而不是"脚底坐标到球心"——
     * 高个子目标（铁傀儡、监守者）的碰撞箱上半段也在刀光里，用脚底判会漏。</p>
     *
     * <p>施法者自己除外：这是玩家武器，不该把自己一起割了。</p>
     */
    private static void applySlashDamage(ServerLevel level, ServerPlayer player, ItemStack held,
                                         Vec3 center) {
        double rangeSqr = SLASH_RANGE * SLASH_RANGE;
        AABB box = new AABB(center, center).inflate(SLASH_RANGE);

        // getEntitiesOfClass 返回的是快照列表，所以下面逐个结算（可能致死）是安全的
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive()
                        && e.getBoundingBox().distanceToSqr(center) <= rangeSqr)) {
            hurtOneSlash(level, player, held, victim);
        }
    }

    /**
     * 对单个目标结算一斩：<b>改血</b> {@link #PERCENT_HEALTH}% 最大生命值（下限 1）+ <b>纯冰伤</b>
     * （{@link #ICE_DAMAGE} × 攻击力倍率），并顶满冻结计时。
     *
     * <p>两段合成<b>一次</b>扣血：它们现在都是直接写血量，分开写只会多一次
     * "目标先被改血打死、冰伤被跳过"的机会。</p>
     */
    private static void hurtOneSlash(ServerLevel level, ServerPlayer player, ItemStack held,
                                     LivingEntity victim) {
        // 冰系来源：DamageTypes.FREEZE + 攻击者是玩家 —— 伤害类型读作"冰冻"，
        // 归因仍然是玩家（掉落 killer 条件、死亡消息、进度都正常）。
        DamageSource source = SpellDamageUtil.iceSourceOf(victim, player);

        // ① 百分比改血：直接写血量，保底留一口气（见 writePercentOfMax 里的说明），
        //    所以它不会把目标打死，后面的冰伤一定有机会结算
        writePercentOfMax(player, victim, PERCENT_HEALTH, source);

        // ② 冰伤：正常造成 —— 走 hurt，吃护甲 / 抗性 / 无敌帧 / 伤害事件与击杀归因
        if (victim.isAlive()) {
            // 每斩前清一次无敌帧，保证 6 tick 间隔的连斩不会被"重复伤害"分支吃掉
            SpellDamageUtil.prepareRepeatHit(victim);
            float ice = iceDamageOf(player);
            boolean applied = SpellDamageUtil.hurt(victim, source, held, player, ice);
            if (!applied && victim.isAlive()) {
                // 事件链把这一下吃掉了（MagicDamageEvent / LivingHurtEvent 被取消、
                // 数值被别的模组减到 0、或改血引擎写入失败退回原版又被判成重复伤害）。
                // 冰伤是这个武器的主要输出，不能就这么静默消失 —— 直接改血兜底，
                // 并留一条 WARN，方便以后定位到底是谁吃掉的。
                warnIceFallbackOnce(victim, ice);
                writeIceDamage(player, victim, ice, source);
            }
            iceHitFeedback(level, victim);
        }

        // ③ 兜底：血量已经 ≤0 但没走过 die() → 按 die 的流程直接补一次，掉落才在
        if (victim.isDeadOrDying() && !victim.isRemoved() && !victim.dead) {
            victim.die(source);
        }

        // 冻结表现：把冻结计时顶到"结冰"，读作冰刃命中
        if (victim.isAlive() || victim.isDeadOrDying()) {
            victim.setTicksFrozen(victim.getTicksRequiredToFreeze());
        }
    }

    /**
     * 「冰伤被事件链吃掉」的警告只打一次 —— 否则每一斩都会刷一屏日志。
     */
    private static boolean iceFallbackWarned;

    /**
     * 冰伤的<b>兜底</b>结算：直接改血（和 {@link #writePercentOfMax} 同一条路）。
     *
     * <p>正常路径是 {@link SpellDamageUtil#hurt} —— 它会把伤害抛给三个事件、
     * 让护甲以外的减伤照常介入。但只要其中任何一个环节把这一下取消/减到 0，
     * 冰伤就会<b>静默消失</b>（"斩击的冰伤没了"就是这么来的）。
     * 冰伤是这个武器的主要输出，所以这里补一条不经过任何事件的落地路径。</p>
     *
     * <p>注意顺序：先写击杀归因再改血，这样真打死时 {@code die(source)} 里的掉落
     * 仍然归给玩家。</p>
     */
    private static void writeIceDamage(ServerPlayer player, LivingEntity victim,
                                       float ice, DamageSource source) {
        if (victim == null || !victim.isAlive() || ice <= 0F) {
            return;
        }
        float before = BCCoreCompat.getHealth(victim);
        if (Float.isNaN(before)) {
            before = victim.getHealth();
        }
        float after = before - ice;
        if (after >= before) {
            return;
        }
        SpellDamageUtil.applyKillCredit(player, victim);
        // 引擎写一次（能穿透"覆写 getHealth"的抗改血实现），再无条件写原版同步血量字段
        BCCoreCompat.setHealth(victim, after);
        victim.entityData.set(DATA_HEALTH_ID, after);
        if (after <= 0F) {
            victim.die(source);
        }
    }

    /** 记下"冰伤被吃掉"这件事（只一次），带上目标与数值，便于定位。 */
    private static void warnIceFallbackOnce(LivingEntity victim, float ice) {
        if (iceFallbackWarned) {
            return;
        }
        iceFallbackWarned = true;
        ModUtils.LOGGER.warn("[SilentDaylight] 冰伤 {} 点没有经事件链落地（目标 {}），"
                        + "已改用直接改血兜底。此警告只打一次，后续静默走兜底。",
                ice, victim.getType().toShortString());
    }

    /**
     * 冰伤命中的<b>表现</b>：目标身上炸一小簇雪花。
     *
     * <p>为什么要有：改血与冰伤最终都是"血量掉了多少"，玩家一眼分不出这一下是
     * <b>冰伤</b>还是百分比改血。贴身的雪花把"这是冰打进去的"这件事显式标出来，
     * 也是"冰伤是不是真的生效了"最直接的肉眼判据。</p>
     */
    private static void iceHitFeedback(ServerLevel level, LivingEntity victim) {
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.6d, victim.getZ(),
                6, 0.3d, 0.35d, 0.3d, 0.02d);
    }

    /**
     * 按目标最大生命值的百分比做一次<b>改血</b>（直接写血量）。
     *
     * <p>改血<b>可以打死目标</b>：血量写到 ≤0 之后直接调 {@code victim.die(source)}，
     * {@code source} 里带着玩家，所以 {@code LivingDeathEvent}、死亡消息、
     * {@code dropAllDeathLoot}（战利品 + 经验）、击杀归因全都照常。
     * {@code die()} 自带 {@code isRemoved() || dead} 的幂等保护，不会死两次。</p>
     *
     * @param percent 百分比，例如 {@code 5.0} 表示最大生命值的 5%
     * @param source  伤害来源（带着玩家），致死时用它走死亡流程
     */
    private static void writePercentOfMax(ServerPlayer player, LivingEntity victim,
                                          float percent, DamageSource source) {
        if (victim == null || !victim.isAlive() || percent <= 0F) {
            return;
        }
        float max = victim.getMaxHealth();
        if (!Float.isFinite(max) || max <= 0F) {
            return;
        }
        // "至少为 1"：低血量目标上 5% 会小于 1，否则等于没伤害
        float amount = Math.max(1.0F, max * (percent / 100.0F));

        float before = BCCoreCompat.getHealth(victim);
        if (Float.isNaN(before)) {
            before = victim.getHealth();
        }
        float after = before - amount;
        if (after >= before) {
            return;
        }

        // 击杀归因：改血不经过 hurt，掉落表读的 lastHurtByPlayer 要自己写
        SpellDamageUtil.applyKillCredit(player, victim);

        // 先让 VitalProbe 引擎写一次（可用时能穿透"覆写 getHealth"的抗改血实现），
        // 再<b>无条件</b>把结果写进原版同步血量字段（引擎不可用时这就是唯一生效的那次）
        BCCoreCompat.setHealth(victim, after);
        victim.entityData.set(DATA_HEALTH_ID, after);

        // 拆血拆到 ≤0：按 die 的流程直接结算（掉落 / 归因 / 死亡消息都在里面）
        if (after <= 0F) {
            victim.die(source);
        }
    }

    /** 服务器停止时清队列，避免跨存档残留。 */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** 调试用：当前队列长度。 */
    public static int pendingCount() {
        return PENDING.size();
    }
}
