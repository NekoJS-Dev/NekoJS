package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.ScriptTypeViews;
import com.tkisor.nekojs.api.annotation.ScriptOutput;
import com.tkisor.nekojs.api.annotation.ScriptTypeView;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.villager.VillagerTradeDeclarationEvent;
import com.tkisor.nekojs.api.villager.VillagerTradeQuery;
import com.tkisor.nekojs.api.villager.VillagerTradeReloadEvent;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclarationEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeQuerySurface;
import com.tkisor.nekojs.core.villager.VillagerTradeReloadEventJS;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises output views across the public metadata seam and both production declaration backends. */
class ProbeScriptTypeViewTest {
    private static final List<Class<?>> NATIVE_TYPES = List.of(VillagerTradeDeclarationEventJS.class,
            VillagerTradeReloadEventJS.class, VillagerTradeQuerySurface.class);
    private static final List<Class<?>> PUBLIC_TYPES = List.of(VillagerTradeDeclarationEvent.class,
            VillagerTradeReloadEvent.class, VillagerTradeQuery.class);

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void helperAcceptsOnlyImplementedPublicNonGenericInterfaces() {
        assertNull(ScriptTypeViews.outputClass(null));
        assertSame(String.class, ScriptTypeViews.outputClass(String.class));
        for (int index = 0; index < NATIVE_TYPES.size(); index++) {
            assertSame(PUBLIC_TYPES.get(index), ScriptTypeViews.outputClass(NATIVE_TYPES.get(index)));
        }
        for (Class<?> invalid : List.of(NotAnInterface.class, NotImplemented.class,
                NonPublicView.class, GenericView.class)) {
            var failure = assertThrows(IllegalArgumentException.class, () -> ScriptTypeViews.outputClass(invalid));
            assertTrue(failure.getMessage().startsWith("[NEKO-4065]"), failure::getMessage);
        }
    }

