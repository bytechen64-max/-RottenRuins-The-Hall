package org.bytechen.rottenruins.overworld.registry;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.IAutoRenderableItem;
import org.bytechen.rottenruins.overworld.registry.items.BaseGlowingGeoItem;
import org.bytechen.rottenruins.overworld.registry.items.GeoItemRenderManager;
import org.bytechen.rottenruins.overworld.registry.items.VoidSword;
import org.bytechen.rottenruins.overworld.registry.items.InfEnderPearItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.overworld.registry.items.*;
import org.bytechen.rottenruins.overworld.registry.items.magic.ManaPlateItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.WandItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.its.FireBall;
import org.bytechen.rottenruins.overworld.registry.items.magic.its.SpearOfJudgment;
import org.bytechen.rottenruins.overworld.registry.items.magic.its.SuppressionField;
import org.bytechen.rottenruins.overworld.registry.items.magic.its.TidalPulse;
import org.bytechen.rottenruins.overworld.registry.items.magic.its.BriarSnare;
import org.bytechen.rottenruins.overworld.registry.items.magic.its.PurifyingLight;
import org.bytechen.rottenruins.overworld.registry.items.magic.its.ArcaneStep;
import org.bytechen.rottenruins.overworld.registry.items.magic.its.Sprint;

import java.util.function.Supplier;

