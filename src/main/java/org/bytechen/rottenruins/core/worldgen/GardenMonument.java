package org.bytechen.rottenruins.core.worldgen;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry. Coordinates and palette names are independent of Minecraft and block registries. */
public final class GardenMonument {
    public enum Kind {
        EMPTY_THRONE(48, -24, 127), SUNKEN_CROWN(72, -32, 84);

        public final int radius;
        public final int minY;
        public final int maxY;

        Kind(int radius, int minY, int maxY) {
            this.radius = radius;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    public enum Material { KEEP, AIR, STONE, TRIM, CARVED, DIRT, GRASS, LOG, LEAVES, DOMITE, DOMERITE }

    public record Slice(int minX, int minZ, int maxX, int maxZ) {}

    /** Persisted per-piece placement; rotation is shared by all slices of a monument. */
    public record Placement(Kind kind, int centerX, int baseY, int centerZ, int turns) {
        public Placement {
            if (kind == null || turns < 0 || turns > 3) throw new IllegalArgumentException("Invalid monument placement");
        }

        public Material at(int worldX, int worldY, int worldZ) {
            int x = worldX - centerX;
            int z = worldZ - centerZ;
            return switch (turns) {
                case 0 -> sample(kind, x, worldY - baseY, z);
                case 1 -> sample(kind, z, worldY - baseY, -x);
                case 2 -> sample(kind, -x, worldY - baseY, -z);
                default -> sample(kind, -z, worldY - baseY, x);
            };
        }

        /** Exact, non-overlapping chunk slices, including structures centered at negative coordinates. */
        public List<Slice> slices() {
            int minX = centerX - kind.radius;
            int maxX = centerX + kind.radius;
            int minZ = centerZ - kind.radius;
            int maxZ = centerZ + kind.radius;
            List<Slice> result = new ArrayList<>();
            for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(maxX, 16); cx++) {
                for (int cz = Math.floorDiv(minZ, 16); cz <= Math.floorDiv(maxZ, 16); cz++) {
                    result.add(new Slice(Math.max(minX, cx * 16), Math.max(minZ, cz * 16),
                            Math.min(maxX, cx * 16 + 15), Math.min(maxZ, cz * 16 + 15)));
                }
            }
            return List.copyOf(result);
        }
    }

    private GardenMonument() {}

    public static Material sample(Kind kind, int x, int y, int z) {
        if (Math.abs(x) > kind.radius || Math.abs(z) > kind.radius || y < kind.minY || y > kind.maxY) {
            return Material.KEEP;
        }
        return kind == Kind.EMPTY_THRONE ? throne(x, y, z) : crown(x, y, z);
    }

