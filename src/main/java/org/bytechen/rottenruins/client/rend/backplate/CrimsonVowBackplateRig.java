package org.bytechen.rottenruins.client.rend.backplate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.IBlockingWeapon;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.config.data.SplendidingConfig;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 绯红誓约背板的姿态（位置 + 朝向）与动画状态机：主手拿 {@code hall:crimson_vow} 时玩家身后浮出血月誓约纹章，
 * 板面永远正对镜头，且永远落在"相对镜头后方"，身体不会切穿它。
 *
 * <p>坐标空间是命门：实体通道里相机旋转与"减相机位置"已在 {@code PoseStack} 做完，{@code ModelViewMat} 实测是单位阵，
 * 栈顶矩阵就是相机空间。所以这里的做法是「在相机空间里摆好板子，再左乘 {@code ModelViewMat⁻¹} 抵消管线那一步」，
 * 对 MV 含不含相机变换两种约定都成立。<b>不要把 {@code world − camPos} 写进平移列</b>（等于相机旋转乘两次，
 * 表现为一转视角光环就乱飞）；锚点只用玩家插值位置 + 世界高度 + 完整视线（三个分量都要），不含 yaw / bodyRot。
 * 驱动量都已是客户端同步数据（主手物品 + 格挡状态），不需要网包与 capability。</p>
 */
public final class CrimsonVowBackplateRig {

    /** 着色器图案可见半径的上限（几何 UV 径向值）：肉眼半径 = 0.86 × 矩阵缩放，故 config 的 Scale 就是看得见的半径；改 fsh 的 R_OUT 要同步这里。 */
    private static final float SHADER_VISIBLE_RADIUS = 0.86F;

    /** 展开 / 格挡蓄满 / 格挡回退的时长（tick）。 */
    private static final float UNFOLD_TICKS = 6.0F;
    private static final float BLOCK_ATTACK_TICKS = 4.0F;
    private static final float BLOCK_RELEASE_TICKS = 7.0F;
    /** 格挡进度累加器的刻度数，每 tick 走 1/BLOCK_ATTACK_TICKS 份。 */
    private static final long HOLD_STEPS = 60L;

    private CrimsonVowBackplateRig() {}

    /** 该玩家此刻该不该有背板；排除游泳/爬行与鞘翅滑翔 —— 那两种姿态下锚点会落到怪地方，镜头也会被拉近。 */
    public static boolean shouldRender(AbstractClientPlayer player) {
        if (!config().crimsonBackplateEnabled) return false;
        if (!(player.getMainHandItem().getItem()
                instanceof org.bytechen.rottenruins.overworld.registry.items.CrimsonVow)) {
            return false;
        }
        return !player.isFallFlying() && !player.isVisuallySwimming();
    }

    public static boolean isBlocking(AbstractClientPlayer player) {
        return IBlockingWeapon.isBlocking(player);
    }

    /** 每个玩家一本账；键用 {@link UUID} 而不是实体引用 —— 客户端实体对象可能被重建，账丢了只是动画重播一次。 */
    private static final Map<UUID, long[]> BOOKKEEPING = new HashMap<>();

    /** 账本下标：0 拿起剑的 tick、1 最后访问 tick、2 格挡进度累加器、3 是否进入过格挡。 */
    private static final int IDX_UNFOLD_START = 0;
    private static final int IDX_LAST_SEEN = 1;
    private static final int IDX_BLOCK_TICKS = 2;
    private static final int IDX_BLOCK_ACTIVE = 3;

    private static final long STALE_TICKS = 600L;

