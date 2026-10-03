package org.bytechen.hall.datagen.gen.lang;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.hall.overworld.registry.RegisterEffect;
import org.bytechen.hall.overworld.registry.RegisterItem;
import org.bytechen.hall.utils.TranslateUtils;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public class LangDataCN extends LanguageProvider {
    public LangDataCN(PackOutput output, String locale) { super(output, HallMod.MODID, locale); }

    @Override
    protected void addTranslations() {
        add("itemGroup." + HallMod.MODID + ".main", "腐朽之疫:王庭");
        add(RegisterItem.EXAMPLE_ITEM.get(), "测试物品");
        add(RegisterItem.VOID_SWORD.get(), "伪-虚无之刃");
        add(RegisterItem.HALL_BONE_FRAGMENTS.get(),  "王庭骨");
        add(RegisterItem.HALL_TENDON.get(), "王庭肌腱");
        add(RegisterItem.INF_ENDER_PEAR.get(), "畸骸末影珍珠");
        add(RegisterItem.BONECRUSHER_CORE.get(), "碎骨核心");
        add(RegisterItem.DOMITE_ORE.get(), "穹顶胚晶矿");
        add(RegisterItem.DOMERITE_ORE.get(), "穹顶云铁矿");
        add(RegisterItem.DOMITE_CRYSTAL.get(), "穹顶胚晶");
        add(RegisterItem.DOMERITE_INGOT.get(), "穹顶云铁锭");
        add(RegisterItem.DOMERITE_STICK.get(), "穹顶只杖");
        add(RegisterItem.DOMERITE_SWORD.get(), "云顶之剑");
        add(RegisterItem.DOMERITE_PICKAXE.get(), "云顶之镐");
        add(RegisterItem.DOMERITE_AXE.get(), "云顶之斧");
        add(RegisterItem.DOMERITE_SHOVEL.get(), "云顶之锹");
        add(RegisterItem.DOMERITE_HOE.get(), "云顶之锄");
        add(RegisterItem.DOMERITE_LONGSWORD.get(), "天穹裁决");
        add(RegisterItem.CRIMSON_VOW.get(), "绯红誓约");
        add(RegisterItem.ACID_ANOMALY_EXTRACT.get(), "酸异常提取物");
        add(RegisterItem.COLD_ANOMALY_EXTRACT.get(), "冷异常提取物");
        add(RegisterItem.HEAT_ANOMALY_EXTRACT.get(), "热异常提取物");
        add(RegisterItem.INF_PLAYER_SPAWN_EGG.get(), "畸骸玩家刷怪蛋");
        add(RegisterItem.INF_ENDERMAN_SPAWN_EGG.get(), "畸骸末影人刷怪蛋");
        add(RegisterItem.INF_SKELETON_SPAWN_EGG.get(), "畸骸骷髅刷怪蛋");
        add(RegisterItem.SCOUT_SPAWN_EGG.get(), "溃烂纠察刷怪蛋");
        add(RegisterItem.PURSUER_SPAWN_EGG.get(), "溃烂追蹤者刷怪蛋");
        add(RegisterItem.MONOLITH_SPAWN_EGG.get(), "溃烂巨岩刷怪蛋");
        add(RegisterItem.BONECRUSHER_SPAWN_EGG.get(), "王庭碎骨刷怪蛋");
        add(RegisterItem.HEAVY_BOMB_SPAWN_EGG.get(), "王庭重型轰炸刷怪蛋");
        add(RegisterItem.COLLAPSAR_SPAWN_EGG.get(), "萨米使徒刷怪蛋【未完成】");
        add(RegisterBlock.HALL_GRASS_BLOCK.get(), "王庭草方块");
        add(RegisterBlock.HALL_DIRT.get(), "王庭泥土");
        add(RegisterBlock.HALL_STONE.get(), "王庭石块");
        add(RegisterBlock.HALL_LOG.get(), "王庭原木");
        add(RegisterBlock.HALL_LEAVES.get(), "王庭树叶");
        add(RegisterBlock.HALL_PILLAR.get(), "王庭石柱");
        add(RegisterBlock.HALL_PLANKS.get(), "王庭木板");
        add(RegisterBlock.HALL_STAIRS.get(), "王庭楼梯");
        add(RegisterBlock.HALL_SLAB.get(), "王庭台阶");
        add(RegisterBlock.HALL_FENCE.get(), "王庭栅栏");
        add(RegisterBlock.HALL_FENCE_GATE.get(), "王庭栅栏门");
        add(RegisterBlock.HALL_DOOR.get(), "王庭木门");
        add(RegisterBlock.HALL_TRAPDOOR.get(), "王庭木活板门");
        add(RegisterBlock.HALL_BUTTON.get(), "王庭按钮");
        add(RegisterBlock.HALL_PRESSURE_PLATE.get(), "王庭压力板");
        add(RegisterBlock.HALL_STONE_STAIRS.get(), "王庭石楼梯");
        add(RegisterBlock.HALL_STONE_SLAB.get(), "王庭石台阶");
        add(RegisterBlock.HALL_STONE_WALL.get(), "王庭石墙");
        add(RegisterBlock.HALL_STONE_BUTTON.get(), "王庭石质按钮");
        add(RegisterBlock.HALL_STONE_PRESSURE_PLATE.get(), "王庭石质压力板");
        add(RegisterBlock.DOMITE_MINERAL.get(), "穹顶胚晶矿石");
        add(RegisterBlock.DOMERITE_MINERAL.get(), "穹顶云铁矿石");
        add(RegisterBlock.HALL_VINE.get(), "王庭藤蔓");
        add(RegisterBlock.HALL_FLOWER.get(), "王庭花");
        add(RegisterBlock.HALL_GRASS.get(), "王庭草");
        add(RegisterBlock.HALL_SANDSTONE.get(), "王庭砂岩");
        add(RegisterBlock.HALL_ASH_SAND.get(), "王庭烬痕沙子");
        add(RegisterBlock.HALL_ASH_CUT_SANDSTONE.get(), "王庭烬痕切制砂岩");
        add(RegisterBlock.HALL_ASH_SMOOTH_SANDSTONE.get(), "王庭烬痕平滑砂岩");
        add(RegisterBlock.HALL_ASH_COLLAPSED_CHISELED_SANDSTONE.get(), "王庭烬痕坍缩雕纹砂岩");
        add(RegisterBlock.HALL_ASH_CACTUS.get(), "王庭烬痕仙人掌");
        add(RegisterBlock.HALL_ASH_DEAD_BUSH.get(), "王庭烬痕枯灌木");
        add(EntityTypeRegistry.INF_PLAYER.get(), "畸骸玩家");
        add(EntityTypeRegistry.INF_ENDERMAN.get(), "畸骸末影人");
        add(EntityTypeRegistry.INF_SKELETON.get(), "畸骸骷髅");
        add(EntityTypeRegistry.INF_SKELETON_ARROW.get(), "畸骸骷髅炸弹");
        add(EntityTypeRegistry.HEAVY_BOMB.get(), "王庭重型轰炸");
        add(EntityTypeRegistry.HEAVY_BOMB_TNT.get(), "重型炸弹TNT");
        add(EntityTypeRegistry.BONECRUSHER.get(), "王庭碎骨");
        add(EntityTypeRegistry.COLLAPSAR.get(), "萨米使徒");
        add(EntityTypeRegistry.SCOUT.get(), "溃烂纠察");
        add(EntityTypeRegistry.PURSUER.get(), "溃烂追蹤者");
        add(EntityTypeRegistry.MONOLITH.get(), "溃烂巨岩");
        add(EntityTypeRegistry.SHOCKWAVE.get(), "冲击波");
        add(EntityTypeRegistry.METEORITE.get(), "陨石");
        // 天穹裁决 · 裁决领域的落剑（纯视觉技能实体，但仍给一个可读名字便于调试）
        add(EntityTypeRegistry.VERDICT_SWORD_DROP.get(), "裁决落剑");
        add(RegisterEffect.ACID_ANOMALY_ADAPTATION.get(), "酸异常适应");
        add(RegisterEffect.COLD_ANOMALY_ADAPTATION.get(), "冷异常适应");
        add(RegisterEffect.HEAT_ANOMALY_ADAPTATION.get(), "热异常适应");
        add(RegisterEffect.VERDICT.get(), "裁决");
        // 天穹裁决：冷却中右键时的动作栏提示
        add("item.hall.domerite_longsword.cooldown", "裁决尚未就绪（%s 秒）");
        // 天穹裁决：抬头不足，光柱无法立起（原本这一下是完全静默的）
        add("item.hall.domerite_longsword.no_aim", "未瞄准 · 抬头才能落下裁决");
        // 天穹裁决：这一招完全落空（空放只记很短的一笔冷却）
        add("item.hall.domerite_longsword.missed", "落空 · 冷却已返还");
        add(TranslateUtils.DIFFICULTY_EASY, "简单");
        add(TranslateUtils.DIFFICULTY_NORMAL, "普通");
        add(TranslateUtils.DIFFICULTY_HARD, "困难");
        add(TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE, "无法理解");
        add(TranslateUtils.DIFFICULTY_EASY_TOOLTIP, "轻松的体验氛围，适合养老玩家");
        add(TranslateUtils.DIFFICULTY_NORMAL_TOOLTIP, "标准游戏体验");
        add(TranslateUtils.DIFFICULTY_HARD_TOOLTIP, "哦不不不！这无疑是难以战胜的");
        add(TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE_TOOLTIP, "你确定你要这么做吗？");
        add(TranslateUtils.GUI_DIFFICULTY_SELECT_TITLE, "选择难度");
        add(TranslateUtils.GUI_DIFFICULTY_SELECT_HINT, "请选择世界难度");

        // ---- 绯红誓约 tooltip（占位文案，待定稿） ----
        // 带 %s 的是"标签 + 说明"结构：标签由 Java 侧染成粉紫流动色，说明保持灰色。
        // %% 是原版 TranslatableContents 的转义（见其 FORMAT_PATTERN 那一段）。
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_LORE, "「以血为誓，此刃不折。」");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_LABEL_BLOCK, "格挡");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_LABEL_VOW, "誓约");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_BLOCK, "%s  按住右键举剑迎击 · 只挡正面来敌");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_VOW, "%s  不可损坏 · 火焰免疫 · 合成后归还");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_BAR_LABEL, "格挡减伤");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_BAR_VALUE, "%s%%");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_DEBUG,
                "调试 · 格挡结算 ×%s%% · 仅正面 %s · 使用时长 %s tick · 实体范围 +%s");

        // ---- 血肉庭园维度（hall:heall） ----
        // 该维度的地形完全复用现有王庭方块，因此这里只有通道方块的译名
        add(RegisterBlock.FLESH_RIFT.get(), "血肉裂隙");
        add("dimension." + HallMod.MODID + ".heall", "血肉庭园");
        add("biome." + HallMod.MODID + ".flesh_marrow", "血肉庭园");
        add("block." + HallMod.MODID + ".flesh_rift.no_dimension", "裂隙的另一端尚未形成……");
    }
}
