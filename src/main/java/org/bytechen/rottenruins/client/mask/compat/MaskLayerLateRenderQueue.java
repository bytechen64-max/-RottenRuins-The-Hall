package org.bytechen.rottenruins.client.mask.compat;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.mask.MaskLayerRenderer;
import org.bytechen.rottenruins.client.mask.MaskLayerResolver;
import org.bytechen.rottenruins.client.rend.glint.LateOutlineRenderState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * mask 效果层的延迟回放队列（光影包 Oculus/Iris 兼容落点）。光影下物品写进 GBuffer 而非主帧缓冲，
 * 自定义着色器在那里混合结果不对、还会被当成场景几何参与延迟光照，所以只记下「画什么、什么矩阵」，
 * 等光影合成完再原样回放到主帧缓冲。
 * 相位一 {@link #renderNonFirstPerson()} 在 renderHand 前回放世界空间层（主场景深度已写、手臂未画）；
 * 相位二 {@link #renderAll()} 在 renderLevel 末尾回放剩余条目（含一手，一手不测深度）。
 * 与 cosmic 队列不同，这里不统一 flush —— 每层的 RenderType 不固定，由 {@link MaskLayerRenderer#render} 逐层 flush。
 */
public final class MaskLayerLateRenderQueue {

    private record Entry(String key,
                         ItemStack stack,
                         ItemDisplayContext context,
                         Matrix4f pose,
                         Matrix3f normal,
                         Matrix4f modelView,
                         Matrix4f projection,
                         int packedLight,
                         int packedOverlay,
                         List<MaskLayerResolver.Resolved> layers,
                         @Nullable TextureAtlasSprite baseSprite) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /**
     * 记下一次延迟回放。必须在渲染线程、且正确的 modelView/projection 仍生效时调用（物品渲染当帧）。
     * baseSprite 在入队时就取好存下：回放时早已没有该物品的 BakedModel；sprite 是图集里的稳定对象，存引用安全。
     */
    public static void enqueue(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                               int packedLight, int packedOverlay,
                               List<MaskLayerResolver.Resolved> layers,
                               @Nullable TextureAtlasSprite baseSprite) {
        if (layers.isEmpty()) return;
        PoseStack.Pose pose = poseStack.last();

        // 光影包会把同一物品渲染不止一遍（GBuffer + 独立手部 pass），两遍 view/projection 不同，
        // 都回放会按错的投影盖回画面（一手变体是 NO_DEPTH_TEST，会直接压在最上层）。
        // 同一帧内只保留最后一条（Iris 帧序里手部那遍在后）；键含物品自身 pose，
        // 地上两把同款但位置不同的剑不会被误合并。
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String key = RenderSystem.getShaderGameTime() + "|" + item + "|"
                + context + "|" + pose.pose().hashCode();
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            if (ENTRIES.get(i).key().equals(key)) {
                ENTRIES.remove(i);
                reportDuplicateOnce(item, context);
                break;
            }
        }

        ENTRIES.add(new Entry(
                key,
                stack.copy(),
                context,
                new Matrix4f(pose.pose()),
                new Matrix3f(pose.normal()),
                new Matrix4f(RenderSystem.getModelViewMatrix()),
                new Matrix4f(RenderSystem.getProjectionMatrix()),
                packedLight,
                packedOverlay,
                List.copyOf(layers),
                baseSprite
        ));
    }

    /** 相位一：在 renderHand 之前回放非一手条目。 */
    public static void renderNonFirstPerson() {
        renderEntries(false, false);
    }

    /** 相位二：在 renderLevel 末尾回放全部剩余条目。 */
    public static void renderAll() {
        renderEntries(true, true);
    }

    private static void renderEntries(boolean includeFirstPersonHand, boolean clearSkipped) {
        if (ENTRIES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();

        try {
            LateOutlineRenderState.prepareMainTargetPass();

            Iterator<Entry> iterator = ENTRIES.iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (isFirstPerson(entry.context()) && !includeFirstPersonHand) {
                    if (clearSkipped) iterator.remove();
                    continue;
                }

                // 还原入队时的矩阵：此刻的 modelView/projection 与当初画该物品时毫无关系。
                modelViewStack.last().pose().set(entry.modelView());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f(entry.projection()),
                        VertexSorting.DISTANCE_TO_ORIGIN);

                PoseStack poseStack = new PoseStack();
                poseStack.last().pose().set(entry.pose());
                poseStack.last().normal().set(entry.normal());

                MaskLayerRenderer.render(entry.stack(), entry.context(), poseStack, buffers,
                        entry.packedLight(), entry.packedOverlay(), entry.layers(), true,
                        entry.baseSprite());

                iterator.remove();
            }
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            LateOutlineRenderState.finishMainTargetPass();

            if (clearSkipped) ENTRIES.clear();
        }
    }

    private static boolean isFirstPerson(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    // 去重动作在 enqueue 里做；这里只把「光影把同一物品渲染了几遍」记一次日志，便于排查。
    private static final Set<String> DUPLICATE_REPORTED = new HashSet<>();

    private static void reportDuplicateOnce(ResourceLocation item, ItemDisplayContext ctx) {
        if (!DUPLICATE_REPORTED.add(item + "|" + ctx)) return;
        HallMod.LOGGER.info("[MaskLayer] 同一帧内 {} 的 {} 被入队两次（光影的重复渲染），"
                        + "已只保留最后一条 —— 两条的 view/projection 不同，都回放会与最终画面错位",
                item, ctx);
    }

    /** 丢掉所有待回放条目（换维度/退出世界时兜底）。 */
    public static void clear() {
        ENTRIES.clear();
    }

    public static int pendingCount() {
        return ENTRIES.size();
    }

    private MaskLayerLateRenderQueue() {}
}
