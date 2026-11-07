package org.bytechen.rottenruins.overworld.registry.corruption;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.base.HallEntityManager;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseCorruptionEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.degenerate.DegenerateCowEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.degenerate.DegeneratePigEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.degenerate.DegenerateVillagerEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.degenerate.DegenerateZombieEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.deteriorated.GrovelerEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionBatEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionBoneRuinsEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionChickenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionCowEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionPigEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionPlayerEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionSheepEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionSkeletonEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionVillagerEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionWolfEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionZombieEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.linked.HumusColumnEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.linked.SummoningPillarEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.original.OriginalBugEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.refactored.AttackerEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.refactored.BearerEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.refactored.LongArmBeastEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.refactored.ShockerEntity;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.AcidBallProjectile;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.BoneSpikeEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionDart;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionPoolEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CuboidEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.PoisonArrowProjectile;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.ShieldEntity;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 腐化系（原 corruption 工程）生物注册表 —— 简化版移植。
 *
 * <p>23 只生物全部复用王庭侧的统一基类体系（{@link AbstractRottenEntity} + family 薄层），
 * 索敌走 infcore {@code InfectedTargetGoal}；原来的 Brain/SmartMove/技能等机制层未移植。</p>
 *
 * <p>id 约定：同化种统一 {@code corruption_*} 前缀（原 {@code infection_*}）；其余族沿用原名。</p>
 */
