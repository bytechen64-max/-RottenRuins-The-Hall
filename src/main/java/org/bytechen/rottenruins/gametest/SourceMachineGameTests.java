package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.overworld.energy.ElementBand;
import org.bytechen.rottenruins.overworld.inventory.SourceMachineMenu;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.blockentity.SourceMachineBlockEntity;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.GroundingStakeBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceMachineBlock;
import org.bytechen.rottenruins.overworld.worldgen.HighPotentialChunks;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class SourceMachineGameTests {
    @GameTest(template="potential_empty",batch="source_machines",timeoutTicks=100)
    public static void hopperInputAndOutput(GameTestHelper helper) {
        var level=helper.getLevel();
        BlockPos pos=helper.absolutePos(new BlockPos(8,3,8));
        level.setBlockAndUpdate(pos,RegisterBlock.SOURCE_COLLECTOR.get().defaultBlockState());
        level.setBlockAndUpdate(pos.above(),Blocks.HOPPER.defaultBlockState());
        level.setBlockAndUpdate(pos.below(),Blocks.HOPPER.defaultBlockState());
        var collector=(SourceMachineBlockEntity)level.getBlockEntity(pos);
        var input=(HopperBlockEntity)level.getBlockEntity(pos.above());
        var output=(HopperBlockEntity)level.getBlockEntity(pos.below());
        input.setItem(0,new ItemStack(RegisterItem.MANA_BOTTLE.get(),2));
        input.setItem(1,new ItemStack(Items.DIRT));
        helper.assertTrue(collector.canPlaceItemThroughFace(0,new ItemStack(RegisterItem.MANA_BOTTLE.get()),Direction.UP)
                && collector.canPlaceItemThroughFace(0,new ItemStack(RegisterItem.MANA_BOTTLE.get()),Direction.NORTH),"Top and side must accept empty bottles");
        helper.assertTrue(!collector.canPlaceItemThroughFace(0,new ItemStack(RegisterItem.MANA_BOTTLE.get()),Direction.DOWN)
                && !collector.canTakeItemThroughFace(0,new ItemStack(RegisterItem.MANA_BOTTLE.get()),Direction.DOWN),"Bottom must neither insert nor extract raw input");
        helper.runAfterDelay(40,()->{
            helper.assertTrue(collector.getItem(0).getCount()==2 && output.isEmpty(),"Input hopper must feed raw bottles without bottom hopper stealing them");
            helper.assertTrue(input.getItem(1).is(Items.DIRT),"Hopper must reject non-bottle materials");
            collector.resolveFilter(ElementBand.WATER,1,45);
            var powered=collector.getBlockState().setValue(SourceMachineBlock.POWER,15);
            for(int tick=0;tick<=SourceMachineBlockEntity.TOTAL_WORK && collector.getItem(1).isEmpty();tick++)
                SourceMachineBlockEntity.tick(level,pos,powered,collector);
            helper.assertTrue(collector.getItem(1).is(RegisterItem.WATER_MANA_BOTTLE.get()) && collector.getItem(0).getCount()==1,"Charge exactly one bottle before extraction");
        });
        helper.runAfterDelay(60,()->{
            int products=0;
            for(int slot=0;slot<output.getContainerSize();slot++) {
                ItemStack stack=output.getItem(slot);
                helper.assertTrue(!stack.is(RegisterItem.MANA_BOTTLE.get()),"Output hopper must never receive raw bottles");
                if(stack.is(RegisterItem.WATER_MANA_BOTTLE.get())) products+=stack.getCount();
            }
            helper.assertTrue(products==1 && collector.getItem(1).isEmpty() && collector.getItem(0).getCount()==1,"Bottom hopper must extract only the finished product");
            helper.succeed();
        });
    }

    @GameTest(template="potential_empty",batch="source_machines")
    public static void manaPlateCrafting(GameTestHelper helper) {
        var level=helper.getLevel();
        var grid=new TransientCraftingContainer(ChestMenu.threeRows(1,helper.makeMockPlayer().getInventory()),3,3);
        grid.setItem(1,new ItemStack(Items.COPPER_INGOT));
        grid.setItem(3,new ItemStack(Items.IRON_INGOT));
        grid.setItem(4,new ItemStack(RegisterItem.SOURCE_CHARGE_SAND.get()));
        grid.setItem(5,new ItemStack(Items.IRON_INGOT));
        grid.setItem(7,new ItemStack(Items.COPPER_INGOT));
        var base=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow();
        helper.assertTrue(base.assemble(grid,level.registryAccess()).is(RegisterItem.SOURCE_MANA_PLATE.get()),"Base plate recipe must produce source mana plate");
        Item[] plates={RegisterItem.LIGHT_MANA_PLATE.get(),RegisterItem.FIRE_MANA_PLATE.get(),RegisterItem.WATER_MANA_PLATE.get(),
                RegisterItem.NATURE_MANA_PLATE.get(),RegisterItem.DEATH_MANA_PLATE.get(),RegisterItem.VOID_MANA_PLATE.get(),RegisterItem.MYSTERIOUS_MANA_PLATE.get()};
        for(ElementBand element:ElementBand.values()) {
            grid.clearContent();
            for(int slot=0;slot<9;slot++) grid.setItem(slot,new ItemStack(slot==4?RegisterItem.SOURCE_MANA_PLATE.get():SourceMachineBlockEntity.bottle(element)));
            var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow();
            var output=recipe.assemble(grid,level.registryAccess());
            helper.assertTrue(output.is(plates[element.ordinal()]) && output.getCount()==1,"Wrong plate for "+element);
            var remaining=recipe.getRemainingItems(grid);
            for(int slot=0;slot<9;slot++) helper.assertTrue(slot==4?remaining.get(slot).isEmpty():
                    remaining.get(slot).is(RegisterItem.MANA_BOTTLE.get()) && remaining.get(slot).getCount()==1,"Must return exactly eight empty bottles");
            grid.setItem(0,new ItemStack(SourceMachineBlockEntity.bottle(ElementBand.values()[(element.ordinal()+1)%7])));
            helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).isEmpty(),"Mixed elements must not craft a plate");
            grid.setItem(0,ItemStack.EMPTY);
            helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).isEmpty(),"Incomplete bottle ring must not craft a plate");
        }
        helper.succeed();
    }

    @GameTest(template="potential_empty",batch="source_machines")
    public static void bottlesAndSettings(GameTestHelper helper) {
        var level=helper.getLevel();
        BlockPos filterPos=helper.absolutePos(new BlockPos(4,2,4));
        BlockPos collectorPos=filterPos.east();
        level.setBlockAndUpdate(filterPos,RegisterBlock.SOURCE_FILTER.get().defaultBlockState());
        level.setBlockAndUpdate(collectorPos,RegisterBlock.SOURCE_COLLECTOR.get().defaultBlockState());
        var filter=(SourceMachineBlockEntity)level.getBlockEntity(filterPos);
        var collector=(SourceMachineBlockEntity)level.getBlockEntity(collectorPos);
        var powered=collector.getBlockState().setValue(SourceMachineBlock.POWER,15);
        for (ElementBand element:ElementBand.values()) {
            int[] preset=element.preset();
            filter.configure(preset[0],preset[1],preset[2],preset[3]!=0);
            helper.assertTrue(filter.element()==element,"Window must match "+element);
            collector.setItem(0,new ItemStack(RegisterItem.MANA_BOTTLE.get(),2));
            collector.setItem(1,ItemStack.EMPTY);
            collector.resolveFilter(filter.element(),1,filter.data.get(2));
            for(int tick=0;tick<=SourceMachineBlockEntity.TOTAL_WORK && collector.getItem(1).isEmpty();tick++)
                SourceMachineBlockEntity.tick(level,collectorPos,powered,collector);
            helper.assertTrue(collector.getItem(1).is(SourceMachineBlockEntity.bottle(element)),"Wrong bottle for "+element);
            helper.assertTrue(collector.getItem(1).getCount()==1 && collector.getItem(0).getCount()==1,"Must consume and output exactly one bottle");
        }
        filter.configure(5,65,20,true);
        var saved=filter.saveWithoutMetadata();
        var restored=new SourceMachineBlockEntity(filterPos,filter.getBlockState(),false);
        restored.load(saved);
        helper.assertTrue(restored.element()==ElementBand.VOID,"Settings and phase must survive save/load");
        filter.configure(Integer.MAX_VALUE,-1,1000,false);
        helper.assertTrue(filter.data.get(0)==100 && filter.data.get(1)==0 && filter.data.get(2)==100,"Untrusted parameters must be clamped");

        collector.setItem(0,new ItemStack(RegisterItem.MANA_BOTTLE.get()));
        collector.setItem(1,new ItemStack(RegisterItem.FIRE_MANA_BOTTLE.get(),64));
        collector.resolveFilter(ElementBand.WATER,1,45);
        SourceMachineBlockEntity.tick(level,collectorPos,powered,collector);
        helper.assertTrue(collector.getItem(0).getCount()==1 && collector.data.get(4)==0,"Blocked output must not consume input or work");
        collector.setItem(1,ItemStack.EMPTY);
        SourceMachineBlockEntity.tick(level,collectorPos,powered,collector);
        int progress=collector.data.get(4);
        SourceMachineBlockEntity.tick(level,collectorPos,powered.setValue(SourceMachineBlock.POWER,0),collector);
        helper.assertTrue(progress>0 && collector.data.get(4)==progress,"Power loss must pause progress");
        collector.resolveFilter(ElementBand.FIRE,1,75);
        helper.assertTrue(collector.data.get(4)==0,"Changing element must reset work");
        collector.resolveFilter(null,2,0);
        SourceMachineBlockEntity.tick(level,collectorPos,powered,collector);
        helper.assertTrue(collector.data.get(4)==0,"Invalid filter window must not charge");
        var player=helper.makeMockPlayer();
        var menu=new SourceMachineMenu(1,player.getInventory(),collector);
        helper.assertTrue(menu.slots.size()==38,"Collector menu must contain input, output and player inventory");
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(RegisterItem.FIRE_MANA_BOTTLE.get())) && !menu.getSlot(1).mayPlace(new ItemStack(RegisterItem.MANA_BOTTLE.get())),"Input and output restrictions must hold");
        helper.succeed();
    }

    @GameTest(template="potential_empty",batch="source_machines",timeoutTicks=400)
    public static void directedCircuitAndCharging(GameTestHelper helper) {
        var level=helper.getLevel();
        var config=ConfigHelper.configHolder.get();
        int original=config.highPotentialChunkRarity;
        BlockPos found=null;
        int rarity=0;
        for(int candidateRarity=2;candidateRarity<=64 && found==null;candidateRarity++) {
            for(int x=3;x<23;x++) {
                BlockPos pos=helper.absolutePos(new BlockPos(x,2,16));
                if(HighPotentialChunks.isHighPotentialChunk(level.getSeed(),level.dimension(),pos.getX()>>4,pos.getZ()>>4,candidateRarity)
                        !=HighPotentialChunks.isHighPotentialChunk(level.getSeed(),level.dimension(),pos.east(8).getX()>>4,pos.getZ()>>4,candidateRarity)) {
                    found=pos;rarity=candidateRarity;break;
                }
            }
        }
        helper.assertTrue(found!=null,"Need a high/low test boundary");
        config.highPotentialChunkRarity=rarity;
        BlockPos start=found;
        boolean highLeft=HighPotentialChunks.isHighPotentialChunk(level,start);
        Direction flow=highLeft?Direction.EAST:Direction.WEST;
        BlockPos filterPos=start.east(highLeft?2:6);
        BlockPos collectorPos=start.east(highLeft?3:5);
        try {
            for(int i=0;i<=8;i++) {
                BlockPos pos=start.east(i);
                level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
                var state=i==0 || i==8?RegisterBlock.GROUNDING_STAKE.get().defaultBlockState().setValue(GroundingStakeBlock.FACING,Direction.UP):
                        pos.equals(filterPos)?RegisterBlock.SOURCE_FILTER.get().defaultBlockState().setValue(SourceMachineBlock.FACING,flow):
                        pos.equals(collectorPos)?RegisterBlock.SOURCE_COLLECTOR.get().defaultBlockState().setValue(SourceMachineBlock.FACING,flow):RegisterBlock.SOURCE_WIRE.get().defaultBlockState();
                level.setBlockAndUpdate(pos,state);
            }
            ((SourceMachineBlockEntity)level.getBlockEntity(collectorPos)).setItem(0,new ItemStack(RegisterItem.MANA_BOTTLE.get()));
        } catch(RuntimeException error) { config.highPotentialChunkRarity=original;throw error; }
        helper.runAfterDelay(5,()->{
            try {
                helper.assertTrue(level.getBlockState(collectorPos).getValue(SourceMachineBlock.POWER)>0,"Forward chain must be powered");
                level.setBlockAndUpdate(collectorPos,level.getBlockState(collectorPos).setValue(SourceMachineBlock.FACING,flow.getOpposite()));
            } catch(RuntimeException error) { config.highPotentialChunkRarity=original;throw error; }
        });
        helper.runAfterDelay(10,()->{
            try {
                helper.assertTrue(level.getBlockState(collectorPos).getValue(SourceMachineBlock.POWER)==0,"Reversed collector must interrupt circuit");
                level.setBlockAndUpdate(collectorPos,level.getBlockState(collectorPos).setValue(SourceMachineBlock.FACING,flow));
            } catch(RuntimeException error) { config.highPotentialChunkRarity=original;throw error; }
        });
        helper.runAfterDelay(320,()->{
            try {
                var collector=(SourceMachineBlockEntity)level.getBlockEntity(collectorPos);
                helper.assertTrue(collector.getItem(1).is(RegisterItem.WATER_MANA_BOTTLE.get()) && collector.getItem(0).isEmpty(),"Full directed circuit must convert one bottle to water");
            } finally { config.highPotentialChunkRarity=original; }
            helper.succeed();
        });
    }
}
