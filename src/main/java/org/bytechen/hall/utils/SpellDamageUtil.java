package org.bytechen.hall.utils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.bytechen.hall.api.magic.MagicDamageEvent;
import org.bytechen.hall.compat.BCCoreCompat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>法术伤害工具类</b> —— 把 VitalProbe（{@code BCCoreCompat}）的「直接改血」
 * 包装成「像原版 {@code hurt} 一样」的调用：带伤害来源、走死亡流程、正常掉落。
 *
 * <h3>为什么需要这层封装</h3>
 * <p>{@link BCCoreCompat#damage} 直接写血量存储，绕过了 {@code LivingEntity.hurt}。
 * 这带来两个必须补回来的东西：</p>
 * <ol>
 *   <li><b>来源与击杀归因</b>：{@code hurt} 会把 {@code lastHurtByPlayer} /
 *       {@code lastHurtByMob} 记下来；改血不会。而战利品表的 {@code killer}
 *       条件、进度（advancement）、以及"击杀者是谁"全都读这两个字段。
 *       不补的话，击杀者专用的掉落会静默消失、进度不触发。</li>
 *   <li><b>死亡流程</b>：改血到 ≤0 之后，掉落实现在 {@code LivingEntity.die(source)}
 *      里的 {@code dropAllDeathLoot(source)}。所以必须再走一次真实的
 *       {@code hurt(source, 极大值)}，而<b>不能</b>用 {@code kill()}
 *       —— 后者用的是 {@code genericKill} 来源，会把击杀归因丢掉。</li>
 * </ol>
 *
 * <h3>它做了什么</h3>
 * <pre>
 *   1. 归因：把施法者写进目标的 lastHurtByPlayer / lastHurtByMob
 *   2. 抛 MagicDamageEvent        （本模组的法术伤害事件：可取消、可改数值）
 *   3. 抛 LivingHurtEvent         （Forge：可取消、可改数值 —— 护甲/格挡/友伤仍能看到它）
 *   4. 抛 LivingDamageEvent       （Forge：最终数值）
 *   5. 走 BCCoreCompat.damage 改血（不可用时自动退回原版 hurt）
 *   6. 血量落到 ≤0 → 用<b>原始 DamageSource</b> 补一次真实 hurt
 *      → die(source) → dropAllDeathLoot(source) → 掉落 / 死亡事件 / 进度
 *   7. 抛 MagicDamageEvent.Post   （实伤统计、吸血、连击数…）
 * </pre>
 *
 * <h3>注意</h3>
 * <ul>
 *   <li><b>只在服务端调用</b>；客户端调用直接返回 {@code false}。</li>
 *   <li>改血路径<b>不吃护甲/抗性/无敌帧</b>（这正是它存在的意义）。想让护甲生效请用
 *       {@link #hurtVanilla}。</li>
 *   <li>创造 / 旁观玩家默认豁免（改血会绕过原版那条 {@code isInvulnerableTo} 的创造免疫）。</li>
 * </ul>
 */
public final class SpellDamageUtil {

    private SpellDamageUtil() {
    }

    // ══════════════════════════════════════════════════════════════
    // DamageSource 构造
    // ══════════════════════════════════════════════════════════════

    /**
     * 造一个「谁用什么法术打的」伤害来源。
     *
     * <p>用原版 {@code indirect_magic}：{@code getEntity()} 是施法者（所以击杀归因、
     * 掉落 {@code killer} 条件、本模组的击杀回蓝都能认出来），
     * {@code getDirectEntity()} 同样指向施法者（避免有些模组读 directEntity 时拿到 null）。</p>
     *
     * <p>施法者为 {@code null} 时（陷阱 / 环境法术）以受击者自己的 {@code magic()} 来源占位，
     * 掉落不会被归给任何人 —— 这与原版"环境伤害"的语义一致。</p>
     */
    @NotNull
    public static DamageSource spellSourceOf(@NotNull LivingEntity target, @Nullable Player caster) {
        if (caster != null) {
            return caster.damageSources().indirectMagic(caster, caster);
        }
        return target.damageSources().magic();
    }

    // ══════════════════════════════════════════════════════════════
    // 主入口
    // ══════════════════════════════════════════════════════════════

    /**
     * 对目标造成一次法术伤害（改血路径 + 原版语义的来源与掉落）。
     *
     * @param target 受击者
     * @param spell  使用的法术物品（用于事件与附魔判定；可用 {@link ItemStack#EMPTY}）
     * @param caster 施法者，可为 {@code null}
     * @param amount 伤害（正数）
     * @return 是否真的造成了伤害
     */
    public static boolean hurt(@NotNull LivingEntity target, @NotNull ItemStack spell,
                               @Nullable ServerPlayer caster, float amount) {
        return hurt(target, spellSourceOf(target, caster), spell, caster, amount, true);
    }

    /**
     * 显式指定伤害来源的版本 —— 想让伤害吃某个特定 {@code DamageType} 时用这个。
     */
    public static boolean hurt(@NotNull LivingEntity target, @NotNull DamageSource source,
                               @NotNull ItemStack spell, @Nullable ServerPlayer caster, float amount) {
        return hurt(target, source, spell, caster, amount, true);
    }

    /**
     * 按「目标最大生命值百分比」造成法术伤害。
     *
     * @param percent 百分比，例如 {@code 20} 表示最大生命值的 20%
     */
    public static boolean hurtPercentOfMax(@NotNull LivingEntity target, @NotNull ItemStack spell,
                                          @Nullable ServerPlayer caster, float percent) {
        if (percent <= 0f) {
            return false;
        }
        float max = target.getMaxHealth();
        if (!Float.isFinite(max) || max <= 0f) {
            return false;
        }
        return hurt(target, spell, caster, max * (percent / 100f));
    }

    /**
     * <b>完全走原版 {@code hurt}</b>：护甲 / 抗性 / 无敌帧 / 减伤全部生效。
     *
     * <p>不碰 VitalProbe。想"打穿抗改血模组"就用 {@link #hurt}；想"和原版近战一样"
     * 就用这个。两者都会抛 {@link MagicDamageEvent} 与 Forge 的伤害事件。</p>
     */
    public static boolean hurtVanilla(@NotNull LivingEntity target, @NotNull ItemStack spell,
                                     @Nullable ServerPlayer caster, float amount) {
        return hurt(target, spellSourceOf(target, caster), spell, caster, amount, false);
    }

    /**
     * 主实现。
     *
     * @param useHealthWrite {@code true} = 走 VitalProbe 改血（绕过护甲），
     *                       {@code false} = 只走原版 {@code hurt}
     */
    private static boolean hurt(@NotNull LivingEntity target, @NotNull DamageSource source,
                                @NotNull ItemStack spell, @Nullable ServerPlayer caster,
                                float amount, boolean useHealthWrite) {
        if (target == null || amount <= 0f || !target.isAlive()) {
            return false;
        }
        if (target.level().isClientSide()) {
            return false;
        }
        // 创造 / 旁观豁免：改血会绕过原版 Player.hurt 里的创造免疫，必须自己挡
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        // ---- 1. 击杀归因：必须在伤害之前写，否则 die() 里的掉落拿不到 killer ----
        applyKillCredit(caster, target);

        // ---- 2. 本模组的法术伤害事件 ----
        MagicDamageEvent magic = new MagicDamageEvent(caster, spell, target, source, amount);
        if (MinecraftForge.EVENT_BUS.post(magic) || magic.isCanceled()) {
            return false;
        }
        amount = magic.getAmount();
        if (amount <= 0f) {
            return false;
        }

        // ---- 3/4. Forge 的伤害事件：让护甲以外的减伤逻辑（格挡、友伤、别的模组）照常介入 ----
        LivingHurtEvent hurtEvent = new LivingHurtEvent(target, source, amount);
        if (MinecraftForge.EVENT_BUS.post(hurtEvent) || hurtEvent.isCanceled()) {
            return false;
        }
        amount = hurtEvent.getAmount();
        if (amount <= 0f) {
            return false;
        }
        LivingDamageEvent damageEvent = new LivingDamageEvent(target, source, amount);
        MinecraftForge.EVENT_BUS.post(damageEvent);
        amount = damageEvent.getAmount();
        if (amount <= 0f) {
            return false;
        }

        // ---- 5. 结算 ----
        float applied;
        if (useHealthWrite && BCCoreCompat.isHealthApiAvailable()) {
            float before = BCCoreCompat.getHealth(target);
            if (Float.isNaN(before)) {
                // 读不到有效血量（目标没挂 VitalProbe 的引擎）→ 退回原版
                applied = vanillaApply(target, source, amount);
            } else {
                float after = before - amount;   // 故意不钳制：允许 ≤0，避免浮点残值打不死
                if (BCCoreCompat.setHealth(target, after)) {
                    applied = amount;
                    if (after <= 0f) {
                        finishOff(target, source);
                    }
                } else {
                    // 写失败（目标是"抗改血"实现）→ 退回原版
                    applied = vanillaApply(target, source, amount);
                }
            }
        } else {
            applied = vanillaApply(target, source, amount);
        }

        if (applied <= 0f) {
            return false;
        }

        // ---- 7. 结算完成 ----
        MinecraftForge.EVENT_BUS.post(new MagicDamageEvent.Post(caster, spell, target, source, applied));
        return true;
    }

    /** 原版 {@code hurt}，带异常保护。 */
    private static float vanillaApply(@NotNull LivingEntity target, @NotNull DamageSource source, float amount) {
        try {
            return target.hurt(source, amount) ? amount : 0f;
        } catch (Throwable t) {
            ModUtils.LOGGER.warn("[SpellDamageUtil] 原版兜底伤害失败：{}", t.toString());
            return 0f;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 击杀归因 / 死亡流程
    // ══════════════════════════════════════════════════════════════

    /**
     * 把击杀者写进目标的「最后被谁打」字段。
     *
     * <p>{@code LivingEntity.hurt} 原本会做这件事，改血路径必须手动补。它决定了：</p>
     * <ul>
     *   <li>战利品表里的 {@code killer} 条件（击杀者专用掉落）；</li>
     *   <li>原版进度（advancement）的触发；</li>
     *   <li>{@code LivingDeathEvent.getSource().getEntity()} 是谁 —— 本模组的
     *       击杀回蓝、威胁点数都读它。</li>
     * </ul>
     */
    public static void applyKillCredit(@Nullable Entity attacker, @NotNull LivingEntity target) {
        if (attacker == null) {
            return;
        }
        try {
            if (attacker instanceof Player player) {
                target.setLastHurtByPlayer(player);
            } else if (attacker instanceof LivingEntity living) {
                target.setLastHurtByMob(living);
            }
        } catch (Throwable t) {
            ModUtils.LOGGER.warn("[SpellDamageUtil] 写入击杀归因失败：{}", t.toString());
        }
    }

    /**
     * 「收尾」：血量已经 ≤0，用<b>原始来源</b>再走一次真实的死亡流程。
     *
     * <p>这是整层封装里最关键的一步 —— 掉落实现在 {@code die(source)} 里，
     * 而 {@code die} 是 {@code protected}，够不着。所以只能通过
     * {@code hurt(source, 极大值)} 把它引出来。</p>
     *
     * <p>刻意<b>不用</b> {@code kill()}：它内部用的是 {@code genericKill} 来源，
     * 会把已经写好的击杀归因冲掉，掉落也就归不到施法者头上。只有当
     * {@code hurt} 也收不掉（例如被别的模组取消）时才退到 {@code kill()} 兜底。</p>
     */
    public static void finishOff(@NotNull LivingEntity target, @Nullable DamageSource source) {
        if (!target.isAlive()) {
            return;
        }
        // 清掉无敌帧与受击红闪，保证这一下不会被 invulnerableTime 分支吃掉
        BCCoreCompat.clearHurtGuard(target);

        DamageSource src = source != null ? source : target.damageSources().genericKill();
        try {
            target.hurt(src, Float.MAX_VALUE);
        } catch (Throwable t) {
            ModUtils.LOGGER.warn("[SpellDamageUtil] 终结击失败：{}", t.toString());
        }
        if (target.isAlive()) {
            // 兜底：至少让它死。注意这条路的掉落会归到 genericKill
            try {
                target.kill();
            } catch (Throwable ignored) {
                // 尽力而为
            }
        }
    }
}
