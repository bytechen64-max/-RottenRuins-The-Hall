package org.bytechen.rottenruins.event.impl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.content.magic.spell.SoulDrink;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.Nullable;

/**
 * <b>灵魂吸引</b>的骷髅头伤害接管。
 *
 * <p>原版 {@link WitherSkull} 命中固定给 8 点。这里在 {@code LivingHurtEvent} 里把
 * "带 {@link SoulDrink#SKULL_TAG} 标记的骷髅头"造成的伤害改成本模组算好的数值
 * （{@code 10 + count × 0.5}，已含法杖等级与法强）。</p>
 *
 * <p>不取消事件、也不自己调 {@code hurt}：只改数值，所以受击红闪、击退、死亡消息、
 * 击杀归因（骷髅头的 owner 就是施法者）全都保持原版行为。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulDrinkSkullHandler {

    private SoulDrinkSkullHandler() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getDirectEntity() instanceof WitherSkull skull)
                || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        var data = skull.getPersistentData();
        if (!data.getBoolean(SoulDrink.SKULL_TAG)) {
            return;
        }
        float damage = data.getFloat(SoulDrink.SKULL_DAMAGE_TAG);
        if (damage <= 0f) {
            return;
        }
        event.setAmount(damage);

        // 收尾表现：灵魂碎片 + 音效（服务端发包，客户端都能看到）
        ServerPlayer caster = skull.getOwner() instanceof ServerPlayer player ? player : null;
        SpellDamageUtil.applyKillCredit(caster, victim);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5d, victim.getZ(),
                18, 0.4d, 0.5d, 0.4d, 0.06d);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                net.minecraft.sounds.SoundEvents.WITHER_HURT, victim.getSoundSource(), 0.7f, 1.3f);
        // 标记用掉一次就清掉，避免同一颗骷髅头重复改写
        data.remove(SoulDrink.SKULL_TAG);
    }
}
