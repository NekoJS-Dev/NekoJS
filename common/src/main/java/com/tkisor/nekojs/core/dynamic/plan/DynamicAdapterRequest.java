package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;

import java.util.List;

/**
 * One <b>inert</b> adapter request: a generation-scoped candidate plan's description of what it
 * would ask the platform/version adapter to do. This class has no execution channel. It holds no
 * registry, supplier or factory reference, so constructing or holding a request can never modify
 * the live registry; whether registration actually happens is decided separately by the adapter
 * activation path once the transaction/sync gate passes.
 *
 * <p>The action set is closed to {@code register}: remove/replace/modify do not exist in the
 * first version (a changed definition for the same key fails preflight as a conflict, see
 * {@link DynamicCandidateRegistryPlan}).
 *
 * <p>{@code readings} carries every normalized property reading of the definition as
 * {@code name=value} strings, from the same source and in the same order as
 * {@link DynamicDefinition#readings()}. The platform adapter rebuilds the registry value from
 * them at the point of application (stack size / rarity / fixedRange / category / color) without
 * looking the builder up again. They are still pure data: this class has zero MC types, and
 * values are parsed only in the platform adapter.
 */
public record DynamicAdapterRequest(
        long generation,
        String registryKey,
        String id,
        DynamicRegisterMode mode,
        String ownerScriptId,
        String fingerprint,
        String action,
        List<String> readings) {

    /** The only action value: register or redeclare. There is no remove/replace/modify. */
    public static final String ACTION_REGISTER = "register";

    /**
     * Derives a request from a normalized definition. Both the server-side batch path
     * and the client-side sync path construct requests this way; there is no other
     * production construction site. The definition's normalized property readings
     * travel with the request, so the platform adapter can rebuild the registry value
     * without a second lookup.
     */
    public DynamicAdapterRequest(DynamicDefinition definition, long generation, String ownerScriptId) {
        this(generation, definition.type().registryKey(), definition.id(), definition.mode(),
                ownerScriptId == null || ownerScriptId.isBlank() ? "<unknown>" : ownerScriptId,
                definition.fingerprint(), ACTION_REGISTER, definition.readings());
    }

    public DynamicAdapterRequest {
        if (!ACTION_REGISTER.equals(action)) {
            throw new IllegalArgumentException(
                    "DynamicAdapterRequest action is closed to '" + ACTION_REGISTER + "'; got '" + action + "'");
        }
        readings = List.copyOf(readings == null ? List.of() : readings);
    }
}
