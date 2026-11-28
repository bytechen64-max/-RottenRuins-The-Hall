package org.bytechen.rottenruins.overworld.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.GroundingStakeBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceWireBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceMachineBlock;
import org.bytechen.rottenruins.overworld.registry.blockentity.SourceMachineBlockEntity;
import org.bytechen.rottenruins.overworld.worldgen.HighPotentialChunks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** 只遍历已加载区块内的源导线，不加载远处区块，不输出原版红石信号。 */
public final class SourceWireNetwork {
    private static final Map<ServerLevel, TickCache> CACHE = new WeakHashMap<>();

    private static final class TickCache {
        long tick = Long.MIN_VALUE;
        final Set<Long> processed = new HashSet<>();
    }

    private SourceWireNetwork() {}

    public static void invalidate(ServerLevel level) { CACHE.remove(level); }

    private static boolean machine(BlockState state) { return state.getBlock() instanceof SourceMachineBlock; }
    private static boolean input(BlockState state,Direction side) { return !machine(state) || side==state.getValue(SourceMachineBlock.FACING).getOpposite(); }
    private static boolean output(BlockState state,Direction side) { return !machine(state) || side==state.getValue(SourceMachineBlock.FACING); }

    public static void refresh(ServerLevel level, BlockPos start, SourceWireBlock wire) {
        TickCache cache = CACHE.computeIfAbsent(level, ignored -> new TickCache());
        if (cache.tick != level.getGameTime()) {
            cache.tick = level.getGameTime();
            cache.processed.clear();
        }
        if (cache.processed.contains(start.asLong())) return;

        Map<Long, List<Long>> graph = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> highContacts = new HashSet<>();
        Map<Long, Boolean> terminalTypes = new HashMap<>();
        Set<Long> lowContacts = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.asLong());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            BlockState current=level.getBlockState(pos);
            List<Long> neighbors = new ArrayList<>();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (machine(current) && !SourceMachineBlock.hasPort(current,direction)) continue;
                BlockPos other=pos.relative(direction);
                if (!level.hasChunkAt(other)) continue;
                BlockState adjacent=level.getBlockState(other);
                boolean physical=false;
                if (machine(current)) {
                    physical=adjacent.is(wire) || SourceMachineBlock.hasPort(adjacent,direction.getOpposite());
                } else if (SourceMachineBlock.hasPort(adjacent,direction.getOpposite())) physical=true;
                else {
                    other=wire.wireNeighbor(level,pos,direction);
                    if (other!=null) {
                        adjacent=level.getBlockState(other);
                        physical=pos.equals(wire.wireNeighbor(level,other,direction.getOpposite()));
                    }
                }
                if (!physical || other==null) continue;
                if (output(current,direction) && input(adjacent,direction.getOpposite())) neighbors.add(other.asLong());
                if (seen.add(other.asLong())) queue.add(other);
            }
            graph.put(pos.asLong(), neighbors);
            for (Direction direction : Direction.values()) {
                if (machine(current) && !SourceMachineBlock.hasPort(current,direction)) continue;
                BlockPos terminal = pos.relative(direction);
                if (!level.hasChunkAt(terminal)) continue;
                BlockState state = level.getBlockState(terminal);
                if (!(state.getBlock() instanceof GroundingStakeBlock)) continue;
                BlockPos support = terminal.relative(state.getValue(GroundingStakeBlock.FACING).getOpposite());
                if (!level.hasChunkAt(support) || !state.canSurvive(level, terminal)) continue;
                boolean high = terminalTypes.computeIfAbsent(terminal.asLong(), ignored ->
                        HighPotentialChunks.isHighPotentialChunk(level, terminal));
                if (high && input(current,direction)) highContacts.add(pos.asLong());
                else if (!high && output(current,direction)) lowContacts.add(pos.asLong());
            }
        }
        Map<Long, Integer> strengths = PotentialCircuit.solve(graph, highContacts, lowContacts);
        for (long node : graph.keySet()) {
            BlockPos pos = BlockPos.of(node);
            BlockState old = level.getBlockState(pos);
            BlockState updated = (old.is(wire)?wire.connections(level, pos, old):old)
                    .setValue(SourceWireBlock.POWER, strengths.getOrDefault(node, 0));
            if (updated != old) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
        Map<Long,List<Long>> reverse=new HashMap<>();
        graph.forEach((node,neighbors)->neighbors.forEach(other->reverse.computeIfAbsent(other,ignored->new ArrayList<>()).add(node)));
        for (long node:graph.keySet()) {
            if (level.getBlockEntity(BlockPos.of(node)) instanceof SourceMachineBlockEntity collector && collector.collector) {
                resolveCollector(level,node,collector,reverse,strengths);
            }
        }
        cache.processed.addAll(graph.keySet());
    }

    /** 向上游寻找最近一层滤波器，防止跨过其它滤波器取到更早的设置。 */
    private static void resolveCollector(ServerLevel level,long node,SourceMachineBlockEntity collector,
                                         Map<Long,List<Long>> reverse,Map<Long,Integer> strengths) {
        if (!strengths.containsKey(node)) { collector.resolveFilter(null,0,0); return; }
        ArrayDeque<Long> queue=new ArrayDeque<>();
        Set<Long> seen=new HashSet<>();
        queue.add(node); seen.add(node);
        while (!queue.isEmpty()) {
            int size=queue.size();
            List<SourceMachineBlockEntity> filters=new ArrayList<>();
            for (int i=0;i<size;i++) {
                long current=queue.remove();
                if (current!=node && level.getBlockEntity(BlockPos.of(current)) instanceof SourceMachineBlockEntity filter && !filter.collector) {
                    filters.add(filter);
                    continue;
                }
                for (long previous:reverse.getOrDefault(current,List.of())) {
                    if (strengths.containsKey(previous) && seen.add(previous)) queue.add(previous);
                }
            }
            if (!filters.isEmpty()) {
                ElementBand element=filters.get(0).element();
                boolean conflict=filters.stream().anyMatch(filter->filter.element()!=element || filter.data.get(2)!=filters.get(0).data.get(2));
                collector.resolveFilter(conflict?null:element,conflict?3:element==null?2:1,filters.get(0).data.get(2));
                return;
            }
        }
        collector.resolveFilter(null,0,0);
    }
}
