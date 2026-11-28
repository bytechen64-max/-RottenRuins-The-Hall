package org.bytechen.rottenruins.overworld.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.infection.blockspread.BlockSpreadManager;
import org.bytechen.rottenruins.utils.ModUtils;

import java.util.Set;

/**
 * 王庭方块扩散辅助：与 {@link CorruptionSpreadHelper} 对称，走 {@code rotten_ruins:spread} 规则。
 *
 * <p>扩散本身由 {@code BlockSpreadManager} 落方块，群系变更由
 * {@code HallSpreadEventHandler} 的 {@code BlockSpreadEvent.Post} 入队处理，
 * 所以这里不需要单独写群系代码。</p>
 */
public final class HallSpreadHelper {

    /** 王庭扩散类型（与 {@code BlockSpreadDataProvider} 对应）。 */
    public static final ResourceLocation SPREAD_TYPE = ModUtils.modLoc("spread");

    /** 全部王庭类群系：四个维度陆生群系 + 王庭海洋 + 入侵用的废土。 */
    private static final Set<ResourceKey<Biome>> HALL_BIOMES = Set.of(
            key("hall_wasteland"), key("flesh_marrow"), key("shattered_lands"),
            key("folded_fields"), key("pierced_continent"), key("hall_ocean"));

    private HallSpreadHelper() {}

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HallMod.MODID, name));
    }

    /** 该位置是否已经是王庭类群系。 */
    public static boolean isHallBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().filter(HALL_BIOMES::contains).isPresent();
    }

    /**
     * 在中心附近随机扩散若干方块（只落到有 spread 规则的方块上）。
     *
     * @param radius   水平半径
     * @param attempts 尝试次数
     * @return 实际转换成功的方块数
     */
    public static int spreadArea(ServerLevel level, BlockPos center, int radius, int attempts) {
        int converted = 0;
        for (int i = 0; i < attempts; i++) {
            BlockPos pos = center.offset(
                    level.random.nextInt(radius * 2 + 1) - radius,
                    level.random.nextInt(5) - 2,
                    level.random.nextInt(radius * 2 + 1) - radius);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!BlockSpreadManager.hasSpreadRule(state, SPREAD_TYPE)) continue;
            if (BlockSpreadManager.applySpread(level, pos, state, SPREAD_TYPE)) converted++;
        }
        return converted;
    }
}
