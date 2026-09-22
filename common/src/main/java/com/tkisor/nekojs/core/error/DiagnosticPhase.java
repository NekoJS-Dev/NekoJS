package com.tkisor.nekojs.core.error;

/**
 * Diagnostic phase of a script failure (ticket 30 frozen record).
 *
 * <p>One failure is classified into exactly one phase at record time. The phase names the
 * pipeline stage the failure belongs to, which is independent of the reload phase the failure
 * was observed in (see {@link com.tkisor.nekojs.core.lifecycle.ReloadPhase} for the reload
 * transaction stages).
 *
 * <ul>
 *   <li>{@link #PREPARE} — syntax/transform failures raised by Script Preparation
 *       (including guest syntax errors that only surface at eval time);</li>
 *   <li>{@link #RESOLVE_LINK} — module path resolution and ESM link failures
 *       (Module Resolution/Cache);</li>
 *   <li>{@link #CACHE} — failures of the prepared cache itself, such as source
 *       stamping/snapshot IO (Module Resolution/Cache);</li>
 *   <li>{@link #EXECUTION} — runtime failures of evaluated modules, entry scripts and
 *       event/timer callbacks (Script Execution Environment);</li>
 *   <li>{@link #TRUST} — pack trust authorization denials (Pack Trust);</li>
 *   <li>{@link #RELOAD_CANCEL} — transactional reload failures and candidate cancellation
 *       (Runtime Reload);</li>
 *   <li>{@link #WATCHDOG} — runaway/cancelled evaluations closed by the watchdog or engine
 *       resource limits (Runaway Watchdog).</li>
 * </ul>
 */
public enum DiagnosticPhase {
    PREPARE,
    RESOLVE_LINK,
    CACHE,
    EXECUTION,
    TRUST,
    RELOAD_CANCEL,
    WATCHDOG
}
