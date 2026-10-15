package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import graal.graalvm.polyglot.Context;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeScriptInterfaceBindingTest {
    public interface EmptyApi {
        void run();
    }

    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void emptyInterfaceHasARealClassValueUnderProductionHostAccess() {
        try (Context context = Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false).allowCreateThread(false)
                .option("js.nashorn-compat", "true").build()) {
            assertTrue(context.eval("js", "Java.type('" + EmptyApi.class.getName() + "')").isMetaObject());
        }
    }

    @Test
    void actualBindingGetsAClassValueAndReusedBackendDoesNotLeakIt(@TempDir Path directory) throws Exception {
        var backend = new TypeScriptProbeBackend();
        var bound = BindingCatalogEntry.of("EmptyClass", ScriptType.SERVER, EmptyApi.class, true);
        Map<String, String> files = render(backend, List.of(bound), directory);
        String module = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertTrue(module.contains("export const $TypeScriptInterfaceBindingTest$EmptyApi: {\n    };"), module);
        String bindings = files.get("@side-only/server/bindings/index.d.ts");
        assertTrue(bindings.contains("let EmptyClass: typeof $TypeScriptInterfaceBindingTest$EmptyApi;"), bindings);
        Path output = Path.of("build/probe-ts-interface-binding");
        Files.createDirectories(output);
        Files.writeString(output.resolve("types.d.ts"), module);
        Files.writeString(output.resolve("bindings.d.ts"), bindings);

        String unbound = render(backend, List.of(), directory).get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertFalse(unbound.contains("export const"), unbound);
        var instance = BindingCatalogEntry.of("EmptyInstance", ScriptType.SERVER, EmptyApi.class, false);
        String instanceModule = render(backend, List.of(instance), directory).get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertFalse(instanceModule.contains("export const"), instanceModule);
        var suppressed = new BindingCatalogEntry("Suppressed", ScriptType.SERVER, EmptyApi.class,
                true, true, false, null, null, List.of(), List.of());
        assertFalse(render(backend, List.of(suppressed), directory)
                .get("@package/com/tkisor/nekojs/probe/index.d.ts").contains("export const"));
        var overridden = new BindingCatalogEntry("Overridden", ScriptType.SERVER, EmptyApi.class,
                true, true, true, "unknown", null, List.of(), List.of());
        assertFalse(render(backend, List.of(overridden), directory)
                .get("@package/com/tkisor/nekojs/probe/index.d.ts").contains("export const"));
        var delegated = new BindingCatalogEntry("Delegated", ScriptType.SERVER, null,
                false, false, true, "{ helper(): void }", null, List.of(), List.of(EmptyApi.class));
        Map<String, String> delegateFiles = render(backend, List.of(delegated), directory);
        assertTrue(delegateFiles.get("@package/com/tkisor/nekojs/probe/index.d.ts")
                .contains("export const $TypeScriptInterfaceBindingTest$EmptyApi: {\n    };"));
        assertTrue(delegateFiles.get("@side-only/server/bindings/index.d.ts")
                .contains("let Delegated: { helper(): void } & typeof $TypeScriptInterfaceBindingTest$EmptyApi;"));
        var suppressedDelegate = new BindingCatalogEntry("SuppressedDelegate", ScriptType.SERVER, null,
                false, false, false, "{ helper(): void }", null, List.of(), List.of(EmptyApi.class));
        assertFalse(render(backend, List.of(suppressedDelegate), directory)
                .get("@package/com/tkisor/nekojs/probe/index.d.ts").contains("export const"));
        TypeDecl renamed = new TypeReflector().reflect(EmptyApi.class);
        renamed.renameTo = "RenamedEmptyApi";
        String renamedModule = render(backend, List.of(bound), directory, renamed)
                .get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertTrue(renamedModule.contains("export const $RenamedEmptyApi: {\n    };"), renamedModule);
        assertTrue(renamedModule.contains("export const $TypeScriptInterfaceBindingTest$EmptyApi: typeof $RenamedEmptyApi;"), renamedModule);
        renamed.hidden = true;
        assertFalse(render(backend, List.of(bound), directory, renamed).values().stream()
                .anyMatch(body -> body.contains("export const $RenamedEmptyApi")));
    }

    private static Map<String, String> render(TypeScriptProbeBackend backend, List<BindingCatalogEntry> bindings,
                                             Path directory) {
        return render(backend, bindings, directory, new TypeReflector().reflect(EmptyApi.class));
    }

    private static Map<String, String> render(TypeScriptProbeBackend backend, List<BindingCatalogEntry> bindings,
                                             Path directory, TypeDecl declaration) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), bindings, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of(), List.of(), 5, "SMART"));
        var context = new ProbeContext.Of(snapshot, List.of(EmptyApi.class), config, NekoJSPaths.fromGameDir(directory),
                "typescript", directory.resolve(".neko_probe/typescript"),
                List.of(declaration), ProbeOverrides.empty());
        return backend.render(context);
    }
}
