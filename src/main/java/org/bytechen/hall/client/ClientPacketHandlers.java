package org.bytechen.hall.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.hall.network.all.PacketActionRegistry;
import org.bytechen.hall.network.s2c.HeartLosePacket;
import org.bytechen.hall.overworld.registry.RegisterParticles;

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
                org.bytechen.hall.client.rend.text.FlowingNameTooltipHook::onItemTooltip);

        // 虚空剑「蹲下左键」的客户端选中：射线选中后发 C2S 包给服务端结算。
        // 挂在同一个 Forge 总线上，所以和上面的 tooltip 钩子一样走 addListener。
        org.bytechen.hall.client.VoidSwordClientHandler.register();

        PacketActionRegistry.register(HeartLosePacket.ACTION, (data, ctx) -> {
            if (!(data.getValue() instanceof CompoundTag tag)) return;
            spawnHeartLose(tag);
        });
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
