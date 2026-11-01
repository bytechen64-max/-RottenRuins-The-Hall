package org.bytechen.rottenruins.overworld.registry.corruption;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption.CorruptionSpreadBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption.CorruptionGrassBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption.CorruptionVariableHeightCropBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption.FleshbedBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption.RemainsBlock;

import java.util.function.Supplier;

/**
 * 腐化系方块注册表。
 *
 * <p>大部分地形方块仍按普通满方块占位（扩散/群系机制后续补）；以下"特殊模型/数据格式"
 * 的方块已经按 corruption 原实现移植：</p>
 * <ul>
 *   <li>残骸 ×9：{@link RemainsBlock}（六向 facing + 十字交叉模型，datagen 生成）</li>
 *   <li>腐染矮草：{@link CorruptionGrassBlock}（cutout cross，只认腐染泥土）</li>
 *   <li>腐染经络：{@link CorruptionVariableHeightCropBlock}（age/section 三段模型 + 生长）</li>
 *   <li>血肉温床：{@link FleshbedBlock}（手写模型 + 水平朝向；BlockEntity/交互后续补）</li>
 * </ul>
 * <p>id 沿用 corruption 原 id（KING 系列已剔除）。</p>
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

    /** 植物类（腐染矮草 / 腐染经络）：小麦属性 + 无碰撞 + 瞬间破坏 + 植物音效。 */
    private static BlockBehaviour.Properties plant() {
        return BlockBehaviour.Properties.copy(Blocks.WHEAT)
                .noCollission()
                .instabreak()
                .sound(SoundType.CROP)
                .noOcclusion();
    }

    // ==================== 腐化地貌 ====================
    public static final RegistryObject<Block> SPREAD_DIRT =
            block("spread_dirt", () -> new CorruptionSpreadBlock(soil().randomTicks()));
    public static final RegistryObject<Block> SPREAD_STONE =
            block("spread_stone", () -> new CorruptionSpreadBlock(stone().randomTicks()));
    public static final RegistryObject<Block> INFECTION_COBBLESTONE_BLOCK =
            block("infection_cobblestone_block", () -> new CorruptionSpreadBlock(stone().randomTicks()));
    public static final RegistryObject<Block> SPREAD_INFECTION_LEAVES =
            block("spread_infection_leaves",
                    () -> new CorruptionSpreadBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES).randomTicks()));
    public static final RegistryObject<Block> SPREAD_INFECTION_WOOD =
            // 注意：不能 copy(OAK_LOG)——它的 mapColor 回调会读 RotatedPillarBlock.AXIS，
            // 普通 Block 没有该属性会在注册期直接抛异常。用 OAK_PLANKS 的静态属性。
            block("spread_infection_wood",
                    () -> new CorruptionSpreadBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).randomTicks()));
    public static final RegistryObject<Block> CORRUPTION_GRASS =
            block("corruption_grass", () -> new CorruptionGrassBlock(plant().randomTicks()));
    public static final RegistryObject<Block> CORRUPTION_TRACHEA = block("corruption_trachea", () -> new Block(organic()));
    public static final RegistryObject<Block> CONNECT_BLOCK = block("connect_block", () -> new Block(organic()));
    public static final RegistryObject<Block> DECIDUAL_TISSUE_BLOCK = block("decidual_tissue_block", () -> new Block(organic()));
    public static final RegistryObject<Block> FLESHBED = block("fleshbed", FleshbedBlock::new);
    public static final RegistryObject<Block> VARIABLE_HEIGHT_CROP =
            block("variable_height_crop",
                    () -> new CorruptionVariableHeightCropBlock(plant().randomTicks()));
    public static final RegistryObject<Block> SPIRACLE_BLOCK = block("spiracle_block", () -> new Block(organic()));

    // ==================== 遗骸（腐化/同化/重构 × 小/中/大，六向 facing） ====================
    public static final RegistryObject<Block> DEGENERATE_REMAINS_SMALL = block("degenerate_remains_small", RemainsBlock::new);
    public static final RegistryObject<Block> DEGENERATE_REMAINS_MEDIUM = block("degenerate_remains_medium", RemainsBlock::new);
    public static final RegistryObject<Block> DEGENERATE_REMAINS_LARGE = block("degenerate_remains_large", RemainsBlock::new);
    public static final RegistryObject<Block> HOST_REMAINS_SMALL = block("host_remains_small", RemainsBlock::new);
    public static final RegistryObject<Block> HOST_REMAINS_MEDIUM = block("host_remains_medium", RemainsBlock::new);
    public static final RegistryObject<Block> HOST_REMAINS_LARGE = block("host_remains_large", RemainsBlock::new);
    public static final RegistryObject<Block> REFACTORED_REMAINS_SMALL = block("refactored_remains_small", RemainsBlock::new);
    public static final RegistryObject<Block> REFACTORED_REMAINS_MEDIUM = block("refactored_remains_medium", RemainsBlock::new);
    public static final RegistryObject<Block> REFACTORED_REMAINS_LARGE = block("refactored_remains_large", RemainsBlock::new);
}
