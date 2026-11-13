package org.bytechen.rottenruins.mexin;

/**
 * {@link MeXinType#BODY} 钩子的"可取消返回值"上下文。
 *
 * <p>进场时 {@link #result()} 里装的是<b>原实现的返回值</b>（基本类型会自动装箱）。
 * 钩子什么都不做 → 返回值保持原样；{@link #set(Object)} / {@link #cancel(Object)}
 * → 换成新值。{@link MeXinType#MIX_BEFORE_ENTER} / {@link MeXinType#MIX_BEFORE_RETURN}
 * 的钩子拿不到它（只读观察）。</p>
 *
 * <p>这个类的内部名/描述符被 {@code MeXinTransformer} 以字符串形式写死用于发射字节码，
 * 改名时必须同步改那边（{@code MeXinTransformer#CALL_DESC}）。</p>
 *
 * @param <T> 原方法的返回类型（基本类型为装箱类型）
 */
public final class MeXinCall<T> {

    private boolean canceled;
    private T result;

    public MeXinCall(T result) {
        this.result = result;
    }

    /** 取消这次调用（不改值：按返回类型的默认值返回）。 */
    public void cancel() {
        this.canceled = true;
    }

    /** 取消这次调用并把返回值换成 {@code value}。 */
    public void cancel(T value) {
        this.canceled = true;
        this.result = value;
    }

    public boolean canceled() {
        return canceled;
    }

    public T result() {
        return result;
    }

    /** 只换值，不显式取消。 */
    public void set(T value) {
        this.result = value;
    }

    @Override
    public String toString() {
        return "MeXinCall{canceled=" + canceled + ", result=" + result + "}";
    }
}
