package org.bytechen.hall.network.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.network.all.UniversalPacket;
import org.bytechen.hall.network.all.tools.UniversalPacketData;
import org.bytechen.hall.overworld.registry.RegisterParticles;

/**
 * 失心粒子的 S2C 包 —— 把"往哪飞、飞多快"交给服务端决定。
 *
 * <h3>为什么方向必须走网络包</h3>
 * <p>粒子的方向要表达"<b>从攻击者飞向受击者</b>"，这个方向只有服务端同时握着
 * 攻击者与受击者的实时位置。如果在客户端各自算：</p>
 * <ul>
 *   <li>客户端只知道受击者，不知道谁打的（伤害来源不一定同步到这里）；</li>
 *   <li>就算知道，攻击者与受击者的位置在每个客户端上都有插值误差，
 *       算出来的方向会各不相同 —— 同一个动作在不同玩家屏幕上朝不同方向炸开。</li>
 * </ul>
 * <p>所以由服务端算好一个速度向量发下去，客户端原样采用。</p>
 *
 * <h3>模长规则（按需求）</h3>
 * <pre>
 *   方向 = normalize(受击者位置 - 攻击者位置)      // 攻击者 → 受击者的单位方向
 *   模长 = 攻击者到受击者的距离，距离越近越大        // 见 HEART_LOSE_SPEED_AT_MELEE / _AT_RANGE
 * </pre>
 * <p>实际速度向量 = 方向 × 模长，粒子沿这条路从攻击者那侧飞向受击者。</p>
 *
 * <h3>为什么用通用包而不是新注册一个包</h3>
 * <p>项目里已经有 {@code UniversalPacket} + {@code PacketActionRegistry} 这套
 * "action 字符串 + 通用数据"的分发机制（{@code UniversalPacketData} 支持 NBT）。
 * 这个包只有一个一次性载荷、没有自己的状态机，走通用机制就够了，
 * 也省得动 {@code NetworkHelper.register()} 里的包序号 —— 那东西一改就要
 * 保证客户端服务端严格同序。</p>
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

    // ── 速度模长（= 距离系数）的取值区间 ──
    //
    // 需求是"模长系数为目标到攻击者的距离，距离越近系数越大"。
    // 这里把距离映射成速度模长：贴脸时最大、拉开距离后迅速衰减。
    /** 贴脸（距离 0）时的模长 —— 约 0.8 格/tick。 */
    public static final double HEART_LOSE_SPEED_AT_MELEE = 0.80;
    /** 衰减到最小值处的距离（格）。 */
    public static final double HEART_LOSE_SPEED_ZERO_DISTANCE = 3.0;
    /** 距离超过上面那个值时保留的最小模长 —— 留一点惯性，不要变成静止粒子。 */
    public static final double HEART_LOSE_SPEED_MIN = 0.12;

    private HeartLosePacket() {}

    /**
     * 计算"距离 → 速度模长"。
     *
     * <p>线性衰减：距离 0 时 {@link #HEART_LOSE_SPEED_AT_MELEE}，
     * 距离 ≥ {@link #HEART_LOSE_SPEED_ZERO_DISTANCE} 时落到
     * {@link #HEART_LOSE_SPEED_MIN}。距离越近模长越大。</p>
     */
    public static double speedForDistance(double distance) {
        if (distance <= 0.0) return HEART_LOSE_SPEED_AT_MELEE;
        double t = distance / HEART_LOSE_SPEED_ZERO_DISTANCE;
        if (t > 1.0) t = 1.0;
        return HEART_LOSE_SPEED_AT_MELEE + (HEART_LOSE_SPEED_MIN - HEART_LOSE_SPEED_AT_MELEE) * t;
    }

    /**
     * 服务端入口：在 {@code target} 位置生成一簇失心粒子，方向为
     * {@code attacker → target}，模长按两者距离换算。
     *
     * <p>只在服务端调用；由 {@code sendParticles} 负责广播，客户端不自行生成 ——
     * 否则每个客户端会因为不知道攻击者而各飞各的。</p>
     *
     * @param attacker 攻击者（方向起点）
     * @param target   受击者（生成位置与方向终点）
     * @param count    粒子数量
     */
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

        // 碰撞箱：连同中心一起发下去，客户端在【整个碰撞箱体积内】均匀撒点。
        // 之前只发一个点，所有粒子从同一处冒出来，看起来像"喷泉"而不是"炸开"。
        AABB box = target.getBoundingBox();

        CompoundTag data = new CompoundTag();
        data.putDouble(KEY_X, box.getCenter().x);
        data.putDouble(KEY_Y, box.getCenter().y);
        data.putDouble(KEY_Z, box.getCenter().z);
        data.putDouble(KEY_VX, velocity.x);
        data.putDouble(KEY_VY, velocity.y);
        data.putDouble(KEY_VZ, velocity.z);
        data.putInt(KEY_COUNT, count);
        data.putDouble(KEY_SPREAD, 0.04);                            // 每个粒子再叠一点微抖动
        data.putDouble(KEY_HW, (box.maxX - box.minX) * 0.5);
        data.putDouble(KEY_HH, (box.maxY - box.minY) * 0.5);
        data.putDouble(KEY_HD, (box.maxZ - box.minZ) * 0.5);

        NetworkHelper.sendToClient(target, new UniversalPacket(ACTION, UniversalPacketData.ofNbt(data)));
    }

    // ── 客户端读取 ──

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
