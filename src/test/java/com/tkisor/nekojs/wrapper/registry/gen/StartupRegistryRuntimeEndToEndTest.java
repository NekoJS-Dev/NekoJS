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
 * AC10 端到端：成功 / 失败 / 重复 / 类型冲突 / 连带缺失 / drain 失败，全部从
 * <b>唯一调用者 Interface</b>（{@code RegistryEvents.register}，经生产同款
 * {@link EventGroupJS} 绑定 + 真实 GraalJS 监听）贯穿到 Runtime 抽干的可观察结果
 * （{@link StartupRegistryRuntime.DrainResult}：定义、注册表、节点、错误来源），
 * 不读私有仓库字段。
 *
 * <p>事件 payload 用测试内构造的 infos/types（同一 {@link RegistryEventJS} 类与
 * 生产构造路径，只是扩展点产物换成 fixture 输入——collectOnce(RegistryEventJS)
 * 是公开 seam）。sink 是记录式注册器（立即执行 supplier，fabric 直注同款形状；
 * NeoForge 的延迟 supplier 形状由 validatedSupplier 包装同一校验）。
 */
class StartupRegistryRuntimeEndToEndTest {

    private static final String NODE = "e2e-test-node";

    /** 记录式 sink：立即执行 supplier（可观察对象与校验错误），记录 (registry, id)。 */
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

