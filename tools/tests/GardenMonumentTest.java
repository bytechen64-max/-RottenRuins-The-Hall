import org.bytechen.rottenruins.core.worldgen.GardenMonument;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Kind;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Material;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Placement;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Pure Java geometry checks; does not load Minecraft or start a game/server. */
public final class GardenMonumentTest {
    public static void main(String[] args) throws Exception {
        // Check that a two-block-high player can climb the entire front flight.
        int previous = 8;
        for (int z = -48; z <= -4; z++) {
            int top = z == -4 ? 52 : z + 56;
            if (z > -48) require(top - previous <= 1, "unclimbable tread at " + z);
            require(solid(GardenMonument.sample(Kind.EMPTY_THRONE, 10, top, z)), "missing tread at " + z);
            for (int dy = 1; dy <= 2; dy++) {
                require(!solid(GardenMonument.sample(Kind.EMPTY_THRONE, 10, top + dy, z)), "stair headroom at " + z);
            }
            previous = top;
        }
        require(GardenMonument.sample(Kind.EMPTY_THRONE, 0, 52, 10) == Material.GRASS, "tree soil");
        require(GardenMonument.sample(Kind.EMPTY_THRONE, 0, 53, 10) == Material.LOG, "tree root contact");
        require(GardenMonument.sample(Kind.EMPTY_THRONE, 20, 54, 10) == Material.AIR, "empty seat");
        require(GardenMonument.sample(Kind.SUNKEN_CROWN, 0, 20, 0) == Material.KEEP, "open crown lagoon");

        for (Kind kind : Kind.values()) {
            // Negative world coordinates exercise chunk floor division, and all four rotations.
            int r = kind.radius;
            checkSlices(new Placement(kind, -9, 80, -25, 0));
            checkSlices(new Placement(kind, 17, 80, 31, 0));
            int solidCount = 0;
            int[] counts = new int[Material.values().length];
            Set<Long> occupied = new HashSet<>();
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                for (int y = kind.minY; y <= kind.maxY; y++) {
                    Material material = GardenMonument.sample(kind, x, y, z);
                    counts[material.ordinal()]++;
                    if (solid(material)) {
                        solidCount++;
                        occupied.add(key(x, y, z));
                    }
                    // Rotating the footprint must preserve every voxel, including across slice boundaries.
                    for (int turn = 0; turn < 4; turn++) {
                        int rx = switch (turn) { case 0 -> x; case 1 -> -z; case 2 -> -x; default -> z; };
                        int rz = switch (turn) { case 0 -> z; case 1 -> x; case 2 -> -z; default -> -x; };
                        require(new Placement(kind, -9, 80, -25, turn).at(-9 + rx, 80 + y, -25 + rz) == material, "rotation changed geometry");
                    }
                }
            }
            // Every solid voxel must connect to the monument, preventing floating decorations/teeth.
            ArrayDeque<Long> queue = new ArrayDeque<>();
            long start = occupied.iterator().next();
            occupied.remove(start);
            queue.add(start);
            int[][] directions = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            while (!queue.isEmpty()) {
                long k = queue.removeFirst();
                int x = (int) (k >> 20) - 128;
                int y = (int) ((k >> 10) & 1023) - 128;
                int z = (int) (k & 1023) - 128;
                for (int[] d : directions) {
                    long next = key(x + d[0], y + d[1], z + d[2]);
                    if (occupied.remove(next)) queue.add(next);
                }
            }
            require(occupied.isEmpty(), kind + " has disconnected blocks: " + occupied.size());
            require(solidCount > 50000, "landmark scale");
            require(counts[Material.TRIM.ordinal()] > 0 && counts[Material.CARVED.ordinal()] > 0, "masonry detail");
            if (kind == Kind.SUNKEN_CROWN) require(counts[Material.DOMITE.ordinal()] > 0 && counts[Material.DOMERITE.ordinal()] > 0, "fracture ore");
            require(GardenMonument.sample(kind, r + 1, 0, 0) == Material.KEEP, "horizontal bounds");
            require(GardenMonument.sample(kind, 0, kind.maxY + 1, 0) == Material.KEEP, "vertical bounds");
            System.out.println(kind + ": connected geometry, " + solidCount + " solid voxels, rotations and slice coordinates passed");
            if (args.length > 0) export(kind, Path.of(args[0]));
        }
    }

    private static long key(int x, int y, int z) { return ((long)(x + 128) << 20) | ((long)(y + 128) << 10) | (z + 128); }

    private static void checkSlices(Placement placement) {
        Set<Long> covered = new HashSet<>();
        int radius = placement.kind().radius;
        for (var slice : placement.slices()) {
            require(Math.floorDiv(slice.minX(), 16) == Math.floorDiv(slice.maxX(), 16), "slice crosses chunk X");
            require(Math.floorDiv(slice.minZ(), 16) == Math.floorDiv(slice.maxZ(), 16), "slice crosses chunk Z");
            for (int x = slice.minX(); x <= slice.maxX(); x++) for (int z = slice.minZ(); z <= slice.maxZ(); z++) {
                require(Math.abs(x - placement.centerX()) <= radius && Math.abs(z - placement.centerZ()) <= radius, "slice outside footprint");
                require(covered.add(((long)x << 32) ^ (z & 0xffffffffL)), "overlapping slices");
            }
        }
        require(covered.size() == (2 * radius + 1) * (2 * radius + 1), "missing slice coverage");
    }
    private static boolean solid(Material m) { return m != Material.KEEP && m != Material.AIR; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    // Optional voxel export for an offline geometry preview; not a Minecraft screenshot.
    private static void export(Kind kind, Path directory) throws Exception {
        Files.createDirectories(directory);
        try (var out = new DataOutputStream(Files.newOutputStream(directory.resolve(kind.name() + ".voxels")))) {
            int r = kind.radius;
            out.writeInt(r); out.writeInt(kind.minY); out.writeInt(kind.maxY);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) for (int y = kind.minY; y <= kind.maxY; y++) {
                out.writeByte(GardenMonument.sample(kind, x, y, z).ordinal());
            }
        }
    }
}
