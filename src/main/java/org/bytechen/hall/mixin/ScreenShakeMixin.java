package org.bytechen.hall.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.bytechen.hall.client.rend.ScreenShake;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 {@link ScreenShake} 的偏移接到当帧的投影矩阵上。
 *
 * <h3>为什么钩 {@code getProjectionMatrix} 而不是相机事件</h3>
 * <p>{@code ViewportEvent.ComputeCameraAngles} 只能改<b>角度</b>；要做"整幅画面
 * 平移"就必须动投影。而 {@code getProjectionMatrix(double)} 正是原版每帧
 * 为世界与手持物各建一次投影的地方 —— 在这里左乘一个纯 NDC 平移矩阵，
 * 整条世界渲染链（地形 / 实体 / 我们的自发光特效 / 光影包回放）会一起被平移，
 * 而 HUD、准星、GUI 走的是正交矩阵，完全不受影响。</p>
 *
 * <p>方法签名 {@code (double)Matrix4f} 与 {@code GameRendererMixin} 里已有的一处
 * 调用（{@code getProjectionMatrix(accessor.invokeGetFov(...))}）对得上，
 * 改映射时编译器会先报错，不会悄悄失效。</p>
 */
@Mixin(value = GameRenderer.class, priority = 900)
public class ScreenShakeMixin {

    @Inject(method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;",
            at = @At("RETURN"),
            cancellable = true)
    private void hall$applyScreenShake(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (!ScreenShake.active()) return;
        cir.setReturnValue(ScreenShake.applyTo(cir.getReturnValue()));
    }
}
