package org.bytechen.rottenruins.client.combat.slash.trail.anchors;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import org.bytechen.rottenruins.client.rend.glint.LateOutlineRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.api.combat.slash.*;
import org.bytechen.rottenruins.client.combat.slash.SlashClient;
import org.bytechen.rottenruins.client.combat.slash.trail.*;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineRenderer;
import org.bytechen.rottenruins.client.rend.glint.ShaderPackDetector;
import org.bytechen.rottenruins.client.rend.slash.ItemSlashAnimation;
import org.bytechen.rottenruins.core.combat.slash.TrailHistory;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Samples the SAME final model matrix used to render the blade; never projects a HUD quad. */
@Mod.EventBusSubscriber(modid="rotten_ruins",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackedSlashTrails {
    private record Scope(AbstractClientPlayer player, ItemStack stack, ItemDisplayContext context, boolean first) {}
    private static final Deque<Scope> SCOPES = new ArrayDeque<>();
    private static final Map<AbstractClientPlayer, State[]> STATES = new WeakHashMap<>();
    private record SolvedFrame(ItemDisplayContext context,int sequence,double time,Matrix4f modelPose) {}
    private static final Map<AbstractClientPlayer,SolvedFrame[]> FRAMES=new WeakHashMap<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(32768));
    private record DrawEntry(List<TrailHistory.Sample> samples,List<List<TrailHistory.Point>> sections,
                             SlashTrail material,SlashEffect effect,int sequence,double cutStart,double cutEnd,
                             double now,Matrix4f pose,Matrix4f projection) {}
    private static final List<DrawEntry> PENDING=new ArrayList<>();
    private static Matrix4f worldInverse = new Matrix4f();
    private static Matrix4f worldProjection = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static Object level;
    private static final class State {
        final TrailHistory history = new TrailHistory();
        int sequence; double lastTime = Double.NaN; Matrix4f oldPose;
        Vec3 origin; AnchorProfile.View view; SlashTrail material; SlashEffect effect; double cutStart,cutEnd;
    }
    private TrackedSlashTrails() {}
    public static void clear() { STATES.clear(); FRAMES.clear(); SCOPES.clear(); PENDING.clear(); SlashAirRefraction.clear(); }
    public static void clear(AbstractClientPlayer player) { STATES.remove(player); FRAMES.remove(player); }
    public static void publishFrame(LivingEntity entity,ItemStack stack,ItemDisplayContext context,Matrix4f modelPose){
        if(!(entity instanceof AbstractClientPlayer player)||HeldItemOutlineRenderer.isCaptureActive()||ShaderPackDetector.isShadowPass())return;
        var active=SlashClient.active(player);
        if(active==null||!ItemStack.isSameItem(stack,active.visual().stack()))return;
        var pair=FRAMES.computeIfAbsent(player,p->new SolvedFrame[2]);
        pair[context.firstPerson()?0:1]=new SolvedFrame(context,active.visual().sequence(),player.tickCount+Minecraft.getInstance().getFrameTime(),new Matrix4f(modelPose));
    }
    public static void begin(LivingEntity entity, ItemStack stack, ItemDisplayContext context) {
        boolean first = context.firstPerson();
        // A sentinel also shields a parent scope from nested item/effect rendering.
        Scope scope = new Scope(null,stack,context,first);
        if (entity instanceof AbstractClientPlayer player && stack.getItem() instanceof ISlashWeapon
                && (context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    == (player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)
                && !HeldItemOutlineRenderer.isCaptureActive() && (first || context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND))
            scope = new Scope(player,stack,context,first);
        SCOPES.push(scope);
    }
    public static void finish() {
        if (SCOPES.isEmpty()) return;
        SCOPES.pop();
    }
    public static void capture(ItemStack stack, ItemDisplayContext context, PoseStack poses) {
        if (SCOPES.isEmpty() || HeldItemOutlineRenderer.isCaptureActive() || ShaderPackDetector.isShadowPass() || !ItemSlashAnimation.enabled()) return;
        Scope scope = SCOPES.peek();
        if (scope.player==null || scope.context!=context || scope.stack!=stack) return;
        var active = SlashClient.active(scope.player); var profile = AnchorProfiles.get(stack);
        if (active==null || profile==null || active.visual().trail().renderer().equals(SlashTrail.NONE)) return;
        var view = scope.first ? profile.firstPerson() : profile.thirdPerson();
        if (!view.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        double now = scope.player.tickCount + mc.getFrameTime();
        double elapsed = now-active.startTick(), progress=elapsed/active.visual().animationTicks();
        State[] pair = STATES.computeIfAbsent(scope.player,p->new State[2]); int index=scope.first?0:1;
        State state=pair[index];
        if(state==null || state.sequence!=active.visual().sequence()) {
            state=new State();state.sequence=active.visual().sequence();state.origin=camera;pair[index]=state;
        }
        state.view=view;state.effect=SlashEffects.get(stack);
        var base=active.visual().trail();var effect=state.effect;
        var renderer=effect.renderer().equals("inherit")?base.renderer():effect.renderer().equals("texture")?SlashTrail.TEXTURE:SlashTrail.SHADER;
        state.material=new SlashTrail(renderer,net.minecraft.resources.ResourceLocation.parse(effect.texture()),effect.tint(base.color()),base.width(),base.radius(),(int)Math.ceil(effect.lifetimeTicks()));
        state.history.prune(now,effect.lifetimeTicks());
        state.cutStart=active.startTick()+active.visual().animationTicks()*view.start();
        state.cutEnd=active.startTick()+active.visual().animationTicks()*view.end();
        if(progress<view.start() || progress>view.end()) { state.oldPose=null;return; }
        if(Double.isFinite(state.lastTime) && now<=state.lastTime+1e-6) return;
        Matrix4f matrix=new Matrix4f(poses.last().pose());
        var frames=FRAMES.get(scope.player);
        var solved=frames==null?null:frames[index];
        if(solved!=null&&solved.context==context&&solved.sequence==state.sequence&&Math.abs(solved.time-now)<1e-6)
            matrix.set(solved.modelPose);
        Vec3 offset=camera.subtract(state.origin);
        if(scope.first) {
            matrix=TrailWorldTransform.fromHand(matrix,com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),worldProjection,
                    worldInverse,new Vector3f((float)offset.x,(float)offset.y,(float)offset.z));
        }else {
            matrix = new Matrix4f().translation((float)offset.x,(float)offset.y,(float)offset.z).mul(worldInverse).mul(matrix);
        }
        int steps=state.oldPose==null || now-state.lastTime>2.5 ? 1 : TrailSampling.steps(state.oldPose,matrix,view.points(),now-state.lastTime,view.subdivisions());
        if(state.oldPose!=null && now-state.lastTime>2.5) {state.history.clear();state.oldPose=null;}
        // A discontinuity must be rejected before interpolation, or subdividing would hide a teleport.
        if(state.oldPose!=null && transformed(view,matrix).stream().anyMatch(p->!Double.isFinite(p.x()+p.y()+p.z()))) {state.history.clear();state.oldPose=null;return;}
        if(state.oldPose!=null) {
            var before=transformed(view,state.oldPose);var after=transformed(view,matrix);
            for(int i=0;i<before.size();i++) if(before.get(i).distance(after.get(i))>view.maxDistance()) {state.history.clear();state.oldPose=null;steps=1;break;}
        }
        for(int i=1;i<=steps;i++) {
            float t=i/(float)steps;
            Matrix4f sample=state.oldPose==null?matrix:TrailTransforms.interpolate(state.oldPose,matrix,t);
            double time=state.oldPose==null?now:state.lastTime+(now-state.lastTime)*t;
            state.history.append(time,transformed(view,sample),view.maxDistance(),view.maxSamples());
        }
        state.oldPose=matrix;state.lastTime=now;
    }
    private static List<TrailHistory.Point> transformed(AnchorProfile.View view,Matrix4f matrix) {
        List<TrailHistory.Point> points=new ArrayList<>();
        for(Vector3f anchor:view.points()) {Vector3f p=matrix.transformPosition(new Vector3f(anchor).div(16));points.add(new TrailHistory.Point(p.x,p.y,p.z));}
        return points;
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if(ShaderPackDetector.isShadowPass())return;
        Minecraft mc=Minecraft.getInstance();
        if(level!=mc.level) {clear();level=mc.level;}
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY) {
            worldInverse=new Matrix4f(event.getPoseStack().last().pose()).invert();camera=event.getCamera().getPosition();
            worldProjection=new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        }
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || !ItemSlashAnimation.enabled())return;
        PENDING.clear();
        for(var entry:List.copyOf(STATES.entrySet())) {
            var player=entry.getKey();State[] pair=entry.getValue();
            if(player.level()!=mc.level || SlashClient.active(player)==null){STATES.remove(player);continue;}
            boolean first=player==mc.player&&mc.options.getCameraType().isFirstPerson();
            pair[first?1:0]=null;State state=pair[first?0:1];if(state==null)continue;
            PoseStack poses=event.getPoseStack();poses.pushPose();
            Vec3 offset=state.origin.subtract(event.getCamera().getPosition());poses.translate(offset.x,offset.y,offset.z);
            prepare(state,player.tickCount+event.getPartialTick(),poses);poses.popPose();
        }
    }
    private static void prepare(State state,double now,PoseStack poses) {
        if(state.view==null)return;
        state.history.prune(now,state.effect.lifetimeTicks());var samples=state.history.samples();if(samples.size()<2)return;
        var material=state.material;
        if(SlashTrailRenderers.hasRenderer(material.renderer()))return;
        if(!material.renderer().equals(SlashTrail.TEXTURE) && !material.renderer().equals(SlashTrail.SHADER))return;
        if(material.renderer().equals(SlashTrail.SHADER) && SlashTrailShaders.shader==null)return;
        var sections=TrailRibbonGeometry.sections(samples,state.effect.width(),state.effect.curvature(),state.effect.stability());
        Matrix4f matrix=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
        PENDING.add(new DrawEntry(samples,sections,material,state.effect,state.sequence,state.cutStart,state.cutEnd,now,matrix,new Matrix4f(worldProjection)));
    }
    /** Both passes consume the same immutable frame. Copy clean world color BEFORE drawing light. */
    public static void renderDeferred() {
        if(PENDING.isEmpty())return;
        if(Minecraft.getInstance().level==null){PENDING.clear();SlashAirRefraction.clear();return;}
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        LateOutlineRenderState.prepareMainTargetPass();
        try {
            for(var entry:PENDING)SlashAirRefraction.enqueue(entry.sections,entry.samples,entry.pose,entry.projection,entry.effect,entry.now,
                    entry.cutStart,entry.cutEnd,(entry.material.color()>>>24)/255F*entry.effect.opacity());
            SlashAirRefraction.renderAll();
            for(var entry:PENDING){RenderSystem.setProjectionMatrix(entry.projection,VertexSorting.DISTANCE_TO_ORIGIN);draw(entry);}
        } finally {
            PENDING.clear();model.popPose();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);LateOutlineRenderState.finishMainTargetPass();
        }
    }
    private static void draw(DrawEntry state) {
        var samples=state.samples;double now=state.now;var material=state.material;
        PoseStack poses=new PoseStack();poses.last().pose().set(state.pose);
        if(material.renderer().equals(SlashTrail.SHADER)){
            var shader=SlashTrailShaders.shader;var e=state.effect;
            shader.safeGetUniform("TrailTime").set((float)((now%24000)/20));
            shader.safeGetUniform("TrailShape").set(e.coreWidth(),e.fill(),e.glow(),e.noise());
            shader.safeGetUniform("TrailStyle").set(e.brightness(),e.flowSpeed(),e.tailPower(),0);
        }
        VertexConsumer vertices=BUFFERS.getBuffer(material.renderer().equals(SlashTrail.SHADER)?SlashTrailRenderType.shader():SlashTrailRenderType.texture(material.texture()));
        int color=material.color();float r=((color>>16)&255)/255F,g=((color>>8)&255)/255F,b=(color&255)/255F,opacity=(color>>>24)/255F*state.effect.opacity();
        if(material.renderer().equals(SlashTrail.TEXTURE)){r=Math.min(1,r*state.effect.brightness());g=Math.min(1,g*state.effect.brightness());b=Math.min(1,b*state.effect.brightness());}
        double oldest=state.cutStart,span=Math.max(.001,state.cutEnd-oldest);
        // Compute each shared section once so adjacent cells have no cracks or twisted normals.
        var sections=state.sections;
        double first=samples.get(0).time(),last=samples.get(samples.size()-1).time();
        for(int i=0;i<samples.size()-1;i++) {
            var a=samples.get(i);var z=samples.get(i+1);
            float u0=(float)((a.time()-oldest)/span),u1=(float)((z.time()-oldest)/span);
            float alpha0=opacity*TrailRibbonGeometry.fade(now-a.time(),state.effect.lifetimeTicks())*TrailSampling.edgeFade(a.time(),first,last);
            float alpha1=opacity*TrailRibbonGeometry.fade(now-z.time(),state.effect.lifetimeTicks())*TrailSampling.edgeFade(z.time(),first,last);
            var pa=sections.get(i);var pb=sections.get(i+1);
            for(int j=0;j<pa.size()-1;j++) {
                float v0=j/(float)(pa.size()-1),v1=(j+1)/(float)(pa.size()-1);
                vertex(vertices,poses,pa.get(j),r,g,b,alpha0,u0,v0);
                vertex(vertices,poses,pa.get(j+1),r,g,b,alpha0,u0,v1);
                vertex(vertices,poses,pb.get(j+1),r,g,b,alpha1,u1,v1);
                vertex(vertices,poses,pb.get(j),r,g,b,alpha1,u1,v0);
            }
        }
        sparks(state,samples,now,poses,r,g,b,opacity);
        BUFFERS.endBatch();
    }
    private static void sparks(DrawEntry state,List<TrailHistory.Sample> samples,double now,PoseStack poses,float r,float g,float b,float opacity) {
        var spark=BUFFERS.getBuffer(SlashTrailRenderType.texture(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("rotten_ruins","textures/effect/slash_spark.png")));
        long lastBucket=Long.MIN_VALUE;int count=0;
        for(int i=samples.size()-1;i>0&&count<(int)(28*state.effect.particles());i--) {
            var sample=samples.get(i);long bucket=(long)Math.floor(sample.time()*4);if(bucket==lastBucket)continue;lastBucket=bucket;
            if(state.effect.particles()==0)return;
            long hash=(bucket*73428767L+state.sequence*912931L)&0x7fffffff; if(hash%3==0)continue;count++;
            double age=now-sample.time(),life=Math.min(5,state.effect.lifetimeTicks());if(age>life)continue;
            var before=samples.get(i-1);float along=.5F+(hash%47)/100F;
            var root=sample.points().get(0);var tip=sample.points().get(sample.points().size()-1);
            var old=before.points().get(before.points().size()-1);
            Vector3f velocity=new Vector3f((float)(tip.x()-old.x()),(float)(tip.y()-old.y()),(float)(tip.z()-old.z()));
            if(velocity.lengthSquared()>1e-8)velocity.normalize().mul(.025F);
            Vector3f center=new Vector3f((float)(root.x()+(tip.x()-root.x())*along),(float)(root.y()+(tip.y()-root.y())*along),(float)(root.z()+(tip.z()-root.z())*along)).fma((float)age,velocity);
            center.y+=(float)(age*.006);float size=(.012F+(hash%7)*.002F)*(1-(float)(age/life)*.6F);
            Vector3f right=new Vector3f(size,0,0),up=new Vector3f(0,size,0);
            var rotation=Minecraft.getInstance().gameRenderer.getMainCamera().rotation();right.rotate(rotation);up.rotate(rotation);
            float alpha=opacity*.85F*TrailRibbonGeometry.fade(age,life);
            for(int k=0;k<4;k++) {
                float x=k==0||k==3?-1:1,y=k<2?-1:1;
                Vector3f p=new Vector3f(center).fma(x,right).fma(y,up);
                vertex(spark,poses,new TrailHistory.Point(p.x,p.y,p.z),r,g,b,alpha,x<0?0:1,y<0?0:1);
            }
        }
    }
    private static void vertex(VertexConsumer v,PoseStack poses,TrailHistory.Point p,float r,float g,float b,float a,float u,float w) {
        v.vertex(poses.last().pose(),(float)p.x(),(float)p.y(),(float)p.z()).color(r,g,b,a).uv(u,w).endVertex();
    }
}
