package org.bytechen.rottenruins.datagen.infection;

import org.bytechen.rottenruins.core.infection.InfectionIds;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.core.infection.evolution.EvolutionData;
import org.bytechen.rottenruins.core.infection.evolution.EvolutionEntry;
import com.google.gson.Gson;
import org.bytechen.rottenruins.core.infection.evolution.EvolutionTarget;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 进化/感染数据生成器。
 * <p>
 * 为实体进化和感染系统生成统一的 JSON 数据文件。
 * 副模组通过继承此类并覆盖 {@link #buildEntries()} 来添加自定义进化规则。
 * <p>
 * 使用 {@link #type(String)} 可以自动将本地类型名补全为 {@code "modId:typeName"} 格式，
 * 确保不同模组的进化规则互不覆盖。
 * <p>
 * 输出路径：{@code data/<modid>/infcore_evolution/<filename>.json}
 *
 * <h3>快速使用</h3>
 * <pre>{@code
 * @SubscribeEvent
 * public static void gatherData(GatherDataEvent event) {
 *     event.getGenerator().addProvider(
 *         event.includeServer(),
 *         new EvolutionDataProvider(event.getGenerator().getPackOutput(), "mymod", "my_rules") {
 *             @Override
 *             public List<EvolutionEntry> buildEntries() {
 *                 return List.of(
 *                     entry("infection", EntityType.COW, false, true, List.of(
 *                         target(MyEntities.INF_COW.get(), 100)
 *                     )),
 *                     entry("evolution", MyEntities.INF_COW.get(), false, false, List.of(
 *                         target(MyEntities.EVO_COW.get(), 100)
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
 *       "type": "mymod:infection",
 *       "source": "minecraft:cow",
 *       "keepEquipment": false,
 *       "keepNbt": true,
 *       "results": [
 *         {"target": "mymod:inf_cow", "weight": 100}
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
public class EvolutionDataProvider implements DataProvider {
    private static final Gson GSON = new Gson();

    private final PackOutput packOutput;
    private final String modId;
    private final String fileName;

    /**
     * @param packOutput 数据生成输出目录
     * @param modId      此数据提供者所属的模组 ID（决定输出到哪个命名空间，
     *                   也是 {@link #type(String)} 的前缀）
     * @param fileName   输出 JSON 文件名（不含扩展名），如 {@code "default_infection"}
     */
    public EvolutionDataProvider(PackOutput packOutput, String modId, String fileName) {
        this.packOutput = packOutput;
        this.modId = modId;
        this.fileName = fileName;
    }

    /**
     * 默认构造，使用本模组的 modId。
     */
    public EvolutionDataProvider(PackOutput packOutput) {
        this(packOutput, InfectionIds.NAMESPACE, "default_evolution");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        EvolutionData data = new EvolutionData(false, buildEntries());
        String rawJson = GSON.toJson(data);
        JsonElement jsonElement = JsonParser.parseString(rawJson);

        Path path = packOutput.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(modId + "/infcore_evolution/" + fileName + ".json");

        return DataProvider.saveStable(cachedOutput, jsonElement, path);
    }

    @Override
    public String getName() {
        return "InfCore Evolution Data: " + modId + "/" + fileName;
    }

    /**
     * 构建进化条目列表。
     * 子类覆盖此方法以添加自定义规则。
     * 在覆盖的方法体内直接调用实例方法 {@link #entry} 和 {@link #type}。
     */
    public List<EvolutionEntry> buildEntries() {
        return new ArrayList<>();
    }

    // ==================== 实例便利方法 ====================

    /**
     * 将本地类型名自动补全为带命名空间的完整标识符。
     * {@code type("infection")} → {@code "mymod:infection"}
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
     * 创建一条进化条目，type 自动用 modId 作为命名空间前缀。
     *
     * @param localType     本地类型名，如 {@code "infection"}，自动补全为 {@code "mymod:infection"}
     * @param source        源实体注册名，如 {@code "minecraft:cow"}
     * @param keepEquipment 是否保留装备
     * @param keepNbt       是否保留 NBT
     * @param results       目标及权重列表
     */
    public EvolutionEntry entry(String localType, String source, boolean keepEquipment, boolean keepNbt,
                                List<EvolutionTarget> results) {
        return new EvolutionEntry(type(localType), source, keepEquipment, keepNbt, results);
    }

    /**
     * 创建一条进化条目（使用 EntityType 自动获取注册名），type 自动用 modId 作为命名空间前缀。
     */
    public EvolutionEntry entry(String localType, EntityType<?> source, boolean keepEquipment, boolean keepNbt,
                                List<EvolutionTarget> results) {
        return entry(localType, regName(source), keepEquipment, keepNbt, results);
    }

    // ==================== 静态辅助方法（需要手动指定完整 type） ====================

    /**
     * 创建一条进化条目（静态方法，需要完整的 type 字符串如 {@code "mymod:infection"}）。
     */
    public static EvolutionEntry entryFull(String type, String source, boolean keepEquipment, boolean keepNbt,
                                           List<EvolutionTarget> results) {
        return new EvolutionEntry(type, source, keepEquipment, keepNbt, results);
    }

    /**
     * 创建一条进化条目（静态方法，使用 EntityType 自动获取注册名）。
     */
    public static EvolutionEntry entryFull(String type, EntityType<?> source, boolean keepEquipment, boolean keepNbt,
                                           List<EvolutionTarget> results) {
        return entryFull(type, regName(source), keepEquipment, keepNbt, results);
    }

    // ==================== Target 辅助方法 ====================

    /**
     * 创建一个进化目标。
     *
     * @param target 目标实体注册名，如 {@code "mymod:inf_cow"}
     * @param weight 权重（值越大被选中的概率越高）
     */
    public static EvolutionTarget target(String target, int weight) {
        return new EvolutionTarget(target, weight);
    }

    /**
     * 创建一个进化目标（使用 EntityType 自动获取注册名）。
     */
    public static EvolutionTarget target(EntityType<?> target, int weight) {
        return new EvolutionTarget(regName(target), weight);
    }

    /**
     * 获取实体类型的注册名。
     */
    public static String regName(EntityType<?> type) {
        return ForgeRegistries.ENTITY_TYPES.getKey(type).toString();
    }
}
