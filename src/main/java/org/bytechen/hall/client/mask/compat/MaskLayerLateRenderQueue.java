package org.bytechen.hall.client.mask.compat;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.mask.MaskLayerRenderer;
import org.bytechen.hall.client.mask.MaskLayerResolver;
import org.bytechen.hall.client.rend.glint.LateOutlineRenderState;
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
 * mask 效果层的延迟回放队列 —— 光影包（Oculus / Iris）兼容的落点。
 *
 * <p>结构与 {@code CosmicItemLateRenderQueue} 相同，只是载荷换成了
 * 「一个物品的若干 mask 层」。为什么必须延迟：光影包下物品渲染写进的是 GBuffer
 * 而不是主帧缓冲，我们的自定义着色器在那里既拿不到正确的混合结果，也会被当成
 * 场景几何参与后续的延迟光照 —— 所以此刻只把「画什么、在什么矩阵下画」记下来，
 * 等 {@code renderLevel} 结束、光影包已经把最终画面合成到主帧缓冲之后，再原样
 * 回放一次，直接写主帧缓冲。</p>
 *
 * <h3>两个相位</h3>
 * <ol>
 *   <li>{@link #renderNonFirstPerson()} —— 在 {@code renderHand} 之前回放世界空间的
 *       层：此时主场景深度已经写好，而第一人称手臂还没画，遮挡关系是对的。</li>
 *   <li>{@link #renderAll()} —— 在 {@code renderLevel} 末尾回放剩下的（含一手）。
 *       一手不测深度，见 {@code MaskLayerRenderType.handAfterLevel}。</li>
 * </ol>
 *
 * <h3>与 cosmic 队列的一处差别</h3>
 * <p>cosmic 那边的 RenderType 是四个固定常量，所以回放时可以写死
 * {@code endBatch(COSMIC_AFTER_LEVEL)} 之类；本系统每一层用的是哪个 RenderType
 * 取决于它挂的是什么效果（而且效果可以不断新增），写不出来。所以这里不负责
 * flush —— {@link MaskLayerRenderer#render} 在回放时也会每层自己 flush，
 * 那本来就是 uniform 正确性要求的做法。</p>
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
     * 记下一次延迟回放。必须在渲染线程、且在正确的 modelView/projection
     * 矩阵还生效时调用（也就是物品渲染的当帧）。
     *
     * @param baseSprite 本体贴图 sprite（各层的光源兼几何来源）。它在入队时就取好存下来，
     *                   而不是回放时再问模型 —— 回放发生在完全不同的时刻，那时手上已经没有
     *                   这个物品的 BakedModel 了。sprite 本身是图集里的稳定对象，
     *                   存引用是安全的。
     */
    public static void enqueue(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                               int packedLight, int packedOverlay,
                               List<MaskLayerResolver.Resolved> layers,
                               @Nullable TextureAtlasSprite baseSprite) {
        if (layers.isEmpty()) return;
        PoseStack.Pose pose = poseStack.last();

        // ── 同一帧内的重复条目只保留最后一条 ──
        //
        // 光影包会把同一个物品渲染不止一遍：一遍进 GBuffer（延迟管线），再单独渲染一遍
        // 手部。两遍的 view/projection **不同**，而我们是在帧末按入队时的矩阵回放的 ——
        // 两条都留着，多出来的那条就会被按另一个投影盖回画面。第一人称的变体是
        // NO_DEPTH_TEST，所以它直接压在最上层（症状：像背面的发光穿到前面来）；
        // 第三人称是 LEQUAL + 深度偏移，那条会被真实深度剔掉，所以看起来一直正常。
        //
        // 判定键 = 帧 + 物品 + 上下文 + **物品自己的 pose**：
        //   · pose 两遍相同（都是同一个 display 变换）→ 能合并；
        //   · 但"地上两把一模一样、位置不同的剑"pose 不同 → 不会被误合并。
        // 保留最后一条是因为 Iris 的帧序里手部那遍在后，它的投影才与最终画面一致。
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

                // 还原入队时的矩阵：回放发生在完全不同的时刻，此刻的
                // modelView/projection 跟当初画这个物品时毫无关系。
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

    // ── 重复入队的报告 ─────────────────────────────────────────────
    //
    // 去重动作本身在 enqueue 里做（见那里的注释）。这里只负责说一次，
    // 让「光影把同一物品渲染了几遍」这件事在日志里有据可查 ——
    // 这一轮就是靠它把范围从"阴影 pass"缩到"独立手部 pass"的。
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

    /** 只用于诊断日志。 */
    public static int pendingCount() {
        return ENTRIES.size();
    }

    private MaskLayerLateRenderQueue() {}
}
