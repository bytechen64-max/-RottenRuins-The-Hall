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
import org.bytechen.rottenruins.overworld.registry.entities.population.skills.MeteorEventEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 陨石事件渲染器 —— 天空十字星 / 坠落拖尾 / 火球。
 *
 * <h3>为什么实体渲染阶段什么都不做</h3>
 * <p>十字星在 150 格高空、拖尾有几十格长，都远远超出实体自己的包围盒。
 * 和法阵一样，真正的绘制由 {@code MeteorEventRenderHandler} 在
 * {@code AFTER_TRIPWIRE_BLOCKS} 统一做 —— 那时地形已经写完深度，
 * 高空的东西才会被山挡住。</p>
 *
 * <h3>朝向相机的四边形（billboard）</h3>
 * <p>用视基的两个向量在<b>世界空间</b>里铺开一个正方形：视基的
 * {@code basis[0..2]} 是"视空间 +X 在世界里的方向"（右），
 * {@code basis[3..5]} 是上。这样四边形永远正对镜头，而顶点仍然喂世界坐标，
 * 与其它特效共用同一套 {@code view} 矩阵 —— 不需要在着色器里做 billboard。</p>
 */
public class MeteorEventRenderer extends EntityRenderer<MeteorEventEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 拖尾圆柱沿高度的分环数。 */
    private static final int TRAIL_RINGS = 28;
    /** 拖尾圆柱的分段数。 */
    private static final int TRAIL_SEGMENTS = 40;
    /**
     * 尾端半径相对头部的比例 —— <b>尾迹收成尖的</b>。
     *
     * <p>第一版是 1.9（尾端张开），读起来像一团倒挂的烟囱；实机反馈
     * "不应该是尾巴尖的吗"。0.10 让尾端几乎收成一个点，配合着色器里
     * 随 rel 的衰减，剪影就是一条从头部拖出去、尖端消散的火舌。</p>
     */
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
        // no-op（见类注释）
    }

    // ══════════════════════════════════════════════════════════════
    //  绘制入口
    // ══════════════════════════════════════════════════════════════

    /**
     * 画这一帧的陨石事件。
     *
     * @param view  世界 → 视空间（已含 {@code translate(-camera)}）
     * @param basis 视基三向量（右 / 上 / 前，世界方向）
     */
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

        // 共用 uniform
        CrimsonVowRenderSupport.setUniform(sh, "uTime", age * 0.05f + (e.getId() % 13) * 0.31f);

        float x = (float) e.getX();
        float z = (float) e.getZ();
        float ground = (float) e.getY();

        if (age < MeteorEventEntity.SIGN_TICKS) {
            // ── ① 天空十字星：越来越大、越来越亮 ──
            setPalette(sh, 0f);
            float p = e.signProgress(pt);
            float grow = p * p * (3f - 2f * p);          // smoothstep：先慢后快
            float size = Mth.lerp(2.5f, MeteorEventEntity.SIGN_MAX_SIZE, grow);
            // 头 10 tick 淡入，最后 10 tick 开始烧起来
            float fadeIn = Mth.clamp(age / 10f, 0f, 1f);
            float ramp = 0.65f + 0.35f * grow;
            setMode(sh, 0f, p, 0f, 0f);
            setIntensity(sh, fadeIn * ramp * 1.35f);
            // 十字星出现在**斜上方**（见 MeteorEventEntity#signPos），
            // 不是落点正上方 —— 这样陨石才有一个大倾角的进入。
            var sp = e.signPos();
            billboard(view, basis, (float) sp.x, (float) sp.y, (float) sp.z,
                    size, 0f);
        } else if (age < MeteorEventEntity.SIGN_TICKS + MeteorEventEntity.FALL_TICKS) {
            // ── ② 坠落：**还是那颗十字**，只是越来越大、越来越红 ──
            float f = e.fallProgress(pt);
            // 头部沿"起点 → 落点"的**斜线**移动；尾迹沿同一条线往起点退
            var hp = e.headPos(pt);
            var te = e.trailEnd(pt);
            float radius = Mth.lerp(f, 0.7f, MeteorEventEntity.TRAIL_MAX_RADIUS);

            // 颜色随下落从白热烧到暗红
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

            // ── 热浪折射：沿尾迹的一串"离屏采样层" ──
            //  第一版是整屏后处理，没落地就把整个画面替换掉了（满屏噪点）。
            //  现在只有这几块四边形在动，四边形外一个像素都不碰。
            if (f > 0.06f) {
                drawHeat(view, basis, hp, te, radius, f, age);
            }

            // 十字本体：与天上是**同一个形状**（uMode 0），
            // 尺寸一路涨到 1.8 倍，快砸到地面时几乎铺满视野。
            float size = MeteorEventEntity.SIGN_MAX_SIZE * (0.85f + 0.95f * f);
            setMode(sh, 0f, f, 0f, 0f);
            setIntensity(sh, 1.25f + 0.85f * f);
            billboard(view, basis, (float) hp.x, (float) hp.y, (float) hp.z, size, 0f);
        } else {
            // ── ③ 砸中：一记超大白光 + 十字爆闪 + 扩散火球 ──
            float a = e.afterTicks(pt);
            float t = Mth.clamp(a / (float) MeteorEventEntity.AFTER_TICKS, 0f, 1f);

            // 白光：前 6 tick 在落点放一个 140 格的加法白球，强度 5 ——
            //  这是"冲击"里最重要的一帧：整块天空被烧白，屏幕特效再叠上去。
            //  没有它，世界本身一点反应都没有，光靠全屏遮罩会觉得"假"。
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

            // ── 蘑菇云 ──
            //  体积感只有一条路：**用云团堆**。上一版只有冠是堆的，柱是
            //  一根圆柱、尘是单张 billboard，所以实机反馈"只有顶部有体积，
            //  其他部分太平滑"。现在柱也是 5 圈云团堆出来的，圆柱只留一根
            //  很细的芯做热源。
            //
            //  顺序：先扬尘（alpha，暗）→ 再火（加法）→ 最后火球。
            //  尘画在火前面，火才会把尘照亮；反过来尘会盖住火。
            float rise = Mth.clamp((a - 6f) / 90f, 0f, 1f);
            float fade = Mth.clamp(1f - (a - 250f) / 110f, 0f, 1f);
            // 火先熄、尘最后散：dustFade 比 fade 晚 250 tick 才开始收，
            //  所以火没了之后，天上还挂着一层灰。
            float dustFade = Mth.clamp(1f - (a - 200f) / 260f, 0f, 1f);
            int seed = e.getSeed();
            float stemH = Mth.lerp(rise, 6f, 92f);
            float stemR = Mth.lerp(rise, 5f, 16f);
            float capR = Mth.lerp(rise, 7f, 56f);
            float capY = ground + stemH + 10f * rise * rise;

            // ── 扬尘（alpha 混合，先画）──
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
                // 柱脚翻滚的浓尘
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

            // ── 火：柱 + 冠（全部云团堆）──
            if (fade > 0.01f) {
                setPalette(sh, 0.90f);
                setOpacity(sh, 1.0f);
                setMode(sh, 3f, 1f, 0f, 0f);

                // 柱：5 圈云团，下宽上窄
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

                // 冠：三层
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
                // 悬垂：冠底一圈向下的云
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
            // 扩散火球：前 70 tick 撑到最大，之后慢慢淡（不再跟着 AFTER 走）
            float fb = Mth.clamp(a / 70f, 0f, 1f);
            float grow = 1f - (1f - fb) * (1f - fb);
            float size = Mth.lerp(10f, MeteorEventEntity.BLAST_MAX_RADIUS, grow);
            float intensity = Mth.clamp(1f - (a - 70f) / 150f, 0f, 1f);
            intensity *= intensity;
            // ⚠️ 这里不能 return：上面的混合 / 深度状态还没还原。
            //    早期版本在这里直接 return，结果余波期之后的每一帧都把混合留在
            //    "加法 + 不写深度"的状态上 —— 别的特效会跟着变亮。
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
     * 沿尾迹画一串**离屏采样层**（热浪折射）。
     *
     * <p>每一块是一个正对镜头的四边形，片元里用 {@code gl_FragCoord/uScreenSize}
     * 反查自己覆盖的屏幕位置、去采样场景拷贝，再按局部圆的 mask 做偏移与色散；
     * 圆外 {@code discard}，所以只有陨石周围那一圈会被扰动。</p>
     *
     * <p>场景拷贝必须在这一趟之前拷好（此时尾迹已经画进主帧缓冲），
     * 否则折射的是"没有尾迹"的旧画面。</p>
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
        // ⚠️ setSampler 的第二个参数是**纹理 id**，不是纹理单元号。
        //    传 0 会让采样器指向 unit 0（方块图集），于是热浪层把图集贴到屏幕上。
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

        // 还原加法混合（后面的十字本体还要用）
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        // ⚠️ 还必须把着色器切回陨石那支：BufferUploader 用的是
        //    RenderSystem 当前 shader，忘了这一步十字本体就会用热浪着色器画。
        RenderSystem.setShader(() -> SplendidingShaders.meteorEventShader);
    }

    /** 输出不透明度：火焰趟是 1（加法，无影响），扬尘趟用它压成半透明。 */
    private static void setOpacity(ShaderInstance sh, float v) {
        CrimsonVowRenderSupport.setUniform(sh, "uOpacity", v);
    }

    /** 扬尘的颜色：灰褐。配合 alpha 混合，它能把天空**压暗**。 */
    private static void setDustPalette(ShaderInstance sh) {
        //  比第一版亮一档：夜里太暗的尘在 alpha 混合下几乎看不见，
        //  实机反馈"扬尘雾不清楚"。偏暖是因为它被火光照着。
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

    /**
     * 火色盘：随坠落推进从白热烧到暗红。
     *
     * <p>{@code fall = 0} 还是天上的白色十字；{@code 0.5} 橙；{@code 1} 暗红。
     * 三段插值而不是直接换色，才能在 10 秒里读出"越来越红"。</p>
     */
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
     * 发一个正对镜头的正方形。
     *
     * <p>四个顶点的 {@code Color.rg} 就是局部 uv（0/1），片元用它做对称图案；
     * {@code Normal.x} 在非拖尾模式下没有意义，填 0。</p>
     *
     * <p>⚠️ {@code rotation} 是<b>弧度</b>，而且必须是小角度（星与拖尾都传 0）。
     * 第一版传的是 {@code e.getSeed() * 0.12f} —— 种子是 0~100 万的随机整数，
     * 乘出来是几万弧度：{@code cos/sin} 精度早就丢了，十字被转到一个任意角度，
     * 实机看起来就是"陨石斜着射下来"外加一根被切平的光柱。
     * 要转动请用 {@code Mth.DEG_TO_RAD * 角度}。</p>
     */
    private static void billboard(Matrix4f view, float[] basis,
                                  float cx, float cy, float cz, float half,
                                  float rotation) {
        billboard(view, basis, cx, cy, cz, half, rotation, 0f);
    }

    /**
     * 同上，但把 {@code seed} 写进顶点的 {@code Color.b}。
     *
     * <p>蘑菇云的冠是十几个云团拼的，每个云的边缘扰动相位要靠它错开，
     * 否则十几团会变成一圈一模一样的圆球。0..1 的 ubyte 精度足够。</p>
     */
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
