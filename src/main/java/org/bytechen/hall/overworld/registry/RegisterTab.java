package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组的创造模式标签。
 *
 * <p>物品表按 {@link SectionedCreativeModeTab 分区}声明：每个分区在网格里以一行
 * 流动色带 + 居中彩色标题开头，分区之间自动补空到整行，不会出现"标题从半行折下去"。
 * 分区逻辑本身在 {@code SectionedCreativeModeTab} + 客户端 mixin 里，
 * 这里只负责决定「什么归哪一段」。</p>
 *
 * <h3>最后那个分区为什么可以"全都倒进来"</h3>
 * <p>{@code SectionedCreativeModeTab} 会在转发给原版之前按 {@link Item} 去重，
 * 先声明的分区赢。所以 {@code misc} 直接遍历整个 {@code hall} 命名空间是安全的 ——
 * 前面几段拿走的不会再出现一次，新加的物品也不会因为忘了登记而消失。
 * （这正是改造前那句 {@code LinkedHashSet<Item>} 的作用，只是现在去重发生在
 * "段"这一级。）</p>
 */
public class RegisterTab {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HallMod.MODID);

    /**
     * 分区标题。
     *
     * <p>用 {@code Component.translatable} 而不是硬编码中文：这些字符串会随
     * {@code runData} 一起进 {@code LangDataCN/EN}，语言文件里改文案不需要动代码。</p>
     */
    private static Component section(String key) {
        return Component.translatable("itemGroup." + HallMod.MODID + ".main.section." + key);
    }

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            HallMod.MODID + "_tab",
            () -> SectionedCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + HallMod.MODID + ".main"))
                    .icon(() -> new ItemStack(RegisterItem.EXAMPLE_ITEM.get()))

                    // ── 畸骸与王庭生物（刷怪蛋）──
                    .section(section("creatures"), (params, output) -> {
                        output.accept(RegisterItem.INF_PLAYER_SPAWN_EGG.get());
                        output.accept(RegisterItem.INF_ENDERMAN_SPAWN_EGG.get());
                        output.accept(RegisterItem.INF_SKELETON_SPAWN_EGG.get());
                        output.accept(RegisterItem.SCOUT_SPAWN_EGG.get());
                        output.accept(RegisterItem.PURSUER_SPAWN_EGG.get());
                        output.accept(RegisterItem.MONOLITH_SPAWN_EGG.get());
                        output.accept(RegisterItem.BONECRUSHER_SPAWN_EGG.get());
                        output.accept(RegisterItem.HEAVY_BOMB_SPAWN_EGG.get());
                        output.accept(RegisterItem.COLLAPSAR_SPAWN_EGG.get());
                    })

                    // ── 王庭材料 ──
                    .section(section("materials"), (params, output) -> {
                        output.accept(RegisterItem.HALL_TENDON.get());
                        output.accept(RegisterItem.HALL_BONE_FRAGMENTS.get());
                        output.accept(RegisterItem.BONECRUSHER_CORE.get());
                        output.accept(RegisterItem.DOMITE_ORE.get());
                        output.accept(RegisterItem.DOMITE_CRYSTAL.get());
                        output.accept(RegisterItem.DOMERITE_ORE.get());
                        output.accept(RegisterItem.DOMERITE_INGOT.get());
                        output.accept(RegisterItem.DOMERITE_STICK.get());
                        output.accept(RegisterItem.HEAT_ANOMALY_EXTRACT.get());
                        output.accept(RegisterItem.COLD_ANOMALY_EXTRACT.get());
                        output.accept(RegisterItem.ACID_ANOMALY_EXTRACT.get());
                    })

                    // ── 武器与工具 ──
                    .section(section("gear"), (params, output) -> {
                        output.accept(RegisterItem.DOMERITE_SWORD.get());
                        output.accept(RegisterItem.DOMERITE_AXE.get());
                        output.accept(RegisterItem.DOMERITE_PICKAXE.get());
                        output.accept(RegisterItem.DOMERITE_SHOVEL.get());
                        output.accept(RegisterItem.DOMERITE_HOE.get());
                        output.accept(RegisterItem.DOMERITE_LONGSWORD.get());
                        output.accept(RegisterItem.VOID_SWORD.get());
                        output.accept(RegisterItem.CRIMSON_VOW.get());
                        output.accept(RegisterItem.SILENT_DAYLIGHT.get());
                    })

                    // ── 王庭建材 ──
                    .section(section("blocks"), (params, output) -> {
                        output.accept(Item.byBlock(RegisterBlock.HALL_GRASS_BLOCK.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_DIRT.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_STONE.get()));
                        // 王庭石系列
                        output.accept(Item.byBlock(RegisterBlock.HALL_STONE_STAIRS.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_STONE_SLAB.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_STONE_WALL.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_STONE_BUTTON.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_STONE_PRESSURE_PLATE.get()));
                        // 王庭烬痕沙漠系列
                        output.accept(Item.byBlock(RegisterBlock.HALL_SANDSTONE.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_ASH_SAND.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_ASH_CUT_SANDSTONE.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_ASH_SMOOTH_SANDSTONE.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_ASH_COLLAPSED_CHISELED_SANDSTONE.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_ASH_CACTUS.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_ASH_DEAD_BUSH.get()));
                        // 王庭木系列
                        output.accept(Item.byBlock(RegisterBlock.HALL_LOG.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_LEAVES.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_PILLAR.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_PLANKS.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_STAIRS.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_SLAB.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_FENCE.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_FENCE_GATE.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_DOOR.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_TRAPDOOR.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_BUTTON.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_PRESSURE_PLATE.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_VINE.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_GRASS.get()));
                        output.accept(Item.byBlock(RegisterBlock.HALL_FLOWER.get()));
                        output.accept(Item.byBlock(RegisterBlock.DOMITE_MINERAL.get()));
                        output.accept(Item.byBlock(RegisterBlock.DOMERITE_MINERAL.get()));
                    })

                    // ── 其它：本模组里还没归类的物品兜底 ──
                    .section(section("misc"), (params, output) ->
                            ForgeRegistries.ITEMS.getValues().stream()
                                    .filter(item -> item.builtInRegistryHolder().key().location()
                                            .getNamespace().equals(HallMod.MODID))
                                    .forEach(output::accept))

                    .build()
    );
}