    @Test
    void reflectorProjectsOnlyOutputsAndPreservesNativeMethodAndTypeIdentity() throws Exception {
        var declaration = new TypeReflector().reflect(VillageBinding.class);
        for (var entry : Map.of("declaration", VillagerTradeDeclarationEvent.class,
                "reload", VillagerTradeReloadEvent.class, "query", VillagerTradeQuery.class).entrySet()) {
            MethodDecl method = method(declaration.methods, entry.getKey());
            var source = VillageBinding.class.getMethod(entry.getKey());
            assertEquals(source, method.sourceMethod);
            assertEquals(source.getGenericReturnType(), method.returnType.sourceType);
            assertFalse(method.returnType.overridden);
            assertEquals("java:" + entry.getValue().getName(), method.returnType.ref.name());
        }
        for (String name : List.of("queries", "nestedQueries")) {
            MethodDecl method = method(declaration.methods, name);
            var source = VillageBinding.class.getMethod(name);
            assertEquals(source, method.sourceMethod);
            assertEquals(source.getGenericReturnType(), method.returnType.sourceType);
            assertFalse(method.returnType.overridden);
            assertEquals(TypeReflector.toRef(source.getGenericReturnType()), method.returnType.ref);
            assertTrue(method.returnType.ref.compatibilityKey().contains("java:" + VillagerTradeQuery.class.getName()));
            assertFalse(method.returnType.ref.compatibilityKey().contains(VillagerTradeQuerySurface.class.getName()));
        }
        MethodDecl input = method(new TypeReflector().reflect(NativeInputBinding.class).methods, "accept");
        assertSame(VillagerTradeQuerySurface.class, input.params.getFirst().type.sourceType);
        assertEquals("java:" + VillagerTradeQuerySurface.class.getName(), input.params.getFirst().type.ref.name());
        assertFalse(input.params.getFirst().type.overridden);
        for (Class<?> publicType : PUBLIC_TYPES) {
            var view = new TypeReflector().reflect(publicType);
            for (var getter : publicType.getMethods()) {
                if (!getter.getName().startsWith("get") || getter.getParameterCount() != 0) continue;
                MethodDecl reflected = method(view.methods, getter.getName());
                assertEquals(getter, reflected.sourceMethod);
                assertEquals(getter.getGenericReturnType(), reflected.returnType.sourceType);
                assertTrue(reflected.isGetter, getter::toGenericString);
                String suffix = getter.getName().substring(3);
                assertEquals(Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1), reflected.property);
            }
        }
    }

    @Test
    void collectorFollowsOutputViewsBeforeDefaultCoreExclusionButDoesNotProjectInputs() {
        ProbeConfig config = ProbeConfig.defaultConfig();
        for (Class<?> nativeType : NATIVE_TYPES) assertTrue(config.isExcluded(nativeType.getName()));
        var catalog = snapshot(VillageBinding.class, nativeEvents());
        assertEquals(PUBLIC_TYPES, catalog.events().stream().map(EventCatalogEntry::eventType).toList());
        Set<Class<?>> collected = ProbeCoordinator.collectClasses(catalog, config);
        assertTrue(collected.containsAll(PUBLIC_TYPES), collected::toString);
        assertTrue(NATIVE_TYPES.stream().noneMatch(collected::contains), collected::toString);
        Set<Class<?>> inputOnly = ProbeCoordinator.collectClasses(snapshot(NativeInputBinding.class, List.of()), config);
        assertFalse(inputOnly.contains(VillagerTradeQuery.class), inputOnly::toString);
        assertFalse(inputOnly.contains(VillagerTradeQuerySurface.class), inputOnly::toString);
        // A direct native output maps without event seeds; explicitly public containers need no projection.
        Set<Class<?>> outputOnly = ProbeCoordinator.collectClasses(snapshot(DirectOutputBinding.class, List.of()), config);
        assertTrue(outputOnly.contains(VillagerTradeQuery.class), outputOnly::toString);
        assertFalse(outputOnly.contains(VillagerTradeQuerySurface.class), outputOnly::toString);
        Set<Class<?>> publicContainers = ProbeCoordinator.collectClasses(snapshot(PublicContainerBinding.class, List.of()), config);
        assertTrue(publicContainers.contains(VillagerTradeQuery.class), publicContainers::toString);
        assertFalse(publicContainers.contains(VillagerTradeQuerySurface.class), publicContainers::toString);
    }

    @Test
    void reflectedPublicViewKeepsOutputMetadataAndExplicitProbeEditPrecedence() throws Exception {
        var declaration = new TypeReflector().reflect(AnnotatedOutputBinding.class);
        MethodDecl reflected = method(declaration.methods, "query");
        var source = AnnotatedOutputBinding.class.getMethod("query");
        assertEquals(source, reflected.sourceMethod);
        assertSame(VillagerTradeQuerySurface.class, reflected.returnType.sourceType);
        assertEquals("java:" + VillagerTradeQuery.class.getName(), reflected.returnType.ref.name());
        assertEquals("unknown", reflected.returnType.outputTypeScript);
        assertFalse(reflected.returnType.overridden);
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        assertTrue(renderer.render(declaration).contains("query(): unknown;"));

        reflected.returnType.ref = ApiTypeRef.primitive("boolean");
        reflected.returnType.overridden = true;
        String edited = renderer.render(declaration);
        assertTrue(edited.contains("query(): boolean;"), edited);
        assertFalse(edited.contains("query(): unknown;"), edited);
        assertSame(VillagerTradeQuerySurface.class, reflected.returnType.sourceType);
        assertEquals(source, reflected.sourceMethod);
    }

    @Test
    void returnedContainersKeepNativeTypeArgumentsAndArrayWrites() throws Exception {
        var declaration = new TypeReflector().reflect(NativeContainerBinding.class);
        for (String name : List.of("consumer", "function", "list", "queries", "nestedQueries",
                "matrix", "genericArrays", "upperBoundList", "lowerBoundList")) {
            MethodDecl reflected = method(declaration.methods, name);
            var source = NativeContainerBinding.class.getMethod(name);
            assertEquals(source, reflected.sourceMethod);
            assertEquals(source.getGenericReturnType(), reflected.returnType.sourceType);
            assertEquals(TypeReflector.toRef(source.getGenericReturnType()), reflected.returnType.ref, name);
            assertFalse(reflected.returnType.overridden, name);
            if (!name.equals("lowerBoundList")) {
                assertTrue(reflected.returnType.ref.compatibilityKey().contains(VillagerTradeQuerySurface.class.getName()), name);
            }
            assertFalse(reflected.returnType.ref.compatibilityKey().contains("java:" + VillagerTradeQuery.class.getName()), name);
        }
        // Returned mutable and callable containers retain their native input promises, even across core exclusion.
        Set<Class<?>> collected = ProbeCoordinator.collectClasses(
                snapshot(NativeContainerBinding.class, List.of()), ProbeConfig.defaultConfig());
        assertFalse(collected.contains(VillagerTradeQuery.class), collected::toString);
        assertFalse(collected.contains(VillagerTradeQuerySurface.class), collected::toString);
    }

    @Test
    void bothBackendsEmitPublicViewsAndBeanGettersWithoutNativeCoreNames(@TempDir Path directory) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        Files.createDirectories(paths.probeConfig().getParent());
        Files.writeString(paths.probeConfig(), """
                [scan]
                includePackages = ["java", "com.tkisor.nekojs.probe", "com.tkisor.nekojs.api.villager"]
                maxDepth = 2
                mode = "SMART"
                """);
        var coordinator = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        var results = coordinator.runProbe(snapshot(VillageBinding.class, nativeEvents()),
                List.of(new TypeScriptProbeBackend(), new PythonProbeBackend()));
        assertEquals(2, results.size());
        for (var result : results) assertTrue(result.success(), result::message);
        String ts = generatedText(directory.resolve(".neko_probe/typescript"), ".d.ts");
        String python = generatedText(directory.resolve(".neko_probe/python"), ".pyi");
        for (Class<?> nativeType : NATIVE_TYPES) {
            assertFalse(ts.contains(nativeType.getName()), nativeType::getName);
            assertFalse(python.contains(nativeType.getName()), nativeType::getName);
            assertFalse(ts.contains("$" + nativeType.getSimpleName()),
                    () -> occurrenceContext(ts, "$" + nativeType.getSimpleName()));
            assertFalse(python.contains(nativeType.getSimpleName()), () -> occurrenceContext(python, nativeType.getSimpleName()));
        }
        // Mixin selector string literals keep native JVM descriptors; they are not authoring type references.
        for (var entry : Map.of("declaration", VillagerTradeDeclarationEventJS.class,
                "reload", VillagerTradeReloadEventJS.class, "query", VillagerTradeQuerySurface.class).entrySet()) {
            String descriptor = entry.getKey() + "()L" + entry.getValue().getName().replace('.', '/') + ";";
            assertTrue(ts.contains("\"" + descriptor + "\""), descriptor);
        }
        assertTrue(ts.contains("\"queries()[Lcom/tkisor/nekojs/api/villager/VillagerTradeQuery;\""));
        for (Class<?> publicType : PUBLIC_TYPES) {
            assertTrue(ts.contains("export interface $" + publicType.getSimpleName()), ts);
            assertTrue(python.contains("class " + publicType.getSimpleName()), python);
            assertTrue(ts.contains("event: $" + publicType.getSimpleName() + ") => void"), ts);
            assertTrue(python.contains("Callable[[" + publicType.getSimpleName() + "], None]"), python);
        }
        for (String getter : List.of("addedCount", "tradeSets", "total", "status", "statusReason", "generation",
                "adapterId", "tradeSetIds", "unrestoredListingKeys", "retiredListingKeys")) {
            String methodName = "get" + Character.toUpperCase(getter.charAt(0)) + getter.substring(1);
            assertTrue(ts.contains("get " + getter + "(): "), getter);
            assertTrue(ts.contains(methodName + "(): "), methodName);
            assertTrue(python.contains("def " + getter + "(self) ->"), getter);
        }
        // Public views retain both native Bean authoring and actual callable getters.
        for (String signature : List.of("get addedCount(): number;", "get total(): number;",
                "get generation(): number;", "get statusReason(): string;", "get adapterId(): string;",
                "getAddedCount(): number;", "getTotal(): number;", "getGeneration(): number;",
                "getStatusReason(): string;", "getAdapterId(): string;")) {
            assertTrue(ts.contains(signature), signature);
        }
        for (String callable : List.of("add", "countOf", "declareObsolete", "describe")) {
            assertTrue(ts.contains(callable + "("), callable);
            assertTrue(python.contains("def " + callable + "("), callable);
        }
        assertTrue(ts.contains("query(): $VillagerTradeQuery;"), ts);
        assertTrue(ts.contains("$VillagerTradeQuery[]"), ts);
        assertTrue(python.contains("def query(self) -> VillagerTradeQuery:"), python);
        var dependencies = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve(
                ".neko_probe/typescript/@special/dependencies.json"))).getAsJsonObject();
        assertTrue(dependencies.get("complete").getAsBoolean(), dependencies::toString);
        assertTrue(dependencies.getAsJsonObject("unavailable").isEmpty(), dependencies::toString);
    }

    private static String occurrenceContext(String text, String needle) {
        int index = text.indexOf(needle);
        return needle + " at " + index + ": " + text.substring(Math.max(0, index - 160),
                Math.min(text.length(), index + needle.length() + 160));
    }

    private static MethodDecl method(List<MethodDecl> methods, String name) {
        return methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static String generatedText(Path directory, String suffix) throws Exception {
        StringBuilder text = new StringBuilder();
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(suffix)).sorted().toList()) {
                text.append(Files.readString(file)).append('\n');
            }
        }
        return text.toString();
    }

    private static List<EventCatalogEntry> nativeEvents() {
        return java.util.stream.IntStream.range(0, NATIVE_TYPES.size())
                .mapToObj(index -> EventCatalogEntry.of("TradeEvents", "event" + index,
                        ScriptType.SERVER, NATIVE_TYPES.get(index), null, false, false)).toList();
    }

    private static NekoScriptCatalogSnapshot snapshot(Class<?> host, List<EventCatalogEntry> events) {
        return new NekoScriptCatalogSnapshot(List.of(ScriptType.SERVER),
                List.of(BindingCatalogEntry.of("VillagerTrades", ScriptType.SERVER, host, false)),
                events, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    /** Signature fixture: native direct descriptors and explicitly public containers, without another owner. */
    public static final class VillageBinding {
        public VillagerTradeDeclarationEventJS declaration() { return null; }
        public VillagerTradeReloadEventJS reload() { return null; }
        public VillagerTradeQuerySurface query() { return null; }
        public VillagerTradeQuery[] queries() {
            return new VillagerTradeQuery[]{VillagerTradeQuerySurface.unavailable("signature fixture")};
        }
        public List<VillagerTradeQuery[]> nestedQueries() {
            return List.<VillagerTradeQuery[]>of(queries());
        }
    }

    public static final class NativeInputBinding {
        public void accept(VillagerTradeQuerySurface query) {}
    }

    public static final class DirectOutputBinding {
        public VillagerTradeQuerySurface query() { return null; }
    }

    public static final class PublicContainerBinding {
        public List<VillagerTradeQuery[]> nestedQueries() { return null; }
    }

    public static final class AnnotatedOutputBinding {
        @ScriptOutput("unknown")
        public VillagerTradeQuerySurface query() { return null; }
    }

    public static final class NativeContainerBinding {
        public Consumer<VillagerTradeQuerySurface> consumer() { return null; }
        public Function<VillagerTradeQuerySurface, VillagerTradeQuerySurface> function() { return null; }
        public List<VillagerTradeQuerySurface> list() { return null; }
        public VillagerTradeQuerySurface[] queries() { return null; }
        public List<VillagerTradeQuerySurface[]> nestedQueries() { return null; }
        public VillagerTradeQuerySurface[][] matrix() { return null; }
        public List<VillagerTradeQuerySurface>[] genericArrays() { return null; }
        public List<? extends VillagerTradeQuerySurface> upperBoundList() { return null; }
        public List<? super VillagerTradeQuerySurface> lowerBoundList() { return null; }
    }

    @ScriptTypeView(String.class)
    public static final class NotAnInterface {}
    @ScriptTypeView(Runnable.class)
    public static final class NotImplemented {}
    private interface PrivateContract {}
    @ScriptTypeView(PrivateContract.class)
    public static final class NonPublicView implements PrivateContract {}
    public interface GenericContract<T> {}
    @ScriptTypeView(GenericContract.class)
    public static final class GenericView implements GenericContract<String> {}
}
