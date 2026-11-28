package org.bytechen.rottenruins.utils;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * 实体选择工具：按形状（AABB / 方形 / 球 / 圆柱 / 前方扇形）圈出一批活着的实体。
 * 距离量到"目标碰撞箱最近点"而非脚底中心；自动排除旁观者与 except（按 UUID 比）；返回值永远非 null。
 * 只按几何筛（更细的条件交给调用方 stream().filter），且都该在服务端做 —— 客户端实体列表只是同屏副本。
 */
public final class EntityChooseUtil {

    private EntityChooseUtil() {
    }

    /** 最底层的一档：在任意 AABB 里找指定类型的实体（带泛型与谓词，查掉落物/箭/别的模组实体时用它）。 */
    @NotNull
    public static <T extends Entity> List<T> inBox(@NotNull Level level, @Nullable Entity except,
                                                   @NotNull AABB box, @NotNull Class<T> type,
                                                   @Nullable Predicate<? super T> filter) {
        return level.getEntitiesOfClass(type, box,
                candidate -> isEligible(except, candidate)
                        && (filter == null || filter.test(candidate)));
    }

    @NotNull
    public static List<LivingEntity> inBox(@NotNull Level level, @Nullable Entity except,
                                           @NotNull AABB box) {
        return inBox(level, except, box, LivingEntity.class, null);
    }

    /** 以某个实体为中心、在给定 AABB 里找生物并排除它自己（保留方法名为兼容原调用点）。 */
    @NotNull
    public static List<LivingEntity> getEntitiesAround(@NotNull Entity entity, @NotNull AABB box) {
        return inBox(entity.level(), entity, box);
    }

    /** 用两个角点指定长方体范围；角点顺序无所谓，内部自动取 min/max。 */
    @NotNull
    public static List<LivingEntity> inBox(@NotNull Level level, @Nullable Entity except,
                                           double x1, double y1, double z1,
                                           double x2, double y2, double z2) {
        return inBox(level, except, boxBetween(x1, y1, z1, x2, y2, z2));
    }

    /** 两个角点围成的 AABB（自动排序，不会因为 min>max 得到空盒子）。 */
    @NotNull
    public static AABB boxBetween(double x1, double y1, double z1,
                                  double x2, double y2, double z2) {
        return new AABB(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /** 实体周围方形区域（碰撞箱朝六个方向各撑开 radius）。最快的一档：只查盒子不做距离判定，
     *  角落目标离中心其实是 radius×√3 —— 方形的定义，不是 bug；要圆的用 {@link #sphereAround}。 */
    @NotNull
    public static List<LivingEntity> squareAround(@NotNull Entity center, double radius) {
        return inBox(center.level(), center, center.getBoundingBox().inflate(Math.max(0.0d, radius)));
    }

    /** 以实体为中心、半径 radius 的球内生物（不含它自己）。先查外接正方体再按"碰撞箱到中心最短距离"筛圆。 */
    @NotNull
    public static List<LivingEntity> sphereAround(@NotNull Entity center, double radius) {
        return sphereAt(center.level(), center, center.getBoundingBox().getCenter(), radius);
    }

    /** 以任意坐标为中心的球。 */
    @NotNull
    public static List<LivingEntity> sphereAt(@NotNull Level level, @Nullable Entity except,
                                              @NotNull Vec3 center, double radius) {
        double r = Math.max(0.0d, radius);
        double rSqr = r * r;
        AABB box = new AABB(center, center).inflate(r);
        return level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> isEligible(except, candidate)
                        && candidate.getBoundingBox().distanceToSqr(center) <= rSqr);
    }

    /** 以实体脚底为底面中心、向上高 height、半径 radius 的圆柱内生物。半径只算水平距离（XZ），
     *  高度是硬的上下边界（球则是三维直线距离）；height 传负会向下延伸。 */
    @NotNull
    public static List<LivingEntity> cylinderAround(@NotNull Entity center, double radius,
                                                    double height) {
        return cylinderAt(center.level(), center, center.position(), radius, height);
    }

    @NotNull
    public static List<LivingEntity> cylinderAt(@NotNull Level level, @Nullable Entity except,
                                                @NotNull Vec3 base, double radius, double height) {
        double r = Math.max(0.0d, radius);
        double rSqr = r * r;
        double y1 = base.y;
        double y2 = base.y + height;
        AABB box = new AABB(base.x - r, Math.min(y1, y2), base.z - r,
                base.x + r, Math.max(y1, y2), base.z + r);
        return level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> isEligible(except, candidate)
                        && horizontalDistanceSqr(candidate, base) <= rSqr);
    }

