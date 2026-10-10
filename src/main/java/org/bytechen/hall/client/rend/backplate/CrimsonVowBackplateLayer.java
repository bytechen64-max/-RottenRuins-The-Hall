package org.bytechen.hall.client.rend.backplate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.joml.Matrix4f;

/**
 * 绯红誓约背板的渲染层。
 *
 * <h3>挂在 PlayerRenderer 上的理由</h3>
 * <p>它是唯一一个"每帧、每个玩家、在世界空间里"都会被调到一次的钩子，
 * 而且拿得到 {@code partialTick}（插值动画必需）。触发条件只需要读主手物品，
 * 所以这里不碰网络层。</p>
 *
 * <h3>两遍绘制</h3>
 * <ol>
 *   <li><b>底</b>（{@code baseType}，普通 alpha 混合）：板面 + 三层图案的暗部。</li>
 *   <li><b>亮</b>（{@code glowType}，加法混合）：五边形顶点、六芒星的亮角、
 *       外圈条带。单独一遍是为了让"发光"是加法而不是覆盖 ——
 *       覆盖会把底下的图案压成纯色，加法才读得出"在发亮"。</li>
 * </ol>
 * <p>两遍之间必须 {@code endBatch}：uniform 是全局状态，
 * {@code Uniform.set()} 只是把值记下来，真正上传发生在
 * {@code ShaderInstance.apply()}（也就是 flush）的时候。攒在一起再 flush，
 * 后一遍设的值会覆盖前一遍 —— 症状是"底层也变成了发光层的颜色"。
 * 这与 {@code MaskLayerRenderer} 的类注释记的是同一条。</p>
 *
 * <h3>为什么只有正面一遍几何</h3>
 * <p>billboard 的板面永远正对镜头，背面永远不会被看到，所以几何里没有补反绕序的
 * 那一份（贴在背上那版必须补，因为会被从两侧看）。RenderType 仍然关掉剔除 ——
 * 留着它是为了少一个"某个角度整个消失"的可能。</p>
 */
public class CrimsonVowBackplateLayer extends RenderLayer<AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>> {

    /** 拿不到 RenderType（着色器没加载）时的诊断只打一次。 */
    private static boolean loggedMissingShader = false;

    public CrimsonVowBackplateLayer(RenderLayerParent<AbstractClientPlayer,
            PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** 便利构造：直接从玩家渲染器建（避免调用处写一长串泛型）。 */
    public static void attachTo(PlayerRenderer renderer) {
        renderer.addLayer(new CrimsonVowBackplateLayer(renderer));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        if (!CrimsonVowBackplateRig.shouldRender(player)) return;

        RenderType baseType = SplendidingShaders.getCrimsonBackplateRenderType();
        RenderType glowType = SplendidingShaders.getCrimsonBackplateGlowRenderType();
        if (baseType == null || glowType == null) {
            if (!loggedMissingShader) {
                loggedMissingShader = true;
                HallMod.LOGGER.warn("[CrimsonVowBackplate] 着色器 rendertype_crimson_backplate 未加载，"
                        + "背板不绘制（看上面 [Shaders] 的报错堆栈）");
            }
            return;
        }

        CrimsonVowBackplateRig.Anim anim = CrimsonVowBackplateRig.pose(player, player.tickCount);
        float time = player.tickCount + partialTick;

        poseStack.pushPose();
        // 在既有的实体姿态之上叠加"挂到背上"的相对变换；身体朝向留在这里面，
        // 所以玩家怎么转、怎么潜行它都跟着 —— 详见 CrimsonVowBackplateRig 的类注释。
        CrimsonVowBackplateRig.transform(poseStack, player, anim, partialTick);
        Matrix4f pose = poseStack.last().pose();

        VertexConsumer base = buffer.getBuffer(baseType);
        SplendidingShaders.setBackplateUniforms(time, anim, false);
        CrimsonVowBackplateMesh.render(pose, base, packedLight, 0);
        flush(buffer, baseType);

        VertexConsumer glow = buffer.getBuffer(glowType);
        SplendidingShaders.setBackplateUniforms(time, anim, true);
        CrimsonVowBackplateMesh.render(pose, glow, packedLight, 0);
        flush(buffer, glowType);

        poseStack.popPose();
    }

    private static void flush(MultiBufferSource buffer, RenderType type) {
        if (buffer instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(type);
        }
    }
}
