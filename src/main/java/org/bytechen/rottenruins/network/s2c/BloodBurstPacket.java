package org.bytechen.rottenruins.network.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;

/**
 * 受击血花（{@link RegisterParticles#BLOOD}）的 S2C 包 —— 在目标身上朝"攻击源方向"喷一串血点。
 * 只在 {@code AbstractRottenEntity} 的两个 protected 钩子里触发（自己被打 / 击杀并成功感染），
 * 所以原版与其它模组的生物被打不会出血 —— 结构上的保证，不依赖事件过滤；与 heart_lose 同一套
 * "速度释放"模型（方向由服务端算、模长按距离、数量按实际伤害），走通用包以免动包序号。
 */
public final class BloodBurstPacket {

    /** action 标识，客户端按这个字符串注册处理函数。 */
    public static final String ACTION = "blood_burst";

    // NBT 键名（服务端写 / 客户端读，必须一一对应）
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_VX = "vx";
    private static final String KEY_VY = "vy";
    private static final String KEY_VZ = "vz";
    private static final String KEY_COUNT = "count";
    private static final String KEY_SPREAD = "spread";
    private static final String KEY_HW = "hw";
    private static final String KEY_HH = "hh";
    private static final String KEY_HD = "hd";

    // 数量：1.5 颗/点伤害（普通一刀 3~6 点 → 4~9 颗），封顶 24 —— 再多只是屏幕上一团红，还白耗带宽。
    /** 无论伤害多低，至少喷这么多颗（挨打就该见血）。 */
    public static final int MIN_COUNT = 4;
    public static final int MAX_COUNT = 24;
    /** 每 1 点实际伤害换算出的血滴数。 */
    public static final double COUNT_PER_DAMAGE = 1.5D;

    // 速度模长：与 heart_lose 同形状的线性衰减，数值按"血滴要溅得够远"重新定过（贴脸 0.85 格/tick，4 格开外仍保留 0.30）。
    /** 贴脸（距离 0）时的速度模长。 */
    public static final double SPEED_AT_MELEE = 0.85D;
    public static final double SPEED_ZERO_DISTANCE = 4.0D;
    /** 距离超过 SPEED_ZERO_DISTANCE 后保留的最小模长 —— 留一点惯性，不要变成静止粒子。 */
    public static final double SPEED_MIN = 0.30D;

    public static final double POSITION_SPREAD = 0.04D;

    /** 客户端锥形散布系数：每颗粒子叠 ±speed × CONE_FACTOR / 2 的随机分量，0.6 ≈ ±17°；太小像激光，太大看不出主方向。 */
    public static final double CONE_FACTOR = 0.6D;

    /** 感染击杀路径按坐标广播的半径（格）。与实体追踪的发包范围同一量级即可。 */
    public static final double NEARBY_RADIUS = 32.0D;

    private BloodBurstPacket() {}

    /** 伤害 → 血滴数量：ceil(伤害 × COUNT_PER_DAMAGE)，再夹在 MIN_COUNT ~ MAX_COUNT 之间；damage ≤ 0 返回 0。 */
    public static int countForDamage(float damage) {
        if (damage <= 0.0F) return 0;
        int count = (int) Math.ceil(damage * COUNT_PER_DAMAGE);
        if (count < MIN_COUNT) count = MIN_COUNT;
        if (count > MAX_COUNT) count = MAX_COUNT;
        return count;
    }

    /** 距离 → 速度模长：0 距离取 SPEED_AT_MELEE，≥ SPEED_ZERO_DISTANCE 后落到 SPEED_MIN。 */
    public static double speedForDistance(double distance) {
        if (distance <= 0.0D) return SPEED_AT_MELEE;
        double t = distance / SPEED_ZERO_DISTANCE;
        if (t > 1.0D) t = 1.0D;
        return SPEED_AT_MELEE + (SPEED_MIN - SPEED_AT_MELEE) * t;
    }

