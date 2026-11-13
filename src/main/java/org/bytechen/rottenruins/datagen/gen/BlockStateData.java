package org.bytechen.rottenruins.datagen.gen;

import org.bytechen.rottenruins.HallMod;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.HallVineBlock;

import java.util.Objects;

/** 数据生成器：自动生成方块状态（blockstates）与模型（models）JSON。 */
public class BlockStateData extends BlockStateProvider {

    public BlockStateData(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, HallMod.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        grassBlockWithItem(RegisterBlock.HALL_GRASS_BLOCK.get());
        logBlockWithItem(RegisterBlock.HALL_PILLAR.get());
        simpleBlockWithItem(RegisterBlock.HALL_DIRT.get());
        simpleBlockWithItem(RegisterBlock.HALL_STONE.get());
        simpleBlockWithItem(RegisterBlock.HALL_PLANKS.get());
        stairsBlockWithItem((StairBlock) RegisterBlock.HALL_STAIRS.get(), RegisterBlock.HALL_PLANKS.get());
        slabBlockWithItem((SlabBlock) RegisterBlock.HALL_SLAB.get(), RegisterBlock.HALL_PLANKS.get());
        fenceBlockWithItem((FenceBlock) RegisterBlock.HALL_FENCE.get(), RegisterBlock.HALL_PLANKS.get());
        fenceGateBlockWithItem((FenceGateBlock) RegisterBlock.HALL_FENCE_GATE.get(), RegisterBlock.HALL_PLANKS.get());
        doorBlockWithItem((DoorBlock) RegisterBlock.HALL_DOOR.get(),
                ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "block/hall_door_bottom"),
                ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "block/hall_door_top"));
        trapdoorBlockWithItem((TrapDoorBlock) RegisterBlock.HALL_TRAPDOOR.get(), RegisterBlock.HALL_PLANKS.get());
        buttonBlockWithItem((ButtonBlock) RegisterBlock.HALL_BUTTON.get(), RegisterBlock.HALL_PLANKS.get());
        pressurePlateBlockWithItem((PressurePlateBlock) RegisterBlock.HALL_PRESSURE_PLATE.get(), RegisterBlock.HALL_PLANKS.get());
        stairsBlockWithItem((StairBlock) RegisterBlock.HALL_STONE_STAIRS.get(), RegisterBlock.HALL_STONE.get());
        slabBlockWithItem((SlabBlock) RegisterBlock.HALL_STONE_SLAB.get(), RegisterBlock.HALL_STONE.get());
        wallBlockWithItem((WallBlock) RegisterBlock.HALL_STONE_WALL.get(), RegisterBlock.HALL_STONE.get());
        buttonBlockWithItem((ButtonBlock) RegisterBlock.HALL_STONE_BUTTON.get(), RegisterBlock.HALL_STONE.get());
        pressurePlateBlockWithItem((PressurePlateBlock) RegisterBlock.HALL_STONE_PRESSURE_PLATE.get(), RegisterBlock.HALL_STONE.get());
        simpleBlockWithItem(RegisterBlock.DOMITE_MINERAL.get());
        simpleBlockWithItem(RegisterBlock.DOMERITE_MINERAL.get());
        hallVineBlockWithItem(RegisterBlock.HALL_VINE.get());
        logBlockWithItem(RegisterBlock.HALL_LOG.get());
        leavesBlockWithItem(RegisterBlock.HALL_LEAVES.get());
        crossBlockWithItem(RegisterBlock.HALL_FLOWER.get());
        crossBlockWithItem(RegisterBlock.HALL_GRASS.get());

        sandstoneBlockWithItem(RegisterBlock.HALL_SANDSTONE.get(), RegisterBlock.HALL_ASH_CUT_SANDSTONE.get());
        simpleBlockWithItem(RegisterBlock.HALL_ASH_SAND.get());
        sandstoneColumnBlockWithItem(RegisterBlock.HALL_ASH_CUT_SANDSTONE.get());
        sandstoneColumnBlockWithItem(RegisterBlock.HALL_ASH_SMOOTH_SANDSTONE.get());
        sandstoneColumnBlockWithItem(RegisterBlock.HALL_ASH_COLLAPSED_CHISELED_SANDSTONE.get());
        cactusBlockWithItem(RegisterBlock.HALL_ASH_CACTUS.get());
        crossBlockWithItem(RegisterBlock.HALL_ASH_DEAD_BUSH.get());

        simpleBlockWithItem(RegisterBlock.FLESH_RIFT.get());

        // 死亡核心本体是普通 cube_all：发光靠 BER 里的 death_core mask 层，那层不参与 blockstate/model 生成。
        simpleBlockWithItem(RegisterBlock.DEATH_CORE.get());
    }

    protected void simpleBlockWithItem(Block block) {
        simpleBlock(block);
        simpleBlockItem(block, cubeAll(block));
    }

    /** 柱子类方块：侧面用自身纹理，顶 / 底用带 {@code _top} 后缀的纹理。 */
    protected void logBlockWithItem(Block block) {
        logBlock((net.minecraft.world.level.block.RotatedPillarBlock) block);
        simpleBlockItem(block, models().cubeColumn(
                name(block),
                blockTexture(block),
                extend(blockTexture(block), "_top")
        ));
    }

    /** 交叉植物：cross 模型 + cutout 渲染类型（处理透明像素），物品栏用 item/generated。 */
    protected void crossBlockWithItem(Block block) {
        simpleBlock(block, models().cross(name(block), blockTexture(block)).renderType("cutout"));
        itemModels().withExistingParent(name(block), "item/generated")
                .texture("layer0", blockTexture(block));
    }

    /**
     * 王庭藤蔓：按 GROWTH_DIR + SECTION 组合出 6 种 cross 变体；
     * 注意 DOWN 方向的模型映射是交错的（生长端取 middle、茎段取 bottom）。
     */
    protected void hallVineBlockWithItem(Block block) {
        ResourceLocation texUp = extend(blockTexture(block), "_up");
        ResourceLocation texUpTop = extend(blockTexture(block), "_up_top");
        ResourceLocation texDownBottom = extend(blockTexture(block), "_down_bottom");
        ResourceLocation texDownMiddle = extend(blockTexture(block), "_down_middle");
        ResourceLocation texDownTop = extend(blockTexture(block), "_down_top");

        ModelFile upBottomModel = models().cross(name(block) + "_up_bottom", texUp).renderType("cutout");
        ModelFile upMiddleModel = models().cross(name(block) + "_up_middle", texUp).renderType("cutout");
        ModelFile upTopModel = models().cross(name(block) + "_up_top", texUpTop).renderType("cutout");

        ModelFile downBottomModel = models().cross(name(block) + "_down_bottom", texDownBottom).renderType("cutout");
        ModelFile downMiddleModel = models().cross(name(block) + "_down_middle", texDownMiddle).renderType("cutout");
        ModelFile downTopModel = models().cross(name(block) + "_down_top", texDownTop).renderType("cutout");

        getVariantBuilder(block).forAllStates(state -> {
            HallVineBlock.GrowthDirection dir = state.getValue(HallVineBlock.GROWTH_DIR);
            HallVineBlock.VineSection section = state.getValue(HallVineBlock.SECTION);

            ModelFile model = switch (dir) {
                case UP -> switch (section) {
                    case BOTTOM -> upBottomModel;
                    case MIDDLE -> upMiddleModel;
                    case TOP -> upTopModel;
                };
                case DOWN -> switch (section) {
                    case BOTTOM -> downMiddleModel;
                    case MIDDLE -> downBottomModel;
                    case TOP -> downTopModel;
                };
            };

            return ConfiguredModel.builder().modelFile(model).build();
        });

        itemModels().withExistingParent(name(block), "item/generated")
                .texture("layer0", texUpTop);
    }

    protected void slabBlockWithItem(net.minecraft.world.level.block.SlabBlock slab, Block fullBlock) {
        slabBlock(slab, blockTexture(fullBlock), blockTexture(fullBlock));
        simpleBlockItem(slab, models().slab(
                name(slab),
                blockTexture(fullBlock),
                blockTexture(fullBlock),
                blockTexture(fullBlock)
        ));
    }

    protected void stairsBlockWithItem(net.minecraft.world.level.block.StairBlock stair, Block fullBlock) {
        stairsBlock(stair, blockTexture(fullBlock));
        simpleBlockItem(stair, models().stairs(
                name(stair),
                blockTexture(fullBlock),
                blockTexture(fullBlock),
                blockTexture(fullBlock)
        ));
    }

    protected void wallBlockWithItem(net.minecraft.world.level.block.WallBlock wall, Block fullBlock) {
        wallBlock(wall, blockTexture(fullBlock));
        simpleBlockItem(wall, models().wallInventory(
                name(wall) + "_inventory",
                blockTexture(fullBlock)
        ));
    }

    protected void fenceBlockWithItem(net.minecraft.world.level.block.FenceBlock fence, Block plank) {
        fenceBlock(fence, blockTexture(plank));
        simpleBlockItem(fence, models().fenceInventory(
                name(fence) + "_inventory",
                blockTexture(plank)
        ));
    }

    /** 板状方块：顶部纹理额外带 {@code _pane_top} 后缀。 */
    protected void paneBlockWithItem(net.minecraft.world.level.block.IronBarsBlock pane, Block glass) {
        paneBlock(pane, blockTexture(glass), extend(blockTexture(glass), "_pane_top"));
        itemModels().withExistingParent(name(pane), "item/generated")
                .texture("layer0", blockTexture(glass));
    }

    protected void fenceGateBlockWithItem(FenceGateBlock gate, Block plank) {
        fenceGateBlock(gate, blockTexture(plank));
        simpleBlockItem(gate, models().fenceGate(
                name(gate),
                blockTexture(plank)
        ));
    }

    protected void doorBlockWithItem(DoorBlock door, Block plank) {
        doorBlockWithRenderType(door, blockTexture(plank), blockTexture(plank), "cutout");
        // 门的物品模型用平面 generated（和原版门一致），纹理是专用的门贴图
        itemModels().withExistingParent(name(door), "item/generated")
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "item/" + name(door)));
    }

    protected void trapdoorBlockWithItem(TrapDoorBlock trapdoor, Block plank) {
        trapdoorBlockWithRenderType(trapdoor, blockTexture(plank), true, "cutout");
        simpleBlockItem(trapdoor, models().trapdoorBottom(
                name(trapdoor) + "_bottom",
                blockTexture(plank)
        ));
    }

    protected void buttonBlockWithItem(ButtonBlock button, Block plank) {
        ResourceLocation tex = blockTexture(plank);
        ModelFile buttonModel = models().button(name(button), tex);
        ModelFile buttonPressedModel = models().buttonPressed(name(button) + "_pressed", tex);
        buttonBlock(button, buttonModel, buttonPressedModel);
        simpleBlockItem(button, models().buttonInventory(
                name(button) + "_inventory",
                tex
        ));
    }

    protected void pressurePlateBlockWithItem(PressurePlateBlock plate, Block plank) {
        ResourceLocation tex = blockTexture(plank);
        ModelFile plateModel = models().pressurePlate(name(plate), tex);
        ModelFile plateDownModel = models().pressurePlateDown(name(plate) + "_down", tex);
        pressurePlateBlock(plate, plateModel, plateDownModel);
        simpleBlockItem(plate, plateModel);
    }

    /** 纹理命名约定：侧面 = 注册名，顶面 = 注册名 + {@code _top}，底面 = 注册名 + {@code _bottom}。 */
    protected void grassBlockWithItem(Block block) {
        ResourceLocation base = blockTexture(block);
        ResourceLocation side = base;
        ResourceLocation top = extend(base, "_top");
        ResourceLocation bottom = extend(base, "_bottom");

        simpleBlock(block, models().cubeBottomTop(name(block), side, bottom, top));
        simpleBlockItem(block, models().cubeBottomTop(name(block), side, bottom, top));
    }

    protected void sandstoneBlockWithItem(Block block, Block sideBlock) {
        ResourceLocation side = blockTexture(sideBlock);
        ResourceLocation top = extend(blockTexture(block), "_top");
        ResourceLocation bottom = extend(blockTexture(block), "_bottom");
        simpleBlock(block, models().cubeBottomTop(name(block), side, bottom, top));
        simpleBlockItem(block, models().cubeBottomTop(name(block), side, bottom, top));
    }

    /** 砂岩变体：顶 / 底固定用 {@code hall_sandstone_top/bottom}，四周用方块自身纹理。 */
    protected void sandstoneColumnBlockWithItem(Block block) {
        ResourceLocation side = blockTexture(block);
        ResourceLocation top = extend(blockTexture(RegisterBlock.HALL_SANDSTONE.get()), "_top");
        ResourceLocation bottom = extend(blockTexture(RegisterBlock.HALL_SANDSTONE.get()), "_bottom");
        simpleBlock(block, models().cubeBottomTop(name(block), side, bottom, top));
        simpleBlockItem(block, models().cubeBottomTop(name(block), side, bottom, top));
    }

    /**
     * 不能套用原版 {@code block/cactus} 父模型：本模组三张贴图四周各留 1 像素透明留白，
     * 而原版六面 UV 都是 {@code [0,0,16,16]}，会把留白铺开成四周一圈黑边。
     * 所以这里显式写 UV：侧面 {@code [1,0,15,16]}、顶 / 底 {@code [1,1,15,15]}。
     */
    protected void cactusBlockWithItem(Block block) {
        ResourceLocation base = blockTexture(block);
        ResourceLocation side = extend(base, "_side");
        ResourceLocation bottom = extend(base, "_bottom");
        ResourceLocation top = extend(base, "_top");

        ModelFile cactusModel = models().withExistingParent(name(block), "block/block")
                .texture("particle", side)
                .texture("side", side)
                .texture("top", top)
                .texture("bottom", bottom)
                .element()
                    .from(0.0F, 0.0F, 0.0F)
                    .to(16.0F, 16.0F, 16.0F)
                    .face(Direction.DOWN).uvs(1.0F, 1.0F, 15.0F, 15.0F).texture("#bottom").cullface(Direction.DOWN).end()
                    .face(Direction.UP).uvs(1.0F, 1.0F, 15.0F, 15.0F).texture("#top").cullface(Direction.UP).end()
                .end()
                .element()
                    .from(0.0F, 0.0F, 1.0F)
                    .to(16.0F, 16.0F, 15.0F)
                    .face(Direction.NORTH).uvs(1.0F, 0.0F, 15.0F, 16.0F).texture("#side").end()
                    .face(Direction.SOUTH).uvs(1.0F, 0.0F, 15.0F, 16.0F).texture("#side").end()
                .end()
                .element()
                    .from(1.0F, 0.0F, 0.0F)
                    .to(15.0F, 16.0F, 16.0F)
                    .face(Direction.WEST).uvs(1.0F, 0.0F, 15.0F, 16.0F).texture("#side").end()
                    .face(Direction.EAST).uvs(1.0F, 0.0F, 15.0F, 16.0F).texture("#side").end()
                .end();

        simpleBlock(block, cactusModel);
        simpleBlockItem(block, cactusModel);
    }

    /** 树叶：必须 cutout，否则"高质量"画质下透明像素会变黑。 */
    private void leavesBlockWithItem(Block block) {
        ModelFile leavesModel = models().cubeAll(name(block), blockTexture(block))
                .renderType("cutout");
        simpleBlock(block, leavesModel);
        simpleBlockItem(block, leavesModel);
    }

    protected void doorBlockWithItem(DoorBlock door, ResourceLocation bottomTex, ResourceLocation topTex) {
        doorBlockWithRenderType(door, bottomTex, topTex, "cutout");
        itemModels().withExistingParent(name(door), "item/generated")
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "item/" + name(door)));
    }

    protected String name(Block block) {
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block)).getPath();
    }

    /** 给路径追加后缀、命名空间保持不变（例：{@code modid:block/test} + {@code _top} → {@code modid:block/test_top}）。 */
    protected ResourceLocation extend(ResourceLocation rl, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), rl.getPath() + suffix);
    }
}