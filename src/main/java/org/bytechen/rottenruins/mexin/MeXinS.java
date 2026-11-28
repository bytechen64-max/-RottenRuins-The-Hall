package org.bytechen.rottenruins.mexin;

import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;
import org.jetbrains.annotations.NotNull;

/** 打到原版类上的钩子集合（每个 public static 方法配一个 {@link MeXin} 注解）。 */
public final class MeXinS {

    private MeXinS() {
    }

    /**
     * 目标：{@code LivingEntity#getActiveEffects()}（SRG {@code m_21220_}），注入在返回之前。
     * <p>带奥数印记时把返回值换成 {@code activeEffects} 字段的原始 values —— 这样即便子类
     * （或别的模组）重写里过滤、隐藏了效果，印记在场也照样拿到全量效果；没有印记则原样返回。</p>
     * <p>{@code includextend = true}：子类里对 {@code getActiveEffects()} 的覆盖一并生效。</p>
     */
    @MeXin(clazz = LivingEntity.class, method = "getActiveEffects", srg = "m_21220_",
            desc = "()Ljava/util/Collection;", type = MeXinType.BODY, includextend = true,includmy = true)
    public static void onGetActiveEffects(@NotNull LivingEntity self,
                                          @NotNull MeXinCall<Object> call) {
        if (!CustomEffects.hasEffect(self, RegisterCustomEffect.ARCANE_MARK)) {
            return;
        }
        call.set(self.activeEffects.values());
    }
}
