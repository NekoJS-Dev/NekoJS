package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventVariant;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.EventPayloadDeclarations;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryScriptFixture.bootstrap;
import static com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryScriptFixture.initializePlatform;
import static org.junit.jupiter.api.Assertions.*;

class StartupRegistryDefaultDeclarationsTest {
    @TempDir Path temporary;

    private static final ResourceKey<Registry<Object>> BUILDERLESS = ResourceKey.createRegistryKey(
            Identifier.parse("startup-test-addon:builderless"));
    private static final ResourceKey<Registry<Object>> METADATA_ONLY = ResourceKey.createRegistryKey(
            Identifier.parse("startup-test-addon:metadata_only"));

    private static final class DeclarationRegistries implements RegistryInfosPoint.Contributor {
        @Override
        public void registerRegistryInfos(RegistryInfosPoint.RegistryInfosCollector collector) {
            collector.openStartupRegistry(BUILDERLESS);
            collector.addInfo(new RegistryInfo(METADATA_ONLY, Object.class));
        }
    }

    private static EventVariant variant(NekoScriptCatalogSnapshot snapshot, String selector) {
        return EventPayloadDeclarations.resolve(snapshot.events(), snapshot.registryBuilderSurfaces())
                .get(RegistryEventJS.class).variants().stream().filter(entry -> entry.selectors().contains(selector))
                .findFirst().orElseThrow();
    }

//? if neoforge {
    @Test
    void nestedItemDeclarationKeepsOneNullableCallableProperty() throws Exception {
        var runtime = bootstrap(new NekoRegistryPointsPlugin());
        var snapshot = NekoScriptCatalog.snapshot(runtime);
        String manual = com.tkisor.nekojs.probe.backend.typescript.RegistryBuilderTsRenderer
                .render(snapshot.registryBuilderSurfaces());
        String capture = System.getenv("NEKO_REGISTRY_MANUAL_CAPTURE");
        if (capture != null) {
            Path nodeRoot = Path.of(capture).resolve(Path.of("").toAbsolutePath().getFileName());
            Files.createDirectories(nodeRoot);
            Files.writeString(nodeRoot.resolve("registry.d.ts"), manual, StandardCharsets.UTF_8);
        }
        String block = manual.substring(manual.indexOf("interface BlockBuilder"), manual.indexOf("interface FluidBuilder"));
        assertFalse(block.contains("item(cb:"), "A callable property must not also emit a duplicate method declaration");
        assertTrue(block.contains("item: ItemBuilder & ((consumer: (b: ItemBuilder) => void) => void) | null;"), block);
    }
//?}

    @Test
    void nestedItemCallbackPropertyAndNullWritesUseTheRestrictedRuntimeSurface() {
        initializePlatform();
        var block = new BlockBuilder(Identifier.parse("test:manual_item"));
        try (var context = graal.graalvm.polyglot.Context.newBuilder("js")
                .allowHostAccess(new com.tkisor.nekojs.core.NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false).build()) {
            context.getBindings("js").putMember("block", BuilderSurface.of(block));
            assertTrue(context.eval("js", """
                    const nested = block.item;
                    nested.maxStackSize = 16;
                    nested(build => { build.maxStackSize = 8; });
                    block.setItem(nested);
                    const configured = nested.maxStackSize;
                    block.item = null;
                    configured === 8 && block.item === null
                    """).asBoolean());
            assertNull(block.getItem());
            assertThrows(RuntimeException.class, () -> context.eval("js", "block.item(build => {})"));
        }
    }

    @Test
    void nestedFoodCallbackAcceptsDeclaredStringIdsWithoutANodeSpecificAdapter() {
        initializePlatform();
        var item = new ItemBuilder(Identifier.parse("test:food_callback"));
        try (var context = graal.graalvm.polyglot.Context.newBuilder("js")
                .allowHostAccess(new com.tkisor.nekojs.core.NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false).build()) {
            context.getBindings("js").putMember("item", BuilderSurface.of(item));
            assertTrue(context.eval("js", """
                    let helpersVisible = false;
                    item.food(food => {
                        food.nutrition(2).saturation(0.25).effect('minecraft:speed', 100, 0, 1);
                        helpersVisible = typeof food.buildFood === 'function';
                    });
                    helpersVisible
                    """).asBoolean());
        }
    }

