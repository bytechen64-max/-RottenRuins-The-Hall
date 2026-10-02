package org.bytechen.hall;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.overworld.difficulty.HallDifficulty;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.overworld.registry.*;
import org.bytechen.hall.overworld.registry.entities.population.ulcerated.UlceratedConversionRules;
import org.bytechen.hall.overworld.registry.spawning.SplendidingSpawnPlacements;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

@Mod(HallMod.MODID)
public class HallMod {

    public static final String MODID = "hall";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 感染类型 —— 进化表（{@code data/hall/infcore_evolution/*.json} 里的 {@code type}）
     * 与兜底档位共用；被王庭感染生物击杀的生物按该类型被感染。
     */
    public static final ResourceLocation INFECTION_TYPE = new ResourceLocation(MODID, "inf");

    /** 群系修改器序列化器注册（数据包级别群系修改） */
    private static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, HallMod.MODID);

    @SuppressWarnings("removal")
    public HallMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 确保 BlockSetType / WoodType 在方块注册前初始化
        HallBlockSetTypes.init();

        // 方块 / 物品 / 效果 / 音效 / 实体 / 粒子 / 方块实体 注册
        RegisterTab.CREATIVE_MODE_TABS.register(modEventBus);
        RegisterBlock.BLOCKS.register(modEventBus);
        RegisterItem.ITEMS.register(modEventBus);
        RegisterEffect.EFFECTS.register(modEventBus);
        SoundEventRegistry.SOUND_EVENTS.register(modEventBus);
        EntityTypeRegistry.ENTITY_TYPES.register(modEventBus);
        RegisterParticles.PARTICLE_TYPES.register(modEventBus);
        RegisterBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        RegisterFeature.FEATURES.register(modEventBus);

        // 群系修改器序列化器
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);

        // Cosmic starfield rendering（参考 Live 模组）
        if (FMLEnvironment.dist.isClient()) {
            org.bytechen.hall.client.cosmic.CosmicClient.init(modEventBus);
            // mask 效果层系统（可与星空层叠加：泛光、以及以后新增的其他效果）
            org.bytechen.hall.client.mask.MaskLayerClient.init(modEventBus);
        }

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(SplendidingSpawnPlacements::register);

        NetworkHelper.register();
        ConfigHelper.registry();

        // 注册 Hall 自定义难度
        HallDifficulty.registerAll();

        // 感染兜底转化规则（溃烂）：没有专属感染形态的生物按碰撞体积分档
        // 转化为斥候 / 巨碑（判断与转化在 infcore 的感染流程里执行）
        UlceratedConversionRules.register();

        LOGGER.info("Hall common setup complete");
    }
}
