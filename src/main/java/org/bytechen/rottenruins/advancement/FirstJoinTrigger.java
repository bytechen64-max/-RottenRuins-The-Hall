package org.bytechen.rottenruins.advancement;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bytechen.rottenruins.HallMod;

/**
 * 「玩家初次进入世界」判据触发器。
 *
 * <p>原版没有「登录即触发」的进度判据：{@code minecraft:tick} 语义上是每 tick 都在判定，
 * 与「第一次进入世界」不是一回事。所以这里按原版 {@link SimpleCriterionTrigger} 的写法
 * 自建一个最小判据：</p>
 * <ul>
 *   <li>数据侧：进度的 criterion 写成 {@code "trigger": "rotten_ruins:first_join"}，不带额外条件；</li>
 *   <li>运行侧：{@code PlayerEvent.PlayerLoggedInEvent} 里调用 {@link #trigger(ServerPlayer)}
 *       （见 {@code AdvancementEventHandler}）。</li>
 * </ul>
 *
 * <p>进度是「每个玩家 / 每个存档」只发放一次的数据，重复登录时原版的 award 天然是 no-op，
 * 不需要额外记录「这个玩家是否已经登录过」。</p>
 *
 * <p>判据必须通过 {@link net.minecraft.advancements.CriteriaTriggers#register} 注册，
 * 且注册要早于服务端解析进度数据包 —— 调用点见 {@link RotAdvancementTriggers#registerAll()}。</p>
 */
public class FirstJoinTrigger extends SimpleCriterionTrigger<FirstJoinTrigger.TriggerInstance> {

    /** 判据 id；成就 JSON 里 criterion 的 trigger 字段写这个。 */
    public static final ResourceLocation ID = new ResourceLocation(HallMod.MODID, "first_join");

    /** 单例：注册与触发都走它。 */
    public static final FirstJoinTrigger INSTANCE = new FirstJoinTrigger();

    private FirstJoinTrigger() {
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate playerPredicate,
                                            DeserializationContext context) {
        // 该判据不需要任何条件；生成出来的 JSON 里 conditions 会被省略。
        return new TriggerInstance(playerPredicate);
    }

    /** 对指定玩家发放一次触发；没有监听该进度的监听者 / 进度已完成时静默无副作用。 */
    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    /** 判据实例：不携带任何条件。 */
    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        public TriggerInstance(ContextAwarePredicate playerPredicate) {
            super(ID, playerPredicate);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            // 返回空对象 → Criterion.serializeToJson 会省略 "conditions"，生成最简 JSON：
            // {"trigger": "rotten_ruins:first_join"}
            return new JsonObject();
        }
    }
}
