package org.bytechen.rottenruins.network.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;

/**
 * 失心粒子的 S2C 包：方向是"攻击者 → 受击者"，只有服务端同时握有两者位置，
 * 客户端各自算会因插值误差朝不同方向炸开；载荷一次性，走 {@code UniversalPacket}。
 */
public final class HeartLosePacket {

    /** action 标识，客户端按这个字符串注册处理函数。 */
    public static final String ACTION = "heart_lose_burst";

    // NBT 键名（服务端写 / 客户端读，必须一一对应）
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_VX = "vx";
    private static final String KEY_VY = "vy";
    private static final String KEY_VZ = "vz";
    private static final String KEY_COUNT = "count";
    private static final String KEY_SPREAD = "spread";
    /** 受击者碰撞箱的半宽 / 半高 / 半深 —— 客户端按这个范围随机撒点，而不是全挤在一个位置。 */
    private static final String KEY_HW = "hw";
    private static final String KEY_HH = "hh";
    private static final String KEY_HD = "hd";

    /** 贴脸（距离 0）时的模长，单位 格/tick。 */
    public static final double HEART_LOSE_SPEED_AT_MELEE = 0.80;
    public static final double HEART_LOSE_SPEED_ZERO_DISTANCE = 3.0;
    /** 超过该距离后保留的最小模长 —— 留一点惯性，不要变成静止粒子。 */
    public static final double HEART_LOSE_SPEED_MIN = 0.12;

    private HeartLosePacket() {}

    public static double speedForDistance(double distance) {
        if (distance <= 0.0) return HEART_LOSE_SPEED_AT_MELEE;
        double t = distance / HEART_LOSE_SPEED_ZERO_DISTANCE;
        if (t > 1.0) t = 1.0;
        return HEART_LOSE_SPEED_AT_MELEE + (HEART_LOSE_SPEED_MIN - HEART_LOSE_SPEED_AT_MELEE) * t;
    }

    /** 服务端入口：在 {@code target} 处生成一簇失心粒子。只在服务端调用，客户端不自行生成。 */
    public static void sendBurst(Entity attacker, Entity target, int count) {
        if (attacker == null || target == null || count <= 0) return;
        if (!(target.level() instanceof ServerLevel serverLevel)) return;

        Vec3 from = attacker.position();
        Vec3 to = target.position();

        // 方向：攻击者 → 受击者。两者重合时退化为"向上"，避免除零得到 NaN 速度
        Vec3 dir = to.subtract(from);
        double distance = dir.length();
        if (distance < 1.0e-4) {
            dir = new Vec3(0.0, 1.0, 0.0);
        } else {
            dir = dir.scale(1.0 / distance);
        }

        double speed = speedForDistance(distance);
        Vec3 velocity = dir.scale(speed);

        // 碰撞箱：客户端在整个碰撞箱体积内均匀撒点，否则粒子全从一点冒出像"喷泉"
        AABB box = target.getBoundingBox();

        CompoundTag data = new CompoundTag();
        data.putDouble(KEY_X, box.getCenter().x);
        data.putDouble(KEY_Y, box.getCenter().y);
        data.putDouble(KEY_Z, box.getCenter().z);
        data.putDouble(KEY_VX, velocity.x);
        data.putDouble(KEY_VY, velocity.y);
        data.putDouble(KEY_VZ, velocity.z);
        data.putInt(KEY_COUNT, count);
        data.putDouble(KEY_SPREAD, 0.04);
        data.putDouble(KEY_HW, (box.maxX - box.minX) * 0.5);
        data.putDouble(KEY_HH, (box.maxY - box.minY) * 0.5);
        data.putDouble(KEY_HD, (box.maxZ - box.minZ) * 0.5);

        NetworkHelper.sendToClient(target, new UniversalPacket(ACTION, UniversalPacketData.ofNbt(data)));
    }

    public static double readX(CompoundTag t) { return t.getDouble(KEY_X); }
    public static double readY(CompoundTag t) { return t.getDouble(KEY_Y); }
    public static double readZ(CompoundTag t) { return t.getDouble(KEY_Z); }
    public static double readVx(CompoundTag t) { return t.getDouble(KEY_VX); }
    public static double readVy(CompoundTag t) { return t.getDouble(KEY_VY); }
    public static double readVz(CompoundTag t) { return t.getDouble(KEY_VZ); }
    public static int readCount(CompoundTag t) { return Math.max(1, t.getInt(KEY_COUNT)); }
    public static double readSpread(CompoundTag t) { return t.getDouble(KEY_SPREAD); }
    public static double readHalfWidth(CompoundTag t) { return t.getDouble(KEY_HW); }
    public static double readHalfHeight(CompoundTag t) { return t.getDouble(KEY_HH); }
    public static double readHalfDepth(CompoundTag t) { return t.getDouble(KEY_HD); }

    /** 兼容性占位：粒子类型由客户端直接用 {@code RegisterParticles.HEART_LOSE}，包里不带类型。 */
    public static net.minecraft.core.particles.ParticleOptions particle() {
        return RegisterParticles.HEART_LOSE.get();
    }
}
