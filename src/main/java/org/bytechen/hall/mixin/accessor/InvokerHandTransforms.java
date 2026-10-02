package org.bytechen.hall.mixin.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 打开 {@code ItemInHandRenderer.applyItemArmTransform}。
 *
 * <h3>为什么需要它</h3>
 * <p>第一人称摆一个物品，正确的起点是原版那条公式：</p>
 * <pre>
 *   translate(armX * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
 * </pre>
 * <p>它是 {@code private}，而我们取消了 {@code renderArmWithItem} 之后
 * 就够不到它了，所以只能由 Mixin 生成桥接。</p>
 *
 * <h3>为什么不算一遍而直接调原版</h3>
 * <p>这条基准对<b>任何物品</b>都成立（原版第一人称的剑、镐、盾都从它出发），
 * 所以先套它、再叠很小的差值，就能保证武器落在"正常手持"的可见位置附近。
 * 参考模组 ArcaneVortex 的 {@code renderInstantBlocking} 也是这个结构；
 * 而早先我改用一组手抄的绝对偏移，结果武器被移到画面外、玩家看到"空手"——
 * 绝对偏移对每个物品模型的支点高度都不一样，本来就不可移植。</p>
 *
 * <p>注：这里<b>不需要</b> {@code renderPlayerArm}。原版画手持物品时本来就不画手臂
 * （手臂只在手上什么都没拿时才画），参考实现同样如此。</p>
 *
 * <p>方法名写成 Mojang 名即可，Mixin 会按 refmap 在运行期映射到 SRG。</p>
 */
@Mixin(ItemInHandRenderer.class)
public interface InvokerHandTransforms {

    /**
     * 复刻 {@code ItemInHandRenderer.applyItemArmTransform}：原版第一人称
     * <b>普通手持</b>物品的位移基准。
     */
    @Invoker("applyItemArmTransform")
    void hall$applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float equipProgress);
}
