package org.bytechen.rottenruins.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.event.impl.FirstJoinStoryHandler;
import org.bytechen.rottenruins.event.impl.RespawnStoryHandler;
import org.bytechen.rottenruins.gameplay.story.StoryDirector;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.UUID;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class StoryTextGameTests {
    private static CompoundTag respawnState(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(RespawnStoryHandler.STATE);
    }

    private static ServerPlayer respawn(GameTestHelper helper, ServerPlayer original, net.minecraft.world.damagesource.DamageSource source) {
        RespawnStoryHandler.death(new LivingDeathEvent(original, source));
        var restored = player(helper);
        RespawnStoryHandler.clonePlayer(new PlayerEvent.Clone(restored, original, true));
        RespawnStoryHandler.respawn(new PlayerEvent.PlayerRespawnEvent(restored, false));
        return restored;
    }

    @GameTest(template = "potential_empty", batch = "story_text")
    public static void respawnStoryDelayPersistenceAndPriority(GameTestHelper helper) {
        var original = player(helper);
        var first = respawn(helper, original, original.damageSources().fall());
        tick(first, 39);
        helper.assertTrue(StoryDirector.previous(first, "rotten_ruins:respawn").isEmpty(), "Wait two seconds after respawn");
        tick(first, 1);
        helper.assertTrue(StoryDirector.previous(first, "rotten_ruins:respawn").endsWith("first_0"), "First death overrides cause");
        helper.assertTrue(StoryDirector.pendingCount(first) == 0, "Caption sends once");
        RespawnStoryHandler.respawn(new PlayerEvent.PlayerRespawnEvent(first, false));
        helper.assertTrue(StoryDirector.pendingCount(first) == 0, "Consumed respawn cannot repeat");
        var second = respawn(helper, first, first.damageSources().drown());
        tick(second, 40);
        String previous = StoryDirector.previous(second, "rotten_ruins:respawn");
        helper.assertTrue(previous.contains("/rapid_"), "Rapid deaths override cause");
        var third = respawn(helper, second, second.damageSources().fall());
        tick(third, 40);
        helper.assertTrue(!previous.equals(StoryDirector.previous(third, "rotten_ruins:respawn")), "No consecutive duplicate captions");
        helper.assertTrue(respawnState(third).getInt("deaths") == 3, "Death history survives player clones");
        var reload = player(helper);
        reload.getPersistentData().put(Player.PERSISTED_NBT_TAG,
                third.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).copy());
        RespawnStoryHandler.respawn(new PlayerEvent.PlayerRespawnEvent(reload, false));
        helper.assertTrue(StoryDirector.pendingCount(reload) == 0, "Reload cannot replay consumed story");
        var endReturn = player(helper);
        RespawnStoryHandler.clonePlayer(new PlayerEvent.Clone(endReturn, reload, false));
        RespawnStoryHandler.respawn(new PlayerEvent.PlayerRespawnEvent(endReturn, true));
        helper.assertTrue(StoryDirector.pendingCount(endReturn) == 0, "End return is not a death");
        helper.assertTrue(respawnState(endReturn).getInt("deaths") == 3, "End return preserves death history");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "story_text")
    public static void respawnStoryCausePools(GameTestHelper helper) {
        var current = player(helper);
        current = respawn(helper, current, current.damageSources().generic());
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        for (String cause : new String[]{"fall", "drown", "mob", "normal"}) {
            respawnState(current).putLong("lastDeath", helper.getLevel().getServer().overworld().getGameTime() - 3601);
            var source = switch (cause) {
                case "fall" -> current.damageSources().fall();
                case "drown" -> current.damageSources().drown();
                case "mob" -> current.damageSources().mobAttack(zombie);
                default -> current.damageSources().generic();
            };
            current = respawn(helper, current, source);
            tick(current, 40);
            helper.assertTrue(StoryDirector.previous(current, "rotten_ruins:respawn").contains("/" + cause + "_"), "Correct cause pool: " + cause);
            helper.assertTrue(respawnState(current).getInt("streak") == 1, "Long interval resets streak");
        }
        var canceled = new LivingDeathEvent(current, current.damageSources().fall());
        canceled.setCanceled(true);
        RespawnStoryHandler.death(canceled);
        helper.assertTrue(!current.getPersistentData().contains("rotten_ruins:death_story_cause"), "Canceled deaths do not record cause");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "story-test"));
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        return player;
    }

    private static void tick(ServerPlayer player, int ticks) {
        for (int i = 0; i < ticks; i++) {
            StoryDirector.tick(player);
        }
    }

    @GameTest(template = "potential_empty", batch = "story_text")
    public static void fontPreviewAndCustomTextCommands(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = player(helper);
        var source = player.createCommandSourceStack().withPermission(2);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        for (String font : new String[]{"default", "wenkai", "xiaowei", "mashan"}) {
            helper.assertTrue(dispatcher.execute("rotten_ruins story preview " + font, source) == 1,
                    "Can preview font " + font);
        }
        helper.assertTrue(dispatcher.execute("rotten_ruins story preview all", source) == 4,
                "All fonts are queued for comparison");
        helper.assertTrue(dispatcher.execute("rotten_ruins story show_font @s 5 0 wenkai 测试文字", source) == 1,
                "Custom text supports a font preset");
        helper.assertTrue(dispatcher.execute("rotten_ruins story append_font @s 5 0 mashan 下一段", source) == 1,
                "Queued text supports a different font");
        helper.assertTrue(dispatcher.execute("rotten_ruins story show @s 5 0 原有命令", source) == 1,
                "Original command still works");
        boolean rejected = false;
        try { dispatcher.execute("rotten_ruins story preview missing_font", source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
        helper.assertTrue(rejected, "Unknown presets are rejected clearly");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "story_text")
    public static void openingWaitsForLoginAndDoesNotRepeat(GameTestHelper helper) {
        var player = player(helper);
        FirstJoinStoryHandler.login(new PlayerEvent.PlayerLoggedInEvent(player));
        tick(player, 59);
        helper.assertTrue(!player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                .getBoolean("rotten_ruins:opening_story_seen"), "Wait before sending the opening");
        tick(player, 1);
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        helper.assertTrue(persisted.getBoolean("rotten_ruins:opening_story_seen"), "Opening sent after login delay");
        helper.assertTrue(StoryDirector.pendingCount(player) == 0, "Pending trigger cleared");

        // Restore the persisted player tag as on a later session or respawn.
        var restored = player(helper);
        restored.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted.copy());
        FirstJoinStoryHandler.login(new PlayerEvent.PlayerLoggedInEvent(restored));
        helper.assertTrue(StoryDirector.pendingCount(restored) == 0,
                "A saved player does not schedule the scene again");
        var newcomer = player(helper);
        FirstJoinStoryHandler.login(new PlayerEvent.PlayerLoggedInEvent(newcomer));
        helper.assertTrue(StoryDirector.pendingCount(newcomer) == 1,
                "Another player in the same world gets their own opening");
        helper.succeed();
    }
}
