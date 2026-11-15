package org.bytechen.rottenruins.content.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;

/** Decorates lower island surfaces missed by the topmost heightmap. Bounded to the active chunk. */
public final class IslandVegetationFeature extends Feature<NoneFeatureConfiguration> {
    public IslandVegetationFeature() {super(NoneFeatureConfiguration.CODEC);}
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level=context.level(); var random=context.random();
        var tree=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(
                ResourceKey.create(Registries.CONFIGURED_FEATURE,ResourceLocation.fromNamespaceAndPath("rotten_ruins","hall_tree")));
        int chunkX=Math.floorDiv(context.origin().getX(),16)*16;
        int chunkZ=Math.floorDiv(context.origin().getZ(),16)*16;
        boolean placed=false;
        // A six-block margin keeps the reused tree's crown/spacing reads within the active chunk.
        for(int attempt=0;attempt<3;attempt++) {
            int x=chunkX+6+random.nextInt(4), z=chunkZ+6+random.nextInt(4);
            boolean highest=true;
            for(int y=Math.min(level.getMaxBuildHeight()-12,704);y>=112;y--) {
                BlockPos soil=new BlockPos(x,y,z);
                if (!level.getBlockState(soil).is(RegisterBlock.HALL_GRASS_BLOCK.get()) || !level.isEmptyBlock(soil.above())) continue;
                if (highest) {highest=false;continue;}
                placed |= tree.place(level,context.chunkGenerator(),random,soil.above());
                if (level.isEmptyBlock(soil.above()) && random.nextBoolean()) {
                    var plant=RegisterBlock.HALL_GRASS.get().defaultBlockState();
                    if(plant.canSurvive(level,soil.above())) level.setBlock(soil.above(),plant,2);
                }
                y-=12;
            }
        }
        return placed;
    }
}
