package org.bytechen.rottenruins.overworld.registry.corruption;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;

import java.util.function.Supplier;

/**
 * 腐化系方块注册表 —— 简化版移植。
 *
 * <p>本轮全部按"普通满方块 / 普通方块"处理（不做扩散、不做生长、不做方块实体），
 * 美术与玩法细节后续补齐。id 沿用 corruption 原 id（KING 系列已剔除）。</p>
 */
@SuppressWarnings("removal")
public class CorruptionBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HallMod.MODID);

    private static RegistryObject<Block> block(String name, Supplier<Block> supplier) {
        RegistryObject<Block> registered = BLOCKS.register(name, supplier);
        CorruptionItems.ITEMS.register(name,
                () -> new net.minecraft.world.item.BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.copy(Blocks.STONE);
    }

    private static BlockBehaviour.Properties soil() {
        return BlockBehaviour.Properties.copy(Blocks.DIRT);
    }

    private static BlockBehaviour.Properties organic() {
        return BlockBehaviour.Properties.copy(Blocks.NETHER_WART_BLOCK);
    }

    // ==================== 腐化地貌 ====================
    public static final RegistryObject<Block> SPREAD_DIRT = block("spread_dirt", () -> new Block(soil()));
    public static final RegistryObject<Block> SPREAD_STONE = block("spread_stone", () -> new Block(stone()));
    public static final RegistryObject<Block> INFECTION_COBBLESTONE_BLOCK =
            block("infection_cobblestone_block", () -> new Block(stone()));
    public static final RegistryObject<Block> SPREAD_INFECTION_LEAVES =
            block("spread_infection_leaves", () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)));
    public static final RegistryObject<Block> SPREAD_INFECTION_WOOD =
            // 注意：不能 copy(OAK_LOG)——它的 mapColor 回调会读 RotatedPillarBlock.AXIS，
            // 普通 Block 没有该属性会在注册期直接抛异常。用 OAK_PLANKS 的静态属性。
            block("spread_infection_wood", () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> CORRUPTION_GRASS = block("corruption_grass", () -> new Block(soil()));
    public static final RegistryObject<Block> CORRUPTION_TRACHEA = block("corruption_trachea", () -> new Block(organic()));
    public static final RegistryObject<Block> CONNECT_BLOCK = block("connect_block", () -> new Block(organic()));
    public static final RegistryObject<Block> DECIDUAL_TISSUE_BLOCK = block("decidual_tissue_block", () -> new Block(organic()));
    public static final RegistryObject<Block> FLESHBED = block("fleshbed", () -> new Block(organic()));
    public static final RegistryObject<Block> VARIABLE_HEIGHT_CROP =
            block("variable_height_crop", () -> new Block(BlockBehaviour.Properties.copy(Blocks.GRASS)));
    public static final RegistryObject<Block> SPIRACLE_BLOCK = block("spiracle_block", () -> new Block(organic()));

    // ==================== 遗骸（腐化/同化/重构 × 小/中/大） ====================
    public static final RegistryObject<Block> DEGENERATE_REMAINS_SMALL = block("degenerate_remains_small", () -> new Block(organic()));
    public static final RegistryObject<Block> DEGENERATE_REMAINS_MEDIUM = block("degenerate_remains_medium", () -> new Block(organic()));
    public static final RegistryObject<Block> DEGENERATE_REMAINS_LARGE = block("degenerate_remains_large", () -> new Block(organic()));
    public static final RegistryObject<Block> HOST_REMAINS_SMALL = block("host_remains_small", () -> new Block(organic()));
    public static final RegistryObject<Block> HOST_REMAINS_MEDIUM = block("host_remains_medium", () -> new Block(organic()));
    public static final RegistryObject<Block> HOST_REMAINS_LARGE = block("host_remains_large", () -> new Block(organic()));
    public static final RegistryObject<Block> REFACTORED_REMAINS_SMALL = block("refactored_remains_small", () -> new Block(organic()));
    public static final RegistryObject<Block> REFACTORED_REMAINS_MEDIUM = block("refactored_remains_medium", () -> new Block(organic()));
    public static final RegistryObject<Block> REFACTORED_REMAINS_LARGE = block("refactored_remains_large", () -> new Block(organic()));
}
