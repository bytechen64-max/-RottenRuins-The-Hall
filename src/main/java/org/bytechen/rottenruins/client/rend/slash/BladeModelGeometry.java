package org.bytechen.rottenruins.client.rend.slash;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** Reads baked geometry once per model/scale, including the opaque silhouette side quads. */
public final class BladeModelGeometry {
    private record Scale(int x, int y, int z, boolean requireLongAxis) {}
    private record Point(int x, int y, int z) {}
    private static final Map<BakedModel, Map<Scale, Optional<BladeGeometry.Profile>>> CACHE = new WeakHashMap<>();

    private BladeModelGeometry() {}

    static Optional<BladeGeometry.Profile> profile(BakedModel model, ItemStack stack, Vector3f scale) {
        return profile(model, stack, scale, true);
    }

    public static Optional<BladeGeometry.Profile> flatProfile(BakedModel model, ItemStack stack, Vector3f scale) {
        return profile(model, stack, scale, false);
    }

    private static Optional<BladeGeometry.Profile> profile(BakedModel model, ItemStack stack, Vector3f scale,
                                                           boolean requireLongAxis) {
        Scale key = new Scale(Math.round(scale.x * 10000), Math.round(scale.y * 10000), Math.round(scale.z * 10000), requireLongAxis);
        return CACHE.computeIfAbsent(model, ignored -> new HashMap<>()).computeIfAbsent(key, ignored -> {
            Set<Point> unique = new HashSet<>();
            var points = new ArrayList<BladeGeometry.Vec>();
            RandomSource random = RandomSource.create(42);
            for (BakedModel pass : model.getRenderPasses(stack, true)) {
                for (int face = -1; face < 6; face++) {
                    random.setSeed(42);
                    for (BakedQuad quad : pass.getQuads(null, face < 0 ? null : Direction.values()[face], random)) {
                        int[] data = quad.getVertices();
                        int stride = data.length / 4;
                        if (stride < 3) continue;
                        for (int i = 0; i < 4; i++) {
                            float x = Float.intBitsToFloat(data[i * stride]);
                            float y = Float.intBitsToFloat(data[i * stride + 1]);
                            float z = Float.intBitsToFloat(data[i * stride + 2]);
                            if (!Float.isFinite(x + y + z)) continue;
                            Point point = new Point(Math.round(x * 100000), Math.round(y * 100000), Math.round(z * 100000));
                            if (unique.add(point)) points.add(new BladeGeometry.Vec(
                                    (x - 0.5) * scale.x, (y - 0.5) * scale.y, (z - 0.5) * scale.z));
                        }
                    }
                }
            }
            return requireLongAxis ? BladeGeometry.analyze(points) : BladeGeometry.analyzeFlat(points);
        });
    }
}
