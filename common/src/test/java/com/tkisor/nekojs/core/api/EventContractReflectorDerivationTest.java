package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 14 AC2：冻结事件进入 managed contract 的派生面——共同事件名、payload 成员、
 * side（tier）、dispatch 与 cancel 由 {@link EventContractReflector} 从运行时
 * {@link EventGroup} 反射派生（生产消费点 {@code NekoPluginRuntime.installManagedCallbackSchemas}
 * → {@code ManagedCallbackSchemaRegistry.installContractEvents}）。
 *
 * <p>钉住的契约：
 * <ul>
 *   <li>每个 bus 恰好派生一条 {@link NormativeApiContract.ContractEvent}（无重复）；
 *       server-only / client-only / mixed side 事件保持独立条目；</li>
 *   <li>tier 由 bus 的规范 {@code scriptType} 映射（STARTUP/SERVER/CLIENT）；</li>
 *   <li>dispatch = PLAIN/BY_ID，BY_ID 的 dispatchKeyType 固定 {@code "string"}
 *       （脚本侧注册键永远是字符串 id）；</li>
 *   <li>cancellable 三态来自 bus 的可取消性（无外部 predicate 时为 false）；</li>
 *   <li>payload 成员 = 事件类的 public 零参 getter（getXxx/isXxx），
 *       {@code neko$} mixin 别名与桥方法不进 payload。</li>
 * </ul>
 */
class EventContractReflectorDerivationTest {

    /** payload 反射样本：getXxx / isXxx 进 payload，neko$ 别名与带参方法不进。 */
    public static final class SamplePayload {
        public String getMessage() {
            return "m";
        }

        public boolean isCancelled() {
            return false;
        }

        public String neko$getAlias() {
            return "alias";
        }

        public String withArg(String arg) {
            return arg;
        }

        public void doThing() {}
    }

    /** 无 public 零参 getter 的载荷：payload 为空列表。 */
    public static final class EmptyPayload {
        public void run() {}
    }

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void eachBusDerivesExactlyOneContractEventWithTierDispatchCancelAndPayload() {
        EventGroup group = EventGroup.of("ServerEvents");
        group.server("plain", SamplePayload.class);
        group.server("dispatched", EmptyPayload.class, DispatchKey.string());
        group.add("cancellable", ScriptType.SERVER, EventBusJS.of(SamplePayload.class, true));

        List<NormativeApiContract.ContractEvent> events =
                EventContractReflector.extractEvents(List.of(group));

        assertEquals(3, events.size(), "one ContractEvent per bus, no duplicates");
        Map<String, NormativeApiContract.ContractEvent> byName = events.stream()
                .collect(Collectors.toMap(NormativeApiContract.ContractEvent::name, e -> e));

        for (NormativeApiContract.ContractEvent event : events) {
            assertEquals("ServerEvents", event.group(), "group name is carried");
            assertEquals(NormativeApiContract.EventTier.SERVER, event.tier(),
                    "tier maps the bus's canonical script type");
        }

        NormativeApiContract.ContractEvent plain = byName.get("plain");
        assertEquals(NormativeApiContract.Dispatch.PLAIN, plain.dispatch());
        assertNull(plain.dispatchKeyType(), "PLAIN events must not carry a dispatchKeyType");
        assertEquals(Boolean.FALSE, plain.cancellable(), "no external predicate -> not cancellable");
        Set<String> payloadNames = plain.payload().stream()
                .map(NormativeApiContract.ContractEventField::name)
                .collect(Collectors.toSet());
        assertEquals(Set.of("message", "cancelled"), payloadNames,
                "payload = public zero-arg getters; neko$ aliases and parameterized methods excluded");
        assertTrue(plain.payload().stream().allMatch(
                        f -> f.kind() == NormativeApiContract.FieldKind.NATIVE),
                "reflected fields are declared NATIVE (no portability promise)");

        NormativeApiContract.ContractEvent dispatched = byName.get("dispatched");
        assertEquals(NormativeApiContract.Dispatch.BY_ID, dispatched.dispatch());
        assertEquals("string", dispatched.dispatchKeyType(),
                "script-side dispatch keys are string ids");
        assertTrue(dispatched.payload().isEmpty(),
                "a payload class without public zero-arg getters reflects no fields");

        assertEquals(Boolean.TRUE, byName.get("cancellable").cancellable());
    }

    @Test
    void serverClientAndMixedSidesKeepDistinctEntries() {
        EventGroup serverOnly = EventGroup.of("ServerEvents");
        serverOnly.server("started", EmptyPayload.class);
        EventGroup clientOnly = EventGroup.of("ClientEvents");
        clientOnly.client("render", EmptyPayload.class);
        EventGroup mixed = EventGroup.of("MixedEvents");
        mixed.startup("early", EmptyPayload.class);
        mixed.server("late", EmptyPayload.class);

        List<NormativeApiContract.ContractEvent> events = EventContractReflector.extractEvents(
                List.of(serverOnly, clientOnly, mixed));

        assertEquals(4, events.size(), "each bus one entry: 1 server + 1 client + 2 mixed");
        Map<String, NormativeApiContract.EventTier> tierByQualifiedName = events.stream()
                .collect(Collectors.toMap(e -> e.group() + "." + e.name(),
                        NormativeApiContract.ContractEvent::tier));
        assertEquals(NormativeApiContract.EventTier.SERVER, tierByQualifiedName.get("ServerEvents.started"));
        assertEquals(NormativeApiContract.EventTier.CLIENT, tierByQualifiedName.get("ClientEvents.render"));
        assertEquals(NormativeApiContract.EventTier.STARTUP, tierByQualifiedName.get("MixedEvents.early"));
        assertEquals(NormativeApiContract.EventTier.SERVER, tierByQualifiedName.get("MixedEvents.late"),
                "mixed side keeps a distinct entry per bus instead of merging sides");

        // side 过滤口径与 catalog 一致：条目按各自规范 side 归属，不因 test(side) 宽容而合并
        Set<String> serverSideQualified = events.stream()
                .filter(e -> e.tier() == NormativeApiContract.EventTier.SERVER)
                .map(e -> e.group() + "." + e.name())
                .collect(Collectors.toSet());
        assertEquals(Set.of("ServerEvents.started", "MixedEvents.late"), serverSideQualified);
    }

    @Test
    void nullAndEmptyInputsDeriveEmptyEventLists() {
        assertEquals(List.of(), EventContractReflector.extractEvents(null));
        assertEquals(List.of(), EventContractReflector.extractEvents(List.of()));
    }

    /**
     * 事件契约条目进入回调 schema 后可被 {@code EventCallbackSourceValidator} 消费：
     * BY_ID 无 keyType / PLAIN 带 keyType 在 {@link NormativeApiContract.ContractEvent}
     * 构造期即被拒绝（可诊断失败前置到契约构造，而非运行时分发时）。
     */
    @Test
    void contractEventShapeViolationsFailAtConstruction() {
        assertFails(() -> new NormativeApiContract.ContractEvent(
                "G", "n", NormativeApiContract.EventTier.SERVER,
                NormativeApiContract.Dispatch.BY_ID, null, null, null, null),
                "BY_ID event requires dispatchKeyType");
        assertFails(() -> new NormativeApiContract.ContractEvent(
                "G", "n", NormativeApiContract.EventTier.SERVER,
                NormativeApiContract.Dispatch.PLAIN, "string", null, null, null),
                "PLAIN event must not have dispatchKeyType");
    }

    private static void assertFails(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
            assertFalse(expected.getMessage().isEmpty());
            return;
        }
        throw new AssertionError("expected IllegalArgumentException: " + message);
    }
}
