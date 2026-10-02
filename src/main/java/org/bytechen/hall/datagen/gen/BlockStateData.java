package org.bytechen.hall.datagen.gen;

import org.bytechen.hall.HallMod;
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
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.HallVineBlock;

import java.util.Objects;

/**
 * 数据生成器：用于自动生成方块状态（blockstates）和模型（models）的 JSON 文件。
 * 继承自 Forge 的 BlockStateProvider，在模组构建时会根据本类生成对应的资源文件。
 */
public class BlockStateData extends BlockStateProvider {

    /**
     * 构造方法，由 Forge 数据生成系统调用。
     *
     * @param output       数据输出的路径封装
     * @param exFileHelper 现有文件辅助器，用于判断文件是否已存在
     */
    public BlockStateData(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, HallMod.MODID, exFileHelper);
    }

    /**
     * 核心注册方法：在此处调用各种辅助方法，为模组中的每个方块生成对应的 blockstate 和 model。
     * 目前仅为 Hall Grass 方块生成了数据。
     * 如需为其他方块生成，只需在此方法中添加对应的调用即可。
     */
    @Override
    protected void registerStatesAndModels() {
        // 为自定义的草方块生成状态和模型（顶、底、侧纹理不同）
        grassBlockWithItem(RegisterBlock.HALL_GRASS_BLOCK.get());
        logBlockWithItem(RegisterBlock.HALL_PILLAR.get());
        simpleBlockWithItem(RegisterBlock.HALL_DIRT.get());
        simpleBlockWithItem(RegisterBlock.HALL_STONE.get());
        simpleBlockWithItem(RegisterBlock.HALL_PLANKS.get());
        // HALL_PLANKS 建筑方块系列
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
        // HALL_STONE 建筑方块系列（楼梯/台阶/石墙/按钮/压力板）
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

        // 王庭烬痕沙漠系列
        sandstoneBlockWithItem(RegisterBlock.HALL_SANDSTONE.get(), RegisterBlock.HALL_ASH_CUT_SANDSTONE.get());
        simpleBlockWithItem(RegisterBlock.HALL_ASH_SAND.get());
        sandstoneColumnBlockWithItem(RegisterBlock.HALL_ASH_CUT_SANDSTONE.get());
        sandstoneColumnBlockWithItem(RegisterBlock.HALL_ASH_SMOOTH_SANDSTONE.get());
        sandstoneColumnBlockWithItem(RegisterBlock.HALL_ASH_COLLAPSED_CHISELED_SANDSTONE.get());
        cactusBlockWithItem(RegisterBlock.HALL_ASH_CACTUS.get());
        crossBlockWithItem(RegisterBlock.HALL_ASH_DEAD_BUSH.get());

        // 血肉庭园维度只需通道方块，地形全部复用现有王庭方块族
        simpleBlockWithItem(RegisterBlock.FLESH_RIFT.get());

        // 若要为原木生成，使用 logBlockWithItem(...)
        // 等等
    }

    // ======================== 各种方块类型的辅助生成方法 ========================

    /**
     * 1. 普通完整方块（六面纹理相同）
     * 使用 cubeAll 模型，所有面使用同一张纹理。
     *
     * @param block 要生成的方块实例
     */
    protected void simpleBlockWithItem(Block block) {
        simpleBlock(block);                               // 生成 blockstate
        simpleBlockItem(block, cubeAll(block));           // 生成物品模型，直接复用方块模型
    }

    /**
     * 2. 原木/柱子类方块（RotatedPillarBlock）
     * 侧面使用方块自身的纹理，顶/底使用带 "_top" 后缀的纹理。
     * 支持方块旋转（根据放置方向）。
     *
     * @param block 要生成的柱子方块实例（必须为 RotatedPillarBlock）
     */
    protected void logBlockWithItem(Block block) {
        // 调用父类方法生成柱子的 blockstate（包含旋转逻辑）
        logBlock((net.minecraft.world.level.block.RotatedPillarBlock) block);
        // 生成物品模型：使用 cubeColumn，侧面纹理 = blockTexture(block)，顶底纹理 = 带 "_top" 后缀
        simpleBlockItem(block, models().cubeColumn(
                name(block),                       // 模型名称
                blockTexture(block),               // 侧面纹理
                extend(blockTexture(block), "_top") // 顶/底纹理
        ));
    }

    /**
     * 3. 交叉植物（如花、高草等，属于 CrossBlock）
     * 使用 cross 模型，并设置渲染类型为 cutout（透明裁剪）。
     * 物品栏使用 item/generated 模型。
     *
     * @param block 要生成的植物方块实例
     */
    protected void crossBlockWithItem(Block block) {
        // 方块模型：使用 cross，并指定 cutout 渲染类型
        simpleBlock(block, models().cross(name(block), blockTexture(block)).renderType("cutout"));
        // 物品模型：使用 generated，纹理作为 layer0
        itemModels().withExistingParent(name(block), "item/generated")
                .texture("layer0", blockTexture(block));
    }

    /**
     * 4. 王庭藤蔓（HallVineBlock）—— 支持上下双向生长、多段模型。
     * <p>
     * 根据 GROWTH_DIR（UP/DOWN）和 SECTION（BOTTOM/MIDDLE/TOP）组合，
     * 生成 6 种 cross 模型变体，纹理映射如下：
     * <ul>
     *   <li>UP + BOTTOM  → hall_vine_up 纹理</li>
     *   <li>UP + MIDDLE  → hall_vine_up 纹理</li>
     *   <li>UP + TOP     → hall_vine_up_top 纹理</li>
     *   <li>DOWN + BOTTOM → hall_vine_down_bottom 纹理</li>
     *   <li>DOWN + MIDDLE → hall_vine_down_middle 纹理</li>
     *   <li>DOWN + TOP    → hall_vine_down_top 纹理</li>
     * </ul>
     *
     * @param block 王庭藤蔓方块实例
     */
    protected void hallVineBlockWithItem(Block block) {
        // 纹理资源定位
        ResourceLocation texUp = extend(blockTexture(block), "_up");             // hall_vine_up
        ResourceLocation texUpTop = extend(blockTexture(block), "_up_top");   // hall_vine_up_top
        ResourceLocation texDownBottom = extend(blockTexture(block), "_down_bottom");
        ResourceLocation texDownMiddle = extend(blockTexture(block), "_down_middle");
        ResourceLocation texDownTop = extend(blockTexture(block), "_down_top");

        // 向上生长模型
        ModelFile upBottomModel = models().cross(name(block) + "_up_bottom", texUp).renderType("cutout");
        ModelFile upMiddleModel = models().cross(name(block) + "_up_middle", texUp).renderType("cutout");
        ModelFile upTopModel = models().cross(name(block) + "_up_top", texUpTop).renderType("cutout");

        // 向下生长模型
        ModelFile downBottomModel = models().cross(name(block) + "_down_bottom", texDownBottom).renderType("cutout");
        ModelFile downMiddleModel = models().cross(name(block) + "_down_middle", texDownMiddle).renderType("cutout");
        ModelFile downTopModel = models().cross(name(block) + "_down_top", texDownTop).renderType("cutout");

        // 根据 GROWTH_DIR + SECTION 选择模型
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
                    case BOTTOM -> downMiddleModel;  // 生长端 → middle
                    case MIDDLE -> downBottomModel;  // 茎段   → bottom
                    case TOP -> downTopModel;        // 附着端 → top
                };
            };

            return ConfiguredModel.builder().modelFile(model).build();
        });

        // 物品模型：使用向上生长的顶端形态
        itemModels().withExistingParent(name(block), "item/generated")
                .texture("layer0", texUpTop);
    }

    /**
     * 4. 台阶方块（SlabBlock）
     * 生成 bottom、top、double 三种变体，纹理取自对应的完整方块。
     *
     * @param slab     台阶方块实例
     * @param fullBlock 对应的完整方块实例（用于获取纹理）
     */
    protected void slabBlockWithItem(net.minecraft.world.level.block.SlabBlock slab, Block fullBlock) {
        // 生成 blockstate：包含 bottom、top、double 变体
        slabBlock(slab, blockTexture(fullBlock), blockTexture(fullBlock));
        // 生成物品模型：使用 slab 模型
        simpleBlockItem(slab, models().slab(
                name(slab),
                blockTexture(fullBlock),
                blockTexture(fullBlock),
                blockTexture(fullBlock)
        ));
    }

    /**
     * 5. 楼梯方块（StairBlock）
     * 生成各种朝向的楼梯模型，纹理取自对应的完整方块。
     *
     * @param stair     楼梯方块实例
     * @param fullBlock 对应的完整方块实例
     */
    protected void stairsBlockWithItem(net.minecraft.world.level.block.StairBlock stair, Block fullBlock) {
        // 生成 blockstate（含朝向、形状等）
        stairsBlock(stair, blockTexture(fullBlock));
        // 生成物品模型：使用 stairs 模型
        simpleBlockItem(stair, models().stairs(
                name(stair),
                blockTexture(fullBlock),
                blockTexture(fullBlock),
                blockTexture(fullBlock)
        ));
    }

    /**
     * 6. 墙方块（WallBlock）
     * 生成围墙的各种连接状态，物品栏使用 wall_inventory 模型。
     *
     * @param wall      墙方块实例
     * @param fullBlock 对应的完整方块实例（用于获取纹理）
     */
    protected void wallBlockWithItem(net.minecraft.world.level.block.WallBlock wall, Block fullBlock) {
        // 生成 blockstate（包含柱、侧连接、顶帽等）
        wallBlock(wall, blockTexture(fullBlock));
        // 生成物品模型：使用 wall_inventory
        simpleBlockItem(wall, models().wallInventory(
                name(wall) + "_inventory",
                blockTexture(fullBlock)
        ));
    }

    /**
     * 7. 栅栏方块（FenceBlock）
     * 生成栅栏的连接模型，物品栏使用 fence_inventory 模型。
     *
     * @param fence 栅栏方块实例
     * @param plank 用于纹理的木板方块（通常是与栅栏配套的木板）
     */
    protected void fenceBlockWithItem(net.minecraft.world.level.block.FenceBlock fence, Block plank) {
        // 生成 blockstate（含柱、连接、侧边等）
        fenceBlock(fence, blockTexture(plank));
        // 生成物品模型：使用 fence_inventory
        simpleBlockItem(fence, models().fenceInventory(
                name(fence) + "_inventory",
                blockTexture(plank)
        ));
    }

    /**
     * 8. 玻璃板/铁栏杆（IronBarsBlock）
     * 生成板状方块的连接模型，顶部额外加 "_pane_top" 纹理。
     * 物品栏使用 item/generated 模型。
     *
     * @param pane  板方块实例（如 IronBarsBlock）
     * @param glass 对应的玻璃或纹理方块（用于获取基础纹理）
     */
    protected void paneBlockWithItem(net.minecraft.world.level.block.IronBarsBlock pane, Block glass) {
        // 生成 blockstate（含各方向连接）
        paneBlock(pane, blockTexture(glass), extend(blockTexture(glass), "_pane_top"));
        // 物品模型：使用 generated
        itemModels().withExistingParent(name(pane), "item/generated")
                .texture("layer0", blockTexture(glass));
    }

    /**
     * 9. 栅栏门（FenceGateBlock）
     * 生成栅栏门的 blockstate（含朝向、开关、墙连接），物品栏使用 fence_gate 模型。
     *
     * @param gate  栅栏门方块实例
     * @param plank 用于纹理的木板方块
     */
    protected void fenceGateBlockWithItem(FenceGateBlock gate, Block plank) {
        fenceGateBlock(gate, blockTexture(plank));
        simpleBlockItem(gate, models().fenceGate(
                name(gate),
                blockTexture(plank)
        ));
    }

    /**
     * 10. 门（DoorBlock）
     * 生成门的 blockstate（含朝向、半高、铰链），使用 cutout 渲染类型。
     * 纹理复用木板纹理。
     *
     * @param door  门方块实例
     * @param plank 用于纹理的木板方块
     */
    protected void doorBlockWithItem(DoorBlock door, Block plank) {
        doorBlockWithRenderType(door, blockTexture(plank), blockTexture(plank), "cutout");
        // 门的物品模型使用平面 generated 模型（和原版门一致），纹理用专用的门贴图
        itemModels().withExistingParent(name(door), "item/generated")
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "item/" + name(door)));
    }

    /**
     * 11. 活板门（TrapDoorBlock）
     * 生成活板门的 blockstate（含朝向、开关、半高），使用 cutout 渲染类型。
     * 物品栏使用 trapdoor_bottom 模型。
     *
     * @param trapdoor 活板门方块实例
     * @param plank    用于纹理的木板方块
     */
    protected void trapdoorBlockWithItem(TrapDoorBlock trapdoor, Block plank) {
        trapdoorBlockWithRenderType(trapdoor, blockTexture(plank), true, "cutout");
        simpleBlockItem(trapdoor, models().trapdoorBottom(
                name(trapdoor) + "_bottom",
                blockTexture(plank)
        ));
    }

    /**
     * 12. 按钮（ButtonBlock）
     * 生成按钮的 blockstate（含按压/释放状态），物品栏使用 button_inventory 模型。
     *
     * @param button 按钮方块实例
     * @param plank  用于纹理的木板方块
     */
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

    /**
     * 13. 压力板（PressurePlateBlock）
     * 生成压力板的 blockstate（含受压/释放状态）。
     *
     * @param plate 压力板方块实例
     * @param plank 用于纹理的木板方块
     */
    protected void pressurePlateBlockWithItem(PressurePlateBlock plate, Block plank) {
        ResourceLocation tex = blockTexture(plank);
        ModelFile plateModel = models().pressurePlate(name(plate), tex);
        ModelFile plateDownModel = models().pressurePlateDown(name(plate) + "_down", tex);
        pressurePlateBlock(plate, plateModel, plateDownModel);
        simpleBlockItem(plate, plateModel);
    }

    /**
     * 9. 自定义草方块（专门为本模组设计，顶、底、侧纹理各不相同）
     * 约定纹理文件命名：
     *   - 侧面：方块注册名.png（例如 hall_grass_block.png）
     *   - 顶面：方块注册名 + "_top".png（例如 hall_grass_block_top.png）
     *   - 底面：方块注册名 + "_bottom".png（例如 hall_grass_block_bottom.png）
     * 生成 cubeBottomTop 模型，并同时生成物品模型。
     *
     * @param block 要生成的草方块实例
     */
    protected void grassBlockWithItem(Block block) {
        ResourceLocation base = blockTexture(block); // 基础纹理，通常为 modid:block/方块名
        // 按约定拼接纹理路径
        ResourceLocation side = base;                      // 侧面纹理（和基础纹理相同）
        ResourceLocation top = extend(base, "_top");       // 顶面纹理（加 _top）
        ResourceLocation bottom = extend(base, "_bottom"); // 底面纹理（加 _bottom）

        // 生成方块模型：使用 cubeBottomTop（顶底不同）
        simpleBlock(block, models().cubeBottomTop(name(block), side, bottom, top));
        // 生成物品模型：直接复用该方块模型
        simpleBlockItem(block, models().cubeBottomTop(name(block), side, bottom, top));
    }

    /**
     * 14. 砂岩方块（顶/底与侧面使用不同纹理）
     * 侧面复用指定的砂岩纹理方块，顶/底使用本方块 + "_top"/"_bottom" 后缀。
     *
     * @param block     砂岩方块实例
     * @param sideBlock 提供侧面纹理的方块实例
     */
    protected void sandstoneBlockWithItem(Block block, Block sideBlock) {
        ResourceLocation side = blockTexture(sideBlock);
        ResourceLocation top = extend(blockTexture(block), "_top");
        ResourceLocation bottom = extend(blockTexture(block), "_bottom");
        simpleBlock(block, models().cubeBottomTop(name(block), side, bottom, top));
        simpleBlockItem(block, models().cubeBottomTop(name(block), side, bottom, top));
    }

    /**
     * 14b. 砂岩类变体方块（切制/雕纹等）
     * 顶/底使用 hall_sandstone_top/bottom，四周使用方块自身纹理。
     *
     * @param block 砂岩变体方块实例
     */
    protected void sandstoneColumnBlockWithItem(Block block) {
        ResourceLocation side = blockTexture(block);
        ResourceLocation top = extend(blockTexture(RegisterBlock.HALL_SANDSTONE.get()), "_top");
        ResourceLocation bottom = extend(blockTexture(RegisterBlock.HALL_SANDSTONE.get()), "_bottom");
        simpleBlock(block, models().cubeBottomTop(name(block), side, bottom, top));
        simpleBlockItem(block, models().cubeBottomTop(name(block), side, bottom, top));
    }

    /**
     * 15. 仙人掌类方块（侧面/底面/顶面纹理不同）
     * <p>
     * 模型结构对齐原版 {@code minecraft:block/cactus}：中心一个 16×16×16 的柱体
     * （只出面 up/down，负责顶面与底面的贴图），外加南北、东西两片 1 像素内缩的侧片。
     * <p>
     * <b>为什么不用 {@code withExistingParent(name, "block/cactus")：</b>
     * 本模组这三张贴图在四周各留了 <b>1 像素透明留白</b>（实际可见像素为 x/y = 1..15，
     * 即 14×14 内容 + 1 像素内边距，和原版 16×16 满幅贴图不同）。
     * 原版父模型六面 UV 都是 {@code [0,0,16,16]}，会把留白一起铺开：
     * <ul>
     *   <li>侧面 {@code [0,0,16,16]}：南北两片与东西两片之间各漏出 1 像素缝隙，
     *       直接看进柱体内部 → 四周一圈黑边；</li>
     *   <li>顶/底 {@code [0,0,16,16]}：顶面外围一圈透明环直接露出柱体内部暗面 → 顶底也有黑边。</li>
     * </ul>
     * 所以这里显式写 UV，把每一面都映射到贴图真正有像素的区域内：
     * 侧面 {@code [1,0,15,16]}（横向内缩 1），顶/底 {@code [1,1,15,15]}（四周内缩 1）。
     *
     * @param block 仙人掌方块实例
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
                // 中心柱体：只出面 up / down，负责顶底贴图（UV 四周内缩 1 像素，避开透明内边距）
                .element()
                    .from(0.0F, 0.0F, 0.0F)
                    .to(16.0F, 16.0F, 16.0F)
                    .face(Direction.DOWN).uvs(1.0F, 1.0F, 15.0F, 15.0F).texture("#bottom").cullface(Direction.DOWN).end()
                    .face(Direction.UP).uvs(1.0F, 1.0F, 15.0F, 15.0F).texture("#top").cullface(Direction.UP).end()
                .end()
                // 南北侧片：UV 横向内缩 1 像素，正好落在不透明像素上
                .element()
                    .from(0.0F, 0.0F, 1.0F)
                    .to(16.0F, 16.0F, 15.0F)
                    .face(Direction.NORTH).uvs(1.0F, 0.0F, 15.0F, 16.0F).texture("#side").end()
                    .face(Direction.SOUTH).uvs(1.0F, 0.0F, 15.0F, 16.0F).texture("#side").end()
                .end()
                // 东西侧片：UV 横向内缩 1 像素
                .element()
                    .from(1.0F, 0.0F, 0.0F)
                    .to(15.0F, 16.0F, 16.0F)
                    .face(Direction.WEST).uvs(1.0F, 0.0F, 15.0F, 16.0F).texture("#side").end()
                    .face(Direction.EAST).uvs(1.0F, 0.0F, 15.0F, 16.0F).texture("#side").end()
                .end();

        simpleBlock(block, cactusModel);
        simpleBlockItem(block, cactusModel);
    }

    /**
     * 封装：树叶方块
     * 树叶需要特殊的渲染类型以支持透明部分
     */
    private void leavesBlockWithItem(Block block) {
        // 1. 生成方块模型：继承自 minecraft:block/leaves
        // 这样可以确保它在不同画质设置下表现正确
        ModelFile leavesModel = models().cubeAll(name(block), blockTexture(block))
                .renderType("cutout"); // 关键：处理透明像素
        simpleBlock(block, leavesModel);
        // 2. 生成物品模型：直接继承方块模型
        simpleBlockItem(block, leavesModel);
    }

    /**
     * 普通的门
     * @param door
     * @param bottomTex
     * @param topTex
     */

    protected void doorBlockWithItem(DoorBlock door, ResourceLocation bottomTex, ResourceLocation topTex) {
        doorBlockWithRenderType(door, bottomTex, topTex, "cutout");
        // 物品模型沿用原逻辑（使用一个独立纹理）
        itemModels().withExistingParent(name(door), "item/generated")
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "item/" + name(door)));
    }

    // ======================== 工具方法 ========================

    /**
     * 获取方块的注册名（即路径部分，不含命名空间）。
     *
     * @param block 方块实例
     * @return 方块注册路径（例如 "hall_grass"）
     */
    protected String name(Block block) {
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block)).getPath();
    }

    /**
     * 给一个 ResourceLocation 的路径追加后缀，命名空间保持不变。
     * 例如：extend(new ResourceLocation("modid:block/test"), "_top") -> modid:block/test_top
     *
     * @param rl     原始 ResourceLocation
     * @param suffix 要追加的后缀（如 "_top"）
     * @return 新的 ResourceLocation
     */
    protected ResourceLocation extend(ResourceLocation rl, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), rl.getPath() + suffix);
    }
}