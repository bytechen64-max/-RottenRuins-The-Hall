package org.bytechen.hall.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import org.bytechen.hall.network.c2s.VoidSwordStrikePacket;
import org.bytechen.hall.overworld.registry.items.VoidSword;
import org.bytechen.hall.overworld.registry.items.VoidSwordGuard;

/**
 * 虚空剑「蹲下左键」的<b>客户端选中</b>。
 *
 * <h3>它解决什么问题</h3>
 * <p>原版的攻击链路是"客户端射线选中 → 发 {@code ServerboundInteractPacket} → 服务端结算"。
 * 100 格的攻击距离靠物品属性（{@code forge:entity_reach}）已经能抬起来，
 * 但服务端那一侧的距离判定是别人的地盘：被整合包里别的模组改写、或者
 * 某次网络重排让位置差出一格，玩家看到的就是"蹲着砍了一刀，什么都没发生"。</p>
 *
 * <p>所以这里自己把"选中"这一步做实：从<b>相机视线</b>（而不是玩家眼睛）做一次
 * 100 格的射线，命中生物就发 {@link VoidSwordStrikePacket}。
 * 服务端收到后自己校验姿态与距离并结算 —— <b>不依赖原版的交互距离判定</b>。</p>
 *
 * <h3>为什么挂 {@code InteractionKeyMappingTriggered}</h3>
 * <p>它在"左键已经通过原版判定、即将真的出手"之后触发，且不依赖任何按键映射的
 * 本地化名字（{@code keyAttack} 的 key 名是可配置的，写死字符串会失效）。</p>
 *
 * <h3>为什么射线起点用相机而不是眼睛</h3>
 * <p>第三人称下相机在玩家身后，用眼睛做起点会和"玩家看到的准星"错位几格；
 * 原版 {@code GameRenderer.pick} 用的就是相机位置，这里保持一致。</p>
 *
 * <h3>为什么不做准星提示</h3>
 * <p>刻意不加：这条能力的价值在于"一刀就是一刀"，没有蓄力/冷却，加提示反而多一层
 * 与客户端状态同步的负担。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VoidSwordClientHandler {

    private VoidSwordClientHandler() {}

    /**
     * 客户端射线的最大距离。
     *
     * <p>比服务端的 100 略放宽：客户端多选一点、服务端再按 100 + 容错裁掉，
     * 判定的唯一权威始终在服务端，客户端宽松不会造成越权。</p>
     */
    private static final double CLIENT_PICK_RANGE = 104.0D;

    /** 命中判定的额外扩张（格）。太小会"明明砍到翅膀却没选中"。 */
    private static final double PICK_INFLATE = 0.5D;

    /** 注册到 Forge 总线。在 {@code ClientPacketHandlers.init()} 里调用。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL, false, InputEvent.InteractionKeyMappingTriggered.class,
                VoidSwordClientHandler::onInteraction);
    }

    /**
     * 左键事件入口。
     *
     * <p>刻意<b>不</b>用 {@code @SubscribeEvent}：它由 {@link #register()}
     * 用 {@code MinecraftForge.EVENT_BUS.addListener} 手动挂到 Forge 总线上，
     * 再加注解会变成"两条注册路径同时生效"，事件会被处理两遍。</p>
     */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        // 姿态：蹲下 + 主手虚空剑。与服务端 VoidSwordGuard.isArmed 同一套判定。
        if (!VoidSwordGuard.isArmed(player)) return;

        Entity target = pickEntity(mc, player, level);
        if (target instanceof LivingEntity) {
            VoidSwordStrikePacket.send(target);
        }
    }

    /**
     * 从相机视线做一次射线选中。
     *
     * <p>先用原版已经算好的 {@code mc.hitResult}：如果它已经命中生物就直接用
     * （既省一次射线，也尊重别的模组对选中逻辑的改写）。
     * 只有在原版没命中时才自己射线 —— 这时才需要 {@link #CLIENT_PICK_RANGE}
     * 在这条 100 格的链路上补一次。</p>
     */
    private static Entity pickEntity(Minecraft mc, Player player, ClientLevel level) {
        if (mc.hitResult instanceof EntityHitResult entityHit) {
            return entityHit.getEntity();
        }

        Entity camera = mc.getCameraEntity();
        Vec3 eye = camera != null ? camera.getEyePosition() : player.getEyePosition();
        Vec3 look = camera != null
                ? camera.getViewVector(1.0f)
                : player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(CLIENT_PICK_RANGE));

        AABB search = new AABB(eye, end).inflate(PICK_INFLATE + 1.0D);
        Entity best = null;
        double bestDistanceSqr = Double.MAX_VALUE;

        for (Entity candidate : level.getEntities(player, search,
                e -> e instanceof LivingEntity && e.isAlive() && e.isPickable())) {
            AABB box = candidate.getBoundingBox().inflate(PICK_INFLATE);
            var clip = box.clip(eye, end);
            if (clip.isEmpty()) continue;
            double distanceSqr = eye.distanceToSqr(clip.get());
            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                best = candidate;
            }
        }
        return best;
    }

    /** 便于外部判断"当前手上是不是这把剑"（例如 HUD / 提示层复用）。 */
    public static boolean holdingVoidSword(Player player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        return !main.isEmpty() && main.getItem() instanceof VoidSword;
    }
}