    @Test
    void contributedFactoriesSelectTheirOwnBuilderTypesAndRetainUntypedRoutes() {
        RegistryTypesPoint.Contributor addon = new RegistryTypesPoint.Contributor() {
            @Override
            public void registerRegistryTypes(RegistryTypesPoint.RegistryTypesCollector collector) {
                collector.registerType(Registries.SOUND_EVENT, "variant",
                        VariantSoundBuilder.class, VariantSoundBuilder::new);
                collector.registerType(Registries.VILLAGER_TYPE, "opaque", VillagerTypeBuilder::new);
                collector.setDefault(Registries.VILLAGER_TYPE, "opaque");
            }
        };
        var runtime = bootstrap(new NekoRegistryPointsPlugin(), addon);
        var snapshot = NekoScriptCatalog.snapshot(runtime);
        var sound = variant(snapshot, "sound_event");
        var villager = variant(snapshot, "villager_type");
        assertTrue(sound.members().stream().anyMatch(member -> member.tsType().equals(
                "create(id: string, typeName: \"variant\", build: (build: NekoStartupBuilders.VariantSoundBuilder) => void): NekoStartupBuilders.VariantSoundBuilder")));
        assertTrue(sound.members().stream().anyMatch(member -> member.pyType().equals(
                "def create(self, id: str, typeName: Literal[\"variant\"], build: Callable[[VariantSoundBuilder], None]) -> VariantSoundBuilder: ...")));
        assertFalse(sound.members().stream().anyMatch(member -> member.tsType().contains("typeName: string")));
        assertFalse(villager.members().stream().anyMatch(member -> member.tsType().contains("VariantSoundBuilder")));
        List<String> signatures = villager.members().stream().map(member -> member.tsType()).toList();
        assertTrue(signatures.contains("create(id: string, build: (build: any) => void): any"));
        assertTrue(signatures.contains("create(id: string, typeName: \"opaque\", build: (build: any) => void): any"));
        assertTrue(snapshot.registryBuilderSurfaces().stream().noneMatch(entry -> entry.typeName().equals("opaque")));
    }

    @Test
    void startupProxyCallsKeepRequiredCallbacksAndDeferredSupplierSemantics() {
        initializePlatform();
        bootstrap(new NekoRegistryPointsPlugin());
        var event = new RegistryEventJS(new RegistryRepository(), "restricted-startup-test", Registries.SOUND_EVENT,
                NekoRegistryPointsPlugin.registryTypes());
        try (var context = graal.graalvm.polyglot.Context.newBuilder("js")
                .allowHostAccess(new com.tkisor.nekojs.core.NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false).build()) {
            context.getBindings("js").putMember("event", event);
            assertTrue(context.eval("js", """
                    const sound = event.create('test:default', build => { build.fixedRange = null; });
                    const named = event.create('test:named', 'basic', build => { build.setFixedRange(null); });
                    let supplierCalls = 0;
                    const supplied = event.createCustom('test:supplier', () => { supplierCalls++; return null; });
                    typeof sound === 'object' && typeof named === 'object' &&
                        typeof supplied === 'object' && supplierCalls === 0
                    """).asBoolean());
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.create('test:missing')"));
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.create('test:null', null)"));
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.createCustom('test:bad_supplier', 1)"));
            assertTrue(context.eval("js", "typeof event.soundEvent === 'undefined' && typeof event.custom === 'undefined' "
                    + "&& typeof event.register === 'undefined' && typeof event.registryName === 'undefined'").asBoolean());
        }
    }

    public static final class VariantSoundBuilder extends RegistryObjectBuilder<net.minecraft.sounds.SoundEvent> {
        public VariantSoundBuilder(Identifier id) { super(id); }
        public int getVolume() { return 1; }
        public void setVolume(int volume) {}
        @Override public net.minecraft.sounds.SoundEvent build() { return new SoundEventBuilder(id).build(); }
    }

