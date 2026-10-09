package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.testfixture.CommunityProbeTypes;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Writes actual Probe output consumed by test:community-probe-types, without source goldens. */
class CommunityProbeTypeScriptFixtureTest {
    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void writesGeneratedLambdaAndAuthoredRpcDeclarations(@TempDir Path gameDir) throws Exception {
        var replacement = ClassDeclarationCatalogEntry.of(CommunityProbeTypes.RpcBuilder.class, """
                export class $CommunityProbeTypes$RpcBuilder<
                    T extends Record<string, 'string' | 'int'> = {},
                    R extends 'string' | 'int' | undefined = undefined> {
                    schema<const S extends Record<string, 'string' | 'int'>>(value: S): $CommunityProbeTypes$RpcBuilder<S, R>;
                    returns<V extends 'string' | 'int'>(value: V): $CommunityProbeTypes$RpcBuilder<T, V>;
                    fn(callback: (args: { [K in keyof T]: T[K] extends 'string' ? string : number }) =>
                        (R extends 'string' ? string : R extends 'int' ? number : void)): void;
                }
                """);
        var adapter = new AdapterCatalogEntry(CommunityProbeTypes.Payload.class,
                List.of(AdapterInputShape.self(), AdapterInputShape.string()),
                ConversionPrecedence.LOWEST, Optional.empty());
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(adapter),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(replacement),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
        List<Class<?>> classes = List.of(CommunityProbeTypes.Api.class, CommunityProbeTypes.Payload.class,
                CommunityProbeTypes.Mapper.class, CommunityProbeTypes.StringMapper.class,
                CommunityProbeTypes.Supplier.class, CommunityProbeTypes.RpcBuilder.class,
                com.tkisor.nekojs.probe.testfixture.callback.CallbackValue.class);
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe.testfixture"), List.of(), List.of(), List.of(), 3, "SMART"));
        var context = new ProbeContext.Of(snapshot, classes, config, NekoJSPaths.fromGameDir(gameDir),
                "typescript", gameDir.resolve("types"), null);
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        Path output = Path.of("build", "community-probe-types");
        for (var file : files.entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        String module = files.get("@package/com/tkisor/nekojs/probe/testfixture/index.d.ts");
        assertTrue(module.contains("schema<const S"), module);
        assertTrue(module.contains("$CommunityProbeTypes$Mapper_"), module);
    }
}
