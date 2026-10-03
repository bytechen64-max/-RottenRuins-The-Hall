import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 独立数值探针（不属于模组源码，只在 build/ 下，不进打包）。
 *
 * <p>目的：在<b>不开游戏</b>的前提下，把 Minecraft 1.20.1 的实体渲染管线复刻出来，
 * 验证 {@code CrimsonVowBackplateRig.transform()} 的数学。管线来自实际反编译源码
 * （Forge 47.4.20 sources jar），不是凭记忆：
 *
 * <pre>
 *   GameRenderer.renderLevel:      levelPose = Rz(roll) · Rx(xRot) · Ry(yRot + 180)
 *   Camera.setRotation:            rot = rotationYXZ(−yRot, xRot, 0)，基向量由 rot 旋转出来
 *   EntityRenderDispatcher.render: pose = levelPose · T(entityPos + renderOffset − camPos)
 *   LivingEntityRenderer.render:   pose = pose · Ry(180 − bodyRot) · S(−1,−1,1) · S(0.9375)
 *                                        · T(0, −1.501, 0)
 *   BufferUploader:                ModelViewMat = RenderSystem.getModelViewMatrix()
 *   顶点着色器:                    gl_Position = ProjMat · ModelViewMat · vec4(Position, 1)
 * </pre>
 *
 * <p>先做"答案显然"的自检（JOML 的 set() 参数顺序、mul() 乘法顺序、视图矩阵的定义式），
 * 这些必须全过，后面的结论才可信。<b>探针第一版就栽在这里</b>：正下方那个退化用例
 * 分不出"基矩阵"和"基矩阵的转置"，于是得出了错误结论 —— 现在补了斜视角用例 1i/1j。
 */
public final class BackplateProbe {

    // ── 被测参数（与 SplendidingConfig 默认值一致） ──
    static final float SCALE = 1.30F;        // 可见半径（格）—— config 里就是它
    static final float HEIGHT = 1.55F;       // 中心离脚底（格）
    static final float BACK = 0.65F;         // 沿视线远离相机（格）
    static final float VISIBLE_R = 0.86F;    // fsh 里 discard 的半径上限（UV 径向）
    /** 可见边缘在**模型空间**里的半径：几何是单位平面，矩阵里乘了 SCALE/VISIBLE_R。 */
    static final float EDGE_MODEL = VISIBLE_R;

    static int failures = 0;

    public static void main(String[] args) {
        case0_jomlConventions();
        case1_viewMatrixDefinition();
        caseHalf_whichViewRotation();
        case2_pipelineYawPitchSweep();
        case3_oldVersionControl();
        case4_modelViewMatConventions();
        case5_facingAndWinding();

        System.out.println();
        System.out.println(failures == 0
                ? "== 全部通过：探针结论可用 =="
                : "== " + failures + " 条断言失败 ==");
        if (failures != 0) System.exit(1);
    }

    // ─────────────────────────────────────────────────────────────
    //  0. JOML 约定自检
    // ─────────────────────────────────────────────────────────────
    static void case0_jomlConventions() {
        System.out.println("── 用例 0：JOML 约定自检（答案已知） ──");

        Matrix4f m = new Matrix4f().set(
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16);
        checkVec("0a set() 前 4 个参数 = 第 1 列 (m00,m01,m02)",
                new Vector3f(m.m00(), m.m01(), m.m02()), new Vector3f(1, 2, 3));
        checkVec("0b set() 最后 4 个参数 = 平移列 (m30,m31,m32)",
                new Vector3f(m.m30(), m.m31(), m.m32()), new Vector3f(13, 14, 15));
        Matrix4f t = new Matrix4f().translation(1, 2, 3);
        checkVec("0c translation() 写平移列",
                new Vector3f(t.m30(), t.m31(), t.m32()), new Vector3f(1, 2, 3));

        Matrix4f a = new Matrix4f().translation(10, 0, 0);
        Matrix4f b = new Matrix4f().rotationY((float) (Math.PI / 2));
        Vector3f v = new Vector3f(1, 0, 0);
        Vector3f got = a.mul(b, new Matrix4f()).transformPosition(new Vector3f(v));
        checkVec("0d (A·B)·v：先 B 后 A", got,
                new Vector3f(b.transformPosition(new Vector3f(v))).add(10, 0, 0));

        Matrix4f r = new Matrix4f().rotationYXZ(0.7F, -0.4F, 0.0F).translate(1, 2, 3);
        Matrix4f id = new Matrix4f(r).mul(new Matrix4f(r).invert());
        checkVec("0e M · M⁻¹ = I（平移列为 0）",
                new Vector3f(id.m30(), id.m31(), id.m32()), new Vector3f(0, 0, 0));
    }

