package org.bytechen.rottenruins.event;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.infcore.api.event.BlockSpreadEvent;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.manager.CorruptionSpreadHelper;
import org.bytechen.rottenruins.overworld.swarm.HallBiomeProcessor;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 腐化方块扩散 → 腐化群系扩散。
 *
 * <p>与王庭完全相同的管线：方块扩散事件（{@code BlockSpreadEvent.Post}）触发时，
 * 把该方块位置入队给 {@link HallBiomeProcessor}，由 infcore 的
 * {@code BiomeSpreadHelper} 把整个方块柱的群系改成腐化群系。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptionBiomeSpreadHandler {

    /** 与 {@code CorruptionBlockSpreadDataProvider} 对应的扩散类型 */
    private static final ResourceLocation CORRUPTION_SPREAD_TYPE = CorruptionSpreadHelper.SPREAD_TYPE;

    /** 腐土被感染时顶部长出腐化草的概率（只在转换瞬间判定一次） */
    private static final float GRASS_ON_CONVERT_CHANCE = 0.10F;

    private CorruptionBiomeSpreadHandler() {}

    @SubscribeEvent
    public static void onBlockSpread(BlockSpreadEvent.Post event) {
        if (!CORRUPTION_SPREAD_TYPE.equals(event.getSpreadType())) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        Holder<Biome> corruptionBiome = resolveBiome(serverLevel);
        if (corruptionBiome == null) return;

        HallBiomeProcessor.enqueueForward(serverLevel, event.getPos(), corruptionBiome, event.getOldState());

        // 只在"刚刚被感染成腐土"的那一刻判定一次长草，避免每个随机刻反复长草
        // （密度由 GRASS_ON_CONVERT_CHANCE 控制）
        if (event.getNewState().is(CorruptionBlocks.SPREAD_DIRT.get())
                && serverLevel.getBlockState(event.getPos().above()).isAir()
                && serverLevel.random.nextFloat() < GRASS_ON_CONVERT_CHANCE) {
            serverLevel.setBlockAndUpdate(event.getPos().above(),
                    CorruptionBlocks.CORRUPTION_GRASS.get().defaultBlockState());
        }
    }

    /** 腐化群系 key（与 {@code CorruptionBiomeData} 一致）。 */
    private static final ResourceKey<Biome> CORRUPTION_WASTELAND = ResourceKey.create(
            Registries.BIOME, new ResourceLocation(HallMod.MODID, "corruption_wasteland"));

    /** 解析腐化群系（{@code rotten_ruins:corruption_wasteland}）。 */
    private static Holder<Biome> resolveBiome(ServerLevel level) {
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var optional = registry.getHolder(CORRUPTION_WASTELAND);
        if (optional.isPresent()) return optional.get();
        HallMod.LOGGER.warn("[CorruptionBiome] 腐化群系未注册，跳过群系扩散");
        return null;
    }
}
