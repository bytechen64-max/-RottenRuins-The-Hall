package org.bytechen.hall.network.s2c;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.bytechen.hall.overworld.registry.capability.TwistedPoint;
import org.bytechen.hall.overworld.registry.items.magic.bases.MagicHandle;

import java.util.function.Supplier;

/**
 * <b>法力同步</b>（S2C）—— 把服务端权威的法力值推给客户端。
 *
 * <p>为什么单开一个小包、而不是和法术槽一起发：两者的变化频率差了两个数量级。
 * 法力每秒回 1 点、每次施法跳一次；槽位只在放置/取出/切格时变。合并发送会让
 * "回蓝"这种小事也拖着整份槽位 NBT 一起走。</p>
 *
 * <h3>也带 {@code maxMana}</h3>
 * <p>客户端本来可以自己用 {@code MagicStats} 现算上限，但属性修饰器/附魔若引用了
 * 只有服务端才准的数据，算出来会和实际不符，法力条就会画错。所以把上限一起发过来，
 * 客户端在<b>收到过服务端数值之后</b>以服务端为准。</p>
 *
 * <h3>方向</h3>
 * <p>注册时用 {@link NetworkDirection#PLAY_TO_CLIENT}，所以这个包不可能从客户端发上来；
 * 处理器里还有一道 {@code DistExecutor} 的侧守卫，双保险。</p>
 */
public class ManaSyncPacket {

    private final int entityId;
    private final int mana;
    private final int maxMana;

    public ManaSyncPacket(int entityId, int mana, int maxMana) {
        this.entityId = entityId;
        this.mana = mana;
        this.maxMana = maxMana;
    }

    public static void encode(ManaSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.mana);
        buf.writeVarInt(msg.maxMana);
    }

    public static ManaSyncPacket decode(FriendlyByteBuf buf) {
        return new ManaSyncPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ManaSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // 方向校验：本包只允许服务端 → 客户端
        if (!ctx.getDirection().getReceptionSide().isClient()) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ManaSyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(msg.entityId);
        if (!(entity instanceof Player player)) {
            return;
        }
        TwistedPoint mana = MagicHandle.getMana(player);
        if (mana != null) {
            mana.applyServerState(msg.mana, msg.maxMana);
        }
    }
}
