package com.tkisor.nekojs.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.api.pack.PackAccess;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Activation-owned callbacks never cross contexts; only bounded JSON data crosses the bridge. */
final class PackCapabilityBridge implements PackAccess, ProxyObject {
    private final ScriptPack source;
    private final long activation;
    private final Context context;
    private final Function<String, PackCapabilityBridge> providers;
    private final Map<String, Service> services = new LinkedHashMap<>();
    private final Value parse;
    private final Value kind;
    private volatile boolean valid = true;
    private boolean sealed;

    PackCapabilityBridge(ScriptPack source, long activation, Context context,
                         Function<String, PackCapabilityBridge> providers) {
        this.source = source;
        this.activation = activation;
        this.context = context;
        this.providers = providers;
        parse = context.eval("js", "text => JSON.parse(text)");
        kind = context.eval("js", """
                (() => {
                    const prototype = Object.prototype, getPrototypeOf = Object.getPrototypeOf, isArray = Array.isArray;
                    return value => value === null ? 'null' : isArray(value) ? 'array'
                        : typeof value === 'object' && (getPrototypeOf(value) === prototype || getPrototypeOf(value) === null)
                            ? 'object' : typeof value === 'object' ? 'unsupported' : typeof value;
                })()
                """);
    }

    public String getSelector() { return source.selector(); }
    public long getActivation() { return activation; }

    public void provide(String name, Value callback) {
        assertLive();
        if (sealed || !source.manifest().descriptor().provides().contains(name) || !callback.canExecute()) {
            throw new IllegalStateException("[NEKO-1017] Capability publication rejected: undeclared, non-callable or outside initialization; " + name);
        }
        if (services.putIfAbsent(name, new Service(callback, ScriptContextRegistry.currentScriptIdOf(context))) != null) {
            throw new IllegalStateException("[NEKO-1017] Capability already provided: " + name);
        }
    }

    public Value require(String provider, String name) {
        assertLive();
        var declared = source.manifest().descriptor().requires().get(provider);
        if (declared == null || !declared.contains(name)) {
            throw new IllegalStateException("[NEKO-1017] Capability access was not declared: " + provider + "/" + name);
        }
        PackCapabilityBridge target = providers.apply(provider);
        if (target == null || !target.available(name)) {
            throw new IllegalStateException("[NEKO-1017] Capability is waiting for an available provider: " + provider + "/" + name);
        }
        return context.asValue((ProxyExecutable) arguments -> {
            assertLive();
            target.assertLive();
            if (arguments.length != 1) throw new IllegalArgumentException("[NEKO-1017] Capability calls require one JSON value");
            String request = json(arguments[0], 0, new int[]{0, 0}).toString();
            String response = target.invoke(name, request);
            return parse.execute(response);
        });
    }

    private String invoke(String name, String request) {
        synchronized (context) {
            assertLive();
            Service service = services.get(name);
            var type = ScriptContextRegistry.scriptTypeOf(context);
            String previous = ScriptContextRegistry.switchCurrentScriptId(context, service.scriptId());
            ScriptManager.noteCallbackEnter();
            try {
                return json(service.callback().execute(parse.execute(request)), 0, new int[]{0, 0}).toString();
            } catch (RuntimeException failure) {
                ScriptManager.reportContextKilled(context, failure);
                com.tkisor.nekojs.api.event.ScriptBindingSchema.diagnosticReporter(context).accept(
                        new com.tkisor.nekojs.api.event.ScriptBindingSchema.Diagnostic(type, "pack-capability:" + name, failure));
                throw failure;
            } finally {
                try { ScriptContextRegistry.restoreCurrentScriptId(context, previous); }
                finally { ScriptManager.noteCallbackExit(); }
            }
        }
    }

    boolean available(String name) { return valid && !ScriptManager.isContextDead(context) && services.containsKey(name); }
    boolean live() { return valid && !ScriptManager.isContextDead(context); }
    void seal() { sealed = true; }
    void invalidate() { valid = false; services.clear(); }
    Context contextForOwner() { return context; }

    private void assertLive() {
        if (!valid || ScriptManager.isContextDead(context)) {
            throw new IllegalStateException("[NEKO-1017] Capability handle belongs to an expired activation: " + source.selector() + "#" + activation);
        }
    }

    private JsonElement json(Value value, int depth, int[] count) {
        if (depth > 32 || ++count[0] > 10000) throw new IllegalArgumentException("[NEKO-1017] Capability JSON exceeds its structural limit");
        if (value.isHostObject() || value.canExecute()) throw new IllegalArgumentException("[NEKO-1017] Capability data contains a host object or function");
        String shape = kind.execute(value).asString();
        if (shape.equals("null")) return JsonNull.INSTANCE;
        if (shape.equals("undefined") || shape.equals("bigint") || shape.equals("symbol")) {
            throw new IllegalArgumentException("[NEKO-1017] Capability data must be JSON");
        }
        if (value.isBoolean()) return new JsonPrimitive(value.asBoolean());
        if (value.isString()) {
            String text = value.asString();
            count[1] += text.length();
            if (count[1] > 1048576) throw new IllegalArgumentException("[NEKO-1017] Capability strings exceed their total size limit");
            return new JsonPrimitive(text);
        }
        if (value.isNumber()) {
            double number = value.asDouble();
            if (!Double.isFinite(number)) throw new IllegalArgumentException("[NEKO-1017] Capability number must be finite");
            return new JsonPrimitive(number);
        }
        if (shape.equals("array")) {
            if (value.getArraySize() > 10000) throw new IllegalArgumentException("[NEKO-1017] Capability array exceeds its size limit");
            JsonArray result = new JsonArray();
            for (long i = 0; i < value.getArraySize(); i++) result.add(json(value.getArrayElement(i), depth + 1, count));
            return result;
        }
        if (shape.equals("object") && value.hasMembers()) {
            JsonObject result = new JsonObject();
            for (String key : value.getMemberKeys()) {
                count[1] += key.length();
                if (count[1] > 1048576) throw new IllegalArgumentException("[NEKO-1017] Capability JSON names and strings exceed their total size limit");
                result.add(key, json(value.getMember(key), depth + 1, count));
            }
            return result;
        }
        throw new IllegalArgumentException("[NEKO-1017] Capability data must be JSON");
    }

    public Object getMember(String key) {
        return switch (key) {
            case "selector" -> getSelector();
            case "activation" -> getActivation();
            case "provide" -> (ProxyExecutable) args -> {
                if (args.length != 2) throw new IllegalArgumentException("[NEKO-1017] Pack.provide requires a name and callback");
                provide(args[0].asString(), args[1]); return null;
            };
            case "require" -> (ProxyExecutable) args -> {
                if (args.length != 2) throw new IllegalArgumentException("[NEKO-1017] Pack.require requires a provider and name");
                return require(args[0].asString(), args[1].asString());
            };
            default -> null;
        };
    }

    public Object getMemberKeys() { return new String[]{"selector", "activation", "provide", "require"}; }
    public boolean hasMember(String key) { return java.util.Set.of("selector", "activation", "provide", "require").contains(key); }
    public void putMember(String key, Value value) { throw new UnsupportedOperationException("[NEKO-1017] Pack binding is read-only"); }
    private record Service(Value callback, String scriptId) {}
}
