package org.bytechen.rottenruins.gametest;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.advancement.RotAdvancementTriggers;
import org.bytechen.rottenruins.advancement.SourceMilestoneTrigger;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.datagen.gen.AdvancementProviderData;
import org.bytechen.rottenruins.event.impl.AdvancementEventHandler;
import org.bytechen.rottenruins.overworld.energy.ElementBand;
import org.bytechen.rottenruins.overworld.inventory.SourceMachineMenu;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.blockentity.SourceMachineBlockEntity;
import org.bytechen.rottenruins.overworld.worldgen.HighPotentialChunks;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class SourceAdvancementGameTests {
    private static Advancement advancement(GameTestHelper helper,String name) {
        var advancement=helper.getLevel().getServer().getAdvancements().getAdvancement(
                AdvancementProviderData.id(AdvancementProviderData.sourcePath(name)));
        helper.assertTrue(advancement!=null,"Missing advancement: "+name);
        return advancement;
    }

    private static boolean done(GameTestHelper helper,ServerPlayer player,String name) {
        return player.getAdvancements().getOrStartProgress(advancement(helper,name)).isDone();
    }

    private static void obtain(ServerPlayer player,Item item) {
        // 每次只持有一种物品，验证七系收藏是累计记录而非同时占用七个槽位。
        player.getInventory().clearContent();
        ItemStack stack=new ItemStack(item);
        player.getInventory().setItem(0,stack);
        CriteriaTriggers.INVENTORY_CHANGED.trigger(player,player.getInventory(),stack);
    }

    @GameTest(template="potential_empty",batch="source_advancements")
    public static void sourceMilestonesAndCollections(GameTestHelper helper) {
        // 测试真实进度监听器，但不走 Forge 登录握手，也不把无网络 Channel 的测试玩家加入服务器。
        ServerPlayer player=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),new GameProfile(UUID.randomUUID(),"source-adv-test"));
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),player);
        var config=ConfigHelper.configHolder.get();
        int oldRarity=config.highPotentialChunkRarity;
        try {
            config.highPotentialChunkRarity=64;
            BlockPos high=null,low=null;
            BlockPos origin=helper.absolutePos(BlockPos.ZERO);
            for(int x=-16;x<=16 && (high==null || low==null);x++) {
                for(int z=-16;z<=16 && (high==null || low==null);z++) {
                    BlockPos candidate=new BlockPos(((origin.getX()>>4)+x)*16+8,80,((origin.getZ()>>4)+z)*16+8);
                    if(HighPotentialChunks.isHighPotentialChunk(helper.getLevel(),candidate)) high=candidate;
                    else low=candidate;
                }
            }
            helper.assertTrue(high!=null && low!=null,"Need both potential types");
            player.getInventory().clearContent();
            player.setPos(high.getX(),high.getY(),high.getZ());
            AdvancementEventHandler.checkHighPotential(player);
            helper.assertTrue(!done(helper,player,"high_potential"),"High potential discovery requires a meter");
            ItemStack meter=new ItemStack(RegisterItem.SIMPLE_FIELD_METER.get());
            meter.getOrCreateTag().putByte("FieldPotential",(byte)2);
            player.getInventory().setItem(0,meter);
            player.setPos(low.getX(),low.getY(),low.getZ());
            AdvancementEventHandler.checkHighPotential(player);
            helper.assertTrue(!done(helper,player,"high_potential"),"Low potential must not grant discovery");
            player.setPos(high.getX(),high.getY(),high.getZ());
            AdvancementEventHandler.checkHighPotential(player);
            helper.assertTrue(done(helper,player,"high_potential"),"Meter with reading NBT must work in high potential");

            BlockPos filterPos=helper.absolutePos(new BlockPos(5,2,5));
            helper.getLevel().setBlockAndUpdate(filterPos,RegisterBlock.SOURCE_FILTER.get().defaultBlockState());
            var filter=(SourceMachineBlockEntity)helper.getLevel().getBlockEntity(filterPos);
            player.setPos(filterPos.getX()+0.5,filterPos.getY()+0.5,filterPos.getZ()+0.5);
            var menu=new SourceMachineMenu(1,player.getInventory(),filter);
            menu.configure(player,45,45,45,false);
            helper.assertTrue(!done(helper,player,"tuned_filter"),"Invalid tuning must not grant advancement");
            menu.configure(player,55,45,45,false);
            helper.assertTrue(done(helper,player,"tuned_filter") && !done(helper,player,"void_tuning"),"Valid water must grant tuning but not void");
            menu.configure(player,5,65,20,true);
            helper.assertTrue(done(helper,player,"void_tuning"),"Valid inverted void must grant its milestone");
            helper.assertTrue(!done(helper,player,"powered_circuit"),"Other milestone types must not grant powered circuit");
            RotAdvancementTriggers.triggerSourceMilestone(player,SourceMilestoneTrigger.Milestone.POWERED_CIRCUIT);
            helper.assertTrue(done(helper,player,"powered_circuit"),"Powered circuit criterion must match its event");

            obtain(player,RegisterItem.MANA_BOTTLE.get());
            helper.assertTrue(!done(helper,player,"element_bottle"),"Empty bottle is not an elemental bottle");
            int count=0;
            for(ElementBand element:ElementBand.values()) {
                obtain(player,SourceMachineBlockEntity.bottle(element));
                helper.assertTrue(done(helper,player,"element_bottle"),"Any elemental bottle must grant first-bottle milestone");
                helper.assertTrue(done(helper,player,"all_bottles")== (++count==7),"Seven bottles must accumulate over separate inventory changes");
            }
            Item[] plates={RegisterItem.LIGHT_MANA_PLATE.get(),RegisterItem.FIRE_MANA_PLATE.get(),RegisterItem.WATER_MANA_PLATE.get(),
                    RegisterItem.NATURE_MANA_PLATE.get(),RegisterItem.DEATH_MANA_PLATE.get(),RegisterItem.VOID_MANA_PLATE.get(),RegisterItem.MYSTERIOUS_MANA_PLATE.get()};
            count=0;
            for(Item plate:plates) {
                obtain(player,plate);
                helper.assertTrue(done(helper,player,"element_plate"),"Any elemental plate must grant first-plate milestone");
                helper.assertTrue(done(helper,player,"all_plates")== (++count==7),"Seven plates must accumulate over time");
            }
        } finally {
            config.highPotentialChunkRarity=oldRarity;
            player.getAdvancements().stopListening();
            player.discard();
        }
        helper.succeed();
    }
}
