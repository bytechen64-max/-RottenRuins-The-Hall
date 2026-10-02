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
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 使徒斩击外层空间扭曲的渲染入口。
 *
 * <h3>为什么不在 {@code SwordAuraRenderer.render()} 里画</h3>
 * 扭曲需要采样"完整的场景"（包括所有实体、粒子）。实体渲染阶段里，
 * 排在自己后面的实体还没画，拷贝出来的画面会缺一块。所以和冲击波一样，
 * 挪到 {@link RenderLevelStageEvent.Stage#AFTER_TRIPWIRE_BLOCKS} —— 那时实体已全部就位。
 *
 * <p>只挑 {@link SwordAuraEntity#STYLE_APOSTLE} 的剑气：默认样式的剑气
 * （虚空剑/碎骨/畸骸玩家）不加扭曲，行为与改动前完全一致。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ApostleSlashRenderHandler {

    private ApostleSlashRenderHandler() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<SwordAuraEntity> slashes = collectApostleSlashes(mc);
        if (slashes.isEmpty()) return;

        PoseStack ps = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();

        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);
        ApostleSlashWarp.renderAll(slashes, event.getPartialTick(), ps);
        ps.popPose();
    }

    /** 收集本帧需要画扭曲的斩击。数量很少（单次技能最多约 3 道同时在世），直接用列表。 */
    private static List<SwordAuraEntity> collectApostleSlashes(Minecraft mc) {
        List<SwordAuraEntity> result = new ArrayList<>(4);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof SwordAuraEntity aura
                    && aura.getStyle() == SwordAuraEntity.STYLE_APOSTLE) {
                result.add(aura);
            }
        }
        return result;
    }
}
