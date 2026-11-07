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
 * <b>实体选择工具</b> —— 按形状快速地圈出一批活着的实体。
 *
 * <p>六种常用形状（都是"服务端判定用"）：</p>
 * <table border="1">
 *   <tr><th>方法</th><th>形状</th><th>典型用途</th></tr>
 *   <tr><td>{@link #inBox(Level, Entity, AABB)}</td><td>任意 AABB</td><td>已有现成盒子时</td></tr>
 *   <tr><td>{@link #inBox(Level, Entity, double, double, double, double, double, double)}</td>
 *       <td>指定坐标范围（两个角点）</td><td>区域/陷阱，两个角随便传，内部会排序</td></tr>
 *   <tr><td>{@link #squareAround(Entity, double)}</td><td>实体周围<b>方形</b></td>
 *       <td>最快的一档：只查盒子，不做距离判定</td></tr>
 *   <tr><td>{@link #sphereAround(Entity, double)}</td><td><b>圆形</b>（球）</td>
 *       <td>爆炸、光环、治疗圈</td></tr>
 *   <tr><td>{@link #cylinderAround(Entity, double, double)}</td><td><b>圆柱</b></td>
 *       <td>光柱、升降梯、塔状技能</td></tr>
 *   <tr><td>{@link #sectorInFront(Entity, double, double)}</td><td><b>前方扇形</b></td>
 *       <td>锥形吐息、横扫、正面 AoE</td></tr>
 * </table>
 *
 * <h3>三条统一的判定约定</h3>
 * <ol>
 *   <li><b>距离量到"目标碰撞箱的最近点"</b>，不是量到脚底中心。所以一只 5 格宽的大怪
 *       擦着场地边缘，也算"在里面" —— 不然会出现"我明明打到它了但它没掉血"。
 *       反过来，如果碰撞箱把中心整个罩住（贴脸），最近距离是 0，必然入选。</li>
 *   <li><b>自动排除两件事</b>：旁观者、以及调用方传进来的"排除对象"（按 <b>UUID</b> 比，
 *       不是按对象引用 —— 从 UUID 反查出来的实体和列表里那个不保证是同一个对象）。</li>
 *   <li><b>返回值永远非 null。</b> 没有命中时返回空表，调用方不用判 null，
 *       直接 {@code for} 或者 {@code isEmpty()}。返回的表是<b>快照</b>，不会随世界变化。</li>
 * </ol>
 *
 * <h3>要更细的条件怎么办</h3>
 * <p>这些方法只按几何筛。要"只要敌对生物""排除自己的宠物"这类条件，
 * 拿到列表后自己 {@code stream().filter(...)} 就行 —— 与其为每种条件再重载一遍，
 * 不如让调用方用 lambda 说清楚。</p>
 *
 * <p>只有最底层的 {@link #inBox(Level, Entity, AABB, Class, Predicate)} 带泛型与谓词，
 * 需要"查非生物实体"（掉落物、箭、别的模组的实体）时用它。</p>
 *
 * <p><b>注意</b>：这些查询都该在<b>服务端</b>做。客户端手里的实体列表只是同屏副本，
 * 在客户端选出来的结果和服务端会对不上（症状：本地看着打到了，实际没掉血）。</p>
 */
public final class EntityChooseUtil {

    private EntityChooseUtil() {
    }

    // ══════════════════════════════════════════════════════════════
    // ① 任意 AABB
    // ══════════════════════════════════════════════════════════════

    /**
     * 最底层的一档：在任意 AABB 里找指定类型的实体。
     *
     * @param except 要排除的实体（通常是施法者自己），可为 {@code null}
     * @param type   想要的类型
     * @param filter 额外条件，可为 {@code null}
     */
    @NotNull
    public static <T extends Entity> List<T> inBox(@NotNull Level level, @Nullable Entity except,
                                                   @NotNull AABB box, @NotNull Class<T> type,
                                                   @Nullable Predicate<? super T> filter) {
        return level.getEntitiesOfClass(type, box,
                candidate -> isEligible(except, candidate)
                        && (filter == null || filter.test(candidate)));
    }

    /** 在任意 AABB 里找活着的生物（最常见的用法）。 */
    @NotNull
    public static List<LivingEntity> inBox(@NotNull Level level, @Nullable Entity except,
                                           @NotNull AABB box) {
        return inBox(level, except, box, LivingEntity.class, null);
    }

    /**
     * 以某个实体为中心、在给定 AABB 里找生物，并把这个实体自己排除掉。
     * <p>保留这个方法名是为了兼容原来的调用点，等价于
     * {@code inBox(entity.level(), entity, box)}。</p>
     */
    @NotNull
    public static List<LivingEntity> getEntitiesAround(@NotNull Entity entity, @NotNull AABB box) {
        return inBox(entity.level(), entity, box);
    }

    // ══════════════════════════════════════════════════════════════
    // ② 指定坐标范围
    // ══════════════════════════════════════════════════════════════

    /**
     * 用<b>两个角点</b>指定一个长方体范围。
     *
     * <p>两个角点的大小顺序无所谓，内部会自动取 min/max —— 这样从命令参数
     * 或者从两个方块坐标直接拼出来的调用点不用先自己排一遍。</p>
     *
     * @param except 要排除的实体，可为 {@code null}
     */
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

    // ══════════════════════════════════════════════════════════════
    // ③ 实体周围方形
    // ══════════════════════════════════════════════════════════════

    /**
     * 实体周围<b>方形</b>区域（把它的碰撞箱朝六个方向各撑开 {@code radius}）。
     *
     * <p>最快的一档：只查一次盒子，不做额外的距离判定 —— 所以角落里的目标离中心
     * 其实有 {@code radius × √3}，这是方形的定义，不是 bug。要圆的用
     * {@link #sphereAround}。</p>
     */
    @NotNull
    public static List<LivingEntity> squareAround(@NotNull Entity center, double radius) {
        return inBox(center.level(), center, center.getBoundingBox().inflate(Math.max(0.0d, radius)));
    }

    // ══════════════════════════════════════════════════════════════
    // ④ 圆形（球）
    // ══════════════════════════════════════════════════════════════

    /**
     * 以实体为中心、半径 {@code radius} 的<b>球</b>内的生物（不含它自己）。
     *
     * <p>先用外接正方体查一遍，再按"碰撞箱到中心的最短距离"筛圆 ——
     * 直接遍历全世界太贵，只查盒子又会把四个角落算进来，两步是标准做法。</p>
     */
    @NotNull
    public static List<LivingEntity> sphereAround(@NotNull Entity center, double radius) {
        return sphereAt(center.level(), center, center.getBoundingBox().getCenter(), radius);
    }

    /**
     * 以任意坐标为中心的球。
     *
     * @param except 要排除的实体（通常是触发者），可为 {@code null}
     */
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

    // ══════════════════════════════════════════════════════════════
    // ⑤ 圆柱
    // ══════════════════════════════════════════════════════════════

    /**
     * 以实体<b>脚底</b>为底面中心、向上高 {@code height}、半径 {@code radius} 的圆柱内的生物。
     *
     * <p>半径只算<b>水平距离</b>（XZ 平面），高度是硬的上下边界 ——
     * 所以站在柱子外沿、但高度在范围内的目标不会被算进来
     * （和球不同，球是按三维直线距离算的）。</p>
     *
     * @param height 向上延伸的高度；传负数会向下延伸（内部会自己排 min/max）
     */
    @NotNull
    public static List<LivingEntity> cylinderAround(@NotNull Entity center, double radius,
                                                    double height) {
        return cylinderAt(center.level(), center, center.position(), radius, height);
    }

    /**
     * 以任意坐标为底面中心的圆柱。
     *
     * @param base   底面中心（圆柱从这里沿 Y 轴延伸）
     * @param except 要排除的实体，可为 {@code null}
     */
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

    // ══════════════════════════════════════════════════════════════
    // ⑥ 前方扇形
    // ══════════════════════════════════════════════════════════════

    /**
     * 实体<b>前方</b>的扇形（锥形）区域内的生物，朝向取它的视线。
     *
     * @param radius       扇形半径（格）
     * @param angleDegrees <b>总张角</b>（度）—— 传 90 就是左右各 45°。传 360 等于球
     */
    @NotNull
    public static List<LivingEntity> sectorInFront(@NotNull Entity source, double radius,
                                                   double angleDegrees) {
        return sectorInFront(source, radius, angleDegrees, false);
    }

    /**
     * 同上，可选"只比水平方向"。
     *
     * @param horizontalOnly {@code true} = 忽略俯仰，只看水平方向上的夹角
     *                       （地面扇形技能该用这个：不然站在斜坡上往上看，扇形会漏掉脚下的目标）
     */
    @NotNull
    public static List<LivingEntity> sectorInFront(@NotNull Entity source, double radius,
                                                   double angleDegrees, boolean horizontalOnly) {
        return sectorFrom(source.level(), source, source.getEyePosition(),
                source.getLookAngle(), radius, angleDegrees, horizontalOnly);
    }

    /**
     * 最通用的一档：任意起点 + 任意朝向的扇形。
     *
     * @param except         要排除的实体（通常是施法者自己），可为 {@code null}
     * @param origin         扇形的顶点
     * @param direction      扇形指向（不需要先归一化，内部会处理；零向量视为"没有方向"，返回空表）
     * @param radius         半径（格）
     * @param angleDegrees   总张角（度）
     * @param horizontalOnly 是否忽略俯仰
     */
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

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    /** 最基本的两个条件：活着（非旁观），且不是要排除的那个。 */
    private static boolean isEligible(@Nullable Entity except, @NotNull Entity candidate) {
        if (candidate.isSpectator() || !candidate.isAlive()) {
            return false;
        }
        // 按 UUID 比：从 UUID 反查出来的实体实例和列表里那个不保证是同一个对象
        return except == null || !except.getUUID().equals(candidate.getUUID());
    }

    /**
     * 目标是否落在扇形张角内。
     *
     * <p>方向取"顶点 → 目标碰撞箱最近点"，所以贴脸/被箱子罩住时向量长度是 0，
     * 那种情况直接算在扇形内（都在你身上了，谈不上"背后"）。</p>
     */
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
