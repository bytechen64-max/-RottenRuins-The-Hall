package org.bytechen.hall.api.magic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>法术造成伤害</b>事件 —— 在伤害真正落到目标身上<b>之前</b>抛出，可取消、可改数值。
 *
 * <p>监听方式（FORGE 总线）：</p>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onSpellDamage(MagicDamageEvent event) {
 *     // 例：火系法术打冰系生物翻倍
 *     if (event.getSpell().getItem() == MySpells.FIREBALL.get()
 *             && event.getTarget().getType().is(MyTags.ICE_WEAK)) {
 *         event.setAmount(event.getAmount() * 2f);
 *     }
 *     // 例：PVP 关闭时免伤
 *     if (!serverPvpAllowed && event.getTarget() instanceof Player) {
 *         event.setCanceled(true);
 *     }
 * }
 * }</pre>
 *
 * <h3>为什么需要它，而不是直接监听 {@code LivingHurtEvent}</h3>
 * <p>法术伤害走的是 {@code SpellDamageUtil} → VitalProbe 改血路径，
 * <b>不是</b>原版 {@code hurt}。改血路径下 Forge 的 {@code LivingHurtEvent}
 * 只能看到最后一记"终结击"，看不到真实的法术数值。这个事件是唯一能拿到
 * 「谁、用什么法术、打谁、多少伤害」这四个要素的地方。</p>
 *
 * <p>{@code SpellDamageUtil} 会在抛出本事件之后<b>仍然</b>抛出 Forge 的
 * {@code LivingHurtEvent} / {@code LivingDamageEvent}，所以已有的减伤逻辑
 * （护甲、格挡、友伤）照常生效。</p>
 */
public class MagicDamageEvent extends Event {

    private final ServerPlayer caster;
    private final ItemStack spell;
    private final LivingEntity target;
    private final DamageSource source;
    private float amount;

    public MagicDamageEvent(@Nullable ServerPlayer caster, @NotNull ItemStack spell,
                            @NotNull LivingEntity target, @NotNull DamageSource source,
                            float amount) {
        this.caster = caster;
        this.spell = spell;
        this.target = target;
        this.source = source;
        this.amount = amount;
    }

    /** 施法者；法术不是玩家放的（例如陷阱）时为 {@code null}。 */
    @Nullable
    public ServerPlayer getCaster() {
        return caster;
    }

    /** 使用的法术物品。 */
    @NotNull
    public ItemStack getSpell() {
        return spell;
    }

    /** 受击者。 */
    @NotNull
    public LivingEntity getTarget() {
        return target;
    }

    /** 伤害来源（已带好施法者归因，可直接用于掉落与击杀归属）。 */
    @NotNull
    public DamageSource getSource() {
        return source;
    }

    /** 结算前的伤害数值。 */
    public float getAmount() {
        return amount;
    }

    /** 改写伤害数值。负数会被钳到 0。 */
    public void setAmount(float amount) {
        this.amount = Math.max(0f, amount);
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    /**
     * 伤害<b>已结算</b>之后抛出。不可取消，用来做实伤统计、吸血、连击数之类。
     */
    public static class Post extends Event {

        private final ServerPlayer caster;
        private final ItemStack spell;
        private final LivingEntity target;
        private final DamageSource source;
        private final float applied;

        public Post(@Nullable ServerPlayer caster, @NotNull ItemStack spell,
                    @NotNull LivingEntity target, @NotNull DamageSource source, float applied) {
            this.caster = caster;
            this.spell = spell;
            this.target = target;
            this.source = source;
            this.applied = applied;
        }

        @Nullable
        public ServerPlayer getCaster() {
            return caster;
        }

        @NotNull
        public ItemStack getSpell() {
            return spell;
        }

        @NotNull
        public LivingEntity getTarget() {
            return target;
        }

        @NotNull
        public DamageSource getSource() {
            return source;
        }

        /** 实际生效的伤害。 */
        public float getApplied() {
            return applied;
        }

        /** 这次伤害是否直接打死了目标。 */
        public boolean wasLethal() {
            return !target.isAlive();
        }
    }
}