    /** 实体前方的扇形（锥形）区域内的生物，朝向取视线。angleDegrees 是<b>总张角</b>：90 即左右各 45°，360 等于球。 */
    @NotNull
    public static List<LivingEntity> sectorInFront(@NotNull Entity source, double radius,
                                                   double angleDegrees) {
        return sectorInFront(source, radius, angleDegrees, false);
    }

    /** 同上，horizontalOnly = 忽略俯仰只比水平夹角（地面扇形技能该用它，否则站在斜坡上会漏掉脚下的目标）。 */
    @NotNull
    public static List<LivingEntity> sectorInFront(@NotNull Entity source, double radius,
                                                   double angleDegrees, boolean horizontalOnly) {
        return sectorFrom(source.level(), source, source.getEyePosition(),
                source.getLookAngle(), radius, angleDegrees, horizontalOnly);
    }

    /** 最通用的一档：任意起点 + 任意朝向的扇形。direction 不需要先归一化，零向量视为没有方向（返回空表）。 */
    @NotNull
    public static List<LivingEntity> sectorFrom(@NotNull Level level, @Nullable Entity except,
                                                @NotNull Vec3 origin, @NotNull Vec3 direction,
                                                double radius, double angleDegrees,
                                                boolean horizontalOnly) {
        Vec3 dir = horizontalOnly
                ? new Vec3(direction.x, 0.0d, direction.z)
                : direction;
        double r = Math.max(0.0d, radius);
        if (r <= 0.0d || dir.lengthSqr() < 1.0e-8) {
            // 没半径或者没朝向 —— 扇形没有意义，直接给空表而不是退化成"整个球"
            return List.of();
        }
        dir = dir.normalize();

        // 张角钳在 [0, 360]：小于 0 没有意义，大于 360 已经不是扇形了
        double halfAngle = Mth.clamp(angleDegrees, 0.0d, 360.0d) * 0.5d;
        double cosLimit = Math.cos(Math.toRadians(halfAngle));

        double rSqr = r * r;
        Vec3 finalDir = dir;
        AABB box = new AABB(origin, origin).inflate(r);
        return level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> isEligible(except, candidate)
                        && candidate.getBoundingBox().distanceToSqr(origin) <= rSqr
                        && isInSector(candidate, origin, finalDir, cosLimit, horizontalOnly));
    }

    /** 最基本的两个条件：活着（非旁观），且不是要排除的那个。 */
    private static boolean isEligible(@Nullable Entity except, @NotNull Entity candidate) {
        if (candidate.isSpectator() || !candidate.isAlive()) {
            return false;
        }
        // 按 UUID 比：从 UUID 反查出来的实体实例和列表里那个不保证是同一个对象
        return except == null || !except.getUUID().equals(candidate.getUUID());
    }

    /** 目标是否落在扇形张角内。方向取"顶点 → 目标碰撞箱最近点"，向量长度为 0（贴脸）时算在扇形内。 */
    private static boolean isInSector(@NotNull Entity candidate, @NotNull Vec3 origin,
                                      @NotNull Vec3 dir, double cosLimit,
                                      boolean horizontalOnly) {
        Vec3 toTarget = closestPointOnBox(candidate.getBoundingBox(), origin).subtract(origin);
        if (horizontalOnly) {
            toTarget = new Vec3(toTarget.x, 0.0d, toTarget.z);
        }
        double lengthSqr = toTarget.lengthSqr();
        if (lengthSqr < 1.0e-8) {
            return true;
        }
        // dot / |v| = cos(夹角)；和阈值比，省掉一次 acos
        double cos = toTarget.dot(dir) / Math.sqrt(lengthSqr);
        return cos >= cosLimit;
    }

    /** 碰撞箱上离某点最近的那个点（点在箱内时就是它自己）。 */
    @NotNull
    private static Vec3 closestPointOnBox(@NotNull AABB box, @NotNull Vec3 point) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    /** 水平（XZ 平面）距离的平方 —— 圆柱用。 */
    private static double horizontalDistanceSqr(@NotNull Entity target, @NotNull Vec3 point) {
        double dx = target.getX() - point.x;
        double dz = target.getZ() - point.z;
        return dx * dx + dz * dz;
    }
}
