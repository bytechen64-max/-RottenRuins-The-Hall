package org.bytechen.rottenruins.platform.minecraft.infection.worlddata;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 世界数据注册中心。
 * <p>
 * 集中管理所有世界数据类型，提供 ResourceLocation 与构造/加载工厂之间的映射，
 * 以及客户端同步数据的缓存存储。
 * <p>
 * <b>服务端用法：</b>
 * <pre>{@code
 * // 注册（模组构造时）
 * WorldDataRegistry.register(
 *     ResourceLocation.fromNamespaceAndPath("mymod", "my_data"),
 *     MyWorldData.class,
 *     MyWorldData::new,
 *     MyWorldData::load
 * );
 *
 * // 获取（服务端任意位置）
 * MyWorldData data = WorldDataRegistry.get(serverLevel, MyWorldData.class);
 * }</pre>
 *
 * <b>客户端用法：</b>
 * <pre>{@code
 * CompoundTag synced = WorldDataRegistry.getClientData(
 *     level.dimension().location(),
 *     ResourceLocation.fromNamespaceAndPath("mymod", "my_data")
 * );
 * }</pre>
 */
public final class WorldDataRegistry {

    private WorldDataRegistry() {}

    /** 世界数据类型构造工厂：ResourceLocation → Supplier */
    private static final Map<ResourceLocation, Supplier<?>> CONSTRUCTORS = new HashMap<>();

    /** 世界数据类型反序列化工厂：ResourceLocation → Function(CompoundTag, T) */
    private static final Map<ResourceLocation, Function<CompoundTag, ?>> DESERIALIZERS = new HashMap<>();

    /** Class → ResourceLocation 反向查找 */
    private static final Map<Class<?>, ResourceLocation> ID_BY_CLASS = new HashMap<>();

    /** 实例 → ResourceLocation 反向查找（用于 sync 派发） */
    private static final Map<AbstractWorldData, ResourceLocation> ID_BY_INSTANCE = new java.util.WeakHashMap<>();

    /** 客户端缓存：dimensionId → (worldDataId → syncData) */
    private static final Map<ResourceLocation, Map<ResourceLocation, CompoundTag>> CLIENT_DATA = new HashMap<>();

    // ==================== 注册 ====================

    /**
     * 注册一个世界数据类型。
     *
     * @param id            唯一标识符，同时用作 SavedData 存储的文件名
     * @param clazz         数据类型，用于 {@link #get(ServerLevel, Class)} 按类型查找
     * @param constructor   创建新实例的工厂（全新创建，无 NBT 数据时使用）
     * @param deserializer  从 NBT 加载实例的工厂（从磁盘恢复时使用）
     * @param <T>           世界数据类型
     */
    public static <T extends AbstractWorldData> void register(
            ResourceLocation id,
            Class<T> clazz,
            Supplier<T> constructor,
            Function<CompoundTag, T> deserializer) {

        CONSTRUCTORS.put(id, constructor);
        DESERIALIZERS.put(id, deserializer);
        ID_BY_CLASS.put(clazz, id);
    }

    // ==================== 服务端获取 ====================

    /**
     * 按类型获取世界数据实例（服务端）。
     * <p>
     * 首次调用时通过 {@link net.minecraft.world.level.storage.DimensionDataStorage#computeIfAbsent}
     * 自动创建或从磁盘加载实例。
     *
     * @param level 服务端 Level
     * @param clazz 世界数据类型
     * @return 世界数据实例，未注册时返回 null
     */
    @Nullable
    public static <T extends AbstractWorldData> T get(ServerLevel level, Class<T> clazz) {
        ResourceLocation id = ID_BY_CLASS.get(clazz);
        if (id == null) return null;
        return get(level, id);
    }

    /**
     * 按 ResourceLocation 获取世界数据实例（服务端）。
     * <p>
     * 首次调用时通过 {@link net.minecraft.world.level.storage.DimensionDataStorage#computeIfAbsent}
     * 自动创建或从磁盘加载实例。
     *
     * @param level 服务端 Level
     * @param id    世界数据类型标识符
     * @return 世界数据实例，未注册时返回 null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends AbstractWorldData> T get(ServerLevel level, ResourceLocation id) {
        Function<CompoundTag, T> deserializer = (Function<CompoundTag, T>) DESERIALIZERS.get(id);
        Supplier<T> constructor = (Supplier<T>) CONSTRUCTORS.get(id);
        if (deserializer == null || constructor == null) return null;

        // Minecraft 1.20.1: computeIfAbsent(Function<CompoundTag, T>, Supplier<T>, String)
        T data = level.getDataStorage().computeIfAbsent(deserializer, constructor, id.toString());

        // 设置 level 引用供后续 sync 使用
        if (data != null) {
            data.setLevel(level);
            ID_BY_INSTANCE.put(data, id);
        }

        return data;
    }

    // ==================== 客户端获取 ====================

    /**
     * 获取客户端已同步的世界数据（只读 CompoundTag）。
     * <p>
     * 数据由服务端通过 {@link org.bytechen.rottenruins.platform.forge.infection.network.PacketSyncWorldData}
     * 推送后自动缓存。
     *
     * @param dimensionId 维度标识符
     * @param worldDataId 世界数据类型标识符
     * @return 同步数据，未同步时返回 null
     */
    @Nullable
    public static CompoundTag getClientData(ResourceLocation dimensionId, ResourceLocation worldDataId) {
        Map<ResourceLocation, CompoundTag> dimData = CLIENT_DATA.get(dimensionId);
        if (dimData == null) return null;
        return dimData.get(worldDataId);
    }

    /**
     * 客户端：缓存接收到的同步数据。由网络包处理器调用。
     */
    @ApiStatus.Internal
    public static void putClientData(ResourceLocation dimensionId, ResourceLocation worldDataId, CompoundTag data) {
        CLIENT_DATA.computeIfAbsent(dimensionId, k -> new HashMap<>()).put(worldDataId, data);
    }

    // ==================== 内部 API ====================

    /**
     * 根据实例查找其注册 ID。用于 sync 派发。
     */
    @Nullable
    static ResourceLocation getIdByInstance(AbstractWorldData instance) {
        return ID_BY_INSTANCE.get(instance);
    }

    /**
     * 获取所有已注册的 ResourceLocation（用于调试/迭代）。
     */
    public static Set<ResourceLocation> getRegisteredIds() {
        return Collections.unmodifiableSet(CONSTRUCTORS.keySet());
    }
}
