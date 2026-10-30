package org.bytechen.rottenruins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import org.bytechen.rottenruins.network.s2c.BloodBurstPacket;
import org.bytechen.rottenruins.network.s2c.HeartLosePacket;
import org.bytechen.rottenruins.network.s2c.MeatBurstPacket;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;
import org.bytechen.rottenruins.utils.entity.EntityParticleUtils;

import java.util.Random;

/**
 * 客户端 S2C 包处理注册。
 *
 * <h3>为什么走 action 字符串分发</h3>
 * <p>项目已有 {@code UniversalPacket} + {@code PacketActionRegistry} 这套机制，
 * 这里只是把 {@link HeartLosePacket#ACTION} 对应的处理函数挂上。
 * 包本身在 {@code NetworkHelper.register()} 里已经注册过序号，所以
 * <b>新增这个功能不需要动包序号</b> —— 那是唯一一处"客户端服务端必须严格同序"
 * 的地方，能不动就不动。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void init() {
        // Wire up S2C client-side packet handlers here

        // 物品名字的流动彩字：在 tooltip 构建时替换首行文字的颜色（纯文本级，不动渲染层）。
        // 注意挂的是 Forge 总线 —— ItemTooltipEvent 是游戏事件，不是 mod 事件。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                org.bytechen.rottenruins.client.rend.text.FlowingNameTooltipHook::onItemTooltip);

        // tooltip 底板配色：在画底板之前改那四个颜色值（ITooltipStyle 的物品才生效）。
        // 同样是游戏事件、同样挂 Forge 总线 —— 见 TooltipStyleHook 的类注释。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                org.bytechen.rottenruins.client.tooltip.TooltipStyleHook::onTooltipColor);

        // 虚空剑「蹲下左键」的客户端选中：射线选中后发 C2S 包给服务端结算。
        // 挂在同一个 Forge 总线上，所以和上面的 tooltip 钩子一样走 addListener。
        org.bytechen.rottenruins.client.VoidSwordClientHandler.register();

        // 魔法星图：M 键打开界面（按键映射在 ClientModEventHandler 里注册）。
        org.bytechen.rottenruins.client.MagicClientHandler.register();

        PacketActionRegistry.register(HeartLosePacket.ACTION, (data, ctx) -> {
            if (!(data.getValue() instanceof CompoundTag tag)) return;
            spawnHeartLose(tag);
        });

        // 受击血花：服务端按"攻击源 → 受击者"算好方向/距离模长/数量，这里只负责表现。
        // 载荷与 heart_lose 同构（NBT），所以直接读 CompoundTag。
        PacketActionRegistry.register(BloodBurstPacket.ACTION, (data, ctx) -> {
            if (!(data.getValue() instanceof CompoundTag tag)) return;
            spawnBloodBurst(tag);
        });

        // 爆体肉屑：服务端只告诉我们"哪个实体爆了"，这里按共享配置在实体碰撞箱内
        // 现撒粒子。只认 int 载荷（见 MeatBurstPacket#readEntityId，脏数据得到 0）。
        PacketActionRegistry.register(MeatBurstPacket.ACTION, (data, ctx) ->
                spawnMeatBurst(MeatBurstPacket.readEntityId(data)));
    }

    /**
     * 在"刚刚爆体"的实体身上撒一簇肉屑。
     *
     * <p>对应 corruption 的 {@code CParticleSpawnPacket#handle}：客户端用包里的实体 id
     * 在本地 {@code ClientLevel} 里找实体，再交给 {@link EntityParticleUtils} 按
     * {@link MeatBurstPacket#burstConfig()} 生成 —— <b>位置</b>取本地实体的碰撞箱
     * （所以粒子一定从玩家屏幕上那个尸体身上炸开），<b>数量 / 速度</b>用两端共享的
     * 常量，服务端不需要在线上传任何逐粒子数据。</p>
     *
     * <p>找不到实体就直接跳过：服务端是在 {@code remove(...)} 之前发包的，正常情况下
     * 包先于实体移除包到达；但刚进服 / 跨维度时实体可能还没同步下来，此时宁可不放，
     * 也不要凭空在 (0,0,0) 炸一堆肉屑。</p>
     *
     * <p>（{@code EntityParticleUtils#spawnParticles(Entity, ParticleConfig)} 内部已判断
     * {@code level.isClientSide}，因此在客户端调用是正确用法。）</p>
     */
    private static void spawnMeatBurst(int entityId) {
        if (entityId <= 0) return; // 0 是 readEntityId 的"载荷类型不对"哨兵值
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        EntityParticleUtils.spawnParticles(entity, MeatBurstPacket.burstConfig());
    }

    /**
     * 按服务端算好的方向 / 距离模长 / 数量喷一簇 {@code blood} 血滴（受击血花）。
     *
     * <p>与 {@link #spawnHeartLose} 同构，随机性只加在两处，都不改变"从哪边打来的"：</p>
     * <ol>
     *   <li><b>生成位置</b>：在受击者碰撞箱体积内撒点（半宽/半高/半深由包带过来），
     *       血从体表/伤口附近出来，不会都从中心一个点冒；</li>
     *   <li><b>锥形散布</b>：主速度乘 0.85~1.15 的抖动，再叠一个
     *       {@code ±速度 × CONE_FACTOR / 2} 的随机分量 —— 让血散成一片雾，
     *       而不是一条笔直的"水枪线"；分量以主速度大小为基准，所以不会把方向带偏。</li>
     * </ol>
     *
     * <p>主速度为零的包（理论上不会出现，服务端总会给一个非零模长）会退化成
     * "血滴原地落下"，不会崩；数量已由 {@link BloodBurstPacket#readCount} 夹到
     * 1~{@link BloodBurstPacket#MAX_COUNT}，脏包也不会刷爆粒子量。</p>
     */
    private static void spawnBloodBurst(CompoundTag tag) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        double x = BloodBurstPacket.readX(tag);
        double y = BloodBurstPacket.readY(tag);
        double z = BloodBurstPacket.readZ(tag);
        double vx = BloodBurstPacket.readVx(tag);
        double vy = BloodBurstPacket.readVy(tag);
        double vz = BloodBurstPacket.readVz(tag);
        int count = BloodBurstPacket.readCount(tag);
        double spread = BloodBurstPacket.readSpread(tag);
        double hw = BloodBurstPacket.readHalfWidth(tag);
        double hh = BloodBurstPacket.readHalfHeight(tag);
        double hd = BloodBurstPacket.readHalfDepth(tag);

        // 锥形散布的幅度按主速度大小缩放：喷得越快，散开的绝对幅度越大
        double cone = Math.sqrt(vx * vx + vy * vy + vz * vz) * BloodBurstPacket.CONE_FACTOR;
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            double px = x + (random.nextDouble() - 0.5) * 2.0 * (hw + spread);
            double py = y + (random.nextDouble() - 0.5) * 2.0 * (hh + spread);
            double pz = z + (random.nextDouble() - 0.5) * 2.0 * (hd + spread);

            double jitter = 0.85 + random.nextDouble() * 0.3;
            level.addParticle(RegisterParticles.BLOOD.get(),
                    px, py, pz,
                    vx * jitter + (random.nextDouble() - 0.5) * cone,
                    vy * jitter + (random.nextDouble() - 0.5) * cone,
                    vz * jitter + (random.nextDouble() - 0.5) * cone);
        }
    }

    /**
     * 按服务端算好的位置与速度生成失心粒子。
     *
     * <p>方向与模长<b>原样使用</b>：它们是服务端根据"攻击者 → 受击者"算出来的，
     * 客户端再掺自己的随机方向就会把"从攻击者飞向受击者"这个语义冲掉。</p>
     *
     * <p>随机性只体现在两处，都不影响主方向：</p>
     * <ol>
     *   <li><b>生成位置</b>：在受击者的碰撞箱体积内均匀撒点（半宽/半高/半深由包带过来），
     *       而不是全部从中心一个点冒出 —— 否则看起来像喷泉而不是炸开；</li>
     *   <li><b>速度抖动</b>：乘一个 0.9~1.0 的系数，只改快慢不改方向。</li>
     * </ol>
     */
    private static void spawnHeartLose(CompoundTag tag) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        double x = HeartLosePacket.readX(tag);
        double y = HeartLosePacket.readY(tag);
        double z = HeartLosePacket.readZ(tag);
        double vx = HeartLosePacket.readVx(tag);
        double vy = HeartLosePacket.readVy(tag);
        double vz = HeartLosePacket.readVz(tag);
        int count = HeartLosePacket.readCount(tag);
        double spread = HeartLosePacket.readSpread(tag);
        double hw = HeartLosePacket.readHalfWidth(tag);
        double hh = HeartLosePacket.readHalfHeight(tag);
        double hd = HeartLosePacket.readHalfDepth(tag);

        Random random = new Random();
        for (int i = 0; i < count; i++) {
            // 在碰撞箱体积内均匀撒点 + 一点点额外抖动（spread），让边缘不至于太整齐
            double px = x + (random.nextDouble() - 0.5) * 2.0 * (hw + spread);
            double py = y + (random.nextDouble() - 0.5) * 2.0 * (hh + spread);
            double pz = z + (random.nextDouble() - 0.5) * 2.0 * (hd + spread);

            // 速度抖动取下限 0.9，保证"朝着目标方向"的主方向不被抖乱
            double jitter = 0.9 + random.nextDouble() * 0.1;
            level.addParticle(RegisterParticles.HEART_LOSE.get(),
                    px, py, pz,
                    vx * jitter, vy * jitter + 0.06, vz * jitter);
        }
    }
}
