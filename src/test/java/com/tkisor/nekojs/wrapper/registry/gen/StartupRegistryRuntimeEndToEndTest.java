package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventGroupJS;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of success, failure, duplicate, type conflict, missing
 * co-registration and drain failure. Every case runs from the only caller-facing
 * interface ({@code RegistryEvents.register}, bound through the production
 * {@link EventGroupJS} plus a real GraalJS listener) through to the observable result of
 * a Runtime drain ({@link StartupRegistryRuntime.DrainResult}: definition, registry, node
 * and error origin). The test never reads private repository fields.
 *
 * <p>The event payload uses infos/types built inside the test with the same
 * {@link RegistryEventJS} class and the production construction path; only the
 * extension-point output is replaced with fixture input, and
 * {@code collectOnce(RegistryEventJS)} is public. The sink is a recording registerer that
 * runs the supplier immediately, the same shape as a Fabric direct registration; the
 * deferred supplier shape of NeoForge puts the same validation behind
 * {@code validatedSupplier}.
 */
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

    private static RegistryInfosPoint.RegistryInfos testInfos() {
        RegistryInfosPoint.RegistryInfosCollector collector = new RegistryInfosPoint.RegistryInfosCollector();
        return RegistryInfosPoint.scan(collector);
    }

    private static RegistryTypesPoint.RegistryTypes testTypes() {
        RegistryTypesPoint.RegistryTypesCollector collector = new RegistryTypesPoint.RegistryTypesCollector();
        collector.registerType(Registries.SOUND_EVENT, "basic", SoundEventBuilder.class, SoundEventBuilder::new);
        collector.setDefault(Registries.SOUND_EVENT, "basic");
        // A globally unique type name, which is the valid resolution path for custom.
        collector.registerType(Registries.VILLAGER_TYPE, "art", VillagerTypeBuilder.class, VillagerTypeBuilder::new);
        collector.registerType(Registries.VILLAGER_TYPE, "basic", VillagerTypeBuilder.class, VillagerTypeBuilder::new);
        collector.setDefault(Registries.VILLAGER_TYPE, "basic");
        collector.registerType(Registries.ITEM, "basic", ItemBuilder.class, ItemBuilder::new);
        collector.setDefault(Registries.ITEM, "basic");
        // Test-only: the same type name under two registries, which is the ambiguous
        // cross-registry path for custom.
        collector.registerType(Registries.BLOCK, "ambiguous", BlockBuilder.class, BlockBuilder::new);
        collector.registerType(Registries.ITEM, "ambiguous", ItemBuilder.class, ItemBuilder::new);
        return new RegistryTypesPoint.RegistryTypes(collector.byRegistry, collector.defaults, collector.builderClasses);
    }

    /** Drives one full collection: the script installs its listener through the production
     * EventGroupJS binding on the register bus, and the post happens while the Context is
     * still alive. */
    private static StartupRegistryRuntime collect(String script, ErrorLog errors) {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            // Same as production: a STARTUP script context. EventBusJS relies on this binding to
            // record the scriptType.
            com.tkisor.nekojs.script.ScriptContextRegistry.bind(context, ScriptType.STARTUP);
            try {
                context.getBindings("js").putMember("RegistryEvents", new EventGroupJS(RegistryEvents.GROUP, ScriptType.STARTUP));
                context.getBindings("js").putMember("errors", errors);
                context.eval("js", script);
                // The collection event is posted after the script installed its listeners and while the
                // Context is still alive. In production the adapter fires it before the first pass.
                runtime.collectOnce(new RegistryEventJS(runtime.repository(), NODE, testInfos(), testTypes()));
            } finally {
                // Unbind even when an assertion fails, so no static binding leaks.
                com.tkisor.nekojs.script.ScriptContextRegistry.unbind(context);
            }
        }
        return runtime;
    }

    // ------------------------------------------------------------------
    // Success: sugar methods, named types, custom and bare suppliers all reach the Runtime request
    // ------------------------------------------------------------------

    @Test
    void successPathCarriesDefinitionRegistryNodeAndFingerprint() {
        StartupRegistryRuntime runtime = collect("""
                RegistryEvents.register(event => {
                    event.villagerType('mymod:scholar', b => { })
                    event.soundEvent('mymod:boom', b => { b.fixedRange = 16 })
                    event.soundEvent('mymod:ping', 'basic', b => { })
                    event.custom('mymod:scholar_variant', 'art', b => { })
                    event.register('minecraft:villager_type', 'mymod:raw', () => 'raw-object')
                });
                """, new ErrorLog());

        assertEquals(Map.of(
                "minecraft:villager_type", List.of("mymod:scholar", "mymod:scholar_variant", "mymod:raw"),
                "minecraft:sound_event", List.of("mymod:boom", "mymod:ping")),
                keepIds(runtime),
                "五种入口（糖/命名/custom/supplier）都要到达对应注册表请求");

        RecordingSink sink = new RecordingSink();
        StartupRegistryRuntime.DrainResult drained = runtime.drainFor(Registries.SOUND_EVENT, sink);
        assertEquals(2, drained.registered().size());
        // The observable result carries the definition, registry, node, request origin and fingerprint.
        StartupRegistryRuntime.RegistrationRecord record = drained.registered().get(0);
        assertEquals("mymod:boom", record.definition().toString());
        assertEquals("minecraft:sound_event", record.registry().identifier().toString());
        assertEquals(NODE, record.node());
        assertEquals("soundEvent:minecraft:sound_event", record.origin());
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
                RegistryEvents.register(event => {
                    event.soundEvent('mymod:dup', b => { })
                    try { event.soundEvent('mymod:dup', b => { }) } catch (e) { errors.add(String(e)) }
                });
                """, errors);

        assertEquals(1, errors.messages().size(), "第二个重复注册必须 fail-fast: " + errors.messages());
        assertTrue(errors.messages().get(0).contains("Duplicate registration 'mymod:dup' in registry 'minecraft:sound_event'"),
                errors.messages().get(0));
        assertTrue(errors.messages().get(0).contains("node") || errors.messages().get(0).contains("soundEvent"),
                "错误信息带来源与节点上下文: " + errors.messages().get(0));
        assertEquals(List.of("mymod:dup"), keepIds(runtime).get("minecraft:sound_event"), "先到者保留");
    }

    // ------------------------------------------------------------------
    // Type conflict: unknown type name, custom ambiguity across registries, unknown registry
    // ------------------------------------------------------------------

    @Test
    void typeConflictsAreDiagnosedAtCollection() {
        ErrorLog errors = new ErrorLog();
        StartupRegistryRuntime runtime = collect("""
                RegistryEvents.register(event => {
                    try { event.soundEvent('mymod:x', 'nope', b => { }) } catch (e) { errors.add('A:' + e) }
                    try { event.custom('mymod:y', 'ambiguous', b => { }) } catch (e) { errors.add('B:' + e) }
                    try { event.custom('mymod:z', 'missing', b => { }) } catch (e) { errors.add('C:' + e) }
                    try { event.register('not_a_registry', 'mymod:w', () => 1) } catch (e) { errors.add('D:' + e) }
                    event.soundEvent('mymod:ok', b => { })
                });
                """, errors);

        assertEquals(4, errors.messages().size());
        assertTrue(errors.messages().get(0).contains("unknown type 'nope' for registry 'minecraft:sound_event'"), errors.messages().get(0));
        assertTrue(errors.messages().get(0).contains("known types: [basic]"), errors.messages().get(0));
        assertTrue(errors.messages().get(1).contains("'ambiguous' is registered under multiple registries"), errors.messages().get(1));
        assertTrue(errors.messages().get(2).contains("unknown type name 'missing'"), errors.messages().get(2));
        assertTrue(errors.messages().get(3).contains("unknown registry 'not_a_registry'"), errors.messages().get(3));
        assertEquals(List.of("mymod:ok"), keepIds(runtime).get("minecraft:sound_event"), "失败输入不进仓库");
    }

    // ------------------------------------------------------------------
    // Bare supplier: return value, actual type and duplicate id validation
    // ------------------------------------------------------------------

    @Test
    void bareSupplierReturnValueAndActualTypeAreValidatedAtDrain() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        StartupRegistryRuntime.DrainResult drained;
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            com.tkisor.nekojs.script.ScriptContextRegistry.bind(context, ScriptType.STARTUP);
            try {
                context.getBindings("js").putMember("RegistryEvents", new EventGroupJS(RegistryEvents.GROUP, ScriptType.STARTUP));
                context.eval("js", """
                        RegistryEvents.register(event => {
                            event.register('minecraft:item', 'mymod:null_supplier', () => null)
                            event.register('minecraft:item', 'mymod:wrong_type', () => 'not-an-item')
                        });
                        """);
                runtime.collectOnce(new RegistryEventJS(runtime.repository(), NODE, testInfos(), testTypes()));
                // The supplier holds a JS function bound to the Context, so the drain must happen while the
                // Context is alive. In production the pass drives it.
                drained = runtime.drainFor(Registries.ITEM, new RecordingSink());
            } finally {
                // Unbind even when an assertion fails, so no static binding leaks.
                com.tkisor.nekojs.script.ScriptContextRegistry.unbind(context);
            }
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
                new SoundEventBuilder(Identifier.parse("mymod:clash")), "basic", "soundEvent:minecraft:sound_event");
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
        assertEquals("soundEvent:minecraft:sound_event", soundPass.registered().get(0).origin(),
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
                RegistryEvents.register(event => {
                    event.soundEvent('mymod:good1', b => { })
                    event.soundEvent('mymod:bad', b => { })
                    event.soundEvent('mymod:good2', b => { })
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
        ErrorLog errors = new ErrorLog();
        StartupRegistryRuntime runtime = collect("""
                RegistryEvents.register(event => { event.soundEvent('mymod:once', b => { }) });
                """, errors);
        int afterFirst = runtime.undrainedLiveView().get(Registries.SOUND_EVENT).size();

        // The second collectOnce, simulating re-entry on a later pass, must be a no-op.
        runtime.collectOnce(new RegistryEventJS(runtime.repository(), NODE, testInfos(), testTypes()));
        assertEquals(afterFirst, runtime.undrainedLiveView().get(Registries.SOUND_EVENT).size(),
                "收集事件在首轮 pass 前恰好投递一次");

        RecordingSink sink = new RecordingSink();
        runtime.drainFor(Registries.SOUND_EVENT, sink);
        assertEquals(List.of("minecraft:sound_event|mymod:once"), sink.registered);
        // Drain the same registry again: each pass drains a registry once, so nothing is delivered twice.
        assertEquals(0, runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink()).registered().size());
    }
}
