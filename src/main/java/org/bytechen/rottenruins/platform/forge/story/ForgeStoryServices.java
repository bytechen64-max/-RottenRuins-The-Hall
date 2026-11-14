package org.bytechen.rottenruins.platform.forge.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.bytechen.rottenruins.gameplay.story.StoryRuntime;
import org.bytechen.rottenruins.gameplay.story.StoryText;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;

public final class ForgeStoryServices implements StoryRuntime.Services {
    @Override public void send(ServerPlayer player, CompoundTag payload) {
        NetworkHelper.sendToPlayer(player, new UniversalPacket(StoryText.ACTION, UniversalPacketData.ofNbt(payload)));
    }
}
