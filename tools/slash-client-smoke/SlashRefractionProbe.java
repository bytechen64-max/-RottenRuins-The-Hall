package org.bytechen.rottenruins.smoke;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.bytechen.rottenruins.client.combat.slash.trail.*;
import org.bytechen.rottenruins.client.combat.slash.trail.anchors.*;
import org.bytechen.rottenruins.core.combat.slash.TrailHistory;
import com.google.gson.JsonObject;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Tests background displacement and occlusion through the production Minecraft shader. */
public final class SlashRefractionProbe {
    public static void run(){
        var mc=Minecraft.getInstance();var sh=SlashTrailShaders.air;
        if(sh==null)throw new AssertionError("Air shader did not link");
        var source=new TextureTarget(32,32,false,Minecraft.ON_OSX);
        var target=new TextureTarget(32,32,true,Minecraft.ON_OSX);
        Matrix4f old=new Matrix4f(RenderSystem.getProjectionMatrix());
        var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        try{
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,0,32,-1,1),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1,1,1,1);
            gradient(source);var sourcePixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,sourcePixel);System.out.println("AIR_SOURCE red="+(sourcePixel.get(0)&255)+" tex="+source.getColorTextureId()+" error="+GL11.glGetError());sh.setSampler("ScreenTexture",source.getColorTextureId());sh.safeGetUniform("AirScreenSize").set(32F,32F);
            int off=draw(target,0,false),on=draw(target,8,false),hidden=draw(target,8,true);
            if(Math.abs(on-off)<4)throw new AssertionError("Refraction does not displace actual background: "+off+" / "+on);
            if(Math.abs(hidden-off)>2)throw new AssertionError("Air refraction ignores scene depth");
            System.out.println("SLASH_REFRACTION_PROBE_PASS off="+off+" on="+on+" occluded="+hidden);
            queueProbe();
        }finally{
            GL11.glClearDepth(1);source.destroyBuffers();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
            model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(old,VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }
    private static void gradient(RenderTarget target){
        target.bindWrite(true);RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
        for(int x=0;x<32;x++){
            RenderSystem.enableScissor(x,0,1,32);GL11.glClearColor(x/31F,.2F,.3F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        RenderSystem.disableScissor();GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }
    private static void queueProbe(){
        // Exercise the REAL queue, expanded world mesh, scene blit, projection replay and cleanup.
        var target=Minecraft.getInstance().getMainRenderTarget();int w=target.width,h=target.height;
        var projection=new Matrix4f().setOrtho(0,w,0,h,-1,1);
        List<TrailHistory.Sample> samples=new ArrayList<>();List<List<TrailHistory.Point>> sections=new ArrayList<>();
        for(int row=0;row<=4;row++){
            var points=List.of(new TrailHistory.Point(0,h*row/4F,0),new TrailHistory.Point(w*.85F,h*row/4F,0));
            samples.add(new TrailHistory.Sample(row/4.,points));sections.add(points);
        }
        int[] values=new int[3];
        for(int mode=0;mode<3;mode++){
            target.bindWrite(true);RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
            for(int x=0;x<w;x++){
                RenderSystem.enableScissor(x,0,1,h);GL11.glClearColor((x%32)/31F,.2F,.3F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            }
            RenderSystem.disableScissor();GL11.glClearDepth(mode==2?0:1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL11.glClearDepth(1);
            JsonObject json=new JsonObject();json.addProperty("refraction",mode==0?0:9);
            SlashAirRefraction.enqueue(sections,samples,new Matrix4f(),projection,SlashEffect.parse(json),1,0,1,1);
            SlashAirRefraction.renderAll();
            var pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels((int)(w*.84),(int)(h*.65),1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            values[mode]=pixel.get(0)&255;
        }
        if(Math.abs(values[1]-values[0])<3||Math.abs(values[2]-values[0])>2)
            throw new AssertionError("Full air queue/scene copy failed: "+Arrays.toString(values));
        int error=GL11.glGetError();if(error!=0)throw new AssertionError("Refraction queue GL error: "+error);
        System.out.println("SLASH_REFRACTION_QUEUE_PASS clean-scene copy + expanded mesh + projection replay + depth: "+Arrays.toString(values));
    }
    private static int draw(TextureTarget target,float strength,boolean hidden){
        gradient(target);if(hidden){GL11.glClearDepth(0);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL11.glClearDepth(1);}
        SlashTrailShaders.air.safeGetUniform("AirStyle").set(strength,.15F,.3F,0);
        var buffers=MultiBufferSource.immediate(new BufferBuilder(256));var v=buffers.getBuffer(SlashTrailRenderType.air());
        for(float[] p:new float[][]{{0,0},{32,0},{32,32},{0,32}})
            v.vertex(p[0],p[1],0).color(1F,1F,1F,1F).uv(.65F,.4F+p[0]/32*.8F).endVertex();
        buffers.endBatch();var pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
        return pixel.get(0)&255;
    }
}
