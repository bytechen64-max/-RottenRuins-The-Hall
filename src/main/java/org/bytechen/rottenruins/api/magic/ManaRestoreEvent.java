package org.bytechen.rottenruins.api.magic;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 法力回复事件：法力即将被回复时抛出（FORGE 总线），可取消、可改数值。
 * REGEN 每 tick 抛一次（一个玩家 20 次/秒），监听器要尽量轻。
 * amount 用 float 是因为自然回蓝每 tick 只有 0.05，在这里取整会永远回不上来（下游有小数累加器）。
 */
public class ManaRestoreEvent extends Event {

    /** 回复来源；REGEN 每 tick 一次。 */
    public enum Cause {
        REGEN,
        KILL,
        SPELL,
        COMMAND,
        OTHER
    }

    private final Player player;
    private final Cause cause;
    private final LivingEntity victim;
    private float amount;

    public ManaRestoreEvent(@NotNull Player player, @NotNull Cause cause,
                           @Nullable LivingEntity victim, float amount) {
        this.player = player;
        this.cause = cause;
        this.victim = victim;
        this.amount = amount;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public Cause getCause() {
        return cause;
    }

    /** 触发本次回复的生物（只有 {@link Cause#KILL} 有值）；它的 getMaxHealth() 就是回蓝依据。 */
    @Nullable
    public LivingEntity getVictim() {
        return victim;
    }

    public float getAmount() {
        return amount;
    }

    /** 改写回复量；负数会被钳到 0。 */
    public void setAmount(float amount) {
        this.amount = Math.max(0f, amount);
    }

    public void multiplyAmount(float factor) {
        setAmount(this.amount * Math.max(0f, factor));
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    /**
     * 回复已结算后抛出（不可取消），用来做统计 / HUD 提示 / 特效。
     * requested 是事件链开始时的请求量；applied 是实际进入法力的整数点（满蓝时为 0）。
     */
    public static class Post extends Event {

        private final Player player;
        private final Cause cause;
        private final float requested;
        private final int applied;

        public Post(@NotNull Player player, @NotNull Cause cause, float requested, int applied) {
            this.player = player;
            this.cause = cause;
            this.requested = requested;
            this.applied = applied;
        }

        @NotNull
        public Player getPlayer() {
            return player;
        }

        @NotNull
        public Cause getCause() {
            return cause;
        }

        public float getRequested() {
            return requested;
        }

        public int getApplied() {
            return applied;
        }
    }
}
