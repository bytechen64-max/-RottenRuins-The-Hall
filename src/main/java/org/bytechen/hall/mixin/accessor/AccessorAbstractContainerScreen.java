package org.bytechen.hall.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读 {@code AbstractContainerScreen.menu}。
 *
 * <p>这个字段声明在父类上（{@code protected final T menu}），泛型擦除后描述符是
 * {@code Lnet/minecraft/world/inventory/AbstractContainerMenu;}。之所以不直接
 * 在 {@code CreativeModeInventoryScreenMixin} 里 {@code @Shadow}，是想让字段解析
 * 落在它真正声明的那一层上 —— 少一层「继承来的字段能不能 shadow」的怀疑。</p>
 */
@Mixin(AbstractContainerScreen.class)
public interface AccessorAbstractContainerScreen {

    @Accessor("menu")
    AbstractContainerMenu splendiding$getMenu();
}
