package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionItems;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 模组的创造模式标签。
 *
 * <p>物品表按 {@link SectionedCreativeModeTab 分区}声明：每个分区在网格里以一行
 * 流动色带 + 居中彩色标题开头，分区之间自动补空到整行。</p>
 *
 * <p>合并后内容分 6 类：刷怪蛋 / 材料 / 魔法 / 武器与工具 / 方块 / 其它。
 * 每类里先列王庭（原 Hall）内容，再列腐化（原 corruption）内容。</p>
 *
 * <p><b>物品一律使用强类型引用</b>：普通物品用 {@code RegisterItem.X.get()} /
 * {@code CorruptionItems.X.get()}，方块物品用 {@code Item.byBlock(RegisterBlock.X.get())} /
 * {@code Item.byBlock(CorruptionBlocks.X.get())}。这样物品改名 / 迁移时编译器会直接报错，
 * IDE 可以跳转和重命名，也不会出现"字符串拼错只在运行期打日志"的问题。</p>
 *
 * <p>最后一个 {@code misc} 分区是兜底：遍历本模组命名空间、按 {@link Item} 去重
 * （先声明的分区赢），保证以后新加的物品即使忘了登记也至少能在创造栏看到。</p>
 */
public class RegisterTab {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HallMod.MODID);

    private static Component section(String key) {
        return Component.translatable("itemGroup." + HallMod.MODID + ".main.section." + key);
    }

    /** 批量放入当前分区的物品（全部是编译期可校验的注册对象引用）。 */
    private static void items(CreativeModeTab.Output output, Item... items) {
        for (Item item : items) {
            output.accept(item);
        }
    }

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            HallMod.MODID + "_tab",
            () -> SectionedCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + HallMod.MODID + ".main"))
                    .icon(() -> new ItemStack(RegisterItem.EXAMPLE_ITEM.get()))

                    // ─────────────────────────── 刷怪蛋（33） ───────────────────────────
                    .section(section("creatures"), (params, output) -> {
                        // 王庭系（原 Hall，畸骸种 hall_*）
                        items(output,
                        RegisterItem.INF_PLAYER_SPAWN_EGG.get(),
                        RegisterItem.INF_ENDERMAN_SPAWN_EGG.get(),
                        RegisterItem.INF_SKELETON_SPAWN_EGG.get(),
                        RegisterItem.SCOUT_SPAWN_EGG.get(),
                        RegisterItem.PURSUER_SPAWN_EGG.get(),
                        RegisterItem.MONOLITH_SPAWN_EGG.get(),
                        RegisterItem.BONECRUSHER_SPAWN_EGG.get(),
                        RegisterItem.HEAVY_BOMB_SPAWN_EGG.get(),
                        RegisterItem.COLLAPSAR_SPAWN_EGG.get(),
                        RegisterItem.WINDING_COLUMN_SPAWN_EGG.get());
                        // 腐化系（原 corruption，同化种 corruption_*）
                        items(output,
                        CorruptionItems.CORRUPTION_ZOMBIE_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_SKELETON_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_PLAYER_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_VILLAGER_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_COW_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_SHEEP_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_PIG_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_CHICKEN_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_WOLF_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_BAT_SPAWN_EGG.get(),
                        CorruptionItems.CORRUPTION_BONE_RUINS_SPAWN_EGG.get());
                        items(output,
                        CorruptionItems.DEGENERATE_PIG_SPAWN_EGG.get(),
                        CorruptionItems.DEGENERATE_ZOMBIE_SPAWN_EGG.get(),
                        CorruptionItems.DEGENERATE_VILLAGER_SPAWN_EGG.get(),
                        CorruptionItems.DEGENERATE_COW_SPAWN_EGG.get(),
                        CorruptionItems.GROVELER_SPAWN_EGG.get(),
                        CorruptionItems.ATTACKER_SPAWN_EGG.get(),
                        CorruptionItems.BEARER_SPAWN_EGG.get(),
                        CorruptionItems.LONG_ARM_BEAST_SPAWN_EGG.get(),
                        CorruptionItems.SHOCKER_SPAWN_EGG.get(),
                        CorruptionItems.HUMUS_COLUMN_SPAWN_EGG.get(),
                        CorruptionItems.SUMMONING_PILLAR_SPAWN_EGG.get(),
                        CorruptionItems.ORIGINAL_BUG_SPAWN_EGG.get());
                    })

                    // ─────────────────────────── 材料（23） ───────────────────────────
                    .section(section("materials"), (params, output) -> {
                        // 王庭材料
                        items(output,
                        RegisterItem.HALL_TENDON.get(),
                        RegisterItem.HALL_BONE_FRAGMENTS.get(),
                        RegisterItem.BONECRUSHER_CORE.get(),
                        RegisterItem.DOMITE_ORE.get(),
                        RegisterItem.DOMITE_CRYSTAL.get(),
                        RegisterItem.DOMERITE_ORE.get(),
                        RegisterItem.DOMERITE_INGOT.get(),
                        RegisterItem.DOMERITE_STICK.get(),
                        RegisterItem.HEAT_ANOMALY_EXTRACT.get(),
                        RegisterItem.COLD_ANOMALY_EXTRACT.get(),
                        RegisterItem.ACID_ANOMALY_EXTRACT.get());
                        // 腐化材料
                        items(output,
                        CorruptionItems.INFECTION_BONE.get(),
                        CorruptionItems.HARD_FRAGMENTS.get(),
                        CorruptionItems.CHITIN_POLYMER.get(),
                        CorruptionItems.HOST_CARAPACE.get(),
                        CorruptionItems.HOST_TOOTH.get(),
                        CorruptionItems.HOST_MEAT.get(),
                        CorruptionItems.LIVE_MEAT.get(),
                        CorruptionItems.DEGENERATE_HEART.get(),
                        CorruptionItems.LONG_ARM_BEAST_HEAD.get(),
                        CorruptionItems.LONG_ARM_BEAST_TOOTH.get(),
                        CorruptionItems.ACID_SAC.get(),
                        CorruptionItems.ACID_BALL.get());

                    })

                    // ─────────────────────────── 魔法（13） ───────────────────────────
                    .section(section("magic"), (params, output) -> {
                        items(output,RegisterBlock.SOURCE_FILTER.get().asItem(),RegisterBlock.SOURCE_COLLECTOR.get().asItem());
                        items(output, RegisterBlock.GROUNDING_STAKE.get().asItem(), RegisterBlock.SOURCE_WIRE.get().asItem());
                        // 储魔瓶与魔法材料
                        items(output,
                        RegisterItem.MANA_BOTTLE.get(),
                        RegisterItem.LIGHT_MANA_BOTTLE.get(),
                        RegisterItem.FIRE_MANA_BOTTLE.get(),
                        RegisterItem.WATER_MANA_BOTTLE.get(),
                        RegisterItem.NATURE_MANA_BOTTLE.get(),
                        RegisterItem.VOID_MANA_BOTTLE.get(),
                        RegisterItem.DEATH_MANA_BOTTLE.get(),
                        RegisterItem.MYSTERIOUS_MANA_BOTTLE.get(),
                        RegisterItem.SOURCE_CHARGE_SAND.get());
                        // 基础板材与七系法力板
                        items(output,
                        RegisterItem.SOURCE_MANA_PLATE.get(),
                        RegisterItem.LIGHT_MANA_PLATE.get(),
                        RegisterItem.FIRE_MANA_PLATE.get(),
                        RegisterItem.WATER_MANA_PLATE.get(),
                        RegisterItem.NATURE_MANA_PLATE.get(),
                        RegisterItem.VOID_MANA_PLATE.get(),
                        RegisterItem.DEATH_MANA_PLATE.get(),
                        RegisterItem.MYSTERIOUS_MANA_PLATE.get());
                        // 施法媒介 / 催化剂
                        items(output,
                        RegisterItem.MAGIC_CATA.get(),
                        RegisterItem.VOID_RUNE.get(),
                        RegisterItem.WAND.get(),
                        RegisterItem.WAND_NATURE.get(),
                        RegisterItem.WAND_FIRE.get(),
                        RegisterItem.WAND_DEATH.get(),
                        RegisterItem.WAND_LIGHT.get(),
                        RegisterItem.WAND_VOID.get(),
                        RegisterItem.WAND_WATER.get(),
                        RegisterItem.WAND_MYSTERIOUS.get(),
                        RegisterItem.WAND_COMMON.get());
                        // 每种魔法类型至少一个主动法术
                        items(output,
                        RegisterItem.FIRE_BALL.get(),
                        RegisterItem.SPEAR_OF_JUDGMENT.get(),
                        RegisterItem.SUPPRESSION_FIELD.get(),
                        RegisterItem.TIDAL_PULSE.get(),
                        RegisterItem.BRIAR_SNARE.get(),
                        RegisterItem.PURIFYING_LIGHT.get(),
                        RegisterItem.ARCANE_STEP.get());
                    })

                    // ─────────────────────── 武器与工具（16） ───────────────────────
                    .section(section("gear"), (params, output) -> {
                        // 王庭武器与工具
                        items(output,
                        RegisterItem.SIMPLE_FIELD_METER.get(),
                        RegisterItem.DOMERITE_SWORD.get(),
                        RegisterItem.DOMERITE_LONGSWORD.get(),
                        RegisterItem.DOMERITE_AXE.get(),
                        RegisterItem.DOMERITE_PICKAXE.get(),
                        RegisterItem.DOMERITE_SHOVEL.get(),
                        RegisterItem.DOMERITE_HOE.get(),
                        RegisterItem.VOID_SWORD.get(),
                        RegisterItem.CRIMSON_VOW.get(),
                        RegisterItem.SILENT_DAYLIGHT.get());
                        // 腐化工具与注射器
                        items(output,
                        CorruptionItems.CRUDE_SWORD.get(),
                        CorruptionItems.CRUDE_AXE.get(),
                        CorruptionItems.CRUDE_PICKAXE.get(),
                        CorruptionItems.CRUDE_SHOVEL.get(),
                        CorruptionItems.CRUDE_HOE.get(),
                        CorruptionItems.SYRINGE.get(),
                        CorruptionItems.SYRINGE_BLOODY_CONCENTRATE.get());
                    })

                    // ─────────────────────── 方块（54） ───────────────────────
                    .section(section("blocks"), (params, output) -> {
                        // 王庭：地表 / 石系
                        items(output,
                        Item.byBlock(RegisterBlock.HALL_GRASS_BLOCK.get()),
                        Item.byBlock(RegisterBlock.HALL_DIRT.get()),
                        Item.byBlock(RegisterBlock.HALL_STONE.get()),
                        Item.byBlock(RegisterBlock.HALL_STONE_STAIRS.get()),
                        Item.byBlock(RegisterBlock.HALL_STONE_SLAB.get()),
                        Item.byBlock(RegisterBlock.HALL_STONE_WALL.get()),
                        Item.byBlock(RegisterBlock.HALL_STONE_BUTTON.get()),
                        Item.byBlock(RegisterBlock.HALL_STONE_PRESSURE_PLATE.get()),
                        Item.byBlock(RegisterBlock.HALL_SANDSTONE.get()),
                        Item.byBlock(RegisterBlock.HALL_ASH_SAND.get()),
                        Item.byBlock(RegisterBlock.HALL_ASH_CUT_SANDSTONE.get()),
                        Item.byBlock(RegisterBlock.HALL_ASH_SMOOTH_SANDSTONE.get()),
                        Item.byBlock(RegisterBlock.HALL_ASH_COLLAPSED_CHISELED_SANDSTONE.get()),
                        Item.byBlock(RegisterBlock.HALL_ASH_CACTUS.get()),
                        Item.byBlock(RegisterBlock.HALL_ASH_DEAD_BUSH.get()));
                        // 王庭：木系 / 植被 / 矿石
                        items(output,
                        Item.byBlock(RegisterBlock.HALL_LOG.get()),
                        Item.byBlock(RegisterBlock.HALL_LEAVES.get()),
                        Item.byBlock(RegisterBlock.HALL_PILLAR.get()),
                        Item.byBlock(RegisterBlock.HALL_PLANKS.get()),
                        Item.byBlock(RegisterBlock.HALL_STAIRS.get()),
                        Item.byBlock(RegisterBlock.HALL_SLAB.get()),
                        Item.byBlock(RegisterBlock.HALL_FENCE.get()),
                        Item.byBlock(RegisterBlock.HALL_FENCE_GATE.get()),
                        Item.byBlock(RegisterBlock.HALL_DOOR.get()),
                        Item.byBlock(RegisterBlock.HALL_TRAPDOOR.get()),
                        Item.byBlock(RegisterBlock.HALL_BUTTON.get()),
                        Item.byBlock(RegisterBlock.HALL_PRESSURE_PLATE.get()),
                        Item.byBlock(RegisterBlock.HALL_VINE.get()),
                        Item.byBlock(RegisterBlock.HALL_GRASS.get()),
                        Item.byBlock(RegisterBlock.HALL_FLOWER.get()),
                        Item.byBlock(RegisterBlock.DOMITE_MINERAL.get()),
                        Item.byBlock(RegisterBlock.DOMERITE_MINERAL.get()),
                        Item.byBlock(RegisterBlock.FLESH_RIFT.get()),
                        Item.byBlock(RegisterBlock.DEATH_CORE.get()));
                        // 腐化：地表 / 组织
                        items(output,
                        Item.byBlock(CorruptionBlocks.SPREAD_DIRT.get()),
                        Item.byBlock(CorruptionBlocks.SPREAD_STONE.get()),
                        Item.byBlock(CorruptionBlocks.INFECTION_COBBLESTONE_BLOCK.get()),
                        Item.byBlock(CorruptionBlocks.SPREAD_INFECTION_LEAVES.get()),
                        Item.byBlock(CorruptionBlocks.SPREAD_INFECTION_WOOD.get()),
                        Item.byBlock(CorruptionBlocks.CORRUPTION_GRASS.get()),
                        Item.byBlock(CorruptionBlocks.CORRUPTION_TRACHEA.get()),
                        Item.byBlock(CorruptionBlocks.CONNECT_BLOCK.get()),
                        Item.byBlock(CorruptionBlocks.DECIDUAL_TISSUE_BLOCK.get()),
                        Item.byBlock(CorruptionBlocks.FLESHBED.get()),
                        Item.byBlock(CorruptionBlocks.VARIABLE_HEIGHT_CROP.get()),
                        Item.byBlock(CorruptionBlocks.SPIRACLE_BLOCK.get()));
                        // 腐化：遗骸（腐化 / 同化 / 重构 × 小/中/大）
                        items(output,
                        Item.byBlock(CorruptionBlocks.DEGENERATE_REMAINS_SMALL.get()),
                        Item.byBlock(CorruptionBlocks.DEGENERATE_REMAINS_MEDIUM.get()),
                        Item.byBlock(CorruptionBlocks.DEGENERATE_REMAINS_LARGE.get()),
                        Item.byBlock(CorruptionBlocks.HOST_REMAINS_SMALL.get()),
                        Item.byBlock(CorruptionBlocks.HOST_REMAINS_MEDIUM.get()),
                        Item.byBlock(CorruptionBlocks.HOST_REMAINS_LARGE.get()),
                        Item.byBlock(CorruptionBlocks.REFACTORED_REMAINS_SMALL.get()),
                        Item.byBlock(CorruptionBlocks.REFACTORED_REMAINS_MEDIUM.get()),
                        Item.byBlock(CorruptionBlocks.REFACTORED_REMAINS_LARGE.get()));
                    })

                    // ─────────────────────── 其它（兜底） ───────────────────────
                    .section(section("misc"), (params, output) -> {
                        // 先放已经明确归入"其它"的物品
                        items(output,
                        RegisterItem.CHIP_NULL.get(),
                        RegisterItem.CHIP_ENHANCED.get(),
                        RegisterItem.CHIP_SCAN.get(),
                        RegisterItem.CHIP_EXTRACT.get(),
                        RegisterItem.INF_ENDER_PEAR.get());
                        // 再兜底收本命名空间里尚未归类的物品（example_item 是占位图标，不展示）
                        Set<Item> claimed = new LinkedHashSet<>();
                        ForgeRegistries.ITEMS.getValues().stream()
                                .filter(item -> item.builtInRegistryHolder().key().location()
                                        .getNamespace().equals(HallMod.MODID))
                                .filter(item -> item != RegisterItem.EXAMPLE_ITEM.get())
                                .forEach(claimed::add);
                        claimed.forEach(output::accept);
                    })

                    .build()
    );
}
