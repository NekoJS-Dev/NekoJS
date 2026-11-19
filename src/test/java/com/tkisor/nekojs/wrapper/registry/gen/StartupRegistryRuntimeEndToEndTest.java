package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises JS subscriptions through the versioned module publication and observes runtime drain results. */
class StartupRegistryRuntimeEndToEndTest {

    private static final String NODE = "e2e-test-node";

    /** Recording sink: runs the supplier immediately so the built object and any validation
     * error are observable, and records each (registry, id). */
    private static class RecordingSink implements StartupRegistryRuntime.RegistrySink {
        final List<String> registered = new ArrayList<>();
        final Map<String, Object> built = new LinkedHashMap<>();
        final List<String> failures = new ArrayList<>();

        @Override
        public void register(ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier) {
            try {
                built.put(registry.identifier() + "|" + id, supplier.get());
                registered.add(registry.identifier() + "|" + id);
            } catch (RuntimeException e) {
                failures.add(registry.identifier() + "|" + id + " -> " + e.getMessage());
                throw e;
            }
        }
    }

    /** Lets JS record collection-time errors; EventBusJS catches listener exceptions and does
     * not rethrow them, so the test observes them here. Public because Graal host access
     * requires it. */
    public static final class ErrorLog {
        final List<String> messages = new ArrayList<>();

        public void add(String message) {
            messages.add(message);
        }

        List<String> messages() {
            return messages;
        }
    }

    private static final class TestTypes implements RegistryTypesPoint.Contributor {
        @Override
        public void registerRegistryTypes(RegistryTypesPoint.RegistryTypesCollector collector) {
            collector.registerType(Registries.VILLAGER_TYPE, "art", VillagerTypeBuilder.class, VillagerTypeBuilder::new);
        }
    }

    private static StartupRegistryRuntime collect(String source, ErrorLog errors) {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        try (var script = new StartupRegistryScriptFixture(new NekoRegistryPointsPlugin(), new TestTypes())) {
            script.context.getBindings("js").putMember("errors", errors);
            script.context.eval("js", source);
            runtime.collectOnce();
        }
        return runtime;
    }

    @Test
    void successPathCarriesDefinitionRegistryNodeAndFingerprint() {
        StartupRegistryRuntime runtime = collect("""
                StartupEvents.registry('villager_type', event => {
                    event.create('mymod:scholar', build => {});
                    event.create('mymod:scholar_variant', 'art', build => {});
                    event.createCustom('mymod:raw', () => 'raw-object');
                });
                StartupEvents.registry('sound_event', event => {
                    event.create('mymod:boom', build => { build.fixedRange = 16 });
                    event.create('mymod:ping', 'basic', build => {});
                });
                """, new ErrorLog());

        assertEquals(Map.of(
                "minecraft:villager_type", List.of("mymod:scholar", "mymod:scholar_variant", "mymod:raw"),
                "minecraft:sound_event", List.of("mymod:boom", "mymod:ping")),
                keepIds(runtime),
                "Default builders, named builders and suppliers stay in their selected registry");

        RecordingSink sink = new RecordingSink();
        StartupRegistryRuntime.DrainResult drained = runtime.drainFor(Registries.SOUND_EVENT, sink);
        assertEquals(2, drained.registered().size());
        // The observable result carries the definition, registry, node, request origin and fingerprint.
        StartupRegistryRuntime.RegistrationRecord record = drained.registered().get(0);
        assertEquals("mymod:boom", record.definition().toString());
        assertEquals("minecraft:sound_event", record.registry().identifier().toString());
        assertEquals(NODE, record.node());
        assertEquals("create:minecraft:sound_event", record.origin());
        assertEquals(64, record.fingerprint().length(), "SHA-256 hex 指纹");
        Object built = sink.built.get("minecraft:sound_event|mymod:boom");
        assertNotNull(built, "sink 立即执行 supplier（fabric 直注同款形状）");
        assertTrue(built instanceof net.minecraft.sounds.SoundEvent, "构建产物是注册表元素类型");
    }

    private static Map<String, List<String>> keepIds(StartupRegistryRuntime runtime) {
        Map<String, List<String>> view = new LinkedHashMap<>();
        runtime.undrainedLiveView().forEach((registry, ids) ->
                view.put(registry.identifier().toString(), ids.stream().map(Identifier::toString).toList()));
        return view;
    }

