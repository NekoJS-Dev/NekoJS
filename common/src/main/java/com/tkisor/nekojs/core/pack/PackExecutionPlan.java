package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.api.ScriptType;

import java.util.List;

/** File execution order and exclusions from current source content; no scripts are evaluated. */
public record PackExecutionPlan(ScriptType type, List<PackScriptView> entries, String orderWarning,
                                java.util.Map<String, String> blockedPacks) {
    public PackExecutionPlan {
        entries = List.copyOf(entries);
        blockedPacks = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(blockedPacks));
    }
}
