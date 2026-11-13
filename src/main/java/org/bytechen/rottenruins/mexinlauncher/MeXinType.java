package org.bytechen.rottenruins.mexinlauncher;

/**
 * 注入时机 —— 钩子被调用的位置。
 *
 * <p>钩子签名规则见 {@link MeXin}。</p>
 */
public enum MeXinType {

    /** 方法<b>进入前</b>：原方法体第一条指令之前调用钩子，钩子返回 {@code void}。 */
    MIX_BEFORE_ENTER,

    /** 方法<b>返回前</b>：每个 {@code return} 之前调用钩子，钩子返回 {@code void}（只看不改）。 */
    MIX_BEFORE_RETURN,

    /**
     * <b>方法体内</b>：原方法体跑完、真正返回之前插入。
     * 钩子末尾多一个 {@link MeXinCall} 参数 —— 拿得到返回值，也改得动返回值。
     */
    BODY,

    /** <b>整段替换</b>：丢弃原方法体，直接返回钩子的返回值（类型必须与原方法一致）。 */
    EXCHANGE
}
