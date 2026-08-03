package org.bytechen.hall.overworld.registry.items;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.bytechen.hall.api.IAutoRenderableItem;
import org.bytechen.hall.utils.ModUtils;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Base class for geo-animated items with an optional emissive glow layer.
 * <p>
 * Automatically specifies Geo model resource files (geo model, texture, animation,
 * and optional glow texture) from constructor parameters or name-based conventions.
 * </p>
 *
 * <h3>Name-based conventions</h3>
 * When using the name-based constructor, resource paths are auto-derived:
 * <pre>
 * Model:     hall:geo/{name}.geo.json
 * Texture:   hall:textures/item/{name}.png
 * Animation: null (override in subclass or use full-spec ctor for animations)
 * Glow:      hall:textures/item/{name}_glow_layer.png (only when useGlowLayer=true)
 * </pre>
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * // With glow layer using name convention:
 * public static final RegistryObject<Item> GLOWING_GEM =
 *     ITEMS.register("glowing_gem", () -> new BaseGlowingGeoItem(
 *         new Item.Properties().stacksTo(1),
 *         "glowing_gem", true  // name + enable glow
 *     ));
 * }</pre>
 */
public class BaseGlowingGeoItem extends Item implements IAutoRenderableItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;
    private final ResourceLocation glowTexture;
    private final boolean hasGlowLayer;

    // ==================== Full-spec constructors ====================

    /**
     * Full constructor with explicit resource locations for all textures.
     *
     * @param properties  item properties
     * @param model       geo model resource location
     * @param texture     main texture resource location
     * @param animation   animation resource location
     * @param glowTexture glow/emissive texture resource location (null = no glow)
     */
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

    /**
     * Full constructor without glow layer.
     */
    public BaseGlowingGeoItem(Properties properties,
                              ResourceLocation model, ResourceLocation texture,
                              ResourceLocation animation) {
        this(properties, model, texture, animation, null);
    }

    // ==================== Name-based constructors ====================

    /**
     * Name-based constructor with glow layer toggle.
     * <p>
     * Auto-derives resource paths from the item name using conventions.
     * </p>
     *
     * @param properties   item properties
     * @param name         base name for auto-deriving resource paths
     * @param useGlowLayer whether to expect and use a glow layer texture
     */
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

    /**
     * Name-based constructor without glow layer.
     *
     * @param properties item properties
     * @param name       base name for auto-deriving resource paths
     */
    public BaseGlowingGeoItem(Properties properties, String name) {
        this(properties, name, false);
    }

    // ==================== ISTER (Forge builtin/entity hook) ====================

    /**
     * Provides the GeoItemRenderer as an ISTER via {@link IClientItemExtensions}.
     * <p>
     * This is the standard Forge pattern: the item model JSON uses
     * {@code "parent": "builtin/entity"}, which causes vanilla to use ISTER
     * rendering. The display transforms from the item model JSON are applied
     * <em>before</em> our renderer is called, so hand-held positioning works.
     * </p>
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

    // ==================== IGeoResources ====================

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

    // ==================== Glow layer ====================

    @Override
    public ResourceLocation glowTexture() {
        return glowTexture;
    }

    @Override
    public boolean hasGlowLayer() {
        return hasGlowLayer;
    }

    // ==================== GeoAnimatable ====================

    /**
     * Register animation controllers here in subclasses that need item animations.
     * <p>
     * Default implementation is a no-op — override to add
     * {@link software.bernie.geckolib.core.animation.AnimationController}s.
     * </p>
     *
     * @param controllers the controller registrar provided by GeckoLib
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No-op by default: override in subclasses to add animation controllers
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