    // ─────────────────────────────────────────────────────────────
    //  1. 视图矩阵的**定义式**：V·视线 = (0,0,-1)、V·上 = (0,1,0)、V·left = (-1,0,0)
    //     —— 加上斜视角的具体数字，防"写成转置"
    // ─────────────────────────────────────────────────────────────
    static void case1_viewMatrixDefinition() {
        System.out.println("── 用例 1：视图矩阵定义式 + 斜视角具体数字 ──");

        // 相机在 (0,10,0) 朝正下方看，点 (0,0,0) 必在 (0,0,-10)（视图中心）
        Quaternionf rot = new Quaternionf().rotationYXZ(0.0F, (float) Math.toRadians(90.0), 0.0F);
        Vector3f look = new Vector3f(0, 0, 1).rotate(rot);
        Vector3f up = new Vector3f(0, 1, 0).rotate(rot);
        Matrix4f v = viewRotation(look, up);
        checkVec("1a 视线朝正下 (0,-1,0)", look, new Vector3f(0, -1, 0));
        checkVec("1b 正下方 10 格的点 → 视图 (0,0,-10)",
                v.transformPosition(new Vector3f(0, 0, 0).sub(0, 10, 0)), new Vector3f(0, 0, -10));

        // 定义式
        checkVec("1c V·视线 = (0,0,-1)（前方为 −Z）",
                v.transformDirection(new Vector3f(look)), new Vector3f(0, 0, -1));
        checkVec("1d V·上 = (0,1,0)", v.transformDirection(new Vector3f(up)), new Vector3f(0, 1, 0));
        Vector3f left = new Vector3f(up).cross(look).normalize();
        checkVec("1e V·left = (-1,0,0)（MC 的 left 在屏幕上指向左）",
                v.transformDirection(new Vector3f(left)), new Vector3f(-1, 0, 0));

        // 斜视角（非退化）：相机在原点、yaw=90 ⇒ 朝世界 −X 看，up = +Y
        //   世界 (-10,0,+5)：正前方 10 格 + 偏 5 格（+Z 在镜头左侧）⇒ 视图 (-5,0,-10)
        //   转置矩阵会得到完全不同的值
        Quaternionf rot2 = new Quaternionf().rotationYXZ((float) Math.toRadians(-90.0), 0.0F, 0.0F);
        Vector3f look2 = new Vector3f(0, 0, 1).rotate(rot2);
        Vector3f up2 = new Vector3f(0, 1, 0).rotate(rot2);
        Matrix4f v2 = viewRotation(look2, up2);
        checkVec("1f 斜视角：视线 = (−1,0,0)", look2, new Vector3f(-1, 0, 0));
        checkVec("1g 斜视角：世界 (−10,0,+5) → 视图 (−5,0,−10)",
                v2.transformPosition(new Vector3f(-10, 0, 5)), new Vector3f(-5, 0, -10));
        checkVec("1h 斜视角：世界 (−10,0,−5) → 视图 (+5,0,−10)",
                v2.transformPosition(new Vector3f(-10, 0, -5)), new Vector3f(5, 0, -10));
    }

    // ─────────────────────────────────────────────────────────────
    //  0.5 相机基向量 vs 官方 levelPose 公式
    // ─────────────────────────────────────────────────────────────
    static void caseHalf_whichViewRotation() {
        System.out.println("── 用例 0.5：相机基向量算出的视图矩阵 == 官方 levelPose 公式？ ──");
        float[] yaws = {0, 30, 45, 90, 135, 180, 225, 270, 315};
        float[] pitches = {-60, -30, 0, 30, 60};
        float maxErr = 0;
        for (float yaw : yaws) {
            for (float pitch : pitches) {
                Quaternionf rot = new Quaternionf().rotationYXZ(
                        (float) Math.toRadians(-yaw), (float) Math.toRadians(pitch), 0.0F);
                Vector3f look = new Vector3f(0, 0, 1).rotate(rot);
                Vector3f up = new Vector3f(0, 1, 0).rotate(rot);
                Matrix4f fromBasis = viewRotation(look, up);
                Matrix4f levelPose = new Matrix4f()
                        .rotateX((float) Math.toRadians(pitch))
                        .rotateY((float) Math.toRadians(yaw + 180.0F));
                maxErr = Math.max(maxErr, matrixDiff(fromBasis, levelPose));
            }
        }
        System.out.printf("   45 个姿态下两种算法的最大元素差 = %.6f%n", maxErr);
        if (maxErr < 1e-4F) pass("0.5a 相机基构成的视图矩阵 == 官方 levelPose（两种算法等价）");
        else fail("0.5a 两者不等价（差 " + maxErr + "），必须以官方 levelPose 为准");
    }

