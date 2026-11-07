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

/**
 * 死亡核心（{@code death_core}）—— 第一个走「方块版 mask 附加着色器层」的方块。
 *
 * <h3>渲染契约</h3>
 * <pre>
 * 区块 mesh：方块本体（death_core.png，实心 cube_all）写颜色 + 写深度
 *      └─ BER（DeathCoreRenderer，每帧）
 *            └─ BlockMaskOverlayRenderer 用方块模型的 quad 再画一遍
 *                  · UV 重映射到 death_core_mask.png
 *                  · RenderType：LEQUAL + polygon offset + TRANSLUCENT + 只写颜色
 *                  · 效果：death_core（ShaderToy "Ghosts" 体积光，只画在 mask 白层里）
 * </pre>
 *
 * <p>本体贴图保持深色，渲染上只有 <b>mask 白层窗口内</b> 会被替换成 Ghosts 体积光
 * （窗口内 alpha = 1，原贴图不透出来），窗口外的本体像素照常可见；窗口形状完全
 * 由遮罩决定。该表面层在三种形态下都会显示：世界里的方块（BER）、手持/物品栏/
 * 掉落物的方块物品（{@code BlockMaskLayerItem} + 物品模型 quad）。</p>
 *
 * <h3>为什么层列表是 static final</h3>
 * <p>{@link org.bytechen.rottenruins.api.mask.BlockMaskLayerProvider#maskLayers} 每帧
 * 每个可见方块都会被调用，热路径里不该分配。参数改动直接改这个常量即可，不需要
 * 碰模型 JSON（对比物品侧：物品既可以走 JSON 也可以走接口，方块 v1 只走接口）。</p>
 */
public class DeathCoreBlock extends Block implements EntityBlock {

    /** 遮罩贴图（方块图集里的 sprite 名，放在 {@code textures/block/} 下即自动进图集）。 */
    public static final ResourceLocation MASK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "block/death_core_mask");

    /**
     * 死亡核心的 mask 层配置。
     *
     * <ul>
     *   <li>{@code color} —— 冷青白；death_core 的光源由着色器自己算，这里是染色，
     *       alpha 会乘进整体强度。</li>
     *   <li>{@code intensity} 1.6 —— 加法层，稍微压过本体亮度让核心"烧"出来。</li>
     *   <li>{@code opacity} 1.0 —— 整体强度旋钮。</li>
     *   <li>{@code speed} 1.0 —— 动画速度（0 = 静止；death_core 的 time 也乘在这里）。</li>
     *   <li>{@code order} 100 —— 与其他层叠加时的绘制顺序（升序 = 越晚画）。</li>
     * </ul>
     */
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
                // 自身发光，便于在昏暗处看见；渲染上不依赖光照贴图。
                .lightLevel(state -> 10)
                .sound(SoundType.SCULK));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeathCoreBlockEntity(pos, state);
    }
}
