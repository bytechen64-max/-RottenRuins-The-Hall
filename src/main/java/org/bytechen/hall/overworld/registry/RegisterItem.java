package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.IAutoRenderableItem;
import org.bytechen.hall.overworld.registry.items.BaseGlowingGeoItem;
import org.bytechen.hall.overworld.registry.items.GeoItemRenderManager;
import org.bytechen.hall.overworld.registry.items.VoidSword;
import org.bytechen.hall.overworld.registry.items.InfEnderPearItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.hall.overworld.registry.items.*;
import org.bytechen.hall.overworld.registry.items.magic.ManaPlateItem;
import org.bytechen.hall.overworld.registry.items.magic.WandItem;
import org.bytechen.hall.overworld.registry.items.magic.bases.MagicType;

import java.util.function.Supplier;

public class RegisterItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HallMod.MODID);

    public static RegistryObject<Item> registerSimpleItem(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().stacksTo(64)));
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
    public static final RegistryObject<Item> INF_ENDER_PEAR = ITEMS.register("inf_ender_pear",
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
    // 目前都是纯材料，唯一的例外是光明法力板 —— 它挂了 mask 泛光层（自发光）。
    // 想让别的板也发光：放一张同画布的 <流派>_mana_plate_mask_glow.png，
    // 再把对应那行从 registerSimpleItem 换成
    // ITEMS.register(name, () -> new ManaPlateItem(props, <MaskLayerSpec>))。
    //
    // 注意：贴图里是 water（水），而 bases/MagicType 里对应位置是 ACE。
    // 这两者目前<b>没有建立任何映射</b>（板子还没有接进法术系统），
    // 等做流派数值时再决定 ACE 与水的关系。
    public static final RegistryObject<Item> DEATH_MANA_PLATE = registerSimpleItem("death_mana_plate");
    public static final RegistryObject<Item> FIRE_MANA_PLATE = registerSimpleItem("fire_mana_plate");

    /**
     * 光明法力板 —— 唯一带泛光的板材。
     * 泛光走 {@code MaskLayerProvider}（物品自描述），所以模型仍是 datagen 生成的
     * {@code minecraft:item/generated}，没有手写 JSON。
     */
    public static final RegistryObject<Item> LIGHT_MANA_PLATE = ITEMS.register("light_mana_plate",
            () -> new ManaPlateItem(new Item.Properties().stacksTo(64), ManaPlateItem.LIGHT_GLOW));

    public static final RegistryObject<Item> NATURE_MANA_PLATE = registerSimpleItem("nature_mana_plate");
    public static final RegistryObject<Item> VOID_MANA_PLATE = registerSimpleItem("void_mana_plate");
    public static final RegistryObject<Item> WATER_MANA_PLATE = registerSimpleItem("water_mana_plate");

    // ---- 催化剂 / 符文：目前是纯材料 ----
    public static final RegistryObject<Item> MAGIC_CATA = registerSimpleItem("magic_cata");
    public static final RegistryObject<Item> VOID_RUNE = registerSimpleItem("void_rune");

    // ---- 法杖 ----
    // 走 StaffBase（纯转发层），所以它们已经"能被魔法系统识别"：
    // 右键会走进 MagicHandle。但魔法槽里目前一件法术都没有，
    // 于是每次右键都只会拿到 CastResult.NO_SPELL —— 表现为"什么都没发生"。
    // 模型用 minecraft:item/handheld，由 ItemGenData 的 isHandheld 判定。
    public static final RegistryObject<Item> WAND = ITEMS.register("wand",
            () -> new WandItem(new Item.Properties().stacksTo(1), MagicType.MAGIC));
    public static final RegistryObject<Item> WAND_NATURE = ITEMS.register("wand_nature",
            () -> new WandItem(new Item.Properties().stacksTo(1), MagicType.NATURE));

    public static final RegistryObject<Item> INF_PLAYER_SPAWN_EGG = registerSpawnEgg("inf_player",EntityTypeRegistry.INF_PLAYER);
    public static final RegistryObject<Item> INF_ENDERMAN_SPAWN_EGG = registerSpawnEgg("inf_enderman",EntityTypeRegistry.INF_ENDERMAN);
    public static final RegistryObject<Item> INF_SKELETON_SPAWN_EGG = registerSpawnEgg("inf_skeleton",EntityTypeRegistry.INF_SKELETON);
    public static final RegistryObject<Item> SCOUT_SPAWN_EGG = registerSpawnEgg("scout",EntityTypeRegistry.SCOUT);
    public static final RegistryObject<Item> PURSUER_SPAWN_EGG = registerSpawnEgg("pursuer",EntityTypeRegistry.PURSUER);
    public static final RegistryObject<Item> MONOLITH_SPAWN_EGG = registerSpawnEgg("monolith",EntityTypeRegistry.MONOLITH);
    public static final RegistryObject<Item> BONECRUSHER_SPAWN_EGG = registerSpawnEgg("bonecrusher",EntityTypeRegistry.BONECRUSHER);
    public static final RegistryObject<Item> HEAVY_BOMB_SPAWN_EGG = registerSpawnEgg("heavy_bomb",EntityTypeRegistry.HEAVY_BOMB);
    public static final RegistryObject<Item> COLLAPSAR_SPAWN_EGG = registerSpawnEgg("collapsar",EntityTypeRegistry.COLLAPSAR);

}
