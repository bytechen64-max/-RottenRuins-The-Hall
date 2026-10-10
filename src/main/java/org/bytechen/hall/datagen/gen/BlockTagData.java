package org.bytechen.hall.datagen.gen;

import net.minecraft.tags.BlockTags;
import org.bytechen.hall.HallMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.bytechen.hall.overworld.registry.RegisterBlock;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class BlockTagData extends BlockTagsProvider {
    public BlockTagData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                        @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, HallMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // ========== 矿石 ==========

        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(RegisterBlock.DOMITE_MINERAL.get());
        tag(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(RegisterBlock.DOMITE_MINERAL.get());

        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(RegisterBlock.DOMERITE_MINERAL.get());
        tag(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(RegisterBlock.DOMERITE_MINERAL.get());

        // ========== HALL_PLANKS 建筑方块 ==========

        // 工具类型：木板类方块使用斧
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(RegisterBlock.HALL_PLANKS.get())
                .add(RegisterBlock.HALL_STAIRS.get())
                .add(RegisterBlock.HALL_SLAB.get())
                .add(RegisterBlock.HALL_FENCE.get())
                .add(RegisterBlock.HALL_FENCE_GATE.get())
                .add(RegisterBlock.HALL_DOOR.get())
                .add(RegisterBlock.HALL_TRAPDOOR.get())
                .add(RegisterBlock.HALL_BUTTON.get())
                .add(RegisterBlock.HALL_PRESSURE_PLATE.get());

        // 原版木质方块标签（影响掠夺者生成、村庄结构等）
        tag(BlockTags.PLANKS).add(RegisterBlock.HALL_PLANKS.get());
        tag(BlockTags.WOODEN_STAIRS).add(RegisterBlock.HALL_STAIRS.get());
        tag(BlockTags.WOODEN_SLABS).add(RegisterBlock.HALL_SLAB.get());
        tag(BlockTags.WOODEN_FENCES).add(RegisterBlock.HALL_FENCE.get());
        tag(BlockTags.FENCE_GATES).add(RegisterBlock.HALL_FENCE_GATE.get());
        tag(BlockTags.WOODEN_DOORS).add(RegisterBlock.HALL_DOOR.get());
        tag(BlockTags.WOODEN_TRAPDOORS).add(RegisterBlock.HALL_TRAPDOOR.get());
        tag(BlockTags.WOODEN_BUTTONS).add(RegisterBlock.HALL_BUTTON.get());
        tag(BlockTags.WOODEN_PRESSURE_PLATES).add(RegisterBlock.HALL_PRESSURE_PLATE.get());

        // 栅栏连接标签
        tag(BlockTags.FENCES).add(RegisterBlock.HALL_FENCE.get());

        // 通用建筑方块标签 —— 原版把「所有」楼梯 / 台阶 / 门 / 活板门都登记在这里，
        // 模组方块漏登记会让别的模组（以及部分原版逻辑）按标签找方块时找不到它。
        tag(BlockTags.STAIRS).add(RegisterBlock.HALL_STAIRS.get());
        tag(BlockTags.SLABS).add(RegisterBlock.HALL_SLAB.get());
        tag(BlockTags.DOORS).add(RegisterBlock.HALL_DOOR.get());
        tag(BlockTags.TRAPDOORS).add(RegisterBlock.HALL_TRAPDOOR.get());

        // ========== HALL_STONE 建筑方块 ==========

        // 工具类型：石质方块使用镐
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(RegisterBlock.HALL_STONE.get())
                .add(RegisterBlock.HALL_PILLAR.get())
                .add(RegisterBlock.HALL_STONE_STAIRS.get())
                .add(RegisterBlock.HALL_STONE_SLAB.get())
                .add(RegisterBlock.HALL_STONE_WALL.get())
                .add(RegisterBlock.HALL_STONE_BUTTON.get())
                .add(RegisterBlock.HALL_STONE_PRESSURE_PLATE.get());

        // 通用建筑方块标签
        tag(BlockTags.STAIRS).add(RegisterBlock.HALL_STONE_STAIRS.get());
        tag(BlockTags.SLABS).add(RegisterBlock.HALL_STONE_SLAB.get());
        tag(BlockTags.WALLS).add(RegisterBlock.HALL_STONE_WALL.get());
        tag(BlockTags.BUTTONS).add(RegisterBlock.HALL_STONE_BUTTON.get());
        tag(BlockTags.STONE_BUTTONS).add(RegisterBlock.HALL_STONE_BUTTON.get());
        tag(BlockTags.STONE_PRESSURE_PLATES).add(RegisterBlock.HALL_STONE_PRESSURE_PLATE.get());

        // ========== 王庭遗迹自然方块 ==========

        // 工具类型：草方块 / 泥土 / 沙子系用锹，原木 / 树叶 / 藤蔓用斧
        tag(BlockTags.MINEABLE_WITH_SHOVEL)
                .add(RegisterBlock.HALL_GRASS_BLOCK.get())
                .add(RegisterBlock.HALL_DIRT.get())
                .add(RegisterBlock.HALL_ASH_SAND.get());

        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(RegisterBlock.HALL_LOG.get())
                .add(RegisterBlock.HALL_LEAVES.get())
                .add(RegisterBlock.HALL_VINE.get());

        // 原木标签：影响原版营火 / 烟熏炉 / 烧木炭 / 燃料等一整票行为。
        // 原版的 logs.json 里引用了 #minecraft:logs_that_burn，所以只登记后者其实也能进 logs；
        // 这里两条都显式登记，避免以后原版改动引用关系时静默失效。
        tag(BlockTags.LOGS).add(RegisterBlock.HALL_LOG.get());
        tag(BlockTags.LOGS_THAT_BURN).add(RegisterBlock.HALL_LOG.get());

        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(RegisterBlock.HALL_SANDSTONE.get())
                .add(RegisterBlock.HALL_ASH_CUT_SANDSTONE.get())
                .add(RegisterBlock.HALL_ASH_SMOOTH_SANDSTONE.get())
                .add(RegisterBlock.HALL_ASH_COLLAPSED_CHISELED_SANDSTONE.get());

        // 沙子：让模组沙子被原版 / 其它模组按 #minecraft:sand 识别
        // （这也是王庭烬痕仙人掌 / 枯灌木的土壤判定依据）
        tag(BlockTags.SAND).add(RegisterBlock.HALL_ASH_SAND.get());

        // 树叶 / 藤蔓标签，影响渲染与部分原版逻辑
        tag(BlockTags.LEAVES).add(RegisterBlock.HALL_LEAVES.get());
        tag(BlockTags.REPLACEABLE_BY_TREES)
                .add(RegisterBlock.HALL_GRASS.get())
                .add(RegisterBlock.HALL_FLOWER.get())
                .add(RegisterBlock.HALL_ASH_DEAD_BUSH.get());
        tag(BlockTags.SWORD_EFFICIENT)
                .add(RegisterBlock.HALL_GRASS.get())
                .add(RegisterBlock.HALL_FLOWER.get())
                .add(RegisterBlock.HALL_ASH_DEAD_BUSH.get());
    }
}
