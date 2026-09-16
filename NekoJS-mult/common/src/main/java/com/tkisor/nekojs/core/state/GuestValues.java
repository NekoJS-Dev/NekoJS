package com.tkisor.nekojs.core.state;

import graal.graalvm.polyglot.Value;

/**
 * guest 值判定（票 10 guest 生命周期）。
 *
 * <p><b>判定依据（实证探针，ticket 10，GraalMC 25.1.3.7）</b>：脚本把对象/数组/函数存进
 * 宿主 Map 时，Graal 侧收到的是 Truffle 内部代理——{@code com.oracle.truffle.polyglot.PolyglotMap /
 * PolyglotList / PolyglotMapAndFunction}；宿主值（String/Integer/宿主对象）永远是宿主类。
 * 因此「值所属类是 Truffle 内部代理或 {@link Value}」是确定性的 guest 判定。
 *
 * <p><b>为什么不能用 {@code Value.asValue(v).getContext() != null}</b>：同一探针实证，在
 * <b>已进入</b>（entered）Context 的线程上调用时，该方法对宿主 String/Integer 也返回当前
 * Context——而视图的 put 正是在脚本求值线程上被 interop 调用的，用它会把所有脚本写入都
 * 误判为 guest（随 generation 失效清除，破坏「跨 reload 保留」）。
 *
 * <p>guest 值受 generation 生命周期约束（Context 销毁即失效，见
 * {@link GlobalStore#evictGuestOwned}），宿主值不受影响。判定发生在<b>写入时</b>（写入方
 * Context 存活期），失效清除只读写入时记下的标记——Context 关闭后绝不回头触碰残留代理
 * （探针实证：对已销毁 Context 的残留代理调 asValue 会抛错）。
 */
final class GuestValues {

    private GuestValues() {}

    /** v 是否绑定某个 guest Context（guest 函数 / guest 对象 / Value）。 */
    static boolean isGuest(Object value) {
        if (value == null) return false;
        if (value instanceof Value) return true;
        // Truffle 内部代理未被 relocation（relocation 只覆盖 graal.graalvm.polyglot API 包）；
        // 防御性一并接受另外两个前缀，避免未来升级 relocation 策略时静默漏判。
        String className = value.getClass().getName();
        return className.startsWith("com.oracle.truffle.polyglot.")
                || className.startsWith("org.graalvm.polyglot.")
                || className.startsWith("graal.graalvm.polyglot.proxy.");
    }
}
