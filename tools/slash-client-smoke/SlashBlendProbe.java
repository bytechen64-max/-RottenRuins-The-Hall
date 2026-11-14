package org.bytechen.rottenruins.smoke;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.bytechen.rottenruins.client.combat.slash.trail.SlashTrailRenderType;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Uses the real Minecraft shader JSON, ShaderInstance.apply and production RenderType. */
public final class SlashBlendProbe {
    public static void run(){
        Minecraft mc=Minecraft.getInstance();TextureTarget target=new TextureTarget(32,32,true,Minecraft.ON_OSX);
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        try{
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,0,32,-1,1),VertexSorting.ORTHOGRAPHIC_Z);
            for(boolean priorTransparent:new boolean[]{false,true}){
                int[] low=draw(target,.2F,false,priorTransparent),high=draw(target,.8F,false,priorTransparent);
                if(high[1]-low[1]<5)throw new AssertionError("Real shader alpha is ignored; low="+low[1]+" high="+high[1]);
                int[] hidden=draw(target,.8F,true,priorTransparent);
                if(Math.abs(hidden[0]-89)>2||Math.abs(hidden[1]-31)>2||Math.abs(hidden[2]-20)>2)
                    throw new AssertionError("Trail ignores scene depth");
                System.out.println("SLASH_BLEND_PROBE_PASS priorTransparent="+priorTransparent+" lowG="+low[1]+" highG="+high[1]+" depthOcclusion=true");
            }
            var shader=org.bytechen.rottenruins.client.combat.slash.trail.SlashTrailShaders.shader;
            shader.safeGetUniform("TrailStyle").set(.5F,1.2F,1.6F,0);
            int[] dim=draw(target,.4F,false,true);
            shader.safeGetUniform("TrailStyle").set(1.5F,1.2F,1.6F,0);
            int[] bright=draw(target,.4F,false,true);
            if(bright[1]-dim[1]<10)throw new AssertionError("Effect brightness parameter has no rendered effect");
            System.out.println("SLASH_EFFECT_PARAMETER_PASS dimG="+dim[1]+" brightG="+bright[1]);
        }finally{
            GL11.glClearDepth(1);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
            model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }
    private static int[] draw(TextureTarget target,float alpha,boolean occluded,boolean priorTransparent){
        target.setClearColor(.35F,.12F,.08F,1);GL11.glClearDepth(1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        if(occluded){GL11.glClearDepth(0);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL11.glClearDepth(1);}
        if(priorTransparent)new BlendMode(770,771,1,771,32774).apply();else new BlendMode().apply();
        var buffers=MultiBufferSource.immediate(new BufferBuilder(256));var v=buffers.getBuffer(SlashTrailRenderType.shader());
        for(float[] p:new float[][]{{0,0},{32,0},{32,32},{0,32}})
            v.vertex(p[0],p[1],0).color(.05F,.6F,1F,alpha).uv(.65F,.8F).endVertex();
        buffers.endBatch();
        var pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
        return new int[]{pixel.get(0)&255,pixel.get(1)&255,pixel.get(2)&255};
    }
}
