package org.bytechen.hall.client.rend.backplate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.IBlockingWeapon;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.config.data.SplendidingConfig;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 绯红誓约背板的<b>姿态</b>（位置 + 朝向）与动画状态机。
 *
 * <h3>它是什么</h3>
 * <p>主手拿着 {@code hall:crimson_vow} 时，玩家身后浮出一圈三层誓约纹章。
 * 板面<b>永远正对镜头</b>（billboard），位置<b>永远在玩家的"相对镜头后方"</b>：
 * 从镜头看过去，它始终落在玩家背后，不会转到侧面、更不会跑到身前。</p>
 *
 * <h3>坐标空间：整个功能的命门（前面几版全栽在这里）</h3>
 * <p>顶点着色器算的是 {@code ProjMat × ModelViewMat × vec4(Position, 1)}，
 * 而 {@code BufferUploader} 在 flush 时把 {@code ModelViewMat} 设成
 * {@code RenderSystem.getModelViewMatrix()}。1.20.1 的实体渲染通道里，
 * <b>相机的旋转与"减相机位置"已经在 {@code PoseStack} 里做完了</b>
 * （{@code GameRenderer.renderLevel} 先 {@code mulPose(相机旋转)}，
 * {@code EntityRenderDispatcher.render} 再 {@code translate(实体 − 相机)}），
 * 所以那个 {@code ModelViewMat} 实测就是<b>单位阵</b>，栈顶矩阵直接就是<b>相机空间</b>。</p>
 *
 * <p>于是本方法这样写：先在<b>相机空间</b>里把板子摆好（单位旋转 = 正对镜头），
 * 再左乘 {@code ModelViewMat⁻¹} 抵消管线自己那一步。
 * 这一写法对"ModelViewMat 是单位阵"和"ModelViewMat 真含相机变换"两种约定<b>都成立</b>
 * —— 因为它保证的是一条与约定无关的恒等式：{@code ModelViewMat × pose = 我想要的相机空间矩阵}。
 * 详见 {@link #transform} 的注释。</p>
 *
 * <p><b>不要再把 {@code world − camPos} 写进平移列</b>：那是"相机相对世界坐标"，
 * 会被当成相机空间坐标用一次，等于相机旋转被乘了两次 —— 表现就是
 * "一转视角光环就绕着我乱飞，一会儿背后一会儿侧面一会儿脸前"。
 * 数值复现见文档 §陷阱。</p>
 *
 * <h3>位置与朝向怎么来的</h3>
 * <ul>
 *   <li><b>位置</b>：玩家插值位置 + 固定世界高度（决定"挂在多高"），再沿相机视线方向
 *       <b>远离相机</b>退 {@code HeadOffset} 格（用<b>完整的</b>视线向量，三个分量都要）。
 *       最后这一项是刻意的："从镜头看过去的玩家身后"就是"+视线方向"，所以任何视角下
 *       它都在玩家背后，而且恰好比头部那一点远 {@code HeadOffset} 格 ——
 *       屏位与头部重合、深度余量恒定，身体永远不会切穿它。
 *       <b>偏移量里不能出现玩家朝向（yaw / bodyRot）</b> —— 那是前几版"绕到身前"的来源。</li>
 *   <li><b>朝向</b>：相机空间里的单位旋转，也就是板面永远正对镜头。</li>
 * </ul>
 *
 * <h3>状态从哪来：不需要任何网包</h3>
 * <p>两个驱动量都已经是客户端已知的同步数据：主手物品是不是 {@code CrimsonVow}
 * （物品同步）、{@link IBlockingWeapon#isBlocking} 的格挡状态（"正在使用物品"同步）。
 * 所以这里不引入 capability、不改网络层。</p>
 */
public final class CrimsonVowBackplateRig {

    // ──────────────────────────────────────────────────────────────
    //  姿态常数（实机调参优先改 config，见 SplendidingConfig）
    // ──────────────────────────────────────────────────────────────

    /**
     * 着色器里"图案半径的上限"，单位是几何的 UV 径向值。
     *
     * <p>几何烘在单位平面上（见 {@link CrimsonVowBackplateMesh}），片元最后有一句
     * {@code if (r > RingRadius.x + 0.06) discard;}，其中 {@code RingRadius.x = 0.80}，
     * 所以肉眼看到的圆盘半径 = {@code 0.86 × 矩阵缩放}。</p>
     *
     * <p>把它显式写出来，是为了让 config 里的 {@code Scale} 就是<b>看得见的半径（格）</b>
     * —— 否则用户调 1.3 却得到 1.84 格，谁也说不清该填多少。
     * <b>改 fsh 里的 {@code RingRadius} 时记得同步这里。</b></p>
     */
    private static final float SHADER_VISIBLE_RADIUS = 0.86F;

    /** 展开时长（tick）与格挡满强度时长（tick）。 */
    private static final float UNFOLD_TICKS = 6.0F;
    private static final float BLOCK_ATTACK_TICKS = 4.0F;
    /** 格挡从松开到完全回到常态的时长（tick）。 */
    private static final float BLOCK_RELEASE_TICKS = 7.0F;
    /** 格挡进度累加器的刻度数（= 每 tick 走 1/HOLD_STEPS）。 */
    private static final long HOLD_STEPS = 60L;

    private CrimsonVowBackplateRig() {}

    // ──────────────────────────────────────────────────────────────
    //  触发条件
    // ──────────────────────────────────────────────────────────────

    /**
     * 这个玩家此刻该不该有背板。
     *
     * <p>刻意排除两种姿态：<b>游泳/爬行</b>与<b>鞘翅滑翔</b>。
     * 这两种姿态下玩家模型整个横过来，"头部后方"这个锚点会落到很奇怪的位置，
     * 而第三人称镜头也会被拉近，观感只会更差。</p>
     */
    public static boolean shouldRender(AbstractClientPlayer player) {
        if (!config().crimsonBackplateEnabled) return false;
        if (!(player.getMainHandItem().getItem()
                instanceof org.bytechen.hall.overworld.registry.items.CrimsonVow)) {
            return false;
        }
        return !player.isFallFlying() && !player.isVisuallySwimming();
    }

    /** 是否正在举剑格挡（用于动画强度，不影响是否渲染）。 */
    public static boolean isBlocking(AbstractClientPlayer player) {
        return IBlockingWeapon.isBlocking(player);
    }

    // ──────────────────────────────────────────────────────────────
    //  动画进度（按玩家记账）
    // ──────────────────────────────────────────────────────────────

    /**
     * 每个玩家一本账。四个字段的含义见下面的 {@code IDX_*} 常量。
     *
     * <p>用 {@link UUID} 而不是实体引用做键：客户端实体对象可能被重建，
     * 而这里记的内容"丢了也只是让动画重播一次"。{@link #prune} 负责在
     * 玩家离开视野后清掉，避免长时间游戏后无限增长。</p>
     */
    private static final Map<UUID, long[]> BOOKKEEPING = new HashMap<>();

    /** 拿起剑的那一刻（tick）。 */
    private static final int IDX_UNFOLD_START = 0;
    /** 最后一次被访问的 tick —— 只用来判"凉了没"。 */
    private static final int IDX_LAST_SEEN = 1;
    /** 格挡进度累加器（刻度，满值 {@link #HOLD_STEPS}）。 */
    private static final int IDX_BLOCK_TICKS = 2;
    /** 是否曾经进入过格挡（1/0）—— 用来区分"从没举过"与"举完松开了"。 */
    private static final int IDX_BLOCK_ACTIVE = 3;

    /** 超过这么久没被访问的账本会被清掉（tick，约 30 秒）。 */
    private static final long STALE_TICKS = 600L;

    /**
     * 算出这一帧的姿态数据，并把账本推进一 tick 的量。
     *
     * <p>这两个进度<b>只喂给着色器</b>（展开时变亮、格挡时更亮更快的自转），
     * <b>不参与矩阵</b> —— 上一版拿它们做缩放/呼吸，结果"光环老在动"，
     * 而用户要的是一块不动的牌子。</p>
     *
     * @param player 目标玩家
     * @param now    当前客户端 tick 计数（{@code player.tickCount}）
     */
    public static Anim pose(AbstractClientPlayer player, int now) {
        long[] book = BOOKKEEPING.computeIfAbsent(player.getUUID(),
                k -> new long[]{-1L, now, 0L, 0L});

        // 世界重载 / 换维度时 tickCount 会跳变甚至回退，这时把展开动画重来一遍，
        // 而不是让它卡在"永远展开"或"永远没展开"。
        if (now < book[IDX_LAST_SEEN] || now - book[IDX_LAST_SEEN] > 100L) {
            book[IDX_UNFOLD_START] = now;
            book[IDX_BLOCK_TICKS] = 0L;
            book[IDX_BLOCK_ACTIVE] = 0L;
        }
        book[IDX_LAST_SEEN] = now;

        // ── 展开 ──
        if (book[IDX_UNFOLD_START] < 0L) book[IDX_UNFOLD_START] = now;
        float unfold = Mth.clamp((now - book[IDX_UNFOLD_START]) / UNFOLD_TICKS, 0.0F, 1.0F);

        // ── 格挡：按下时往上加，松开后往回退 ──
        // 用「累加器」而不是「开始时间戳」：松开右键后 isBlocking 立刻为假，
        // 拿开始时间戳就只能一路淡出到 0，而举剑只举了 2 tick 也会淡出整整一档。
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

    /** 清掉超过 {@link #STALE_TICKS} 没访问过的账本。 */
    private static void prune(int now) {
        if (BOOKKEEPING.size() < 32) return;   // 人少时不做无谓的遍历
        BOOKKEEPING.entrySet().removeIf(e -> now - e.getValue()[IDX_LAST_SEEN] > STALE_TICKS);
    }

    /** 平滑曲线：起止速度为 0，中段最快（把线性进度变成"弹出"的手感）。 */
    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    // ──────────────────────────────────────────────────────────────
    //  变换
    // ──────────────────────────────────────────────────────────────

    /**
     * 这一帧的动画进度。
     *
     * @param unfold 展开进度 0..1（已缓动）
     * @param block  格挡强度 0..1（已缓动）
     */
    public record Anim(float unfold, float block) {}

    // 每帧复用，避免在渲染线程上制造垃圾。只在渲染线程用，不需要同步。
    private static final Matrix4f SCRATCH_VIEW = new Matrix4f();
    private static final Matrix4f SCRATCH_MV = new Matrix4f();

    /**
     * 把栈顶矩阵整个换成"光环在相机空间里的位置与朝向"。
     *
     * <h3>为什么可以直接覆盖栈顶</h3>
     * <p>栈顶原本是「相机旋转 × (玩家 − 相机) × 身体朝向 × 翻转 × 缩放」，
     * 也就是"玩家的姿态"。本方法<b>只要位置、不要朝向</b>（要的是 billboard），
     * 所以整个换掉；位置自己按下面的算法重算，与玩家姿态无关。</p>
     *
     * <h3>算出来的矩阵为什么一定对</h3>
     * <p>顶点着色器最终算的是 {@code Proj × ModelViewMat × pose}。记
     * {@code MV = ModelViewMat}，本方法写入的是</p>
     * <pre>
     *     pose = MV⁻¹ × M_view
     * </pre>
     * <p>于是 {@code MV × pose = M_view}，<b>与 MV 到底是什么无关</b>。
     * {@code M_view} 是"相机空间里正对镜头的板子"：三列取单位轴（相机空间里
     * +X 是屏幕右、+Y 是屏幕上、+Z 指向镜头），平移列取锚点的相机空间坐标。</p>
     * <p>这不是花活，是被实测逼出来的：实体通道里 {@code MV} 实际是单位阵
     * （相机变换已经在 PoseStack 里做完了），但把这一步显式写出来，
     * 就同时兼容"MV 真含相机变换"的情形 —— 上一版正是赌错了这件事，
     * 把"相机相对世界坐标"填进了平移列，于是相机旋转被乘了两次。</p>
     *
     * <h3>两条不变量（改代码时守住它们就不会再乱跑）</h3>
     * <ol>
     *   <li>锚点<b>只由玩家位置 + 世界高度 + 视线方向</b>决定。
     *       里面<b>没有</b>玩家朝向（yaw / bodyRot / headRot），也没有任何
     *       "沿视线退 N 格"之外的相机相关量。</li>
     *   <li>锚点恰好比"头部那一点"沿视线远 {@code HeadOffset} 格 ——
     *       所以屏幕上环心与头部重合、深度上永远在玩家之后。</li>
     * </ol>
     *
     * @param poseStack   栈（内容会被整个覆盖）
     * @param player      目标玩家
     * @param anim        {@link #pose} 的结果（只喂着色器，这里不用）
     * @param partialTick 帧插值系数：玩家位置用
     */
    public static void transform(PoseStack poseStack, AbstractClientPlayer player,
                                 Anim anim, float partialTick) {
        SplendidingConfig cfg = config();

        // ── ① 相机基（世界空间） ──
        //    look 是视线（指向镜头前方），right = look × up 是屏幕向右。
        //    这三根轴正好就是"世界 → 相机空间"的三行，所以后面点积即可，不需要矩阵求逆。
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f look = new Vector3f(camera.getLookVector());
        Vector3f up = new Vector3f(camera.getUpVector());
        Vector3f right = new Vector3f(look).cross(up).normalize();

        // ── ② 世界空间锚点 ──
        //    玩家插值位置 + 固定高度（世界 Y：决定"光环挂在多高"，与镜头无关）
        //    + 沿视线"远离相机"退 HeadOffset 格。
        //
        //    ⚠ 后退这一步必须用**完整的 look（三个分量都要）**，不能只取水平分量：
        //    只取水平分量时，"环心与头部同屏 + 恰好远 HeadOffset 格"这两条不变量
        //    只在镜头放平时成立，镜头一俯仰就退化成 HeadOffset×cos²(pitch)
        //    （45° 只剩一半：深度余量 0.65→0.325，环会被身体切穿；屏位也偏出 NDC 0.16）。
        float playerScale = player.getScale();          // 幼年体 0.5，其余 1.0
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        double back = cfg.crimsonBackplateHeadOffset * playerScale;
        double headY = py + cfg.crimsonBackplateHeight * playerScale;
        double anchorX = px + look.x * back;
        double anchorY = headY + look.y * back;
        double anchorZ = pz + look.z * back;

        // ── ③ 锚点 → 相机空间坐标（= 与相机三轴的点积；前方为 −Z） ──
        Vec3 cam = camera.getPosition();
        double dx = anchorX - cam.x;
        double dy = anchorY - cam.y;
        double dz = anchorZ - cam.z;
        float vx = (float) (right.x * dx + right.y * dy + right.z * dz);
        float vy = (float) (up.x * dx + up.y * dy + up.z * dz);
        float vz = (float) -(look.x * dx + look.y * dy + look.z * dz);

        // ── ④ 相机空间里"正对镜头"就是单位旋转 ──
        //    模型 +X → 屏幕右、+Y → 屏幕上、+Z → 朝着镜头（几何法线就是 +Z）。
        //    config 的 Scale 是**看得见的半径**，除以着色器的图案半径才是矩阵缩放。
        float s = (float) (cfg.crimsonBackplateScale * playerScale) / SHADER_VISIBLE_RADIUS;
        SCRATCH_VIEW.set(
                s, 0.0F, 0.0F, 0.0F,
                0.0F, s, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                vx, vy, vz, 1.0F);

        // ── ⑤ pose = ModelViewMat⁻¹ × M_view（见方法注释里的恒等式） ──
        //    实测 MV 是单位阵，这一步就是恒等；显式写出来是为了不赌约定。
        Matrix4f entry = new Matrix4f(poseStack.last().pose());   // 诊断用：覆盖前的栈顶
        SCRATCH_MV.set(RenderSystem.getModelViewMatrix()).invert();
        poseStack.last().pose().set(SCRATCH_MV.mul(SCRATCH_VIEW));

        logDiagnostics(player, cfg, entry, poseStack.last().pose(),
                right, up, look, camera, px, py, pz, headY, back);
    }

    /**
     * 读一次配置；配置没就绪（早期初始化、单元测试等）时回落到内置默认值。
     *
     * <p>每次绘制都查一次 config holder 是刻意的：Cloth Config 保存后会换掉
     * holder 里的实例，缓存字段就会读到旧值 —— 而这一组参数的整个意义就是
     * "改完即生效、不用重编译"。</p>
     */
    private static SplendidingConfig config() {
        try {
            ConfigHolder<SplendidingConfig> holder = ConfigHelper.configHolder;
            if (holder != null && holder.get() != null) return holder.get();
        } catch (Throwable ignored) {
            // 配置系统尚未初始化 —— 用默认值继续，不要因为调参面板而崩掉渲染
        }
        return FALLBACK_CONFIG;
    }

    /** 配置不可用时的兜底实例（与字段默认值一致）。 */
    private static final SplendidingConfig FALLBACK_CONFIG = new SplendidingConfig();

    // ──────────────────────────────────────────────────────────────
    //  诊断：config 里 debug = true 时每 2 秒打一行组
    //
    //  全部数字都来自"渲染管线真正在用的那几个矩阵"（RenderSystem 里的
    //  ModelViewMat / ProjMat），而不是自己拼的近似 —— 上一版拿
    //  RenderSystem.getModelViewMatrix() 当"世界→相机"的真值去算环的屏幕位置，
    //  结论全错（实测那个矩阵就是单位阵）。现在用它只做一件事：
    //  代入 MV × pose × 顶点 算出屏幕 NDC，这正是 GPU 要做的事。
    // ──────────────────────────────────────────────────────────────

    /** 上次打印的墙上时钟（毫秒）。初值取 0 而不是 Long.MIN_VALUE —— 后者会让
     *  {@code nowMs - lastLogMillis} 立刻溢出成负数，于是"每 2 秒"永远不成立（踩过）。 */
    private static long lastLogMillis = 0L;

    /** 本局是否已经打过一组诊断。 */
    private static boolean loggedOnce = false;

    private static void logDiagnostics(AbstractClientPlayer player, SplendidingConfig cfg,
                                       Matrix4f entry, Matrix4f pose,
                                       Vector3f right, Vector3f up, Vector3f look, Camera camera,
                                       double px, double py, double pz, double headY, double back) {
        // 第一组无条件打（"到底有没有被调用、算出来对不对"要能一眼看到），
        // 之后只在 debug 打开时每 2 秒打一组。
        //
        // 计时用**墙上时钟**而不是 player.tickCount：单机窗口失去焦点时游戏会自动暂停，
        // 那时 tickCount 冻住不动，按 tick 计时的诊断就再也不打了（踩过）。
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
        Matrix4f effective = new Matrix4f(mv).mul(pose);      // = 着色器真正拿到的那个矩阵

        Vec3 cam = camera.getPosition();
        // 栈顶矩阵该有的样子：玩家模型原点（脚底上方 1.407 格，含 PlayerRenderer 的 0.9375 缩放）
        // 在**相机空间**里的坐标。数值吻合 ⇒ 覆盖前栈顶确实是相机空间，与本文档的判断一致。
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

        // 屏幕预测：把着色器那条式子照抄一遍（环心 + 四个可见边缘点）
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

        // 头部那一点的屏幕位置（同一套相机基 + 同一个真值 Proj/MV）。
        // 环心必须与它重合 —— 这是"转视角不会乱跑"的判据。
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