    private static Material throne(int x, int y, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        // One small, persistent tree on the otherwise empty seat.
        int treeDistance = x * x + (z - 10) * (z - 10);
        if (x == 0 && z == 10 && y >= 53 && y <= 60) return Material.LOG;
        if (y >= 58 && y <= 64 && treeDistance + (y - 61) * (y - 61) <= 20) return Material.LEAVES;
        if (treeDistance <= 49 && y == 52) return Material.GRASS;
        if (treeDistance <= 49 && y >= 50 && y <= 51) return Material.DIRT;

        // Clear the seat, not the whole bounding box: surrounding terrain is retained.
        if (ax <= 27 && z >= -4 && z <= 25 && y >= 53 && y <= 69) return Material.AIR;
        // A narrow entrance and vaulted void below the seat.
        if (ax <= 7 && z >= -32 && z <= 25 && y >= 9 && y <= 16) return Material.AIR;
        if (ax <= 16 && az <= 16 && y >= 17 && y <= 39 - ax / 2) return Material.AIR;

        // Embedded foundations and three broad plinth courses.
        if (y < 0 && ax <= 32 && az <= 36) return Material.STONE;
        if (y >= 0 && y <= 8 && ax <= 40 - y / 3 && az <= 44 - y / 3) {
            return y % 4 == 0 ? Material.TRIM : Material.STONE;
        }
        // Each tread rises one block: the seat can be reached without flying.
        if (ax <= 12 && z >= -48 && z <= -5 && y >= 0 && y <= z + 56) {
            return y == z + 56 ? Material.TRIM : Material.STONE;
        }
        if (ax <= 28 && z >= -4 && z <= 27 && y >= 46 && y <= 52) {
            return y == 46 || y == 52 ? Material.TRIM : Material.STONE;
        }
        // Tapered piers and sweeping arms, with a pointed arcade behind the empty seat.
        if (ax >= 20 && ax <= 28 && az >= 18 && az <= 26 && y >= 9 && y <= 45) {
            return ax == 20 || ax == 28 ? Material.TRIM : Material.STONE;
        }
        int armTop = 64 + (z + 24) * (z + 24) / 200;
        if (ax >= 29 && ax <= 35 && z >= -24 && z <= 27 && y >= armTop - 5 && y <= armTop) {
            return y == armTop ? Material.TRIM : Material.STONE;
        }
        int pierWidth = Math.max(2, 5 - Math.max(0, y) / 18);
        if (Math.abs(ax - 32) <= pierWidth && z >= -24 && z <= -18 && y >= 9 && y <= 64) {
            return Material.STONE;
        }
        if (z >= 26 && z <= 37 && y >= 46 && y <= 127) {
            int outline = 30 - Math.max(0, y - 104) / 3;
            if (ax > outline) return Material.KEEP;
            // Three pointed lancets; width narrows toward each arch apex.
            int opening = ax <= 10 ? 0 : 20;
            int apex = opening == 0 ? 116 : 102;
            int halfWidth = opening == 0 ? 8 : 5;
            int distance = Math.abs(ax - opening);
            if (y >= 61 && y < apex && distance <= Math.min(halfWidth, (apex - y) / 2)) return Material.AIR;
            // Deep vertical reveals and projecting borders provide shadow instead of color bands.
            if (z <= 27 && y >= 58 && y <= 112 && (ax == 11 || ax == 12)) return Material.AIR;
            if (distance == Math.min(halfWidth, Math.max(0, (apex - y) / 2)) + 1 && y >= 59 && y <= apex + 2) return Material.CARVED;
            return ax >= outline - 1 || y >= 124 ? Material.TRIM : Material.STONE;
        }
        return Material.KEEP;
    }

    private static Material crown(int x, int y, int z) {
        double radius = Math.sqrt(x * x + z * z);
        if (radius < 51 || radius > 71) return Material.KEEP;
        double sectorPosition = (Math.atan2(z, x) + Math.PI) * 6 / Math.PI;
        int tooth = Math.floorMod((int) Math.floor(sectorPosition + 0.5), 12);
        double along = Math.abs(sectorPosition - Math.floor(sectorPosition + 0.5));
        boolean broken = tooth == 3;
        if (broken && y > 12) return Material.KEEP;
        if (y < 0) return radius >= 63 && radius <= 68 ? Material.STONE : Material.KEEP;
        if (y <= 28) {
            if (radius < 62 || radius > 70) return Material.KEEP;
            // Pointed openings between the ribs: sea water remains inside each opening.
            double gap = 0.5 - along;
            if (!broken && y >= 7 && y <= 24 && gap < Math.min(0.23, (25 - y) * 0.025)) return Material.KEEP;
            if (broken && y >= 8 && (x + 2 * z) % 7 == 0) return Material.DOMERITE;
            return y <= 2 || y >= 26 ? Material.TRIM : Material.STONE;
        }
        int top = tooth % 2 == 0 ? 84 : 66;
        if (tooth == 8) top = 43;
        if (y > top) return Material.KEEP;
        double t = (y - 28.0) / (top - 28.0);
        // Blades sweep inward as they taper, instead of rising as a cylindrical fence.
        double center = 66 - 11 * t * t;
        double thickness = 3.5 - 1.5 * t;
        double halfWidth = 0.38 * (1 - t) + 0.045;
        if (along > halfWidth || Math.abs(radius - center) > thickness) return Material.KEEP;
        if (tooth == 8 && y >= top - 2 && Math.floorMod(x * 3 + z, 5) == 0) return Material.DOMITE;
        return along >= halfWidth - 0.045 || y >= top - 2 ? Material.CARVED : Material.STONE;
    }
}
