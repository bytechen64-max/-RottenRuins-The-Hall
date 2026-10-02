package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.entity.IAutoRenderableEntity;
import org.bytechen.hall.client.entity.IAutoRenderableProjectile;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.base.EntityManager;
import org.bytechen.hall.overworld.registry.entities.base.HallEntityManager;
import org.bytechen.hall.overworld.registry.entities.base.HallProjectileManager;
import org.bytechen.hall.overworld.registry.entities.population.apostle.CollapsarEntity;
import org.bytechen.hall.overworld.registry.entities.population.ecological.BonecrusherEntity;
import org.bytechen.hall.overworld.registry.entities.population.ecological.HeavyBombEntity;
import org.bytechen.hall.overworld.registry.entities.population.ecological.HeavyBombTntEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.InfEndermanEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.InfPlayerEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.InfSkeletonArrowEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.InfSkeletonEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.BlackHoleEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.CollapseEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.MeteoriteEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictBeamEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictFieldEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictSwordDropEntity;
import org.bytechen.hall.overworld.registry.entities.population.ulcerated.MonolithEntity;
import org.bytechen.hall.overworld.registry.entities.population.ulcerated.PursuerEntity;
import org.bytechen.hall.overworld.registry.entities.population.ulcerated.ScoutEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("removal")
public class EntityTypeRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HallMod.MODID);

    public static <T extends AbstractHallEntity> RegistryObject<EntityType<T>> registerMob(
            String name, float width, float height,
            EntityType.EntityFactory<T> factory,
            Supplier<AttributeSupplier.Builder> attributes) {
        return ENTITY_TYPES.register(name, () -> {
            EntityType<T> type = EntityType.Builder.of(factory, MobCategory.MONSTER)
                    .sized(width, height)
                    .build(name);
            return HallEntityManager.registerAll(type, attributes);
        });
    }

    public static <T extends Entity & IAutoRenderableProjectile> RegistryObject<EntityType<T>> registerProjectile(
            String name, float width, float height,
            EntityType.EntityFactory<T> factory) {
        return ENTITY_TYPES.register(name, () -> {
            EntityType<T> type = EntityType.Builder.of(factory, MobCategory.MISC)
                    .sized(width, height)
                    .build(name);
            return HallProjectileManager.registerProjectile(type);
        });
    }

    public static <T extends Entity & IAutoRenderableEntity> RegistryObject<EntityType<T>> registerSkill(
            String name, float width, float height,
            EntityType.EntityFactory<T> factory) {
        return ENTITY_TYPES.register(name, () -> {
            EntityType<T> type = EntityType.Builder.of(factory, MobCategory.MISC)
                    .sized(width, height)
                    .build(name);
            return EntityManager.registerSkill(type);
        });
    }

    public static <T extends Entity> RegistryObject<EntityType<T>> registerNoopEntity(
            String name, float width, float height,
            EntityType.EntityFactory<T> factory) {
        return ENTITY_TYPES.register(name, () -> {
            return EntityType.Builder.of(factory, MobCategory.MISC)
                    .sized(width, height)
                    .build(name);
        });
    }

    public static <T extends AbstractHallEntity> Consumer<T> defaultResources(String name) {
        return entity -> {
            entity.model = new ResourceLocation(HallMod.MODID, "geo/" + name + ".geo.json");
            entity.texture = new ResourceLocation(HallMod.MODID, "textures/entity/" + name + ".png");
            entity.animation = new ResourceLocation(HallMod.MODID, "animations/" + name + ".animation.json");
        };
    }

    // Your entities here
    // public static final RegistryObject<EntityType<ExampleEntity>> EXAMPLE =
    //         registerMob("example", 0.6f, 0.8f,
    //                 (type, level) -> new ExampleEntity(type, level, defaultResources("example")),
    //                 ExampleEntity::createAttributes);



    public static final RegistryObject<EntityType<InfPlayerEntity>> INF_PLAYER =
            registerMob("inf_player", 1.2f, 4f,
                    (type, level) -> new InfPlayerEntity(type, level, defaultResources("inf_player")),
                    InfPlayerEntity::createAttributes);

    public static final RegistryObject<EntityType<InfEndermanEntity>> INF_ENDERMAN =
            registerMob("inf_enderman", 1.2f, 4f,
                    (type, level) -> new InfEndermanEntity(type, level, defaultResources("inf_enderman")),
                    InfEndermanEntity::createAttributes);

    public static final RegistryObject<EntityType<InfSkeletonEntity>> INF_SKELETON =
            registerMob("inf_skeleton", 1.2f, 1.2f,
                    (type, level) -> new InfSkeletonEntity(type, level, defaultResources("inf_skeleton")),
                    InfSkeletonEntity::createAttributes);

    public static final RegistryObject<EntityType<InfSkeletonArrowEntity>> INF_SKELETON_ARROW =
            registerSkill("inf_skeleton_arrow", 0.6f, 0.6f,
                    InfSkeletonArrowEntity::new);

    // ────────── 溃烂 —— 斥候 / 追蹤者 / 巨碑（无专属感染形态时的兜底形态） ──────────
    //  斥候：碰撞箱 1.0 × 1.2（体积 1.2，落在 < 2 档），生命 12 / 伤害 5 / 移动缓慢 / 会爬墙
    public static final RegistryObject<EntityType<ScoutEntity>> SCOUT =
            registerMob("scout", 1.0f, 1.2f,
                    (type, level) -> new ScoutEntity(type, level, defaultResources("scout")),
                    ScoutEntity::createAttributes);

    //  巨碑：碰撞箱 2.4 × 2.8，生命 100 / 伤害 20 / 慢速抗击退（> 8 档的转化形态）
    public static final RegistryObject<EntityType<MonolithEntity>> MONOLITH =
            registerMob("monolith", 2.4f, 2.8f,
                    (type, level) -> new MonolithEntity(type, level, defaultResources("monolith")),
                    MonolithEntity::createAttributes);

    //  追蹤者：碰撞箱 0.8 × 1.6（体积 1.28，是 2 ~ 8 档的转化形态），生命 24 / 伤害 8 / 速度快
    //  注：geo 网格实测约 1.25 宽 × 4.0 高，比这里给的碰撞箱大不少，若模型看起来穿箱再调这两个数
    public static final RegistryObject<EntityType<PursuerEntity>> PURSUER =
            registerMob("pursuer", 0.8f, 4,
                    (type, level) -> new PursuerEntity(type, level, defaultResources("pursuer")),
                    PursuerEntity::createAttributes);


    public static final RegistryObject<EntityType<BonecrusherEntity>> BONECRUSHER=
            registerMob("bonecrusher", 5f, 5f,
                    (type, level) -> new BonecrusherEntity(type, level, defaultResources("bonecrusher")),
                    BonecrusherEntity::createAttributes);

    public static final RegistryObject<EntityType<HeavyBombEntity>> HEAVY_BOMB =
            registerMob("heavy_bomb", 3f, 3f,
                    (type, level) -> new HeavyBombEntity(type, level, HeavyBombEntity.resources()),
                    HeavyBombEntity::createAttributes);

    public static final RegistryObject<EntityType<HeavyBombTntEntity>> HEAVY_BOMB_TNT =
            registerSkill("heavy_bomb_tnt", 0.8f, 0.8f,
                    HeavyBombTntEntity::new);


    // ────────── 技能实体 —— 陨石 ──────────
    public static final RegistryObject<EntityType<MeteoriteEntity>> METEORITE =
            registerNoopEntity("meteorite", 0.5f, 1.6f,
                    (type, level) -> new MeteoriteEntity(type, level));

    // ────────── 技能实体 —— 冲击波 ──────────
    public static final RegistryObject<EntityType<ShockwaveEntity>> SHOCKWAVE =
            registerNoopEntity("shockwave", 0.5f, 0.5f,
                    (type, level) -> new ShockwaveEntity(type, level));

    // ────────── 技能实体 —— 剑气（双锥体白色发光） ──────────
    public static final RegistryObject<EntityType<SwordAuraEntity>> SWORD_AURA =
            registerNoopEntity("sword_aura", 0.5f, 0.5f,
                    (type, level) -> new SwordAuraEntity(type, level));

    // ────────── 技能实体 —— 天穹裁决的垂直光柱 ──────────
    //  纯渲染实体：伤害在 DomeriteLongsword#castBeam 里已经结算，这里只负责被看见。
    //  碰撞箱给得很小（0.5）—— 光柱的"范围"是渲染与判定各自按 radius/length 算的，
    //  给它一个真实的 44×5 碰撞箱会让区块剔除与拾取逻辑都变得很怪。
    public static final RegistryObject<EntityType<VerdictBeamEntity>> VERDICT_BEAM =
            registerNoopEntity("verdict_beam", 0.5f, 0.5f,
                    (type, level) -> new VerdictBeamEntity(type, level));

    // ────────── 技能实体 —— 天穹裁决的裁决领域 ──────────
    //  与光柱不同，这个实体是**有逻辑的**：每 1.2 秒找领域内最近目标出剑并结算伤害。
    //  它同时承载视觉（地面光纹 + 悬浮二十面体棱片），因为两者本来就是同一个东西。
    public static final RegistryObject<EntityType<VerdictFieldEntity>> VERDICT_FIELD =
            registerNoopEntity("verdict_field", 0.5f, 0.5f,
                    (type, level) -> new VerdictFieldEntity(type, level));

    // ────────── 技能实体 —— 天穹裁决的落剑（剑阵） ──────────
    //  视觉上是"从天而降、落地后插在地上"的剑气；实体本身不移动，
    //  位移由渲染侧按同步数据重建（见 VerdictSwordDropEntity 的类注释）。
    //  碰撞箱给得极小：它的"体积"是渲染出来的锥体，不是碰撞箱。
    public static final RegistryObject<EntityType<VerdictSwordDropEntity>> VERDICT_SWORD_DROP =
            registerNoopEntity("verdict_sword_drop", 0.5f, 0.5f,
                    (type, level) -> new VerdictSwordDropEntity(type, level));

    // ────────── 技能实体 —— 坍缩（多类型坍缩渲染） ──────────
    public static final RegistryObject<EntityType<CollapseEntity>> COLLAPSE =
            registerNoopEntity("collapse", 0.5f, 0.5f,
                    (type, level) -> new CollapseEntity(type, level));

    // ────────── 视觉实体 —— 黑洞（光线追踪引力透镜，纯视觉） ──────────
    public static final RegistryObject<EntityType<BlackHoleEntity>> BLACK_HOLE =
            registerNoopEntity("black_hole", 0.5f, 0.5f,
                    (type, level) -> new BlackHoleEntity(type, level));

    // ────────── 使徒 —— 坍缩使徒（飞行 / 坍缩黑洞 / 斩击风暴） ──────────
    //  碰撞箱按 geo 网格实测值给（宽 2.38 / 高 2.51），不要用 Blockbench 的
    //  visible_bounds_height（3.5 是显示框，不是网格高度），否则头顶会多出一格空箱。
    public static final RegistryObject<EntityType<CollapsarEntity>> COLLAPSAR =
            registerMob("collapsar", 2.4f, 2.6f,
                    (type, level) -> new CollapsarEntity(type, level, CollapsarEntity.resources()),
                    CollapsarEntity::createAttributes);
}
