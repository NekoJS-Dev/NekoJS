package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.core.lifecycle.OnceRegistry;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;


/**
 * {@code once} / {@code clearOnce} 全局函数绑定（{@link ProxyExecutable}，直接以函数形式调用）。
 *
 * <p>{@code once(key, callback)}：首次使用该 key 时执行 callback 并记录标记，此后
 * （包括脚本 reload 后）同 key 调用直接跳过——标记存于 {@link OnceRegistry#SHARED}，
 * 进程内存活、不持久化。{@code clearOnce(key)} 移除单个标记，{@code clearOnce()} 清空全部，
 * 之后的 {@code once} 会重新执行。
 *
 * <p>v1 的 key 全局（无按脚本命名空间隔离），{@link OnceRegistry#resolveKey(String)}
 * 是后续接入脚本所有者前缀的唯一改动点。
 *
 * <pre>
 * once('myPack:migration', () => {
 *   // 只在进程首次加载（或 clearOnce('myPack:migration') 之后）执行一次
 * })
 * clearOnce('myPack:migration')  // 移除单个标记
 * clearOnce()                    // 清空全部标记
 * </pre>
 */
@Doc("Run-once guard: once(key, callback) executes the callback only the first time the key is used.")
@Doc("Markers deliberately survive script reloads (process-lifetime, in-memory); use clearOnce(key) to re-arm.")
public final class OnceJS implements ProxyExecutable {

    /** {@code once} 函数的进程级单例（标记表共享，实例无状态）。 */
    public static final OnceJS ONCE = new OnceJS();

    /** {@code clearOnce} 函数的进程级单例。 */
    public static final ClearOnceJS CLEAR_ONCE = new ClearOnceJS();

    private OnceJS() {}

    @Override
    public Object execute(Value... arguments) {
        // once(key, callback)
        if (arguments == null || arguments.length != 2) {
            throw new IllegalArgumentException("once(key, callback) expects exactly 2 arguments");
        }
        String key = arguments[0].isString() ? arguments[0].asString() : null;
        Value callback = arguments[1];
        if (key == null) {
            throw new IllegalArgumentException("once(key, callback): key must be a string");
        }
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("once(key, callback): callback must be a function");
        }
        // 原子 check-and-set：只有抢到“首次”的调用才执行回调
        if (!OnceRegistry.SHARED.runOnce(key)) {
            return null;
        }
        return callback.execute();
    }

    /** {@code clearOnce} 函数：无参清空全部标记，带 key 参数移除单个标记并返回它是否存在。 */
    @Doc("clearOnce(key) removes one run-once marker (returns whether it existed); clearOnce() clears all markers.")
    public static final class ClearOnceJS implements ProxyExecutable {

        private ClearOnceJS() {}

        @Override
        public Object execute(Value... arguments) {
            // clearOnce() -> 清空全部；clearOnce(key) -> 移除单个，返回标记是否原本存在
            if (arguments == null || arguments.length == 0) {
                OnceRegistry.SHARED.clearAll();
                return null;
            }
            if (arguments.length != 1 || !arguments[0].isString()) {
                throw new IllegalArgumentException("clearOnce() expects no arguments, or a single string key");
            }
            return OnceRegistry.SHARED.clear(arguments[0].asString());
        }
    }
}
