package org.bytechen.rottenruins.api.magic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 法术造成伤害事件，在伤害落地前抛出，可取消、可改数值。
 * 法术伤害走 SpellDamageUtil → VitalProbe 改血路径而非原版 hurt，
 * 所以只有这里能同时拿到施法者、法术、目标与数值；之后仍会照常抛出 LivingHurtEvent。
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

    /** 施法者；法术不是玩家放的（例如陷阱）时为 null。 */
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

    /** 伤害来源（已带好施法者归因，可直接用于掉落与击杀归属）。 */
    @NotNull
    public DamageSource getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }

    /** 改写伤害数值，负数会被钳到 0。 */
    public void setAmount(float amount) {
        this.amount = Math.max(0f, amount);
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    /** 伤害已结算之后抛出，不可取消；用于实伤统计、吸血、连击数。 */
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

        public float getApplied() {
            return applied;
        }

        public boolean wasLethal() {
            return !target.isAlive();
        }
    }
}
