package org.bytechen.hall.network.s2c;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.network.all.UniversalPacket;
import org.bytechen.hall.network.all.tools.UniversalPacketData;
import org.bytechen.hall.overworld.registry.RegisterParticles;
import org.bytechen.hall.utils.entity.EntityParticleUtils;

/**
 * 爆体（body explosion）肉屑粒子的 S2C 包 —— 只告诉客户端"<b>哪个实体炸了</b>"，
 * 位置 / 数量 / 速度全部由客户端按同一份配置自己算。
 *
 * <h3>为什么必须由服务端决定"什么时候炸"</h3>
 * <p>爆体（{@code AbstractHallEntity} 的 fake death → {@code onBurstEnd()}）是<b>服务端</b>
 * 状态机推进出来的事件：只有服务端知道 fake death 计时器何时归零、实体何时被
 * {@code remove(RemovalReason.KILLED)}。如果让每个客户端各自监听"实体消失了"再放粒子，
 * 就会遇到两个问题：</p>
 * <ul>
 *   <li>实体移除包到达各客户端的时机受网络抖动影响，同一个爆体在不同玩家屏幕上
 *       会错开几十毫秒甚至不同 tick；</li>
 *   <li>客户端分不清"爆体结束被移除"和"走远后不再同步 / 区块卸载被移除"，
 *       会凭空多放一串肉屑。</li>
 * </ul>
 * <p>所以这里完全照搬 corruption（原模组）的做法：服务端在 {@code onFakeDeathEnd} 里
 * 发一个只带实体 id 的小包（{@code CParticleSpawnPacket}），收到包的客户端再各自生成。</p>
 *
 * <h3>为什么包体里不带每颗粒子的数据</h3>
 * <p>肉屑的生成位置是"<b>实体碰撞箱内均匀撒点</b>"、速度是"随机上抛 + 小水平扰动"，
 * 这些随机性<b>不需要</b>在客户端之间保持逐颗粒子一致 —— 玩家看到的是同一时刻、
 * 同一位置炸开的一团肉屑，具体颗粒的散落方向本来就可以各不相同（corruption 也是这么做的：
 * 服务端只发 entityId，客户端用 {@code getDeathParticleConfig()} 现算）。</p>
 * <p>因此线上只有 4 个字节（一个 int），而不是 30 颗粒子的坐标与速度：</p>
 * <pre>
 *   服务端：onBurstEnd() → MEAT_BURST(entityId)
 *   客户端：level.getEntity(entityId) → EntityParticleUtils.spawnParticles(entity, burstConfig())
 * </pre>
 * <p>碰撞箱由客户端从本地实体现读（{@code EntityParticleUtils} 内部读
 * {@code entity.getBoundingBox()}），所以实体的位置/体型在客户端有插值误差也没关系 ——
 * 粒子一定从<b>玩家屏幕上那个实体</b>身上炸开，观感反而更对。</p>
 *
 * <h3>为什么走通用包而不是新注册一个包</h3>
 * <p>项目已有 {@code UniversalPacket} + {@code PacketActionRegistry}（action 字符串 +
 * 通用数据）这套分发机制，本包只有一个一次性 int 载荷、没有自己的状态机，
 * 走通用机制就够了；动 {@code NetworkHelper.register()} 里的包序号反而要求
 * 客户端服务端严格同序，风险更大。风格与 {@link HeartLosePacket} 一致。</p>
 *
 * <p>粒子类型取 {@code RegisterParticles.MEAT}（注册名 {@code hall:meat}，单帧静态贴图，
 * 客户端 {@code MeatParticle} 负责弹跳 / 随寿命缩小）。</p>
 */
public final class MeatBurstPacket {

    /** action 标识，客户端按这个字符串注册处理函数。 */
    public static final String ACTION = "meat_burst";

