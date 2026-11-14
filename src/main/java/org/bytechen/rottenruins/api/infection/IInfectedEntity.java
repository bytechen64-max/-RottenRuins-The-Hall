package org.bytechen.rottenruins.api.infection;

import net.minecraft.resources.ResourceLocation;

/**
 * 感染生物接口。
 * <p>
 * 实现此接口的实体被标记为"感染生物"，并可以返回其感染类型。
 * 使用 {@link ResourceLocation} 而非纯字符串作为感染类型标识符，
 * 以保证不同模组间的命名空间隔离，方便模组联动
 * （如 {@code mod_a:zombie} 与 {@code mod_b:parasite} 不会冲突）。
 * <p>
 * 使用示例：
 * <pre>{@code
 * public class MyInfectedZombie extends Zombie implements IInfectedEntity {
 *     public static final ResourceLocation TYPE =
 *         ResourceLocation.fromNamespaceAndPath("mymod", "zombie");
 *
 *     @Override
 *     public ResourceLocation getInfectionType() {
 *         return TYPE;
 *     }
 * }
 * }</pre>
 * <p>
 * 下游模组可以通过 {@code instanceof IInfectedEntity} 检测实体是否为感染生物，
 * 并通过 {@link #getInfectionType()} 获取具体类型以实现联动逻辑。
 *
 * @see IKillCounter
 */
public interface IInfectedEntity {

    /**
     * 获取该感染生物的感染类型。
     * <p>
     * 返回值应为一个带命名空间的 {@link ResourceLocation}，
     * 建议格式为 {@code <模组id>:<类型名>}。
     *
     * @return 感染类型的 ResourceLocation，不为 null
     */
    ResourceLocation getInfectionType();
}
