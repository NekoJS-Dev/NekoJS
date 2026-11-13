package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Facade behavior with real GraalJS and the same {@link EventGroupJS} binding production uses: collection
 * through the direct per-type entry points, no generic type catalog (an unknown type string is not a
 * registration capability), a closed candidate scope, and a collection failure that poisons the whole batch.
 * That failure is the first path where a failed batch must not produce its own writes.
 */
class DynamicRegistryEventFacadeTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** Runs a script in a real GraalJS SERVER context with the production group binding, then triggers collection. */
    private static DynamicRegistryFacadeRuntime run(String script, String trigger) {
        DynamicRegistryFacadeRuntime runtime = new DynamicRegistryFacadeRuntime();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("DynamicRegistryEvents",
                        new EventGroupJS(DynamicRegistryEvents.GROUP, ScriptType.SERVER));
                context.eval("js", script);
                // The initial candidate. Production triggers it when the server registry is ready; this test drives it while
                // the Context is alive.
                runtime.collectInitial(trigger);
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
        return runtime;
    }

    @Test
    void typedDirectEntriesReachThePlanAndStore() {
        DynamicRegistryFacadeRuntime runtime = run("""
                DynamicRegistryEvents.dynamicRegistry(event => {
                    event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' });
                    event.soundEvent('mymod:boom', b => { b.setFixedRange(16) });
                    event.mobEffect('mymod:wither_touch', b => { b.setCategory('harmful').setColor(0x8B0000) });
                });
                """, "server-registry-ready");

        DynamicRegistryFacadeRuntime.CollectionOutcome outcome = runtime.lastOutcome();
        assertNotNull(outcome, "收集结果可观察");
        assertTrue(outcome.published(), "收集成功才发布: " + outcome.failureDetail());
        assertEquals("server-registry-ready", outcome.trigger());
        assertEquals(1, outcome.generation(), "初次候选 generation");
        assertEquals(3, runtime.store().lastCommittedBatch().size(), "三个类型直达入口各一条定义");
        assertNotNull(runtime.store().exposedEntry("minecraft:item|mymod:ruby"));
        assertNotNull(runtime.store().exposedEntry("minecraft:sound_event|mymod:boom"));
        assertNotNull(runtime.store().exposedEntry("minecraft:mob_effect|mymod:wither_touch"));
        assertFalse(runtime.store().claimOf(DynamicDefinitionType.ITEM, "mymod:ruby").stale());
    }

    @Test
    void defaultConfigEntryWithoutCallbackUsesDocumentedDefaults() {
        DynamicRegistryFacadeRuntime runtime = run("""
                DynamicRegistryEvents.dynamicRegistry(event => {
                    event.soundEvent('mymod:ping');
                });
                """, "server-registry-ready");
        var exposed = runtime.store().exposedEntry("minecraft:sound_event|mymod:ping");
        assertNotNull(exposed, "callback 可省略（全默认值）");
        assertTrue(exposed.definition().hasReading("fixedRange", "null"), "默认 fixedRange=null（抑制）");
        assertTrue(exposed.definition().hasReading("mode", "world"), "默认 mode=world");
    }

    @Test
    void unknownTypeNamesAreNotRegistrationCapability() {
        // An unknown type string is rejected while the member is parsed, since there is no generic catalog. The bus
        // swallows the listener exception, and the poison marker records the batch failure, so the batch is not
        // published.
        DynamicRegistryFacadeRuntime runtime = run("""
                DynamicRegistryEvents.dynamicRegistry(event => {
                    try { event.block('mymod:x', b => {}) } catch (e) { /* 脚本级吞掉只让脚本继续 */ }
                    try { event.register('entity_type', 'mymod:y', () => {}) } catch (e) { }
                    try { event.custom('mymod:z', 'fluid', b => {}) } catch (e) { }
                    event.item('mymod:good', b => { b.maxStackSize = 8 });
                });
                """, "server-registry-ready");

        assertEquals(1, runtime.store().exposedSnapshot().size(),
                "未知入口的失败不产生任何定义；合法入口照常收集");
        assertTrue(runtime.store().exposedSnapshot().containsKey("minecraft:item|mymod:good"));

        // The form that does not swallow the exception poisons the whole batch: a collection error makes preflight
        // reject the batch, so it is not published.
        DynamicRegistryFacadeRuntime poisoned = run("""
                DynamicRegistryEvents.dynamicRegistry(event => {
                    event.item('mymod:bad', b => { b.maxStackSize = 200 });
                });
                """, "server-registry-ready");
        assertTrue(poisoned.store().isEmpty(),
                "毒化批次不发布：store 零写入（失败不另起写入）");
    }

    @Test
    void groupMembersAreFrozenToTheSingleServerCollectionBus() {
        assertEquals(java.util.Set.of("dynamicRegistry"), DynamicRegistryEvents.GROUP.viewBuses().keySet(),
                "facade 组成员冻结为唯一收集总线（不复制第二 bus）");
        var holder = DynamicRegistryEvents.GROUP.getBusHolder("dynamicRegistry");
        assertTrue(holder.canApplyOn(ScriptType.SERVER), "SERVER 脚本可监听");
        assertFalse(holder.canApplyOn(ScriptType.CLIENT), "CLIENT 侧不可见");
        // STARTUP sees the SERVER bus, which follows the existing side rule (the same shape as
        // ServerEvents.recipes: startup scripts can attach server-side listeners ahead of time).
        // A separate group with its own lifecycle keeps it apart from the startup RegistryEvents.
        assertTrue(holder.canApplyOn(ScriptType.STARTUP), "STARTUP 侧沿用 SERVER 总线可见性语义");
    }

    @Test
    void payloadMemberDirectoryIsClosedToTheThreeVerifiedTypes() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                com.tkisor.nekojs.core.dynamic.plan.DynamicCandidateRegistryPlan plan =
                        new com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore().beginBatch();
                DynamicRegistryEventJS payload = new DynamicRegistryEventJS(plan);
                Object keys = payload.getMemberKeys();
                assertTrue(keys instanceof Object[] array && array.length == 3,
                        "成员目录只有三个冻结入口: " + java.util.Arrays.toString((Object[]) keys));
                context.getBindings("js").putMember("event", payload);
                // Call form: with hasMember=false Graal raises Unknown identifier, so the rejection is diagnosable.
                RuntimeException callError = org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class,
                        () -> context.eval("js", "event.block('x:y', b => {})"));
                assertTrue(callError.getMessage().contains("block"), callError.getMessage());
                // Read form: reading an unknown member of a ProxyObject with hasMember=false yields undefined
                // (Graal semantics) and grants no registration ability. The member catalog semantics live
                // in getMemberKeys, with its three frozen entries, and in the declaration surface.
                context.eval("js", "if (typeof event.block !== 'undefined') throw new Error('block must be undefined')");
                // The Java-side member catalog (probe, completion, diagnostics) reports errors with the full explanation.
                RuntimeException hostError = org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class, () -> payload.getMember("block"));
                assertTrue(hostError.getMessage().contains("no member 'block'"), hostError.getMessage());
                assertTrue(hostError.getMessage().contains("no generic type catalog"), hostError.getMessage());
                assertTrue(hostError.getMessage().contains("soundEvent"),
                        "错误列出冻结入口: " + hostError.getMessage());
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    @Test
    void typeNamesAndRegistryKeysAreNotInterchangeable() {
        // An unknown type string, including a registry key passed off as a type, is rejected while members
        // are resolved: no conversion path turns a string into a registration ability.
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore store =
                        new com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore();
                DynamicRegistryEventJS payload = new DynamicRegistryEventJS(store.beginBatch());
                context.getBindings("js").putMember("event", payload);
                Object[] keys = (Object[]) payload.getMemberKeys();
                for (String probe : java.util.List.of("minecraft:item", "item_type", "block", "custom",
                        "register", "sound_event")) {
                    assertTrue(DynamicDefinitionType.byApiName(probe).isEmpty(),
                            "'" + probe + "' 不是类型直达入口名");
                    assertFalse(java.util.Arrays.asList(keys).contains(probe),
                            "成员目录不含 '" + probe + "'");
                }
                // Bracket property access is undefined too (Graal semantics for a ProxyObject with hasMember=false).
                context.eval("js", """
                        if (typeof event['minecraft:item'] !== 'undefined') {
                            throw new Error('registry keys must not be members');
                        }
                        """);
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
        // The registry keys of the three typed entry points are the complete closed candidate set.
        assertEquals(java.util.Set.of("minecraft:item", "minecraft:sound_event", "minecraft:mob_effect"),
                com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition.supportedRegistryKeys());
    }
}
