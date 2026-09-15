package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventGroupJS;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR37 收集与冻结边界负例（ticket 15 最后一条 AC）+ epoch 隔离（AC2）：
 * <ul>
 *   <li>Builder 配置 callback 抛错 → 不得留下可被 drain 的半成品（先配置后入库）；</li>
 *   <li>drain 后底层收集容器的变化不得修改已发布快照（{@code DrainResult} 冻结）；</li>
 *   <li>只读 live view（{@code undrainedLiveView}）是防御性副本，不冒充冻结结果；</li>
 *   <li>失败 epoch 不留进程级暂存污染下一轮启动（新 epoch 从空仓库开始）；</li>
 *   <li>任意 Supplier 的内部可变状态不被误称为深不可变（supplier 原样转发，不快照其产物）。</li>
 * </ul>
 */
class StartupRegistryEpochNegativesTest {

    private static final String NODE = "negative-test-node";

    /** 供 JS 记录收集期异常（EventBusJS 捕获监听器异常，不向上抛）。public：Graal host 访问要求。 */
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
        return RegistryInfosPoint.scan(new RegistryInfosPoint.RegistryInfosCollector());
    }

    private static RegistryTypesPoint.RegistryTypes testTypes() {
        RegistryTypesPoint.RegistryTypesCollector collector = new RegistryTypesPoint.RegistryTypesCollector();
        collector.registerType(Registries.SOUND_EVENT, "basic", SoundEventBuilder.class, SoundEventBuilder::new);
        collector.setDefault(Registries.SOUND_EVENT, "basic");
        return new RegistryTypesPoint.RegistryTypes(collector.byRegistry, collector.defaults, collector.builderClasses);
    }

    @Test
    void throwingConfigCallbackLeavesNoDrainableHalfFinishedEntry() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        ErrorLog errors = new ErrorLog();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            com.tkisor.nekojs.script.ScriptContextRegistry.bind(context, ScriptType.STARTUP);
            context.getBindings("js").putMember("RegistryEvents", new EventGroupJS(RegistryEvents.GROUP, ScriptType.STARTUP));
            context.getBindings("js").putMember("errors", errors);
            context.eval("js", """
                    RegistryEvents.register(event => {
                        // 先完成一条完整声明（保留：它是完整产物，不是半成品）
                        event.soundEvent('mymod:complete', b => { b.fixedRange = 16 })
                        // 配置途中抛错：该 builder 不得成为可 drain 的半成品
                        event.soundEvent('mymod:broken', b => {
                            b.fixedRange = 8;
                            throw new Error('script exploded mid-config');
                        })
                    });
                    """);
            runtime.collectOnce(new RegistryEventJS(runtime.repository(), NODE, testInfos(), testTypes()));
            com.tkisor.nekojs.script.ScriptContextRegistry.unbind(context);
        }

        // EventGroupJS/EventBusJS 把监听器异常捕获并继续：完整条目保留、半成品不入库
        var view = runtime.undrainedLiveView();
        assertEquals(List.of(Identifier.parse("mymod:complete")), view.get(Registries.SOUND_EVENT),
                "抛错的 builder 不入库；完整条目保留");

        RecordingSink sink = new RecordingSink();
        var drained = runtime.drainFor(Registries.SOUND_EVENT, sink);
        assertEquals(1, drained.registered().size());
        assertEquals("mymod:complete", drained.registered().get(0).definition().toString());
        assertTrue(runtime.isFullyDrained(), "半成品没有残留到 drain 期");
    }

    @Test
    void postDrainCollectionChangesDoNotAlterThePublishedSnapshot() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        ErrorLog errors = new ErrorLog();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            com.tkisor.nekojs.script.ScriptContextRegistry.bind(context, ScriptType.STARTUP);
            context.getBindings("js").putMember("RegistryEvents", new EventGroupJS(RegistryEvents.GROUP, ScriptType.STARTUP));
            context.getBindings("js").putMember("errors", errors);
            context.eval("js", "RegistryEvents.register(event => { event.soundEvent('mymod:first', b => { }) });");
            runtime.collectOnce(new RegistryEventJS(runtime.repository(), NODE, testInfos(), testTypes()));
            var first = runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink());
            // 快照之后底层收集容器再变化（同名同注册表的新声明，经直接仓库 seam 注入）
            runtime.repository().add(Registries.SOUND_EVENT,
                    new SoundEventBuilder(Identifier.parse("mymod:second")), "basic", "post-snapshot");
            com.tkisor.nekojs.script.ScriptContextRegistry.unbind(context);

            assertEquals(1, first.registered().size(), "已发布快照不受后续收集影响");
            assertEquals("mymod:first", first.registered().get(0).definition().toString());
            assertThrows(UnsupportedOperationException.class, () -> first.registered().add(null),
                    "快照本身不可变");
            assertEquals(1, runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink()).registered().size(),
                    "后到的声明进下一轮 drain，不改写上一轮结果");
        }
    }

    @Test
    void liveViewIsADefensiveCopyNotAFrozenResult() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        runtime.repository().add(Registries.SOUND_EVENT,
                new SoundEventBuilder(Identifier.parse("mymod:live")), "basic", "test");

        var view = runtime.undrainedLiveView();
        assertEquals(1, view.get(Registries.SOUND_EVENT).size());
        // 防御性副本：外部无法通过视图改动 runtime 状态（内层列表不可变；map 是副本，写入不回灌）
        assertThrows(UnsupportedOperationException.class, () -> view.get(Registries.SOUND_EVENT).clear());
        view.put(Registries.SOUND_EVENT, List.of());
        assertEquals(1, runtime.undrainedLiveView().get(Registries.SOUND_EVENT).size(),
                "live view 是副本；真实状态以 drain 结果快照为准");

        var drained = runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink());
        assertNotSame(view, drained);
        assertTrue(runtime.undrainedLiveView().isEmpty(), "drain 后 live view 反映真实状态（空）");
        assertEquals(1, drained.registered().size(), "冻结结果在 DrainResult 里，不靠 live view 冒充");
    }

    @Test
    void failedEpochStagingDoesNotPolluteTheNextBoot() {
        StartupRegistryRuntime failedBoot = new StartupRegistryRuntime(NODE);
        failedBoot.repository().add(Registries.SOUND_EVENT,
                new SoundEventBuilder(Identifier.parse("mymod:stale")), "basic", "never-drained");
        List<String> diagnostics = new ArrayList<>();
        failedBoot.reportUndelivered(diagnostics::add);
        assertEquals(1, diagnostics.size(), "未交付内容在 epoch 收尾可诊断");
        assertTrue(diagnostics.get(0).contains("mymod:stale"));
        assertTrue(diagnostics.get(0).contains(NODE), "诊断带节点");
        assertFalse(failedBoot.isFullyDrained(), "失败 epoch 确有暂存（测试前提）");

        // 下一轮 boot：新 epoch 从空仓库开始（adapter 的 beginBoot 即此语义 + 丢弃诊断日志）
        StartupRegistryRuntime nextBoot = new StartupRegistryRuntime(NODE);
        assertTrue(nextBoot.isFullyDrained(), "新一轮启动不被上一轮的进程级暂存污染");
        assertTrue(nextBoot.undrainedLiveView().isEmpty());
        assertEquals(0, nextBoot.drainFor(Registries.SOUND_EVENT, new RecordingSink()).registered().size());
    }

    @Test
    void arbitrarySupplierMutableStateIsForwardedNotSnapshot() {
        // 裸 supplier 的内部可变状态不被深拷贝/快照：Runtime 只在执行时做非空+类型校验
        java.util.List<String> box = new ArrayList<>(List.of("before"));
        RegistryRepository repository = new RegistryRepository();
        RegistryObjectBuilder<Object> supplier = new RegistryObjectBuilder<>(Identifier.parse("mymod:mutable")) {
            @Override
            public Object build() {
                return box.get(0);
            }
        };
        repository.add(Registries.SOUND_EVENT, supplier, "supplier", "test");
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE, repository);

        RecordingSink sink = new RecordingSink();
        var first = runtime.drainFor(Registries.SOUND_EVENT, sink);
        box.set(0, "after");
        // 已 drain 的执行结果不因 supplier 内部状态变化而重放/改写
        assertEquals("before", sink.built.get("minecraft:sound_event|mymod:mutable"));
        assertEquals(1, first.registered().size());
        // 指纹不承诺覆盖任意 supplier 的副作用（裸 supplier 记 additional:式来源标记）
        assertEquals("test", first.registered().get(0).origin());
    }

    /** 记录式 sink（与 EndToEnd 测试同款）。 */
    static class RecordingSink implements StartupRegistryRuntime.RegistrySink {
        final List<String> registered = new ArrayList<>();
        final java.util.Map<String, Object> built = new java.util.LinkedHashMap<>();

        @Override
        public void register(
                net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> registry,
                Identifier id, java.util.function.Supplier<?> supplier) {
            built.put(registry.identifier() + "|" + id, supplier.get());
            registered.add(registry.identifier() + "|" + id);
        }
    }
}
