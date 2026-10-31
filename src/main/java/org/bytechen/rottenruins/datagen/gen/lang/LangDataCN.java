package org.bytechen.rottenruins.datagen.gen.lang;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.datagen.gen.AdvancementProviderData;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionItems;
import org.bytechen.rottenruins.utils.TranslateUtils;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public class LangDataCN extends LanguageProvider {
    public LangDataCN(PackOutput output, String locale) { super(output, HallMod.MODID, locale); }

    @Override
    protected void addTranslations() {
        add("itemGroup." + HallMod.MODID + ".main", "腐朽之疫");
        // 创造物品栏的分区隔断行标题（见 SectionedCreativeModeTab / RegisterTab）
        add("itemGroup." + HallMod.MODID + ".main.section.creatures", "刷怪蛋");
        add("itemGroup." + HallMod.MODID + ".main.section.materials", "材料");
        add("itemGroup." + HallMod.MODID + ".main.section.magic", "魔法");
        add("itemGroup." + HallMod.MODID + ".main.section.gear", "武器与工具");
        add("itemGroup." + HallMod.MODID + ".main.section.blocks", "方块");
        add("itemGroup." + HallMod.MODID + ".main.section.misc", "其它");
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
        add(RegisterItem.SILENT_DAYLIGHT.get(), "寂寒白日");
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
        add(RegisterItem.WINDING_COLUMN_SPAWN_EGG.get(), "缠绕之柱刷怪蛋");
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
        add(EntityTypeRegistry.HEARTHSTONE.get(), "王庭炉石");
        add(EntityTypeRegistry.BONECRUSHER.get(), "王庭碎骨");
        add(EntityTypeRegistry.COLLAPSAR.get(), "萨米使徒");
        add(EntityTypeRegistry.SCOUT.get(), "溃烂纠察");
        add(EntityTypeRegistry.PURSUER.get(), "溃烂追蹤者");
        add(EntityTypeRegistry.MONOLITH.get(), "溃烂巨岩");
        // 防御型生物：缠绕之柱（不会移动 · 抓取 · 伤害传导 · 潜地偷袭）
        add(EntityTypeRegistry.WINDING_COLUMN.get(), "缠绕之柱");
        add(EntityTypeRegistry.SHOCKWAVE.get(), "冲击波");
        add(EntityTypeRegistry.METEORITE.get(), "陨石");
        // 天穹裁决 · 裁决领域的落剑（纯视觉技能实体，但仍给一个可读名字便于调试）
        add(EntityTypeRegistry.VERDICT_SWORD_DROP.get(), "裁决落剑");
        add(RegisterEffect.ACID_ANOMALY_ADAPTATION.get(), "酸异常适应");
        add(RegisterEffect.COLD_ANOMALY_ADAPTATION.get(), "冷异常适应");
        add(RegisterEffect.HEAT_ANOMALY_ADAPTATION.get(), "热异常适应");
        add(RegisterEffect.VERDICT.get(), "裁决");
        // 天穹裁决：冷却中右键时的动作栏提示
        add("item.rotten_ruins.domerite_longsword.cooldown", "裁决尚未就绪（%s 秒）");
        // 天穹裁决：抬头不足，光柱无法立起（原本这一下是完全静默的）
        add("item.rotten_ruins.domerite_longsword.no_aim", "未瞄准 · 抬头才能落下裁决");
        // 天穹裁决：这一招完全落空（空放只记很短的一笔冷却）
        add("item.rotten_ruins.domerite_longsword.missed", "落空 · 冷却已返还");
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

        // ---- tooltip 文案（占位文案，待定稿） ----
        // 机制说明共用一份（几把格挡武器是同一件事）；各物品只拥有自己的风味。
        // "标签 + 正文"拆成两个键：渐变的逐字上色会把整行拍平，合成一条
        // "%s …" 模板就分不出两种颜色（见 client.rend.text.TooltipLines）。
        // %% 是原版 TranslatableContents 的转义（见其 FORMAT_PATTERN 那一段）。
        add(TranslateUtils.TOOLTIP_LABEL_BLOCK, "格挡");
        add(TranslateUtils.TOOLTIP_BLOCK, "按住右键举剑迎击 · 只挡正面来敌");
        add(TranslateUtils.TOOLTIP_TRAIT, "不可损坏 · 火焰免疫 · 合成后归还");
        add(TranslateUtils.TOOLTIP_BAR_LABEL, "格挡减伤");
        add(TranslateUtils.TOOLTIP_BAR_VALUE, "%s%%");

        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_LORE, "「以血为誓，此刃不折。」");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_LABEL_TRAIT, "誓约");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_LABEL_SIGIL, "起誓");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_SIGIL,
                "潜行 + 右键举剑 · 蓄满后于视线所指展开绯红法阵（20 秒）");
        add(TranslateUtils.CRIMSON_VOW_COOLDOWN, "誓约尚未就绪（%s 秒）");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_DEBUG,
                "调试 · 格挡结算 ×%s%% · 仅正面 %s · 使用时长 %s tick · 实体范围 +%s");

        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LORE, "「白昼无声，唯余一刃寒水。」");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_TIDE, "湍流");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_TIDE, "剑刃浸在一层流动的水光里");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_TRAIT, "白日");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_DEBUG,
                "调试 · 格挡结算 ×%s%% · 仅正面 %s · 使用时长 %s tick");

        // ---- 血肉庭园维度（hall:heall） ----
        // 该维度的地形完全复用现有王庭方块，因此这里只有通道方块的译名
        add(RegisterBlock.FLESH_RIFT.get(), "血肉裂隙");
        add("dimension." + HallMod.MODID + ".heall", "血肉庭园");
        add("biome." + HallMod.MODID + ".flesh_marrow", "血肉庭园");
        add(RegisterBlock.FLESH_RIFT.get().getDescriptionId() + ".no_dimension", "裂隙的另一端尚未形成……");

        // ---- 魔法：流派名 + 物品介绍（见 MagicType / MagicTooltips） ----
        // 流派名会被法术/饰品的介绍行用到；颜色由 MagicType#rgb() 提供，这里只管文字。
        add("magic_type." + HallMod.MODID + ".ice", "寒冰");
        add("magic_type." + HallMod.MODID + ".fire", "火焰");
        add("magic_type." + HallMod.MODID + ".death", "死亡");
        add("magic_type." + HallMod.MODID + ".nature", "自然");
        add("magic_type." + HallMod.MODID + ".light", "光明");
        add("magic_type." + HallMod.MODID + ".void", "虚空");
        add("magic_type." + HallMod.MODID + ".mysterious", "神秘");
        // 介绍行：参数统一 %s，数值部分在代码里按流派上色
        add("tooltip." + HallMod.MODID + ".magic.type", "类型：%s");
        add("tooltip." + HallMod.MODID + ".magic.cooldown", "冷却：%s 秒");
        add("tooltip." + HallMod.MODID + ".magic.cooldown.none", "冷却：无");
        add("tooltip." + HallMod.MODID + ".magic.mana", "消耗：%s 法力");
        add("tooltip." + HallMod.MODID + ".magic.mana.none", "消耗：无");
        add("tooltip." + HallMod.MODID + ".magic.keep_cost", "维持：%s 法力/秒");

        // ---- 魔法属性（见 RegisterAttributes） ----
        // 键名必须与 RangedAttribute 构造器里写死的那个字符串**完全一致**，
        // 对不上就会在属性界面里显示原始键名。
        add("attribute.name." + HallMod.MODID + ".max_mana", "法力上限");        add("attribute.name." + HallMod.MODID + ".mana_regeneration", "法力回复速度");
        add("attribute.name." + HallMod.MODID + ".mana_restore", "法力回复量");
        add("attribute.name." + HallMod.MODID + ".spell_power", "法术强度");
        add("attribute.name." + HallMod.MODID + ".magic_slots", "魔法槽位");
        add("attribute.name." + HallMod.MODID + ".cooldown_reduction", "冷却缩减");
        // 各流派的法术强度：与通用法强**相加**（默认 0），键名必须和
        // RegisterAttributes.spellPowerOf(String) 拼出来的完全一致。
        add("attribute.name." + HallMod.MODID + ".spell_power_ice", "寒冰法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_fire", "火焰法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_death", "死亡法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_nature", "自然法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_light", "光明法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_void", "虚空法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_mysterious", "神秘法术强度");

        // ---- 魔法体系 · 物品 ----
        // 法力板：六个流派各一块，目前都是纯材料（见 RegisterItem 里的说明，
        // 贴图是 water 而 MagicType 对应位置是 ACE，两边尚未建立映射）。
        add(RegisterItem.DEATH_MANA_PLATE.get(), "死亡魔法版材");
        add(RegisterItem.FIRE_MANA_PLATE.get(), "火焰魔法版材");
        add(RegisterItem.LIGHT_MANA_PLATE.get(), "光明魔法版材");
        add(RegisterItem.NATURE_MANA_PLATE.get(), "自然魔法版材");
        add(RegisterItem.VOID_MANA_PLATE.get(), "虚空魔法版材");
        add(RegisterItem.WATER_MANA_PLATE.get(), "水魔法版材");
        add(RegisterItem.MAGIC_CATA.get(), "魔法催化剂");
        add(RegisterItem.VOID_RUNE.get(), "虚空符文");
        add(RegisterItem.WAND.get(), "法杖");
        add(RegisterItem.WAND_NATURE.get(), "自然法杖");
        // 法术（上游 1.20.1forge「魔法更新」）
        add(RegisterItem.FIRE_BALL.get(), "火球术");
        add(RegisterItem.SPEAR_OF_JUDGMENT.get(), "审判之矛");
        add(RegisterItem.SUPPRESSION_FIELD.get(), "抑制场");
        // 死亡之星：下界之星 + 凋零玫瑰
        add(RegisterItem.DEATH_STAR.get(), "死亡之星");

        // ---- 魔法星图 GUI / 按键 ----
        add("key.categories." + HallMod.MODID, "腐朽之疫");
        add("key." + HallMod.MODID + ".open_magic", "打开魔法星图");
        add("rotten_ruins.gui.magic.title", "魔法星图");
        add("rotten_ruins.gui.magic.inventory", "背包中的法术");
        add("rotten_ruins.gui.magic.empty", "背包里没有法术");
        add("rotten_ruins.gui.magic.hint", "拖动法术放入槽位 · 点击槽位选中");
        add("rotten_ruins.gui.magic.slots", "法术槽 %s / %s");
        add("rotten_ruins.gui.magic.kind.active", "主动法术");
        add("rotten_ruins.gui.magic.kind.passive", "被动法术");
        add("rotten_ruins.gui.magic.mana", "耗蓝 %s");
        add("rotten_ruins.gui.magic.cooldown", "冷却 %s 秒");
        add("rotten_ruins.gui.magic.charge", "蓄力 %s 秒");

        // ---- 音效字幕（见 SoundData）----
        // 爆体音效：与 corruption 的 rip_body 同一条字幕文案，只是命名空间换成 hall。
        add("subtitles." + HallMod.MODID + ".rip_body", "躯体爆裂");

        // ====================================================================
        // ---- 简化版腐化内容（自 corruption 工程迁入，沿用 rotten_ruins 命名空间）----
        // 方块/物品/实体/效果都直接引用注册对象（CorruptionBlocks / CorruptionItems /
        // CorruptionEntityTypes / CorruptionEffects），翻译键由注册 ID 自动推导，
        // 注册名改动时编译器会直接报错，不会出现键名与注册名脱节。
        // ====================================================================

        // ---- A. 方块 block.rotten_ruins.<id>（21 个）----
        add(CorruptionBlocks.SPREAD_DIRT.get(), "腐染泥土");
        add(CorruptionBlocks.SPREAD_STONE.get(), "腐染原石");
        add(CorruptionBlocks.INFECTION_COBBLESTONE_BLOCK.get(), "腐染圆石");
        add(CorruptionBlocks.SPREAD_INFECTION_LEAVES.get(), "腐染树叶");
        // corruption 里该方块注册为 spread_infection_wood，但那边两个语言文件都缺这条译名
        add(CorruptionBlocks.SPREAD_INFECTION_WOOD.get(), "腐染原木");
        add(CorruptionBlocks.CORRUPTION_GRASS.get(), "腐染矮草");
        add(CorruptionBlocks.CORRUPTION_TRACHEA.get(), "呼吸道");
        add(CorruptionBlocks.CONNECT_BLOCK.get(), "结缔块");
        add(CorruptionBlocks.DECIDUAL_TISSUE_BLOCK.get(), "蜕膜组织");
        add(CorruptionBlocks.FLESHBED.get(), "血肉温床");
        add(CorruptionBlocks.DEGENERATE_REMAINS_SMALL.get(), "腐化种小型遗骸");
        add(CorruptionBlocks.DEGENERATE_REMAINS_MEDIUM.get(), "腐化种中型遗骸");
        add(CorruptionBlocks.DEGENERATE_REMAINS_LARGE.get(), "腐化种大型遗骸");
        add(CorruptionBlocks.HOST_REMAINS_SMALL.get(), "同化种小型遗骸");
        add(CorruptionBlocks.HOST_REMAINS_MEDIUM.get(), "同化种中型遗骸");
        add(CorruptionBlocks.HOST_REMAINS_LARGE.get(), "同化种大型遗骸");
        add(CorruptionBlocks.REFACTORED_REMAINS_SMALL.get(), "重构种小型遗骸");
        add(CorruptionBlocks.REFACTORED_REMAINS_MEDIUM.get(), "重构种中型遗骸");
        add(CorruptionBlocks.REFACTORED_REMAINS_LARGE.get(), "重构种大型遗骸");
        add(CorruptionBlocks.VARIABLE_HEIGHT_CROP.get(), "腐染经络");
        add(CorruptionBlocks.SPIRACLE_BLOCK.get(), "活体气门");

        // ---- B. 物品 item.rotten_ruins.<id>（42 个：材料 14 + 工具 5 + 刷怪蛋 23）----
        // 材料
        add(CorruptionItems.INFECTION_BONE.get(), "感染的骨头");
        add(CorruptionItems.HARD_FRAGMENTS.get(), "硬质碎片");
        add(CorruptionItems.CHITIN_POLYMER.get(), "聚合甲壳");
        add(CorruptionItems.LIVE_MEAT.get(), "活体肉块");
        add(CorruptionItems.HOST_MEAT.get(), "被同化的肉");
        add(CorruptionItems.HOST_CARAPACE.get(), "同化种甲壳");
        add(CorruptionItems.HOST_TOOTH.get(), "被同化的牙齿");
        add(CorruptionItems.DEGENERATE_HEART.get(), "腐化之心");
        add(CorruptionItems.LONG_ARM_BEAST_HEAD.get(), "长臂兽头颅");
        add(CorruptionItems.LONG_ARM_BEAST_TOOTH.get(), "长臂兽骨齿");
        add(CorruptionItems.ACID_SAC.get(), "酸囊");
        add(CorruptionItems.ACID_BALL.get(), "酸液球");
        add(CorruptionItems.SYRINGE.get(), "注射器");
        add(CorruptionItems.SYRINGE_BLOODY_CONCENTRATE.get(), "浓缩血液注射器");
        // 工具
        add(CorruptionItems.CRUDE_AXE.get(), "粗制斧");
        add(CorruptionItems.CRUDE_HOE.get(), "粗制锄");
        add(CorruptionItems.CRUDE_PICKAXE.get(), "粗制镐");
        add(CorruptionItems.CRUDE_SHOVEL.get(), "粗制铲");
        add(CorruptionItems.CRUDE_SWORD.get(), "粗制剑");
        // 刷怪蛋（键名取实体 ID，值取「<实体名>刷怪蛋」）
        add(CorruptionItems.CORRUPTION_ZOMBIE_SPAWN_EGG.get(), "同化僵尸刷怪蛋");
        add(CorruptionItems.CORRUPTION_PLAYER_SPAWN_EGG.get(), "同化玩家刷怪蛋");
        add(CorruptionItems.CORRUPTION_COW_SPAWN_EGG.get(), "同化牛刷怪蛋");
        add(CorruptionItems.CORRUPTION_SHEEP_SPAWN_EGG.get(), "同化羊刷怪蛋");
        add(CorruptionItems.CORRUPTION_PIG_SPAWN_EGG.get(), "同化猪刷怪蛋");
        add(CorruptionItems.CORRUPTION_BAT_SPAWN_EGG.get(), "同化蝙蝠刷怪蛋");
        add(CorruptionItems.CORRUPTION_SKELETON_SPAWN_EGG.get(), "同化骷髅刷怪蛋");
        add(CorruptionItems.CORRUPTION_BONE_RUINS_SPAWN_EGG.get(), "骷髅遗骸刷怪蛋");
        add(CorruptionItems.CORRUPTION_VILLAGER_SPAWN_EGG.get(), "同化村民刷怪蛋");
        add(CorruptionItems.CORRUPTION_WOLF_SPAWN_EGG.get(), "同化狼刷怪蛋");
        add(CorruptionItems.CORRUPTION_CHICKEN_SPAWN_EGG.get(), "同化鸡刷怪蛋");
        add(CorruptionItems.DEGENERATE_PIG_SPAWN_EGG.get(), "腐化猪刷怪蛋");
        add(CorruptionItems.DEGENERATE_ZOMBIE_SPAWN_EGG.get(), "腐化僵尸刷怪蛋");
        add(CorruptionItems.DEGENERATE_VILLAGER_SPAWN_EGG.get(), "腐化村民刷怪蛋");
        add(CorruptionItems.DEGENERATE_COW_SPAWN_EGG.get(), "腐化牛刷怪蛋");
        add(CorruptionItems.GROVELER_SPAWN_EGG.get(), "匍匐者刷怪蛋");
        add(CorruptionItems.ATTACKER_SPAWN_EGG.get(), "袭击者刷怪蛋");
        add(CorruptionItems.BEARER_SPAWN_EGG.get(), "孕育者刷怪蛋");
        add(CorruptionItems.LONG_ARM_BEAST_SPAWN_EGG.get(), "长臂兽刷怪蛋");
        add(CorruptionItems.SHOCKER_SPAWN_EGG.get(), "震撼者刷怪蛋");
        add(CorruptionItems.HUMUS_COLUMN_SPAWN_EGG.get(), "Ⅰ阶腐植柱刷怪蛋");
        add(CorruptionItems.SUMMONING_PILLAR_SPAWN_EGG.get(), "Ⅰ阶召唤柱刷怪蛋");
        add(CorruptionItems.ORIGINAL_BUG_SPAWN_EGG.get(), "原虫刷怪蛋");

        // ---- C. 实体 entity.rotten_ruins.<id>（23 个）----
        add(CorruptionEntityTypes.CORRUPTION_ZOMBIE.get(), "同化僵尸");
        add(CorruptionEntityTypes.CORRUPTION_PLAYER.get(), "同化玩家");
        add(CorruptionEntityTypes.CORRUPTION_COW.get(), "同化牛");
        add(CorruptionEntityTypes.CORRUPTION_SHEEP.get(), "同化羊");
        add(CorruptionEntityTypes.CORRUPTION_PIG.get(), "同化猪");
        add(CorruptionEntityTypes.CORRUPTION_BAT.get(), "同化蝙蝠");
        add(CorruptionEntityTypes.CORRUPTION_SKELETON.get(), "同化骷髅");
        add(CorruptionEntityTypes.CORRUPTION_BONE_RUINS.get(), "骷髅遗骸");
        add(CorruptionEntityTypes.CORRUPTION_VILLAGER.get(), "同化村民");
        add(CorruptionEntityTypes.CORRUPTION_WOLF.get(), "同化狼");
        add(CorruptionEntityTypes.CORRUPTION_CHICKEN.get(), "同化鸡");
        add(CorruptionEntityTypes.DEGENERATE_PIG.get(), "腐化猪");
        add(CorruptionEntityTypes.DEGENERATE_ZOMBIE.get(), "腐化僵尸");
        add(CorruptionEntityTypes.DEGENERATE_VILLAGER.get(), "腐化村民");
        add(CorruptionEntityTypes.DEGENERATE_COW.get(), "腐化牛");
        add(CorruptionEntityTypes.GROVELER.get(), "匍匐者");
        add(CorruptionEntityTypes.ATTACKER.get(), "袭击者");
        add(CorruptionEntityTypes.BEARER.get(), "孕育者");
        add(CorruptionEntityTypes.LONG_ARM_BEAST.get(), "长臂兽");
        add(CorruptionEntityTypes.SHOCKER.get(), "震撼者");
        add(CorruptionEntityTypes.HUMUS_COLUMN.get(), "Ⅰ阶腐植柱");
        add(CorruptionEntityTypes.SUMMONING_PILLAR.get(), "Ⅰ阶召唤柱");
        add(CorruptionEntityTypes.ORIGINAL_BUG.get(), "原虫");

        // ---- D. 状态效果 effect.rotten_ruins.<id>（6 个）----
        add(CorruptionEffects.CORRUPTION.get(), "腐化");
        add(CorruptionEffects.LYSIS.get(), "裂解");
        add(CorruptionEffects.MIASMA.get(), "绝望");
        add(CorruptionEffects.SCLEROSIS.get(), "硬化");
        add(CorruptionEffects.BLEEDING.get(), "流血");
        add(CorruptionEffects.FEAR.get(), "恐惧");

        // ---- E. 成就 advancements.rotten_ruins.<path> ----
        // 键名与 AdvancementProviderData 里生成 display 时用的键同源，避免两边手写不一致。
        // 树根：玩家第一次进入世界时触发；标题另起，悬浮说明按需求原文显示。
        add(AdvancementProviderData.titleKey(AdvancementProviderData.ROOT_PATH), "腐朽之疫");
        add(AdvancementProviderData.descriptionKey(AdvancementProviderData.ROOT_PATH), "身为神，体验作为人的自己");
        // 死亡分支：获得死亡之星（DeathStar）时触发。
        add(AdvancementProviderData.titleKey(AdvancementProviderData.DEATH_PATH), "死亡");
        add(AdvancementProviderData.descriptionKey(AdvancementProviderData.DEATH_PATH), "获得死亡之星");
    }
}
