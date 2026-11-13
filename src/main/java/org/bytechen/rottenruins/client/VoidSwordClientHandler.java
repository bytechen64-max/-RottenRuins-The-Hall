package org.bytechen.rottenruins.client;

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
import org.bytechen.rottenruins.network.c2s.VoidSwordStrikePacket;
import org.bytechen.rottenruins.overworld.registry.items.VoidSword;
import org.bytechen.rottenruins.overworld.registry.items.VoidSwordGuard;

/**
 * 虚空剑「蹲下左键」的客户端选中：服务端的距离判定可能被整合包改写或网络重排，所以自己从相机
 * 视线做一次 100 格射线，命中生物就发 {@link VoidSwordStrikePacket}，姿态与距离由服务端复核。
 * 挂 InteractionKeyMappingTriggered（左键已过原版判定，且不依赖 keyAttack 的本地化名）。
 * 起点用相机而非眼睛：第三人称下眼睛起点会和准星错位，与原版 pick 一致；刻意不做准星提示。
 */
@OnlyIn(Dist.CLIENT)
public final class VoidSwordClientHandler {

    private VoidSwordClientHandler() {}

    /** 客户端射线上限：比服务端的 100 略放宽，判定权威仍在服务端，放宽不会越权。 */
    private static final double CLIENT_PICK_RANGE = 104.0D;

    /** 命中判定的额外扩张（格）。太小会"明明砍到翅膀却没选中"。 */
    private static final double PICK_INFLATE = 0.5D;

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL, false, InputEvent.InteractionKeyMappingTriggered.class,
                VoidSwordClientHandler::onInteraction);
    }

    /** 左键入口。刻意不加 {@code @SubscribeEvent}：register() 已手动挂总线，双注册会处理两遍。 */
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

    /** 从相机视线射线选中：优先复用原版算好的 {@code mc.hitResult}，未命中生物时才自己补一次射线。 */
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

    public static boolean holdingVoidSword(Player player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        return !main.isEmpty() && main.getItem() instanceof VoidSword;
    }
}
