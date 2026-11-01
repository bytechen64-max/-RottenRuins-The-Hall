package org.bytechen.rottenruins.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.manager.CorruptionSpreadHelper;
import org.bytechen.rottenruins.overworld.manager.CorruptionZoneData;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 腐化群系自然生成：在腐化区域（{@link CorruptionZoneData}）内周期性生成腐化生物。
 *
 * <p>召唤柱也可以自然生成，但权重明显更低（"召唤柱生成相对较少"）。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptionZoneSpawnHandler {

    /** 每 100 tick 尝试一轮 */
    private static final int SPAWN_INTERVAL = 100;
    /** 区域附近玩家的感应距离 */
    private static final double PLAYER_RANGE = 96.0;
    /** 区域内同族数量上限 */
    private static final int MOB_CAP = 14;
    /** 每轮每个区域最多尝试次数 */
    private static final int ATTEMPTS_PER_ZONE = 2;
    /** 统计同族数量的半径 */
    private static final double COUNT_RADIUS = 32.0;

    /** 生成权重表：腐化生物全部可生成，召唤柱权重低 */
    private static final List<SpawnEntry> SPAWN_TABLE = new ArrayList<>();

    static {
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_ZOMBIE::get, 12));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_COW::get, 10));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_PIG::get, 10));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_SHEEP::get, 10));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_CHICKEN::get, 10));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_SKELETON::get, 8));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_WOLF::get, 6));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_BAT::get, 6));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_VILLAGER::get, 4));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.CORRUPTION_BONE_RUINS::get, 2));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.DEGENERATE_ZOMBIE::get, 4));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.DEGENERATE_COW::get, 4));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.DEGENERATE_PIG::get, 4));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.DEGENERATE_VILLAGER::get, 4));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.GROVELER::get, 3));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.ATTACKER::get, 2));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.BEARER::get, 1));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.LONG_ARM_BEAST::get, 1));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.SHOCKER::get, 1));
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.HUMUS_COLUMN::get, 2));
        // 召唤柱：权重最低（相对较少）
        SPAWN_TABLE.add(new SpawnEntry(CorruptionEntityTypes.SUMMONING_PILLAR::get, 1));
    }

    private CorruptionZoneSpawnHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if ((event.getServer().getTickCount() % SPAWN_INTERVAL) != 0) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            trySpawnForLevel(level);
        }
    }

    private static void trySpawnForLevel(ServerLevel level) {
        CorruptionZoneData data = CorruptionZoneData.get(level);
        List<CorruptionZoneData.Zone> zones = data.getZones();
        if (zones.isEmpty()) return;

        for (CorruptionZoneData.Zone zone : zones) {
            BlockPos center = zone.center;
            if (!level.isLoaded(center)) continue;

            Player player = level.getNearestPlayer(center.getX(), center.getY(), center.getZ(),
                    PLAYER_RANGE, false);
            if (player == null) continue;

            long nearby = level.getEntitiesOfClass(Mob.class,
                            new net.minecraft.world.phys.AABB(center).inflate(COUNT_RADIUS))
                    .stream()
                    .filter(mob -> mob instanceof IInfectedEntity infected
                            && RottenFaction.isCorruption(infected.getInfectionType()))
                    .count();
            if (nearby >= MOB_CAP) continue;

            for (int i = 0; i < ATTEMPTS_PER_ZONE; i++) {
                trySpawnOne(level, zone);
            }
        }
    }

    private static void trySpawnOne(ServerLevel level, CorruptionZoneData.Zone zone) {
        RandomSource random = level.random;
        int radius = Math.max(3, zone.radius);
        int dx = random.nextInt(radius * 2 + 1) - radius;
        int dz = random.nextInt(radius * 2 + 1) - radius;
        BlockPos column = zone.center.offset(dx, 0, dz);

        for (int dy = 8; dy >= -12; dy--) {
            BlockPos pos = column.offset(0, dy, 0);
            if (!level.isLoaded(pos)) return;

            BlockState ground = level.getBlockState(pos);
            if (!CorruptionSpreadHelper.isCorruptionGround(ground)) continue;
            BlockPos spawnPos = pos.above();
            if (!level.getBlockState(spawnPos).isAir()) return;
            if (!level.getBlockState(spawnPos.above()).isAir()) return;

            EntityType<?> type = pickType(random);
            if (type == null) return;

            Entity entity = type.create(level);
            if (!(entity instanceof Mob mob)) return;

            mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.NATURAL, null, null);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            return;
        }
    }

    private static EntityType<?> pickType(RandomSource random) {
        int total = SPAWN_TABLE.stream().mapToInt(SpawnEntry::weight).sum();
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (SpawnEntry entry : SPAWN_TABLE) {
            cumulative += entry.weight();
            if (roll < cumulative) return entry.type().get();
        }
        return SPAWN_TABLE.get(0).type().get();
    }

    private record SpawnEntry(Supplier<EntityType<?>> type, int weight) {}
}