    // ------------------------------------------------------------------
    // Duplicate: the same id in one batch fails fast while the repository add runs, and the first
    // entry is kept
    // ------------------------------------------------------------------

    @Test
    void sameBatchDuplicateFailsFastAndKeepsTheFirstEntry() {
        ErrorLog errors = new ErrorLog();
        StartupRegistryRuntime runtime = collect("""
                StartupEvents.registry('sound_event', event => {
                    event.create('mymod:dup', b => { })
                    try { event.create('mymod:dup', b => { }) } catch (e) { errors.add(String(e)) }
                });
                """, errors);

        assertEquals(1, errors.messages().size(), "第二个重复注册必须 fail-fast: " + errors.messages());
        assertTrue(errors.messages().get(0).contains("Duplicate registration 'mymod:dup' in registry 'minecraft:sound_event'"),
                errors.messages().get(0));
        assertTrue(errors.messages().get(0).contains("node") || errors.messages().get(0).contains("create:minecraft:sound_event"),
                "错误信息带来源与节点上下文: " + errors.messages().get(0));
        assertEquals(List.of("mymod:dup"), keepIds(runtime).get("minecraft:sound_event"), "先到者保留");
    }

    @Test
    void namedTypesAreResolvedOnlyInTheSelectedRegistry() {
        ErrorLog errors = new ErrorLog();
        StartupRegistryRuntime runtime = collect("""
                StartupEvents.registry('villager_type', event => {
                    event.create('mymod:shared_type', 'basic', build => {});
                });
                StartupEvents.registry('sound_event', event => {
                    try { event.create('mymod:wrong_scope', 'art', build => {}) } catch (error) { errors.add(String(error)) }
                    try { event.create('mymod:unknown', 'missing', build => {}) } catch (error) { errors.add(String(error)) }
                    event.create('mymod:shared_type', 'basic', build => {});
                });
                """, errors);

        assertEquals(2, errors.messages().size());
        assertTrue(errors.messages().get(0).contains("Registry 'minecraft:sound_event' has no type 'art'"), errors.messages().get(0));
        assertTrue(errors.messages().get(0).contains("known types [basic]"), errors.messages().get(0));
        assertTrue(errors.messages().get(1).contains("Registry 'minecraft:sound_event' has no type 'missing'"), errors.messages().get(1));
        assertEquals(Map.of("minecraft:sound_event", List.of("mymod:shared_type"),
                "minecraft:villager_type", List.of("mymod:shared_type")), keepIds(runtime));
    }

    // ------------------------------------------------------------------
    // Bare supplier: return value, actual type and duplicate id validation
    // ------------------------------------------------------------------

    @Test
    void bareSupplierReturnValueAndActualTypeAreValidatedAtDrain() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        StartupRegistryRuntime.DrainResult drained;
        try (var script = new StartupRegistryScriptFixture(new NekoRegistryPointsPlugin())) {
            script.context.eval("js", """
                    const supplied = [];
                    StartupEvents.registry('item', event => {
                        event.createCustom('mymod:null_supplier', () => { supplied.push('null'); return null; });
                        event.createCustom('mymod:wrong_type', () => { supplied.push('wrong'); return 'not-an-item'; });
                    });
                    """);
            runtime.collectOnce();
            assertEquals(0, script.context.eval("js", "supplied.length").asInt());
            drained = runtime.drainFor(Registries.ITEM, new RecordingSink());
            assertEquals("[\"null\",\"wrong\"]", script.context.eval("js", "JSON.stringify(supplied)").asString());
        }

