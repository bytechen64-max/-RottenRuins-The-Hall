package org.bytechen.rottenruins.event.impl;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterAttributes;
import org.bytechen.rottenruins.utils.entity.DirectDamageHooks;

/**
 * 生命汲取：造成伤害后按 {@code 实际伤害 × life_steal} 回复自身。
 *
 * <p>两条入口都要走：{@link LivingDamageEvent} 负责正常 {@code hurt} 流程（护甲/抗性结算之后的数值），
 * {@link DirectDamageHooks} 负责 {@code SetHealthDieUnti} 这类直接改血、不发事件的路径。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LifeStealHandlers {

    private LifeStealHandlers() {}

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        apply(event.getEntity(), event.getSource().getEntity(), event.getAmount());
    }

    /** {@code SetHealthDieUnti} 的改血汇报（由 {@link LifeStealSetup} 在初始化时挂上）。 */
    public static void onDirectDamage(LivingEntity target, LivingEntity source, float amount) {
        apply(target, source, amount);
    }

    private static void apply(LivingEntity target, Entity source, float amount) {
        if (!(source instanceof LivingEntity attacker) || attacker == target) {
            return;
        }
        if (amount <= 0.0F || attacker.level().isClientSide) {
            return;
        }
        AttributeInstance instance = attacker.getAttribute(RegisterAttributes.LIFE_STEAL.get());
        if (instance == null) {
            return;
        }
        double value = instance.getValue();
        if (value <= 0.0d) {
            return;
        }
        attacker.heal((float) (amount * value));
    }
}