    /** 算出这一帧的姿态数据并把账本推进一 tick；两个进度只喂给着色器（变亮/自转），不参与矩阵。 */
    public static Anim pose(AbstractClientPlayer player, int now) {
        long[] book = BOOKKEEPING.computeIfAbsent(player.getUUID(),
                k -> new long[]{-1L, now, 0L, 0L});

        // 世界重载 / 换维度时 tickCount 会跳变甚至回退，这时把展开动画重来一遍，别卡在"永远展开"或"永远没展开"。
        if (now < book[IDX_LAST_SEEN] || now - book[IDX_LAST_SEEN] > 100L) {
            book[IDX_UNFOLD_START] = now;
            book[IDX_BLOCK_TICKS] = 0L;
            book[IDX_BLOCK_ACTIVE] = 0L;
        }
        book[IDX_LAST_SEEN] = now;

        if (book[IDX_UNFOLD_START] < 0L) book[IDX_UNFOLD_START] = now;
        float unfold = Mth.clamp((now - book[IDX_UNFOLD_START]) / UNFOLD_TICKS, 0.0F, 1.0F);

        // 用累加器而不是开始时间戳：松开右键后 isBlocking 立刻为假，只举 2 tick 也会淡出整整一档。
        boolean blocking = isBlocking(player);
        if (blocking) {
            book[IDX_BLOCK_ACTIVE] = 1L;
            book[IDX_BLOCK_TICKS] = Math.min(HOLD_STEPS,
                    book[IDX_BLOCK_TICKS] + Math.round(1.0F / BLOCK_ATTACK_TICKS));
        } else if (book[IDX_BLOCK_TICKS] > 0L) {
            book[IDX_BLOCK_TICKS] = Math.max(0L,
                    book[IDX_BLOCK_TICKS] - Math.round(1.0F / BLOCK_RELEASE_TICKS));
            if (book[IDX_BLOCK_TICKS] == 0L) book[IDX_BLOCK_ACTIVE] = 0L;
        }

        float block = Mth.clamp(book[IDX_BLOCK_TICKS] / (float) HOLD_STEPS, 0.0F, 1.0F);

        prune(now);
        return new Anim(ease(unfold), ease(block));
    }

