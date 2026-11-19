package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.ModuleEventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.api.ModuleEventGroupProxy;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModuleEventExposureTest {
    public interface View { int getCount(); }
    public record Payload(int value) { public int secret() { return 99; } }
    public record KeyedPayload(int value, net.minecraft.nekojstest.NativeToken target) { public int secret() { return 99; } }
    public record PublicPayload(int count) implements View { public int getCount() { return count; } }
    public interface DeclaredPayload { int getCount(); }
    public static final class WiderPayload implements DeclaredPayload, java.util.function.IntSupplier {
        @Override public int getCount() { return 9; }
        @Override public int getAsInt() { return 9; }
        public int secret() { return 99; }
        public net.minecraft.nekojstest.NativeToken nativeToken() { return new net.minecraft.nekojstest.NativeToken(); }
    }

    @BeforeAll static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test void requiredSelectorRejectsUnkeyedSubscriptionsWithoutChangingOtherPublications() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("RequiredSelectorEvents");
        var required = group.startup("required", String.class, DispatchKey.<String>string(),
                api -> api.versioned().requiredSelector(String.class, key -> "test:" + key));
        var optional = group.startup("optional", String.class, DispatchKey.<String>string());
        registry.register(group);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.STARTUP);
            try {
                context.getBindings("js").putMember("events", new ModuleEventGroupProxy(
                        context, registry.moduleGroups().getFirst(), ScriptType.STARTUP));
                assertThrows(PolyglotException.class, () -> context.eval("js", "events.required(() => {})"));
                assertThrows(PolyglotException.class, () -> context.eval("js", "events.required(null, () => {})"));
                assertThrows(PolyglotException.class, () -> context.eval("js", "events.required(1, () => {})"));
                assertThrows(PolyglotException.class, () -> context.eval("js", "events.required('x', () => {}, 0)"));
                context.eval("js", "globalThis.seen = []; events.required('HIGH', 'x', v => seen.push(v));"
                        + "events.optional(v => seen.push(v));");
                required.post("skipped", "x");
                required.post("matched", "test:x");
                optional.post("unkeyed", "x");
                assertEquals("[\"matched\",\"unkeyed\"]", context.eval("js", "JSON.stringify(seen)").asString());
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    @Test void publicationsAreExplicitPerEventAndSourcesAreTrackedOnce() {
        var root = new EventGroupRegistry.Impl();
        var registry = root.scopedTo("example");
        var group = EventGroup.of("MachineEvents");
        group.server("changed", Payload.class, api -> api.stable());
        group.add("privateEvent", ScriptType.SERVER, EventBusJS.of(Payload.class));
        registry.register(group);
        assertEquals(java.util.Set.of("changed"), root.moduleGroups().getFirst().events());
        assertEquals(1, root.sources().size());
        assertSame(group, root.sources().getFirst());
        assertThrows(IllegalArgumentException.class, () -> registry.register(group));
        assertEquals(1, root.sources().size());
        assertEquals(1, root.moduleGroups().size());
    }

    @Test void exactPublicationScopeNarrowsButNeverWidensSourceVisibility() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("ScopedEvents");
        assertThrows(IllegalArgumentException.class, () -> group.server("empty", Payload.class, api -> api.stable().scriptTypes()));
        assertThrows(IllegalArgumentException.class, () -> group.server("duplicate", Payload.class, api -> api.stable().scriptTypes(ScriptType.SERVER, ScriptType.SERVER)));
        assertThrows(IllegalArgumentException.class, () -> group.server("repeated", Payload.class, api -> api.stable().scriptTypes(ScriptType.SERVER).scriptTypes(ScriptType.SERVER)));
        var unavailable = EventGroup.of("UnavailableEvents");
        unavailable.server("tick", Payload.class, api -> api.stable().scriptTypes(ScriptType.CLIENT));
        var unavailableFailure = assertThrows(IllegalArgumentException.class, () -> registry.register(unavailable));
        assertTrue(unavailableFailure.getMessage().contains("NEKO-4057"));
        assertTrue(registry.moduleGroups().isEmpty());
        var partiallyUnavailable = EventGroup.of("PartiallyUnavailableEvents");
        partiallyUnavailable.server("tick", Payload.class, api -> api.stable().scriptTypes(ScriptType.SERVER, ScriptType.CLIENT));
        var partialFailure = assertThrows(IllegalArgumentException.class, () -> registry.register(partiallyUnavailable));
        assertTrue(partialFailure.getMessage().contains("NEKO-4057"));
        assertTrue(registry.moduleGroups().isEmpty());
        group.server("tick", Payload.class, api -> api.stable().scriptTypes(ScriptType.SERVER));
        assertTrue(group.getBusHolder("tick").canApplyOn(ScriptType.STARTUP));
        registry.register(group);
        var publication = registry.moduleGroups().getFirst();
        assertEquals(java.util.Set.of(ScriptType.SERVER), publication.scriptTypes());
        assertTrue(publication.available(ScriptType.SERVER));
        assertFalse(publication.available(ScriptType.STARTUP));
        assertFalse(publication.available(ScriptType.TEST));
        assertFalse(publication.available(ScriptType.CLIENT));
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            assertFalse(new ModuleEventGroupProxy(context, publication, ScriptType.STARTUP).hasMember("tick"));
            assertTrue(new ModuleEventGroupProxy(context, publication, ScriptType.SERVER).hasMember("tick"));
        }
    }

    @Test void cancellableProjectedEventsRequireExplicitCancellationControlAndPropagateTrueReturn() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var missingControl = EventGroup.of("MissingControlEvents");
        missingControl.add("changed", ScriptType.SERVER, EventBusJS.of(Payload.class, true),
                api -> api.stable().view(View.class, event -> new PublicPayload(event.value())));
        var missingFailure = assertThrows(IllegalArgumentException.class, () -> registry.register(missingControl));
        assertTrue(missingFailure.getMessage().contains("NEKO-4057"));
        assertTrue(registry.moduleGroups().isEmpty());

        var group = EventGroup.of("ControlledCancelEvents");
        var cancellableBus = EventBusJS.of(Payload.class, true);
        group.add("changed", ScriptType.SERVER, cancellableBus,
                api -> api.stable().view(View.class, event -> new PublicPayload(event.value())).cancellable());
        // Bare unit tests without externalCancellabilityPredicate leave non-cancellable buses valid when .cancellable() is declared.
        group.server("nonCancelInBareUnit", Payload.class,
                api -> api.stable().view(View.class, event -> new PublicPayload(event.value())).cancellable());
        registry.register(group);

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(
                        context, registry.moduleGroups().getFirst(), ScriptType.SERVER));
                context.eval("js", "globalThis.seen = []; "
                        + "stable.changed('HIGH', e => { seen.push(e.count, typeof e.secret); return e.count > 10; }); "
                        + "stable.changed('LOW', e => { seen.push(-e.count); });");
                assertFalse(cancellableBus.post(new Payload(5)));
                assertTrue(cancellableBus.post(new Payload(15)));
                assertEquals("[5,\"undefined\",-5,15,\"undefined\"]",
                        context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(cancellableBus.hasListeners());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    @Test void stablePublicationRejectsNativeSelectors() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("NativeKeyEvents");
        group.server("changed", Payload.class,
                DispatchKey.<Payload, net.minecraft.nekojstest.NativeToken>of(net.minecraft.nekojstest.NativeToken.class),
                api -> api.stable());

        var failure = assertThrows(IllegalArgumentException.class, () -> registry.register(group));
        assertTrue(failure.getMessage().contains("NEKO-4057"));
        assertTrue(registry.moduleGroups().isEmpty());
    }

    @Test void configuredStableProjectionRejectsNativeSelectorsButVersionKeepsThem() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("ProjectedNativeKeyEvents");
        var key = DispatchKey.<Payload, net.minecraft.nekojstest.NativeToken>of(net.minecraft.nekojstest.NativeToken.class);
        var bus = group.server("changed", Payload.class, key,
                api -> api.stable().view(View.class, event -> new PublicPayload(event.value())));
        var failure = assertThrows(IllegalArgumentException.class, () -> registry.register(group));
        assertTrue(failure.getMessage().contains("NEKO-4057"));
        assertTrue(registry.moduleGroups().isEmpty());
        assertTrue(registry.sources().isEmpty());
        var versionGroup = EventGroup.of("ProjectedNativeKeyEvents");
        versionGroup.add("changed", ScriptType.SERVER, bus, api -> api.versioned());
        registry.register(versionGroup);
        var version = registry.moduleGroups().getFirst();
        assertEquals(BindingExposure.Category.VERSION, version.category());
        assertSame(bus, version.group().getBusHolder("changed").getBus(ScriptType.SERVER));
        assertSame(key, ((DispatchEventBus<?, ?>) bus.bus()).dispatchKey());
    }

    @Test void stablePortableSelectorProjectsNativeDispatchKeysAndCatalogMetadata() {
        var stone = new net.minecraft.nekojstest.NativeToken();
        var dirt = new net.minecraft.nekojstest.NativeToken();
        var tokens = Map.of("minecraft:stone", stone, "minecraft:dirt", dirt);
        var key = DispatchKey.of(net.minecraft.nekojstest.NativeToken.class, KeyedPayload::target);
        var root = new EventGroupRegistry.Impl();
        var registry = root.scopedTo("nekojs");
        var group = EventGroup.of("BlockEvents");
        var bus = group.server("broken", KeyedPayload.class, key, api -> api.stable()
                .view(View.class, event -> new PublicPayload(event.value()))
                .selector(String.class, tokens::get));
        registry.register(group);

        var runtime = new CatalogStubRuntime(root.view(), root.moduleGroups());
        var snapshot = NekoScriptCatalog.snapshot(runtime, ScriptType.SERVER);
        ModuleEventCatalogEntry stableEntry = snapshot.moduleEvents().stream()
                .filter(entry -> entry.moduleId().equals("nekojs:event/stable") && entry.event().name().equals("broken"))
                .findFirst()
                .orElseThrow();
        assertEquals(View.class, stableEntry.event().eventType());
        assertEquals(String.class, stableEntry.event().dispatchKeyType());
        assertTrue(stableEntry.event().dispatchable());

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(
                        context, registry.moduleGroups().getFirst(), ScriptType.SERVER));
                context.eval("js", "globalThis.seen = []; "
                        + "stable.broken('minecraft:stone', e => seen.push('stone', e.count, typeof e.secret)); "
                        + "stable.broken('HIGH', 'minecraft:dirt', e => seen.push('dirt', e.count)); "
                        + "stable.broken(e => seen.push('all', e.count));");
                bus.post(new KeyedPayload(3, stone), stone);
                bus.post(new KeyedPayload(8, dirt), dirt);
                assertEquals("[\"all\",3,\"stone\",3,\"undefined\",\"all\",8,\"dirt\",8]",
                        context.eval("js", "JSON.stringify(seen)").asString());
                var unknownKeyFailure = assertThrows(PolyglotException.class,
                        () -> context.eval("js", "stable.broken('minecraft:unknown', e => {});"));
                assertTrue(unknownKeyFailure.getMessage().contains("NEKO-4057"));
                group.clearListeners(ScriptType.SERVER);
                assertFalse(bus.hasListeners());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    @Test void stablePortableSelectorKeepsPriorityCancellationAndCleanup() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("PortableKeyEvents");
        var bus = EventBusJS.of(DeclaredPayload.class, true, DispatchKey.of(String.class, event -> "HIGH"));
        group.add("changed", ScriptType.SERVER, bus, api -> api.stable());
        registry.register(group);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(
                        context, registry.moduleGroups().getFirst(), ScriptType.SERVER));
                context.eval("js", "globalThis.seen = []; "
                        + "stable.changed('HIGH', e => { seen.push(e.count, typeof e.secret); return true; }); "
                        + "stable.changed('LOW', 'HIGH', e => seen.push(0));");
                assertTrue(bus.post(new WiderPayload()));
                assertEquals("[9,\"undefined\"]", context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(bus.hasListeners());
                assertFalse(bus.post(new WiderPayload()));
                assertEquals(2, context.eval("js", "seen.length").asInt());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    @Test void stableIdentityPayloadIsNarrowedWhileVersionPayloadRemainsNative() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("IdentityEvents");
        var bus = group.server("changed", DeclaredPayload.class, api -> {
            api.stable();
            api.versioned();
        });
        registry.register(group);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                for (var publication : registry.moduleGroups()) {
                    String name = publication.category() == BindingExposure.Category.STABLE ? "stable" : "version";
                    context.getBindings("js").putMember(name, new ModuleEventGroupProxy(context, publication, ScriptType.SERVER));
                }
                context.eval("js", "globalThis.seen = []; "
                        + "stable.changed(e => seen.push(e.count, typeof e.secret, typeof e.nativeToken)); "
                        + "version.changed(e => seen.push(e.getCount(), e.secret(), e.nativeToken() !== null));");
                bus.post(new WiderPayload());
                assertEquals("[9,\"undefined\",\"undefined\",9,99,true]",
                        context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(bus.hasListeners());
                bus.post(new WiderPayload());
                assertEquals(6, context.eval("js", "seen.length").asInt());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    @Test void stableIdentityAlsoNarrowsJavaInterfacePayloads() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("JavaInterfaceEvents");
        var bus = group.server("changed", java.util.function.IntSupplier.class, api -> api.stable());
        registry.register(group);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(
                        context, registry.moduleGroups().getFirst(), ScriptType.SERVER));
                context.eval("js", "globalThis.seen = []; stable.changed(e => "
                        + "seen.push(e.getAsInt(), typeof e.secret, typeof e.nativeToken));");
                bus.post(new WiderPayload());
                assertEquals("[9,\"undefined\",\"undefined\"]", context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(bus.hasListeners());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    @Test void stableIdentityKeepsScalarAndArrayPayloadValues() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("ScalarEvents");
        var text = group.server("text", String.class, api -> api.stable());
        var count = group.server("count", Integer.class, api -> api.stable());
        var array = group.server("array", String[].class, api -> api.stable());
        var flag = group.server("flag", Boolean.class, api -> api.stable());
        registry.register(group);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(
                        context, registry.moduleGroups().getFirst(), ScriptType.SERVER));
                context.eval("js", "globalThis.seen = []; stable.text(e => seen.push(typeof e, e)); "
                        + "stable.count(e => seen.push(typeof e, e)); "
                        + "stable.array(e => seen.push(e.length, e[1])); "
                        + "stable.flag(e => seen.push(typeof e, e));");
                text.post("hello");
                count.post(7);
                array.post(new String[]{"a", "b"});
                flag.post(true);
                assertEquals("[\"string\",\"hello\",\"number\",7,2,\"b\",\"boolean\",true]",
                        context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(text.hasListeners());
                assertFalse(count.hasListeners());
                assertFalse(array.hasListeners());
                assertFalse(flag.hasListeners());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stableSubscriptionsWrapTheSelectedCallbackNotAnIgnoredTrailingCallback(boolean projected) {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("SelectedCallbackEvents");
        var bus = group.server("changed", DeclaredPayload.class, api -> {
            if (projected) api.stable().view(View.class, event -> new PublicPayload(event.getCount()));
            else api.stable();
        });
        registry.register(group);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(
                        context, registry.moduleGroups().getFirst(), ScriptType.SERVER));
                context.eval("js", "globalThis.seen = []; stable.changed("
                        + "e => seen.push(e.count, typeof e.secret), () => seen.push('unused'));");
                bus.post(new WiderPayload());
                assertEquals("[9,\"undefined\"]", context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(bus.hasListeners());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    @Test void stableSubscriptionsIgnoreUnusedArgumentsLikeTheirSourceBus() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("IgnoredArgumentsEvents");
        var plain = group.server("changed", DeclaredPayload.class, api -> api.stable());
        var keyed = group.server("keyed", DeclaredPayload.class,
                DispatchKey.of(String.class, event -> "match"), api -> api.stable());
        registry.register(group);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(
                        context, registry.moduleGroups().getFirst(), ScriptType.SERVER));
                context.eval("js", "globalThis.seen = []; "
                        + "const observe = e => seen.push(e.count, typeof e.secret); "
                        + "stable.changed(observe, 42); stable.changed('HIGH', observe, false); "
                        + "stable.keyed('match', observe, 42); stable.keyed('LOW', 'match', observe, false);");
                plain.post(new WiderPayload());
                keyed.post(new WiderPayload());
                assertEquals("[9,\"undefined\",9,\"undefined\",9,\"undefined\",9,\"undefined\"]",
                        context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(plain.hasListeners());
                assertFalse(keyed.hasListeners());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    @Test void stableAndVersionedViewsConvertIndependentlyWhenTheSourceIsPostedDirectly() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("IndependentViewEvents");
        var bus = group.server("changed", Payload.class, api -> {
            api.stable().view(View.class, event -> new PublicPayload(event.value() + 10));
            api.versioned().view(View.class, event -> new PublicPayload(event.value() + 20));
        });
        registry.register(group);
        assertEquals(2, registry.moduleGroups().size());
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                for (var publication : registry.moduleGroups()) {
                    String name = publication.category() == BindingExposure.Category.STABLE ? "stable" : "versioned";
                    context.getBindings("js").putMember(name,
                            new ModuleEventGroupProxy(context, publication, ScriptType.SERVER));
                }
                context.getBindings("js").putMember("raw", bus);
                context.eval("js", "globalThis.seen = []; "
                        + "stable.changed(e => seen.push(e.count, typeof e.secret)); "
                        + "versioned.changed(e => seen.push(e.count, typeof e.secret)); "
                        + "raw(e => seen.push(e.value(), e.secret()));");
                assertFalse(bus.post(new Payload(7)));
                assertEquals("[17,\"undefined\",27,\"undefined\",7,99]",
                        context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(bus.hasListeners());
                bus.post(new Payload(8));
                assertEquals(6, context.eval("js", "seen.length").asInt());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    @Test void projectionSharesTheRawBusAndItsListenerCleanup() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("MachineEvents");
        var bus = group.server("changed", Payload.class, api -> api.stable().view(
                View.class, payload -> new PublicPayload(payload.value())));
        registry.register(group);
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(context, registry.moduleGroups().getFirst(), ScriptType.SERVER));
                context.getBindings("js").putMember("raw", bus);
                context.eval("js", "globalThis.seen = []; stable.changed(e => seen.push(e.count, typeof e.secret)); raw(e => seen.push(e.secret()));");
                bus.post(new Payload(7));
                assertEquals("[7,\"undefined\",99]", context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(bus.hasListeners());
                bus.post(new Payload(8));
                assertEquals(3, context.eval("js", "seen.length").asInt());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }

    private record CatalogStubRuntime(Map<String, EventGroup> eventGroups,
                                      List<ModuleEventGroup> moduleEventGroups) implements IPluginRuntime {
        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
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
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }
}
