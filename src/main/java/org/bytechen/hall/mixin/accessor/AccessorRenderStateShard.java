package org.bytechen.hall.mixin.accessor;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.class)
public interface AccessorRenderStateShard {
    @Accessor("TRANSLUCENT_TRANSPARENCY")
    static RenderStateShard.TransparencyStateShard splendiding$getTranslucentTransparency() {
        throw new UnsupportedOperationException();
    }

    @Accessor("LEQUAL_DEPTH_TEST")
    static RenderStateShard.DepthTestStateShard splendiding$getLequalDepthTest() {
        throw new UnsupportedOperationException();
    }

    @Accessor("NO_CULL")
    static RenderStateShard.CullStateShard splendiding$getNoCull() {
        throw new UnsupportedOperationException();
    }

    @Accessor("COLOR_DEPTH_WRITE")
    static RenderStateShard.WriteMaskStateShard splendiding$getColorDepthWrite() {
        throw new UnsupportedOperationException();
    }

    @Accessor("EQUAL_DEPTH_TEST")
    static RenderStateShard.DepthTestStateShard splendiding$getEqualDepthTest() {
        throw new UnsupportedOperationException();
    }

    @Accessor("COLOR_WRITE")
    static RenderStateShard.WriteMaskStateShard splendiding$getColorWrite() {
        throw new UnsupportedOperationException();
    }

    @Accessor("NO_DEPTH_TEST")
    static RenderStateShard.DepthTestStateShard splendiding$getNoDepthTest() {
        throw new UnsupportedOperationException();
    }

    @Accessor("MAIN_TARGET")
    static RenderStateShard.OutputStateShard splendiding$getMainTarget() {
        throw new UnsupportedOperationException();
    }
}