package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.MeteorEventEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 陨石事件渲染：天空十字星 / 坠落拖尾 / 火球。
 * 实体渲染阶段是 no-op，真正的绘制由 {@code MeteorEventRenderHandler} 在
 * {@code AFTER_TRIPWIRE_BLOCKS} 统一做（此时地形写完深度，高空物才会被山挡住）。
 * billboard 用视基 basis[0..2]=右、basis[3..5]=上 在世界空间铺正方形，顶点喂世界坐标。
 */
public class MeteorEventRenderer extends EntityRenderer<MeteorEventEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 拖尾圆柱沿高度的分环数。 */
    private static final int TRAIL_RINGS = 28;
    /** 拖尾圆柱的分段数。 */
    private static final int TRAIL_SEGMENTS = 40;
    /** 尾端半径相对头部的比例：0.10 让尾迹收成尖的，剪影是条火舌而非烟囱。 */
    private static final float TRAIL_TIP_SCALE = 0.10f;

    // 火色盘：白热 → 橙 → 暗红
    private static final float[] HOT   = { 1.00f, 0.95f, 0.85f };
    private static final float[] ORANGE = { 1.00f, 0.42f, 0.10f };
    private static final float[] RED    = { 0.65f, 0.05f, 0.02f };
    private static final float[] DEEP   = { 0.30f, 0.02f, 0.01f };

    public MeteorEventRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(MeteorEventEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // no-op：见类注释
    }

    /** 画这一帧陨石事件：view 已含 translate(-camera)，basis 为视基右/上/前（世界方向）。 */
    static void draw(MeteorEventEntity e, float pt, Matrix4f view, float[] basis, Vec3 cam) {
        ShaderInstance sh = SplendidingShaders.meteorEventShader;
        if (sh == null) return;

        float age = e.getAge() + pt;
        if (age < 0f) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> sh);

        CrimsonVowRenderSupport.setUniform(sh, "uTime", age * 0.05f + (e.getId() % 13) * 0.31f);

        float x = (float) e.getX();
        float z = (float) e.getZ();
        float ground = (float) e.getY();

        if (age < MeteorEventEntity.SIGN_TICKS) {
            setPalette(sh, 0f);
            float p = e.signProgress(pt);
            float grow = p * p * (3f - 2f * p);          // smoothstep：先慢后快
            float size = Mth.lerp(2.5f, MeteorEventEntity.SIGN_MAX_SIZE, grow);
            // 头 10 tick 淡入，最后 10 tick 开始烧起来
            float fadeIn = Mth.clamp(age / 10f, 0f, 1f);
            float ramp = 0.65f + 0.35f * grow;
            setMode(sh, 0f, p, 0f, 0f);
            setIntensity(sh, fadeIn * ramp * 1.35f);
            // 十字星出现在斜上方（见 MeteorEventEntity#signPos），给陨石留出大倾角入场
            var sp = e.signPos();
            billboard(view, basis, (float) sp.x, (float) sp.y, (float) sp.z,
                    size, 0f);
        } else if (age < MeteorEventEntity.SIGN_TICKS + MeteorEventEntity.FALL_TICKS) {
            float f = e.fallProgress(pt);
            // 头部沿"起点 → 落点"的斜线移动；尾迹沿同一条线往起点退
            var hp = e.headPos(pt);
            var te = e.trailEnd(pt);
            float radius = Mth.lerp(f, 0.7f, MeteorEventEntity.TRAIL_MAX_RADIUS);

            setPalette(sh, f);

            // 轴向参数版拖尾：uHeadY=0 / uSpan=1，顶点里塞的是 0..1 的 t
            setMode(sh, 1f, f, 0f, 1f);
            setIntensity(sh, 0.55f + 0.75f * f);
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            CrimsonVowRenderSupport.emitTube(buffer, view,
                    (float) hp.x, (float) hp.y, (float) hp.z,
                    (float) te.x, (float) te.y, (float) te.z,
                    radius, radius * TRAIL_TIP_SCALE, TRAIL_RINGS);
            BufferUploader.drawWithShader(buffer.end());

            // 热浪折射：只扰动尾迹附近那几块四边形，四边形外一个像素都不碰
            if (f > 0.06f) {
                drawHeat(view, basis, hp, te, radius, f, age);
            }

            // 十字本体与天上同形状（uMode 0），快砸地时几乎铺满视野
            float size = MeteorEventEntity.SIGN_MAX_SIZE * (0.85f + 0.95f * f);
            setMode(sh, 0f, f, 0f, 0f);
            setIntensity(sh, 1.25f + 0.85f * f);
            billboard(view, basis, (float) hp.x, (float) hp.y, (float) hp.z, size, 0f);
        } else {
            float a = e.afterTicks(pt);
            float t = Mth.clamp(a / (float) MeteorEventEntity.AFTER_TICKS, 0f, 1f);

            // 白光：前 6 tick 的冲击帧；没有它世界本身毫无反应，全靠屏幕遮罩会显得假
            if (a < 6f) {
                float k = a / 6f;
                setPalette(sh, 1f);
                setMode(sh, 2f, 1f, 0f, 0f);
                setIntensity(sh, (1f - k) * 5.0f);
                billboard(view, basis, x, ground + 8f, z,
                        60f + 80f * k, 0f);
            }

            // 十字在落点继续亮 14 tick 并炸开 —— "砸下来的还是那颗十字"
            if (a < 14f) {
                float k = a / 14f;
                setPalette(sh, 1f);
                setMode(sh, 0f, 1f, 0f, 0f);
                setIntensity(sh, (1f - k) * 2.6f);
                billboard(view, basis, x, ground + 6f, z,
                        Mth.lerp(22f, 60f, k), 0f);
            }

            // 体积感靠云团堆：柱 5 圈、冠 3 层。
            // 顺序：先扬尘（alpha，暗）→ 再火（加法）→ 最后火球，火才能照亮尘。
            float rise = Mth.clamp((a - 6f) / 90f, 0f, 1f);
            float fade = Mth.clamp(1f - (a - 250f) / 110f, 0f, 1f);
            // 火先熄、尘最后散：dustFade 比 fade 晚 50 tick 才开始收
            float dustFade = Mth.clamp(1f - (a - 200f) / 260f, 0f, 1f);
            int seed = e.getSeed();
            float stemH = Mth.lerp(rise, 6f, 92f);
            float stemR = Mth.lerp(rise, 5f, 16f);
            float capR = Mth.lerp(rise, 7f, 56f);
            float capY = ground + stemH + 10f * rise * rise;

            // 扬尘：alpha 混合，先画
            if (dustFade > 0.01f) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                setOpacity(sh, 0.65f);
                setDustPalette(sh);
                setMode(sh, 3f, 1f, 0f, 0f);
                float spread = 14f + 72f * Mth.clamp((a - 6f) / 170f, 0f, 1f);
                for (int i = 0; i < 24; i++) {
                    double ang = i * Math.PI * 2.0 / 24.0 + 0.2;
                    float rr = spread * (0.68f + 0.38f * ((i * 31 + seed) % 5) / 5f);
                    float px = x + (float) (Math.cos(ang) * rr);
                    float pz = z + (float) (Math.sin(ang) * rr);
                    float py = ground + 4f + ((i * 17 + seed) % 4) * 3.0f;
                    setIntensity(sh, (1.35f - 0.55f * ((a - 6f) / 440f)) * dustFade);
                    billboard(view, basis, px, py, pz,
                            26f + 44f * Mth.clamp((a - 6f) / 170f, 0f, 1f), 0f,
                            ((i * 53 + seed * 9) % 89) / 89f);
                }
                for (int i = 0; i < 12; i++) {
                    double ang = i * Math.PI * 2.0 / 12.0 + 0.9;
                    float rr = stemR * (2.0f + 0.8f * ((i * 7 + seed) % 5) / 5f);
                    float px = x + (float) (Math.cos(ang) * rr);
                    float pz = z + (float) (Math.sin(ang) * rr);
                    setIntensity(sh, 1.15f * dustFade);
                    billboard(view, basis, px, ground + 8f, pz, 34f, 0f,
                            ((i * 67 + seed * 3) % 83) / 83f);
                }
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE);
                setOpacity(sh, 1.0f);
            }

            // 火：柱 + 冠，全部云团堆
            if (fade > 0.01f) {
                setPalette(sh, 0.90f);
                setOpacity(sh, 1.0f);
                setMode(sh, 3f, 1f, 0f, 0f);

                int stemRings = 5;
                for (int k = 0; k < stemRings; k++) {
                    float hf = k / (float) (stemRings - 1);
                    float ry = ground + 6f + stemH * hf;
                    float rr = stemR * (1.30f - 0.50f * hf);
                    int count = 7;
                    for (int i = 0; i < count; i++) {
                        double ang = i * Math.PI * 2.0 / count + k * 0.55
                                + (seed % 100) * 0.01;
                        float jr = 0.82f + 0.36f * ((i * 11 + seed + k * 7) % 5) / 5f;
                        float px = x + (float) (Math.cos(ang) * rr * jr);
                        float pz = z + (float) (Math.sin(ang) * rr * jr);
                        float py = ry + ((i * 23 + seed) % 4) * 2.2f;
                        setIntensity(sh, (1.05f - 0.28f * hf
                                + 0.20f * ((i % 3) / 3f)) * fade);
                        billboard(view, basis, px, py, pz, stemR * 2.3f, 0f,
                                ((i * 41 + seed + k * 17) % 97) / 97f);
                    }
                }
                // 柱芯：一根细圆柱，给柱中心一点连续的热
                setMode(sh, 1f, 1f, ground + 1f, stemH);
                setIntensity(sh, 0.75f * fade);
                cylinder(view, x, ground + 1f, z, stemR * 0.55f, stemR * 0.30f,
                        ground + 1f + stemH);

                setMode(sh, 3f, 1f, 0f, 0f);
                float[] rf = { 0.80f, 0.48f, 0.20f };
                float[] yf = { 4f, 16f, 30f };
                float[] sf = { 0.55f, 0.46f, 0.36f };
                int[] cn = { 16, 10, 6 };
                for (int layer = 0; layer < 3; layer++) {
                    for (int i = 0; i < cn[layer]; i++) {
                        double ang = i * Math.PI * 2.0 / cn[layer] + layer * 0.7
                                + (seed % 100) * 0.01;
                        float rr = capR * rf[layer]
                                * (0.90f + 0.12f * ((i * 13 + seed) % 7) / 7f);
                        float px = x + (float) (Math.cos(ang) * rr);
                        float pz = z + (float) (Math.sin(ang) * rr);
                        float py = capY + yf[layer] + ((i * 29 + seed) % 5) * 1.6f;
                        setIntensity(sh, (1.15f - 0.18f * layer
                                + 0.25f * ((i % 4) / 4f)) * fade);
                        billboard(view, basis, px, py, pz, capR * sf[layer], 0f,
                                ((i * 37 + seed + layer * 11) % 97) / 97f);
                    }
                }
                for (int i = 0; i < 12; i++) {
                    double ang = i * Math.PI * 2.0 / 12.0 + 0.35;
                    float rr = capR * 0.92f;
                    float px = x + (float) (Math.cos(ang) * rr);
                    float pz = z + (float) (Math.sin(ang) * rr);
                    float py = capY - 14f + ((i * 19 + seed) % 6) * 1.4f;
                    setIntensity(sh, 0.85f * fade);
                    billboard(view, basis, px, py, pz, capR * 0.42f, 0f,
                            ((i * 43 + seed * 5) % 79) / 79f);
                }
            }
            // 扩散火球：70 tick 撑到最大后慢慢淡出
            float fb = Mth.clamp(a / 70f, 0f, 1f);
            float grow = 1f - (1f - fb) * (1f - fb);
            float size = Mth.lerp(10f, MeteorEventEntity.BLAST_MAX_RADIUS, grow);
            float intensity = Mth.clamp(1f - (a - 70f) / 150f, 0f, 1f);
            intensity *= intensity;
            // ⚠️ 不能在这里 return：下面的混合/深度状态还没还原，否则后续特效全被污染
            if (intensity > 0.004f) {
                setMode(sh, 2f, 1f, 0f, 0f);
                setIntensity(sh, intensity * 1.6f);
                billboard(view, basis, x, ground + 4f, z, size, 0f);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void setIntensity(ShaderInstance sh, float v) {
        CrimsonVowRenderSupport.setUniform(sh, "uIntensity", v);
    }

    /**
     * 沿尾迹画一串离屏采样层（热浪折射）：片元反查屏幕位置采样场景拷贝，圆外 discard。
     * 场景拷贝必须在本趟之前完成，否则折射到的是没有尾迹的旧画面。
     */
    private static void drawHeat(Matrix4f view, float[] basis,
                                 net.minecraft.world.phys.Vec3 hp,
                                 net.minecraft.world.phys.Vec3 te,
                                 float radius, float f, float age) {
        ShaderInstance sh = SplendidingShaders.meteorHeatShader;
        if (sh == null) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        var rt = mc.getMainRenderTarget();
        if (rt == null) return;

        ShockwaveRenderer.copySceneForOverlay();
        int tex = ShockwaveRenderer.sceneTextureForOverlay();
        if (tex <= 0) return;

        RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(tex);
        // ⚠️ setSampler 第二个参数是纹理 id，不是纹理单元号；传 0 会采样到方块图集
        sh.setSampler("SceneTexture", tex);
        RenderSystem.setShader(() -> sh);

        // alpha 混合：这块四边形只会"替换"它覆盖的像素
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        CrimsonVowRenderSupport.setUniform2(sh, "uScreenSize",
                Math.max(1, rt.width), Math.max(1, rt.height));
        CrimsonVowRenderSupport.setUniform(sh, "uTime", age * 0.05f);
        CrimsonVowRenderSupport.setUniform(sh, "uStrength",
                Mth.clamp(f * 1.3f, 0f, 1f) * 0.9f);

        // 沿尾迹 7 块，越靠头越强（那边最热）
        for (int i = 0; i < 7; i++) {
            float t = i / 6f;
            float px = (float) Mth.lerp(t, hp.x, te.x);
            float py = (float) Mth.lerp(t, hp.y, te.y);
            float pz = (float) Mth.lerp(t, hp.z, te.z);
            billboard(view, basis, px, py, pz, radius * 4.5f + 4f, 0f, 0f);
        }

        // 还原加法混合与陨石着色器：BufferUploader 用的是 RenderSystem 当前 shader
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(() -> SplendidingShaders.meteorEventShader);
    }

    /** 输出不透明度：火焰趟是 1（加法，无影响），扬尘趟用它压成半透明。 */
    private static void setOpacity(ShaderInstance sh, float v) {
        CrimsonVowRenderSupport.setUniform(sh, "uOpacity", v);
    }

    /** 扬尘的颜色：灰褐偏暖，配合 alpha 混合把天空压暗。 */
    private static void setDustPalette(ShaderInstance sh) {
        CrimsonVowRenderSupport.setUniform3(sh, "uColorA", 0.55f, 0.47f, 0.40f);
        CrimsonVowRenderSupport.setUniform3(sh, "uColorB", 0.36f, 0.31f, 0.27f);
        CrimsonVowRenderSupport.setUniform3(sh, "uColorC", 0.20f, 0.17f, 0.16f);
    }

    /** 画一节竖直圆柱（拖尾模式）。 */
    private static void cylinder(Matrix4f view, float x, float y0, float z,
                                 float r0, float r1, float y1) {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitCylinder(b, view, x, y0, z, r0, r1, y1, 28);
        BufferUploader.drawWithShader(b.end());
    }

    /** 火色盘：fall=0 白热 → 0.5 橙 → 1 暗红，三段插值才有渐变。 */
    private static void setPalette(ShaderInstance sh, float fall) {
        float f = Mth.clamp(fall, 0f, 1f);
        float[] a = mix3(HOT, ORANGE, Mth.clamp(f * 1.7f, 0f, 1f));
        float[] b = mix3(ORANGE, RED, f);
        float[] c = mix3(RED, DEEP, f);
        CrimsonVowRenderSupport.setUniform3(sh, "uColorA", a[0], a[1], a[2]);
        CrimsonVowRenderSupport.setUniform3(sh, "uColorB", b[0], b[1], b[2]);
        CrimsonVowRenderSupport.setUniform3(sh, "uColorC", c[0], c[1], c[2]);
    }

    private static float[] mix3(float[] x, float[] y, float t) {
        return new float[] {
                x[0] + (y[0] - x[0]) * t,
                x[1] + (y[1] - x[1]) * t,
                x[2] + (y[2] - x[2]) * t,
        };
    }

    /** 一次设齐模式相关的 uniform（避免上一趟的残值）。 */
    private static void setMode(ShaderInstance sh, float mode, float progress,
                                float headY, float span) {
        CrimsonVowRenderSupport.setUniform(sh, "uMode", mode);
        CrimsonVowRenderSupport.setUniform(sh, "uProgress", progress);
        CrimsonVowRenderSupport.setUniform(sh, "uHeadY", headY);
        CrimsonVowRenderSupport.setUniform(sh, "uSpan", Math.max(span, 0.01f));
    }

    /**
     * 发一个正对镜头的正方形：顶点 Color.rg 当局部 uv，Normal.x 无意义填 0。
     * ⚠️ rotation 是弧度且必须是小角度（星与拖尾都传 0），要转请用 Mth.DEG_TO_RAD * 角度。
     */
    private static void billboard(Matrix4f view, float[] basis,
                                  float cx, float cy, float cz, float half,
                                  float rotation) {
        billboard(view, basis, cx, cy, cz, half, rotation, 0f);
    }

    /** 同上，seed 写进顶点 Color.b：让各云团边缘扰动相位错开，否则是一圈同样的圆球。 */
    private static void billboard(Matrix4f view, float[] basis,
                                  float cx, float cy, float cz, float half,
                                  float rotation, float seed) {
        float rx = basis[0], ry = basis[1], rz = basis[2];   // 右
        float ux = basis[3], uy = basis[4], uz = basis[5];   // 上

        float c = Mth.cos(rotation);
        float s = Mth.sin(rotation);
        float arx = rx * c + ux * s, ary = ry * c + uy * s, arz = rz * c + uz * s;
        float aux = -rx * s + ux * c, auy = -ry * s + uy * c, auz = -rz * s + uz * c;

        float x0 = cx - arx * half - aux * half, y0 = cy - ary * half - auy * half, z0 = cz - arz * half - auz * half;
        float x1 = cx + arx * half - aux * half, y1 = cy + ary * half - auy * half, z1 = cz + arz * half - auz * half;
        float x2 = cx + arx * half + aux * half, y2 = cy + ary * half + auy * half, z2 = cz + arz * half + auz * half;
        float x3 = cx - arx * half + aux * half, y3 = cy - ary * half + auy * half, z3 = cz - arz * half + auz * half;

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        quadVertex(buffer, view, x0, y0, z0, 0f, 0f, seed);
        quadVertex(buffer, view, x1, y1, z1, 1f, 0f, seed);
        quadVertex(buffer, view, x2, y2, z2, 1f, 1f, seed);
        quadVertex(buffer, view, x3, y3, z3, 0f, 1f, seed);
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void quadVertex(BufferBuilder b, Matrix4f view,
                                   float x, float y, float z, float u, float v,
                                   float seed) {
        b.vertex(view, x, y, z)
                .color(u, v, seed, 1f)
                .normal(0f, 0f, 0f)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEventEntity entity) {
        return DUMMY_TEXTURE;
    }
}
