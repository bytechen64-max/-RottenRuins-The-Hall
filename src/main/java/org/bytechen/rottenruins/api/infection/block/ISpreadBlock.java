package org.bytechen.rottenruins.api.infection.block;

import net.minecraft.resources.ResourceLocation;

/**
 * 可扩散方块接口。
 * <p>
 * 任意方块实现此接口后，扩散系统（如 {@code BlockSpreadManager}）可通过
 * {@link #getSpreadType()} 获取该方块对应的扩散类型标识，
 * 从而自动匹配正确的转换规则，无需手动传递 type 参数。
 * <p>
 * 典型用法：
 * <pre>{@code
 * public class MySpreadBlock extends Block implements ISpreadBlock {
 *     public MySpreadBlock(Properties p) { super(p); }
 *
 *     @Override
 *     public ResourceLocation getSpreadType() {
 *         return new ResourceLocation("mymod", "spread");
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface ISpreadBlock {

    /**
     * 获取该方块对应的扩散类型标识。
     * <p>
     * 返回值会作为 {@code BlockSpreadManager.applySpread} 的 {@code type} 参数。
     * 不同模组应返回各自的命名空间以区分规则。
     *
     * @return 扩散类型 ResourceLocation，如 {@code hall:spread}
     */
    ResourceLocation getSpreadType();
}
