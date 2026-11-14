package org.bytechen.rottenruins.platform.forge.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.bytechen.rottenruins.gameplay.story.StoryRuntime;
import org.bytechen.rottenruins.gameplay.story.StoryText;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;

import org.bytechen.rottenruins.gameplay.story.StoryMessage;
import org.bytechen.rottenruins.core.story.StoryMemory;
import org.bytechen.rottenruins.platform.minecraft.story.StoryPacketCodec;
import net.minecraft.world.entity.player.Player;
import java.util.Set;

public final class ForgeStoryServices implements StoryRuntime.Services {
    @Override public StoryMemory loadMemory(Player player) { return StoryMemoryStore.load(player); }
    @Override public void saveMemory(Player player, StoryMemory memory) { StoryMemoryStore.save(player, memory); }
    @Override public void copyMemory(Player original, Player replacement, Set<String> keys) { StoryMemoryStore.copy(original, replacement, keys); }
    @Override public void send(ServerPlayer player, StoryMessage message) { transmit(player, StoryPacketCodec.encode(player, message)); }
    public static void sendEncoded(ServerPlayer player, CompoundTag snapshot) {
        CompoundTag tag = snapshot.copy();
        tag.putString("dimension", player.level().dimension().location().toString());
        transmit(player, tag);
    }
    private static void transmit(ServerPlayer player, CompoundTag payload) {
        NetworkHelper.sendToPlayer(player, new UniversalPacket(StoryText.ACTION, UniversalPacketData.ofNbt(payload)));
    }
}
