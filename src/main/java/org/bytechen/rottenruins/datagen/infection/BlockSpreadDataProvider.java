package org.bytechen.rottenruins.datagen.infection;

import org.bytechen.rottenruins.core.infection.InfectionIds;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.core.infection.blockspread.BlockSpreadData;
import org.bytechen.rottenruins.core.infection.blockspread.BlockSpreadEntry;
import com.google.gson.Gson;
import org.bytechen.rottenruins.core.infection.blockspread.BlockSpreadTarget;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 方块扩散数据生成器。
 * <p>
 * 为方块扩散系统生成统一的 JSON 数据文件。
 * 副模组通过继承此类并覆盖 {@link #buildEntries()} 来添加自定义扩散规则。
 * <p>
 * 使用 {@link #type(String)} 可以自动将本地类型名补全为 {@code "modId:typeName"} 格式，
 * 确保不同模组的扩散规则互不覆盖。
 * <p>
 * 输出路径：{@code data/<modid>/infcore_blockspread/<filename>.json}
 *
 * <h3>快速使用</h3>
 * <pre>{@code
 * @SubscribeEvent
 * public static void gatherData(GatherDataEvent event) {
 *     event.getGenerator().addProvider(
 *         event.includeServer(),
 *         new BlockSpreadDataProvider(event.getGenerator().getPackOutput(), "mymod", "my_rules") {
 *             @Override
 *             public List<BlockSpreadEntry> buildEntries() {
 *                 return List.of(
 *                     entry("spread", "minecraft:grass_block", false, List.of(
 *                         target(MyBlocks.HALL_GRASS.get(), 100)
 *                     ))
 *                 );
 *             }
 *         }
 *     );
 * }
 * }</pre>
 * <p>
 * 生成的 JSON 格式：
 * <pre>
 * {
 *   "replace": false,
 *   "entries": [
 *     {
 *       "type": "mymod:spread",
 *       "source": "minecraft:grass_block",
 *       "dropResources": false,
 *       "results": [
 *         {"target": "mymod:hall_grass", "weight": 100}
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
public class BlockSpreadDataProvider implements DataProvider {
    private static final Gson GSON = new Gson();

    private final PackOutput packOutput;
    private final String modId;
    private final String fileName;

    /**
     * @param packOutput 数据生成输出目录
     * @param modId      此数据提供者所属的模组 ID（决定输出到哪个命名空间，
     *                   也是 {@link #type(String)} 的前缀）
     * @param fileName   输出 JSON 文件名（不含扩展名），如 {@code "default_spread"}
     */
    public BlockSpreadDataProvider(PackOutput packOutput, String modId, String fileName) {
        this.packOutput = packOutput;
        this.modId = modId;
        this.fileName = fileName;
    }

    /**
     * 默认构造，使用本模组的 modId。
     */
    public BlockSpreadDataProvider(PackOutput packOutput) {
        this(packOutput, InfectionIds.NAMESPACE, "default_blockspread");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        BlockSpreadData data = new BlockSpreadData(false, buildEntries());
        String rawJson = GSON.toJson(data);
        JsonElement jsonElement = JsonParser.parseString(rawJson);

        Path path = packOutput.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(modId + "/infcore_blockspread/" + fileName + ".json");

        return DataProvider.saveStable(cachedOutput, jsonElement, path);
    }

    @Override
    public String getName() {
        return "InfCore BlockSpread Data: " + modId + "/" + fileName;
    }

    /**
     * 构建方块扩散条目列表。
     * 子类覆盖此方法以添加自定义规则。
     * 在覆盖的方法体内直接调用实例方法 {@link #entry} 和 {@link #type}。
     */
    public List<BlockSpreadEntry> buildEntries() {
        return new ArrayList<>();
    }

    // ==================== 实例便利方法 ====================

    /**
     * 将本地类型名自动补全为带命名空间的完整标识符。
     * {@code type("spread")} → {@code "mymod:spread"}
     */
    public String type(String name) {
        return modId + ":" + name;
    }

    /**
     * 返回此 provider 的 modId。
     */
    public String id() {
        return modId;
    }

    /**
     * 创建一条方块扩散条目，type 自动用 modId 作为命名空间前缀。
     *
     * @param localType     本地类型名，如 {@code "spread"}，自动补全为 {@code "mymod:spread"}
     * @param source        源方块注册名，如 {@code "minecraft:grass_block"}
     * @param dropResources 是否掉落资源
     * @param results       目标及权重列表
     */
    public BlockSpreadEntry entry(String localType, String source, boolean dropResources,
                                   List<BlockSpreadTarget> results) {
        return new BlockSpreadEntry(type(localType), source, dropResources, results);
    }

    /**
     * 创建一条方块扩散条目（使用 Block 自动获取注册名），type 自动用 modId 作为命名空间前缀。
     */
    public BlockSpreadEntry entry(String localType, Block source, boolean dropResources,
                                   List<BlockSpreadTarget> results) {
        return entry(localType, regName(source), dropResources, results);
    }

    // ==================== 静态辅助方法（需要手动指定完整 type） ====================

    /**
     * 创建一条方块扩散条目（静态方法，需要完整的 type 字符串如 {@code "mymod:spread"}）。
     */
    public static BlockSpreadEntry entryFull(String type, String source, boolean dropResources,
                                              List<BlockSpreadTarget> results) {
        return new BlockSpreadEntry(type, source, dropResources, results);
    }

    /**
     * 创建一条方块扩散条目（静态方法，使用 Block 自动获取注册名）。
     */
    public static BlockSpreadEntry entryFull(String type, Block source, boolean dropResources,
                                              List<BlockSpreadTarget> results) {
        return entryFull(type, regName(source), dropResources, results);
    }

    // ==================== Target 辅助方法 ====================

    /**
     * 创建一个方块扩散目标。
     *
     * @param target 目标方块注册名，如 {@code "mymod:hall_grass"}
     * @param weight 权重（值越大被选中的概率越高）
     */
    public static BlockSpreadTarget target(String target, int weight) {
        return new BlockSpreadTarget(target, weight);
    }

    /**
     * 创建一个方块扩散目标（使用 Block 自动获取注册名）。
     */
    public static BlockSpreadTarget target(Block target, int weight) {
        return new BlockSpreadTarget(regName(target), weight);
    }

    /**
     * 获取方块的注册名。
     */
    public static String regName(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block).toString();
    }
}
