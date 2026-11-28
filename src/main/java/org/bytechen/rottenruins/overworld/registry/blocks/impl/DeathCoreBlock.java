package org.bytechen.rottenruins.overworld.registry.blocks.impl;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import org.bytechen.rottenruins.overworld.registry.blockentity.DeathCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** 死亡核心方块：本体写颜色+深度，BER 用方块 quad 按 mask 白层窗口补画 Ghosts 体积光（层列表 static final，热路径不分配）。 */
public class DeathCoreBlock extends Block implements EntityBlock {

    public static final ResourceLocation MASK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "block/death_core_mask");

    /** 死亡核心 mask 层：intensity 1.6 的加法层稍压过本体亮度让核心"烧"出来，order 100 = 越晚画。 */
    public static final List<MaskLayerSpec> MASK_LAYERS = List.of(
            MaskLayerSpec.builder(MaskLayerSpec.EFFECT_DEATH_CORE)
                    .mask(MASK_TEXTURE)
                    .color(0xFFB8ECFF)
                    .intensity(1.6F)
                    .opacity(1.0F)
                    .speed(1.0F)
                    .phase(0.0F)
                    .order(100.0F)
                    .build());

    public DeathCoreBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                // 比黑曜石软一点：能挖，但依然需要正确的工具（copy(OBSIDIAN) 自带 requiresCorrectToolForDrops）。
                .strength(3.0F, 1200.0F)
                .lightLevel(state -> 10)
                .sound(SoundType.SCULK));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeathCoreBlockEntity(pos, state);
    }
}
