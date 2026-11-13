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

/** 客户端 S2C 包处理注册：走已有 action 字符串分发；包序号已在 {@code NetworkHelper.register()} 注册，新增处理不必动序号。 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void init() {
        // 流动彩字：在 tooltip 构建时替换首行颜色；挂 Forge 总线（ItemTooltipEvent 是游戏事件，不是 mod 事件）。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                org.bytechen.rottenruins.client.rend.text.FlowingNameTooltipHook::onItemTooltip);

        // tooltip 底板配色：画底板前改那四个颜色值（只对 ITooltipStyle 物品生效），同上走 Forge 总线。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                org.bytechen.rottenruins.client.tooltip.TooltipStyleHook::onTooltipColor);

        // 虚空剑「蹲下左键」的客户端选中：射线选中后发 C2S 包给服务端结算。
        org.bytechen.rottenruins.client.VoidSwordClientHandler.register();

        // 魔法星图：M 键打开界面（按键映射在 ClientModEventHandler 里注册）。
        org.bytechen.rottenruins.client.MagicClientHandler.register();

        // 轮盘：服务端手势回执（蓄力/维持/失败提示，更新 WheelClientState）
        org.bytechen.rottenruins.network.s2c.WheelFeedbackPacket.registerClient();

        PacketActionRegistry.register(HeartLosePacket.ACTION, (data, ctx) -> {
            if (!(data.getValue() instanceof CompoundTag tag)) return;
            spawnHeartLose(tag);
        });

        // 受击血花：方向/距离模长/数量由服务端按"攻击源 → 受击者"算好，这里只负责表现；载荷与 heart_lose 同构（NBT）。
        PacketActionRegistry.register(BloodBurstPacket.ACTION, (data, ctx) -> {
            if (!(data.getValue() instanceof CompoundTag tag)) return;
            spawnBloodBurst(tag);
        });

        // 爆体肉屑：服务端只告诉我们"哪个实体爆了"，这里按共享配置在本地碰撞箱内现撒；只认 int 载荷。
        PacketActionRegistry.register(MeatBurstPacket.ACTION, (data, ctx) ->
                spawnMeatBurst(MeatBurstPacket.readEntityId(data)));
    }

    /** 在"刚刚爆体"的实体身上撒肉屑：位置取本地碰撞箱、数量/速度用两端共享常量；
     *  实体未同步时跳过，宁可不放也别在 (0,0,0) 炸一堆。 */
    private static void spawnMeatBurst(int entityId) {
        if (entityId <= 0) return; // 0 是 readEntityId 的"载荷类型不对"哨兵值
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        EntityParticleUtils.spawnParticles(entity, MeatBurstPacket.burstConfig());
    }

    /** 按服务端算好的方向 / 距离模长 / 数量喷一簇血滴：随机只在碰撞箱撒点与锥形散布上，不改"从哪边打来的"；
     *  数量已被 {@link BloodBurstPacket#readCount} 夹到 1~MAX_COUNT，脏包不会刷爆粒子量。 */
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

    /** 按服务端算好的位置与速度生成失心粒子：方向与模长原样使用（表达"从攻击者飞向受击者"），
     *  随机只在碰撞箱内撒点与 0.9~1.0 的速度抖动上。 */
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
