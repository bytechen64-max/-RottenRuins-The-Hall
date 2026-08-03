package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.entity.IAutoRenderableEntity;
import org.bytechen.hall.client.entity.IAutoRenderableProjectile;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.base.EntityManager;
import org.bytechen.hall.overworld.registry.entities.base.HallEntityManager;
import org.bytechen.hall.overworld.registry.entities.base.HallProjectileManager;
import org.bytechen.hall.overworld.registry.entities.population.ecological.BonecrusherEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.InfEndermanEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.InfPlayerEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.BlackHoleEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.CollapseEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.MeteoriteEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;
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


    public static final RegistryObject<EntityType<BonecrusherEntity>> BONECRUSHER=
            registerMob("bonecrusher", 5f, 5f,
                    (type, level) -> new BonecrusherEntity(type, level, defaultResources("bonecrusher")),
                    BonecrusherEntity::createAttributes);


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

    // ────────── 技能实体 —— 坍缩（多类型坍缩渲染） ──────────
    public static final RegistryObject<EntityType<CollapseEntity>> COLLAPSE =
            registerNoopEntity("collapse", 0.5f, 0.5f,
                    (type, level) -> new CollapseEntity(type, level));

    // ────────── 视觉实体 —— 黑洞（光线追踪引力透镜，纯视觉） ──────────
    public static final RegistryObject<EntityType<BlackHoleEntity>> BLACK_HOLE =
            registerNoopEntity("black_hole", 0.5f, 0.5f,
                    (type, level) -> new BlackHoleEntity(type, level));
}