    private static void prune(int now) {
        if (BOOKKEEPING.size() < 32) return;
        BOOKKEEPING.entrySet().removeIf(e -> now - e.getValue()[IDX_LAST_SEEN] > STALE_TICKS);
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    /** 这一帧的动画进度（0..1，已缓动）。 */
    public record Anim(float unfold, float block) {}

    // 每帧复用，避免在渲染线程上制造垃圾。只在渲染线程用，不需要同步。
    private static final Matrix4f SCRATCH_VIEW = new Matrix4f();
    private static final Matrix4f SCRATCH_MV = new Matrix4f();

    /**
     * 把栈顶矩阵整个换成"光环在相机空间里的位置与朝向"：栈顶原本是玩家姿态，这里只要位置、不要朝向（billboard）。
     *
     * <p>着色器算的是 {@code Proj × ModelViewMat × pose}，本方法写入 {@code pose = MV⁻¹ × M_view}，
     * 于是 {@code MV × pose = M_view} —— <b>与 MV 到底是什么无关</b>，不赌任何约定。
     * 两条不变量：锚点只由玩家位置 + 世界高度 + 视线方向决定（不含 yaw / bodyRot）；
     * 且恰好比头部那一点沿视线远 {@code HeadOffset} 格（屏位与头部重合、深度余量恒定）。</p>
     */
    public static void transform(PoseStack poseStack, AbstractClientPlayer player,
                                 Anim anim, float partialTick) {
        SplendidingConfig cfg = config();

        // 相机三轴正好是"世界 → 相机空间"的三行，点积即可，不需要矩阵求逆。
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f look = new Vector3f(camera.getLookVector());
        Vector3f up = new Vector3f(camera.getUpVector());
        Vector3f right = new Vector3f(look).cross(up).normalize();

        //  锚点 = 玩家插值位置 + 固定世界高度 + 沿视线远离相机 HeadOffset 格。
        //
        //  ⚠ 后退必须用完整的 look（三个分量都要）：只取水平分量时，"环心与头部同屏 + 恰好远 HeadOffset 格"
        //  这两条不变量只在镜头放平时成立，一俯仰就退化成 HeadOffset×cos²(pitch)（45° 只剩一半，环会被身体切穿）。
        float playerScale = player.getScale();          // 幼年体 0.5，其余 1.0
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        double back = cfg.crimsonBackplateHeadOffset * playerScale;
        double headY = py + cfg.crimsonBackplateHeight * playerScale;
        double anchorX = px + look.x * back;
        double anchorY = headY + look.y * back;
        double anchorZ = pz + look.z * back;

        Vec3 cam = camera.getPosition();
        double dx = anchorX - cam.x;
        double dy = anchorY - cam.y;
        double dz = anchorZ - cam.z;
        float vx = (float) (right.x * dx + right.y * dy + right.z * dz);
        float vy = (float) (up.x * dx + up.y * dy + up.z * dz);
        float vz = (float) -(look.x * dx + look.y * dy + look.z * dz);

        // config 的 Scale 是"看得见的半径"，除以着色器的图案半径才是矩阵缩放。
        float s = (float) (cfg.crimsonBackplateScale * playerScale) / SHADER_VISIBLE_RADIUS;
        SCRATCH_VIEW.set(
                s, 0.0F, 0.0F, 0.0F,
                0.0F, s, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                vx, vy, vz, 1.0F);

        // 实测 MV 是单位阵，这一步就是恒等；显式写出逆矩阵是为了不赌约定。
        Matrix4f entry = new Matrix4f(poseStack.last().pose());
        SCRATCH_MV.set(RenderSystem.getModelViewMatrix()).invert();
        poseStack.last().pose().set(SCRATCH_MV.mul(SCRATCH_VIEW));

        logDiagnostics(player, cfg, entry, poseStack.last().pose(),
                right, up, look, camera, px, py, pz, headY, back);
    }

    /** 读一次配置，未就绪（早期初始化、单元测试）时回落默认值；每次绘制都查 holder 是刻意的 —— Cloth Config 保存后会换掉实例，缓存字段会读到旧值。 */
    private static SplendidingConfig config() {
        try {
            ConfigHolder<SplendidingConfig> holder = ConfigHelper.configHolder;
            if (holder != null && holder.get() != null) return holder.get();
        } catch (Throwable ignored) {
        }
        return FALLBACK_CONFIG;
    }

    private static final SplendidingConfig FALLBACK_CONFIG = new SplendidingConfig();

    // 诊断：debug = true 时每 2 秒打一行组。数字全部取自渲染管线真正在用的矩阵（MV / Proj），
    // 代入 MV × pose × 顶点算屏幕 NDC —— 这正是 GPU 要做的事，而不是自己拼的近似。

    /** 上次打印的墙上时钟（毫秒）。初值取 0 而不是 Long.MIN_VALUE —— 后者会让 {@code nowMs - lastLogMillis} 立刻溢出成负数，"每 2 秒"永远不成立（踩过）。 */
    private static long lastLogMillis = 0L;

    private static boolean loggedOnce = false;

    private static void logDiagnostics(AbstractClientPlayer player, SplendidingConfig cfg,
                                       Matrix4f entry, Matrix4f pose,
                                       Vector3f right, Vector3f up, Vector3f look, Camera camera,
                                       double px, double py, double pz, double headY, double back) {
        // 第一组无条件打（"到底有没有被调用、算得对不对"要一眼看到），之后只在 debug 打开时每 2 秒打一组。
        // 计时用墙上时钟而不是 player.tickCount：单机窗口失焦时游戏会自动暂停、tickCount 冻住，按 tick 计时就再也不打了（踩过）。
        int now = player.tickCount;
        if (!loggedOnce) {
            loggedOnce = true;
        } else {
            if (!cfg.debug) return;
            long nowMs = Util.getMillis();
            if (nowMs - lastLogMillis < 2000L) return;
            lastLogMillis = nowMs;
        }

        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f effective = new Matrix4f(mv).mul(pose);

        Vec3 cam = camera.getPosition();
        // 栈顶矩阵该有的样子：玩家模型原点（脚底上方 1.407 格，含 PlayerRenderer 的 0.9375 缩放）在相机空间里的坐标。
        double originY = py + 1.4072 * player.getScale();
        double ox = px - cam.x, oy = originY - cam.y, oz = pz - cam.z;
        float ex = (float) (right.x * ox + right.y * oy + right.z * oz);
        float ey = (float) (up.x * ox + up.y * oy + up.z * oz);
        float ez = (float) -(look.x * ox + look.y * oy + look.z * oz);
        float entryErr = (float) Math.sqrt(sq(entry.m30() - ex) + sq(entry.m31() - ey) + sq(entry.m32() - ez));

        float mvErr = maxAbsDiffFromIdentity(mv);
        Vector3f effZ = new Vector3f(effective.m20(), effective.m21(), effective.m22());
        Vector3f effX = new Vector3f(effective.m00(), effective.m01(), effective.m02());
        Vector3f effY = new Vector3f(effective.m10(), effective.m11(), effective.m12());
        Vector3f effT = new Vector3f(effective.m30(), effective.m31(), effective.m32());

        // 环在相机空间里相对"头部那一点"的深度差 —— 必须正好是 HeadOffset
        double hx = px - cam.x, hy = headY - cam.y, hz = pz - cam.z;
        float headViewZ = (float) -(look.x * hx + look.y * hy + look.z * hz);
        float depthGap = headViewZ - effT.z;

        Matrix4f full = new Matrix4f(proj).mul(effective);
        float r = SHADER_VISIBLE_RADIUS;
        float[] sx = {0.0F, r, -r, 0.0F, 0.0F};
        float[] sy = {0.0F, 0.0F, 0.0F, r, -r};
        float[] ndcX = new float[5];
        float[] ndcY = new float[5];
        float[] wArr = new float[5];
        boolean inFront = true;
        for (int i = 0; i < 5; i++) {
            Vector4f clip = full.transform(new Vector4f(sx[i], sy[i], 0.0F, 1.0F));
            wArr[i] = clip.w;
            if (clip.w <= 0.0F) inFront = false;
            float inv = Math.abs(clip.w) < 1.0E-6F ? Float.NaN : 1.0F / clip.w;
            ndcX[i] = clip.x * inv;
            ndcY[i] = clip.y * inv;
        }
        boolean onScreen = Math.abs(ndcX[0]) <= 1.0F && Math.abs(ndcY[0]) <= 1.0F;

        // 头部那一点的屏幕位置：环心必须与它重合，这是"转视角不会乱跑"的判据。
        Vector3f headView = new Vector3f(
                (float) (right.x * hx + right.y * hy + right.z * hz),
                (float) (up.x * hx + up.y * hy + up.z * hz),
                (float) -(look.x * hx + look.y * hy + look.z * hz));
        Vector4f headClip = new Matrix4f(proj).mul(mv).transform(new Vector4f(headView, 1.0F));
        float headNdcX = headClip.w == 0.0F ? Float.NaN : headClip.x / headClip.w;
        float headNdcY = headClip.w == 0.0F ? Float.NaN : headClip.y / headClip.w;
        float alignErr = (float) Math.hypot(ndcX[0] - headNdcX, ndcY[0] - headNdcY);

        boolean facing = effZ.z > 0.999F;
        boolean behindHead = Math.abs(depthGap - back) < 0.02F;
        boolean aligned = alignErr < 0.01F;
        boolean spaceOk = entryErr < 0.05F;
        boolean allOk = inFront && onScreen && facing && behindHead && aligned && spaceOk && mvErr < 0.01F;

        HallMod.LOGGER.info("[CrimsonVowBackplate] === tick={} debug={} 玩家=({}, {}, {}) 相机=({}, {}, {})"
                        + " 世界差=({}, {}, {}) 视线=({}, {}, {})",
                now, cfg.debug, f(px), f(py), f(pz), f(cam.x), f(cam.y), f(cam.z),
                f(px - cam.x), f(py - cam.y), f(pz - cam.z),
                f(look.x), f(look.y), f(look.z));
        HallMod.LOGGER.info("[CrimsonVowBackplate] 覆盖前栈顶平移列=({}, {}, {}) 预测(相机空间)=({}, {}, {})"
                        + " 偏差={} {}",
                f(entry.m30()), f(entry.m31()), f(entry.m32()), f(ex), f(ey), f(ez), f(entryErr),
                spaceOk ? "✓ 栈顶是相机空间" : "★ 栈顶不是相机空间");
        HallMod.LOGGER.info("[CrimsonVowBackplate] ModelViewMat 与单位阵最大偏差={} {}",
                f(mvErr), mvErr < 0.01F ? "✓ 实测单位阵" : "★ 非单位阵（已用其逆抵消）");
        HallMod.LOGGER.info("[CrimsonVowBackplate] 写入的 pose：X列=({}, {}, {}) Y列=({}, {}, {}) Z列=({}, {}, {}) 平移列=({}, {}, {})",
                f(pose.m00()), f(pose.m01()), f(pose.m02()),
                f(pose.m10()), f(pose.m11()), f(pose.m12()),
                f(pose.m20()), f(pose.m21()), f(pose.m22()),
                f(pose.m30()), f(pose.m31()), f(pose.m32()));
        HallMod.LOGGER.info("[CrimsonVowBackplate] 生效矩阵 MV×pose：X=({}, {}, {}) Y=({}, {}, {}) Z=({}, {}, {}) 平移=({}, {}, {})",
                f(effX.x), f(effX.y), f(effX.z), f(effY.x), f(effY.y), f(effY.z),
                f(effZ.x), f(effZ.y), f(effZ.z), f(effT.x), f(effT.y), f(effT.z));
        HallMod.LOGGER.info("[CrimsonVowBackplate] 屏幕预测：环心 NDC=({}, {}) w={} | 左右=({}, {}) | 上下=({}, {})"
                        + " 可见直径={} 格",
                f(ndcX[0]), f(ndcY[0]), f(wArr[0]),
                f(Math.abs(ndcX[1] - ndcX[2])), f(Math.abs(ndcY[1] - ndcY[2])),
                f(Math.abs(ndcX[3] - ndcX[4])), f(Math.abs(ndcY[3] - ndcY[4])),
                f(2.0 * cfg.crimsonBackplateScale * player.getScale()));
        HallMod.LOGGER.info("[CrimsonVowBackplate] 不变量：在镜头前方={} 环心在屏内={} 板面正对镜头={}"
                        + " 环比头部远 {} 格（期望 {}）={} 环心与头部同屏（偏差 {}）={} → {}",
                inFront ? "✓" : "★", onScreen ? "✓" : "★", facing ? "✓" : "★",
                f(depthGap), f(back), behindHead ? "✓" : "★", f(alignErr), aligned ? "✓" : "★",
                allOk ? "全部通过 ✓" : "★ 有不变量失败");
    }

    private static float sq(float v) {
        return v * v;
    }

    /** 与单位阵的最大元素差。 */
    private static float maxAbsDiffFromIdentity(Matrix4f m) {
        float[] v = new float[16];
        m.get(v);
        float max = 0.0F;
        for (int i = 0; i < 16; i++) {
            float want = (i % 5 == 0) ? 1.0F : 0.0F;
            max = Math.max(max, Math.abs(v[i] - want));
        }
        return max;
    }

    /** 日志里统一用两位小数，避免刷屏。 */
    private static String f(double v) {
        return String.format("%.3f", v);
    }
}
