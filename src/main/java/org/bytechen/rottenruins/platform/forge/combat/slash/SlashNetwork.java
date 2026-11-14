package org.bytechen.rottenruins.platform.forge.combat.slash;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import org.bytechen.rottenruins.api.combat.slash.*;
import java.util.Optional;

/** A separate, direction-checked channel; requests never contain targets/damage/range/pose. */
public final class SlashNetwork {
    public record Request() {}
    public record Visual(int entityId, int sequence, boolean cancelled, SlashPose pose, int combo,
                         int animationTicks, float impact, float arcDegrees, ItemStack stack, SlashTrail trail, Vec3 forward, Vec3 direction) {}
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("rotten_ruins", "slash"), () -> "1", "1"::equals, "1"::equals);
    private SlashNetwork() {}
    public static void register() {
        CHANNEL.registerMessage(0, Request.class, (p, b) -> {}, b -> new Request(), (p, supplier) -> {
            var context = supplier.get();
            context.enqueueWork(() -> { if (context.getSender() != null) SlashServer.request(context.getSender()); });
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Visual.class, SlashNetwork::encode, SlashNetwork::decode, (p, supplier) -> {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> org.bytechen.rottenruins.client.combat.slash.SlashClient.accept(p)));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void request() { CHANNEL.sendToServer(new Request()); }
    public static void broadcast(ServerPlayer player, Visual visual) {
        // Fake/test server players have no Netty channel; real observers may still
        // track them, but there is no owner connection to receive a custom payload.
        if (player.connection == null || player.connection.connection.channel() == null)
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), visual);
        else CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), visual);
    }
    private static void encode(Visual p, FriendlyByteBuf b) {
        b.writeVarInt(p.entityId); b.writeVarInt(p.sequence); b.writeBoolean(p.cancelled);
        b.writeEnum(p.pose); b.writeVarInt(p.combo); b.writeVarInt(p.animationTicks); b.writeFloat(p.impact); b.writeFloat(p.arcDegrees); b.writeItem(p.stack);
        var t = p.trail;
        b.writeResourceLocation(t.renderer()); b.writeResourceLocation(t.texture()); b.writeInt(t.color());
        b.writeFloat(t.width()); b.writeFloat(t.radius()); b.writeVarInt(t.lifetimeTicks());
        writeVec(b, p.forward); writeVec(b, p.direction);
    }
    private static Visual decode(FriendlyByteBuf b) {
        int entity = b.readVarInt(), seq = b.readVarInt(); boolean cancel = b.readBoolean();
        SlashPose pose = b.readEnum(SlashPose.class); int combo = b.readVarInt(), ticks = b.readVarInt();
        float impact = b.readFloat(), arc = b.readFloat(); ItemStack stack = b.readItem();
        var trail = new SlashTrail(b.readResourceLocation(), b.readResourceLocation(), b.readInt(), b.readFloat(), b.readFloat(), b.readVarInt());
        if (ticks < 2 || ticks > 200 || !Float.isFinite(impact + arc) || impact <= 0 || impact >= 1 || arc <= 0 || arc > 360) throw new IllegalArgumentException("Invalid slash visual timing/arc");
        return new Visual(entity, seq, cancel, pose, combo, ticks, impact, arc, stack, trail, readVec(b), readVec(b));
    }
    private static void writeVec(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(FriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
}
