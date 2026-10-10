package org.bytechen.hall.network.c2s;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.network.all.PacketActionRegistry;
import org.bytechen.hall.network.all.UniversalPacket;
import org.bytechen.hall.network.all.tools.UniversalPacketData;
import org.bytechen.hall.overworld.registry.items.VoidSwordGuard;
import org.bytechen.hall.utils.ModUtils;

/**
 * 客户端 → 服务端：<b>蹲下左键虚空剑点中的目标</b>，请执行一次强制斩杀。
 *
 * <h3>为什么这条能力需要自己的包</h3>
 * <p>物品属性把 {@code forge:entity_reach} 抬到 100 格之后，原版的
 * {@code ServerboundInteractPacket} 链路<b>通常</b>已经能在 100 格上命中——
 * 但那是"通常"：服务端最终接不接受，取决于它对交互距离的判定，
 * 而那个判定在 Forge / 整合包环境下是可以被别的模组改写的。</p>
 *
 * <p>这条能力的语义是"强制选中"，一旦被那样的判定挡下就会静默失效，
 * 玩家看到的是"蹲着砍了一刀什么都没发生"。所以这里补一条自有链路：
 * 客户端射线自己选中目标，服务端只校验"是不是这个玩家、距离够不够、姿态对不对"，
 * <b>不依赖原版的交互距离判定</b>。</p>
 *
 * <h3>和 {@code AttackEntityEvent} 的关系</h3>
 * <p>两条路会同时触发（原版那一下本来就会走事件）。重复不会造成双重结算：
 * {@link VoidSwordGuard#executeInstantKill} 内部按"玩家 + tick"去重，
 * 同 tick 内只有第一条生效。</p>
 *
 * <h3>为什么不新增包序号</h3>
 * <p>走项目已有的 {@code UniversalPacket} + {@code PacketActionRegistry} 机制
 * （与 {@code HeartLosePacket} 同一套路），因此<b>完全不用动
 * {@code NetworkHelper.register()} 里的包序号</b>——那是唯一一处
 * "客户端服务端必须严格同序"的地方，能不动就不动。</p>
 */
public final class VoidSwordStrikePacket {

    /** action 名。前缀 modid + 功能名，避免与别的包撞车。 */
    public static final String ACTION = "hall:void_sword_strike";

    /** 服务端允许的最大攻击距离（格）。 */
    private static final double MAX_RANGE = 100.0D;

    /**
     * 容错余量（格）。
     *
     * <p>客户端发包到服务端处理之间，双方位置都会往前走几格（冲刺 / 坐骑 / 高速飞行），
     * 卡在正好 100.0 上会把合法的攻击判成超距。留 8 格余量，
     * 既吸收延迟差，又不会把"隔着半张图"的包放进来。</p>
     */
    private static final double RANGE_SLACK = 8.0D;

    private VoidSwordStrikePacket() {}

    // ══════════════════════════════════════════════════════════════
    // 发送端（客户端）
    // ══════════════════════════════════════════════════════════════

    /** 把"我砍到了这个实体"发给服务端。只在客户端调用。 */
    public static void send(Entity target) {
        if (target == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", target.getId());
        NetworkHelper.sendToServer(new UniversalPacket(ACTION, UniversalPacketData.ofNbt(tag)));
    }

    // ══════════════════════════════════════════════════════════════
    // 接收端（服务端）
    // ══════════════════════════════════════════════════════════════

    /**
     * 注册到 {@code PacketActionRegistry}。
     *
     * <p>在 {@code NetworkHelper.register()} 里调用（公共初始化阶段，两端都会加载），
     * 这样不需要额外挂一个 {@code FMLCommonSetupEvent}。</p>
     */
    public static void register() {
        PacketActionRegistry.register(ACTION, (data, ctx) -> {
            if (!(data.getValue() instanceof CompoundTag tag)) return;
            handle(ctx.getSender(), tag);
        });
    }

    /**
     * 服务端处理。<b>所有判断都在这里做</b>——客户端发过来的只是"申请"。
     */
    private static void handle(ServerPlayer player, CompoundTag tag) {
        if (player == null) return;

        // ── ① 姿态：蹲下 + 主手虚空剑 ──
        // 服务端自己读玩家的真实姿态，不信客户端自称（客户端也只发了目标 id）
        if (!VoidSwordGuard.isArmed(player)) return;

        // ── ② 目标必须真的存在，且在同一个维度 ──
        Entity entity = player.serverLevel().getEntity(tag.getInt("id"));
        if (!(entity instanceof LivingEntity target)) return;
        if (!target.isAlive() || target == player) return;

        // ── ③ 距离校验：按"眼睛 → 目标碰撞箱最近点"算，与原版算法一致 ──
        // 用碰撞箱最近点而不是中心点：大体型生物的中心在外面，
        // 按中心点算会让"贴着它砍"反而判成超距。
        double distance = distanceToBox(player.getEyePosition(), target);
        if (distance > MAX_RANGE + RANGE_SLACK) {
            ModUtils.LOGGER.warn("[VoidSword] 拒绝一次超距斩杀请求：{} → {} ({} 格)",
                    player.getName().getString(), target.getName().getString(),
                    String.format("%.1f", distance));
            return;
        }

        // ── ④ 结算 ──
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
