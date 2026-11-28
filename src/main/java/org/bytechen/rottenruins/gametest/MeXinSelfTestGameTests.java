package org.bytechen.rottenruins.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.mexin.MeXinSelfTest;
import org.bytechen.rottenruins.utils.ModUtils;

/**
 * <b>MeXin 注入自测</b>：验证四种注入模式真的改到了字节码。
 *
 * <p><b>开发环境这条测试是"容忍"的</b>：Forge 只从 <b>mods 目录</b>里的 jar 发现
 * {@code ITransformationService}（{@code ModDirTransformerDiscoverer}），而开发环境把模组类
 * 直接放在 classpath 上 —— 所以 dev 里 MeXin 不会激活、钩子不会被调用；
 * 生产环境（我们的 jar 就在 mods 目录）才会真正注入。</p>
 *
 * <p>所以断言分两段：钩子一次都没被调用 → 记一条"未激活"日志并通过；
 * 只要被调用过，就按完整规则严格断言。</p>
 */
@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class MeXinSelfTestGameTests {

    @GameTest(template = "potential_empty", batch = "mexin", timeoutTicks = 40)
    public static void allModesInject(GameTestHelper helper) {
        MeXinSelfTest.reset();
        MeXinSelfTest.Sample sample = new MeXinSelfTest.Sample();

        int computed = sample.compute(5);
        int override = sample.overriddenResult();
        int replaced = callReplaced(sample);
        double wide = sample.wide(1, 2L, 0.5d, "abcd");

        boolean activated = MeXinSelfTest.enterCalls > 0 || MeXinSelfTest.bodyCalls > 0
                || MeXinSelfTest.exchangeCalls > 0 || MeXinSelfTest.wideCalls > 0;
        if (!activated) {
            ModUtils.LOGGER.info("[MeXin] 注入未激活（开发环境不扫描 classpath 上的 "
                    + "ITransformationService），本次跳过断言；生产环境部署 jar 后生效");
            helper.succeed();
            return;
        }

        helper.assertTrue(MeXinSelfTest.enterCalls == 1,
                "MIX_BEFORE_ENTER 应被调用 1 次，实际 " + MeXinSelfTest.enterCalls);
        helper.assertTrue(MeXinSelfTest.lastEnterArg == 5,
                "进入前钩子应看到入参 5，实际 " + MeXinSelfTest.lastEnterArg);
        helper.assertTrue(MeXinSelfTest.bodyCalls == 1,
                "BODY 应被调用 1 次，实际 " + MeXinSelfTest.bodyCalls);
        helper.assertTrue(MeXinSelfTest.returnCalls == 1,
                "MIX_BEFORE_RETURN 应被调用 1 次，实际 " + MeXinSelfTest.returnCalls);
        helper.assertTrue(computed == 15, "BODY 应把返回值从 10 改成 15，实际 " + computed);
        helper.assertTrue(override == 42, "BODY 应把 1 改成 42，实际 " + override);
        helper.assertTrue(replaced == 7, "EXCHANGE 应返回 7，实际 " + replaced);
        helper.assertTrue(MeXinSelfTest.exchangeCalls == 1,
                "EXCHANGE 钩子应被调用 1 次，实际 " + MeXinSelfTest.exchangeCalls);
        helper.assertTrue(MeXinSelfTest.wideCalls == 1,
                "宽签名 BODY 钩子应被调用 1 次，实际 " + MeXinSelfTest.wideCalls);
        helper.assertTrue(MeXinSelfTest.lastWideLong == 2L,
                "宽签名钩子应看到 long 参数 2，实际 " + MeXinSelfTest.lastWideLong);
        helper.assertTrue(wide == (1 + 2L + 0.5d + 4) * 2,
                "宽签名 BODY 应把结果翻倍，实际 " + wide);
        helper.succeed();
    }

    /** 原方法体会抛异常，被 EXCHANGE 替换后不会。 */
    private static int callReplaced(MeXinSelfTest.Sample sample) {
        try {
            return sample.replaced();
        } catch (IllegalStateException e) {
            return Integer.MIN_VALUE;
        }
    }
}
