package com.tkisor.nekojs.core.mixin;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyArray;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import java.util.Set;

/** Callback-scoped control. Expired views release every native reference. */
final class MixinInvocation implements ProxyObject {
    private static final Set<String> KEYS = Set.of("self", "args", "returnValue", "cancel");
    private final MixinPlan.Hook hook;
    private final ClassLoader loader;
    private Object self;
    private Object[] args;
    private Object value;
    private boolean live = true;
    private boolean valid;
    private boolean changed;
    private boolean poisoned;
    MixinInvocation(MixinPlan.Hook hook, ClassLoader loader, Object self, Object[] args, Object original) {
        this.hook = hook; this.loader = loader; this.self = self; this.args = args; this.value = original;
    }
    @Override public Object getMember(String key) {
        checkLive();
        return switch (key) {
            case "self" -> self;
            case "args" -> new ProxyArray() {
                public Object get(long index) { checkLive(); return args[Math.toIntExact(index)]; }
                public void set(long index, Value value) { throw reject("Arguments are read-only"); }
                public long getSize() { checkLive(); return args.length; }
            };
            case "returnValue" -> value;
            case "cancel" -> (ProxyExecutable) values -> {
                checkLive();
                if (values.length != 0 || !hook.cancellable() || !hook.at().equals("HEAD")
                        || !MixinPlan.Descriptor.parse(hook.descriptor()).result().equals("V")) {
                    throw reject("Only a cancellable void HEAD hook supports cancel()");
                }
                changed = true;
                return null;
            };
            default -> null;
        };
    }
    @Override public Object getMemberKeys() { return KEYS.toArray(String[]::new); }
    @Override public boolean hasMember(String key) { return KEYS.contains(key); }
    @Override public void putMember(String key, Value input) {
        checkLive();
        String result = MixinPlan.Descriptor.parse(hook.descriptor()).result();
        if (!"returnValue".equals(key) || !hook.cancellable() || result.equals("V")) throw reject("This hook cannot replace its return value");
        try { value = convert(input, result, loader); }
        catch (RuntimeException failure) { poisoned = true; throw failure; }
        changed = true;
    }
    void accepted() { valid = true; }
    MixinNativeBridge.Decision finish() {
        return valid && !poisoned && changed ? new MixinNativeBridge.Decision(true, value) : MixinNativeBridge.Decision.PASS;
    }
    void expire() { live = false; self = null; args = null; value = null; }
    private void checkLive() { if (!live) throw invalid("Mixin event has expired"); }
    private IllegalArgumentException reject(String message) { poisoned = true; return invalid(message); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("[NEKO-4092] " + message); }
    static Object convert(Value input, String descriptor, ClassLoader loader) {
        char kind = descriptor.charAt(0);
        if (kind == 'L' || kind == '[') {
            if (input.isNull()) return null;
            if (descriptor.equals("Ljava/lang/String;") && input.isString()) return input.asString();
            if (!input.isHostObject()) throw invalid("Reference returns require an assignable host value or null");
            Object host = input.asHostObject();
            try {
                String name = kind == '[' ? descriptor.replace('/', '.') : descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                if (!Class.forName(name, false, loader).isInstance(host)) throw invalid("Reference return is not assignable");
                return host;
            } catch (ClassNotFoundException failure) { throw invalid("Return class is unavailable"); }
        }
        if (kind == 'Z') { if (!input.isBoolean()) throw invalid("Boolean return required"); return input.asBoolean(); }
        if (kind == 'C') {
            if (!input.isString() || input.asString().length() != 1) throw invalid("Char requires one UTF-16 code unit");
            return input.asString().charAt(0);
        }
        if (!input.isNumber() || !input.fitsInDouble()) throw invalid("Numeric return required");
        double number = input.asDouble();
        if (!Double.isFinite(number)) throw invalid("Finite numeric return required");
        return switch (kind) {
            case 'B' -> { integer(number, Byte.MIN_VALUE, Byte.MAX_VALUE); yield (byte) number; }
            case 'S' -> { integer(number, Short.MIN_VALUE, Short.MAX_VALUE); yield (short) number; }
            case 'I' -> { integer(number, Integer.MIN_VALUE, Integer.MAX_VALUE); yield (int) number; }
            case 'F' -> { if (!Float.isFinite((float) number)) throw invalid("Float overflow"); yield (float) number; }
            case 'D' -> number;
            default -> throw invalid("Unsupported return descriptor");
        };
    }
    private static void integer(double value, double min, double max) {
        if (value < min || value > max || value != Math.rint(value)) throw invalid("Integral return is out of range");
    }
}
