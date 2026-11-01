package org.bytechen.rottenruins.datagen.gen.corruption;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption.CorruptionVariableHeightCropBlock;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;

import java.util.Objects;

/**
 * 腐化系方块的 blockstate / 方块模型 / 方块物品模型生成器（从 corruption 工程
 * {@code datagen/gen/BlockStateData} 的相关方法移植）。
 *
 * <p>覆盖"特殊格式"的几类方块：</p>
 * <ul>
 *   <li>残骸 ×9：六向 <code>facing</code> + 手写十字交叉模型（两片旋转 45° 的平面 + 底面）</li>
 *   <li>腐染矮草：cutout cross 模型</li>
 *   <li>腐染经络：<code>age</code> + <code>section(bottom/middle/top)</code> 三段 cross 模型</li>
 *   <li>血肉温床：水平朝向 + 手写模型 {@code models/block/fleshbed.json}</li>
 * </ul>
 */
@SuppressWarnings("removal")
public class CorruptionBlockStateData extends BlockStateProvider {

    public CorruptionBlockStateData(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, HallMod.MODID, exFileHelper);
    }

    /**
     * DataGenerator 按 provider 名字去重（默认名字同为 "Block States: rotten_ruins"），
     * 这里必须给腐化系生成器一个独立名字，否则 runData 会报 Duplicate provider。
     */
    @Override
    public String getName() {
        return "Corruption Block States: " + HallMod.MODID;
    }

    @Override
    protected void registerStatesAndModels() {
        // 腐染矮草（cross）
        this.corruptionGrassWithItem(CorruptionBlocks.CORRUPTION_GRASS.get());
        // 腐染经络（三段 cross + age/section 变体）
        this.corruptionCropBlockWithItem(CorruptionBlocks.VARIABLE_HEIGHT_CROP.get());
        // 血肉温床（手写模型 + 水平朝向）
        this.horizontalBlockStateWithItem(CorruptionBlocks.FLESHBED.get());

        // 残骸：腐化 / 同化 / 重构 × 小 / 中 / 大
        this.directionComplexBlockWithItem(CorruptionBlocks.HOST_REMAINS_SMALL.get());
        this.directionComplexBlockWithItem(CorruptionBlocks.HOST_REMAINS_MEDIUM.get());
        this.directionComplexBlockWithItem(CorruptionBlocks.HOST_REMAINS_LARGE.get());
        this.directionComplexBlockWithItem(CorruptionBlocks.DEGENERATE_REMAINS_SMALL.get());
        this.directionComplexBlockWithItem(CorruptionBlocks.DEGENERATE_REMAINS_MEDIUM.get());
        this.directionComplexBlockWithItem(CorruptionBlocks.DEGENERATE_REMAINS_LARGE.get());
        this.directionComplexBlockWithItem(CorruptionBlocks.REFACTORED_REMAINS_SMALL.get());
        this.directionComplexBlockWithItem(CorruptionBlocks.REFACTORED_REMAINS_MEDIUM.get());
        this.directionComplexBlockWithItem(CorruptionBlocks.REFACTORED_REMAINS_LARGE.get());
    }

    // ==================== 腐染经络 ====================

    // ==================== corruption grass (two blocks tall) ====================

    private void corruptionGrassWithItem(net.minecraft.world.level.block.Block block) {
        net.minecraft.resources.ResourceLocation texture = blockTexture(block);
        net.minecraftforge.client.model.generators.ModelFile cross =
                models().cross(name(block), texture).renderType("cutout");

        net.minecraft.world.level.block.state.properties.EnumProperty<
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf> half =
                net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF;

        getVariantBuilder(block)
                .partialState()
                .with(half, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                .modelForState().modelFile(cross).addModel()
                .partialState()
                .with(half, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                .modelForState().modelFile(cross).addModel();

        itemModels().withExistingParent(name(block), mcLoc("item/generated"))
                .texture("layer0", texture);
    }

    private void corruptionCropBlockWithItem(Block block) {
        ResourceLocation bottomTex = extend(blockTexture(block), "_bottom");
        ResourceLocation middleTex = extend(blockTexture(block), "_middle");
        ResourceLocation topTex = extend(blockTexture(block), "_top");

        ModelFile bottomModel = models().cross(name(block) + "_bottom", bottomTex).renderType("cutout");
        ModelFile middleModel = models().cross(name(block) + "_middle", middleTex).renderType("cutout");
        ModelFile topModel = models().cross(name(block) + "_top", topTex).renderType("cutout");

        // 忽略 AGE，模型只取决于 SECTION
        getVariantBuilder(block).forAllStates(state -> {
            CorruptionVariableHeightCropBlock.StemSection section =
                    state.getValue(CorruptionVariableHeightCropBlock.SECTION);
            ModelFile model = switch (section) {
                case BOTTOM -> bottomModel;
                case MIDDLE -> middleModel;
                case TOP -> topModel;
            };
            return ConfiguredModel.builder().modelFile(model).build();
        });

        itemModels().withExistingParent(name(block), "item/generated").texture("layer0", topTex);
    }

    // ==================== 腐染矮草 ====================

    private void crossBlockWithItem(Block block) {
        simpleBlock(block, models().cross(name(block), blockTexture(block)).renderType("cutout"));
        itemModels().withExistingParent(name(block), "item/generated")
                .texture("layer0", blockTexture(block));
    }

    // ==================== 残骸 ====================

    /**
     * 残骸类方块：六向 facing + 手写十字交叉模型 + 对应物品模型。
     *
     * @param block 残骸方块
     */
    private void directionComplexBlockWithItem(Block block) {
        ResourceLocation baseTex = blockTexture(block);
        ResourceLocation sideTex = extend(baseTex, "_side");
        String path = "block/" + name(block);
        ModelFile model = models().getBuilder(path)
                .renderType("cutout")
                .texture("4", baseTex)
                .texture("6", sideTex)
                .texture("particle", baseTex)
                // 底面 (Y=0.01, 厚度 0)
                .element()
                .from(0, 0.01f, 0).to(16, 0.01f, 16)
                .face(Direction.UP).uvs(16, 16, 0, 0).texture("#4").end()
                .face(Direction.DOWN).uvs(16, 0, 0, 16).texture("#4").end()
                .end()
                // 第一个交叉面（绕 Y 轴，原点 (8,0,8)，+45°）
                .element()
                .from(0, 0, 8).to(16, 16, 8)
                .rotation()
                .origin(8, 0, 8)
                .axis(Direction.Axis.Y)
                .angle(45)
                .rescale(true)
                .end()
                .face(Direction.NORTH).uvs(0, 0, 16, 16).texture("#6").end()
                .face(Direction.SOUTH).uvs(0, 0, 16, 16).texture("#6").end()
                .end()
                // 第二个交叉面（-45°）
                .element()
                .from(0, 0, 8).to(16, 16, 8)
                .rotation()
                .origin(8, 0, 8)
                .axis(Direction.Axis.Y)
                .angle(-45)
                .rescale(true)
                .end()
                .face(Direction.NORTH).uvs(0, 0, 16, 16).texture("#6").end()
                .face(Direction.SOUTH).uvs(0, 0, 16, 16).texture("#6").end()
                .end();

        // BlockState：根据 FACING 旋转模型
        getVariantBuilder(block).forAllStates(state -> {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            int xRot = 0;
            int yRot = 0;
            switch (facing) {
                case DOWN -> xRot = 180;
                case UP -> xRot = 0;
                case NORTH -> xRot = 90;
                case SOUTH -> {
                    xRot = 90;
                    yRot = 180;
                }
                case WEST -> {
                    xRot = 90;
                    yRot = 270;
                }
                case EAST -> {
                    xRot = 90;
                    yRot = 90;
                }
            }
            return ConfiguredModel.builder()
                    .modelFile(model)
                    .rotationX(xRot)
                    .rotationY(yRot)
                    .build();
        });

        // 物品模型：直接继承方块模型
        itemModels().withExistingParent(name(block), new ResourceLocation(HallMod.MODID, path));
    }

    // ==================== 血肉温床 ====================

    /**
     * 仅生成水平朝向（北/东/南/西）的 blockstate JSON，不生成方块模型文件；
     * 模型文件由 corruption 工程手写资源提供（{@code models/block/fleshbed.json}）。
     */
    private void horizontalBlockStateOnly(Block block) {
        ResourceLocation modelLocation = new ResourceLocation(HallMod.MODID, "block/" + name(block));
        ModelFile existingModel = new ModelFile.UncheckedModelFile(modelLocation);

        getVariantBuilder(block).forAllStates(state -> {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            int yRot = switch (facing) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
            return ConfiguredModel.builder().modelFile(existingModel).rotationY(yRot).build();
        });
    }

    private void horizontalBlockStateWithItem(Block block) {
        horizontalBlockStateOnly(block);
        ResourceLocation modelLocation = new ResourceLocation(HallMod.MODID, "block/" + name(block));
        ModelFile existingModel = new ModelFile.UncheckedModelFile(modelLocation);
        itemModels().getBuilder(name(block)).parent(existingModel);
    }

    // ==================== 工具方法 ====================

    private String name(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block) == null
                ? ""
                : Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block)).getPath();
    }

    private ResourceLocation extend(ResourceLocation rl, String suffix) {
        return new ResourceLocation(rl.getNamespace(), rl.getPath() + suffix);
    }
}