    // ── 爆体肉屑的配置数值 ──
    //
    // 与 corruption BaseOriginalEntity#getDeathParticleConfig 完全一致：
    //   count 30、rangeScale 1.0（原碰撞箱）、水平速度 ±0.15（= (rand-0.5)*0.3）、
    //   竖直速度 0.3 + rand*0.3（即 0.3 ~ 0.6，向上炸开）。
    // 这些数值两端共用（服务端不发、客户端现算），改这里就等于同时改了两端。
    /** 每次爆体生成的肉屑数量。 */
    public static final int BURST_COUNT = 30;
    /** 碰撞箱缩放（1.0 = 按实体原始碰撞箱撒点）。 */
    public static final float BURST_RANGE_SCALE = 1.0f;
    /** 水平速度幅度：实际 x/z 速度 = (rand - 0.5) * 0.3 → ±0.15。 */
    public static final double BURST_HORIZONTAL_SPREAD = 0.3;
    /** 竖直速度下限（向上抛）。 */
    public static final double BURST_UPWARD_MIN = 0.3;
    /** 竖直速度随机幅度：实际 y 速度 = 0.3 + rand * 0.3 → 0.3 ~ 0.6。 */
    public static final double BURST_UPWARD_SPREAD = 0.3;

    private MeatBurstPacket() {}

    /**
     * 服务端入口：把"实体 {@code entity} 爆体了"广播给所有正在追踪它的客户端。
     *
     * <p>只在服务端调用（由 {@code AbstractHallEntity#onBurstEnd()} 发起）。
     * 注意必须<b>在实体被移除之前</b>调用 —— 包先入队、实体移除包后入队，
     * 客户端处理本包时还能用 id 解析到实体。</p>
     *
     * @param entity 爆体的实体（其 id 就是包体内容；碰撞箱让客户端自己读）
     */
    public static void sendBurst(Entity entity) {
        if (entity == null) return;
        // 客户端侧不发送：客户端的实体只是一个副本，由服务端统一触发
        if (!(entity.level() instanceof ServerLevel)) return;
        NetworkHelper.sendToClient(entity, new UniversalPacket(ACTION, UniversalPacketData.ofInt(entity.getId())));
    }

    /**
     * 客户端读取 helper：从通用包数据里取出实体 id。
     *
     * <p>只在载荷类型不对时返回 0（不可能命中实体 id 0，{@code Level#getEntity(0)} 返回 null），
     * 这样处理器里一个非正数判断就能挡掉所有脏数据，不必到处做 {@code instanceof}。</p>
     */
    public static int readEntityId(UniversalPacketData<?> data) {
        if (data == null) return 0;
        return data.getValue() instanceof Integer entityId ? entityId : 0;
    }

    /**
     * 爆体肉屑的共享粒子配置（两端同一份，保证"服务端触发、客户端表现"不会跑偏）。
     *
     * <p>速度语义与 corruption {@code BaseOriginalEntity#getDeathParticleConfig()} 一致：
     * 每颗粒子独立随机，水平 ±0.15、竖直 0.3~0.6，<b>整体向上炸开</b>而不是全向球形
     * 扩散 —— 看起来像"尸体被撑爆、肉块往上溅"。</p>
     *
     * <p>返回的配置<b>不可变</b>（{@link EntityParticleUtils.ParticleConfig} 是 final 字段），
     * 可以每次现建也可以缓存；这里选现建，避免静态持有 {@code RegistryObject#get()} 的
     * 结果（粒子类型注册表在两侧加载顺序不同，缓存到静态字段容易踩到 null）。</p>
     */
    public static EntityParticleUtils.ParticleConfig burstConfig() {
        return EntityParticleUtils.builder()
                .particle(RegisterParticles.MEAT.get())
                .count(BURST_COUNT)
                .rangeScale(BURST_RANGE_SCALE)
                .velocityCustom((rand, e) -> new Vec3(
                        (rand.nextDouble() - 0.5) * BURST_HORIZONTAL_SPREAD,
                        BURST_UPWARD_MIN + rand.nextDouble() * BURST_UPWARD_SPREAD,
                        (rand.nextDouble() - 0.5) * BURST_HORIZONTAL_SPREAD
                ))
                .build();
    }
}
