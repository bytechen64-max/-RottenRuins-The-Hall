package org.bytechen.rottenruins;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.advancement.RotAdvancementTriggers;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.api.magic.MagicAttributeProvider;
import org.bytechen.rottenruins.api.magic.MagicStatRegistry;
import org.bytechen.rottenruins.overworld.difficulty.HallDifficulty;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.overworld.registry.*;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionContent;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ulcerated.UlceratedConversionRules;
import org.bytechen.rottenruins.overworld.registry.spawning.SplendidingSpawnPlacements;
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

    public static final String MODID = "rotten_ruins";
    public static final Logger LOGGER = LogUtils.getLogger();

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
        WandEnchantments.ENCHANTMENTS.register(modEventBus);
        WandRecipes.SERIALIZERS.register(modEventBus);
        RegisterEffect.EFFECTS.register(modEventBus);
        SoundEventRegistry.SOUND_EVENTS.register(modEventBus);
        EntityTypeRegistry.ENTITY_TYPES.register(modEventBus);
        RegisterParticles.PARTICLE_TYPES.register(modEventBus);
        RegisterBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        RegisterFeature.FEATURES.register(modEventBus);

        // 腐化系内容（原 corruption 工程，简化移植）：方块 / 物品 / 生物 / 效果
        CorruptionContent.register(modEventBus);

        // 魔法属性（法力上限 / 回速 / 回复倍率 / 法术强度 / 槽位数）。
        // 注册后由 RegisterAttributes 里的 EntityAttributeModificationEvent 处理器
        // 把这些属性挂到玩家身上 —— 少了那一步 player.getAttribute(...) 会一直是 null。
        RegisterAttributes.ATTRIBUTES.register(modEventBus);

        // 群系修改器序列化器
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);

        // Cosmic starfield rendering（参考 Live 模组）
        if (FMLEnvironment.dist.isClient()) {
            org.bytechen.rottenruins.client.cosmic.CosmicClient.init(modEventBus);
            // mask 效果层系统（可与星空层叠加：泛光、以及以后新增的其他效果）
            org.bytechen.rottenruins.client.mask.MaskLayerClient.init(modEventBus);
        }

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(SplendidingSpawnPlacements::register);
        // 自定义成就判据（首次进入世界等）：同样排到主线程执行，避免在并行 commonSetup 里
        // 改 CriteriaTriggers 的静态判据表；enqueueWork 会在 commonSetup 完成前跑完，
        // 仍早于服务端解析进度数据包。
        event.enqueueWork(RotAdvancementTriggers::registerAll);

        NetworkHelper.register();
        ConfigHelper.registry();

        // 注册 Hall 自定义难度
        HallDifficulty.registerAll();

        // 感染兜底转化规则（溃烂）：没有专属感染形态的生物按碰撞体积分档
        // 转化为斥候 / 巨碑（判断与转化在 infcore 的感染流程里执行）
        UlceratedConversionRules.register();

        // 魔法数值的属性来源：把 hall:max_mana / mana_regeneration / magic_slots
        // 接进 MagicStats 求值链。必须早于任何 MagicStats 求值 —— 放这里安全，
        // 因为玩家要等世界加载才存在。
        MagicStatRegistry.register(new MagicAttributeProvider());

        LOGGER.info("Hall common setup complete");
    }
}
