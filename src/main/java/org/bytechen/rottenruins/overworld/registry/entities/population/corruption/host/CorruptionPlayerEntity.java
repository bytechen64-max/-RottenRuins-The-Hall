package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.HallMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;

import java.util.function.Consumer;

/** 同化玩家：近战 + B10（8 根 / 4.0）+ B11 血肉牵引（0.9）+ B12 汲血（0.35）。 */
public class CorruptionPlayerEntity extends BaseHostEntity {
    private static final EntityDataAccessor<String> SKIN_PLAYER_ID = SynchedEntityData.defineId(
            CorruptionPlayerEntity.class, EntityDataSerializers.STRING);
    public static final ResourceLocation MODEL_SLIM = resource("geo/corruption_player_slim.geo.json");
    public static final ResourceLocation MODEL_WIDE = resource("geo/corruption_player_wide.geo.json");

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(HallMod.MODID, path);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SKIN_PLAYER_ID, "");
    }

    /** UUID 或玩家名。未指定时按命名牌上的玩家名查询皮肤。 */
    public String getSkinPlayerId() {
        String id = this.entityData.get(SKIN_PLAYER_ID);
        return id.isEmpty() && hasCustomName() ? getCustomName().getString() : id;
    }

    public void setSkinPlayerId(String id) {
        this.entityData.set(SKIN_PLAYER_ID, id == null ? "" : id.trim());
    }

    // 用实体 UUID 选择默认外观，服务端、所有客户端及重载存档后保持一致。
    private int defaultSkinVariant() {
        return Math.floorMod(getUUID().hashCode(), 14);
    }

    @Override
    public ResourceLocation model() {
        return defaultSkinVariant() < 7 ? MODEL_SLIM : MODEL_WIDE;
    }

    @Override
    public ResourceLocation texture() {
        int variant = defaultSkinVariant();
        return resource("textures/entity/corruption_player_"
                + (variant < 7 ? "slim_" + (variant + 1) : "wide_1") + ".png");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinPlayerId", this.entityData.get(SKIN_PLAYER_ID));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinPlayerId(tag.getString("SkinPlayerId"));
    }

    public CorruptionPlayerEntity(EntityType<? extends CorruptionPlayerEntity> entityType, Level level,
                                  Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
        this.enableBoneBurst(8, 4.0F);
        this.enableFleshPull(0.9);
        this.enableLifesteal(0.35F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 15.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
