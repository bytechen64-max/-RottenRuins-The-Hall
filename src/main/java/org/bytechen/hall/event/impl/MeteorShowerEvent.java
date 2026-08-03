package org.bytechen.hall.event.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.config.data.MeteorShowerConfig;
import org.bytechen.hall.overworld.registry.entities.population.skills.MeteoriteEntity;

/**
 * 陨石雨事件 —— 每天晚上在玩家周围随机生成陨石从天而降。
 * <p>
 * 所有参数均通过配置文件 meteor_shower.json5 调整。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorShowerEvent {

    private MeteorShowerEvent() {}

    /** 夜晚开始时间（tick）—— 也可以放入配置，但暂保持硬编码 */
    private static final long NIGHT_START = 13000;
    private static final long NIGHT_END = 23000;

    /** 下次生成陨石的倒计时（tick） */
    private static int nextMeteorTicks = 0;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Level level = event.level;
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 获取配置实例（每次从 ConfigHelper 读取最新值）
        MeteorShowerConfig cfg = ConfigHelper.meteorConfigHolder != null
                ? ConfigHelper.meteorConfigHolder.get()
                : null;

        // 如果配置未加载或已禁用，则重置计时器并退出
        if (cfg == null || !cfg.enable) {
            nextMeteorTicks = 0;
            return;
        }

        // 检查是否为夜晚
        long dayTime = level.getDayTime() % 24000;
        if (dayTime < NIGHT_START || dayTime > NIGHT_END) {
            nextMeteorTicks = 0;
            return;
        }

        // 倒计时
        if (nextMeteorTicks > 0) {
            nextMeteorTicks--;
            return;
        }

        // 尝试生成陨石（传入配置参数）
        if (trySpawnMeteor(serverLevel, cfg)) {
            // 成功后重置倒计时（使用配置的间隔和抖动）
            nextMeteorTicks = cfg.intervalBase
                    + level.random.nextIntBetweenInclusive(-cfg.intervalJitter, cfg.intervalJitter);
        } else {
            // 失败则稍后重试（使用配置的重试延迟）
            nextMeteorTicks = cfg.retryDelay;
        }
    }

    /**
     * 尝试在服务器世界生成一颗陨石。
     *
     * @param level 服务器世界
     * @param cfg   陨石配置
     * @return 是否成功生成
     */
    private static boolean trySpawnMeteor(ServerLevel level, MeteorShowerConfig cfg) {
        var players = level.players();
        if (players.isEmpty()) return false;
        ServerPlayer player = players.get(level.random.nextInt(players.size()));

        // 使用配置的半径
        double angle = level.random.nextDouble() * 2.0 * Math.PI;
        double dist = level.random.nextDouble() * cfg.spawnRadius;
        double spawnX = player.getX() + Math.cos(angle) * dist;
        double spawnZ = player.getZ() + Math.sin(angle) * dist;

        // 获取地面高度
        BlockPos groundPos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(spawnX, 0, spawnZ));
        double groundY = groundPos.getY();
        double spawnY = groundY + cfg.heightAboveGround;

        // 确保生成点在天空（不被方块阻挡）
        BlockPos spawnPos = BlockPos.containing(spawnX, spawnY, spawnZ);
        if (!level.isEmptyBlock(spawnPos)) {
            int maxSearch = 30;
            for (int i = 1; i <= maxSearch; i++) {
                if (level.isEmptyBlock(spawnPos.above(i))) {
                    spawnY += i;
                    break;
                }
            }
        }

        // 生成陨石
        Vec3 pos = new Vec3(spawnX, spawnY, spawnZ);
        Vec3 velocity = new Vec3(
                (level.random.nextDouble() - 0.5) * 0.4,
                cfg.fallSpeed,
                (level.random.nextDouble() - 0.5) * 0.4);

        MeteoriteEntity.spawn(level, pos, velocity);
        return true;
    }
}