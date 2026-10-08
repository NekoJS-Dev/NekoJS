package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.plugin.NekoPluginBootstrap;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class StartupRegistryDefaultDeclarationsTest {
    @TempDir Path temporary;

    private void initializePlatform() {
        try {
            Platform.init(new TestPlatform());
        } catch (IllegalStateException initialized) {
            assertNotNull(Platform.instance());
        }
    }

    @Test
    void contributedFactoriesSelectTheirOwnBuilderTypesAndRetainUntypedRoutes() {
        initializePlatform();
        RegistryTypesPoint.Contributor addon = new RegistryTypesPoint.Contributor() {
            @Override
            public void registerRegistryTypes(RegistryTypesPoint.RegistryTypesCollector collector) {
                collector.registerType(net.minecraft.core.registries.Registries.SOUND_EVENT, "variant",
                        VariantSoundBuilder.class, VariantSoundBuilder::new);
                collector.registerType(net.minecraft.core.registries.Registries.VILLAGER_TYPE, "opaque", VillagerTypeBuilder::new);
                collector.setDefault(net.minecraft.core.registries.Registries.VILLAGER_TYPE, "opaque");
            }
        };
        var runtime = NekoPluginBootstrap.bootstrap(List.of(new NekoRegistryPointsPlugin(), addon), new ScriptPropertyRegistry.Impl());
        var snapshot = NekoScriptCatalog.snapshot(runtime);
        var payload = com.tkisor.nekojs.probe.EventPayloadDeclarations.resolve(snapshot.events(), snapshot.registryBuilderSurfaces())
                .get(RegistryEventJS.class);
        List<String> signatures = payload.members().stream().map(member -> member.tsType()).toList();
        assertTrue(signatures.contains("soundEvent(id: string, typeName: \"variant\", build: (build: NekoStartupBuilders.VariantSoundBuilder) => void): NekoStartupBuilders.VariantSoundBuilder"));
        assertTrue(signatures.contains("custom(id: string, typeName: \"variant\", build: (build: NekoStartupBuilders.VariantSoundBuilder) => void): NekoStartupBuilders.VariantSoundBuilder"));
        assertTrue(signatures.contains("villagerType(id: string, build: (build: any) => void): any"));
        assertTrue(signatures.contains("villagerType(id: string, typeName: \"opaque\", build: (build: any) => void): any"));
        assertFalse(signatures.stream().anyMatch(signature -> signature.startsWith("custom(id: string, typeName: \"basic\"")));
        assertTrue(snapshot.registryBuilderSurfaces().stream().noneMatch(entry -> entry.typeName().equals("opaque")));
    }

    @Test
    void startupProxyCallsKeepRequiredCallbacksAmbiguityAndDeferredSupplierSemantics() {
        initializePlatform();
        NekoPluginBootstrap.bootstrap(List.of(new NekoRegistryPointsPlugin()), new ScriptPropertyRegistry.Impl());
        var event = RegistryEventJS.create(new RegistryRepository(), "restricted-startup-test");
        try (var context = graal.graalvm.polyglot.Context.newBuilder("js")
                .allowHostAccess(new com.tkisor.nekojs.core.NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false).build()) {
            context.getBindings("js").putMember("event", event);
            assertTrue(context.eval("js", """
                    const sound = event.soundEvent('test:default', build => { build.fixedRange = null; });
                    const named = event.soundEvent('test:named', 'basic', build => { build.setFixedRange(null); });
                    let supplierCalls = 0;
                    const supplied = event.register('minecraft:sound_event', 'test:supplier', () => { supplierCalls++; return null; });
                    typeof sound === 'object' && typeof named === 'object' &&
                        typeof supplied === 'object' && supplierCalls === 0
                    """).asBoolean());
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.soundEvent('test:missing')"));
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.soundEvent('test:null', null)"));
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.custom('test:ambiguous', 'basic', build => {})"));
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.create('test:host_factory', build => {})"));
        }
    }

    public static final class VariantSoundBuilder extends RegistryObjectBuilder<net.minecraft.sounds.SoundEvent> {
        public VariantSoundBuilder(net.minecraft.resources.Identifier id) { super(id); }
        public int getVolume() { return 1; }
        public void setVolume(int volume) {}
        @Override public net.minecraft.sounds.SoundEvent build() { return new SoundEventBuilder(id).build(); }
    }

    @Test
    void realPluginAndDefaultCollectorExposeTheProxyContractInBothBackends() throws Exception {
        initializePlatform();
        var runtime = NekoPluginBootstrap.bootstrap(List.of(new NekoRegistryPointsPlugin()), new ScriptPropertyRegistry.Impl());
        var snapshot = NekoScriptCatalog.snapshot(runtime);
        assertFalse(snapshot.manualDeclarations().isEmpty());
        var entries = snapshot.registryBuilderSurfaces();
        assertFalse(entries.isEmpty());
        assertTrue(entries.stream().allMatch(entry -> entry.eventPayload() != null),
                "The real type_docs producer must associate STARTUP proxy members with their event");
        var payloads = com.tkisor.nekojs.probe.EventPayloadDeclarations.resolve(snapshot.events(), entries);
        var payload = payloads.get(RegistryEventJS.class);
        assertNotNull(payload);
        var event = RegistryEventJS.create(new RegistryRepository(), "declaration-test");
        assertEquals(Arrays.stream((Object[]) event.getMemberKeys()).map(Object::toString).collect(Collectors.toSet()),
                payload.members().stream().map(member -> member.name()).collect(Collectors.toSet()));
        assertFalse(payload.members().stream().anyMatch(member -> member.name().equals("create") || member.name().equals("paintingVariant")));
        var sugarNames = payload.members().stream().map(member -> member.name()).distinct()
                .filter(name -> !name.equals("custom") && !name.equals("register")).toList();
        assertEquals(sugarNames.stream().sorted().toList(), sugarNames);
        var config = ProbeConfig.defaultConfig();
        var classes = List.copyOf(ProbeCoordinator.collectClasses(snapshot, config));
        assertTrue(classes.contains(RegistryEventJS.class));
        assertTrue(classes.stream().noneMatch(type -> type.getName().startsWith("com.tkisor.nekojs.core.")));
        var reflector = new TypeReflector();
        var declarations = classes.stream().map(reflector::reflect).toList();
        var paths = NekoJSPaths.fromGameDir(temporary);
        var ts = new TypeScriptProbeBackend().render(new ProbeContext.Of(snapshot, classes, config, paths,
                "typescript", temporary.resolve("typescript"), declarations));
        var py = new PythonProbeBackend().render(new ProbeContext.Of(snapshot, classes, config, paths,
                "python", temporary.resolve("python"), declarations));
        String tsEvents = ts.get("@side-only/startup/events/index.d.ts");
        String pyEvents = py.get("nekojs/_events/startup/__init__.pyi");
        assertTrue(tsEvents.contains("event: StartupRegistryEvent"), tsEvents);
        assertFalse(tsEvents.contains("$RegistryEventJS"), tsEvents);
        assertTrue(tsEvents.contains("soundEvent(id: string, build: (build: NekoStartupBuilders.SoundEventBuilder) => void): NekoStartupBuilders.SoundEventBuilder;"), tsEvents);
        assertTrue(tsEvents.contains("typeName: \"basic\", build: (build: NekoStartupBuilders.SoundEventBuilder) => void"), tsEvents);
        assertTrue(tsEvents.contains("register(registry: string, id: string, supplier: () => unknown): unknown;"), tsEvents);
        assertTrue(pyEvents.contains("class StartupRegistryEvent(Protocol):"), pyEvents);
        assertTrue(pyEvents.contains("    @overload\n    def soundEvent(self, id: str, build: Callable[[SoundEventBuilder], None]) -> SoundEventBuilder: ..."), pyEvents);
        assertTrue(pyEvents.contains("typeName: Literal[\"basic\"], build: Callable[[SoundEventBuilder], None]"), pyEvents);
        assertTrue(pyEvents.contains("Literal"), pyEvents);
        assertTrue(ts.get("@event-builders/index.d.ts").contains("declare namespace NekoStartupBuilders {"));
        assertTrue(ts.get("@manual/index.d.ts").contains("interface SoundEventBuilder"));
        assertTrue(ts.get("@registry-builders/index.d.ts").contains("interface SoundEventBuilder"));
        boolean fluid = entries.stream().anyMatch(entry -> entry.builderName().equals("FluidBuilder"));
        assertEquals(fluid ? 11 : 10, payload.members().stream().map(member -> member.name()).distinct()
                .filter(name -> !name.equals("custom") && !name.equals("register")).count());
        String capture = System.getenv("NEKO_STARTUP_DECLARATION_CAPTURE");
        if (capture != null) {
            Path nodeRoot = Path.of(capture).resolve(Path.of("").toAbsolutePath().getFileName());
            capture(nodeRoot.resolve("typescript"), ts);
            capture(nodeRoot.resolve("python"), py);
        }
    }

    private static void capture(Path root, Map<String, String> files) throws Exception {
        for (var file : files.entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
    }

    private final class TestPlatform implements IPlatform {
        public boolean isClient() { return false; }
        public boolean isDevelopment() { return true; }
        public String getMcVersion() { return "0.0.0"; }
        public Path getGameDir() { return temporary; }
        public Map<String, IModInfo> getMods() { return Map.of(); }
        public IModInfo getInfo(String modId) { return null; }
        public String getLoaderId() { return "test"; }
        public String getLoaderVersion() { return "0.0.0"; }
    }
}
