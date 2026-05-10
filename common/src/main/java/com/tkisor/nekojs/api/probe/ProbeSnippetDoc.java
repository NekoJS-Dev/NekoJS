package com.tkisor.nekojs.api.probe;

import com.tkisor.nekojs.script.ScriptType;

public record ProbeSnippetDoc(
        String name,
        ScriptType scriptType,
        String prefix,
        String body,
        String description
) {
}
