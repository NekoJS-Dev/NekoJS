package com.tkisor.nekojs.core.mixin;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.module.NekoTrustApprovedSource;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import java.util.*;
import java.util.function.Predicate;

/** Root-owned hook listeners, sharing the existing event generation commit boundary. */
public final class MixinRuntime implements AutoCloseable, MixinNativeBridge.Sink {
    public interface ThreadGate { boolean allows(String thread); }
    private record Slot(MixinPlan plan, MixinPlan.Hook hook, HookBus bus) {}
    private final Map<String, Slot> slots = new java.util.concurrent.ConcurrentHashMap<>();
    private final NekoTrustContext trust;
    private final ClassLoader loader;
    private final ThreadLocal<Set<String>> entered = ThreadLocal.withInitial(HashSet::new);
    private final Set<String> diagnostics = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile ThreadGate gate = thread -> false;
    private MixinNativeBridge.Lease lease;
    private volatile boolean closed;
    public MixinRuntime(NekoTrustContext trust, ClassLoader loader) { this.trust = trust; this.loader = loader; }

    /** Assembly calls this only with plans discovered from installed loader carriers. */
    public synchronized void install(List<MixinPlan> plans, MixinPlan.Profile profile, ThreadGate threadGate) {
        if (closed || lease != null || !slots.isEmpty()) throw MixinPlan.invalid("Mixin runtime already installed or closed");
        Map<String, Slot> candidate = new LinkedHashMap<>();
        for (MixinPlan plan : plans) {
            if (!profile.equals(plan.profile())) throw MixinPlan.invalid("Carrier profile mismatch: " + plan.hash());
            for (var hook : plan.hooks()) {
                if (candidate.putIfAbsent(hook.id(), new Slot(plan, hook, new HookBus())) != null) throw MixinPlan.invalid("Duplicate installed hook: " + hook.id());
            }
        }
        gate = Objects.requireNonNull(threadGate);
        slots.putAll(candidate);
        try { if (!candidate.isEmpty()) lease = MixinNativeBridge.attach(this); }
        catch (RuntimeException failure) { slots.clear(); gate = thread -> false; throw failure; }
    }
    @Override public boolean enabled(String plan, String id) {
        Slot slot = slots.get(id);
        if (closed || slot == null || !slot.plan.hash().equals(plan) || !slot.bus.hasListeners()
                || ScriptManager.isAnyCandidatePreparing() || entered.get().contains(id)) return false;
        if (!gate.allows(slot.hook.thread())) { diagnose(id, "wrong-thread", null); return false; }
        return true;
    }
    @Override public MixinNativeBridge.Decision invoke(String plan, String id, Object self, Object[] args, Object original) {
        if (!enabled(plan, id)) return MixinNativeBridge.Decision.PASS;
        Slot slot = slots.get(id);
        var active = entered.get();
        active.add(id);
        MixinInvocation invocation = new MixinInvocation(slot.hook, loader, self, args, original);
        try {
            slot.bus.post(invocation);
            return invocation.finish();
        } finally {
            invocation.expire();
            active.remove(id);
            if (active.isEmpty()) entered.remove();
        }
    }
    private void bind(Value bindings, ScriptType type) {
        if (type != ScriptType.SERVER && type != ScriptType.CLIENT) return;
        Context context = bindings.getContext();
        Value invoke = context.eval("js", "(callback, event) => { const result = callback(event); if (result !== undefined) throw new Error('[NEKO-4092] Mixin callbacks must return undefined synchronously'); }");
        bindings.putMember("MixinEvents", ProxyObject.fromMap(Map.of("inject", (ProxyExecutable) args -> {
            if (args.length != 2 || !args[0].isString() || !args[1].canExecute()) throw MixinPlan.invalid("Expected inject(id, callback)");
            Slot slot = slots.get(args[0].asString());
            if (slot == null || !slot.hook.scriptType().equals(type.name())) throw MixinPlan.invalid("Unknown or unavailable installed hook");
            var source = ScriptManager.mixinCandidateSource(context);
            var approval = source == null ? null : trust.approvalFor(source);
            if (approval == null || approval.kind() != NekoTrustApprovedSource.Kind.LOCAL_TRUSTED || !approval.covers(source)) {
                throw new IllegalArgumentException("[NEKO-4091] Mixin callbacks require a local trusted candidate entry");
            }
            Value callback = args[1];
            var location = callback.getSourceLocation();
            java.nio.file.Path callbackSource = null;
            try {
                if (location != null && "file".equals(location.getSource().getURI().getScheme())) {
                    callbackSource = java.nio.file.Path.of(location.getSource().getURI()).toRealPath();
                }
            } catch (java.io.IOException | IllegalArgumentException unavailable) {
                // Unknown or virtual provenance is not sufficient for native hook authority.
            }
            var callbackApproval = callbackSource == null ? null : trust.approvalFor(callbackSource);
            if (callbackApproval == null || callbackApproval.kind() != NekoTrustApprovedSource.Kind.LOCAL_TRUSTED
                    || !callbackApproval.covers(callbackSource)) {
                throw new IllegalArgumentException("[NEKO-4091] Mixin callback origin must resolve to a locally trusted file");
            }
            ProxyExecutable execute = values -> {
                MixinInvocation event = values[0].asProxyObject();
                try { invoke.execute(callback, event); event.accepted(); }
                catch (RuntimeException failure) { diagnose(slot.hook.id(), "callback-rejected", failure); }
                return null;
            };
            slot.bus.execute(context.asValue(execute));
            return null;
        })));
    }
    public ScriptEventBridge wrap(ScriptEventBridge delegate) {
        return new ScriptEventBridge() {
            public boolean requiresTransactionalLoad(ScriptType type) {
                return delegate.requiresTransactionalLoad(type) || slots.values().stream().anyMatch(slot -> slot.hook.scriptType().equals(type.name()));
            }
            public void bindEvents(Value bindings, ScriptType type) { delegate.bindEvents(bindings, type); bind(bindings, type); }
            public void clearListeners(ScriptType type) { delegate.clearListeners(type); slots.values().forEach(s -> s.bus.clearTokens(type)); }
            public void clearListeners(ScriptType type, String id) { delegate.clearListeners(type, id); slots.values().forEach(s -> s.bus.clearTokens(type, id)); }
            public void clearListenersByPrefix(ScriptType type, String prefix) { delegate.clearListenersByPrefix(type, prefix); slots.values().forEach(s -> s.bus.clearTokensByPrefix(type, prefix)); }
            public com.tkisor.nekojs.api.event.ScriptEventRegistrar scriptEventRegistrar() { return delegate.scriptEventRegistrar(); }
            public ListenerBatch prepareCandidateListeners(ScriptType type, List<EventBusJS.PendingListener> pending) {
                return prepare(delegate.prepareCandidateListeners(type, pending), type, pending, c -> true);
            }
            public ListenerBatch preparePackListeners(ScriptType type, List<EventBusJS.PendingListener> pending, Set<Context> replaced) {
                return prepare(delegate.preparePackListeners(type, pending, replaced), type, pending, replaced::contains);
            }
        };
    }
    private ScriptEventBridge.ListenerBatch prepare(ScriptEventBridge.ListenerBatch delegate, ScriptType type,
            List<EventBusJS.PendingListener> pending, Predicate<Context> replaced) {
        var commits = new ArrayList<EventBusJS.PendingCommit>();
        try { for (Slot slot : slots.values()) commits.add(slot.bus.preparePendingListeners(type, pending, replaced)); }
        catch (Throwable failure) { for (int i = commits.size() - 1; i >= 0; i--) commits.get(i).rollback(); delegate.rollback(); throw failure; }
        return new ScriptEventBridge.ListenerBatch() {
            public void finish() { delegate.finish(); commits.forEach(EventBusJS.PendingCommit::finish); }
            public void rollback() { for (int i = commits.size() - 1; i >= 0; i--) commits.get(i).rollback(); delegate.rollback(); }
        };
    }
    private void diagnose(String id, String outcome, Throwable failure) {
        if (diagnostics.add(id + ":" + outcome)) com.tkisor.nekojs.NekoJS.LOGGER.warn(
                "[NEKO-4092] Mixin callback bypassed; hook={} outcome={}", id, outcome, failure);
    }
    /** World teardown revokes native callback values without removing process-lifetime injection slots. */
    public void clearCallbacks() {
        for (Slot slot : slots.values()) for (ScriptType type : ScriptType.all()) slot.bus.clearTokens(type);
    }
    @Override public synchronized void close() {
        closed = true;
        if (lease != null) { lease.close(); lease = null; }
        clearCallbacks();
        slots.clear(); gate = thread -> false; entered.remove(); diagnostics.clear();
    }
    private static final class HookBus extends EventBusJS<MixinInvocation, Void> {
        HookBus() { super(EventBus.create(MixinInvocation.class)); }
        @Override public PendingCommit preparePendingListeners(ScriptType type, List<PendingListener> pending, Predicate<Context> replaced) {
            var candidates = pending.stream().filter(p -> p.owner() == this && p.type() == type).toList();
            if (candidates.stream().anyMatch(p -> !ScriptManager.mixinCandidateOnlyLocal(p.listenerValue().getContext()))) {
                throw new IllegalArgumentException("[NEKO-4091] Mixin candidate loaded a remote dependency after hook registration");
            }
            long count = retainedListenerCount(type, replaced) + candidates.size();
            if (count > 1) throw MixinPlan.invalid("A hook accepts exactly one callback per script type");
            return super.preparePendingListeners(type, pending, replaced);
        }
    }
}