    /** 服务端入口 ①：按实体追踪关系发包（目标还活着、还被追踪），用于王庭生物自己被打，粒子出在被击者身上。 */
    public static void sendBurst(LivingEntity target, Entity attacker, float damage) {
        if (target == null || attacker == null) return;
        if (!(target.level() instanceof ServerLevel)) return;

        CompoundTag data = buildData(target.getBoundingBox(), attacker.position(), target.position(), damage);
        if (data == null) return;

        NetworkHelper.sendToClient(target, new UniversalPacket(ACTION, UniversalPacketData.ofNbt(data)));
    }

    /** 服务端入口 ②：按坐标广播给附近玩家 —— 感染转化会 discard 受害者、原追踪关系消失，所以用转化前快照的坐标与碰撞箱。 */
    public static void sendBurstAt(ServerLevel level, Vec3 center, AABB box, Entity attacker, float damage) {
        if (level == null || center == null || box == null || attacker == null) return;

        CompoundTag data = buildData(box, attacker.position(), center, damage);
        if (data == null) return;

        NetworkHelper.sendToNear(level, center.x, center.y, center.z, NEARBY_RADIUS,
                new UniversalPacket(ACTION, UniversalPacketData.ofNbt(data)));
    }

    /** 组包：方向 = from → to，模长按两者距离，数量按伤害，撒点范围取 box；count 为 0（伤害 ≤ 0）时返回 null。 */
    private static CompoundTag buildData(AABB box, Vec3 from, Vec3 to, float damage) {
        int count = countForDamage(damage);
        if (count <= 0) return null;

        // 方向：攻击源 → 目标。两者重合时退化为"向上"，避免除零得到 NaN 速度
        Vec3 dir = to.subtract(from);
        double distance = dir.length();
        if (distance < 1.0e-4D) {
            dir = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            dir = dir.scale(1.0D / distance);
        }
        Vec3 velocity = dir.scale(speedForDistance(distance));
        Vec3 center = box.getCenter();

        CompoundTag data = new CompoundTag();
        data.putDouble(KEY_X, center.x);
        data.putDouble(KEY_Y, center.y);
        data.putDouble(KEY_Z, center.z);
        data.putDouble(KEY_VX, velocity.x);
        data.putDouble(KEY_VY, velocity.y);
        data.putDouble(KEY_VZ, velocity.z);
        data.putInt(KEY_COUNT, count);
        data.putDouble(KEY_SPREAD, POSITION_SPREAD);
        data.putDouble(KEY_HW, (box.maxX - box.minX) * 0.5D);
        data.putDouble(KEY_HH, (box.maxY - box.minY) * 0.5D);
        data.putDouble(KEY_HD, (box.maxZ - box.minZ) * 0.5D);
        return data;
    }

    public static double readX(CompoundTag t) { return t.getDouble(KEY_X); }
    public static double readY(CompoundTag t) { return t.getDouble(KEY_Y); }
    public static double readZ(CompoundTag t) { return t.getDouble(KEY_Z); }
    public static double readVx(CompoundTag t) { return t.getDouble(KEY_VX); }
    public static double readVy(CompoundTag t) { return t.getDouble(KEY_VY); }
    public static double readVz(CompoundTag t) { return t.getDouble(KEY_VZ); }
    public static int readCount(CompoundTag t) {
        int count = t.getInt(KEY_COUNT);
        if (count < 1) count = 1;
        if (count > MAX_COUNT) count = MAX_COUNT;
        return count;
    }
    public static double readSpread(CompoundTag t) { return t.getDouble(KEY_SPREAD); }
    public static double readHalfWidth(CompoundTag t) { return t.getDouble(KEY_HW); }
    public static double readHalfHeight(CompoundTag t) { return t.getDouble(KEY_HH); }
    public static double readHalfDepth(CompoundTag t) { return t.getDouble(KEY_HD); }
}
