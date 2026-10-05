package org.bytechen.hall.api.magic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import org.bytechen.hall.overworld.registry.items.magic.bases.UsingType;
import org.jetbrains.annotations.NotNull;

/**
 * <b>释放法术</b>事件 —— 在法术真正生效<b>之前</b>抛出，可取消、可改耗蓝。
 *
 * <p>监听方式（FORGE 总线）：</p>
 * <pre>{@code
 * @Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
 * public class MyMagicRules {
 *     @SubscribeEvent
 *     public static void onCast(MagicCastEvent event) {
 *         // 例：某 debuff 下禁止施法
 *         if (event.getCaster().hasEffect(MyEffects.SILENCE.get())) {
 *             event.setCanceled(true);
 *             return;
 *         }
 *         // 例：某附魔让耗蓝减半
 *         if (hasFocusEnchant(event.getSpell())) {
 *             event.setManaCost(event.getManaCost() / 2);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>时机与语义</h3>
 * <ul>
 *   <li>只在<b>服务端</b>抛出（施法以服务端为准）。</li>
 *   <li>抛出时<b>还没有</b>扣法力、<b>还没有</b>进冷却 —— 所以取消它不会白扣蓝、不会白进 CD。</li>
 *   <li>改 {@link #setManaCost(int)} 只影响这一次的扣费，不会写回物品或修饰器。</li>
 *   <li>本事件通过 {@code setCanceled(true)} 取消后，{@code MagicHandle} 会直接返回
 *       {@link CastResult#CANCELLED}，法力与冷却都不动。</li>
 * </ul>
 *
 * <p>想"释放之后"做事（例如放开一个持续施法的标记、播放音效）请用 {@link Post}。</p>
 */
public class MagicCastEvent extends Event {

    private final ServerPlayer caster;
    private final ItemStack spell;
    private final UsingType castType;
    private final InteractionHand hand;
    private final int slot;
    private final float chargeRatio;
    private int manaCost;

    public MagicCastEvent(@NotNull ServerPlayer caster, @NotNull ItemStack spell,
                          @NotNull UsingType castType, @NotNull InteractionHand hand,
                          int slot, float chargeRatio, int manaCost) {
        this.caster = caster;
        this.spell = spell;
        this.castType = castType;
        this.hand = hand;
        this.slot = slot;
        this.chargeRatio = chargeRatio;
        this.manaCost = manaCost;
    }

    /** 施法者。 */
    @NotNull
    public ServerPlayer getCaster() {
        return caster;
    }

    /** 本次要释放的法术物品（来自魔法槽，不是手上的法杖）。 */
    @NotNull
    public ItemStack getSpell() {
        return spell;
    }

    /** 释放模式：{@code IMMEDIATELY} / {@code CHARGE} / {@code KEEP}。 */
    @NotNull
    public UsingType getCastType() {
        return castType;
    }

    /** 触发用的那只手（法杖所在的手）。 */
    @NotNull
    public InteractionHand getHand() {
        return hand;
    }

    /** 法术来自哪个槽位下标；不是从槽位释放时为 {@code -1}。 */
    public int getSlot() {
        return slot;
    }

    /**
     * 蓄力完成度 {@code 0.0~1.0}。
     * <p>非 {@code CHARGE} 模式恒为 {@code 1.0}。想做"蓄力越久打越痛"就按它缩放伤害。</p>
     */
    public float getChargeRatio() {
        return chargeRatio;
    }

    /** 本次实际要扣的法力。 */
    public int getManaCost() {
        return manaCost;
    }

    /**
     * 改写本次扣费。
     * <p>只会影响这一次；想永久改就用 {@link MagicStatProvider#modifyManaCost}。
     */
    public void setManaCost(int manaCost) {
        this.manaCost = Math.max(0, manaCost);
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    /**
     * 释放<b>已成功</b>之后抛出，用来挂"后续逻辑"。
     *
     * <p>不可取消 —— 法力已经扣了、冷却已经进了，取消这个事件没有意义。</p>
     */
    public static class Post extends Event {

        private final ServerPlayer caster;
        private final ItemStack spell;
        private final UsingType castType;
        private final int manaSpent;
        private final float chargeRatio;
        private final CastResult result;

        public Post(@NotNull ServerPlayer caster, @NotNull ItemStack spell,
                    @NotNull UsingType castType, int manaSpent, float chargeRatio,
                    @NotNull CastResult result) {
            this.caster = caster;
            this.spell = spell;
            this.castType = castType;
            this.manaSpent = manaSpent;
            this.chargeRatio = chargeRatio;
            this.result = result;
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

        /** 本次真实扣除的法力。 */
        public int getManaSpent() {
            return manaSpent;
        }

        /**
         * 本次蓄力完成度 {@code 0.0~1.0}；非 {@code CHARGE} 恒为 {@code 1.0}。
         * <p>用来区分"蓄满放的"和"提前放的"（后者会小于 1）。</p>
         */
        public float getChargeRatio() {
            return chargeRatio;
        }

        /** 结果，恒为 {@link CastResult#SUCCESS}（失败不会抛 Post）。 */
        @NotNull
        public CastResult getCastResult() {
            return result;
        }

        /** 便捷抛出。 */
        public void post() {
            MinecraftForge.EVENT_BUS.post(this);
        }
    }
}
