package org.bytechen.hall.api.magic;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>法力回复</b>事件 —— 每次法力即将被回复时抛出，可取消、可改数值。
 *
 * <p>监听方式（FORGE 总线）：</p>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onManaRestore(ManaRestoreEvent event) {
 *     // 例：按属性修饰器缩放一切回复量
 *     AttributeInstance inst = event.getPlayer().getAttribute(MyAttributes.MANA_RESTORE.get());
 *     if (inst != null) {
 *         event.setAmount((float) (event.getAmount() * inst.getValue()));
 *     }
 *     // 例：某个 debuff 期间禁止回蓝
 *     if (event.getPlayer().hasEffect(MyEffects.MANA_BLOCK.get())) {
 *         event.setCanceled(true);
 *     }
 * }
 * }</pre>
 *
 * <h3>什么时候会抛</h3>
 * <ul>
 *   <li>{@link Cause#REGEN} —— <b>每 tick</b>一次自然回蓝。注意频率：一个玩家 20 次/秒，
 *       所以监听器要尽量轻（别在这里做字符串拼接、遍历背包之类的活）。</li>
 *   <li>{@link Cause#KILL} —— 击杀生物按最大生命值回蓝，一次释放抛一次。</li>
 *   <li>{@link Cause#SPELL} / {@link Cause#COMMAND} / {@link Cause#OTHER} —— 外部主动加蓝。</li>
 * </ul>
 *
 * <h3>为什么要允许改小数</h3>
 * <p>{@link #getAmount()} 是 {@code float} 而不是 {@code int}：自然回蓝的速率换算到每 tick
 * 是 {@code 1/20 = 0.05} 这种小数，如果在这里就取整，"每秒 +1" 会永远回不上来。
 * {@code TwistedPoint} 内部有一个小数累加器，收到多少就攒多少、攒够整点才真正进法力
 * —— 所以你把数值乘 1.5 得到的是精确的"每秒 1.5"，而不是"每秒 1 或 2"。</p>
 */
public class ManaRestoreEvent extends Event {

    /** 这次回复是哪来的。 */
    public enum Cause {
        /** 自然回蓝（每 tick 一次）。 */
        REGEN,
        /** 击杀生物按最大生命值折算。 */
        KILL,
        /** 法术效果回复（吸血、回蓝术之类）。 */
        SPELL,
        /** 命令 / 调试。 */
        COMMAND,
        /** 其它来源。 */
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

    /** 要回蓝的玩家。 */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /** 回复来源。 */
    @NotNull
    public Cause getCause() {
        return cause;
    }

    /**
     * 触发这次回复的生物（只有 {@link Cause#KILL} 有值）。
     * <p>它的 {@code getMaxHealth()} 就是"按最大生命值回蓝"依据的那个量。</p>
     */
    @Nullable
    public LivingEntity getVictim() {
        return victim;
    }

    /** 本次要回复的量（可能是小数）。 */
    public float getAmount() {
        return amount;
    }

    /** 改写回复量。负数会被钳到 0。 */
    public void setAmount(float amount) {
        this.amount = Math.max(0f, amount);
    }

    /** 便捷：按倍率缩放。 */
    public void multiplyAmount(float factor) {
        setAmount(this.amount * Math.max(0f, factor));
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    /**
     * 回复<b>已结算</b>后抛出（不可取消），用来做统计 / HUD 提示 / 特效。
     *
     * @param requested 事件链开始时的请求量
     * @param applied   实际进入法力的整数点（可能因为已满而被截断）
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

        /** 事件链结束后确定的回复量。 */
        public float getRequested() {
            return requested;
        }

        /** 实际加进去的整数点；法力已满时会是 0。 */
        public int getApplied() {
            return applied;
        }
    }
}
