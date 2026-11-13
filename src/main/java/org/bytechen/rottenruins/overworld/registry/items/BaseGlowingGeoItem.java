package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.bytechen.rottenruins.api.IAutoRenderableItem;
import org.bytechen.rottenruins.utils.ModUtils;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Base class for geo-animated items with an optional emissive glow layer;
 * the name-based ctor derives hall:geo/{name}.geo.json and hall:textures/item/{name}.png,
 * plus {name}_glow_layer.png when the glow layer is enabled.
 */
public class BaseGlowingGeoItem extends Item implements IAutoRenderableItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;
    private final ResourceLocation glowTexture;
    private final boolean hasGlowLayer;

    public BaseGlowingGeoItem(Properties properties,
                              ResourceLocation model, ResourceLocation texture,
                              ResourceLocation animation, ResourceLocation glowTexture) {
        super(properties);
        this.model = model;
        this.texture = texture;
        this.animation = animation;
        this.glowTexture = glowTexture;
        this.hasGlowLayer = glowTexture != null;
    }

    public BaseGlowingGeoItem(Properties properties,
                              ResourceLocation model, ResourceLocation texture,
                              ResourceLocation animation) {
        this(properties, model, texture, animation, null);
    }

    public BaseGlowingGeoItem(Properties properties, String name, boolean useGlowLayer) {
        super(properties);
        this.model = ModUtils.modLoc("geo/" + name + ".geo.json");
        this.texture = ModUtils.modLoc("textures/item/" + name + ".png");
        this.animation = null;
        this.glowTexture = useGlowLayer
                ? ModUtils.modLoc("textures/item/" + name + "_glow_layer.png")
                : null;
        this.hasGlowLayer = useGlowLayer;
    }

    public BaseGlowingGeoItem(Properties properties, String name) {
        this(properties, name, false);
    }

    /**
     * The item model JSON must use "parent": "builtin/entity" so vanilla routes
     * rendering through this ISTER; model display transforms apply before it runs.
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return GeoItemRenderManager.getOrCreateRenderer(BaseGlowingGeoItem.this);
            }
        });
    }

    @Override
    public ResourceLocation model() {
        return model;
    }

    @Override
    public ResourceLocation texture() {
        return texture;
    }

    @Override
    public ResourceLocation animation() {
        return animation;
    }

    @Override
    public ResourceLocation glowTexture() {
        return glowTexture;
    }

    @Override
    public boolean hasGlowLayer() {
        return hasGlowLayer;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