    static float matrixDiff(Matrix4f a, Matrix4f b) {
        float[] x = new float[16], y = new float[16];
        a.get(x);
        b.get(y);
        float m = 0;
        for (int i = 0; i < 16; i++) m = Math.max(m, Math.abs(x[i] - y[i]));
        return m;
    }

    // ─────────────────────────────────────────────────────────────
    //  2. 完整管线扫描：相机绕玩家一圈 + 俯仰
    // ─────────────────────────────────────────────────────────────
    static void case2_pipelineYawPitchSweep() {
        System.out.println("── 用例 2：复刻实体渲染管线，相机绕一圈 + 俯仰扫描 ──");
        System.out.println("   玩家 (0,64,0)，第三人称相机 = 眼睛 − 视线×4");

        float[] yaws = {0, 45, 90, 135, 180, 225, 270, 315};
        float[] pitches = {-60, -30, 0, 30, 60};
        float minW = 9, maxSpanX = 0, minSpanX = 9, maxSpanY = 0, minSpanY = 9;
        float maxAlignErr = 0, maxDepthErr = 0, minDepthGap = 9;

        for (float yaw : yaws) {
            for (float pitch : pitches) {
                Quaternionf rot = new Quaternionf().rotationYXZ(
                        (float) Math.toRadians(-yaw), (float) Math.toRadians(pitch), 0.0F);
                Vector3f look = new Vector3f(0, 0, 1).rotate(rot);
                Vector3f up = new Vector3f(0, 1, 0).rotate(rot);
                Vector3f playerPos = new Vector3f(0, 64, 0);
                Vector3f eye = new Vector3f(playerPos).add(0, 1.62F, 0);
                Vector3f camPos = new Vector3f(eye).sub(new Vector3f(look).mul(4.0F));

                Matrix4f proj = perspective(70.0F, 16.0F / 9.0F, 0.05F, 1000.0F);
                Matrix4f modelView = new Matrix4f();          // 实测：实体通道里是单位阵
                Matrix4f pose = newTransform(playerPos, camPos, look, up, modelView);

                Vector3f centre = toNdc(proj, modelView, pose.transformPosition(new Vector3f(0, 0, 0)));
                Vector3f leftP = toNdc(proj, modelView, pose.transformPosition(new Vector3f(-EDGE_MODEL, 0, 0)));
                Vector3f rightP = toNdc(proj, modelView, pose.transformPosition(new Vector3f(EDGE_MODEL, 0, 0)));
                Vector3f top = toNdc(proj, modelView, pose.transformPosition(new Vector3f(0, EDGE_MODEL, 0)));
                Vector3f bottom = toNdc(proj, modelView, pose.transformPosition(new Vector3f(0, -EDGE_MODEL, 0)));

                // 参考点：玩家头部（脚底 + HEIGHT）
                Vector3f head = new Vector3f(playerPos).add(0, HEIGHT, 0);
                Vector3f headNdc = toNdc(proj, modelView,
                        viewRotation(look, up).transformPosition(new Vector3f(head).sub(camPos)));

                float w = centre.z;
                minW = Math.min(minW, w);
                float spanX = Math.abs(rightP.x - leftP.x);
                float spanY = Math.abs(top.y - bottom.y);
                maxSpanX = Math.max(maxSpanX, spanX);
                minSpanX = Math.min(minSpanX, spanX);
                maxSpanY = Math.max(maxSpanY, spanY);
                minSpanY = Math.min(minSpanY, spanY);

                // 不变量 A：环与"头部那一点"在屏幕上几乎重合（残差只来自头部那点偏离镜头轴线 7cm）
                float alignErr = (float) Math.hypot(centre.x - headNdc.x, centre.y - headNdc.y);
                maxAlignErr = Math.max(maxAlignErr, alignErr);

                // 不变量 B：环恰好比头部那一点远 BACK 格
                float ringViewZ = pose.transformPosition(new Vector3f(0, 0, 0)).z;
                float headViewZ = viewZ(camPos, look, head);
                float gap = headViewZ - ringViewZ;
                minDepthGap = Math.min(minDepthGap, gap);
                maxDepthErr = Math.max(maxDepthErr, Math.abs(gap - BACK));

                if (w <= 0) fail(String.format("2a yaw=%.0f pitch=%.0f 环在相机后方 w=%.2f", yaw, pitch, w));
                if (Math.abs(centre.x) > 1 || Math.abs(centre.y) > 1)
                    fail(String.format("2b yaw=%.0f pitch=%.0f 环心出屏 NDC=(%.2f,%.2f)", yaw, pitch, centre.x, centre.y));
                if (alignErr > 0.01F)
                    fail(String.format("2c yaw=%.0f pitch=%.0f 环心与头部不重合 err=%.4f", yaw, pitch, alignErr));
                if (spanX < 0.25F || spanY < 0.25F)
                    fail(String.format("2d yaw=%.0f pitch=%.0f 环太小 span=(%.2f,%.2f)", yaw, pitch, spanX, spanY));
                // 不变量 C：管线真正生效的 M = ModelViewMat · pose，其 +Z 列必须是 (0,0,1)
                Matrix4f effective = new Matrix4f(modelView).mul(pose);
                Vector3f normal = new Vector3f(effective.m20(), effective.m21(), effective.m22()).normalize();
                if (normal.dot(new Vector3f(0, 0, 1)) < 0.999F)
                    fail(String.format("2e yaw=%.0f pitch=%.0f 板面没正对镜头 normal=(%.2f,%.2f,%.2f)",
                            yaw, pitch, normal.x, normal.y, normal.z));
            }
        }

        System.out.printf("   环心 与 头部那一点 的屏幕偏差 ≤ %.4f NDC（0.0035 ≈ 头部那点本身偏轴 7cm 造成）%n", maxAlignErr);
        System.out.printf("   环比头部那一点远 %.3f 格（期望 %.2f，误差 ≤ %.4f）%n", minDepthGap, BACK, maxDepthErr);
        System.out.printf("   可见环的屏幕占比：spanX ∈ [%.3f, %.3f]，spanY ∈ [%.3f, %.3f]%n",
                minSpanX, maxSpanX, minSpanY, maxSpanY);
        System.out.printf("   最小 w = %.3f（>0 = 在相机前方）%n", minW);
        pass("2f 全部 " + (yaws.length * pitches.length)
                + " 个相机姿态：环都在屏幕上、与头同屏位、比头远 " + BACK + " 格、板面正对镜头");
    }

