package org.bytechen.rottenruins.overworld.registry;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.entity.IAutoRenderableEntity;
import org.bytechen.rottenruins.client.entity.IAutoRenderableProjectile;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.base.EntityManager;
import org.bytechen.rottenruins.overworld.registry.entities.base.HallEntityManager;
import org.bytechen.rottenruins.overworld.registry.entities.base.HallProjectileManager;
import org.bytechen.rottenruins.overworld.registry.entities.population.death.DeathHunter;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.apostle.CollapsarEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.apostle.HearthstoneEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.defensive.WindingColumnEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ecological.BonecrusherEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ecological.HeavyBombEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ecological.HeavyBombTntEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected.InfEndermanEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected.InfPlayerEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected.InfSkeletonArrowEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected.InfSkeletonEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.BlackHoleEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.CollapseEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.CrimsonVowChargeEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.CrimsonVowFieldEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.MeteorEventEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.MeteoriteEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.ShockwaveEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.VerdictBeamEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.VerdictFieldEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.VerdictSwordDropEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ulcerated.MonolithEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ulcerated.PursuerEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ulcerated.ScoutEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.magic.FireBallEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.magic.OlympiadCubeEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.magic.SpearOfJudgmentEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bytechen.rottenruins.utils.ModUtils;

