package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEventJS;
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEvents;
import com.tkisor.nekojs.core.dynamic.plan.DynamicBuilderSurfaces;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.plugin.NekoPluginBootstrap;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicRegistryDefaultDeclarationsTest {
    @TempDir Path temporaryDirectory;

    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void emptyAssociatedPayloadHasAnExplicitPythonProtocolBody() throws IOException {
        RegistryBuilderSurfaceEntry builder = DynamicBuilderSurfaces.derive().getFirst();
        RegistryBuilderSurfaceEntry emptyPayload = new RegistryBuilderSurfaceEntry(
                builder.builderName(), builder.registryKey(), builder.typeName(), builder.sugarName(),
                builder.members(), builder.description(),
                new RegistryBuilderSurfaceEntry.EventPayload(DynamicRegistryEventJS.class, "EmptyPayload", List.of()));
        BackendOutputs outputs = render(catalog(List.of(emptyPayload)));
        assertTrue(outputs.python().get("nekojs/_events/server/__init__.pyi")
                .contains("class EmptyPayload(Protocol):\n    ...\n"));
        assertTrue(outputs.typescript().get("@side-only/server/events/index.d.ts")
                .contains("event: EmptyPayload"));
    }

    @Test
    void defaultCatalogResolvesDynamicProxyPayloadInBothCompleteBackendsWithoutScanningCore() throws IOException {
        NekoScriptCatalogSnapshot snapshot = catalog(DynamicBuilderSurfaces.derive());
        assertEquals(DynamicBuilderSurfaces.derive(), snapshot.registryBuilderSurfaces());
        BackendOutputs outputs = render(snapshot);
        Map<String, String> typescript = outputs.typescript();
        Map<String, String> python = outputs.python();
        String tsEvents = typescript.get("@side-only/server/events/index.d.ts");
        String pyEvents = python.get("nekojs/_events/server/__init__.pyi");
        assertTrue(tsEvents.contains("event: DynamicRegistryEvent"),
                "Default SERVER callback must reference a declared script-only payload instead of the excluded host class");
        assertTrue(tsEvents.contains("/// <reference path=\"../../../@registry-builders/index.d.ts\" />"),
                "The included side event declaration must pull the existing global builder file into default projects");
        assertTrue(tsEvents.contains("interface DynamicRegistryEvent {"));
        assertTrue(pyEvents.contains("Callable[[DynamicRegistryEvent], None]"));
        assertTrue(pyEvents.contains("class DynamicRegistryEvent(Protocol):"));
        assertFalse(tsEvents.contains("$DynamicRegistryEventJS"));
        assertFalse(pyEvents.contains("def dynamicRegistry(handler: Callable[[Any]"));
        assertTrue(typescript.keySet().stream().noneMatch(path -> path.contains("core/dynamic")));
        assertTrue(python.keySet().stream().noneMatch(path -> path.contains("core/dynamic")));
        assertFalse(tsEvents.contains("java:com/tkisor/nekojs/core"));
        assertFalse(pyEvents.contains("nekojs._java.com.tkisor.nekojs.core"));
        assertTrue(pyEvents.contains("from nekojs._registry_builders import DynamicItemBuilder, DynamicMobEffectBuilder, DynamicSoundEventBuilder"));
        Map<String, String> expectedBuilders = Map.of("item", "DynamicItemBuilder",
                "soundEvent", "DynamicSoundEventBuilder", "mobEffect", "DynamicMobEffectBuilder");
        expectedBuilders.forEach((operation, builder) -> {
            assertTrue(tsEvents.contains(operation + "(id: string, build?: (build: " + builder + ") => void): boolean;"));
            assertTrue(pyEvents.contains("def " + operation + "(self, id: str, build: Callable[[" + builder + "], None] = ...) -> bool: ..."));
            assertTrue(typescript.get("@registry-builders/index.d.ts").contains("interface " + builder + " {"));
            assertTrue(python.get("nekojs/_registry_builders/__init__.pyi").contains("class " + builder + "(Protocol):"));
        });
        Set<String> runtimeNames = Arrays.stream((Object[]) new DynamicRegistryEventJS(
                new DynamicRegistryPlanStore().beginBatch()).getMemberKeys())
                .map(Object::toString).collect(Collectors.toSet());
        assertEquals(Set.of("item", "soundEvent", "mobEffect"), runtimeNames);
        assertEquals(runtimeNames, Arrays.stream(DynamicDefinitionType.values())
                .map(DynamicDefinitionType::apiName).collect(Collectors.toSet()));
        String tsPayload = tsEvents.substring(tsEvents.indexOf("interface DynamicRegistryEvent {"), tsEvents.indexOf("}\n"));
        String pyPayload = pyEvents.substring(pyEvents.indexOf("class DynamicRegistryEvent(Protocol):"),
                pyEvents.indexOf("\nclass DynamicRegistryEventsType:"));
        assertEquals(runtimeNames, Pattern.compile("(?m)^    (\\w+)\\(").matcher(tsPayload)
                .results().map(match -> match.group(1)).collect(Collectors.toSet()));
        assertEquals(runtimeNames, Pattern.compile("(?m)^    def (\\w+)\\(").matcher(pyPayload)
                .results().map(match -> match.group(1)).collect(Collectors.toSet()));
        assertTrue(typescript.get("@registry-builders/index.d.ts").contains("fixedRange: number | null;"));
        assertTrue(python.get("nekojs/_registry_builders/__init__.pyi").contains("fixedRange: float | None"));
        assertTrue(typescript.get("@registry-builders/index.d.ts").contains("setMode(mode: string): DynamicSoundEventBuilder;"));
        assertTrue(python.get("nekojs/_registry_builders/__init__.pyi").contains("def setMode(self, mode: str) -> DynamicSoundEventBuilder: ..."));
        assertFalse(typescript.getOrDefault("@side-only/client/events/index.d.ts", "").contains("DynamicRegistryEvent"));
        assertFalse(python.getOrDefault("nekojs/_events/client/__init__.pyi", "").contains("DynamicRegistryEvent"));
        String captureDirectory = System.getenv("NEKO_PROXY_DECLARATION_CAPTURE");
        if (captureDirectory != null) {
            capture(Path.of(captureDirectory).resolve("typescript"), typescript);
            capture(Path.of(captureDirectory).resolve("python"), python);
        }
    }

    @Test
    void matchingBuilderNamesAndDynamicTypeAloneDoNotInventAnEventPayloadAssociation() {
        List<RegistryBuilderSurfaceEntry> unassociated = DynamicBuilderSurfaces.derive().stream()
                .map(entry -> new RegistryBuilderSurfaceEntry(entry.builderName(), entry.registryKey(),
                        entry.typeName(), entry.sugarName(), entry.members(), entry.description())).toList();
        unassociated.forEach(entry -> assertNull(entry.eventPayload()));
        BackendOutputs outputs = render(catalog(unassociated));
        String tsEvents = outputs.typescript().get("@side-only/server/events/index.d.ts");
        String pyEvents = outputs.python().get("nekojs/_events/server/__init__.pyi");
        assertFalse(tsEvents.contains("interface DynamicRegistryEvent {"));
        assertFalse(pyEvents.contains("class DynamicRegistryEvent(Protocol):"));
        assertFalse(pyEvents.contains("from nekojs._registry_builders import"));
        assertTrue(pyEvents.contains("def dynamicRegistry(handler: Callable[[Any], None]"));
    }

    @Test
    void scriptOnlyPayloadOperationsWorkWithProductionHostAccessWhileCoreJavaLookupStaysDenied() {
        ClassFilter filter = new ClassFilter(SandboxConfig.defaultConfig());
        assertFalse(filter.test(DynamicRegistryEventJS.class.getName()));
        DynamicRegistryEventJS payload = new DynamicRegistryEventJS(new DynamicRegistryPlanStore().beginBatch());
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(filter).build()) {
            context.getBindings("js").putMember("event", payload);
            assertTrue(context.eval("js", """
                    event.item('test:default_item') === true &&
                    event.soundEvent('test:default_sound') === true &&
                    event.mobEffect('test:default_effect') === true &&
                    event.item('test:typed_item', build => { build.maxStackSize = 16 }) === true &&
                    event.soundEvent('test:typed_sound', build => { build.setFixedRange(null).setMode('world') }) === true &&
                    event.mobEffect('test:typed_effect', build => { build.setColor(42).setMode('world') }) === true
                    """).asBoolean());
            assertThrows(RuntimeException.class, () -> context.eval("js",
                    "Java.type('com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEventJS')"));
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.item('test:null_callback', null)"));
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.block('test:forbidden')"));
        }
    }

    private static void capture(Path root, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
    }

    private BackendOutputs render(NekoScriptCatalogSnapshot snapshot) {
        ProbeConfig config = ProbeConfig.defaultConfig();
        List<Class<?>> collected = List.copyOf(ProbeCoordinator.collectClasses(snapshot, config));
        assertFalse(collected.isEmpty());
        assertFalse(collected.contains(DynamicRegistryEventJS.class));
        assertTrue(collected.stream().noneMatch(type -> type.getName().startsWith("com.tkisor.nekojs.core.")));
        TypeReflector reflector = new TypeReflector();
        List<TypeDecl> declarations = collected.stream().map(reflector::reflect).toList();
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temporaryDirectory);
        return new BackendOutputs(new TypeScriptProbeBackend().render(new ProbeContext.Of(
                snapshot, collected, config, paths, "typescript", temporaryDirectory.resolve("typescript"), declarations)),
                new PythonProbeBackend().render(new ProbeContext.Of(
                snapshot, collected, config, paths, "python", temporaryDirectory.resolve("python"), declarations)));
    }

    private static NekoScriptCatalogSnapshot catalog(List<RegistryBuilderSurfaceEntry> builders) {
        return NekoScriptCatalog.snapshot(NekoPluginBootstrap.bootstrap(List.of(new DeclarationPlugin(builders)),
                new ScriptPropertyRegistry.Impl()));
    }

    private record DeclarationPlugin(List<RegistryBuilderSurfaceEntry> builders) implements NekoJSPlugin {
        @Override
        public void registerEvents(EventGroupRegistry registry) {
            registry.register(DynamicRegistryEvents.GROUP);
        }

        @Override
        public void registerBinding(BindingRegistry registry) {
            registry.register("ScriptTypes", ScriptType.class);
        }

        @Override
        public void registerTypeDocs(TypeDocsRegister registry) {
            builders.forEach(registry::registerRegistryBuilderSurface);
        }
    }

    private record BackendOutputs(Map<String, String> typescript, Map<String, String> python) {}
}
