package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictFieldEntity;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDebug;

/**
 * 裁决领域的绘制入口。
 *
 * <h3>为什么必须晚于实体</h3>
 * <p>领域的地面光纹是<b>贴地</b>的东西，而它的深度对手是"当前深度缓冲里已经有的内容"。
 * 如果放在 {@code EntityRenderer.render} 里画，它会在<b>方块与实体之前</b>落到缓冲区里，
 * 于是前方的方块和生物完全挡不住它 —— 表现就是"领域穿透实体和方块"。</p>
 *
 * <p>挪到 {@code AFTER_TRIPWIRE_BLOCKS} 之后就正常了：那时地形与实体都已经写好深度，
 * 光纹开 {@code LEQUAL} 深度测试、关深度写入，于是</p>
 * <ul>
 *   <li>被前方地形挡住的片元 → 深度测试失败，不画；</li>
 *   <li>被前方生物挡住的片元 → 同上；</li>
 *   <li>它自己不写深度 → 不会把之后的东西（比如玩家的手）剪掉。</li>
 * </ul>
 *
 * <p>这个阶段与 {@code ShockwaveRenderHandler} 是同一个，坐标系约定也相同。</p>
 *
 * <h3>坐标系：相机平移由本类施加</h3>
 * <p>顶点着色器算的是 {@code ProjMat * Position}，不含相机变换，
 * 所以顶点必须是<b>相机相对坐标</b>。这里 push 一层并把 {@code -camera} 平移进去，
 * {@link VerdictFieldRenderer#renderOne} 就只需要发局部坐标 ——
 * 与冲击波/黑洞渲染器同一套约定。</p>
 *
 * <p><b>警告：这个平移与渲染器是配对的，只能有一份。</b>
 * 我曾为了"让渲染器自包含"而在 {@code renderOne} 里又加了一次相机平移，
 * 忘了删掉这里的 —— 两者叠加成双重平移，几何被推到两倍距离之外，
 * 症状是"领域彻底看不见了"。改这一对代码时必须两边一起看。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VerdictFieldRenderHandler {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float pt = event.getPartialTick();

        // 唯一的相机平移在这里。（渲染器内部不得再平移一次，见类注释的警告。）
        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);

        int found = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof VerdictFieldEntity field) {
                found++;
                VerdictFieldRenderer.renderOne(field, pt, ps);
            }
        }

        ps.popPose();

        // 诊断：只在真的存在领域时记一行，用来确认"渲染入口到底有没有被调到"。
        // 每条 RenderLevelStageEvent 都会跑，所以这里必须做频率限制，否则日志会被刷爆。
        if (found > 0) {
            long now = System.currentTimeMillis();
            if (now - lastReportMs > 1000L) {
                lastReportMs = now;
                VerdictDebug.log("RenderHandler 发现 %d 个领域实体，当前相机=(%.1f,%.1f,%.1f)",
                        found, camera.x, camera.y, camera.z);
            }
        }
    }

    /** 上次写诊断日志的时间，用于限频。 */
    private static long lastReportMs;
}