@SuppressWarnings("removal")
public class CorruptionEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HallMod.MODID);

    private static <T extends AbstractRottenEntity> RegistryObject<EntityType<T>> registerMob(
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

    /** 默认 GeckoLib 资源：geo/<name>.geo.json、textures/entity/<name>.png、animations/<name>.animation.json。 */
    private static <T extends AbstractRottenEntity> Consumer<T> defaultResources(String name) {
        return entity -> {
            entity.model = new ResourceLocation(HallMod.MODID, "geo/" + name + ".geo.json");
            entity.texture = new ResourceLocation(HallMod.MODID, "textures/entity/" + name + ".png");
            entity.animation = new ResourceLocation(HallMod.MODID, "animations/" + name + ".animation.json");
        };
    }

    // ==================== 同化种（host，corruption_*） ====================
    public static final RegistryObject<EntityType<CorruptionZombieEntity>> CORRUPTION_ZOMBIE =
            registerMob("corruption_zombie", 0.6f, 1.95f,
                    (t, l) -> new CorruptionZombieEntity(t, l, defaultResources("corruption_zombie")),
                    CorruptionZombieEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionPlayerEntity>> CORRUPTION_PLAYER =
            registerMob("corruption_player", 0.6f, 1.95f,
                    (t, l) -> new CorruptionPlayerEntity(t, l, defaultResources("corruption_player")),
                    CorruptionPlayerEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionCowEntity>> CORRUPTION_COW =
            registerMob("corruption_cow", 0.8f, 1.2f,
                    (t, l) -> new CorruptionCowEntity(t, l, defaultResources("corruption_cow")),
                    CorruptionCowEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionSheepEntity>> CORRUPTION_SHEEP =
            registerMob("corruption_sheep", 0.8f, 1.2f,
                    (t, l) -> new CorruptionSheepEntity(t, l, defaultResources("corruption_sheep")),
                    CorruptionSheepEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionPigEntity>> CORRUPTION_PIG =
            registerMob("corruption_pig", 0.8f, 1.2f,
                    (t, l) -> new CorruptionPigEntity(t, l, defaultResources("corruption_pig")),
                    CorruptionPigEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionBatEntity>> CORRUPTION_BAT =
            registerMob("corruption_bat", 0.6f, 0.9f,
                    (t, l) -> new CorruptionBatEntity(t, l, defaultResources("corruption_bat")),
                    CorruptionBatEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionSkeletonEntity>> CORRUPTION_SKELETON =
            registerMob("corruption_skeleton", 0.6f, 1.99f,
                    (t, l) -> new CorruptionSkeletonEntity(t, l, defaultResources("corruption_skeleton")),
                    CorruptionSkeletonEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionBoneRuinsEntity>> CORRUPTION_BONE_RUINS =
            registerMob("corruption_bone_ruins", 0.6f, 1.99f,
                    (t, l) -> new CorruptionBoneRuinsEntity(t, l, defaultResources("corruption_bone_ruins")),
                    CorruptionBoneRuinsEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionVillagerEntity>> CORRUPTION_VILLAGER =
            registerMob("corruption_villager", 0.6f, 1.99f,
                    (t, l) -> new CorruptionVillagerEntity(t, l, defaultResources("corruption_villager")),
                    CorruptionVillagerEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionWolfEntity>> CORRUPTION_WOLF =
            registerMob("corruption_wolf", 1.15f, 0.95f,
                    (t, l) -> new CorruptionWolfEntity(t, l, defaultResources("corruption_wolf")),
                    CorruptionWolfEntity::createAttributes);
    public static final RegistryObject<EntityType<CorruptionChickenEntity>> CORRUPTION_CHICKEN =
            registerMob("corruption_chicken", 0.5f, 0.5f,
                    (t, l) -> new CorruptionChickenEntity(t, l, defaultResources("corruption_chicken")),
                    CorruptionChickenEntity::createAttributes);

    // ==================== 腐化种（degenerate） ====================
    public static final RegistryObject<EntityType<DegeneratePigEntity>> DEGENERATE_PIG =
            registerMob("degenerate_pig", 1.35f, 1.35f,
                    (t, l) -> new DegeneratePigEntity(t, l, defaultResources("degenerate_pig")),
                    DegeneratePigEntity::createAttributes);
    public static final RegistryObject<EntityType<DegenerateZombieEntity>> DEGENERATE_ZOMBIE =
            registerMob("degenerate_zombie", 0.6f, 1.95f,
                    (t, l) -> new DegenerateZombieEntity(t, l, defaultResources("degenerate_zombie")),
                    DegenerateZombieEntity::createAttributes);
    public static final RegistryObject<EntityType<DegenerateVillagerEntity>> DEGENERATE_VILLAGER =
            registerMob("degenerate_villager", 0.6f, 2.6f,
                    (t, l) -> new DegenerateVillagerEntity(t, l, defaultResources("degenerate_villager")),
                    DegenerateVillagerEntity::createAttributes);
    public static final RegistryObject<EntityType<DegenerateCowEntity>> DEGENERATE_COW =
            registerMob("degenerate_cow", 1.2f, 1.6f,
                    (t, l) -> new DegenerateCowEntity(t, l, defaultResources("degenerate_cow")),
                    DegenerateCowEntity::createAttributes);

    // ==================== 退化种 / 重构种 / 联结种 / 原虫 ====================
    public static final RegistryObject<EntityType<GrovelerEntity>> GROVELER =
            registerMob("groveler", 1.2f, 1.8f,
                    (t, l) -> new GrovelerEntity(t, l, defaultResources("groveler")),
                    GrovelerEntity::createAttributes);
    public static final RegistryObject<EntityType<AttackerEntity>> ATTACKER =
            registerMob("attacker", 2.0f, 4.5f,
                    (t, l) -> new AttackerEntity(t, l, defaultResources("attacker")),
                    AttackerEntity::createAttributes);
    public static final RegistryObject<EntityType<BearerEntity>> BEARER =
            registerMob("bearer", 1.0f, 1.0f,
                    (t, l) -> new BearerEntity(t, l, defaultResources("bearer")),
                    BearerEntity::createAttributes);
    public static final RegistryObject<EntityType<LongArmBeastEntity>> LONG_ARM_BEAST =
            registerMob("long_arm_beast", 1.65f, 3.9f,
                    (t, l) -> new LongArmBeastEntity(t, l, defaultResources("long_arm_beast")),
                    LongArmBeastEntity::createAttributes);
    public static final RegistryObject<EntityType<ShockerEntity>> SHOCKER =
            registerMob("shocker", 1.5f, 2.8f,
                    (t, l) -> new ShockerEntity(t, l, defaultResources("shocker")),
                    ShockerEntity::createAttributes);
    public static final RegistryObject<EntityType<HumusColumnEntity>> HUMUS_COLUMN =
            registerMob("humus_column", 0.9f, 1.65f,
                    (t, l) -> new HumusColumnEntity(t, l, defaultResources("humus_column")),
                    HumusColumnEntity::createAttributes);
    public static final RegistryObject<EntityType<SummoningPillarEntity>> SUMMONING_PILLAR =
            registerMob("summoning_pillar", 0.9f, 1.65f,
                    (t, l) -> new SummoningPillarEntity(t, l, defaultResources("summoning_pillar")),
                    SummoningPillarEntity::createAttributes);
    public static final RegistryObject<EntityType<OriginalBugEntity>> ORIGINAL_BUG =
            registerMob("original_bug", 0.6f, 0.8f,
                    (t, l) -> new OriginalBugEntity(t, l, defaultResources("original_bug")),
                    OriginalBugEntity::createAttributes);

    // ==================== 技能实体（腐化技能层，阶段 1） ====================

    /** 骨刺：死亡/技能时弹射的骨制飞刺（GeckoLib 模型） */
    public static final RegistryObject<EntityType<BoneSpikeEntity>> BONE_SPIKE =
            EntityTypeRegistry.registerProjectile("bone_spike", 0.3f, 0.3f,
                    (type, level) -> new BoneSpikeEntity(type, level, spike -> {
                        spike.setModel(new ResourceLocation(HallMod.MODID, "geo/bone_spike.geo.json"));
                        spike.setTexture(new ResourceLocation(HallMod.MODID, "textures/entity/bone_spike.png"));
                        spike.setAnimation(new ResourceLocation(HallMod.MODID, "animations/bone_spike.animation.json"));
                    }));

    /** 酸球：腐化村民吐出的酸液弹（GeckoLib 模型） */
    public static final RegistryObject<EntityType<AcidBallProjectile>> ACID_BALL =
            EntityTypeRegistry.registerProjectile("acid_ball", 0.5f, 0.5f,
                    (type, level) -> new AcidBallProjectile(type, level, ball -> {
                        ball.setModel(new ResourceLocation(HallMod.MODID, "geo/acid_ball.geo.json"));
                        ball.setTexture(new ResourceLocation(HallMod.MODID, "textures/item/acid_ball.png"));
                        ball.setAnimation(new ResourceLocation(HallMod.MODID, "animations/acid_ball.animation.json"));
                    }));

    /** 毒箭：腐化骷髅的远程射击（GeckoLib 模型） */
    public static final RegistryObject<EntityType<PoisonArrowProjectile>> POISON_ARROW =
            EntityTypeRegistry.registerProjectile("poison_arrow", 0.5f, 0.5f,
                    (type, level) -> new PoisonArrowProjectile(type, level, arrow -> {
                        arrow.setModel(new ResourceLocation(HallMod.MODID, "geo/poison_arrow.geo.json"));
                        arrow.setTexture(new ResourceLocation(HallMod.MODID, "textures/entity/poison_arrow.png"));
                        arrow.setAnimation(new ResourceLocation(HallMod.MODID, "animations/poison_arrow.animation.json"));
                    }));

    /** 通用腐蚀弹体：吐息/箭雨/喷吐/酸弹共用（运行时配置伤害/颜色/重力/效果） */
    public static final RegistryObject<EntityType<CorruptionDart>> CORRUPTION_DART =
            EntityTypeRegistry.registerNoopEntity("corruption_dart", 0.3f, 0.3f, CorruptionDart::new);

    /** 腐蚀液池：技能落点的持续区域 */
    public static final RegistryObject<EntityType<CorruptionPoolEntity>> CORRUPTION_POOL =
            EntityTypeRegistry.registerNoopEntity("corruption_pool", 3.0f, 0.4f, CorruptionPoolEntity::new);

    /** 方块墙（自绘立方体，临时阻挡/墙技能） */
    public static final RegistryObject<EntityType<CuboidEntity>> CUBOID =
            EntityTypeRegistry.registerNoopEntity("cuboid", 1.0f, 1.0f, CuboidEntity::new);

    /** 护盾（自绘二十面体） */
    public static final RegistryObject<EntityType<ShieldEntity>> SHIELD =
            EntityTypeRegistry.registerNoopEntity("shield", 1.0f, 1.0f, ShieldEntity::new);
}
