package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptType;

import java.nio.file.Path;

public record TypeOutputLayout(
        Path root,
        Path snippetsPath
) {
    public static TypeOutputLayout defaults() {
        return new TypeOutputLayout(
                NekoJSPaths.PROBE_DIR,
                NekoJSPaths.GAME_DIR.resolve(".vscode").resolve("nekojs.code-snippets")
        );
    }

    public Path typeRoot(ScriptType scriptType) {
        return root.resolve(scriptType.name).resolve("probe-types");
    }
}
