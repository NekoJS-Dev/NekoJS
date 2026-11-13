package com.tkisor.nekojs.core.module.cjs;

import java.util.List;

/**
 * Static analysis result for a CommonJS module.
 *
 * <p>{@link CjsStaticAnalyzer} produces it during preparation by scanning the source token by
 * token, and it serves:
 * <ul>
 *   <li>building the dependency graph (static literal require dependencies, used for reload
 *       invalidation and hot replacement);</li>
 *   <li>predicting the export shape for ESM/CJS interop (whole reassignment of module.exports vs
 *       member assignment through exports.xxx, and the {@code __esModule} interop marker);</li>
 *   <li>require.resolve checks and a future CJS record cache.</li>
 * </ul>
 *
 * <p>This is a conservative static approximation, not a full AST: dynamic require (non-literal
 * argument), export assignments inside conditional branches, and arrow function parameters that
 * shadow require are not modeled precisely (see {@link #shadowedRequire()}).
 */
public record CjsModuleRecord(
        /** Literal require dependencies, deduplicated in order; empty when {@code shadowedRequire} is set. */
        List<String> staticDependencies,
        /**
         * Set when {@code module.exports = ...} was detected: the whole object is reassigned,
         * which replaces the default export.
         */
        boolean assignsModuleExports,
        /** Set when {@code exports.xxx = ...} or {@code module.exports.xxx = ...} was detected (member assignment). */
        boolean assignsExportsMember,
        /**
         * Set when {@code exports.__esModule = true} or {@code module.exports.__esModule = true}
         * was detected (Babel/TS interop marker).
         */
        boolean hasEsmInteropMarker,
        /**
         * Set when require is shadowed by a local declaration or assignment (function parameter,
         * let/const/var, assignment), which makes the static dependencies unreliable.
         */
        boolean shadowedRequire
) {
    public static final CjsModuleRecord EMPTY = new CjsModuleRecord(List.of(), false, false, false, false);

    public CjsModuleRecord {
        staticDependencies = List.copyOf(staticDependencies);
    }
}
