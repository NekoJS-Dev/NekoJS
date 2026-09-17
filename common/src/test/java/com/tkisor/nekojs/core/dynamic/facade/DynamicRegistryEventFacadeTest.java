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
 * 票 16 AC2/AC3 的 facade 行为（真实 GraalJS + 生产同款 {@link EventGroupJS} 绑定）：
 * 类型直达入口收集、无通用 type catalog（未知 type 字符串不是注册能力）、候选范围封闭、
 * 收集失败毒化整批（AC1「失败不另起写入」的初次触发路径）。
 */
class DynamicRegistryEventFacadeTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** 在真实 GraalJS SERVER 上下文里执行脚本（监听经生产同款组绑定注册），再触发收集。 */
    private static DynamicRegistryFacadeRuntime run(String script, String trigger) {
        DynamicRegistryFacadeRuntime runtime = new DynamicRegistryFacadeRuntime();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("DynamicRegistryEvents",
                        new EventGroupJS(DynamicRegistryEvents.GROUP, ScriptType.SERVER));
                context.eval("js", script);
                // 初次候选（生产：server registry ready 触发；本测试在 Context 存活期内驱动）
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
        // 未知 type 字符串：成员解析期拒绝（无通用 catalog）；监听器异常被总线吞掉，
        // 毒化标记承载「整批失败」——批次不发布
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

        // 不吞异常的形态：毒化整批（收集错误 → preflight 拒绝 → 不发布）
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
        // STARTUP 可见 SERVER 总线是既有 side 语义（与 ServerEvents.recipes 同款——启动脚本
        // 可以预挂服务器侧监听）；与启动期 RegistryEvents 的分离靠独立组/独立生命周期表达
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
                // 调用形态：Graal 在 hasMember=false 时给出 Unknown identifier（可诊断拒绝）
                RuntimeException callError = org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class,
                        () -> context.eval("js", "event.block('x:y', b => {})"));
                assertTrue(callError.getMessage().contains("block"), callError.getMessage());
                // 读取形态：ProxyObject hasMember=false 的未知成员读取为 undefined（Graal 语义），
                // 不产生注册能力；详细成员目录语义由 getMemberKeys（三个冻结入口）与声明面承载
                context.eval("js", "if (typeof event.block !== 'undefined') throw new Error('block must be undefined')");
                // Java 侧成员目录（probe/补全/诊断）错误带完整语义说明
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
        // 未知 type 字符串（含用目标注册表键冒充 type 的写法）在成员解析期即被拒绝：
        // 没有「字符串 → 注册能力」的转换通道（AC3）
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
                // 括号取属性同样是 undefined（ProxyObject hasMember=false 的 Graal 语义）
                context.eval("js", """
                        if (typeof event['minecraft:item'] !== 'undefined') {
                            throw new Error('registry keys must not be members');
                        }
                        """);
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
        // 三个类型直达入口的注册表键就是候选范围的封闭集合
        assertEquals(java.util.Set.of("minecraft:item", "minecraft:sound_event", "minecraft:mob_effect"),
                com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition.supportedRegistryKeys());
    }
}