    /** 供 JS 记录收集期错误（EventBusJS 捕获监听器异常，不向上抛——测试侧自行观察）。public：Graal host 访问要求。 */
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
        collector.registerType(Registries.VILLAGER_TYPE, "basic", VillagerTypeBuilder.class, VillagerTypeBuilder::new);
        collector.setDefault(Registries.VILLAGER_TYPE, "basic");
        // 全局唯一的类型名（custom 的合法解析路径）
        collector.registerType(Registries.PAINTING_VARIANT, "art", PaintingVariantBuilder.class, PaintingVariantBuilder::new);
        collector.setDefault(Registries.PAINTING_VARIANT, "art");
        collector.registerType(Registries.ITEM, "basic", ItemBuilder.class, ItemBuilder::new);
        collector.setDefault(Registries.ITEM, "basic");
        // 测试专用：同名类型挂在两个注册表下（custom 的跨注册表歧义路径）
        collector.registerType(Registries.BLOCK, "ambiguous", BlockBuilder.class, BlockBuilder::new);
        collector.registerType(Registries.ITEM, "ambiguous", ItemBuilder.class, ItemBuilder::new);
        return new RegistryTypesPoint.RegistryTypes(collector.byRegistry, collector.defaults, collector.builderClasses);
    }

    /** 驱动一轮完整收集：脚本经生产同款 EventGroupJS 绑定监听 register 总线（post 在 Context 存活期内）。 */
    private static StartupRegistryRuntime collect(String script, ErrorLog errors) {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            // 生产同款：STARTUP 脚本上下文（EventBusJS 的 scriptType 记账依赖此绑定）
            com.tkisor.nekojs.script.ScriptContextRegistry.bind(context, ScriptType.STARTUP);
            context.getBindings("js").putMember("RegistryEvents", new EventGroupJS(RegistryEvents.GROUP, ScriptType.STARTUP));
            context.getBindings("js").putMember("errors", errors);
            context.eval("js", script);
            // 收集事件在脚本监听挂好后、Context 仍存活时投递（生产里由首 pass 前的 adapter 触发）
            runtime.collectOnce(new RegistryEventJS(runtime.repository(), NODE, testInfos(), testTypes()));
            com.tkisor.nekojs.script.ScriptContextRegistry.unbind(context);
        }
        return runtime;
    }

    // ------------------------------------------------------------------
    // 成功：糖方法 / 命名类型 / custom / 裸 supplier 全部到达 Runtime 请求
    // ------------------------------------------------------------------

    @Test
    void successPathCarriesDefinitionRegistryNodeAndFingerprint() {
        StartupRegistryRuntime runtime = collect("""
                RegistryEvents.register(event => {
                    event.villagerType('mymod:scholar', b => { })
                    event.soundEvent('mymod:boom', b => { b.fixedRange = 16 })
                    event.soundEvent('mymod:ping', 'basic', b => { })
                    event.custom('mymod:art', 'art', b => { b.width = 32 })
                    event.register('minecraft:villager_type', 'mymod:raw', () => 'raw-object')
                });
                """, new ErrorLog());

        assertEquals(Map.of(
                "minecraft:villager_type", List.of("mymod:scholar", "mymod:raw"),
                "minecraft:sound_event", List.of("mymod:boom", "mymod:ping"),
                "minecraft:painting_variant", List.of("mymod:art")),
                keepIds(runtime),
                "五种入口（糖/命名/custom/supplier）都要到达对应注册表请求");

        RecordingSink sink = new RecordingSink();
        StartupRegistryRuntime.DrainResult drained = runtime.drainFor(Registries.SOUND_EVENT, sink);
        assertEquals(2, drained.registered().size());
        // 可观察结果包含定义、注册表、节点、请求来源与指纹
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
    // 重复：同批同 id fail-fast（仓库 add 期），先到者保留
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
    // 类型冲突：未知类型名 / custom 跨注册表歧义 / 未知注册表
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
    // 裸 supplier：返回值 / 实际类型 / 重复 ID 校验
    // ------------------------------------------------------------------

    @Test
    void bareSupplierReturnValueAndActualTypeAreValidatedAtDrain() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        StartupRegistryRuntime.DrainResult drained;
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            com.tkisor.nekojs.script.ScriptContextRegistry.bind(context, ScriptType.STARTUP);
            context.getBindings("js").putMember("RegistryEvents", new EventGroupJS(RegistryEvents.GROUP, ScriptType.STARTUP));
            context.eval("js", """
                    RegistryEvents.register(event => {
                        event.register('minecraft:item', 'mymod:null_supplier', () => null)
                        event.register('minecraft:item', 'mymod:wrong_type', () => 'not-an-item')
                    });
                    """);
            runtime.collectOnce(new RegistryEventJS(runtime.repository(), NODE, testInfos(), testTypes()));
            // supplier 的 JS 函数绑定在 Context 上：抽干必须在 Context 存活期内（生产由 pass 驱动）
            drained = runtime.drainFor(Registries.ITEM, new RecordingSink());
            com.tkisor.nekojs.script.ScriptContextRegistry.unbind(context);
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
    // 连带注册：目标 pass 投递 / 抑制 / 目标 pass 已过
    // ------------------------------------------------------------------

    /** 测试用 builder：主对象 + 可开关的连带派生（复用生产 handleAdditionalObjects 通道）。 */
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

        // 连带派生等目标注册表自己的 pass（连带缺失 = suppressed builder 无派生条目）
        RecordingSink soundSink = new RecordingSink();
        StartupRegistryRuntime.DrainResult soundPass = runtime.drainFor(Registries.SOUND_EVENT, soundSink);
        assertEquals(List.of("minecraft:sound_event|mymod:with_extra"), soundSink.registered,
                "只有声明了连带的 builder 投递了派生条目（no_extra 抑制 = 连带缺失于结果）");
        assertTrue(runtime.isFullyDrained(), "drain 后无残留");
    }

    @Test
    void coRegistrationTargetingAnAlreadyPassedRegistryIsReportedNotRegistered() {
        RegistryRepository repository = new RegistryRepository();
        // 主对象住 VILLAGER_TYPE，连带派生指向 SOUND_EVENT——但 SOUND_EVENT 的 pass 先发生
        repository.add(Registries.VILLAGER_TYPE, new GadgetBuilder(Identifier.parse("mymod:late"), true), "basic", "test");
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE, repository);

        // SOUND_EVENT 先抽干（目标注册表先于来源）
        runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink());
        // 主对象在 VILLAGER_TYPE 抽干时投递连带 → 目标已过：记错误、不注册
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
        // AC2：additional 不与「来源对象外」的同 id 冲突——目标注册表里已有**别的来源**的
        // 同 id 主对象时，连带投递在收集期 fail-fast（可观察错误），不是静默覆盖
        RegistryRepository repository = new RegistryRepository();
        repository.add(Registries.SOUND_EVENT,
                new SoundEventBuilder(Identifier.parse("mymod:clash")), "basic", "soundEvent:minecraft:sound_event");
        repository.add(Registries.VILLAGER_TYPE,
                new GadgetBuilder(Identifier.parse("mymod:clash"), true), "basic", "test");
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE, repository);

        // 先抽干 VILLAGER_TYPE：gadget 投递连带 (SOUND_EVENT, mymod:clash) → 与未抽干的主对象冲突
        StartupRegistryRuntime.DrainResult drained = runtime.drainFor(Registries.VILLAGER_TYPE, new RecordingSink());
        assertEquals(1, drained.registered().size(), "主对象本身照常注册");
        assertEquals(1, drained.errors().size(), "冲突的连带投递被拒绝: " + drained.errors());
        StartupRegistryRuntime.ErrorRecord error = drained.errors().get(0);
        assertEquals("mymod:clash", error.definition().toString());
        assertEquals(NODE, error.node());
        assertEquals("additional-collect", error.source());
        assertTrue(error.message().contains("collides with a main entry"), error.message());

        // 被冲突保护的主对象不受影响，照常在自己的 pass 注册
        RecordingSink soundSink = new RecordingSink();
        StartupRegistryRuntime.DrainResult soundPass = runtime.drainFor(Registries.SOUND_EVENT, soundSink);
        assertEquals(List.of("minecraft:sound_event|mymod:clash"), soundSink.registered);
        assertEquals("soundEvent:minecraft:sound_event", soundPass.registered().get(0).origin(),
                "先到的主对象保留（来源可观察）");
        assertTrue(runtime.isFullyDrained(), "冲突不留下可 drain 的残留");
    }

    // ------------------------------------------------------------------
    // drain 失败：平台注册动作失败按条隔离，可观察、不静默丢失
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
    // 收集恰好一次：重复 collectOnce 不再投递
    // ------------------------------------------------------------------

    @Test
    void collectionHappensExactlyOnceBeforeTheFirstPass() {
        ErrorLog errors = new ErrorLog();
        StartupRegistryRuntime runtime = collect("""
                RegistryEvents.register(event => { event.soundEvent('mymod:once', b => { }) });
                """, errors);
        int afterFirst = runtime.undrainedLiveView().get(Registries.SOUND_EVENT).size();

        // 第二次 collectOnce（模拟后续 pass 再入）必须 no-op
        runtime.collectOnce(new RegistryEventJS(runtime.repository(), NODE, testInfos(), testTypes()));
        assertEquals(afterFirst, runtime.undrainedLiveView().get(Registries.SOUND_EVENT).size(),
                "收集事件在首轮 pass 前恰好投递一次");

        RecordingSink sink = new RecordingSink();
        runtime.drainFor(Registries.SOUND_EVENT, sink);
        assertEquals(List.of("minecraft:sound_event|mymod:once"), sink.registered);
        // 再 drain 同一注册表：每 pass 只 drain 一次，无重复交付
        assertEquals(0, runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink()).registered().size());
    }
}
