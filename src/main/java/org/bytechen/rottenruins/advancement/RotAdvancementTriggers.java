package org.bytechen.rottenruins.advancement;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;

/**
 * 本模组自定义成就判据的集中注册 / 触发入口。
 *
 * <p>新增一个自定义判据时的固定步骤：</p>
 * <ol>
 *   <li>仿照 {@link FirstJoinTrigger} 写一个 {@code SimpleCriterionTrigger} 子类；</li>
 *   <li>在 {@link #registerAll()} 里注册它的单例（判据 id 不能和原版/其它模组撞车）；</li>
 *   <li>在数据生成的 {@code AdvancementProviderData} 里用对应 {@code TriggerInstance} 写 criterion；</li>
 *   <li>在合适的服务端事件里调用它的 {@code trigger(ServerPlayer)}，或在下面补一个静态快捷方法。</li>
 * </ol>
 */
public final class RotAdvancementTriggers {

    /** 防止 commonSetup 被重复调用时重复注册（原版对同一判据 id 重复注册会直接抛异常）。 */
    private static boolean registered;

    private RotAdvancementTriggers() {
    }

    /**
     * 注册全部自定义判据。
     *
     * <p>必须放在 {@code FMLCommonSetupEvent} 里（见 {@code HallMod#commonSetup}）：它早于
     * 服务端解析进度数据包，晚于类加载，因此数据包里的 {@code rotten_ruins:first_join}
     * 能正确解析成上面的判据。</p>
     */
    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        CriteriaTriggers.register(FirstJoinTrigger.INSTANCE);
    }

    /** 玩家进入世界时发放「初次踏入世界」判据（进度只发一次，重复登录自动 no-op）。 */
    public static void triggerFirstJoin(ServerPlayer player) {
        FirstJoinTrigger.INSTANCE.trigger(player);
    }
}
