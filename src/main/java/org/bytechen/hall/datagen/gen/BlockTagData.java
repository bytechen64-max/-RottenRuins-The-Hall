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

        // ========== HALL_STONE 建筑方块 ==========

        // 工具类型：石质方块使用镐
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(RegisterBlock.HALL_STONE.get())
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
    }
}
