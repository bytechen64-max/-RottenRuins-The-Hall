package org.bytechen.rottenruins.api.magic;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>释放法术</b>事件 —— 在法术真正生效<b>之前</b>抛出（FORGE 总线），可取消、可改耗蓝。
 *
 * <p>只在<b>服务端</b>抛出；抛出时还没扣法力、还没进冷却，所以取消它不会白扣蓝、不会白进 CD。
 * 需要目标的法术会先解析准星目标再抛（见 {@link #getTarget()}），没锁到目标且不允许自我释放时
 * 根本不抛、直接返回 {@link CastResult#NO_TARGET}。取消后 {@code MagicHandle} 返回
 * {@link CastResult#CANCELLED}。想"释放之后"做事请用 {@link Post}。</p>
 */
public class MagicCastEvent extends Event {

    private final ServerPlayer caster;
    private final ItemStack spell;
    private final UsingType castType;
    private final InteractionHand hand;
    private final int slot;
    private final float chargeRatio;
    private final LivingEntity target;
    private int manaCost;

    public MagicCastEvent(@NotNull ServerPlayer caster, @NotNull ItemStack spell,
                          @NotNull UsingType castType, @NotNull InteractionHand hand,
                          int slot, float chargeRatio, @Nullable LivingEntity target,
                          int manaCost) {
        this.caster = caster;
        this.spell = spell;
        this.castType = castType;
        this.hand = hand;
        this.slot = slot;
        this.chargeRatio = chargeRatio;
        this.target = target;
        this.manaCost = manaCost;
    }

    public MagicCastEvent(@NotNull ServerPlayer caster, @NotNull ItemStack spell,
                          @NotNull UsingType castType, @NotNull InteractionHand hand,
                          int slot, float chargeRatio, int manaCost) {
        this(caster, spell, castType, hand, slot, chargeRatio, null, manaCost);
    }

    @NotNull
    public ServerPlayer getCaster() {
        return caster;
    }

    /** 本次要释放的法术物品（来自魔法槽，不是手上的法杖）。 */
    @NotNull
    public ItemStack getSpell() {
        return spell;
    }

    @NotNull
    public UsingType getCastType() {
        return castType;
    }

    @NotNull
    public InteractionHand getHand() {
        return hand;
    }

    /** 槽位下标；不是从槽位释放时为 {@code -1}。 */
    public int getSlot() {
        return slot;
    }

    /** 蓄力完成度 {@code 0.0~1.0}；非 {@code CHARGE} 模式恒为 {@code 1.0}。 */
    public float getChargeRatio() {
        return chargeRatio;
    }

    /**
     * 只对声明了 {@code needsTarget()} 的法术有意义：框架已用准星解析过一次，不要再自己 raycast
     * （那样容易和框架选到不同实体）。{@code allowSelfCast()} 时可能是施法者自己。
     */
    @Nullable
    public LivingEntity getTarget() {
        return target;
    }

    public boolean hasTarget() {
        return target != null;
    }

    public int getManaCost() {
        return manaCost;
    }

    /** 只影响这一次；想永久改就用 {@link MagicStatProvider#modifyManaCost}。 */
    public void setManaCost(int manaCost) {
        this.manaCost = Math.max(0, manaCost);
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    /**
     * 释放<b>已成功</b>之后抛出，用来挂"后续逻辑"。
     * 不可取消 —— 法力已经扣了、冷却已经进了。
     */
    public static class Post extends Event {

        private final ServerPlayer caster;
        private final ItemStack spell;
        private final UsingType castType;
        private final int manaSpent;
        private final float chargeRatio;
        private final LivingEntity target;
        private final CastResult result;

        public Post(@NotNull ServerPlayer caster, @NotNull ItemStack spell,
                    @NotNull UsingType castType, int manaSpent, float chargeRatio,
                    @Nullable LivingEntity target, @NotNull CastResult result) {
            this.caster = caster;
            this.spell = spell;
            this.castType = castType;
            this.manaSpent = manaSpent;
            this.chargeRatio = chargeRatio;
            this.target = target;
            this.result = result;
        }

        public Post(@NotNull ServerPlayer caster, @NotNull ItemStack spell,
                    @NotNull UsingType castType, int manaSpent, float chargeRatio,
                    @NotNull CastResult result) {
            this(caster, spell, castType, manaSpent, chargeRatio, null, result);
        }

        @NotNull
        public ServerPlayer getCaster() {
            return caster;
        }

        @NotNull
        public ItemStack getSpell() {
            return spell;
        }

        @NotNull
        public UsingType getCastType() {
            return castType;
        }

        public int getManaSpent() {
            return manaSpent;
        }

        /** 蓄力完成度 {@code 0.0~1.0}；非 {@code CHARGE} 恒为 {@code 1.0}（提前放会小于 1）。 */
        public float getChargeRatio() {
            return chargeRatio;
        }

        /** 与 {@link MagicCastEvent#getTarget()} 是同一个对象。 */
        @Nullable
        public LivingEntity getTarget() {
            return target;
        }

        public boolean hasTarget() {
            return target != null;
        }

        /** 恒为 {@link CastResult#SUCCESS}（失败不会抛 Post）。 */
        @NotNull
        public CastResult getCastResult() {
            return result;
        }

        public void post() {
            MinecraftForge.EVENT_BUS.post(this);
        }
    }
}
