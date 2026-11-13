package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;

import java.util.Locale;

/**
 * Common base of the dynamic definition builders; it only carries the register mode. Validation
 * and normalization happen <b>inside the setter methods</b>: an explicit {@code setMode(...)}
 * call and a JavaBean-style property write ({@code b.mode = 'reloadable'}) are forwarded by
 * {@link DynamicBuilderSurface} to the <b>same</b> {@code Method}, so both forms take the same
 * validation, normalization and fingerprint path.
 *
 * <p><b>Member naming convention</b>: a dynamic builder offers both JavaBean forms
 * ({@code setXxx}/{@code getXxx} plus property writes), as in {@code item.setMaxStackSize(...)}
 * ≡ {@code item.maxStackSize = ...}. The fluent chaining form of the {@code DynamicRegistryJS}
 * bindings ({@code b.maxStackSize(64)}) stays on its own path and is not changed here; the
 * migration table describes it.
 * The new facade does not introduce the dual meaning of "property name equals method name",
 * because Graal {@code invokeMember} would then conflict with the property read and the interop
 * semantics are unreliable.
 */
public abstract class DynamicDefinitionBuilder {

    private DynamicRegisterMode mode = DynamicRegisterMode.WORLD;

    /** The register mode: {@code world} by default, or {@code reloadable}; {@code global} is not
     * available at runtime. */
    public DynamicDefinitionBuilder setMode(String mode) {
        this.mode = DynamicRegisterMode.parse(DynamicDefinitionType.normalizeToken(mode, "register mode"));
        return this;
    }

    /** The canonical name of the current mode, used by the read surface and the fingerprint
     * readings. */
    public String getMode() {
        return mode == null ? null : mode.name().toLowerCase(Locale.ROOT);
    }

    /** The parsed mode, for plans and Adapter requests; the setter has already validated it. */
    DynamicRegisterMode modeValue() {
        return mode;
    }
}