@SuppressWarnings("removal")
public class EntityTypeRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HallMod.MODID);

    public static <T extends AbstractRottenEntity> RegistryObject<EntityType<T>> registerMob(
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

    /** 同上，但指定同步间隔（tick）：原版默认 3 tick 一跳，每 tick 都在变的进度条会插出台阶；设成 1 静止时不发包。 */
    public static <T extends Entity> RegistryObject<EntityType<T>> registerNoopEntity(
            String name, float width, float height, int updateInterval,
            EntityType.EntityFactory<T> factory) {
        return registerNoopEntity(name, width, height, updateInterval, 5, factory);
    }

    /** 同上，并指定客户端追踪范围（区块，默认 5 = 80 格）：超出范围实体不下发给客户端，巨型法阵会在远处整个消失。 */
    public static <T extends Entity> RegistryObject<EntityType<T>> registerNoopEntity(
            String name, float width, float height, int updateInterval, int clientTrackingRange,
            EntityType.EntityFactory<T> factory) {
        return ENTITY_TYPES.register(name, () -> {
            return EntityType.Builder.of(factory, MobCategory.MISC)
                    .sized(width, height)
                    .setUpdateInterval(Math.max(1, updateInterval))
                    .clientTrackingRange(Math.max(1, clientTrackingRange))
                    .build(name);
        });
    }

    public static <T extends AbstractRottenEntity> Consumer<T> defaultResources(String name) {
        return entity -> {
            entity.model = ModUtils.modLoc("geo/" + name + ".geo.json");
            entity.texture = ModUtils.modLoc("textures/entity/" + name + ".png");
            entity.animation = ModUtils.modLoc("animations/" + name + ".animation.json");
        };
    }

    public static final RegistryObject<EntityType<InfPlayerEntity>> INF_PLAYER =
            registerMob("hall_player", 1.2f, 4f,
                    (type, level) -> new InfPlayerEntity(type, level, defaultResources("hall_player")),
                    InfPlayerEntity::createAttributes);

    public static final RegistryObject<EntityType<InfEndermanEntity>> INF_ENDERMAN =
            registerMob("hall_enderman", 1.2f, 4f,
                    (type, level) -> new InfEndermanEntity(type, level, defaultResources("hall_enderman")),
                    InfEndermanEntity::createAttributes);

    public static final RegistryObject<EntityType<InfSkeletonEntity>> INF_SKELETON =
            registerMob("hall_skeleton", 1.2f, 1.2f,
                    (type, level) -> new InfSkeletonEntity(type, level, defaultResources("hall_skeleton")),
                    InfSkeletonEntity::createAttributes);

    public static final RegistryObject<EntityType<InfSkeletonArrowEntity>> INF_SKELETON_ARROW =
            registerSkill("hall_skeleton_arrow", 0.6f, 0.6f,
                    InfSkeletonArrowEntity::new);

    // 溃烂系兜底形态：斥候 1.0×1.2（生命 12 / 伤害 5，会爬墙）
    public static final RegistryObject<EntityType<ScoutEntity>> SCOUT =
            registerMob("scout", 1.0f, 1.2f,
                    (type, level) -> new ScoutEntity(type, level, defaultResources("scout")),
                    ScoutEntity::createAttributes);

    // 巨碑 2.4×2.8（生命 100 / 伤害 20，慢速抗击退）
    public static final RegistryObject<EntityType<MonolithEntity>> MONOLITH =
            registerMob("monolith", 2.4f, 2.8f,
                    (type, level) -> new MonolithEntity(type, level, defaultResources("monolith")),
                    MonolithEntity::createAttributes);

    // 追蹤者 0.8×4.0（生命 24 / 伤害 8）；geo 网格实测约 1.25 宽 × 4.0 高，模型穿箱再调这两个数
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

    // 缠绕之柱：不会移动，2 格内抓人、每秒抽 5 点血，挂人时按 40/30/30 分摊伤害；碰撞箱按 geo 实测 1.4×2.8
    public static final RegistryObject<EntityType<WindingColumnEntity>> WINDING_COLUMN =
            registerMob("winding_column", 1.4f, 2.8f,
                    (type, level) -> new WindingColumnEntity(type, level, defaultResources("winding_column")),
                    WindingColumnEntity::createAttributes);

    public static final RegistryObject<EntityType<HeavyBombTntEntity>> HEAVY_BOMB_TNT =
            registerSkill("heavy_bomb_tnt", 0.8f, 0.8f,
                    HeavyBombTntEntity::new);

    // 陨石事件：追踪给 32 区块（512 格）——落点可能在 100 格外、十字星再高 150 格，默认 5 区块连"事件发生了"都看不到
    public static final RegistryObject<EntityType<MeteorEventEntity>> METEOR_EVENT =
            ENTITY_TYPES.register("meteor_event", () -> EntityType.Builder
                    .of(MeteorEventEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build("meteor_event"));
    public static final RegistryObject<EntityType<MeteoriteEntity>> METEORITE =
            registerNoopEntity("meteorite", 0.5f, 1.6f,
                    (type, level) -> new MeteoriteEntity(type, level));

    public static final RegistryObject<EntityType<ShockwaveEntity>> SHOCKWAVE =
            registerNoopEntity("shockwave", 0.5f, 0.5f,
                    (type, level) -> new ShockwaveEntity(type, level));

    public static final RegistryObject<EntityType<SwordAuraEntity>> SWORD_AURA =
            registerNoopEntity("sword_aura", 0.5f, 0.5f,
                    (type, level) -> new SwordAuraEntity(type, level));

    // 裁决光柱：纯渲染，伤害已在 DomeriteLongsword#castBeam 结算；碰撞箱给 0.5，真实 44×5 会让区块剔除与拾取变怪
    public static final RegistryObject<EntityType<VerdictBeamEntity>> VERDICT_BEAM =
            registerNoopEntity("verdict_beam", 0.5f, 0.5f,
                    (type, level) -> new VerdictBeamEntity(type, level));

    // 裁决领域：有逻辑，每 1.2 秒找领域内最近目标出剑结算伤害，同时承载地面光纹与悬浮棱片
    public static final RegistryObject<EntityType<VerdictFieldEntity>> VERDICT_FIELD =
            registerNoopEntity("verdict_field", 0.5f, 0.5f,
                    (type, level) -> new VerdictFieldEntity(type, level));

    // 落剑：实体不移动，位移由渲染侧按同步数据重建；碰撞箱极小，体积是渲染出来的锥体
    public static final RegistryObject<EntityType<VerdictSwordDropEntity>> VERDICT_SWORD_DROP =
            registerNoopEntity("verdict_sword_drop", 0.5f, 0.5f,
                    (type, level) -> new VerdictSwordDropEntity(type, level));

    public static final RegistryObject<EntityType<CollapseEntity>> COLLAPSE =
            registerNoopEntity("collapse", 0.5f, 0.5f,
                    (type, level) -> new CollapseEntity(type, level));

    // 绯红誓约蓄力法阵：同步间隔 1（蓄力进度每 tick 变，3 tick 会看出台阶），追踪 8 区块（直径近 10 格）
    public static final RegistryObject<EntityType<CrimsonVowChargeEntity>> CRIMSON_VOW_CHARGE =
            registerNoopEntity("crimson_vow_charge", 0.5f, 0.5f, 1, 8,
                    (type, level) -> new CrimsonVowChargeEntity(type, level));

    // 绯红誓约巨型法阵：同步间隔 1 让脉冲相位与冲击环同 tick；追踪 20 区块（320 格）——直径 28 格、光柱高 70 格，默认 5 区块远处整个消失
    public static final RegistryObject<EntityType<CrimsonVowFieldEntity>> CRIMSON_VOW_FIELD =
            registerNoopEntity("crimson_vow_field", 0.5f, 0.5f, 1, 20,
                    (type, level) -> new CrimsonVowFieldEntity(type, level));

    // 黑洞：纯视觉，光线追踪引力透镜
    public static final RegistryObject<EntityType<BlackHoleEntity>> BLACK_HOLE =
            registerNoopEntity("black_hole", 0.5f, 0.5f,
                    (type, level) -> new BlackHoleEntity(type, level));

    // 火球：原版 Fireball 子类，渲染走原版 ThrownItemRenderer，故不走 registerProjectile/Skill；碰撞箱照抄恶魂火球 1.0×1.0
    public static final RegistryObject<EntityType<FireBallEntity>> FIRE_BALL =
            registerNoopEntity("fire_ball", 1.0f, 1.0f, FireBallEntity::new);

    // 奥数立方：碰撞箱 0.4 见方便于穿缝；每 tick 同步，否则高速追踪会看出跳格
    public static final RegistryObject<EntityType<OlympiadCubeEntity>> OLYMPIAD_CUBE =
            ENTITY_TYPES.register("olympiad_cube", () -> EntityType.Builder
                    .of(OlympiadCubeEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("olympiad_cube"));

    // 审判之矛：没有客户端预测，一秒飞 32 格，默认 3 tick 同步会看出跳格，故每 tick；追踪 10 区块（原版投射物 4~5）
    public static final RegistryObject<EntityType<SpearOfJudgmentEntity>> SPEAR_OF_JUDGMENT =
            ENTITY_TYPES.register("spear_of_judgment", () -> EntityType.Builder
                    .of(SpearOfJudgmentEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("spear_of_judgment"));

    // 坍缩使徒：碰撞箱按 geo 网格实测 2.4×2.6，勿用 Blockbench 的 visible_bounds_height（3.5 是显示框，会多出一格空箱）
    public static final RegistryObject<EntityType<CollapsarEntity>> COLLAPSAR =
            registerMob("collapsar", 2.4f, 2.6f,
                    (type, level) -> new CollapsarEntity(type, level, CollapsarEntity.resources()),
                    CollapsarEntity::createAttributes);

    // 王庭炉石：碰撞箱 1.6×2.2；geo/textures/animations 的 hearthstone 三件套缺失时只会日志报错，不会崩
    public static final RegistryObject<EntityType<HearthstoneEntity>> HEARTHSTONE =
            registerMob("hearthstone", 1.6f, 2.2f,
                    (type, level) -> new HearthstoneEntity(type, level, HearthstoneEntity.resources()),
                    HearthstoneEntity::createAttributes);

    // 死亡猎手：碰撞箱 = 恼鬼 0.4×0.8 各乘 1.2；资源按 death_hunter 小写命名
    public static final RegistryObject<EntityType<DeathHunter>> DEATH_HUNTER =
            registerMob("death_hunter", DeathHunter.WIDTH, DeathHunter.HEIGHT,
                    (type, level) -> new DeathHunter(type, level, DeathHunter.resources()),
                    DeathHunter::createAttributes);
}
