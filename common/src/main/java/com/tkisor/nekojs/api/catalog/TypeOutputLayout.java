package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;

import java.nio.file.Path;

public record TypeOutputLayout(
        Path root,
        Path snippetsPath
) {
    public Path typeRoot(ScriptType scriptType) {
        return root.resolve(scriptType.name).resolve("probe-types");
    }
}
