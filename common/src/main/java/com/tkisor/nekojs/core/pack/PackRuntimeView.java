package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.api.ScriptType;

import java.util.List;

/** Completed entry outcomes for the current type generation, including its failure isolation. */
public record PackRuntimeView(ScriptType type, long generation, boolean activeFailed, List<PackScriptView> entries) {
    public PackRuntimeView {
        entries = List.copyOf(entries);
    }
}
