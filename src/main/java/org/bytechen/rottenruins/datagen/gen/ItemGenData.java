package org.bytechen.rottenruins.datagen.gen;

import org.bytechen.rottenruins.HallMod;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.overworld.registry.items.BaseGlowingGeoItem;
import org.bytechen.rottenruins.content.magic.base.StaffBase;

import java.util.Set;

public class ItemGenData extends ItemModelProvider {
    public ItemGenData(PackOutput output, ExistingFileHelper efh) { super(output, HallMod.MODID, efh); }

    /**
     * 不自动生成模型的物品 —— 它们的模型由<b>手写</b>维护，放在
     * {@code src/main/resources/assets/rotten_ruins/models/item/<名字>.json}。
     *
     * <h3>加进来意味着什么</h3>
     * <p>下面的 {@link #registerModels()} 会遍历整个 {@code rotten_ruins} 命名空间自动生成
     * {@code minecraft:item/generated} 模型。而在生成模型的同时，Forge 的
     * {@code ExistingFileHelper} 会<b>校验它引用的贴图存在</b> —— 贴图没画好就会直接抛
     * {@code IllegalArgumentException: Texture rotten_ruins:item/xxx does not exist in any
     * known resource pack}，整个 {@code runData} 失败。所以"代码先写、贴图后补"的物品
     * 必须放进这个名单。</p>
     *
     * <p><b>注意</b>：{@code main} 的资源优先于 {@code src/generated}。名单里漏登的物品，
     * datagen 会照样在同一路径再写一份"只有 layer0"的模型 —— 当前者带自定义 loader /
     * display 时，两份文件的内容是不同的，谁先生效完全取决于资源顺序。</p>
     *
     * <h3>贴图画好之后怎么办</h3>
     * <ul>
     *   <li>想让模型<b>回到自动生成</b>：把名字从本名单里删掉，把手写的那份 json 删掉，
     *       跑一次 {@code runData} 即可；</li>
     *   <li>想<b>继续手写</b>（例如要 handheld、要特殊显示）：保持在本名单里，只补贴图。</li>
     * </ul>
     */
    private static final Set<String> MANUAL_WHITELIST = Set.of(
            "domerite_sword", "domerite_pickaxe", "domerite_axe", "domerite_shovel", "domerite_hoe","domerite_longsword","crimson_vow","silent_daylight",
            // 虚空剑：模型带自定义 loader（rotten_ruins:cosmic，宇宙星空层）与手调 display，
            // 自动生成会盖成纯 layer0，必须手写。
            "void_sword",
            // 魔法更新：贴图待补，先用手写模型
            "fire_ball", "spear_of_judgment", "suppression_field",
            // 疾行 / 奥数立方：贴图已到位（sprint.png / olympiad_cube.png），模型仍是手写的；
            // 想改回自动生成就删掉这两行 + 手写 json。
            "sprint",
            "olympiad_cube",
            // 灵魂吸引：贴图仍缺（textures/item/soul_drink.png 不存在，模型暂借 minecraft:item/ghast_tear）
            "soul_drink",
            // 新法术 / 虚空精粹：贴图未到位，模型借用原版或 void_bottle（见 models/item/*.json）
            "shield_ward", "bark_armor", "freezing_rain", "flame_slash", "holy_wings", "black_hole",
            "void_essence",
            // 奥秘仪台：方块几何由 GeckoLib 提供，方块模型留空；物品图标单独手写。
            "potential_crafting_table"
    );

    @Override
    protected void registerModels() {
        ForgeRegistries.ITEMS.getValues().forEach(item -> {
            ResourceLocation loc = ForgeRegistries.ITEMS.getKey(item);
            if (loc != null && loc.getNamespace().equals(HallMod.MODID)) {
                if(item instanceof BaseGlowingGeoItem)return;
                String path = loc.getPath();
                if (path.equals("rampart_spawn_egg")) {
                    withExistingParent(path, mcLoc("item/template_spawn_egg"));
                    return;
                }
                if (item instanceof BlockItem || MANUAL_WHITELIST.contains(path)) return;
                if (path.equals("simple_field_meter")) {
                    fieldMeter(path);
                    return;
                }
                if (isHandheld(item)) handheldItem(path);
                else this.basicItem(item);
            }
        });
    }

    private void fieldMeter(String path) {
        var medium = withExistingParent(path + "_medium", mcLoc("item/generated"))
                .texture("layer0", modLoc("item/" + path + "_medium"));
        var high = withExistingParent(path + "_high", mcLoc("item/generated"))
                .texture("layer0", modLoc("item/" + path + "_high"));
        withExistingParent(path, mcLoc("item/generated"))
                .texture("layer0", modLoc("item/" + path + "_low"))
                .override().predicate(modLoc("field_potential"), 0.5F).model(medium).end()
                .override().predicate(modLoc("field_potential"), 1.0F).model(high).end();
    }

    /**
     * 判断一件物品该不该用 {@code minecraft:item/handheld} 模型。
     *
     * <p>工具/剑之外，{@link StaffBase 法杖} 也走 handheld —— 它们和剑一样是
     * 手持长条状物品，用 {@code item/generated} 会在手里显示成"贴在掌心的一块贴图"。
     * 新加法杖不需要动这里，只要它继承 {@link StaffBase} 就会自动被认出来。</p>
     */
    private boolean isHandheld(Item item) {
        return item instanceof SwordItem || item instanceof PickaxeItem || item instanceof AxeItem
                || item instanceof ShovelItem || item instanceof HoeItem || item instanceof StaffBase;
    }
    protected void handheldItem(String path) { withExistingParent(path, mcLoc("item/handheld")).texture("layer0", modLoc("item/" + path)); }
}
