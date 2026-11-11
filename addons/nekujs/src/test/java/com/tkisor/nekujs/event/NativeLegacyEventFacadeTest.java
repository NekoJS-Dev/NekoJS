package com.tkisor.nekujs.event;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.event.EventSchemaRegistry;
import com.tkisor.nekojs.api.event.ManagedCallbackSchemaRegistry;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.api.EventContractReflector;
import com.tkisor.nekojs.core.compiler.EventCallbackSourceValidator;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.EventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import dev.latvian.mods.kubejs.entity.KubeEntityEvent;
import dev.latvian.mods.kubejs.entity.KubeLivingEntityEvent;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptTypeHolder;
import graal.graalvm.polyglot.Context;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeLegacyEventFacadeTest {
    @TempDir
    static Path gameDirectory;

    @BeforeAll
    static void initializeNativeEvaluatorPlatform() {
        Platform.init(new EvaluatorPlatform(gameDirectory));
    }

    @Test
    void serverAndClientCollectionFilterBeforeResolvingSupportedWrapperSuppliers() {
        AtomicInteger startupResolutions = new AtomicInteger();
        AtomicInteger serverResolutions = new AtomicInteger();
        AtomicInteger clientResolutions = new AtomicInteger();
        EventGroup group = EventGroup.of("LegacyPhaseTest");
        group.startup("startup", () -> { startupResolutions.incrementAndGet(); return Payload.class; });
        group.server("server", () -> { serverResolutions.incrementAndGet(); return Payload.class; });
        group.client("client", () -> { clientResolutions.incrementAndGet(); return Payload.class; });
        var server = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
        var client = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
        group.register(server, false);
        assertEquals(1, startupResolutions.get());
        assertEquals(1, serverResolutions.get());
        assertEquals(0, clientResolutions.get());
        group.register(client, true);
        assertEquals(1, startupResolutions.get());
        assertEquals(1, serverResolutions.get());
        assertEquals(1, clientResolutions.get());
        assertEquals(Set.of("startup", "server"), server.view().get(group.name).viewBuses().keySet());
        assertEquals(Set.of("client"), client.view().get(group.name).viewBuses().keySet());
        EventGroupJS serverView = new EventGroupJS(server.view().get(group.name), ScriptType.SERVER.nativeType());
        assertTrue(serverView.hasMember("server"));
        assertFalse(serverView.hasMember("client"));
        assertThrows(IllegalStateException.class, () -> group.server("late", () -> KubeEvent.class));
        assertThrows(IllegalStateException.class, () -> group.getHandlers().get("server").hasResult());
        assertThrows(UnsupportedOperationException.class, () -> group.getHandlers().clear());
        assertEquals("[NEKO-4211] Legacy event definitions are frozen: LegacyPhaseTest",
                assertThrows(IllegalStateException.class, () -> group.client("late", () -> KubeEvent.class)).getMessage());
    }

    @Test
    void concretePayloadMetadataFeedsCallbackValidationContractsAndProbeDeclarations() {
        EventGroup group = EventGroup.of("LegacyConcreteMetadataTest");
        group.startup("registry", () -> Payload.class);
        var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
        group.register(registry, false);
        var nativeGroup = registry.view().get(group.name);
        var bus = nativeGroup.getBusHolder("registry").getBus(ScriptType.STARTUP.nativeType());
        assertSame(Payload.class, bus.eventType());
        assertSame(Payload.class, bus.bus().eventType());
        assertSame(Payload.class, EventSchemaRegistry.resolve(group.name, "registry"));

        var contracts = EventContractReflector.extractEvents(registry.view().values());
        assertEquals(1, contracts.size());
        assertTrue(contracts.getFirst().payload().stream().anyMatch(field -> field.name().equals("label")));
        ManagedCallbackSchemaRegistry.installContractEvents(contracts);
        List<String> errors = new ArrayList<>();
        ScriptErrorReporter.set((type, kind, failure) -> errors.add(failure.getMessage()));
        try {
            var view = new ScriptBindingSchema.View(Map.of(group.name,
                    new ScriptBindingSchema.BindingMembers(Set.of("registry"))), Set.of());
            Path source = com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(ScriptType.STARTUP.nativeType())
                    .resolve("legacy-concrete-metadata.js");
            EventCallbackSourceValidator.validate(source,
                    "LegacyConcreteMetadataTest.registry(event => { event.register('entry', 'kind'); event.getLabel(); });", view);
            assertTrue(errors.isEmpty(), errors::toString);
            EventCallbackSourceValidator.validate(source,
                    "LegacyConcreteMetadataTest.registry(event => { event.regsiter('entry', 'kind'); });", view);
            assertFalse(errors.isEmpty(), "concrete metadata must still reject misspelled payload members");
        } finally {
            ScriptErrorReporter.set(ScriptErrorReporter.Reporter.NOOP);
            ManagedCallbackSchemaRegistry.clear();
        }

        var catalog = NekoScriptCatalog.events(new EventRuntime(registry.view()));
        assertEquals(1, catalog.size());
        assertSame(Payload.class, catalog.getFirst().eventType());
        var aliases = new TypeAliasRegistry();
        String declaration = new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases))
                .generate(catalog, ScriptType.STARTUP.nativeType());
        assertTrue(declaration.contains("function registry("), declaration);
        assertTrue(declaration.contains("$NativeLegacyEventFacadeTest$Payload"), declaration);
        assertFalse(declaration.contains("$KubeEvent"), declaration);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void invalidPayloadMetadataFailsPublicationAndLinkageErrorsPropagateUnchanged() {
        var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
        EventGroup missing = EventGroup.of("LegacyNullMetadataTest");
        missing.startup("missing", () -> null);
        assertEquals("[NEKO-4215] Invalid legacy event payload type: LegacyNullMetadataTest.missing, actual=null",
                assertThrows(IllegalArgumentException.class, () -> missing.register(registry, false)).getMessage());
        EventGroup wrong = EventGroup.of("LegacyWrongMetadataTest");
        java.util.function.Supplier invalidSupplier = () -> String.class;
        wrong.startup("wrong", invalidSupplier);
        assertEquals("[NEKO-4215] Invalid legacy event payload type: LegacyWrongMetadataTest.wrong, actual=java.lang.String",
                assertThrows(IllegalArgumentException.class, () -> wrong.register(registry, false)).getMessage());
        EventGroup broken = EventGroup.of("LegacyBrokenMetadataTest");
        NoClassDefFoundError linkageFailure = new NoClassDefFoundError("missing/addon/Payload");
        broken.startup("broken", () -> { throw linkageFailure; });
        assertSame(linkageFailure, assertThrows(NoClassDefFoundError.class, () -> broken.register(registry, false)));
        assertTrue(registry.view().isEmpty());
    }

    @Test
    void emptyOrOppositeSideGroupsDoNotCreateNativeNamespaces() {
        EventGroup group = EventGroup.of("ClientOnlyLegacyTest");
        group.client("client", () -> KubeEvent.class);
        var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
        group.register(registry, false);
        assertTrue(registry.view().isEmpty());
    }

    @Test
    void actualNativeGuestRegistrationDispatchAndCleanupUseTheSamePersistentBus() {
        AtomicInteger resolutions = new AtomicInteger();
        EventGroup group = EventGroup.of("LegacyDispatchTest");
        EventHandler handler = group.server("respawn", () -> {
            resolutions.incrementAndGet();
            return Payload.class;
        });
        var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
        group.register(registry, false);
        var nativeGroup = registry.view().get(group.name);
        EventBusJS<?, ?> bus = (EventBusJS<?, ?>) new EventGroupJS(nativeGroup, ScriptType.SERVER.nativeType()).getMember("respawn");
        assertFalse(handler.hasListeners());
        assertSame(EventResult.PASS, handler.post(new Payload()));
        assertEquals(1, resolutions.get());
        for (int generation = 0; generation < 2; generation++) {
            try (Context context = context()) {
                ScriptContextRegistry.bind(context, ScriptType.SERVER.nativeType());
                try {
                    context.getBindings("js").putMember("LegacyDispatchTest", new EventGroupJS(nativeGroup, ScriptType.SERVER.nativeType()));
                    context.eval("js", "globalThis.count = 0; LegacyDispatchTest.respawn(event => { event.register('entry', event.getLabel()); count++; });");
                    assertTrue(handler.hasListeners());
                    Payload payload = new Payload();
                    assertSame(EventResult.PASS, handler.post(ScriptType.SERVER, payload));
                    assertSame(EventResult.PASS, payload.result);
                    assertEquals(1, payload.registrations);
                    assertEquals(1, context.eval("js", "count").asInt());
                    assertEquals(1, resolutions.get(), "published payload metadata stays stable across generations");
                    assertTrue(assertThrows(IllegalArgumentException.class,
                            () -> handler.post(ScriptType.CLIENT, payload)).getMessage().contains("NEKO-4213"));
                    assertTrue(assertThrows(IllegalArgumentException.class,
                            () -> handler.post(new KubeEvent() {})).getMessage().contains("NEKO-4214"));
                } finally {
                    nativeGroup.clearListeners(ScriptType.SERVER.nativeType());
                    ScriptContextRegistry.unbind(context);
                }
            }
            assertFalse(bus.hasListeners(), "native generation cleanup releases subscriptions");
            assertFalse(handler.hasListeners());
        }
    }

    @Test
    void nativeBooleanCancellationProducesLegacyInterruptFalse() {
        EventGroup group = EventGroup.of("LegacyCancelTest");
        EventHandler handler = group.server("cancel", () -> Payload.class).hasResult();
        var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
        group.register(registry, false);
        var nativeGroup = registry.view().get(group.name);
        try (Context context = context()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER.nativeType());
            try {
                context.getBindings("js").putMember("LegacyCancelTest", new EventGroupJS(nativeGroup, ScriptType.SERVER.nativeType()));
                context.eval("js", "LegacyCancelTest.cancel(event => true);");
                Payload payload = new Payload();
                EventResult result = handler.post(payload);
                assertTrue(result.interruptFalse());
                assertTrue(result.override());
                assertFalse(result.pass());
                assertSame(result, payload.result);
                assertNull(result.value());
            } finally {
                nativeGroup.clearListeners(ScriptType.SERVER.nativeType());
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    @Test
    void targetDeclarationsAreCollectableButSubscriptionsAndActivePostsRejectExplicitly() {
        EventGroup group = EventGroup.of("LegacyTargetTest");
        TargetedEventHandler<String> handler = group.server("targeted", () -> {
            throw new AssertionError("unsupported optional target payload must remain unresolved");
        })
                .requiredTarget(EventTargetType.STRING).hasResult();
        var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
        group.register(registry, false);
        EventBusJS<?, ?> bus = (EventBusJS<?, ?>) new EventGroupJS(registry.view().get(group.name), ScriptType.SERVER.nativeType())
                .getMember("targeted");
        assertSame(handler, group.getHandlers().get("targeted"));
        assertTrue(handler.targetRequired);
        assertSame(EventResult.PASS, handler.post(new KubeEvent() {}, "unused"));
        assertTrue(assertThrows(UnsupportedOperationException.class, bus::execute).getMessage().contains("NEKO-4217"));
        rejectActiveTargetPost(bus, handler);
    }

    @Test
    void immediateLegacyExitOperationsRejectRatherThanPretendToCancel() {
        KubeEvent event = new KubeEvent() {};
        assertTrue(assertThrows(UnsupportedOperationException.class, event::cancel).getMessage().contains("NEKO-4218"));
        assertThrows(UnsupportedOperationException.class, () -> event.cancel("value"));
        assertThrows(UnsupportedOperationException.class, event::success);
        assertThrows(UnsupportedOperationException.class, () -> event.success("value"));
        assertThrows(UnsupportedOperationException.class, event::exit);
        assertThrows(UnsupportedOperationException.class, () -> event.exit("value"));
        dev.latvian.mods.rhino.Context context = new dev.latvian.mods.rhino.Context();
        assertThrows(UnsupportedOperationException.class, () -> event.cancel(context));
        assertThrows(UnsupportedOperationException.class, () -> event.cancel(context, "value"));
        assertThrows(UnsupportedOperationException.class, () -> event.success(context));
        assertThrows(UnsupportedOperationException.class, () -> event.success(context, "value"));
        assertThrows(UnsupportedOperationException.class, () -> event.exit(context));
        assertThrows(UnsupportedOperationException.class, () -> event.exit(context, "value"));
        assertThrows(UnsupportedOperationException.class, () -> EventResult.Type.INTERRUPT_FALSE.exit(context, null));
    }

    @Test
    void fixtureMethodDescriptorsAndCovariantEntityBridgesRemainPresent() throws Exception {
        assertEquals(EventResult.class, EventHandler.class.getMethod("post", ScriptTypeHolder.class, KubeEvent.class).getReturnType());
        assertEquals(EventResult.class, EventHandler.class.getMethod("post", KubeEvent.class).getReturnType());
        assertEquals(EventResult.class, TargetedEventHandler.class.getMethod("post", KubeEvent.class, Object.class).getReturnType());
        assertEquals(TargetedEventHandler.class, EventHandler.class.getMethod("requiredTarget", EventTargetType.class).getReturnType());
        assertEquals(Set.of(Player.class, LivingEntity.class, Entity.class),
                Arrays.stream(KubePlayerEvent.class.getDeclaredMethods()).filter(method -> method.getName().equals("getEntity"))
                        .map(java.lang.reflect.Method::getReturnType).collect(java.util.stream.Collectors.toSet()));
        assertTrue(KubeEntityEvent.class.isAssignableFrom(KubeLivingEntityEvent.class));
        assertTrue(KubeLivingEntityEvent.class.isAssignableFrom(KubePlayerEvent.class));
        assertEquals(ScriptType.STARTUP, ScriptType.STARTUP.kjs$getScriptType());
        assertEquals(Object.class, KubeEvent.class.getMethod("cancel", dev.latvian.mods.rhino.Context.class).getReturnType());
        assertEquals(Object.class, KubeEvent.class.getMethod("cancel", dev.latvian.mods.rhino.Context.class, Object.class).getReturnType());
        assertEquals(dev.latvian.mods.rhino.Context.class, EventResult.class.getMethod("cx").getReturnType());
        assertEquals(dev.latvian.mods.kubejs.event.EventExit.class, EventResult.Type.class
                .getMethod("exit", dev.latvian.mods.rhino.Context.class, Object.class).getReturnType());
    }

    private static <E> void rejectActiveTargetPost(EventBusJS<E, ?> bus, TargetedEventHandler<String> handler) {
        var nativeBus = bus.bus();
        var token = nativeBus.listen(event -> {});
        try {
            assertTrue(assertThrows(UnsupportedOperationException.class,
                    () -> handler.post(ScriptType.SERVER, "used", new KubeEvent() {})).getMessage().contains("NEKO-4217"));
        } finally {
            nativeBus.unregister(token);
        }
    }

    private static Context context() {
        return Context.newBuilder("js").allowAllAccess(true).option("engine.WarnInterpreterOnly", "false").build();
    }

    public static final class Payload implements KubeEvent {
        EventResult result;
        int registrations;
        public String getLabel() { return "payload"; }
        public void register(String name, String kind) { registrations++; }
        @Override public void afterPosted(EventResult result) { this.result = result; }
    }

    private record EventRuntime(Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups) implements IPluginRuntime {
        @Override public Map<String, Binding> bindings(com.tkisor.nekojs.api.ScriptType type) { return Map.of(); }
        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(RecipeLifecycleContext context) {}
        @Override public void afterRecipes(RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(com.tkisor.nekojs.api.ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(com.tkisor.nekojs.api.ScriptType type) {}
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }

    private record EvaluatorPlatform(Path gameDir) implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return gameDir; }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "native-event-test"; }
        @Override public String getLoaderVersion() { return "not-a-loader-lifecycle"; }
    }
}
