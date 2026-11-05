//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Exposes host operations through proxies; node handles never expose Java objects to bundled code. */
final class BuiltinUiHostBridge {
    private final Map<ProxyObject, Object> handles = new IdentityHashMap<>();
    private BuiltinUiHostBridge() { }

    static ProxyObject host(JsxHostAdapter adapter) {
        BuiltinUiHostBridge bridge = new BuiltinUiHostBridge();
        return ProxyObject.fromMap(Map.of(
                "isOwnerThread", (ProxyExecutable) args -> adapter.isOwnerThread(),
                "enqueue", (ProxyExecutable) args -> adapter.enqueue(args[0]),
                "viewport", (ProxyExecutable) args -> ProxyObject.fromMap(adapter.viewport()),
                "supportsPrimitive", (ProxyExecutable) args -> adapter.supportsPrimitive(args[0].asString()),
                "measureText", (ProxyExecutable) args -> ProxyObject.fromMap(adapter.measureText(args[0].asString(),
                        args[1].asDouble(), (int) Math.floor(args[2].asDouble()),
                        args.length > 3 && !args[3].isNull() ? args[3].asString() : null)),
                "layout", (ProxyExecutable) args -> {
                    adapter.layout(args[0], args[1], args[2], args.length > 3 && args[3].asBoolean()); return null;
                },
                "begin", (ProxyExecutable) args -> bridge.transaction(adapter.begin()),
                "reportDiagnostic", (ProxyExecutable) args -> { adapter.reportDiagnostic(args[0]); return null; }));
    }

    private ProxyObject transaction(JsxHostAdapter.JsxHostTransaction transaction) {
        List<ProxyObject> created = new ArrayList<>();
        List<ProxyObject> removed = new ArrayList<>();
        return ProxyObject.fromMap(Map.of(
                "create", (ProxyExecutable) args -> {
                    Object node = transaction.create(args[0].asString(), nullableText(args[1]), args[2]);
                    ProxyObject handle = ProxyObject.fromMap(Map.of());
                    handles.put(handle, node); created.add(handle); return handle;
                },
                "update", (ProxyExecutable) args -> {
                    transaction.update(resolve(args[0]), args[1].asString(), nullableText(args[2]), args[3]); return null;
                },
                "order", (ProxyExecutable) args -> { transaction.order(resolve(args[0]), resolveArray(args[1])); return null; },
                "remove", (ProxyExecutable) args -> {
                    transaction.remove(resolve(args[0])); removed.add(args[0].asProxyObject()); return null;
                },
                "commit", (ProxyExecutable) args -> {
                    transaction.commit(resolveArray(args[0])); removed.forEach(handles::remove); return null;
                },
                "rollback", (ProxyExecutable) args -> {
                    try { transaction.rollback(); } finally { created.forEach(handles::remove); } return null;
                }));
    }

    private Object resolve(Value value) {
        if (value.isNull()) return null;
        if (!value.isProxyObject() || !handles.containsKey(value.asProxyObject())) {
            throw new IllegalArgumentException("[NEKO-7015] Built-in UI supplied an unknown host handle");
        }
        return handles.get(value.asProxyObject());
    }

    private List<Object> resolveArray(Value array) {
        List<Object> nodes = new ArrayList<>();
        for (long index = 0; index < array.getArraySize(); index++) nodes.add(resolve(array.getArrayElement(index)));
        return nodes;
    }

    static String nullableText(Value value) { return value.isNull() ? null : value.asString(); }
}
//?}
