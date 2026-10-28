package org.bytechen.hall.network.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.network.all.UniversalPacket;
import org.bytechen.hall.network.all.tools.UniversalPacketData;
import org.bytechen.hall.overworld.registry.RegisterParticles;

/**
 * 受击血花（{@link RegisterParticles#BLOOD}）的 S2C 包 —— 在目标身上朝"攻击源方向"喷一串血点。
 *
 * <h3>只在两个地方触发（由 {@code AbstractHallEntity} 基类直接调用，不走事件）</h3>
 * <ol>
 *   <li><b>王庭生物自己被打</b>：{@code AbstractHallEntity#hurt} → {@code onHurtBlood(...)}
 *       → {@link #sendBurst}，粒子出现在<b>被打的王庭生物</b>身上；</li>
 *   <li><b>王庭生物击杀并成功感染</b>：{@code AbstractHallEntity#onKilledEntity} →
 *       {@code onInfectionBlood(...)} → {@link #sendBurstAt}，粒子出现在<b>被击杀者原位</b>。</li>
 * </ol>
 * <p>因为入口都写在 {@code AbstractHallEntity} 里，原版 / 其它模组的生物被打时根本不会调用到这里 ——
 * "其他生物被攻击不会出血"是<b>结构上</b>保证的，不依赖事件过滤。子类想改/禁用直接覆写
 * 那两个 {@code protected} 钩子即可。</p>
 *
 * <h3>为什么感染击杀需要"按坐标广播"</h3>
 * <p>感染转化成功时 infcore 会 {@code discard()} 受害者并生成新的感染实体，原来的追踪关系
 * 随之消失；因此 {@code onKilledEntity} 在转化<b>前</b>快照好坐标与碰撞箱，转换成功后用
 * {@link #sendBurstAt} 按坐标广播给附近玩家（而不是按实体追踪关系发包）。</p>
 *
 * <h3>和 heart_lose 是同一套"速度释放"模型</h3>
 * <ul>
 *   <li><b>方向</b>：服务端算好 {@code 攻击源 → 目标} 的单位向量。只有服务端同时握着双方实时位置；
 *       客户端各自算会因位置插值不同而方向不一。</li>
 *   <li><b>模长</b>：{@link #speedForDistance(double)} —— 距离越近喷得越快，超过
 *       {@link #SPEED_ZERO_DISTANCE} 格后落到最小值。近战贴脸是一股急喷，远处弓箭命中只剩一点惯性。</li>
 * </ul>
 * <p>区别只在粒子本身：heart_lose 是心形碎片（大、慢、重力小），blood 是小血滴
 * （小、快、重力大，见 {@code BloodParticle}），所以速度模长按血滴重新定过 ——
 * 贴脸更快、远距离保留的下限也更高；另外在客户端加了一个锥形散布
 * （{@link #CONE_FACTOR}），让血是"一片"而不是"一条线"。</p>
 *
 * <h3>数量由"实际吃到的伤害"决定</h3>
 * <p>{@link #countForDamage(float)}：{@code ceil(伤害 × COUNT_PER_DAMAGE)}，再夹在
 * {@link #MIN_COUNT} ~ {@link #MAX_COUNT} 之间。受击路径传的是血条实际变化量
 * + 吸收盾消耗量（见 {@code AbstractHallEntity#hurt}），不是护甲减免前的原始伤害 ——
 * 穿铁甲和裸奔不应该喷一样多。感染击杀路径拿不到"最后一击"的伤害，用生命上限的一半代理。</p>
 *
 * <h3>为什么仍然走通用包</h3>
 * <p>和 {@link HeartLosePacket} 一样，走现成的
 * {@code UniversalPacket} + {@code PacketActionRegistry}（action 字符串 + NBT 载荷）：
 * 只有一个一次性载荷、没有状态机，不值得为它动 {@code NetworkHelper.register()}
 * 里的包序号（那是全项目唯一必须客户端服务端严格同序的地方）。</p>
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

    // ── 数量：伤害 → 血滴数 ──
    //
    // 需求是"伤害决定数量，并且限制最大最小值"。
    // 1.5 颗/点伤害：普通一刀（3~6 点）喷 4~9 颗，重击（10 点以上）到十几颗，
    // 封顶 24 —— 再往上加只是屏幕上一团红，既看不出更多信息也白耗带宽。
    /** 无论伤害多低，至少喷这么多颗（挨打就该见血）。 */
    public static final int MIN_COUNT = 4;
    /** 上限：再重的伤害也不会超过这个数。 */
    public static final int MAX_COUNT = 24;
    /** 每 1 点实际伤害换算出的血滴数。 */
    public static final double COUNT_PER_DAMAGE = 1.5D;

    // ── 速度模长（= 距离系数）的取值区间 ──
    //
    // 与 heart_lose 同一套线性衰减形状，数值按"血滴要溅得够远"重新定过：
    // 贴脸约 0.85 格/tick，4 格开外仍保留 0.30。旧版 0.45 / 3 格 / 0.10 实测
    // 只在脚边散开（配合 BloodParticle 更小的重力与阻力后才够远）。
    /** 贴脸（距离 0）时的速度模长。 */
    public static final double SPEED_AT_MELEE = 0.85D;
    /** 衰减到最小值处的距离（格）。 */
    public static final double SPEED_ZERO_DISTANCE = 4.0D;
    /** 距离超过上面那个值时保留的最小模长 —— 留一点惯性，不要变成静止粒子。 */
    public static final double SPEED_MIN = 0.30D;

    /** 生成位置的额外抖动（在目标碰撞箱基础上再放宽一点，避免全从中心一条线冒出）。 */
    public static final double POSITION_SPREAD = 0.04D;

    /**
     * 客户端锥形散布系数：每颗粒子在主速度上叠加 {@code ±speed × CONE_FACTOR / 2} 的随机分量。
     *
     * <p>0.6 → 大约 ±17° 的散射角。太小会像激光，太大主方向（"从哪边打来的"）就看不出来了，
     * 这个值是在"看得出方向"和"像一片血雾"之间取的折中。</p>
     */
    public static final double CONE_FACTOR = 0.6D;

    /** 感染击杀路径按坐标广播的半径（格）。与实体追踪的发包范围同一量级即可。 */
    public static final double NEARBY_RADIUS = 32.0D;

    private BloodBurstPacket() {}

    /**
     * 伤害 → 血滴数量（带最小 / 最大值夹取）。
     *
     * @param damage 实际受到的伤害
     */
    public static int countForDamage(float damage) {
        if (damage <= 0.0F) return 0;
        int count = (int) Math.ceil(damage * COUNT_PER_DAMAGE);
        if (count < MIN_COUNT) count = MIN_COUNT;
        if (count > MAX_COUNT) count = MAX_COUNT;
        return count;
    }

    /**
     * 距离 → 速度模长（与 {@link HeartLosePacket#speedForDistance(double)} 同形状的线性衰减）。
     *
     * <p>距离 0 时 {@link #SPEED_AT_MELEE}，距离 ≥ {@link #SPEED_ZERO_DISTANCE} 时
     * 落到 {@link #SPEED_MIN}。距离越近喷得越猛。</p>
     */
    public static double speedForDistance(double distance) {
        if (distance <= 0.0D) return SPEED_AT_MELEE;
        double t = distance / SPEED_ZERO_DISTANCE;
        if (t > 1.0D) t = 1.0D;
        return SPEED_AT_MELEE + (SPEED_MIN - SPEED_AT_MELEE) * t;
    }

    /**
     * 服务端入口 ①：受击者在 {@code attacker} 的方向上喷一簇血（按实体追踪关系发包）。
     *
     * <p>用于"王庭生物自己被打"：目标还活着、还在被客户端追踪，所以直接按
     * {@code TRACKING_ENTITY} 发给能看到它的玩家。</p>
     *
     * @param target   被打的王庭生物（喷血位置）
     * @param attacker 攻击源（方向起点；有射击者时传射击者，没有则传弹射物本体）
     * @param damage   实际受到的伤害，决定数量
     */
    public static void sendBurst(LivingEntity target, Entity attacker, float damage) {
        if (target == null || attacker == null) return;
        if (!(target.level() instanceof ServerLevel)) return;

        CompoundTag data = buildData(target.getBoundingBox(), attacker.position(), target.position(), damage);
        if (data == null) return;

        NetworkHelper.sendToClient(target, new UniversalPacket(ACTION, UniversalPacketData.ofNbt(data)));
    }

    /**
     * 服务端入口 ②：在指定坐标喷一簇血（按<b>坐标</b>广播给附近玩家，不依赖实体追踪）。
     *
     * <p>用于"王庭生物击杀并成功感染"：感染转化会把受害者 {@code discard()} 并替换成新实体，
     * 原实体的追踪关系已经没了；这里用转化前快照的坐标 + 碰撞箱，向附近玩家广播。</p>
     *
     * @param level    服务端世界
     * @param center   喷血中心（受害者转化前的碰撞箱中心）
     * @param box      受害者转化前的碰撞箱（决定撒点范围）
     * @param attacker 攻击源（方向起点，也就是完成击杀的王庭生物）
     * @param damage   数量换算用的伤害（击杀时拿不到最后一击伤害，由调用方给代理值）
     */
    public static void sendBurstAt(ServerLevel level, Vec3 center, AABB box, Entity attacker, float damage) {
        if (level == null || center == null || box == null || attacker == null) return;

        CompoundTag data = buildData(box, attacker.position(), center, damage);
        if (data == null) return;

        NetworkHelper.sendToNear(level, center.x, center.y, center.z, NEARBY_RADIUS,
                new UniversalPacket(ACTION, UniversalPacketData.ofNbt(data)));
    }

    /**
     * 组包：方向 = {@code from → to}，模长按两者距离，数量按伤害，撒点范围取 {@code box}。
     *
     * @return 数量为 0（伤害 ≤ 0）时返回 {@code null}
     */
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

    // ── 客户端读取 ──

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
