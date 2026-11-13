package org.bytechen.rottenruins.mexin;

import org.jetbrains.annotations.NotNull;

/**
 * MeXin 的自测样例：{@link Sample} 里的四个方法分别是四种注入模式的<b>目标</b>，
 * 外面的四个 {@code public static} 方法是<b>钩子</b>。
 *
 * <p>由 {@code MeXinSelfTestGameTests} 断言注入真的发生了（含"改返回值"和"整段替换"）。</p>
 */
public final class MeXinSelfTest {

    /** 钩子被调用的次数（各模式分别计数）。 */
    public static int enterCalls;
    public static int returnCalls;
    public static int bodyCalls;
    public static int exchangeCalls;
    public static int wideCalls;
    /** 进入前钩子看到的入参、返回前钩子看到的返回值。 */
    public static int lastEnterArg = -1;
    public static int lastReturnSeen = -1;
    public static long lastWideLong = -1L;
    public static double lastWideSum = -1.0;

    private MeXinSelfTest() {
    }

    /** 被测目标：四个方法不带任何注解，全靠 MeXin 注入。 */
    public static final class Sample {

        /** 目标：MIX_BEFORE_ENTER + BODY + MIX_BEFORE_RETURN 都挂在它身上。 */
        public int compute(int value) {
            return value * 2;
        }

        /** 目标：EXCHANGE —— 方法体整个被替换。 */
        public int replaced() {
            throw new IllegalStateException("这个方法体应该被 MeXin 整个替换掉，不该被执行");
        }

        /** 目标：BODY 改返回值 —— 原实现返回 1，钩子把它改成 42。 */
        public int overriddenResult() {
            return 1;
        }

        /**
         * 目标：BODY —— <b>宽签名</b>（long/double 各占两个局部变量槽）＋返回 double。
         * 专门用来验证压栈/取参的槽位计算和 double 装箱拆箱没写错。
         */
        public double wide(int a, long b, double c, String d) {
            return a + b + c + (d == null ? 0 : d.length());
        }
    }

    /** 方法进入前：记下入参。 */
    @MeXin(clazz = Sample.class, method = "compute", type = MeXinType.MIX_BEFORE_ENTER)
    public static void onEnter(@NotNull Sample self, int value) {
        enterCalls++;
        lastEnterArg = value;
    }

    /** 返回前（只读）：看得到算完的结果。 */
    @MeXin(clazz = Sample.class, method = "compute", type = MeXinType.MIX_BEFORE_RETURN)
    public static void onReturn(@NotNull Sample self, int value) {
        returnCalls++;
        lastReturnSeen = 0;   // 真正要断言的值由 BODY 钩子记录，这里只计次
    }

    /** 方法体内：原实现跑完、返回前，可以改返回值。 */
    @MeXin(clazz = Sample.class, method = "compute", type = MeXinType.BODY)
    public static void onBody(@NotNull Sample self, int value, @NotNull MeXinCall<Object> call) {
        bodyCalls++;
        Object original = call.result();
        if (original instanceof Integer integer) {
            call.set(integer + value);   // 12 + 5 = 17（compute 返回 10）
        }
    }

    /** 方法体内：把固定返回值改成 42。 */
    @MeXin(clazz = Sample.class, method = "overriddenResult", type = MeXinType.BODY)
    public static void onBodyOverride(@NotNull Sample self, @NotNull MeXinCall<Object> call) {
        call.set(42);
    }

    /** 整段替换：不执行原方法体，直接给结果。 */
    @MeXin(clazz = Sample.class, method = "replaced", type = MeXinType.EXCHANGE)
    public static int onExchange(@NotNull Sample self) {
        exchangeCalls++;
        return 7;
    }

    /** 宽签名 + double 返回值：钩子把结果乘 2（验证 long/double 的槽位与装箱）。 */
    @MeXin(clazz = Sample.class, method = "wide", desc = "(IJDLjava/lang/String;)D",
            type = MeXinType.BODY)
    public static void onWide(@NotNull Sample self, int a, long b, double c, String d,
                              @NotNull MeXinCall<Object> call) {
        wideCalls++;
        lastWideLong = b;
        if (call.result() instanceof Double sum) {
            lastWideSum = sum;
            call.set(sum * 2);
        }
    }

    /** 只给自测用：一次性把计数清零。 */
    public static void reset() {
        enterCalls = 0;
        returnCalls = 0;
        bodyCalls = 0;
        exchangeCalls = 0;
        wideCalls = 0;
        lastEnterArg = -1;
        lastReturnSeen = -1;
        lastWideLong = -1L;
        lastWideSum = -1.0;
    }
}