    // ─────────────────────────────────────────────────────────────
    //  3. 对照组：旧版（平移列填 world − camPos）在相机转动时的表现
    // ─────────────────────────────────────────────────────────────
    static void case3_oldVersionControl() {
        System.out.println("── 用例 3：对照组 —— 旧版把 (world − camPos) 填进平移列 ──");
        System.out.println("   固定玩家，相机绕一圈，看环心 NDC 怎么变");

        float[] yaws = {0, 45, 90, 135, 180, 225, 270, 315};
        float oldMinX = 9, oldMaxX = -9, oldMinY = 9, oldMaxY = -9;
        float newMinX = 9, newMaxX = -9, newMinY = 9, newMaxY = -9;
        int oldOffScreen = 0, oldBehind = 0;

        for (float yaw : yaws) {
            Quaternionf rot = new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(-yaw), (float) Math.toRadians(20.0), 0.0F);
            Vector3f look = new Vector3f(0, 0, 1).rotate(rot);
            Vector3f up = new Vector3f(0, 1, 0).rotate(rot);
            Vector3f playerPos = new Vector3f(190.37F, -60.00F, 68.06F);
            Vector3f camPos = new Vector3f(playerPos).add(0, 1.62F, 0)
                    .sub(new Vector3f(look).mul(4.0F));
            Matrix4f proj = perspective(70.0F, 16.0F / 9.0F, 0.05F, 1000.0F);
            Matrix4f modelView = new Matrix4f();

            Vector3f old = oldBrokenTransform(playerPos, camPos, look, up)
                    .transformPosition(new Vector3f(0, 0, 0));
            Vector3f oldNdc = toNdc(proj, modelView, old);
            Vector3f fresh = newTransform(playerPos, camPos, look, up, modelView)
                    .transformPosition(new Vector3f(0, 0, 0));
            Vector3f newNdc = toNdc(proj, modelView, fresh);

            if (old.z >= 0) oldBehind++;
            if (Math.abs(oldNdc.x) > 1 || Math.abs(oldNdc.y) > 1) oldOffScreen++;
            oldMinX = Math.min(oldMinX, oldNdc.x); oldMaxX = Math.max(oldMaxX, oldNdc.x);
            oldMinY = Math.min(oldMinY, oldNdc.y); oldMaxY = Math.max(oldMaxY, oldNdc.y);
            newMinX = Math.min(newMinX, newNdc.x); newMaxX = Math.max(newMaxX, newNdc.x);
            newMinY = Math.min(newMinY, newNdc.y); newMaxY = Math.max(newMaxY, newNdc.y);

            System.out.printf("   yaw=%3.0f  旧版 NDC=(%6.2f,%6.2f) w=%6.2f | 新版 NDC=(%5.2f,%5.2f)%n",
                    yaw, oldNdc.x, oldNdc.y, old.z, newNdc.x, newNdc.y);
        }

