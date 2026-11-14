package org.bytechen.rottenruins.client.combat.slash.trail.anchors;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.bytechen.rottenruins.client.combat.slash.trail.*;
import org.bytechen.rottenruins.client.rend.glint.LateOutlineRenderState;
import org.bytechen.rottenruins.core.combat.slash.TrailHistory;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.util.*;

/** Scene refraction constrained to the same world-space ribbon, with one scene copy per batch. */
public final class SlashAirRefraction {
    private record Entry(float[] vertices, Matrix4f projection, SlashEffect effect, float clock) {}
    private static final List<Entry> ENTRIES=new ArrayList<>();
    private static TextureTarget scene;
    private static final int[][] CORNERS={{0,0},{0,1},{1,1},{1,0}};
    private SlashAirRefraction() {}
    public static void clear(){ENTRIES.clear();}
    public static void enqueue(List<List<TrailHistory.Point>> sections,List<TrailHistory.Sample> samples,
                               Matrix4f pose,Matrix4f projection,SlashEffect effect,double now,
                               double cutStart,double cutEnd,float opacity) {
        if(effect.refraction()<=0 || ENTRIES.size()>=64)return;
        int rows=samples.size(),cols=sections.get(0).size();
        float[] data=new float[(rows-1)*(cols-1)*4*6];int k=0;
        Matrix4f matrix=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose);
        double first=samples.get(0).time(),last=samples.get(rows-1).time();
        for(int i=0;i<rows-1;i++)for(int j=0;j<cols-1;j++)for(int[] corner:CORNERS) {
            int row=i+corner[0],col=j+corner[1];var point=sections.get(row).get(col);var root=sections.get(row).get(0);
            // Extend the air envelope beyond the luminous outer rim, where background is visible.
            point=new TrailHistory.Point(root.x()+(point.x()-root.x())*1.18,root.y()+(point.y()-root.y())*1.18,root.z()+(point.z()-root.z())*1.18);
            var p=matrix.transformPosition(new org.joml.Vector3f((float)point.x(),(float)point.y(),(float)point.z()));
            double birth=samples.get(row).time();
            data[k++]=p.x;data[k++]=p.y;data[k++]=p.z;
            data[k++]=opacity*(float)Math.sqrt(TrailRibbonGeometry.fade(now-birth,effect.lifetimeTicks()))*TrailSampling.edgeFade(birth,first,last);
            data[k++]=(float)((birth-cutStart)/Math.max(.001,cutEnd-cutStart));data[k++]=col/(float)(cols-1);
        }
        ENTRIES.add(new Entry(data,new Matrix4f(projection),effect,(float)(now%24000)/20));
    }
    public static void renderAll() {
        if(ENTRIES.isEmpty())return;
        var mc=Minecraft.getInstance();var shader=SlashTrailShaders.air;
        if(shader==null){ENTRIES.clear();return;}
        var main=mc.getMainRenderTarget();Matrix4f oldProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        LateOutlineRenderState.prepareMainTargetPass();
        try {
            if(scene==null||scene.width!=main.width||scene.height!=main.height) {
                if(scene!=null)scene.destroyBuffers();
                scene=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);
                scene.setFilterMode(GL11.GL_LINEAR);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
            GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,scene.width,scene.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            main.bindWrite(false);
            shader.setSampler("ScreenTexture",scene.getColorTextureId());
            shader.safeGetUniform("AirScreenSize").set((float)main.width,(float)main.height);
            var buffers=MultiBufferSource.immediate(new BufferBuilder(32768));
            for(Entry entry:ENTRIES) {
                var e=entry.effect;
                RenderSystem.setProjectionMatrix(entry.projection,VertexSorting.DISTANCE_TO_ORIGIN);
                shader.safeGetUniform("AirStyle").set(e.refraction(),e.dispersion(),e.noise(),entry.clock);
                var v=buffers.getBuffer(SlashTrailRenderType.air());var d=entry.vertices;
                for(int i=0;i<d.length;i+=6)v.vertex(d[i],d[i+1],d[i+2]).color(1F,1F,1F,d[i+3]).uv(d[i+4],d[i+5]).endVertex();
                buffers.endBatch();
            }
        } finally {
            ENTRIES.clear();model.popPose();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProjection,VertexSorting.DISTANCE_TO_ORIGIN);
            LateOutlineRenderState.finishMainTargetPass();
        }
    }
}