        assertEquals(2, drained.registered().size(), "两条请求都被抽干（结果可观察）");
        assertEquals(2, drained.errors().size(), "两条都被校验拒绝: " + drained.errors());
        assertTrue(drained.errors().get(0).message().contains("returned null"), drained.errors().get(0).message());
        assertTrue(drained.errors().get(0).message().contains("mymod:null_supplier"));
        assertTrue(drained.errors().get(1).message().contains("but this registry holds"), drained.errors().get(1).message());
        assertTrue(drained.errors().get(1).message().contains("node " + NODE), "错误信息带节点");
        assertTrue(runtime.isFullyDrained(), "无未交付残留");
    }

    // ------------------------------------------------------------------
    // Co-registration: delivered on the target pass, suppressed, or target pass already done
    // ------------------------------------------------------------------

    /** Test builder: a main object plus a co-registration that can be switched on or off, reusing
     * the production handleAdditionalObjects channel. */
    public static final class GadgetBuilder extends RegistryObjectBuilder<String> {
        public final boolean withExtra;

        public GadgetBuilder(Identifier id, boolean withExtra) {
            super(id);
            this.withExtra = withExtra;
        }

        @Override
        public String build() {
            return "gadget:" + id;
        }

        @Override
        public void handleAdditionalObjects(AdditionalObjectRegistry registry) {
            if (withExtra) {
                registry.additional(Registries.SOUND_EVENT, id, () -> "extra-of-" + id);
            }
        }
    }

    @Test
    void coRegistrationDeliversInTargetPassAndSuppressionOmitsIt() {
        RegistryRepository repository = new RegistryRepository();
        repository.add(Registries.VILLAGER_TYPE, new GadgetBuilder(Identifier.parse("mymod:with_extra"), true), "basic", "test");
        repository.add(Registries.VILLAGER_TYPE, new GadgetBuilder(Identifier.parse("mymod:no_extra"), false), "basic", "test");
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE, repository);

        RecordingSink villagerSink = new RecordingSink();
        StartupRegistryRuntime.DrainResult villagerPass = runtime.drainFor(Registries.VILLAGER_TYPE, villagerSink);
        assertEquals(2, villagerPass.registered().size());
        assertTrue(villagerSink.built.containsKey("minecraft:villager_type|mymod:with_extra"));

        // The co-registered object waits for its target registry's own pass. A missing co-registration
        // means the builder was suppressed and has no derived entry.
        RecordingSink soundSink = new RecordingSink();
        StartupRegistryRuntime.DrainResult soundPass = runtime.drainFor(Registries.SOUND_EVENT, soundSink);
        assertEquals(List.of("minecraft:sound_event|mymod:with_extra"), soundSink.registered,
                "只有声明了连带的 builder 投递了派生条目（no_extra 抑制 = 连带缺失于结果）");
        assertTrue(runtime.isFullyDrained(), "drain 后无残留");
    }

    @Test
    void coRegistrationTargetingAnAlreadyPassedRegistryIsReportedNotRegistered() {
        RegistryRepository repository = new RegistryRepository();
        // The main object lives in VILLAGER_TYPE and its co-registration points at SOUND_EVENT, but
        // the SOUND_EVENT pass happens first.
        repository.add(Registries.VILLAGER_TYPE, new GadgetBuilder(Identifier.parse("mymod:late"), true), "basic", "test");
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE, repository);

        // Drain SOUND_EVENT first, so the target registry is already past when the source is drained.
        runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink());
        // Draining VILLAGER_TYPE delivers the co-registered object, and its target pass is already
        // done: the runtime records an error and does not register it.
        RecordingSink sink = new RecordingSink();
        StartupRegistryRuntime.DrainResult drained = runtime.drainFor(Registries.VILLAGER_TYPE, sink);

        assertEquals(1, drained.registered().size(), "主对象本身照常注册");
        assertEquals(1, drained.errors().size());
        StartupRegistryRuntime.ErrorRecord error = drained.errors().get(0);
        assertEquals("mymod:late", error.definition().toString());
        assertEquals("minecraft:sound_event", error.registry().identifier().toString());
        assertEquals(NODE, error.node());
        assertEquals("additional-target", error.source());
        assertTrue(error.message().contains("already passed"));
        assertEquals(0, sink.failures.size(), "派生条目未投递（不是平台拒绝）");
        assertTrue(runtime.isFullyDrained(), "错误不留下可 drain 的残留");
    }

    @Test
    void additionalCollidingWithAnUnrelatedMainEntryIsRejectedAtCollection() {
        // An additional object does not clash with the same id outside its source object. When the
        // target registry already holds a main entry with that id from a different source, the
        // co-registration fails fast during collection and reports an observable error instead of
        // silently overwriting it.
        RegistryRepository repository = new RegistryRepository();
        repository.add(Registries.SOUND_EVENT,
                new SoundEventBuilder(Identifier.parse("mymod:clash")), "basic", "create:minecraft:sound_event");
        repository.add(Registries.VILLAGER_TYPE,
                new GadgetBuilder(Identifier.parse("mymod:clash"), true), "basic", "test");
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE, repository);

        // Drain VILLAGER_TYPE first: the gadget delivers a co-registered (SOUND_EVENT, mymod:clash)
        // entry, which clashes with a main object that has not been drained yet.
        StartupRegistryRuntime.DrainResult drained = runtime.drainFor(Registries.VILLAGER_TYPE, new RecordingSink());
        assertEquals(1, drained.registered().size(), "主对象本身照常注册");
        assertEquals(1, drained.errors().size(), "冲突的连带投递被拒绝: " + drained.errors());
        StartupRegistryRuntime.ErrorRecord error = drained.errors().get(0);
        assertEquals("mymod:clash", error.definition().toString());
        assertEquals(NODE, error.node());
        assertEquals("additional-collect", error.source());
        assertTrue(error.message().contains("collides with a main entry"), error.message());

        // The main object protected by the clash check is unaffected and registers on its own pass
        // as usual.
        RecordingSink soundSink = new RecordingSink();
        StartupRegistryRuntime.DrainResult soundPass = runtime.drainFor(Registries.SOUND_EVENT, soundSink);
        assertEquals(List.of("minecraft:sound_event|mymod:clash"), soundSink.registered);
        assertEquals("create:minecraft:sound_event", soundPass.registered().get(0).origin(),
                "先到的主对象保留（来源可观察）");
        assertTrue(runtime.isFullyDrained(), "冲突不留下可 drain 的残留");
    }

    // ------------------------------------------------------------------
    // Drain failure: a failing platform registration is isolated per entry, observable, and never
    // silently dropped
    // ------------------------------------------------------------------

    @Test
    void platformRegisterFailureIsIsolatedPerEntryAndObservable() {
        StartupRegistryRuntime runtime = collect("""
                StartupEvents.registry('sound_event', event => {
                    event.create('mymod:good1', b => { })
                    event.create('mymod:bad', b => { })
                    event.create('mymod:good2', b => { })
                });
                """, new ErrorLog());

        RecordingSink sink = new RecordingSink() {
            @Override
            public void register(ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier) {
                if (id.getPath().equals("bad")) {
                    failures.add("platform rejected " + id);
                    throw new IllegalStateException("platform rejected '" + id + "' in registry '" + registry.identifier() + "'");
                }
                super.register(registry, id, supplier);
            }
        };
        StartupRegistryRuntime.DrainResult drained = runtime.drainFor(Registries.SOUND_EVENT, sink);

        assertEquals(3, drained.registered().size(), "三条请求都有结果记录");
        assertEquals(1, drained.errors().size(), "失败的那条按条隔离: " + drained.errors());
        StartupRegistryRuntime.ErrorRecord error = drained.errors().get(0);
        assertEquals("mymod:bad", error.definition().toString());
        assertEquals("platform-register", error.source());
        assertEquals("minecraft:sound_event", error.registry().identifier().toString());
        assertEquals(NODE, error.node());
        assertEquals(2, sink.registered.size(), "其余条目照常注册");
        assertTrue(runtime.isFullyDrained(), "无未交付残留可污染后续 pass");
    }

    // ------------------------------------------------------------------
    // Collection happens exactly once: a repeated collectOnce delivers nothing
    // ------------------------------------------------------------------

    @Test
    void collectionHappensExactlyOnceBeforeTheFirstPass() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        try (var script = new StartupRegistryScriptFixture(new NekoRegistryPointsPlugin())) {
            script.context.eval("js", """
                    let deliveries = 0;
                    StartupEvents.registry('sound_event', event => {
                        deliveries++;
                        event.create('mymod:once', build => {});
                    });
                    """);
            runtime.collectOnce();
            runtime.collectOnce();
            assertEquals(1, script.context.eval("js", "deliveries").asInt());
        }

        RecordingSink sink = new RecordingSink();
        runtime.drainFor(Registries.SOUND_EVENT, sink);
        assertEquals(List.of("minecraft:sound_event|mymod:once"), sink.registered);
        // Drain the same registry again: each pass drains a registry once, so nothing is delivered twice.
        assertEquals(0, runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink()).registered().size());
    }
}
