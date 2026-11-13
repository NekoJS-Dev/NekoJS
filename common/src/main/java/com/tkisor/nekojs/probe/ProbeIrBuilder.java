package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.events.GlobalDecl;
import com.tkisor.nekojs.probe.events.ProbeAddGlobalEventJS;
import com.tkisor.nekojs.probe.events.ProbeAssignTypeEventJS;
import com.tkisor.nekojs.probe.events.ProbeEvents;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.probe.events.ProbeSnippetEventJS;
import com.tkisor.nekojs.probe.events.Snippet;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Builds the shared IR and fires the probe.* override events. This is the package-private
 * component that {@link ProbeCoordinator} calls; it is stateless and fully static.
 *
 * <p>Fixed order: apply {@code probe.assign_type} (global type redirection, which marks the
 * affected classes mutated), then fire {@code probe.modify_type} (parameter-level edits). A slot
 * that {@code modify_type} sets explicitly is not overwritten a second time by
 * {@code assign_type} ({@code ProbeCoordinatorHardeningTest#assignTypeAppliedBeforeModifyTypeExplicitEdit}
 * guards this).
 */
final class ProbeIrBuilder {

    private ProbeIrBuilder() {
    }

    /**
     * Builds the shared IR by reflecting every collected class with TypeReflector, then applies
     * {@code probe.assign_type} (global type redirection, which marks the affected classes
     * mutated) and fires {@code probe.modify_type} (parameter-level edits). Classes that fail to
     * reflect are skipped. Returns an immutable IR list that already contains the edits. When
     * {@code assignMap} is empty the apply step is skipped. When an event listener throws, the
     * build continues (best-effort) but writes the risk of a partial application into
     * {@code warnings} for the caller.
     *
     * <p>Reflection runs in parallel (one task per class on the shared pool). The results are then
     * collected into a LinkedHashMap in the original collection order (BFS order), so
     * {@code List.copyOf(ir.values())} keeps the same order as the serial version. assign_type
     * and modify_type still run serially once the map is built, so event firing order is unchanged.
     */
    static List<TypeDecl> buildAndMutateIr(Collection<Class<?>> collected, Map<String, ApiTypeRef> assignMap,
                                            ExecutorService pool, List<String> warnings) {
        List<Class<?>> ordered = new ArrayList<>(collected);
        if (ordered.isEmpty()) return List.of();

        Map<String, TypeDecl> ir = new LinkedHashMap<>();
        {
            // One reflection task per class. TypeReflector has no instance state, so each task
            // creates its own instance and stays independent within its thread.
            List<Future<TypeDecl>> futures = new ArrayList<>(ordered.size());
            for (Class<?> c : ordered) {
                futures.add(pool.submit(() -> new TypeReflector().reflect(c)));
            }
            // Collect in the original collection order: LinkedHashMap insertion order is the
            // collectClasses BFS order, so the later List.copyOf is stable.
            for (int i = 0; i < futures.size(); i++) {
                String fqn = ordered.get(i).getName();
                try {
                    ir.put(fqn, futures.get(i).get());
                } catch (InterruptedException e) {
                    // An interrupt must not be swallowed as a reflection failure: restore the
                    // interrupt flag and rethrow so the probe stops at once. Carrying a half-built
                    // IR on into rendering would commit incomplete artifacts to disk.
                    Thread.currentThread().interrupt();
                    futures.forEach(pending -> pending.cancel(true));
                    throw new IllegalStateException("Interrupted while building shared probe IR", e);
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    if (cause instanceof VirtualMachineError || cause instanceof ThreadDeath) {
                        futures.forEach(pending -> pending.cancel(true));
                        throw (Error) cause;
                    }
                    String warning = "[NEKO-4029] Probe type reflection failed for " + fqn
                            + "; its shared declaration was omitted (" + cause + ")";
                    warnings.add(warning);
                    com.tkisor.nekojs.core.error.Diagnostics.report("probe-ir", com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                            warning, cause);
                }
            }
        }
        // 1. assign_type: redirect the reflected types first and mark the affected classes
        // mutated, so TS renders them again.
        if (assignMap != null && !assignMap.isEmpty()) {
            for (TypeDecl d : ir.values()) ProbeAssignTypeEventJS.applyTo(d, assignMap);
        }
        // 2. modify_type: parameter-level edits, applied after assign. A type that modify_type sets
        // explicitly is not overwritten a second time by assign.
        if (!ir.isEmpty() && ProbeEvents.MODIFY_TYPE.hasListeners()) {
            try {
                ProbeEvents.MODIFY_TYPE.post(new ProbeModifyTypeEventJS(ir));
            } catch (Throwable t) {
                NekoJS.LOGGER.error("probe.modify_type event threw; edits may be incomplete", t);
                warnings.add("probe.modify_type event threw; edits may be incomplete (" + t + ")");
            }
        }
        return List.copyOf(ir.values());
    }

    /**
     * Fires {@code probe.assign_type} once and returns the collected mapping from Java FQN to
     * custom type.
     */
    static Map<String, ApiTypeRef> fireAssignType(List<String> warnings) {
        ProbeAssignTypeEventJS ev = new ProbeAssignTypeEventJS();
        try {
            ProbeEvents.ASSIGN_TYPE.post(ev);
        } catch (Throwable t) {
            NekoJS.LOGGER.error("probe.assign_type event threw; assignments may be incomplete", t);
            warnings.add("probe.assign_type event threw; assignments may be incomplete (" + t + ")");
        }
        return ev.assignments();
    }

    /**
     * Fires {@code probe.add_global} and {@code probe.snippets}, each only when it has listeners,
     * and returns the combined result.
     */
    static ProbeOverrides fireOverrides(List<String> warnings) {
        List<GlobalDecl> globals = List.of();
        List<Snippet> snippets = List.of();
        if (ProbeEvents.ADD_GLOBAL.hasListeners()) {
            ProbeAddGlobalEventJS ev = new ProbeAddGlobalEventJS();
            try {
                ProbeEvents.ADD_GLOBAL.post(ev);
            } catch (Throwable t) {
                NekoJS.LOGGER.error("probe.add_global event threw", t);
                warnings.add("probe.add_global event threw (" + t + ")");
            }
            globals = ev.globals();
        }
        if (ProbeEvents.SNIPPETS.hasListeners()) {
            ProbeSnippetEventJS ev = new ProbeSnippetEventJS();
            try {
                ProbeEvents.SNIPPETS.post(ev);
            } catch (Throwable t) {
                NekoJS.LOGGER.error("probe.snippets event threw", t);
                warnings.add("probe.snippets event threw (" + t + ")");
            }
            snippets = ev.snippets();
        }
        return new ProbeOverrides(globals, snippets);
    }
}
