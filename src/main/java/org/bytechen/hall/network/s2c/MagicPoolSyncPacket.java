package org.bytechen.hall.network.s2c;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.bytechen.hall.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.hall.overworld.registry.items.magic.bases.MagicHandle;

import java.util.function.Supplier;

/**
 * <b>魔法槽位同步</b>（S2C）—— 把服务端的法术槽状态整体推给客户端。
 *
 * <h3>为什么整个 NBT 一起发</h3>
 * <p>槽位内容只在"放置 / 取出 / 切换选中格 / 冷却被写入"时才变，频率极低，
 * 所以直接复用 {@link PlayerMagicPool#serializeNBT()} 的全量快照最省事也最不容易出错
 * （不用维护"增量 vs 全量"两套逻辑）。{@code ContainerHelper} 只写非空格，
 * 所以空槽位不占体积。</p>
 *
 * <h3>冷却为什么不随每 tick 的倒计时重发</h3>
 * <p>服务端剩余 tick 每 tick 都在变，全量重发等于每秒几十个包。做法是：
 * <b>只在冷却被"写入"时发一次绝对值</b>，客户端收到后自己按 tick 倒计时
 * （见 {@code PlayerMagicPool#tickCooldowns()} —— 它在客户端也会跑，
 * 但不标脏、也不会驱逐物品）。判定能不能施法始终以服务端为准，客户端那份只是显示用。</p>
 *
 * <p>槽位选择（"当前第几格"）也在这个包里，所以以后做选择界面时，客户端能立刻看到结果。</p>
 */
public class MagicPoolSyncPacket {

    private final int entityId;
    private final CompoundTag poolTag;

    public MagicPoolSyncPacket(int entityId, CompoundTag poolTag) {
        this.entityId = entityId;
        this.poolTag = poolTag;
    }

    public static void encode(MagicPoolSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeNbt(msg.poolTag);
    }

    public static MagicPoolSyncPacket decode(FriendlyByteBuf buf) {
        return new MagicPoolSyncPacket(buf.readVarInt(), buf.readNbt());
    }

    public static void handle(MagicPoolSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (!ctx.getDirection().getReceptionSide().isClient()) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(MagicPoolSyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || msg.poolTag == null) {
            return;
        }
        Entity entity = mc.level.getEntity(msg.entityId);
        if (!(entity instanceof Player player)) {
            return;
        }
        PlayerMagicPool pool = MagicHandle.getPool(player);
        if (pool != null) {
            pool.deserializeNBT(msg.poolTag);
        }
    }
}
