package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.core.lifecycle.OnceRegistry;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;


/**
 * Binding for the global {@code once} / {@code clearOnce} functions ({@link ProxyExecutable},
 * called directly as functions).
 *
 * <p>{@code once(key, callback)} runs the callback the first time the key is used and records
 * a marker; later calls with the same key are skipped, including after a script reload.
 * Markers live in {@link OnceRegistry#SHARED} for the lifetime of the process and are not
 * persisted. {@code clearOnce(key)} removes one marker and {@code clearOnce()} clears all of
 * them, after which the next {@code once} for that key runs again.
 *
 * <p>Keys are global in v1, with no per-script namespace isolation;
 * {@link OnceRegistry#resolveKey(String)} is the single place to add a script-owner prefix
 * later.
 *
 * <pre>
 * once('myPack:migration', () => {
 *   // runs once on the first load of the process (or after clearOnce('myPack:migration'))
 * })
 * clearOnce('myPack:migration')  // remove one marker
 * clearOnce()                    // clear all markers
 * </pre>
 */
@Doc("Run-once guard: once(key, callback) executes the callback only the first time the key is used.")
@Doc("Markers deliberately survive script reloads (process-lifetime, in-memory); use clearOnce(key) to re-arm.")
public final class OnceJS implements ProxyExecutable {

    /** Process-level singleton for the {@code once} function (the marker table is shared; the
     * instance holds no state). */
    public static final OnceJS ONCE = new OnceJS();

    /** Process-level singleton for the {@code clearOnce} function. */
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
        // Atomic check-and-set: only the call that wins the "first time" runs the callback
        if (!OnceRegistry.SHARED.runOnce(key)) {
            return null;
        }
        return callback.execute();
    }

    /** The {@code clearOnce} function: called with no argument it clears all markers; called with
     * a key it removes that one marker and returns whether it existed. */
    @Doc("clearOnce(key) removes one run-once marker (returns whether it existed); clearOnce() clears all markers.")
    public static final class ClearOnceJS implements ProxyExecutable {

        private ClearOnceJS() {}

        @Override
        public Object execute(Value... arguments) {
            // clearOnce() -> clears everything; clearOnce(key) -> removes one entry and returns whether the
            // marker existed before
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
