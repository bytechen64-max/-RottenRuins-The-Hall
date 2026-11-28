package org.bytechen.rottenruins.network.c2s;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;
import org.bytechen.rottenruins.overworld.registry.items.VoidSwordGuard;
import org.bytechen.rottenruins.utils.ModUtils;

/**
 * 客户端 → 服务端：蹲下左键虚空剑点中的目标，请求一次强制斩杀。
 * 自带链路的原因：原版的交互距离判定可被别的模组改写，会让这条"强制选中"静默失效，
 * 所以客户端自己射线选中，服务端只校验玩家 / 距离 / 姿态，不依赖原版判定。
 * 与 {@code AttackEntityEvent} 会同时触发，但 {@link VoidSwordGuard#executeInstantKill} 按"玩家 + tick"去重。
 * 走 {@code UniversalPacket} + {@code PacketActionRegistry}，因此不用动 {@code NetworkHelper.register()} 的包序号。
 */
public final class VoidSwordStrikePacket {

    /** action 名。前缀 modid + 功能名，避免与别的包撞车。 */
    public static final String ACTION = "rotten_ruins:void_sword_strike";

    /** 服务端允许的最大攻击距离（格）。 */
    private static final double MAX_RANGE = 100.0D;

    /**
     * 容错余量（格）。发包到服务端处理之间双方位置会往前走（冲刺 / 坐骑），
     * 卡在正好 100.0 会把合法攻击判成超距；8 格既吸收延迟差又不会放过隔着半张图的包。
     */
    private static final double RANGE_SLACK = 8.0D;

    private VoidSwordStrikePacket() {}

    // 发送端（客户端）

    /** 把"我砍到了这个实体"发给服务端。只在客户端调用。 */
    public static void send(Entity target) {
        if (target == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", target.getId());
        NetworkHelper.sendToServer(new UniversalPacket(ACTION, UniversalPacketData.ofNbt(tag)));
    }

    // 接收端（服务端）

    /** 在 {@code NetworkHelper.register()}（公共初始化，两端都加载）里调用，省掉一个 FMLCommonSetupEvent。 */
    public static void register() {
        PacketActionRegistry.register(ACTION, (data, ctx) -> {
            if (!(data.getValue() instanceof CompoundTag tag)) return;
            handle(ctx.getSender(), tag);
        });
    }

    /** 服务端处理：所有判断都在这里做，客户端发来的只是"申请"。 */
    private static void handle(ServerPlayer player, CompoundTag tag) {
        if (player == null) return;

        // 服务端自己读玩家真实姿态，不信客户端自称（客户端只发了目标 id）
        if (!VoidSwordGuard.isArmed(player)) return;

        Entity entity = player.serverLevel().getEntity(tag.getInt("id"));
        if (!(entity instanceof LivingEntity target)) return;
        if (!target.isAlive() || target == player) return;

        // 按"眼睛 → 目标碰撞箱最近点"算，与原版一致：大体型生物中心在外面，
        // 按中心点算会让"贴着它砍"反而判成超距。
        double distance = distanceToBox(player.getEyePosition(), target);
        if (distance > MAX_RANGE + RANGE_SLACK) {
            ModUtils.LOGGER.warn("[VoidSword] 拒绝一次超距斩杀请求：{} → {} ({} 格)",
                    player.getName().getString(), target.getName().getString(),
                    String.format("%.1f", distance));
            return;
        }

        VoidSwordGuard.executeInstantKill(player, target);
    }

    /** 点到实体碰撞箱的最近距离（点在箱内时返回 0）。 */
    private static double distanceToBox(Vec3 point, Entity entity) {
        AABB box = entity.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
