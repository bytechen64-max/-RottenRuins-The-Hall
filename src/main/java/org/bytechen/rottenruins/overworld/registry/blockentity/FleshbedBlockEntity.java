package org.bytechen.rottenruins.overworld.registry.blockentity;

import org.bytechen.rottenruins.platform.forge.story.ForgeStoryServices;
import org.bytechen.rottenruins.platform.minecraft.story.StoryPacketCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.story.StoryFonts;
import org.bytechen.rottenruins.gameplay.story.StoryText;
import org.bytechen.rottenruins.gameplay.story.StoryDirector;
import org.bytechen.rottenruins.gameplay.story.StoryCatalog;
import org.bytechen.rottenruins.gameplay.story.StoryDefinition;
import org.bytechen.rottenruins.overworld.registry.RegisterBlockEntities;
import org.bytechen.rottenruins.network.s2c.FleshbedRitualParticles;
import org.bytechen.rottenruins.content.ritual.HallSummoningRitual;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.linked.SummoningPillarEntity;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** Server-authoritative feeding, fixed-clock story and one-shot ritual reward. */
public final class FleshbedBlockEntity extends BlockEntity {
    public static final int FOOD_REQUIRED = 128;
    public static final int PAGE_TICKS = 200;
    public static final int RITUAL_TICKS = PAGE_TICKS * 4;
    /** 仪式结束后再投入的魔法催化剂数量：喂满后右键启用王庭召唤仪式。 */
    public static final int CATALYST_REQUIRED = 20;
    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private int fed;
    private long startTime = -1;
    private boolean completed;
    private int spawnedMask;
    private int catalysts;
    /** 王庭仪式已执行的 tick 数（-1 = 未启用）：按执行 tick 计，不按游戏时间，避免区块卸载后回来直接跳完。 */
    private int hallElapsed = -1;
    private boolean hallDone;
    private UUID initiator;
    private UUID session;
    private ServerPlayer lastViewer;
    private CompoundTag storySnapshot;
    private int ritualTicks = RITUAL_TICKS;

    public FleshbedBlockEntity(BlockPos pos, BlockState state) {
        super(RegisterBlockEntities.FLESHBED.get(), pos, state);
    }

    public int foodCount() { return fed; }
    public boolean isRunning() { return startTime >= 0 && !completed; }
    public boolean isCompleted() { return completed; }
    public int spawnedCount() { return Integer.bitCount(spawnedMask); }
    public long endTime() { return startTime + ritualTicks; }
    public int catalystCount() { return catalysts; }
    public boolean isHallRunning() { return hallElapsed >= 0 && !hallDone; }
    public boolean isHallDone() { return hallDone; }
    public boolean needsTicket() { return isRunning() || (completed && spawnedMask != 15); }

    public void interact(ServerPlayer player, InteractionHand hand) {
        if (!completed) {
            if (isRunning()) { message(player, "running"); return; }
            if (fed >= FOOD_REQUIRED) { start(player); return; }
        } else {
            // 仪式结束后的第二阶段：继续喂魔法催化剂，喂满再右键启用王庭召唤仪式。
            if (isHallRunning()) { message(player, "hall_running"); return; }
            if (hallDone) { message(player, "hall_complete"); return; }
            if (catalysts >= CATALYST_REQUIRED) { startHall(player); return; }
            var held = player.getItemInHand(hand);
            if (!held.is(RegisterItem.MAGIC_CATA.get())) {
                message(player, "catalyst", catalysts, CATALYST_REQUIRED);
                return;
            }
            int count = Math.min(held.getCount(), CATALYST_REQUIRED - catalysts);
            catalysts += count;
            if (!player.getAbilities().instabuild) held.shrink(count);
            setChanged();
            message(player, catalysts == CATALYST_REQUIRED ? "catalyst_ready" : "catalyst",
                    catalysts, CATALYST_REQUIRED);
            return;
        }
        var stack = player.getItemInHand(hand);
        if (!stack.is(Items.ROTTEN_FLESH)) { message(player, "feed", fed, FOOD_REQUIRED); return; }
        int count = Math.min(stack.getCount(), FOOD_REQUIRED - fed);
        fed += count;
        if (!player.getAbilities().instabuild) stack.shrink(count);
        setChanged();
        message(player, fed == FOOD_REQUIRED ? "ready" : "feed", fed, FOOD_REQUIRED);
    }

