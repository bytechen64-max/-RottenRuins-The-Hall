package org.bytechen.rottenruins.overworld.registry.corruption;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;

/**
 * 腐化系效果注册表 —— 简化版移植。
 *
 * <p>6 个效果先用最简实现占位：流血按周期扣血，其余只保留名字/颜色/分类；
 * 原本的转化、适应、恐惧 AI 等逻辑等机制层再补。</p>
 */
public class CorruptionEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, HallMod.MODID);

    /** 腐化（周期真伤占位）。 */
    public static final RegistryObject<MobEffect> CORRUPTION = EFFECTS.register("corruption",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6B3FA0) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    int interval = 60 >> amplifier;
                    return interval <= 0 || duration % interval == 0;
                }

                @Override
                public void applyEffectTick(LivingEntity entity, int amplifier) {
                    entity.hurt(entity.damageSources().magic(), 1.0F + amplifier * 0.5F);
                }
            });

    /** 裂解（占位：无 tick 效果）。 */
    public static final RegistryObject<MobEffect> LYSIS = EFFECTS.register("lysis",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x9B2E2E) { });

    /** 绝望 / 瘴气（占位）。 */
    public static final RegistryObject<MobEffect> MIASMA = EFFECTS.register("miasma",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x3A2B4A) { });

    /** 硬化（占位）。 */
    public static final RegistryObject<MobEffect> SCLEROSIS = EFFECTS.register("sclerosis",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x8A7A5A) { });

    /** 流血（周期扣血）。 */
    public static final RegistryObject<MobEffect> BLEEDING = EFFECTS.register("bleeding",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0xAA0000) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    int interval = 40 >> amplifier;
                    return interval <= 0 || duration % interval == 0;
                }

                @Override
                public void applyEffectTick(LivingEntity entity, int amplifier) {
                    entity.hurt(entity.damageSources().magic(), 1.0F);
                }
            });

    /** 恐惧（占位）。 */
    public static final RegistryObject<MobEffect> FEAR = EFFECTS.register("fear",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x1B1B2F) { });
}
