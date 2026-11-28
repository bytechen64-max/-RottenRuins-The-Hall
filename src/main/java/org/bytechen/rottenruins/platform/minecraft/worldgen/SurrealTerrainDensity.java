package org.bytechen.rottenruins.platform.minecraft.worldgen;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.bytechen.rottenruins.core.worldgen.SurrealTerrain;

/** Codec and noise-seed visitor for Minecraft 1.20.1. No world/server references are retained. */
public record SurrealTerrainDensity(DensityFunction original, DensityFunction continents,
                                    NoiseHolder regions, NoiseHolder cells) implements DensityFunction {
    public static final KeyDispatchDataCodec<SurrealTerrainDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("original").forGetter(SurrealTerrainDensity::original),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(SurrealTerrainDensity::continents),
                    NoiseHolder.CODEC.fieldOf("regions").forGetter(SurrealTerrainDensity::regions),
                    NoiseHolder.CODEC.fieldOf("cells").forGetter(SurrealTerrainDensity::cells)
            ).apply(instance, SurrealTerrainDensity::new)));

    @Override public double compute(FunctionContext context) {
        int x=context.blockX(),z=context.blockZ();
        return SurrealTerrain.density(x,context.blockY(),z, original.compute(context),continents.compute(context),
                regions.getValue(x,0,z), (cx,cz) -> cells.getValue(cx,0,cz));
    }
    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values,this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new SurrealTerrainDensity(original.mapAll(visitor),continents.mapAll(visitor),
                visitor.visitNoise(regions),visitor.visitNoise(cells)));
    }
    @Override public double minValue() {return Math.min(original.minValue(),-128);}
    @Override public double maxValue() {return Math.max(original.maxValue(),128);}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {return CODEC;}
}
