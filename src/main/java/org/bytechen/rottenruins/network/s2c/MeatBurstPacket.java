package org.bytechen.rottenruins.network.s2c;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;
import org.bytechen.rottenruins.utils.entity.EntityParticleUtils;

/**
 * 爆体肉屑粒子的 S2C 包 —— 只告诉客户端"哪个实体炸了"，位置 / 数量 / 速度由客户端按同一份配置现算。
 * 必须<b>在实体被移除之前</b>发出：本包先入队、移除包后入队，客户端处理时才解析得到实体。
 * 下面这些数值与 corruption {@code BaseOriginalEntity#getDeathParticleConfig} 一致（水平 ±0.15、
 * 竖直 0.3~0.6 向上），两端共用：服务端不发、客户端现算，改这里等于同时改了两端。
 */
public final class MeatBurstPacket {

    public static final String ACTION = "meat_burst";

    public static final int BURST_COUNT = 30;
    public static final float BURST_RANGE_SCALE = 1.0f;
    public static final double BURST_HORIZONTAL_SPREAD = 0.3;
    public static final double BURST_UPWARD_MIN = 0.3;
    public static final double BURST_UPWARD_SPREAD = 0.3;

    private MeatBurstPacket() {}

    public static void sendBurst(Entity entity) {
        if (entity == null) return;
        if (!(entity.level() instanceof ServerLevel)) return;
        NetworkHelper.sendToClient(entity, new UniversalPacket(ACTION, UniversalPacketData.ofInt(entity.getId())));
    }

    /** 载荷类型不对时返回 0（不是合法实体 id，{@code Level#getEntity(0)} 为 null），调用方一个非正数判断即可挡掉。 */
    public static int readEntityId(UniversalPacketData<?> data) {
        if (data == null) return 0;
        return data.getValue() instanceof Integer entityId ? entityId : 0;
    }

    /** 两端共用的粒子配置。每次现建而不缓存：静态持有 {@code RegistryObject#get()} 的结果容易踩到 null。 */
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
