package org.bytechen.rottenruins.overworld.registry.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.rottenruins.overworld.energy.ElementBand;
import org.bytechen.rottenruins.overworld.energy.SourceWireNetwork;
import org.bytechen.rottenruins.overworld.inventory.SourceMachineMenu;
import org.bytechen.rottenruins.overworld.registry.RegisterBlockEntities;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceMachineBlock;

public final class SourceMachineBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    public static final int TOTAL_WORK=3000;
    public static final int DATA_COUNT=8;
    private static final int[] INPUT_SLOTS={0};
    private static final int[] OUTPUT_SLOTS={1};
    private static final int[] NO_SLOTS={};
    public final boolean collector;
    private final NonNullList<ItemStack> items=NonNullList.withSize(2,ItemStack.EMPTY);
    private int fc=55,bandwidth=45,gain=45;
    private boolean negative;
    private int progress;
    private ElementBand target;
    // 0=无上游滤波器，1=有效窗口，2=无效窗口，3=存在冲突滤波器。
    private int filterStatus;

    public final ContainerData data=new ContainerData() {
        @Override public int get(int index) {
            return switch(index) {
                case 0 -> fc; case 1 -> bandwidth; case 2 -> gain; case 3 -> negative?1:0;
                case 4 -> progress; case 5 -> getBlockState().getValue(SourceMachineBlock.POWER);
                case 6 -> { ElementBand element=collector?(filterStatus==1?target:null):element(); yield element==null?0:element.ordinal()+1; }
                case 7 -> filterStatus; default -> 0;
            };
        }
        @Override public void set(int index,int value) { /* 服务端数据只由设备逻辑修改。 */ }
        @Override public int getCount() { return DATA_COUNT; }
    };

    public SourceMachineBlockEntity(BlockPos pos,BlockState state,boolean collector) {
        super(collector?RegisterBlockEntities.SOURCE_COLLECTOR.get():RegisterBlockEntities.SOURCE_FILTER.get(),pos,state);
        this.collector=collector;
    }

    public ElementBand element() { return ElementBand.match(fc,bandwidth,gain,negative); }

    public void configure(int frequency,int width,int amplification,boolean phase) {
        if (collector) return;
        fc=Math.max(0,Math.min(100,frequency));
        bandwidth=Math.max(0,Math.min(100,width));
        gain=Math.max(0,Math.min(100,amplification));
        negative=phase;
        setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            SourceWireNetwork.invalidate(server);
            SourceWireNetwork.refresh(server,worldPosition,org.bytechen.rottenruins.overworld.registry.RegisterBlock.SOURCE_WIRE.get());
        }
    }

    public void resolveFilter(ElementBand element,int status,int sourceGain) {
        if (!collector) return;
        if (element!=null && target!=element) { progress=0; target=element; setChanged(); }
        filterStatus=status;
        if (status==1) gain=sourceGain;
    }

    public static Item bottle(ElementBand element) {
        return switch(element) {
            case LIGHT -> RegisterItem.LIGHT_MANA_BOTTLE.get();
            case FIRE -> RegisterItem.FIRE_MANA_BOTTLE.get();
            case WATER -> RegisterItem.WATER_MANA_BOTTLE.get();
            case NATURE -> RegisterItem.NATURE_MANA_BOTTLE.get();
            case DEATH -> RegisterItem.DEATH_MANA_BOTTLE.get();
            case VOID -> RegisterItem.VOID_MANA_BOTTLE.get();
            case MYSTERIOUS -> RegisterItem.MYSTERIOUS_MANA_BOTTLE.get();
        };
    }

    public static void tick(Level level,BlockPos pos,BlockState state,SourceMachineBlockEntity machine) {
        if (!machine.items.get(0).is(RegisterItem.MANA_BOTTLE.get())) {
            if (machine.progress!=0) { machine.progress=0; machine.setChanged(); }
            return;
        }
        int power=state.getValue(SourceMachineBlock.POWER);
        if (power==0 || machine.filterStatus!=1 || machine.target==null) return;
        Item output=bottle(machine.target);
        ItemStack result=machine.items.get(1);
        if (!result.isEmpty() && (!result.is(output) || result.getCount()>=result.getMaxStackSize())) return;
        machine.progress+=Math.max(1,power*(10+machine.gain)/60);
        if (machine.progress>=TOTAL_WORK) {
            machine.items.get(0).shrink(1);
            if (result.isEmpty()) machine.items.set(1,new ItemStack(output)); else result.grow(1);
            machine.progress=0;
        }
        machine.setChanged();
    }

    @Override public Component getDisplayName() { return Component.translatable(collector?"block.rotten_ruins.source_collector":"block.rotten_ruins.source_filter"); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new SourceMachineMenu(id,inventory,this); }
    @Override public int getContainerSize() { return collector?2:0; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot,int amount) { ItemStack result=ContainerHelper.removeItem(items,slot,amount); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items,slot); }
    @Override public void setItem(int slot,ItemStack stack) { items.set(slot,stack); stack.setCount(Math.min(stack.getCount(),getMaxStackSize())); if (slot==0 && !stack.is(RegisterItem.MANA_BOTTLE.get())) progress=0; setChanged(); }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return collector && slot==0 && stack.is(RegisterItem.MANA_BOTTLE.get()); }
    // 漏斗按面访问槽位：上方/侧面送入原料，下方只取成品，原料槽不能被自动抽取。
    @Override public int[] getSlotsForFace(Direction side) { return !collector?NO_SLOTS:side==Direction.DOWN?OUTPUT_SLOTS:INPUT_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side) {
        return side!=Direction.DOWN && canPlaceItem(slot,stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) {
        return collector && side==Direction.DOWN && slot==1;
    }
    @Override public boolean stillValid(Player player) { return level!=null && level.getBlockEntity(worldPosition)==this && player.distanceToSqr(worldPosition.getX()+0.5,worldPosition.getY()+0.5,worldPosition.getZ()+0.5)<=64; }
    @Override public void clearContent() { items.clear(); progress=0; setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Frequency",fc); tag.putInt("Bandwidth",bandwidth); tag.putInt("Gain",gain); tag.putBoolean("Negative",negative);
        tag.putInt("Progress",progress); tag.putInt("Target",target==null?0:target.ordinal()+1);
        ContainerHelper.saveAllItems(tag,items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Frequency")) {
            fc=Math.max(0,Math.min(100,tag.getInt("Frequency")));
            bandwidth=Math.max(0,Math.min(100,tag.getInt("Bandwidth")));
            gain=Math.max(0,Math.min(100,tag.getInt("Gain")));
            negative=tag.getBoolean("Negative");
        }
        progress=Math.max(0,Math.min(TOTAL_WORK-1,tag.getInt("Progress")));
        int stored=tag.getInt("Target"); target=stored>0 && stored<=ElementBand.values().length?ElementBand.values()[stored-1]:null;
        // 载入后必须重新验证线路，不能沿用存档中的工作状态。
        filterStatus=0;
        ContainerHelper.loadAllItems(tag,items);
    }
}
