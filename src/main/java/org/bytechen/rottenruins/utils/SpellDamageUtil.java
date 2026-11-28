package org.bytechen.rottenruins.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.bytechen.rottenruins.api.magic.MagicDamageEvent;
import org.bytechen.rottenruins.utils.entity.DirectHealth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>法术伤害工具类</b>：把 {@link org.bytechen.rottenruins.utils.entity.DirectHealth} 的「直接改血」包装成「像原版 {@code hurt} 一样」的
 * 调用。改血绕过 {@code LivingEntity.hurt}，所以必须手动补两件事：① 击杀归因写 {@code lastHurtByPlayer} /
 * {@code lastHurtByMob}（战利品 killer 条件、进度、击杀回蓝都读它）；② 血量 ≤0 后用<b>原始 DamageSource</b> 补一次
 * 真实 hurt 引出 {@code die(source)} → dropAllDeathLoot，不能用 {@code kill()}（genericKill 来源会冲掉归因）。
 * 只在服务端调用；改血路径不吃护甲/抗性/无敌帧（要护甲生效请用 {@link #hurtVanilla}），创造/旁观默认豁免。
 */
public final class SpellDamageUtil {

    private SpellDamageUtil() {
    }

    /**
     * 造一个「谁用什么法术打的」伤害来源：用 {@code indirect_magic}，{@code getEntity()} 与 {@code getDirectEntity()}
     * 都是施法者；施法者为 {@code null}（陷阱/环境法术）时用受击者自己的 {@code magic()} 占位，掉落不归任何人。
     */
    @NotNull
    public static DamageSource spellSourceOf(@NotNull LivingEntity target, @Nullable Player caster) {
        if (caster != null) {
            return caster.damageSources().indirectMagic(caster, caster);
        }
        return target.damageSources().magic();
    }

    /** 寂寒白日的专属冰伤类型（{@code data/rotten_ruins/damage_type/silent_daylight_ice.json}）。 */
    private static final ResourceKey<DamageType> SILENT_DAYLIGHT_ICE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(org.bytechen.rottenruins.HallMod.MODID, "silent_daylight_ice"));

    /**
     * 造一个<b>冰系</b>伤害来源：类型是专属冰伤，攻击者是施法者。不用 {@code freeze()}（没有攻击者，掉落 killer
     * 条件与死亡消息会缺）也不用 {@code playerAttack}（类型读成近战，看不出冰伤）；数据包缺失时退回 {@code FREEZE}。
     */
    @NotNull
    public static DamageSource iceSourceOf(@NotNull LivingEntity target, @Nullable Player caster) {
        Holder<DamageType> type = iceDamageTypeOf(target);
        if (caster == null) {
            return new DamageSource(type);
        }
        return new DamageSource(type, caster, caster);
    }

    @NotNull
    private static Holder<DamageType> iceDamageTypeOf(@NotNull LivingEntity target) {
        var registry = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        try {
            return registry.getHolderOrThrow(SILENT_DAYLIGHT_ICE);
        } catch (Throwable t) {
            return registry.getHolderOrThrow(DamageTypes.FREEZE);
        }
    }

    /** 对目标造成一次法术伤害（改血路径 + 原版语义的来源与掉落）。 */
    public static boolean hurt(@NotNull LivingEntity target, @NotNull ItemStack spell,
                               @Nullable ServerPlayer caster, float amount) {
        return hurt(target, spellSourceOf(target, caster), spell, caster, amount, true);
    }

    public static boolean hurt(@NotNull LivingEntity target, @NotNull DamageSource source,
                               @NotNull ItemStack spell, @Nullable ServerPlayer caster, float amount) {
        return hurt(target, source, spell, caster, amount, true);
    }

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

    /** <b>完全走原版 {@code hurt}</b>：护甲 / 抗性 / 无敌帧 / 减伤全部生效（不碰直接改血）。 */
    public static boolean hurtVanilla(@NotNull LivingEntity target, @NotNull ItemStack spell,
                                     @Nullable ServerPlayer caster, float amount) {
        return hurt(target, spellSourceOf(target, caster), spell, caster, amount, false);
    }

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

        applyKillCredit(caster, target);

        MagicDamageEvent magic = new MagicDamageEvent(caster, spell, target, source, amount);
        if (MinecraftForge.EVENT_BUS.post(magic) || magic.isCanceled()) {
            return false;
        }
        amount = magic.getAmount();
        if (amount <= 0f) {
            return false;
        }

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

        float applied;
        if (useHealthWrite) {
            float before = target.getHealth();
            float after = before - amount;   // 不钳制：允许 <= 0，避免浮点残值打不死
            DirectHealth.setHealth(target, after);
            applied = amount;
            if (after <= 0f) {
                finishOff(target, source);
            }
        } else {
            applied = vanillaApply(target, source, amount);
        }
        if (applied <= 0f) {
            return false;
        }

        MinecraftForge.EVENT_BUS.post(new MagicDamageEvent.Post(caster, spell, target, source, applied));
        return true;
    }

    private static float vanillaApply(@NotNull LivingEntity target, @NotNull DamageSource source, float amount) {
        try {
            return target.hurt(source, amount) ? amount : 0f;
        } catch (Throwable t) {
            ModUtils.LOGGER.warn("[SpellDamageUtil] 原版兜底伤害失败：{}", t.toString());
            return 0f;
        }
    }

    /**
     * 准备一次<b>必须真的落下去</b>的命中：清空无敌帧 + 拉满受击红闪（只在服务端调用）。同一目标 20 tick
     * 内要挨第二下的效果，每次结算前都要调，否则第二下会被 {@code invulnerableTime > 10} 分支当成重复伤害
     * （同数值只补差值 = 0）；红闪要自己拉满，因为改血路径不经过 {@code LivingEntity.hurt}，而清无敌帧的
     * {@link DirectHealth#clearHurtGuard} 顺手把 {@code hurtTime} 清成了 0。
     */
    public static void prepareRepeatHit(@NotNull LivingEntity target) {
        DirectHealth.clearHurtGuard(target);
        target.hurtTime = Math.max(target.hurtTime, target.hurtDuration);
    }

    /** 把击杀者写进目标的「最后被谁打」字段（{@code LivingEntity.hurt} 本来会做，改血路径要手动补）： */
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
     * 「收尾」：血量已 ≤0，用<b>原始来源</b>再走一次真实的死亡流程（掉落在 {@code die(source)} 里，而它是
     * {@code protected}，只能靠 {@code hurt(source, 极大值)} 引出来）。刻意不用 {@code kill()}：genericKill 来源会冲掉归因。
     */
    public static void finishOff(@NotNull LivingEntity target, @Nullable DamageSource source) {
        if (!target.isAlive()) {
            return;
        }
        DirectHealth.clearHurtGuard(target);

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
            }
        }
    }
}
