package org.bytechen.rottenruins.datagen.gen.lang;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.utils.TranslateUtils;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public class LangDataCN extends LanguageProvider {
    public LangDataCN(PackOutput output, String locale) { super(output, HallMod.MODID, locale); }

    @Override
    protected void addTranslations() {
        add("itemGroup." + HallMod.MODID + ".main", "腐朽之疫:王庭");
        // 创造物品栏的分区隔断行标题（见 SectionedCreativeModeTab / RegisterTab）
        add("itemGroup." + HallMod.MODID + ".main.section.creatures", "刷怪蛋");
        add("itemGroup." + HallMod.MODID + ".main.section.materials", "材料");
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
        add("block." + HallMod.MODID + ".flesh_rift.no_dimension", "裂隙的另一端尚未形成……");

        // ---- 魔法属性（见 RegisterAttributes） ----
        // 键名必须与 RangedAttribute 构造器里写死的那个字符串**完全一致**，
        // 对不上就会在属性界面里显示原始键名。
        add("attribute.name." + HallMod.MODID + ".max_mana", "法力上限");
        add("attribute.name." + HallMod.MODID + ".mana_regeneration", "法力回复速度");
        add("attribute.name." + HallMod.MODID + ".mana_restore", "法力回复量");
        add("attribute.name." + HallMod.MODID + ".spell_power", "法术强度");
        add("attribute.name." + HallMod.MODID + ".magic_slots", "魔法槽位");

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

        // ---- 音效字幕（见 SoundData）----
        // 爆体音效：与 corruption 的 rip_body 同一条字幕文案，只是命名空间换成 hall。
        add("subtitles." + HallMod.MODID + ".rip_body", "躯体爆裂");

        // ====================================================================
        // ---- 简化版腐化内容（自 corruption 工程迁入，沿用 rotten_ruins 命名空间）----
        // 这些方块/物品/实体/效果的注册代码尚未迁入本工程，因此先用字符串直接登记
        // "<类型>.rotten_ruins.<id>" 翻译键；待注册迁入后键名与注册 ID 一一对应，
        // runData 重新生成 lang json 时这些键不会丢失。
        // ====================================================================

        // ---- A. 方块 block.rotten_ruins.<id>（21 个）----
        add("block." + HallMod.MODID + ".spread_dirt", "腐染泥土");
        add("block." + HallMod.MODID + ".spread_stone", "腐染原石");
        add("block." + HallMod.MODID + ".infection_cobblestone_block", "腐染圆石");
        add("block." + HallMod.MODID + ".spread_infection_leaves", "腐染树叶");
        // corruption 里该方块注册为 spread_infection_wood，但那边两个语言文件都缺这条译名
        add("block." + HallMod.MODID + ".spread_infection_wood", "腐染原木");
        add("block." + HallMod.MODID + ".corruption_grass", "腐染矮草");
        add("block." + HallMod.MODID + ".corruption_trachea", "呼吸道");
        add("block." + HallMod.MODID + ".connect_block", "结缔块");
        add("block." + HallMod.MODID + ".decidual_tissue_block", "蜕膜组织");
        add("block." + HallMod.MODID + ".fleshbed", "血肉温床");
        add("block." + HallMod.MODID + ".degenerate_remains_small", "腐化种小型遗骸");
        add("block." + HallMod.MODID + ".degenerate_remains_medium", "腐化种中型遗骸");
        add("block." + HallMod.MODID + ".degenerate_remains_large", "腐化种大型遗骸");
        add("block." + HallMod.MODID + ".host_remains_small", "同化种小型遗骸");
        add("block." + HallMod.MODID + ".host_remains_medium", "同化种中型遗骸");
        add("block." + HallMod.MODID + ".host_remains_large", "同化种大型遗骸");
        add("block." + HallMod.MODID + ".refactored_remains_small", "重构种小型遗骸");
        add("block." + HallMod.MODID + ".refactored_remains_medium", "重构种中型遗骸");
        add("block." + HallMod.MODID + ".refactored_remains_large", "重构种大型遗骸");
        add("block." + HallMod.MODID + ".variable_height_crop", "腐染经络");
        add("block." + HallMod.MODID + ".spiracle_block", "活体气门");

        // ---- B. 物品 item.rotten_ruins.<id>（42 个：材料 14 + 工具 5 + 刷怪蛋 23）----
        // 材料
        add("item." + HallMod.MODID + ".infection_bone", "感染的骨头");
        add("item." + HallMod.MODID + ".hard_fragments", "硬质碎片");
        add("item." + HallMod.MODID + ".chitin_polymer", "聚合甲壳");
        add("item." + HallMod.MODID + ".live_meat", "活体肉块");
        add("item." + HallMod.MODID + ".host_meat", "被同化的肉");
        add("item." + HallMod.MODID + ".host_carapace", "同化种甲壳");
        add("item." + HallMod.MODID + ".host_tooth", "被同化的牙齿");
        add("item." + HallMod.MODID + ".degenerate_heart", "腐化之心");
        add("item." + HallMod.MODID + ".long_arm_beast_head", "长臂兽头颅");
        add("item." + HallMod.MODID + ".long_arm_beast_tooth", "长臂兽骨齿");
        add("item." + HallMod.MODID + ".acid_sac", "酸囊");
        add("item." + HallMod.MODID + ".acid_ball", "酸液球");
        add("item." + HallMod.MODID + ".syringe", "注射器");
        add("item." + HallMod.MODID + ".syringe_bloody_concentrate", "浓缩血液注射器");
        // 工具
        add("item." + HallMod.MODID + ".crude_axe", "粗制斧");
        add("item." + HallMod.MODID + ".crude_hoe", "粗制锄");
        add("item." + HallMod.MODID + ".crude_pickaxe", "粗制镐");
        add("item." + HallMod.MODID + ".crude_shovel", "粗制铲");
        add("item." + HallMod.MODID + ".crude_sword", "粗制剑");
        // 刷怪蛋（键名取实体 ID，值取「<实体名>刷怪蛋」）
        add("item." + HallMod.MODID + ".corruption_zombie_spawn_egg", "同化僵尸刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_player_spawn_egg", "同化玩家刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_cow_spawn_egg", "同化牛刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_sheep_spawn_egg", "同化羊刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_pig_spawn_egg", "同化猪刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_bat_spawn_egg", "同化蝙蝠刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_skeleton_spawn_egg", "同化骷髅刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_bone_ruins_spawn_egg", "骷髅遗骸刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_villager_spawn_egg", "同化村民刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_wolf_spawn_egg", "同化狼刷怪蛋");
        add("item." + HallMod.MODID + ".corruption_chicken_spawn_egg", "同化鸡刷怪蛋");
        add("item." + HallMod.MODID + ".degenerate_pig_spawn_egg", "腐化猪刷怪蛋");
        add("item." + HallMod.MODID + ".degenerate_zombie_spawn_egg", "腐化僵尸刷怪蛋");
        add("item." + HallMod.MODID + ".degenerate_villager_spawn_egg", "腐化村民刷怪蛋");
        add("item." + HallMod.MODID + ".degenerate_cow_spawn_egg", "腐化牛刷怪蛋");
        add("item." + HallMod.MODID + ".groveler_spawn_egg", "匍匐者刷怪蛋");
        add("item." + HallMod.MODID + ".attacker_spawn_egg", "袭击者刷怪蛋");
        add("item." + HallMod.MODID + ".bearer_spawn_egg", "孕育者刷怪蛋");
        add("item." + HallMod.MODID + ".long_arm_beast_spawn_egg", "长臂兽刷怪蛋");
        add("item." + HallMod.MODID + ".shocker_spawn_egg", "震撼者刷怪蛋");
        add("item." + HallMod.MODID + ".humus_column_spawn_egg", "Ⅰ阶腐植柱刷怪蛋");
        add("item." + HallMod.MODID + ".summoning_pillar_spawn_egg", "Ⅰ阶召唤柱刷怪蛋");
        add("item." + HallMod.MODID + ".original_bug_spawn_egg", "原虫刷怪蛋");

        // ---- C. 实体 entity.rotten_ruins.<id>（23 个）----
        add("entity." + HallMod.MODID + ".corruption_zombie", "同化僵尸");
        add("entity." + HallMod.MODID + ".corruption_player", "同化玩家");
        add("entity." + HallMod.MODID + ".corruption_cow", "同化牛");
        add("entity." + HallMod.MODID + ".corruption_sheep", "同化羊");
        add("entity." + HallMod.MODID + ".corruption_pig", "同化猪");
        add("entity." + HallMod.MODID + ".corruption_bat", "同化蝙蝠");
        add("entity." + HallMod.MODID + ".corruption_skeleton", "同化骷髅");
        add("entity." + HallMod.MODID + ".corruption_bone_ruins", "骷髅遗骸");
        add("entity." + HallMod.MODID + ".corruption_villager", "同化村民");
        add("entity." + HallMod.MODID + ".corruption_wolf", "同化狼");
        add("entity." + HallMod.MODID + ".corruption_chicken", "同化鸡");
        add("entity." + HallMod.MODID + ".degenerate_pig", "腐化猪");
        add("entity." + HallMod.MODID + ".degenerate_zombie", "腐化僵尸");
        add("entity." + HallMod.MODID + ".degenerate_villager", "腐化村民");
        add("entity." + HallMod.MODID + ".degenerate_cow", "腐化牛");
        add("entity." + HallMod.MODID + ".groveler", "匍匐者");
        add("entity." + HallMod.MODID + ".attacker", "袭击者");
        add("entity." + HallMod.MODID + ".bearer", "孕育者");
        add("entity." + HallMod.MODID + ".long_arm_beast", "长臂兽");
        add("entity." + HallMod.MODID + ".shocker", "震撼者");
        add("entity." + HallMod.MODID + ".humus_column", "Ⅰ阶腐植柱");
        add("entity." + HallMod.MODID + ".summoning_pillar", "Ⅰ阶召唤柱");
        add("entity." + HallMod.MODID + ".original_bug", "原虫");

        // ---- D. 状态效果 effect.rotten_ruins.<id>（6 个）----
        add("effect." + HallMod.MODID + ".corruption", "腐化");
        add("effect." + HallMod.MODID + ".lysis", "裂解");
        add("effect." + HallMod.MODID + ".miasma", "绝望");
        add("effect." + HallMod.MODID + ".sclerosis", "硬化");
        add("effect." + HallMod.MODID + ".bleeding", "流血");
        add("effect." + HallMod.MODID + ".fear", "恐惧");
    }
}
