package org.bytechen.rottenruins.utils.entity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

/**
 * 直接改血（不走 {@code hurt}）的伤害汇报点。
 *
 * <p>{@link SetHealthDieUnti} 会把「改之前 − 改之后」的正差值发到这里，
 * 让生命汲取之类需要在"本次实际造成伤害"上结算的效果也能吃到改血路径。
 * 只做转发，不依赖任何注册表。</p>
 */
public final class DirectDamageHooks {

    /** 一次直接改血造成的伤害。 */
    public interface Listener {
        void onDirectDamage(LivingEntity target, @Nullable LivingEntity source, float amount);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private DirectDamageHooks() {}

    public static void add(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void fire(LivingEntity target, @Nullable LivingEntity source, float amount) {
        if (target == null || amount <= 0.0F || LISTENERS.isEmpty()) {
            return;
        }
        for (Listener listener : LISTENERS) {
            listener.onDirectDamage(target, source, amount);
        }
    }
}