public class RegisterItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HallMod.MODID);

    public static RegistryObject<Item> registerSimpleItem(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().stacksTo(64)));
    }

    private static RegistryObject<Item> registerFilledManaBottle(String name) {
        return ITEMS.register(name,()->new Item(new Item.Properties().stacksTo(64).craftRemainder(MANA_BOTTLE.get())));
    }

    /**
     * Registers an item that implements {@link IAutoRenderableItem} and also
     * adds it to {@link GeoItemRenderManager} for automatic Geo model/renderer creation.
     * <p>
     * This mirrors the entity auto-registration pattern ({@code SplendidingEntityManager.registerAll}).
     * </p>
     *
     * @param name     registry name
     * @param supplier supplier for the item instance
     * @param <T>      item type implementing IAutoRenderableItem
     * @return RegistryObject holding the registered item
     */
    public static <T extends Item & IAutoRenderableItem> RegistryObject<T> registerGeoItem(
            String name, Supplier<T> supplier) {
        RegistryObject<T> regObj = ITEMS.register(name, supplier);
        GeoItemRenderManager.register(name, regObj);
        return regObj;
    }

    /**
     * Registers an existing {@link RegistryObject} item with {@link GeoItemRenderManager}
     * for automatic renderer creation. Use this when the item registration doesn't go through
     * {@link #registerGeoItem} directly.
     *
     * @param name   the registry name (same as used in ITEMS.register)
     * @param regObj the already-registered item
     * @param <T>    item type implementing IAutoRenderableItem
     * @return the same RegistryObject for chaining
     */
    public static <T extends Item & IAutoRenderableItem> RegistryObject<T> registerGeoRenderer(
            String name, RegistryObject<T> regObj) {
        GeoItemRenderManager.register(name, regObj);
        return regObj;
    }

    public static RegistryObject<Item> registerSpawnEgg(String name,
                                                        Supplier<? extends EntityType<? extends Mob>> entityTypeSupplier) {
        return ITEMS.register(name + "_spawn_egg",
                () -> new ForgeSpawnEggItem(entityTypeSupplier, -1, -1, new Item.Properties().stacksTo(64)));
    }



    public static final RegistryObject<Item> EXAMPLE_ITEM = registerSimpleItem("example_item");

    public static final RegistryObject<Item> SIMPLE_FIELD_METER = ITEMS.register("simple_field_meter",
            SimpleFieldMeterItem::new);

    /** VoidSword with cosmic starfield shader (from Live reference mod). */
    public static final RegistryObject<Item> VOID_SWORD = ITEMS.register("void_sword",
            VoidSword::new);

    /**
     * CrimsonVow — 同一个 {@code hall:cosmic} 遮罩填充 loader，但换了一整套
     * 着色器内容：深粉等离子漩涡（{@code style: 17}）而不是宇宙星空。
     * 模型走普通剑的 {@code minecraft:item/handheld}。
     */
    public static final RegistryObject<Item> CRIMSON_VOW = ITEMS.register("crimson_vow",
            CrimsonVow::new);

    /**
     * SilentDaylight（静默白昼）— 同一个 {@code hall:cosmic} loader，
     * 但走的是<b>水面湍流</b>那条片元路径（{@code style: 18}）。
     * 遮罩 {@code silent_daylight_mask} 把水灌进剑刃，剑柄留在水外。
     */
    public static final RegistryObject<Item> SILENT_DAYLIGHT = ITEMS.register("silent_daylight",
            SilentDaylight::new);

    /** Infested ender pearl — teleportation item with glitch twitch visual effect. */
    public static final RegistryObject<Item> INF_ENDER_PEAR = ITEMS.register("hall_ender_pear",
            () -> new InfEnderPearItem(new Item.Properties().stacksTo(16)));

    //impl
    public static final RegistryObject<BaseGlowingGeoItem> ACID_ANOMALY_EXTRACT =
            registerGeoItem("acid_anomaly_extract",
                    () -> new BaseGlowingGeoItem(
                            new Item.Properties().stacksTo(64), "acid_anomaly_extract", true));

    public static final RegistryObject<BaseGlowingGeoItem> COLD_ANOMALY_EXTRACT =
            registerGeoItem("cold_anomaly_extract",
                    () -> new BaseGlowingGeoItem(
                            new Item.Properties().stacksTo(64), "cold_anomaly_extract", true));

    public static final RegistryObject<BaseGlowingGeoItem> HEAT_ANOMALY_EXTRACT =
            registerGeoItem("heat_anomaly_extract",
                    () -> new BaseGlowingGeoItem(
                            new Item.Properties().stacksTo(64), "heat_anomaly_extract", true));

    public static final RegistryObject<Item> HALL_TENDON = registerSimpleItem("hall_tendon");
    public static final RegistryObject<Item> HALL_BONE_FRAGMENTS = registerSimpleItem("hall_bone_fragments");
    public static final RegistryObject<Item> DOMITE_ORE = registerSimpleItem("domite_ore");
    public static final RegistryObject<Item> DOMITE_CRYSTAL = registerSimpleItem("domite_crystal");
    public static final RegistryObject<Item> DOMERITE_ORE = registerSimpleItem("domerite_ore");
    public static final RegistryObject<Item> DOMERITE_INGOT = registerSimpleItem("domerite_ingot");
    public static final RegistryObject<Item> DOMERITE_STICK = registerSimpleItem("domerite_stick");
    public static final RegistryObject<Item> BONECRUSHER_CORE = registerSimpleItem("bonecrusher_core");

    // domerite tools (alloy/netherite tier, stats scale with Y-level)
    public static final RegistryObject<Item> DOMERITE_SWORD = ITEMS.register("domerite_sword",
            () -> new DomeriteSword(new Item.Properties()));
    public static final RegistryObject<Item> DOMERITE_PICKAXE = ITEMS.register("domerite_pickaxe",
            () -> new DomeritePickaxe(new Item.Properties()));
    public static final RegistryObject<Item> DOMERITE_AXE = ITEMS.register("domerite_axe",
            () -> new DomeriteAxe(new Item.Properties()));
    public static final RegistryObject<Item> DOMERITE_SHOVEL = ITEMS.register("domerite_shovel",
            () -> new DomeriteShovel(new Item.Properties()));
    public static final RegistryObject<Item> DOMERITE_HOE = ITEMS.register("domerite_hoe",
            () -> new DomeriteHoe(new Item.Properties()));
    public static final RegistryObject<Item> DOMERITE_LONGSWORD = ITEMS.register("domerite_longsword",
            () -> new DomeriteLongsword(new Item.Properties()));

    // ══════════════════════════════════════════════════════════════
    // 魔法体系 · 物品
    // ══════════════════════════════════════════════════════════════
    // 本轮<b>只做注册与表现</b>（模型 + 译名 + 泛光），数值与法术效果一律留白。
    // 唯一的"行为"是光明法力板的 mask 泛光层，见下面 ManaPlateItem 那一段。
    //
    // 模型与译名都走 datagen（见 datagen/gen/ItemGenData 与 gen/lang/），
    // 不要手写 json —— 模型是按注册表自动遍历生成的，手写的文件会被覆盖。

    // ---- 法力板：各流派的基础板材 ----
    // 目前都是纯材料，两个例外：
    //   * 光明法力板 —— mask 泛光层（自发光）**和**剪影描边（纯色硬边 + 光雾）；
    //   * 火焰法力板 —— 只有剪影描边，颜色模式 "fire_2"（参考 shadertoy 的燃烧描边）。
    // 想让别的板也发光：放一张同画布的 <流派>_mana_plate_mask_glow.png，
    // 再把对应那行从 registerSimpleItem 换成
    // ITEMS.register(name, () -> new ManaPlateItem(props, <MaskLayerSpec>))。
    // 想让别的板也描边：在上面的构造参数后追加一个 ManaPlateItem.GlowOutline，
    // 见下面光明法力板那一行。
    //
    // 水板材对应 MagicType.WATER；各流派板材用于法杖合成与充能。
    public static final RegistryObject<Item> SOURCE_MANA_PLATE = registerSimpleItem("source_mana_plate");
    public static final RegistryObject<Item> DEATH_MANA_PLATE = registerSimpleItem("death_mana_plate");

    /**
     * 火焰法力板 —— 带「燃烧描边」（{@code ICustomOutline} + {@code "fire_2"} 颜色模式）。
     *
     * <p>不发光（没有 mask 泛光层），只描边：剪影外侧一条按参考 shadertoy「fire」烧起来的带子
     * （3D simplex 火焰 + 烟 + 火星），再叠全局光雾。和光明板一样走物品自描述，
     * 模型仍是 datagen 生成的 {@code minecraft:item/generated}。</p>
     *
     * <p>GUI 里不描边（{@link ManaPlateItem.GlowOutline#drawInGui()} = false）：
     * 它是可堆叠材料，GUI 的描边是逐物品就地合成的，代价会随件数线性涨。</p>
     */
    public static final RegistryObject<Item> FIRE_MANA_PLATE = ITEMS.register("fire_mana_plate",
            () -> new ManaPlateItem(new Item.Properties().stacksTo(64),
                    null, ManaPlateItem.FIRE_OUTLINE));

    /**
     * 光明法力板 —— 唯一同时带「泛光」与「泛光描边」的板材。
     *
     * <p>泛光走 {@code MaskLayerProvider}，描边走 {@code ICustomOutline} —— 两条路都是
     * 物品自描述，所以模型仍是 datagen 生成的 {@code minecraft:item/generated}，
     * 没有手写 JSON。描边 <b>只出现在轮廓外侧</b>，和贴在表面的泛光层不抢像素。</p>
     */
    public static final RegistryObject<Item> LIGHT_MANA_PLATE = ITEMS.register("light_mana_plate",
            () -> new ManaPlateItem(new Item.Properties().stacksTo(64),
                    ManaPlateItem.LIGHT_GLOW, ManaPlateItem.LIGHT_OUTLINE));

    public static final RegistryObject<Item> NATURE_MANA_PLATE = registerSimpleItem("nature_mana_plate");
    public static final RegistryObject<Item> VOID_MANA_PLATE = registerSimpleItem("void_mana_plate");
    public static final RegistryObject<Item> WATER_MANA_PLATE = registerSimpleItem("water_mana_plate");
    public static final RegistryObject<Item> MYSTERIOUS_MANA_PLATE = registerSimpleItem("mysterious_mana_plate");

    // ---- 催化剂 / 符文：目前是纯材料 ----
    public static final RegistryObject<Item> MAGIC_CATA = registerSimpleItem("magic_cata");
    public static final RegistryObject<Item> VOID_RUNE = registerSimpleItem("void_rune");

    // 元素储魔瓶用于板材灌注配方，合成后返还普通空储魔瓶。
    public static final RegistryObject<Item> MANA_BOTTLE = registerSimpleItem("mana_bottle");
    public static final RegistryObject<Item> LIGHT_MANA_BOTTLE = registerFilledManaBottle("light_mana_bottle");
    public static final RegistryObject<Item> FIRE_MANA_BOTTLE = registerFilledManaBottle("fire_mana_bottle");
    public static final RegistryObject<Item> WATER_MANA_BOTTLE = registerFilledManaBottle("water_mana_bottle");
    public static final RegistryObject<Item> NATURE_MANA_BOTTLE = registerFilledManaBottle("nature_mana_bottle");
    public static final RegistryObject<Item> VOID_MANA_BOTTLE = registerFilledManaBottle("void_mana_bottle");
    public static final RegistryObject<Item> DEATH_MANA_BOTTLE = registerFilledManaBottle("death_mana_bottle");
    public static final RegistryObject<Item> MYSTERIOUS_MANA_BOTTLE = registerFilledManaBottle("mysterious_mana_bottle");
    public static final RegistryObject<Item> SOURCE_CHARGE_SAND = registerSimpleItem("source_charge_sand");

    // 芯片：蓝色为空白基底，其它三种由空白芯片合成，暂不附加功能。
    public static final RegistryObject<Item> CHIP_NULL = registerSimpleItem("chip_null");
    public static final RegistryObject<Item> CHIP_ENHANCED = registerSimpleItem("chip_enhanced");
    public static final RegistryObject<Item> CHIP_SCAN = registerSimpleItem("chip_scan");
    public static final RegistryObject<Item> CHIP_EXTRACT = registerSimpleItem("chip_extract");


    //死亡
    public static final RegistryObject<Item> DEATH_STAR = registerSimpleItem("death_star");

    // ---- 法杖 ----
    // 空杖保留附魔作为合成基底；七系杖各储存 500 魔能，通用杖储存 2500。
    // 模型用 minecraft:item/handheld，由 ItemGenData 的 isHandheld 判定。
    public static final RegistryObject<Item> WAND = ITEMS.register("wand",
            () -> new WandItem(new Item.Properties().durability(512), MagicType.MYSTERIOUS, 0));
    public static final RegistryObject<Item> WAND_NATURE = ITEMS.register("wand_nature",
            () -> new WandItem(new Item.Properties().durability(512), MagicType.NATURE, WandItem.NORMAL_CAPACITY));
    public static final RegistryObject<Item> WAND_FIRE = ITEMS.register("wand_fire",
            () -> new WandItem(new Item.Properties().durability(512), MagicType.FIRE, WandItem.NORMAL_CAPACITY));
    public static final RegistryObject<Item> WAND_DEATH = ITEMS.register("wand_death",
            () -> new WandItem(new Item.Properties().durability(512), MagicType.DEATH, WandItem.NORMAL_CAPACITY));
    public static final RegistryObject<Item> WAND_LIGHT = ITEMS.register("wand_light",
            () -> new WandItem(new Item.Properties().durability(512), MagicType.LIGHT, WandItem.NORMAL_CAPACITY));
    public static final RegistryObject<Item> WAND_VOID = ITEMS.register("wand_void",
            () -> new WandItem(new Item.Properties().durability(512), MagicType.VOID, WandItem.NORMAL_CAPACITY));
    public static final RegistryObject<Item> WAND_WATER = ITEMS.register("wand_water",
            () -> new WandItem(new Item.Properties().durability(512), MagicType.WATER, WandItem.NORMAL_CAPACITY));
    public static final RegistryObject<Item> WAND_MYSTERIOUS = ITEMS.register("wand_mysterious",
            () -> new WandItem(new Item.Properties().durability(512), MagicType.MYSTERIOUS, WandItem.NORMAL_CAPACITY));
    public static final RegistryObject<Item> WAND_COMMON = ITEMS.register("wand_common",
            () -> new WandItem(new Item.Properties().durability(512), MagicType.MYSTERIOUS, WandItem.COMMON_CAPACITY));

    // ---- 法术 ----
    // 法术物品只声明基值，效果写在 cast() 里，投射物在 entities/magic/ 下。
    // 放进魔法槽（PlayerMagicPool，按 M 打开魔法星图装配）里、拿法杖右键才会释放
    // —— 直接拿在手上右键是没反应的。
    public static final RegistryObject<Item> FIRE_BALL =
            ITEMS.register("fire_ball", FireBall::new);

    public static final RegistryObject<Item> SPEAR_OF_JUDGMENT =
            ITEMS.register("spear_of_judgment", SpearOfJudgment::new);

    public static final RegistryObject<Item> SUPPRESSION_FIELD =
            ITEMS.register("suppression_field", SuppressionField::new);

    public static final RegistryObject<Item> TIDAL_PULSE =
            ITEMS.register("tidal_pulse", TidalPulse::new);

    public static final RegistryObject<Item> BRIAR_SNARE =
            ITEMS.register("briar_snare", BriarSnare::new);

    public static final RegistryObject<Item> PURIFYING_LIGHT =
            ITEMS.register("purifying_light", PurifyingLight::new);

    public static final RegistryObject<Item> ARCANE_STEP =
            ITEMS.register("arcane_step", ArcaneStep::new);

    public static final RegistryObject<Item> SPRINT =
            ITEMS.register("sprint", Sprint::new);

    public static final RegistryObject<Item> INF_PLAYER_SPAWN_EGG = registerSpawnEgg("hall_player",EntityTypeRegistry.INF_PLAYER);
    public static final RegistryObject<Item> INF_ENDERMAN_SPAWN_EGG = registerSpawnEgg("hall_enderman",EntityTypeRegistry.INF_ENDERMAN);
    public static final RegistryObject<Item> INF_SKELETON_SPAWN_EGG = registerSpawnEgg("hall_skeleton",EntityTypeRegistry.INF_SKELETON);
    public static final RegistryObject<Item> SCOUT_SPAWN_EGG = registerSpawnEgg("scout",EntityTypeRegistry.SCOUT);
    public static final RegistryObject<Item> PURSUER_SPAWN_EGG = registerSpawnEgg("pursuer",EntityTypeRegistry.PURSUER);
    public static final RegistryObject<Item> MONOLITH_SPAWN_EGG = registerSpawnEgg("monolith",EntityTypeRegistry.MONOLITH);
    public static final RegistryObject<Item> BONECRUSHER_SPAWN_EGG = registerSpawnEgg("bonecrusher",EntityTypeRegistry.BONECRUSHER);
    public static final RegistryObject<Item> HEAVY_BOMB_SPAWN_EGG = registerSpawnEgg("heavy_bomb",EntityTypeRegistry.HEAVY_BOMB);
    public static final RegistryObject<Item> COLLAPSAR_SPAWN_EGG = registerSpawnEgg("collapsar",EntityTypeRegistry.COLLAPSAR);
    public static final RegistryObject<Item> WINDING_COLUMN_SPAWN_EGG = registerSpawnEgg("winding_column",EntityTypeRegistry.WINDING_COLUMN);

}