    @Test
    void untypedFactoryOverrideDoesNotRetainThePreviousBuilderClass() {
        initializePlatform();
        RegistryTypesPoint.Contributor addon = new RegistryTypesPoint.Contributor() {
            @Override
            public void registerRegistryTypes(RegistryTypesPoint.RegistryTypesCollector collector) {
                collector.registerType(Registries.SOUND_EVENT, "basic", VariantSoundBuilder::new);
            }
        };
        var runtime = bootstrap(new NekoRegistryPointsPlugin(), addon);
        var types = NekoRegistryPointsPlugin.registryTypes();
        var soundRegistry = Registries.SOUND_EVENT;
        assertInstanceOf(VariantSoundBuilder.class, types.defaultType(soundRegistry)
                .apply(Identifier.parse("test:overridden")));
        assertFalse(types.builderClassesOf(soundRegistry).containsKey("basic"),
                "An untyped replacement factory must discard the previous factory's class metadata");
        var snapshot = NekoScriptCatalog.snapshot(runtime);
        var sound = variant(snapshot, "sound_event");
        List<String> signatures = sound.members().stream().map(member -> member.tsType()).toList();
        assertTrue(signatures.contains("create(id: string, build: (build: any) => void): any"));
        assertTrue(signatures.contains("create(id: string, typeName: \"basic\", build: (build: any) => void): any"));
        assertTrue(sound.members().stream().filter(member -> member.name().equals("create"))
                .allMatch(member -> member.pyType().contains("Callable[[Any], None]) -> Any")));
    }

