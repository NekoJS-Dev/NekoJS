package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.probe.ProbeGenerator;

import java.nio.file.Path;

/**
 * NekoJS 内置探针生成器。
 *
 * <p>委托给 {@link ProbeOrchestrator} 执行实际生成。
 * 第三方插件可通过 {@link com.tkisor.nekojs.api.probe.ProbeRegistry#setGenerator} 替换。
 */
public class BuiltinProbeGenerator implements ProbeGenerator {
    private final ProbeOrchestrator orchestrator = new ProbeOrchestrator();

    @Override
    public String name() {
        return "NekoJS Builtin Probe";
    }

    @Override
    public GenerateResult generate(NekoScriptCatalogSnapshot snapshot, Path outputDir) {
        return orchestrator.generate(snapshot, outputDir);
    }
}