        System.out.printf("   旧版 NDC.x ∈ [%.2f, %.2f]，y ∈ [%.2f, %.2f]；出屏 %d/8，落在相机后方 %d/8%n",
                oldMinX, oldMaxX, oldMinY, oldMaxY, oldOffScreen, oldBehind);
        System.out.printf("   新版 NDC.x ∈ [%.3f, %.3f]，y ∈ [%.3f, %.3f]（相机转一圈几乎不动）%n",
                newMinX, newMaxX, newMinY, newMaxY);
        if (oldOffScreen + oldBehind == 0)
            fail("3a 旧版在相机转一圈时始终正常 —— 与实机症状不符，探针有问题");
        else
            pass("3a 旧版随相机转动乱跑/出屏/掉到相机背后 —— 与实机「完全乱飞」一致");
        if (newMaxX - newMinX < 0.001F && newMaxY - newMinY < 0.001F)
            pass("3b 新版的屏位与相机朝向无关（转一圈 NDC 变化 < 0.001）");
        else
            fail(String.format("3b 新版屏位随相机漂移 x∈[%.3f,%.3f] y∈[%.3f,%.3f]",
                    newMinX, newMaxX, newMinY, newMaxY));
    }

    static float maxMax(float a, float b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    /** 旧版（当前仓库里那版）：三列 = 相机基，平移列 = 锚点 − 相机。 */
    static Matrix4f oldBrokenTransform(Vector3f playerPos, Vector3f camPos, Vector3f look, Vector3f up) {
        Vector3f right = new Vector3f(look).cross(up).normalize();
        Vector3f back = new Vector3f(look).negate();
        Vector3f anchor = new Vector3f(playerPos).add(0, HEIGHT, 0);
        Vector3f rel = new Vector3f(anchor).sub(camPos);
        float s = SCALE;
        return new Matrix4f().set(
                -right.x * s, up.x * s, back.x, 0,
                -right.y * s, up.y * s, back.y, 0,
                -right.z * s, up.z * s, back.z, 0,
                rel.x, rel.y, rel.z, 1);
    }

    // ─────────────────────────────────────────────────────────────
    //  4. ModelViewMat 的两种约定：pose = MV⁻¹ · M_view 在两种下都对
    // ─────────────────────────────────────────────────────────────
    static void case4_modelViewMatConventions() {
        System.out.println("── 用例 4：ModelViewMat = 单位阵 / 视图矩阵，两种约定下的等价性 ──");
        // 非退化姿态：yaw=35, pitch=20（正对着 −Z 那种会掩盖错误的姿态不用）
        Quaternionf rot = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-35.0), (float) Math.toRadians(20.0), 0.0F);
        Vector3f look = new Vector3f(0, 0, 1).rotate(rot);
        Vector3f up = new Vector3f(0, 1, 0).rotate(rot);
        Vector3f playerPos = new Vector3f(0, 64, 0);
        Vector3f camPos = new Vector3f(playerPos).add(0, 1.62F, 0).sub(new Vector3f(look).mul(4.0F));

        Matrix4f identityMv = new Matrix4f();
        Matrix4f viewMv = viewRotation(look, up).translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f poseA = newTransform(playerPos, camPos, look, up, identityMv);
        Matrix4f poseB = newTransform(playerPos, camPos, look, up, viewMv);
        Matrix4f effA = new Matrix4f(identityMv).mul(poseA);
        Matrix4f effB = new Matrix4f(viewMv).mul(poseB);
        System.out.printf("   MV=单位阵：pose 平移列 = (%.3f, %.3f, %.3f)（相机空间坐标）%n",
                poseA.m30(), poseA.m31(), poseA.m32());
        System.out.printf("   MV=视图矩阵：pose 平移列 = (%.3f, %.3f, %.3f) +Z 列 = (%.3f, %.3f, %.3f)%n",
                poseB.m30(), poseB.m31(), poseB.m32(), poseB.m20(), poseB.m21(), poseB.m22());
        Vector3f anchor = new Vector3f(playerPos).add(0, HEIGHT, 0).add(new Vector3f(look).mul(BACK));
        // 注意这里推翻了父任务的一条前提：如果 ModelViewMat 真的含相机**平移**，
        // 那么 pose 的平移列必须是**世界绝对坐标**（锚点本身）—— 因为 ModelViewMat
        // 自己会再减一次相机。写成"世界相对相机"反而会少减一次。
        checkVec("4a MV=视图矩阵时 pose 平移列 = 世界锚点（绝对坐标）",
                new Vector3f(poseB.m30(), poseB.m31(), poseB.m32()), anchor);
        checkVec("4a' MV=单位阵时 pose 平移列 = 同一锚点的相机空间坐标（实测就是这一支）",
                new Vector3f(poseA.m30(), poseA.m31(), poseA.m32()),
                viewRotation(look, up).transformPosition(new Vector3f(anchor).sub(camPos)));
        checkVec("4b MV=视图矩阵时 pose 的 +Z 列 = −look（指向相机）",
                new Vector3f(poseB.m20(), poseB.m21(), poseB.m22()), new Vector3f(look).negate());
        System.out.printf("   MV·pose 的最大元素差（两种约定）= %.6f%n", matrixDiff(effA, effB));
        if (matrixDiff(effA, effB) < 1e-4F) pass("4c 两种约定下管线真正用的 MV·pose 完全相同 —— 写法对约定不敏感");
        else fail("4c 两种约定给出的 MV·pose 不同");
    }

    // ─────────────────────────────────────────────────────────────
    //  5. 绕序：底那一遍开着背面剔除，"位置对但三角形朝后"= 看不见
    // ─────────────────────────────────────────────────────────────
    static void case5_facingAndWinding() {
        System.out.println("── 用例 5：板面朝向与三角形绕序（底那一遍开剔除） ──");
        Quaternionf rot = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-35.0), (float) Math.toRadians(20.0), 0.0F);
        Vector3f look = new Vector3f(0, 0, 1).rotate(rot);
        Vector3f up = new Vector3f(0, 1, 0).rotate(rot);
        Vector3f playerPos = new Vector3f(0, 64, 0);
        Vector3f camPos = new Vector3f(playerPos).add(0, 1.62F, 0).sub(new Vector3f(look).mul(4.0F));
        Matrix4f proj = perspective(70.0F, 16.0F / 9.0F, 0.05F, 1000.0F);
        Matrix4f modelView = new Matrix4f();
        Matrix4f pose = newTransform(playerPos, camPos, look, up, modelView);

        // 取扇形的一段：圆心 → 边缘A → 边缘B（几何里的第一条三角形）
        int sides = 48;
        float edge = 1.41421356F;
        float tA = 0.0F;
        float tB = (float) (2.0 * Math.PI / sides);
        Vector3f p0 = pose.transformPosition(new Vector3f(0, 0, 0));
        Vector3f p1 = pose.transformPosition(new Vector3f((float) Math.cos(tA) * edge, (float) Math.sin(tA) * edge, 0));
        Vector3f p2 = pose.transformPosition(new Vector3f((float) Math.cos(tB) * edge, (float) Math.sin(tB) * edge, 0));
        Vector3f n0 = toNdc(proj, modelView, p0);
        Vector3f n1 = toNdc(proj, modelView, p1);
        Vector3f n2 = toNdc(proj, modelView, p2);
        float signedArea = 0.5F * ((n1.x - n0.x) * (n2.y - n0.y) - (n2.x - n0.x) * (n1.y - n0.y));
        System.out.printf("   第一条三角形在 NDC 的有符号面积 = %.6f（>0 = 逆时针 = 正面）%n", signedArea);
        if (signedArea > 0) pass("5a 三角形是正面（底那一遍开着剔除也看得见）");
        else fail("5a 三角形是背面，会被剔除 —— 屏幕上一片空白");

        // 法线方向：模型 +Z 在相机空间里必须指向镜头（+Z）
        Vector3f normal = new Vector3f(pose.m20(), pose.m21(), pose.m22());
        checkVec("5b 模型 +Z → 相机空间 +Z（指向镜头）", normal, new Vector3f(0, 0, 1));
    }

    // ─────────────────────────────────────────────────────────────
    //  被测实现（与 Java 侧 transform() 一一对应）
    // ─────────────────────────────────────────────────────────────

    /** 新版：先在相机空间摆好（单位旋转 = 正对镜头），再用 ModelViewMat⁻¹ 抵消管线那一步。 */
    static Matrix4f newTransform(Vector3f playerPos, Vector3f camPos, Vector3f look, Vector3f up,
                                 Matrix4f modelView) {
        Vector3f right = new Vector3f(look).cross(up).normalize();
        Vector3f anchor = new Vector3f(playerPos)
                .add(new Vector3f(look).mul(BACK))     // 沿视线远离相机 —— 永远在玩家身后
                .add(0, HEIGHT, 0);                    // 世界 Y 固定高度
        Vector3f d = new Vector3f(anchor).sub(camPos);
        float vx = right.dot(d);
        float vy = up.dot(d);
        float vz = -look.dot(d);                       // 视图空间：前方为 −Z
        float s = SCALE / VISIBLE_R;
        Matrix4f mView = new Matrix4f().set(
                s, 0, 0, 0,
                0, s, 0, 0,
                0, 0, 1, 0,
                vx, vy, vz, 1);
        return new Matrix4f(modelView).invert().mul(mView);
    }

    /** MC 的相机基 → 视图旋转矩阵：**行** = right/up/back（视图坐标 = 三个点积）。 */
    static Matrix4f viewRotation(Vector3f look, Vector3f up) {
        Vector3f right = new Vector3f(look).cross(up).normalize();
        Vector3f upo = new Vector3f(right).cross(look).normalize();
        Vector3f back = new Vector3f(look).negate();
        // JOML 的 set() 按**列**吃参数（用例 0 已验证）。要"行 = right/up/back"，
        // 按列写出来就是下面这种交错形式。写成"列 = right/up/back"等于用了转置矩阵 ——
        // 用例 1 的正下方姿态分不出来，用例 1f~1h 的斜视角才能。
        return new Matrix4f().set(
                right.x, upo.x, back.x, 0,
                right.y, upo.y, back.y, 0,
                right.z, upo.z, back.z, 0,
                0, 0, 0, 1);
    }

    static float viewZ(Vector3f camPos, Vector3f look, Vector3f world) {
        Vector3f d = new Vector3f(world).sub(camPos);
        return -look.dot(d);
    }

    static Matrix4f perspective(float fovYDeg, float aspect, float near, float far) {
        return new Matrix4f().perspective((float) Math.toRadians(fovYDeg), aspect, near, far);
    }

    /** 视图空间点 → NDC；返回 (ndcX, ndcY, w)。 */
    static Vector3f toNdc(Matrix4f proj, Matrix4f modelView, Vector3f viewPos) {
        Vector4f clip = new Matrix4f(proj).mul(modelView).transform(new Vector4f(viewPos, 1.0F));
        if (Math.abs(clip.w) < 1e-6F) return new Vector3f(Float.NaN, Float.NaN, clip.w);
        return new Vector3f(clip.x / clip.w, clip.y / clip.w, clip.w);
    }

    // ─────────────────────────────────────────────────────────────
    //  断言工具
    // ─────────────────────────────────────────────────────────────
    static void checkVec(String name, Vector3f got, Vector3f want) {
        float err = new Vector3f(got).sub(want).length();
        if (err < 0.02F) pass(String.format("%s  got=(%.3f, %.3f, %.3f)", name, got.x, got.y, got.z));
        else fail(String.format("%s  got=(%.3f, %.3f, %.3f) want=(%.3f, %.3f, %.3f) err=%.4f",
                name, got.x, got.y, got.z, want.x, want.y, want.z, err));
    }

    static void pass(String msg) {
        System.out.println("   ✓ " + msg);
    }

    static void fail(String msg) {
        failures++;
        System.out.println("   ★ " + msg);
    }
}