    public boolean start(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || fed < FOOD_REQUIRED || isRunning() || completed) return false;
        var selected = StoryDirector.select(player, "fleshbed_start", StoryDirector.context(player));
        if (selected == null || selected.story().timing() != StoryDefinition.Timing.CLOCKED) {
            player.displayClientMessage(Component.translatable("rotten_ruins.story.missing_ritual"), true);
            return false;
        }
        // Check four independent sectors before accepting the ritual; do not place pillars inside blocks.
        for (int i = 0; i < 4; i++) {
            var probe = CorruptionEntityTypes.SUMMONING_PILLAR.get().create(server);
            if (probe == null || findSpawn(server, probe, i) == null) { message(player, "space"); return false; }
        }
        startTime = server.getGameTime();
        initiator = player.getUUID();
        session = UUID.randomUUID();
        ritualTicks = selected.story().durationTicks();
        storySnapshot = StoryPacketCodec.encode(selected.story(), startTime, session);
        StoryDirector.commit(player, selected);
        setChanged();
        forceChunks(server, true);
        showStory(player);
        lastViewer = player;
        message(player, "started");
        return true;
    }

    /**
     * 启用王庭召唤仪式：需要温床已完成、已投入 {@value CATALYST_REQUIRED} 个魔法催化剂。
     *
     * <p>仪式本身（陨石雨 + 地表/群系改造）在 {@link HallSummoningRitual} 里，
     * 这个方块实体只负责状态、存续与喂料。</p>
     */
    public boolean startHall(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || !completed || hallElapsed >= 0 || catalysts < CATALYST_REQUIRED) return false;
        hallElapsed = 0;
        hallDone = false;
        initiator = player.getUUID();
        setChanged();
        server.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.2f, 0.6f);
        message(player, "hall_started");
        return true;
    }

    public static List<Component> storyPages() {
        var definition = StoryCatalog.snapshot().stories().get(net.minecraft.resources.ResourceLocation.parse("rotten_ruins:fleshbed"));
        if (definition == null) return List.of();
        return definition.pages().stream().map(page -> {
            var text = Component.Serializer.fromJson(page.textJson());
            return (Component) (page.font() == null ? text : text.withStyle(style -> style.withFont(page.font())));
        }).toList();
    }

    private void showStory(ServerPlayer player) {
        if (storySnapshot != null) ForgeStoryServices.sendEncoded(player, storySnapshot);
        else StoryText.timedSequence(player, storyPages(), PAGE_TICKS, startTime, session);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FleshbedBlockEntity bed) {
        if (!(level instanceof ServerLevel server) || bed.startTime < 0) return;
        long now = server.getGameTime();
        if (!bed.completed) {
            var viewer = bed.initiator == null ? null : server.getServer().getPlayerList().getPlayer(bed.initiator);
            if (viewer != null && viewer.serverLevel() != server) viewer = null;
            if (viewer != bed.lastViewer) {
                bed.lastViewer = viewer;
                if (viewer != null && now < bed.endTime()) bed.showStory(viewer);
            }
            if (now >= bed.endTime()) {
                bed.completed = true;
                bed.setChanged();
                if (viewer != null) {
                    StoryText.clearSequence(viewer, bed.session);
                    message(viewer, "complete");
                }
                bed.erupt(server, 120);
            } else if ((now & 1) == 0) {
                int count = 12 + (int) (24 * Math.max(0, now - bed.startTime) / bed.ritualTicks);
                bed.erupt(server, count);
            }
        }
        if (bed.completed) {
            if (bed.spawnedMask != 15) bed.spawnPillars(server);
            if (now < bed.endTime() + 100 && (now & 1) == 0) bed.erupt(server, 36);
        }
        if (bed.hallElapsed >= 0 && !bed.hallDone) {
            if (bed.hallElapsed >= HallSummoningRitual.DURATION_TICKS) {
                bed.hallDone = true;
                bed.setChanged();
                var owner = bed.initiator == null ? null : server.getServer().getPlayerList().getPlayer(bed.initiator);
                if (owner != null && owner.serverLevel() == server) message(owner, "hall_complete");
            } else {
                HallSummoningRitual.tick(server, bed.worldPosition, bed.hallElapsed);
                bed.hallElapsed++;
            }
        }
    }

    private void spawnPillars(ServerLevel server) {
        for (int i = 0; i < 4; i++) {
            if ((spawnedMask & (1 << i)) != 0) continue;
            var pillar = CorruptionEntityTypes.SUMMONING_PILLAR.get().create(server);
            if (pillar == null) continue;
            BlockPos spawn = findSpawn(server, pillar, i);
            if (spawn == null) continue;
            pillar.finalizeSpawn(server, server.getCurrentDifficultyAt(spawn), MobSpawnType.TRIGGERED, null, null);
            pillar.setPersistenceRequired();
            if (server.addFreshEntity(pillar)) {
                spawnedMask |= 1 << i;
                setChanged();
            }
        }
        if (spawnedMask == 15) forceChunks(server, false);
    }

    private BlockPos findSpawn(ServerLevel server, SummoningPillarEntity pillar, int sector) {
        Direction direction = DIRECTIONS[sector];
        Direction side = direction.getClockWise();
        for (int radius = 3; radius <= 5; radius++) {
            for (int offset : new int[]{0, 1, -1, 2, -2}) {
                for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4}) {
                    BlockPos target = worldPosition.relative(direction, radius).relative(side, offset).offset(0, dy, 0);
                    if (!server.getWorldBorder().isWithinBounds(target) || server.isOutsideBuildHeight(target)
                            || !server.getBlockState(target.below()).isFaceSturdy(server, target.below(), Direction.UP)
                            || !server.getFluidState(target).isEmpty() || !server.getFluidState(target.above()).isEmpty()) continue;
                    pillar.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0, 0);
                    if (server.noCollision(pillar)) return target;
                }
            }
        }
        return null;
    }

    private void erupt(ServerLevel server, int count) {
        FleshbedRitualParticles.send(server, worldPosition, count);
    }

    private void forceChunks(ServerLevel server, boolean add) {
        for (int x = (worldPosition.getX() - 6) >> 4; x <= (worldPosition.getX() + 6) >> 4; x++) {
            for (int z = (worldPosition.getZ() - 6) >> 4; z <= (worldPosition.getZ() + 6) >> 4; z++) {
                ForgeChunkManager.forceChunk(server, HallMod.MODID, worldPosition, x, z, add, true);
            }
        }
    }

    public void cancel() {
        if (!(level instanceof ServerLevel server) || startTime < 0) return;
        var player = initiator == null ? null : server.getServer().getPlayerList().getPlayer(initiator);
        if (player != null && session != null) StoryText.clearSequence(player, session);
        forceChunks(server, false);
    }

    private static void message(ServerPlayer player, String suffix, Object... args) {
        player.displayClientMessage(Component.translatable("rotten_ruins.fleshbed." + suffix, args), true);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Fed", fed);
        tag.putLong("StartTime", startTime);
        tag.putBoolean("Completed", completed);
        tag.putInt("SpawnedMask", spawnedMask);
        tag.putInt("RitualTicks", ritualTicks);
        tag.putInt("Catalysts", catalysts);
        tag.putInt("HallElapsed", hallElapsed);
        tag.putBoolean("HallDone", hallDone);
        if (storySnapshot != null) tag.put("StorySnapshot", storySnapshot.copy());
        if (initiator != null) tag.putUUID("Initiator", initiator);
        if (session != null) tag.putUUID("StorySession", session);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        fed = Math.max(0, Math.min(FOOD_REQUIRED, tag.getInt("Fed")));
        startTime = tag.contains("StartTime") ? tag.getLong("StartTime") : -1;
        completed = tag.getBoolean("Completed");
        spawnedMask = tag.getInt("SpawnedMask") & 15;
        ritualTicks = tag.contains("RitualTicks") ? Math.max(20, Math.min(384000, tag.getInt("RitualTicks"))) : RITUAL_TICKS;
        catalysts = Math.max(0, Math.min(CATALYST_REQUIRED, tag.getInt("Catalysts")));
        hallElapsed = tag.contains("HallElapsed") ? tag.getInt("HallElapsed") : -1;
        hallDone = tag.getBoolean("HallDone");
        storySnapshot = tag.contains("StorySnapshot") ? tag.getCompound("StorySnapshot").copy() : null;
        initiator = tag.hasUUID("Initiator") ? tag.getUUID("Initiator") : null;
        session = tag.hasUUID("StorySession") ? tag.getUUID("StorySession") : null;
        if (startTime >= 0 && session == null) session = UUID.randomUUID();
        lastViewer = null;
    }
}