    @Test
    void realPluginAndDefaultCollectorExposeTheProxyContractInBothBackends() throws Exception {
        var runtime = bootstrap(new NekoRegistryPointsPlugin(), new DeclarationRegistries());
        var snapshot = NekoScriptCatalog.snapshot(runtime);
        assertTrue(snapshot.manualDeclarations().stream().noneMatch(entry -> entry.id().equals("nekojs.registry")));
        var entries = snapshot.registryBuilderSurfaces();
        assertFalse(entries.isEmpty());
        assertTrue(entries.stream().allMatch(entry -> entry.eventPayload() != null),
                "The real type docs producer associates STARTUP proxy members with their event");
        var item = variant(snapshot, "item");
        assertEquals(List.of("item", "minecraft:item"), item.selectors());
        assertEquals("StartupRegistryEvent_item", item.typeName());
        var builderless = variant(snapshot, "startup-test-addon:builderless");
        assertEquals(List.of("startup-test-addon:builderless"), builderless.selectors());
        assertEquals(List.of("createCustom"), builderless.members().stream().map(member -> member.name()).toList());
        assertRuntimeMembersMatch(item, Registries.ITEM);
        assertRuntimeMembersMatch(builderless, BUILDERLESS);
        var declarations = renderDeclarations(snapshot);
        var ts = declarations.typescript();
        var py = declarations.python();
        String tsEvents = ts.get("@side-only/startup/bindings/api-modules.d.ts");
        assertNotNull(tsEvents);
        assertTrue(tsEvents.contains("nekojs:event/versioned"), tsEvents);
        assertTrue(tsEvents.contains("export namespace StartupEvents"), tsEvents);
        assertFalse(tsEvents.contains("$RegistryEventJS"), tsEvents);
        assertFalse(ts.values().stream().anyMatch(file -> file.matches("(?s).*\\bRegistryEvents\\b.*")));
        assertFalse(py.values().stream().anyMatch(file -> file.matches("(?s).*\\bRegistryEvents\\b.*")));
        String pyEvents = py.get("nekojs/_modules/startup/event/versioned/__init__.pyi");
        assertNotNull(pyEvents);
        assertTrue(pyEvents.contains("StartupEvents:"), pyEvents);
        assertFalse(py.getOrDefault("nekojs/_events/startup/__init__.pyi", "").contains("StartupEvents"));
        assertFalse(ts.getOrDefault("@side-only/startup/events/index.d.ts", "").contains("StartupEvents"));
        String tsFlat = tsEvents.replaceAll("\\s+", " ");
        String pyFlat = pyEvents.replaceAll("\\s+", " ");
        assertTrue(tsFlat.contains("selector: \"item\" | \"minecraft:item\", handler: ((event: StartupRegistryEvent_item) => void)"), tsEvents);
        assertTrue(tsFlat.contains("priority: \"HIGHEST\" | \"HIGH\" | \"NORMAL\" | \"LOW\" | \"LOWEST\", selector: \"item\" | \"minecraft:item\""), tsEvents);
        assertFalse(tsFlat.matches(".*function registry\\(\\s*handler:.*"), tsEvents);
        assertFalse(tsFlat.contains("selector: string"), tsEvents);
        assertTrue(tsFlat.contains("selector: \"startup-test-addon:builderless\", handler: ((event: " + builderless.typeName() + ") => void)"), tsEvents);
        assertFalse(tsEvents.contains("startup-test-addon:metadata_only"), tsEvents);
        assertTrue(tsFlat.contains("interface " + builderless.typeName()
                + " { createCustom(id: string, supplier: () => unknown): any; }"), tsEvents);
        assertTrue(tsEvents.contains("create(id: string, build: (build: NekoStartupBuilders.ItemBuilder) => void): NekoStartupBuilders.ItemBuilder;"), tsEvents);
        assertTrue(tsEvents.contains("create(id: string, typeName: \"basic\", build: (build: NekoStartupBuilders.ItemBuilder) => void)"), tsEvents);
        assertTrue(pyEvents.contains("class StartupRegistryEvent_item(Protocol):"), pyEvents);
        assertTrue(pyFlat.contains("selector: Literal[\"item\", \"minecraft:item\"]"), pyEvents);
        assertTrue(pyFlat.contains("priority: _EventPriority, selector: Literal[\"item\", \"minecraft:item\"]"), pyEvents);
        assertFalse(pyFlat.contains("def registry(self, handler:"), pyEvents);
        assertFalse(pyEvents.contains("selector: str"), pyEvents);
        assertTrue(pyEvents.contains("selector: Literal[\"startup-test-addon:builderless\"]"), pyEvents);
        assertFalse(pyEvents.contains("startup-test-addon:metadata_only"), pyEvents);
        assertTrue(pyFlat.contains("class " + builderless.typeName() + "(Protocol): def createCustom(self, id: str, supplier: Callable[[], Any]) -> Any: ..."), pyEvents);
        assertTrue(pyEvents.contains("    @overload\n    def create(self, id: str, build: Callable[[ItemBuilder], None]) -> ItemBuilder: ..."), pyEvents);
        assertTrue(pyEvents.contains("typeName: Literal[\"basic\"], build: Callable[[ItemBuilder], None]"), pyEvents);
        assertTrue(ts.get("@event-builders/index.d.ts").contains("declare namespace NekoStartupBuilders {"));
        assertFalse(ts.getOrDefault("@manual/index.d.ts", "").contains("interface SoundEventBuilder"));
        assertTrue(ts.get("@registry-builders/index.d.ts").contains("interface SoundEventBuilder"));
        if (entries.stream().anyMatch(entry -> entry.builderName().equals("ItemBuilder"))) {
            String builders = ts.get("@event-builders/index.d.ts");
            assertTrue(builders.contains("(build: FoodBuilder) => void"), builders);
            assertTrue(builders.contains("nutrition(nutrition: number): FoodBuilder;"), builders);
            assertTrue(builders.contains("buildFood(): any;"), builders);
            assertTrue(builders.contains("groupTab: string | null;"), builders);
            assertTrue(py.get("nekojs/_registry_builders/__init__.pyi").contains("Callable[[FoodBuilder], None]"));
        }
        var selectors = EventPayloadDeclarations.resolve(snapshot.events(), entries).get(RegistryEventJS.class)
                .variants().stream().flatMap(entry -> entry.selectors().stream()).collect(Collectors.toSet());
        assertFalse(selectors.contains("minecraft:enchantment"));
//? if neoforge {
        assertTrue(selectors.contains("minecraft:fluid"));
//?} else {
        /*assertFalse(selectors.contains("minecraft:fluid"));
         *///?}
        String capture = System.getenv("NEKO_STARTUP_DECLARATION_CAPTURE");
        if (capture != null) {
            Path nodeRoot = Path.of(capture).resolve(Path.of("").toAbsolutePath().getFileName());
            var defaults = renderDeclarations(NekoScriptCatalog.snapshot(bootstrap(new NekoRegistryPointsPlugin())));
            capture(nodeRoot.resolve("typescript"), defaults.typescript());
            capture(nodeRoot.resolve("python"), defaults.python());
        }
    }

    private record Declarations(Map<String, String> typescript, Map<String, String> python) {}

    private Declarations renderDeclarations(NekoScriptCatalogSnapshot snapshot) throws Exception {
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
        return new Declarations(ts, py);
    }

    private static void capture(Path root, Map<String, String> files) throws Exception {
        for (var file : files.entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static void assertRuntimeMembersMatch(EventVariant variant, ResourceKey<? extends Registry<?>> key) {
        var event = new RegistryEventJS(new RegistryRepository(), "declaration-test", key,
                NekoRegistryPointsPlugin.registryTypes());
        Set<String> runtimeMembers = Set.of((String[]) event.getMemberKeys());
        assertEquals(runtimeMembers, variant.members().stream().map(member -> member.name()).collect(Collectors.toSet()));
    }
}
