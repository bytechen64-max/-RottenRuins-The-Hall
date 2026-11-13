package org.bytechen.rottenruins.mexin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>MeXin</b> —— 简化版 Mixin：注解写在<b>钩子方法</b>上（不是类上），由 coremod
 * （{@link MeXinLuncher}）在类加载时把钩子调用注入到目标方法里。
 *
 * <p>钩子签名按顺序：{@code [目标实例（目标方法非静态时必须）] [目标方法的全部参数...]
 * [MeXinCall（仅 BODY）]}；钩子必须是 {@code public static}。返回值：BODY 与两个 MIX 模式为
 * {@code void}，EXCHANGE 与原方法相同。</p>
 *
 * <ul>
 *   <li><b>覆盖范围</b>：默认只改 {@code clazz}/{@code clazzname} 那一个类；{@code includextend}
 *       把子类里对同名方法的覆盖也算上；{@code includmy} 把目标换成<b>本模组总包</b>
 *       （{@code org.bytechen.rottenruins} 及全部子包）下所有类，此时钩子第一个参数要写公共父类型
 *       （最省事是 {@code Object}）。</li>
 *   <li><b>SRG</b>：正式环境原版方法名是 SRG（{@code m_6084_}），只写 {@code method="isAlive"}
 *       会"开发环境能中、正式环境不中" → 打原版方法务必补 {@code srg}。</li>
 *   <li><b>开关</b>：{@code -Dmexin.disable=true} 让 MeXin 完全不扫描不注入。</li>
 *   <li><b>额外扫描根</b>：{@code -Dmexin.scan.extra=路径;路径}，给 {@code includextend}
 *       补原版 jar 之类的父类索引（默认已自动扫 {@code <gamedir>/versions/<id>/<id>.jar}）。</li>
 *   <li><b>出错不动类</b>：注入抛异常时目标类原样放行；签名不符、abstract/native/合成/桥接方法、
 *       {@code <init>}/{@code <clinit>} 都只记日志并跳过。</li>
 *   <li><b>只发直线代码</b>：注入序列不含新跳转，故不重算 StackMapTable；钩子不能改控制流，
 *       要改返回值用 {@link MeXinCall}。</li>
 *   <li><b>类加载器</b>：钩子与目标类必须在同一个类加载器里；jarjar / 独立库加载的类不适合当目标。</li>
 *   <li><b>BODY 与基本类型</b>：返回值是基本类型时不要 {@code set(null)}，拆箱会 NPE。</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeXin {

    /** 目标类（编译期直接给 class，与 {@link #clazzname()} 二选一）。 */
    Class<?> clazz() default Object.class;

    /** 目标类名：内部名或点分全限定名都认。 */
    String clazzname() default "";

    /** 目标方法名（开发环境可读名）。 */
    String method();

    /** 生产环境的 SRG 名，如 {@code m_6084_}；留空表示不按 SRG 匹配。 */
    String srg() default "";

    /** 目标方法描述符（可选），同名重载时消歧，如 {@code (F)Z}。 */
    String desc() default "";

    /** 注入时机。 */
    MeXinType type() default MeXinType.MIX_BEFORE_ENTER;

    /** 是否也作用于子类里对同名方法的覆盖实现。 */
    boolean includextend() default false;

    /**
     * 总包注入：为 {@code true} 时，目标从"一个类"扩成<b>本模组的总包</b>
     * （{@code org.bytechen.rottenruins} 及其全部子包）—— 该包下所有类里同名的目标方法都会被改。
     *
     * <p>参差不齐的类共用一个钩子，所以钩子第一个参数要写成它们的公共父类型
     * （最省事是 {@code Object}）；注入器会按父类索引检查可赋值性，接不住就跳过并记日志。
     * 根包可用 {@code -Dmexin.rootPackage=a.b.c} 覆盖。</p>
     */
    boolean includmy() default false;
}
