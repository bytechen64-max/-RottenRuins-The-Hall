package org.bytechen.hall.datagen.gen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.bytechen.hall.HallMod;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * 物品标签数据生成 —— 让王庭木 / 王庭石真正「能按原版配方用」。
 *
 * <p><b>为什么必须有这个类？</b>
 * 原版的方块标签（{@code data/minecraft/tags/blocks/...}）和物品标签
 * （{@code data/minecraft/tags/items/...}）是<b>两张互不相通</b>的表，
 * Forge 也不会自动把方块标签镜像成物品标签。
 *
 * <p>而原版<b>配方</b>里的 {@code "tag": "..."} 走的是<b>物品</b>标签。也就是说，
 * 只要只登记方块标签，下面这些原版配方全都认不出王庭木板 / 王庭原木：
 * <ul>
 *   <li>{@code #minecraft:planks} —— 木棍、工作台、箱子、床、书架、木桶、讲台、
 *       唱片机、音符盒、活塞、盾牌、弓…以及<b>全套木制工具</b>（木镐 / 木斧…）</li>
 *   <li>{@code #minecraft:logs_that_burn} —— 烧<b>木炭</b>；{@code #minecraft:logs} —— 营火 / 烟熏炉</li>
 *   <li>{@code #minecraft:wooden_slabs} —— 木桶 / 堆肥桶 / 讲台 / 阳光探测器</li>
 * </ul>
 * 除此之外，{@code AbstractFurnaceBlockEntity} 的<b>燃料</b>判定同样是读物品标签
 * （原木 300 tick、木板 300 tick、木台阶 150 tick…），所以物品标签还决定了
 * 王庭木头能不能当燃料烧。
 *
 * <p>这里用 {@code copy(...)} 把上面 {@link BlockTagData} 里登记过的原版方块标签
 * 原样复制一份到同名物品标签，保证两张表永远一致。
 */
public class ItemTagData extends ItemTagsProvider {

    public ItemTagData(PackOutput output,
                       CompletableFuture<TagsProvider.TagLookup<Block>> blockTags,
                       CompletableFuture<HolderLookup.Provider> lookupProvider,
                       @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, HallMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // ========== 王庭木：原版木质物品标签 ==========

        // 木板 —— 解锁木棍 / 工作台 / 箱子 / 床 / 书架 / 木桶 / 全套木制工具等 39 条原版配方
        copy(BlockTags.PLANKS, ItemTags.PLANKS);

        // 原木 —— 解锁烧木炭（logs_that_burn）、营火 / 烟熏炉（logs），以及燃料
        copy(BlockTags.LOGS, ItemTags.LOGS);
        copy(BlockTags.LOGS_THAT_BURN, ItemTags.LOGS_THAT_BURN);

        // 木质建筑方块 —— 解锁木桶 / 堆肥桶 / 讲台等配方，以及对应燃料时长
        copy(BlockTags.WOODEN_STAIRS, ItemTags.WOODEN_STAIRS);
        copy(BlockTags.WOODEN_SLABS, ItemTags.WOODEN_SLABS);
        copy(BlockTags.WOODEN_FENCES, ItemTags.WOODEN_FENCES);
        copy(BlockTags.FENCE_GATES, ItemTags.FENCE_GATES);
        copy(BlockTags.WOODEN_DOORS, ItemTags.WOODEN_DOORS);
        copy(BlockTags.WOODEN_TRAPDOORS, ItemTags.WOODEN_TRAPDOORS);
        copy(BlockTags.WOODEN_BUTTONS, ItemTags.WOODEN_BUTTONS);
        copy(BlockTags.WOODEN_PRESSURE_PLATES, ItemTags.WOODEN_PRESSURE_PLATES);

        // ========== 王庭石：原版石质物品标签 ==========

        // 注意：原版只有 stone_buttons 的物品标签，没有 stone_pressure_plates 的物品标签
        // （#minecraft:stone_pressure_plates 仅存在于方块标签侧），所以这里不能 copy 后者。
        copy(BlockTags.STONE_BUTTONS, ItemTags.STONE_BUTTONS);

        // ========== 通用建筑方块标签（木 + 石一起）==========

        copy(BlockTags.STAIRS, ItemTags.STAIRS);
        copy(BlockTags.SLABS, ItemTags.SLABS);
        copy(BlockTags.WALLS, ItemTags.WALLS);
        copy(BlockTags.BUTTONS, ItemTags.BUTTONS);
        copy(BlockTags.DOORS, ItemTags.DOORS);
        copy(BlockTags.TRAPDOORS, ItemTags.TRAPDOORS);
        copy(BlockTags.FENCES, ItemTags.FENCES);

        // ========== 其余已登记方块标签的镜像 ==========
        // 这两条和「木 / 石」无关，但方块标签已经在 BlockTagData 里登记过，
        // 复制一份才能让「按 #minecraft:sand / #minecraft:leaves 找物品」的逻辑也看到模组方块。
        copy(BlockTags.SAND, ItemTags.SAND);
        copy(BlockTags.LEAVES, ItemTags.LEAVES);
    }
}
