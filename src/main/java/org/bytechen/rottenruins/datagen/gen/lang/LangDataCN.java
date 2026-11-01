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
        // 创造栏标签页 / 界面左上角的面板标题：带流动渐变（§6 垫底，特效关闭时退回原版金色）。
        add("itemGroup." + HallMod.MODID + ".main",
                "§6§[rrs:grad:#6FBF4A:#8B1E2D:speed=0.6]腐朽之疫§[/]");
        // 创造物品栏的分区隔断行标题（见 SectionedCreativeModeTab / RegisterTab）
        add("itemGroup." + HallMod.MODID + ".main.section.creatures", "刷怪蛋");
        add("itemGroup." + HallMod.MODID + ".main.section.materials", "材料");
        add("itemGroup." + HallMod.MODID + ".main.section.magic", "魔法");
        add("itemGroup." + HallMod.MODID + ".main.section.gear", "武器与工具");
        add("itemGroup." + HallMod.MODID + ".main.section.blocks", "方块");
        add("itemGroup." + HallMod.MODID + ".main.section.misc", "其它");
        add(RegisterItem.EXAMPLE_ITEM.get(), "测试物品");
        add(RegisterItem.SIMPLE_FIELD_METER.get(), "简易场强计");
        add(RegisterItem.VOID_SWORD.get(), "伪-虚无之刃");
        add(RegisterItem.HALL_BONE_FRAGMENTS.get(),  "王庭骨");
        add(RegisterItem.HALL_TENDON.get(), "王庭肌腱");
        add(RegisterItem.INF_ENDER_PEAR.get(), "§[rrs:glitch:amp=2.0]畸骸末影珍珠§[/]");
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
        add(EntityTypeRegistry.DEATH_HUNTER.get(), "死亡猎手");
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
        add(TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE, "§[rrs:glitch:amp=1.4]无法理解§[/]");
        add(TranslateUtils.DIFFICULTY_EASY_TOOLTIP, "轻松的体验氛围，适合养老玩家");
        add(TranslateUtils.DIFFICULTY_NORMAL_TOOLTIP, "标准游戏体验");
        // 难度档位文案是"单个 Component 直接绘制"（DifficultySelectScreen），可以安全地带标记；
        // 注意：走 FlowingNameColors/TooltipLines 逐字拍平的那些 lore 行不能加标记
        // （逐字重建会把标记拆散到不同组件里，反而显示成原文）。
        add(TranslateUtils.DIFFICULTY_HARD_TOOLTIP, "§[rrs:shake:amp=1.0]哦不不不！这无疑是难以战胜的§[/]");
        add(TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE_TOOLTIP, "§[rrs:glitch:amp=2.0]你确定你要这么做吗？§[/]");
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
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_SLASH, "斩击");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_SLASH,
                "左键命中即追加 %s 道蓝白长斩 · 每道 %s 点冰伤 + 目标最大生命 %s%% · %s 格内全体受击");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_WARD, "寒气");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_WARD,
                "举剑展开 %s 格寒气领域 · 持续回血、冻结生物、吹散飞行物");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_DEBUG,
                "调试 · 格挡结算 ×%s%% · 仅正面 %s · 使用时长 %s tick");

        // ---- 血肉庭园维度（hall:heall） ----
        // 该维度的地形完全复用现有王庭方块，因此这里只有通道方块的译名
        add(RegisterBlock.FLESH_RIFT.get(), "血肉裂隙");
        // 死亡核心：第一个通过 BER 绘制 mask 着色器层的方块
        add(RegisterBlock.DEATH_CORE.get(), "死亡核心");
        add("dimension." + HallMod.MODID + ".heall", "血肉庭园");
        add("biome." + HallMod.MODID + ".flesh_marrow", "血肉庭园");
        add(RegisterBlock.FLESH_RIFT.get().getDescriptionId() + ".no_dimension", "裂隙的另一端尚未形成……");

        // ---- 魔法：流派名 + 物品介绍（见 MagicType / MagicTooltips） ----
        // 流派名会被法术/饰品的介绍行用到；颜色由 MagicType#rgb() 提供，这里只管文字。
        add("magic_type." + HallMod.MODID + ".water", "水");
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
        add("attribute.name." + HallMod.MODID + ".spell_power_water", "水系法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_fire", "火焰法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_death", "死亡法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_nature", "自然法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_light", "光明法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_void", "虚空法术强度");
        add("attribute.name." + HallMod.MODID + ".spell_power_mysterious", "神秘法术强度");

        // ---- 魔法体系 · 物品 ----
        // 七个流派各有对应法力板，可用于合成与补充法杖魔能。
        add(RegisterItem.DEATH_MANA_PLATE.get(), "死亡魔法版材");
        add(RegisterItem.SOURCE_MANA_PLATE.get(), "源荷板材");
        add(RegisterItem.FIRE_MANA_PLATE.get(), "火焰魔法版材");
        add(RegisterItem.LIGHT_MANA_PLATE.get(), "光明魔法版材");
        add(RegisterItem.NATURE_MANA_PLATE.get(), "自然魔法版材");
        add(RegisterItem.VOID_MANA_PLATE.get(), "虚空魔法版材");
        add(RegisterItem.WATER_MANA_PLATE.get(), "水魔法版材");
        add(RegisterItem.MYSTERIOUS_MANA_PLATE.get(), "神秘魔法版材");
        add(RegisterItem.MAGIC_CATA.get(), "魔法催化剂");
        add(RegisterItem.VOID_RUNE.get(), "虚空符文");
        add(RegisterItem.MANA_BOTTLE.get(), "储魔瓶");
        add(RegisterItem.LIGHT_MANA_BOTTLE.get(), "储魔瓶[光明]");
        add(RegisterItem.FIRE_MANA_BOTTLE.get(), "储魔瓶[火]");
        add(RegisterItem.WATER_MANA_BOTTLE.get(), "储魔瓶[水]");
        add(RegisterItem.NATURE_MANA_BOTTLE.get(), "储魔瓶[自然]");
        add(RegisterItem.VOID_MANA_BOTTLE.get(), "储魔瓶[虚空]");
        add(RegisterItem.DEATH_MANA_BOTTLE.get(), "储魔瓶[死亡]");
        add(RegisterItem.MYSTERIOUS_MANA_BOTTLE.get(), "储魔瓶[神秘]");
        add(RegisterItem.SOURCE_CHARGE_SAND.get(), "源荷砂");
        add(RegisterItem.CHIP_NULL.get(), "空白芯片");
        add(RegisterItem.CHIP_ENHANCED.get(), "增强芯片");
        add(RegisterItem.CHIP_SCAN.get(), "扫描芯片");
        add(RegisterItem.CHIP_EXTRACT.get(), "提取芯片");
        add(RegisterBlock.GROUNDING_STAKE.get(), "接地桩");
        add(RegisterBlock.SOURCE_WIRE.get(), "源导线");
        add(RegisterBlock.SOURCE_FILTER.get(), "源荷滤波器");
        add(RegisterBlock.SOURCE_COLLECTOR.get(), "集荷器");
        add("gui.rotten_ruins.source.reference", "波段表");
        add("gui.rotten_ruins.source.mode.0", "手动调谐");
        add("gui.rotten_ruins.source.mode.1", "懒人模式");
        add("gui.rotten_ruins.source.mode.2", "波段表");
        add("gui.rotten_ruins.source.fine_tune", "手动微调");
        add("gui.rotten_ruins.source.preset_hint", "一键配置%s：fc %s / B %s / G %s / φ %s。仍需有效源导线回路。");
        add("gui.rotten_ruins.source.param.0.short", "中心频率 fc");
        add("gui.rotten_ruins.source.param.1.short", "带宽 B");
        add("gui.rotten_ruins.source.param.2.short", "增益 G");
        add("gui.rotten_ruins.source.param.3.short", "相位 φ");
        add("gui.rotten_ruins.source.ports_hint", "蓝口输入，橙口输出；空手潜行右键旋转。");
        add("gui.rotten_ruins.source.reference_hint", "窗口含边界；参数步长 0.1；蓝口输入，橙口输出");
        add("gui.rotten_ruins.source.spectrum", "场谱示波仪");
        add("gui.rotten_ruins.source.unmatched", "未匹配有效元素窗口");
        add("gui.rotten_ruins.source.tuned", "元素：%s");
        add("gui.rotten_ruins.source.power", "强度 %s/15");
        add("gui.rotten_ruins.source.input_bottle", "空瓶");
        add("gui.rotten_ruins.source.output_bottle", "产物");
        add("gui.rotten_ruins.source.no_power", "未通电：检查回路和开口方向");
        add("gui.rotten_ruins.source.no_filter", "输入端未连接上游滤波器");
        add("gui.rotten_ruins.source.invalid_band", "滤波参数未落入元素窗口");
        add("gui.rotten_ruins.source.conflict", "上游滤波器设置冲突");
        add("gui.rotten_ruins.source.output_full", "请取出产物后继续灌注");
        add("gui.rotten_ruins.source.charging", "正在灌注源荷");
        add("gui.rotten_ruins.source.insert_bottle", "放入普通储魔瓶开始灌注");
        add("gui.rotten_ruins.source.param.0", "中心频率 fc：%s");
        add("gui.rotten_ruins.source.param.1", "带宽 B：%s");
        add("gui.rotten_ruins.source.param.2", "增益 G：%s");
        add("gui.rotten_ruins.source.param.3", "相位 φ：%s");
        add("gui.rotten_ruins.source.param.0.hint", "势差释放的主频。范围 0–10，步长 0.1。");
        add("gui.rotten_ruins.source.param.1.hint", "通带宽度与相干性。范围 0–10，步长 0.1。");
        add("gui.rotten_ruins.source.param.2.hint", "输出幅值与换能强度。范围 0–10；有效窗口内增益越高，灌注越快。");
        add("gui.rotten_ruins.source.param.3.hint", "场相极性。仅有正相（+）与反相（−）；虚空要求反相。");
        add("gui.rotten_ruins.source.element.light", "光");
        add("gui.rotten_ruins.source.element.fire", "火");
        add("gui.rotten_ruins.source.element.water", "水");
        add("gui.rotten_ruins.source.element.nature", "自然");
        add("gui.rotten_ruins.source.element.death", "死亡");
        add("gui.rotten_ruins.source.element.void", "虚空");
        add("gui.rotten_ruins.source.element.mysterious", "神秘");
        add(RegisterItem.WAND.get(), "空法杖");
        add(RegisterItem.WAND_NATURE.get(), "自然法杖");
        add(RegisterItem.WAND_FIRE.get(), "火焰法杖");
        add(RegisterItem.WAND_DEATH.get(), "死亡法杖");
        add(RegisterItem.WAND_LIGHT.get(), "光明法杖");
        add(RegisterItem.WAND_VOID.get(), "虚空法杖");
        add(RegisterItem.WAND_WATER.get(), "水之法杖");
        add(RegisterItem.WAND_MYSTERIOUS.get(), "神秘法杖");
        add(RegisterItem.WAND_COMMON.get(), "通用法术法杖");
        add("enchantment.rotten_ruins.mana_efficiency", "节能");
        add("tooltip.rotten_ruins.wand_mana", "法杖魔能：%s / %s");
        // 法术（上游 1.20.1forge「魔法更新」）
        add(RegisterItem.FIRE_BALL.get(), "火球术");
        add(RegisterItem.SPEAR_OF_JUDGMENT.get(), "审判之矛");
        add(RegisterItem.SUPPRESSION_FIELD.get(), "抑制场");
        add(RegisterItem.TIDAL_PULSE.get(), "潮汐冲击");
        add(RegisterItem.BRIAR_SNARE.get(), "荆棘禁锢");
        add(RegisterItem.PURIFYING_LIGHT.get(), "净化之光");
        add(RegisterItem.ARCANE_STEP.get(), "奥术闪步");
        add(RegisterItem.SPRINT.get(), "疾行");
        add("item.rotten_ruins.tidal_pulse.desc", "击退周围生物；自身灭火，获得抗火、水下呼吸与海豚的恩惠");
        add("item.rotten_ruins.briar_snare.desc", "在准星处伤害并强力减速生物，附加虚弱与中毒");
        add("item.rotten_ruins.purifying_light.desc", "治疗、净化并保护友方；伤害敌对生物，对亡灵更强");
        add("item.rotten_ruins.arcane_step.desc", "闪现至准星附近可站立的位置，短暂获得速度提升");
        add("item.rotten_ruins.sprint.desc", "沿视线方向冲刺，动量等效于垂直抬升 5×法术强度（上限 30 格）后再放大 3 倍，伴随电火花；特殊使用时冷却减半");
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
        // ---- 施法轮盘 / 装载界面 ----
        add("key." + HallMod.MODID + ".cast_wheel", "打开/关闭施法轮盘");
        add("key." + HallMod.MODID + ".open_loadout", "魔法装载界面");
        add("rotten_ruins.gui.wheel.title", "施法轮盘");
        add("rotten_ruins.gui.wheel.loadout.title", "魔法装载");
        add("rotten_ruins.wheel.hint.hold_staff", "需要手持法杖才能从轮盘施法");
        add("rotten_ruins.wheel.hint.no_spells", "轮盘 I 上没有主动法术 — 按 V 配置");
        add("rotten_ruins.wheel.begin.sustaining", "维持中 — 右键或再按 R 结束");
        add("rotten_ruins.wheel.result.success", "施法成功");
        add("rotten_ruins.wheel.result.invalid_staff", "需要手持法杖");
        add("rotten_ruins.wheel.result.no_spell", "该方位没有法术");
        add("rotten_ruins.wheel.result.passive_spell", "被动法术不能施放");
        add("rotten_ruins.wheel.result.on_cooldown", "冷却中");
        add("rotten_ruins.wheel.result.not_enough_mana", "法力不足");
        add("rotten_ruins.wheel.result.not_charged", "蓄力不足");
        add("rotten_ruins.wheel.result.no_target", "没有目标");
        add("rotten_ruins.wheel.result.cancelled", "已取消");
        add("rotten_ruins.wheel.result.rejected", "被拒绝");
        add("rotten_ruins.wheel.result.invalid_slot", "方位无效");
        add("rotten_ruins.wheel.result.not_active", "没有进行中的施法");
        // 轮盘 HUD / 装载界面（datagen 生成，代码里全部走 WheelI18n.tr）
        add("rotten_ruins.wheel.title", "奥术轮盘");
        add("rotten_ruins.wheel.subtitle", "%s 开关  |  点击方位施法");
        add("rotten_ruins.wheel.active_sigils", "主动法术  %s / %s");
        add("rotten_ruins.wheel.mana", "法力  %s / %s");
        add("rotten_ruins.wheel.release_to_cast", "点击方位施法");
        add("rotten_ruins.wheel.rmb_cancel", "右键关闭");
        add("rotten_ruins.wheel.page", "轮盘  %s");
        add("rotten_ruins.wheel.ready", "就绪");
        add("rotten_ruins.wheel.maintain", "维持中 %s 秒");
        add("rotten_ruins.wheel.maintain_end", "右键 / 再按 R 结束");
        add("rotten_ruins.wheel.center.wheel", "轮盘");
        add("rotten_ruins.wheel.center.aim", "选择方位");
        add("rotten_ruins.wheel.center.no_active", "没有主动法术");
        add("rotten_ruins.wheel.center.sockets", "8 个方位");
        add("rotten_ruins.wheel.center.release_hint", "点击施法");
        add("rotten_ruins.wheel.center.bind_hint", "在装载界面绑定主动法术");
        add("rotten_ruins.wheel.center.drag_hint", "把法术拖到方位上");
        add("rotten_ruins.wheel.center.cooldown", "冷却 %s");
        add("rotten_ruins.wheel.center.not_enough_mana", "法力不足");
        add("rotten_ruins.wheel.center.charging", "蓄力 %s 秒");
        add("rotten_ruins.wheel.center.ready", "就绪  |  %s 法力");
        add("rotten_ruins.wheel.center.selected", "已选中");
        add("rotten_ruins.wheel.school.water", "水");
        add("rotten_ruins.wheel.school.fire", "火焰");
        add("rotten_ruins.wheel.school.death", "死亡");
        add("rotten_ruins.wheel.school.nature", "自然");
        add("rotten_ruins.wheel.school.light", "光明");
        add("rotten_ruins.wheel.school.void", "虚空");
        add("rotten_ruins.wheel.school.mysterious", "神秘");
        add("rotten_ruins.wheel.using.instant", "瞬发");
        add("rotten_ruins.wheel.using.charge", "蓄力");
        add("rotten_ruins.wheel.using.channel", "维持");
        add("rotten_ruins.wheel.loadout.subtitle", "轮盘配置  -  第 %s 页");
        add("rotten_ruins.wheel.loadout.close", "ESC  关闭");
        add("rotten_ruins.wheel.loadout.preview", "轮盘预览");
        add("rotten_ruins.wheel.loadout.preview_summary",
                "轮盘 %s  |  %s / %s 方位");
        add("rotten_ruins.wheel.loadout.drag_hint", "把法术拖到方位上");
        add("rotten_ruins.wheel.loadout.unbind_hint", "右键方位解绑");
        add("rotten_ruins.wheel.loadout.save_hint", "按 SAVE 后生效");
        add("rotten_ruins.wheel.loadout.codex", "法术图鉴");
        add("rotten_ruins.wheel.loadout.count_active", "%s 个主动");
        add("rotten_ruins.wheel.loadout.count_passive", "%s 个被动");
        add("rotten_ruins.wheel.loadout.tab.active", "主动");
        add("rotten_ruins.wheel.loadout.tab.passive", "被动");
        add("rotten_ruins.wheel.loadout.no_active", "没有主动法术");
        add("rotten_ruins.wheel.loadout.no_passive", "没有被动法术");
        add("rotten_ruins.wheel.loadout.passive_hint", "学会被动后会出现");
        add("rotten_ruins.wheel.loadout.equip_hint", "在魔法星图中装备法术");
        add("rotten_ruins.wheel.loadout.footer_readonly", "只读");
        add("rotten_ruins.wheel.loadout.footer_active", "主动法术");
        add("rotten_ruins.wheel.loadout.total", "共 %s 个");
        add("rotten_ruins.wheel.loadout.scroll", "%s-%s / %s  -  滚轮");
        add("rotten_ruins.wheel.loadout.slot_badge", "方位 %s");
        add("rotten_ruins.wheel.loadout.passive_always", "被动  -  始终生效");
        add("rotten_ruins.wheel.loadout.card_sub", "%s   %s 法力   %s");
        add("rotten_ruins.wheel.loadout.details", "详情");
        add("rotten_ruins.wheel.loadout.type", "类型");
        add("rotten_ruins.wheel.loadout.mana", "法力");
        add("rotten_ruins.wheel.loadout.cooldown", "冷却");
        add("rotten_ruins.wheel.loadout.charge", "蓄力");
        add("rotten_ruins.wheel.loadout.sustain", "维持");
        add("rotten_ruins.wheel.loadout.assigned", "已绑定");
        add("rotten_ruins.wheel.loadout.always_on", "始终生效");
        add("rotten_ruins.wheel.loadout.not_assigned", "未绑定");
        add("rotten_ruins.wheel.loadout.assigned_at", "轮盘 %s - 方位 %s");
        add("rotten_ruins.wheel.loadout.description", "描述");
        add("rotten_ruins.wheel.loadout.passive_warn1", "被动法术不能");
        add("rotten_ruins.wheel.loadout.passive_warn2", "放入轮盘");
        add("rotten_ruins.wheel.loadout.passive_lore",
                "被动法术装备后始终生效，不能放入轮盘。");
        add("rotten_ruins.wheel.loadout.desc.instant",
                "瞬发法术：无前摇，施法即刻生效。");
        add("rotten_ruins.wheel.loadout.desc.charge",
                "按住蓄力后释放；蓄力越久威力越强，过早松手可能失败。");
        add("rotten_ruins.wheel.loadout.desc.keep",
                "选中后起手并持续维持，持续消耗法力；松开结束维持。");
        add("rotten_ruins.wheel.loadout.socket", "方位 %s");
        add("rotten_ruins.wheel.loadout.empty_socket", "空方位");
        add("rotten_ruins.wheel.loadout.drag_here", "把法术拖到这里");
        add("rotten_ruins.wheel.loadout.no_selection", "未选择法术");
        add("rotten_ruins.wheel.loadout.click_hint", "点击法术或方位");
        add("rotten_ruins.wheel.loadout.wheel_page", "轮盘页");
        add("rotten_ruins.wheel.loadout.button.clear", "清空方位");
        add("rotten_ruins.wheel.loadout.button.autofill", "自动填充");
        add("rotten_ruins.wheel.loadout.button.save", "保存");
        add("rotten_ruins.wheel.loadout.hint",
                "把法术拖到方位   |   右键解绑   |   SAVE 保存到轮盘 %s");
        add("rotten_ruins.wheel.loadout.hint_short",
                "拖拽绑定   |   右键解绑   |   SAVE 保存当前页");
        add("rotten_ruins.wheel.loadout.hint_tiny", "拖拽绑定   |   SAVE 保存");
        add("rotten_ruins.wheel.loadout.per_second", "%s / 秒");
        add("rotten_ruins.wheel.loadout.bind_to_slot", "绑定到方位 %s");
        add("rotten_ruins.wheel.loadout.release_on_socket", "松开到方位");
        // ---- 分节符文本特效 · 预览屏 / 命令 ----
        add("rotten_ruins.gui.textfx.title", "§[rrs:grad:#6FBF4A:#8B1E2D]分节符文本特效§[/]");
        add("rotten_ruins.gui.textfx.hint", "每行实时动画；按 ESC 关闭；输入 /rrsfx 查看更多");
        add("rotten_ruins.command.rrsfx.enabled", "§a文本特效：开");
        add("rotten_ruins.command.rrsfx.disabled", "§c文本特效：关");

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
        // 成就标题/说明都是单个 Component 直接绘制（成就界面/Toast），可以安全使用文本特效标记。
        add(AdvancementProviderData.titleKey(AdvancementProviderData.ROOT_PATH),
                "§[rrs:grad:#6FBF4A:#8B1E2D:speed=0.5]腐朽之疫§[/]");
        add(AdvancementProviderData.descriptionKey(AdvancementProviderData.ROOT_PATH), "身为神，体验作为人的自己");
        // 死亡分支：获得死亡之星（DeathStar）时触发。
        add(AdvancementProviderData.titleKey(AdvancementProviderData.DEATH_PATH), "死亡");
        add(AdvancementProviderData.descriptionKey(AdvancementProviderData.DEATH_PATH),
                "§[rrs:glitch:amp=1.5]获得死亡之星§[/]");
        add(AdvancementProviderData.titleKey(AdvancementProviderData.MAGIC_PATH), "魔法，轻而易举啊！");
        add(AdvancementProviderData.descriptionKey(AdvancementProviderData.MAGIC_PATH), "一门掌握自然的学问");
        sourceAdvancement("field_meter","看不见，测得到","获得简易场强计，开始寻找势差");
        sourceAdvancement("high_potential","势在必得","携带简易场强计进入一个高势区");
        sourceAdvancement("grounding_stake","脚踏实地","获得接地桩，为势流建立接点");
        sourceAdvancement("source_wire","这回不是红石","获得源导线，准备连接高低势区");
        sourceAdvancement("source_filter","给混沌分个频","获得源荷滤波器");
        sourceAdvancement("tuned_filter","对上频道","将源荷滤波器调入任意有效元素窗口");
        sourceAdvancement("void_tuning","反着来","把滤波器调到虚空波段，并使用反相");
        sourceAdvancement("source_collector","收得住，才算本事","获得集荷器，准备灌注储魔瓶");
        sourceAdvancement("powered_circuit","有来有回","打开一台接入有效高低势回路的源荷设备");
        sourceAdvancement("element_bottle","把势流装进瓶子","获得任意一瓶元素储魔瓶");
        sourceAdvancement("all_bottles","七色收藏家","分别获得七系元素储魔瓶，无需同时持有");
        sourceAdvancement("source_plate","万法之基","获得源荷板材");
        sourceAdvancement("element_plate","魔法有了形状","获得任意一种元素魔法板材");
        sourceAdvancement("all_plates","万象归板","分别获得全部七系魔法板材，无需同时持有");
    }

    private void sourceAdvancement(String name,String title,String description) {
        String path=AdvancementProviderData.sourcePath(name);
        add(AdvancementProviderData.titleKey(path),title);
        add(AdvancementProviderData.descriptionKey(path),description);
    }
}
