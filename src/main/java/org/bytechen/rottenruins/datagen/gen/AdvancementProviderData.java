package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.RequirementsStrategy;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ForgeAdvancementProvider;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.advancement.FirstJoinTrigger;
import org.bytechen.rottenruins.advancement.SourceMilestoneTrigger;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 成就树的数据生成（输出到 {@code data/rotten_ruins/advancements/}）。
 *
 * <p>当前树：树根 {@code rotten_ruins:root}（玩家第一次进入世界时由
 * {@link FirstJoinTrigger} 发放）+ 「死亡」分支 {@code rotten_ruins:death}（获得死亡之星时触发）
 * + 「魔法」分支 {@code rotten_ruins:magic}（获得源荷砂时触发）。
 * 标题 / 描述走语言键（{@code advancements.rotten_ruins.<path>.*}），中文文案见 {@code LangDataCN}。</p>
 *
 * <p><b>如何扩展成完整成就树</b>：在 {@code RuinsAdvancements#buildBranches} 里用
 * {@link #node} 把子节点挂到 {@code root} 下，然后在 {@code LangDataCN} / {@code LangDataEN}
 * 里补 {@code advancements.rotten_ruins.<path>.title} 与 {@code .description} 两个键即可。
 * 节点 id / 语言键 / 图标 / 框类型都由这几个 helper 统一生成，不需要手写 JSON。</p>
 */
public class AdvancementProviderData extends ForgeAdvancementProvider {

    /** 树根的 path；完整 id 是 {@code rotten_ruins:root}。 */
    public static final String ROOT_PATH = "root";

    /** 「死亡」分支的 path；完整 id 是 {@code rotten_ruins:death}。 */
    public static final String DEATH_PATH = "death";

    /** 「魔法」分支的 path；完整 id 是 {@code rotten_ruins:magic}。 */
    public static final String MAGIC_PATH = "magic";

    /** 源荷生产线位于魔法分支下。 */
    public static String sourcePath(String name) { return MAGIC_PATH+"/"+name; }

    public AdvancementProviderData(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                                   ExistingFileHelper fileHelper) {
        super(output, registries, fileHelper, List.of(new RuinsAdvancements()));
    }

    // ==================== 子节点 helper（新增成就树节点时直接用） ====================

    /** 拼出成就 id：{@code rotten_ruins:<path>}。 */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(HallMod.MODID, path);
    }

    /** 成就标题的语言键；与 {@link #id(String)} 用同一个 path，避免两处手写不一致。 */
    public static String titleKey(String path) {
        return "advancements." + HallMod.MODID + "." + path + ".title";
    }

    /** 成就描述（悬浮说明）的语言键。 */
    public static String descriptionKey(String path) {
        return "advancements." + HallMod.MODID + "." + path + ".description";
    }

    private static Component title(String path) {
        return Component.translatable(titleKey(path));
    }

    private static Component description(String path) {
        return Component.translatable(descriptionKey(path));
    }

    /**
     * 生成一个挂在 {@code parent} 下的节点 Builder。
     *
     * <p>display 的 title / description 自动使用 {@code advancements.rotten_ruins.<path>.*}；
     * toast 与聊天栏广播默认打开，{@code hidden} 默认关闭，需要隐藏的节点自行再调 {@code display(...)}。</p>
     */
    public static Advancement.Builder node(Advancement parent, String path, ItemLike icon, FrameType frame) {
        return Advancement.Builder.advancement()
                .parent(parent)
                .display(icon, title(path), description(path), null, frame, true, true, false);
    }

    // ==================== 实际生成内容 ====================

    private static final class RuinsAdvancements implements AdvancementGenerator {

        /** 树根背景：沿用腐化地貌的感染圆石纹理（成就面板会把这张图平铺作背景）。 */
        private static final ResourceLocation BACKGROUND =
                new ResourceLocation(HallMod.MODID, "textures/block/infection_cobblestone_block.png");

        @Override
        public void generate(HolderLookup.Provider registries, Consumer<Advancement> saver,
                             ExistingFileHelper fileHelper) {
            Advancement root = Advancement.Builder.advancement()
                    .display(
                            RegisterItem.HALL_BONE_FRAGMENTS.get(),
                            title(ROOT_PATH),
                            description(ROOT_PATH),
                            BACKGROUND,
                            FrameType.TASK,
                            // show_toast / announce_to_chat：首次进入世界立刻弹出成就提示并在聊天栏广播。
                            true, true, false)
                    // 判据 id 与 FirstJoinTrigger.ID 对应；运行时由 AdvancementEventHandler 触发。
                    .addCriterion("first_join", new FirstJoinTrigger.TriggerInstance(ContextAwarePredicate.ANY))
                    .save(saver, id(ROOT_PATH), fileHelper);

            buildBranches(root, saver, fileHelper);
        }

        /**
         * 成就树分支的挂载点。
         *
         * <p>现有分支：「死亡」—— 获得死亡之星；「魔法」—— 获得源荷砂。
         * 判定用原版 {@code minecraft:inventory_changed}，玩家把对应物品拿进背包（合成 / 拾取 /
         * /give 等都会走容器变动）即完成，不需要自定义判据。</p>
         *
         * <p>继续扩展时照抄这个模式：{@code node(root 或其它父节点, path, 图标, FrameType)}
         * → {@code .addCriterion(...)} → {@code .save(saver, id(path), fileHelper)}，
         * 再到 {@code LangDataCN} / {@code LangDataEN} 补两个语言键。</p>
         */
        private static void buildBranches(Advancement root, Consumer<Advancement> saver,
                                          ExistingFileHelper fileHelper) {
            // ---- 死亡：获得死亡之星 ----
            node(root, DEATH_PATH, RegisterItem.DEATH_STAR.get(), FrameType.GOAL)
                    .addCriterion("death_star",
                            InventoryChangeTrigger.TriggerInstance.hasItems(RegisterItem.DEATH_STAR.get()))
                    .save(saver, id(DEATH_PATH), fileHelper);

            // ---- 魔法：获得源荷砂，作为魔法分支的起点 ----
            Advancement magic=node(root, MAGIC_PATH, RegisterItem.SOURCE_CHARGE_SAND.get(), FrameType.GOAL)
                    .addCriterion("source_charge_sand",
                            InventoryChangeTrigger.TriggerInstance.hasItems(RegisterItem.SOURCE_CHARGE_SAND.get()))
                    .save(saver, id(MAGIC_PATH), fileHelper);
            buildSourceBranch(magic,saver,fileHelper);
        }

        private static void buildSourceBranch(Advancement magic,Consumer<Advancement> saver,ExistingFileHelper helper) {
            // 勘测：有仪器，再找到真实的高势区，最后建立接点。
            Advancement meter=obtain(magic,"field_meter",RegisterItem.SIMPLE_FIELD_METER.get(),saver,helper);
            Advancement high=milestone(meter,"high_potential",RegisterItem.SIMPLE_FIELD_METER.get(),SourceMilestoneTrigger.Milestone.HIGH_POTENTIAL,saver,helper);
            obtain(high,"grounding_stake",RegisterBlock.GROUNDING_STAKE.get(),saver,helper);

            // 生产线：设备物品用原版持有判据，调参和通电需要实际操作。
            Advancement wire=obtain(magic,"source_wire",RegisterBlock.SOURCE_WIRE.get(),saver,helper);
            Advancement filter=obtain(wire,"source_filter",RegisterBlock.SOURCE_FILTER.get(),saver,helper);
            Advancement tuned=milestone(filter,"tuned_filter",RegisterBlock.SOURCE_FILTER.get(),SourceMilestoneTrigger.Milestone.TUNED_FILTER,saver,helper);
            milestone(tuned,"void_tuning",RegisterItem.VOID_MANA_BOTTLE.get(),SourceMilestoneTrigger.Milestone.VOID_TUNING,saver,helper);
            Advancement collector=obtain(tuned,"source_collector",RegisterBlock.SOURCE_COLLECTOR.get(),saver,helper);
            Advancement circuit=milestone(collector,"powered_circuit",RegisterBlock.SOURCE_COLLECTOR.get(),SourceMilestoneTrigger.Milestone.POWERED_CIRCUIT,saver,helper);
            ItemLike[] bottles={RegisterItem.LIGHT_MANA_BOTTLE.get(),RegisterItem.FIRE_MANA_BOTTLE.get(),RegisterItem.WATER_MANA_BOTTLE.get(),
                    RegisterItem.NATURE_MANA_BOTTLE.get(),RegisterItem.DEATH_MANA_BOTTLE.get(),RegisterItem.VOID_MANA_BOTTLE.get(),RegisterItem.MYSTERIOUS_MANA_BOTTLE.get()};
            Advancement firstBottle=obtainSet(circuit,"element_bottle",RegisterItem.WATER_MANA_BOTTLE.get(),false,bottles,saver,helper);
            obtainSet(firstBottle,"all_bottles",RegisterItem.MYSTERIOUS_MANA_BOTTLE.get(),true,bottles,saver,helper);

            // 板材：七系收藏使用独立条件，依次获得即可累计完成。
            Advancement base=obtain(magic,"source_plate",RegisterItem.SOURCE_MANA_PLATE.get(),saver,helper);
            ItemLike[] plates={RegisterItem.LIGHT_MANA_PLATE.get(),RegisterItem.FIRE_MANA_PLATE.get(),RegisterItem.WATER_MANA_PLATE.get(),
                    RegisterItem.NATURE_MANA_PLATE.get(),RegisterItem.DEATH_MANA_PLATE.get(),RegisterItem.VOID_MANA_PLATE.get(),RegisterItem.MYSTERIOUS_MANA_PLATE.get()};
            Advancement firstPlate=obtainSet(base,"element_plate",RegisterItem.WATER_MANA_PLATE.get(),false,plates,saver,helper);
            obtainSet(firstPlate,"all_plates",RegisterItem.MYSTERIOUS_MANA_PLATE.get(),true,plates,saver,helper);
        }

        private static Advancement obtain(Advancement parent,String name,ItemLike item,Consumer<Advancement> saver,ExistingFileHelper helper) {
            String path=sourcePath(name);
            return node(parent,path,item,FrameType.TASK)
                    .addCriterion("obtain",InventoryChangeTrigger.TriggerInstance.hasItems(item)).save(saver,id(path),helper);
        }

        private static Advancement milestone(Advancement parent,String name,ItemLike icon,SourceMilestoneTrigger.Milestone event,
                                               Consumer<Advancement> saver,ExistingFileHelper helper) {
            String path=sourcePath(name);
            return node(parent,path,icon,FrameType.GOAL).addCriterion("perform",SourceMilestoneTrigger.criterion(event)).save(saver,id(path),helper);
        }

        private static Advancement obtainSet(Advancement parent,String name,ItemLike icon,boolean all,ItemLike[] items,
                                             Consumer<Advancement> saver,ExistingFileHelper helper) {
            String path=sourcePath(name);
            var builder=node(parent,path,icon,all?FrameType.CHALLENGE:FrameType.TASK);
            for(ItemLike item:items) builder.addCriterion("obtain_"+ForgeRegistries.ITEMS.getKey(item.asItem()).getPath(),
                    InventoryChangeTrigger.TriggerInstance.hasItems(item));
            builder.requirements(all?RequirementsStrategy.AND:RequirementsStrategy.OR);
            return builder.save(saver,id(path),helper);
        }
    }
}
