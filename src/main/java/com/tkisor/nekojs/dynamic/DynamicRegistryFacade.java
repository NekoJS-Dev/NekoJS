// TODO(fabric): there is no Fabric implementation yet; remove this whole-file guard once the port is done
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEvents;
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryFacadeRuntime;

/**
 * Production assembly of the dynamic registration facade: holds the process-level
 * {@link DynamicRegistryFacadeRuntime} singleton. The platform assembly ({@code NekoJSMod})
 * registers the candidate domain collector into the root through
 * {@code NekoRuntimeRoot#registerDomainCollector}, the same place the modification owner
 * uses; the DOMAIN_PLAN phase of a reload calls it. The first candidates are triggered by
 * {@code ServerEventListener#onServerAboutToStart} when the server registry is ready.
 *
 * <p>Declarations are collected through the event facade only. Successful activation retires
 * backend claims; releasing a script generation never changes registry claims.
 */
public final class DynamicRegistryFacade {

    private static final DynamicRegistryFacadeRuntime RUNTIME = new DynamicRegistryFacadeRuntime();

    private DynamicRegistryFacade() {
    }

    /**
     * Process-level facade runtime, registered into the root by the platform assembly; tests get
     * an isolated instance by construction.
     */
    public static DynamicRegistryFacadeRuntime runtime() {
        return RUNTIME;
    }

    /**
     * Triggers the first candidate collection once the server registry is ready: posts the
     * collection event on the active bus and handles it with the preflight/publish rules.
     * Idempotent (re-declaring the same definition does not conflict) and shares one ledger
     * with the reload candidate collection.
     */
    public static void fireInitialCollection() {
        RUNTIME.collectInitial("server-registry-ready");
    }
}
//?}
//?}
