package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventSchemaRegistry;
import com.tkisor.nekojs.api.event.ManagedCallbackSchemaRegistry;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件回调成员 preflight 检查（反射路径 + 契约 events 注入路径）的聚焦测试。
 *
 * <p>覆盖：
 * <ol>
 *   <li>纯反射路径：{@code EventSchemaRegistry} 注册事件组后，{@code e.getServer()} 放行、
 *       {@code e.getServeer()} 报错并带拼写建议</li>
 *   <li>契约注入路径：{@code ManagedCallbackSchemaRegistry.installContractEvents} 后，
 *       契约 payload 字段（如 {@code server}）即使反射类没有也放行（契约即权威）</li>
 *   <li>并集：契约字段与反射方法名同时放行，互不误报</li>
 *   <li>别名：{@code const x = e; x.getServer()} 与直连等价</li>
 *   <li>ValParser 无法解析的语法：不抛异常、不挂死（防死循环保护 + 日志降级）</li>
 * </ol>
 */
class EventCallbackSourceValidatorTest {

    /** 反射路径用的事件类：有 getServer()（属性 server），无 message。 */
    public static class TestStartedEvent {
        public Object getServer() {
            return new Object();
        }
    }

    /** 链式测试用：getPlayer() 返回 PlayerJS。 */
    public static class ChainedEvent {
        public PlayerJS getPlayer() {
            return new PlayerJS();
        }

        public PlayerJS directPlayer = new PlayerJS();

        public PlayerJS find(String name) {
            return new PlayerJS();
        }

        public PlayerJS find(int id) {
            return new PlayerJS();
        }

        public String varargsJoin(String sep, String... parts) {
            return "";
        }
    }

    public static class PlayerJS {
        public Object getServer() {
            return new Object();
        }
    }

    /** 契约测试用的 ContractEvent 构造助手。 */
    private static NormativeApiContract.ContractEvent contractEvent(String group, String name, String... fields) {
        List<NormativeApiContract.ContractEventField> payload = new ArrayList<>();
        for (String field : fields) {
            payload.add(new NormativeApiContract.ContractEventField(
                    field, NormativeApiContract.FieldKind.NATIVE, null, null));
        }
        return new NormativeApiContract.ContractEvent(
                group, name, NormativeApiContract.EventTier.SERVER,
                NormativeApiContract.Dispatch.PLAIN, null, null, payload, null);
    }

    private final List<String> reported = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ScriptBindingSchema.clearAll();
        ManagedCallbackSchemaRegistry.clear();
        TestPlatformInit.ensureInitialized();
        ScriptErrorReporter.set((type, kind, throwable) ->
                reported.add(throwable.getMessage() == null ? kind : throwable.getMessage()));

        EventGroup group = EventGroup.of("TestEvents");
        group.server("started", TestStartedEvent.class);
        EventSchemaRegistry.registerGroup(group);
    }

    @AfterEach
    void tearDown() {
        ScriptErrorReporter.set(ScriptErrorReporter.Reporter.NOOP);
    }

    private static void registerBindingGroup(String group) {
        ScriptBindingSchema.register(ScriptType.SERVER, Map.of(
                group, new ScriptBindingSchema.BindingMembers(Set.of("started"))));
    }

    private static Path serverScript(String name) {
        return ScriptType.SERVER.path.resolve(name + ".js");
    }

    @Test
    void reflectionPathAllowsKnownGetterAndProperty() {
        registerBindingGroup("TestEvents");

        EventCallbackSourceValidator.validate(serverScript("ok"),
                "TestEvents.started((e) => { e.getServer(); e.server })");

        assertTrue(reported.isEmpty(), "known members must not be reported: " + reported);
    }

    @Test
    void reflectionPathRejectsUnknownMemberWithSuggestion() {
        registerBindingGroup("TestEvents");

        EventCallbackSourceValidator.validate(serverScript("typo"),
                "TestEvents.started((e) => { e.getServeer() })");

        assertEquals(1, reported.size(), "typo must be reported exactly once");
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.getFirst());
    }

    @Test
    void contractFieldsPassEvenWhenReflectionClassLacksThem() {
        ManagedCallbackSchemaRegistry.installContractEvents(List.of(
                contractEvent("ContractEvents", "chat", "message", "username")));
        registerBindingGroup("ContractEvents");

        // message/username 在契约 payload 中但无对应事件类 → 契约即权威，放行
        EventCallbackSourceValidator.validate(serverScript("contract"),
                "ContractEvents.chat((e) => { e.message; e.username })");

        assertTrue(reported.isEmpty(), "contract payload fields must pass: " + reported);
    }

    @Test
    void unionOfContractAndReflectionBothPass() {
        ManagedCallbackSchemaRegistry.installContractEvents(List.of(
                contractEvent("ContractEvents", "started", "server")));
        EventGroup group = EventGroup.of("ContractEvents");
        group.server("started", TestStartedEvent.class);
        EventSchemaRegistry.registerGroup(group);
        registerBindingGroup("ContractEvents");

        // server 来自契约；getServer 来自反射。并集检查两者都放行。
        EventCallbackSourceValidator.validate(serverScript("union"),
                "ContractEvents.started((e) => { e.server; e.getServer() })");

        assertTrue(reported.isEmpty(), "union of contract+reflection must pass: " + reported);
    }

    @Test
    void unknownMemberReportedEvenWithContractInstalled() {
        ManagedCallbackSchemaRegistry.installContractEvents(List.of(
                contractEvent("ContractEvents", "started", "server")));
        registerBindingGroup("ContractEvents");

        EventCallbackSourceValidator.validate(serverScript("unknown"),
                "ContractEvents.started((e) => { e.notAMember })");

        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("notAMember"), reported.getFirst());
    }

    @Test
    void aliasRemapChecksThroughLocalVariable() {
        registerBindingGroup("TestEvents");

        EventCallbackSourceValidator.validate(serverScript("alias"),
                "TestEvents.started((e) => { const x = e; x.getServeer() })");

        assertEquals(1, reported.size(), "alias member access must be checked: " + reported);
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
    }

    @Test
    void aliasToKnownMemberPasses() {
        registerBindingGroup("TestEvents");

        EventCallbackSourceValidator.validate(serverScript("alias-ok"),
                "TestEvents.started((e) => { const x = e; x.getServer() })");

        assertTrue(reported.isEmpty(), "alias to known member must pass: " + reported);
    }

    @Test
    void unparseableInputDoesNotThrowOrHang() {
        registerBindingGroup("TestEvents");

        // ValParser 无法完整解析的语法（未闭合括号/孤立分号/裸操作符）：
        // 不得抛异常，也不得死循环（parseProgram 有未消费前进保护）。
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                EventCallbackSourceValidator.validate(serverScript("weird"),
                        "TestEvents.started((e) => { ; ; @#$ { ( ( }"));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                GlobalBindingMemberValidator.validate(serverScript("weird"),
                        "TestEvents.started((e) => { e.getServer( ; }"));
    }

    @Test
    void contractSchemaKeyedByGroupAndEventName() {
        ManagedCallbackSchemaRegistry.installContractEvents(List.of(
                contractEvent("ServerEvents", "started", "server")));

        ManagedCallbackSchemaRegistry.CallbackSchema schema =
                ManagedCallbackSchemaRegistry.resolve("ServerEvents", "started");

        assertEquals("ServerEvents.started", schema.displayName());
        assertTrue(schema.memberNames().contains("server"));
    }

    // ==================== 动态成员访问（引号 / const key） ====================

    @Test
    void quotedBracketKnownMethodPasses() {
        registerBindingGroup("TestEvents");
        EventCallbackSourceValidator.validate(serverScript("br-ok"),
                "TestEvents.started((e) => { e['getServer']() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void quotedBracketTypoReportedWithSuggestion() {
        registerBindingGroup("TestEvents");
        EventCallbackSourceValidator.validate(serverScript("br-typo"),
                "TestEvents.started((e) => { e['getServeer']() })");
        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.getFirst());
    }

    @Test
    void doubleQuotedBracketPropertyPasses() {
        registerBindingGroup("TestEvents");
        EventCallbackSourceValidator.validate(serverScript("br-prop"),
                "TestEvents.started((e) => { e[\"server\"] })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void constStringComputedKeyChecked() {
        registerBindingGroup("TestEvents");
        EventCallbackSourceValidator.validate(serverScript("ck-ok"),
                "TestEvents.started((e) => { const key = 'getServer'; e[key]() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void constStringComputedKeyTypoReported() {
        registerBindingGroup("TestEvents");
        EventCallbackSourceValidator.validate(serverScript("ck-typo"),
                "TestEvents.started((e) => { const key = 'getServeer'; e[key]() })");
        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.getFirst());
    }

    @Test
    void runtimeDynamicKeyNotReported() {
        registerBindingGroup("TestEvents");
        EventCallbackSourceValidator.validate(serverScript("dyn"),
                "TestEvents.started((e) => { e[keyFromNetwork]() })");
        assertTrue(reported.isEmpty(), "runtime dynamic key must not be diagnosed: " + reported);
    }

    @Test
    void payloadAliasWithConstKey() {
        registerBindingGroup("TestEvents");
        EventCallbackSourceValidator.validate(serverScript("alias-key"),
                "TestEvents.started((e) => { const x = e; const key = 'getServer'; x[key]() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void letVarKeyNotPropagatedAsConstant() {
        registerBindingGroup("TestEvents");
        // let key 可变：不做常量传播，不诊断（也不误报）
        EventCallbackSourceValidator.validate(serverScript("let-key"),
                "TestEvents.started((e) => { let key = 'getServeer'; e[key]() })");
        assertTrue(reported.isEmpty(), "let key must not be treated as constant: " + reported);
    }

    @Test
    void blockShadowingDoesNotLeakConstKey() {
        registerBindingGroup("TestEvents");
        EventCallbackSourceValidator.validate(serverScript("shadow"),
                "TestEvents.started((e) => { const key = 'getServer'; { const key = 'getServeer'; e[key]() } })");
        assertEquals(1, reported.size(), "shadowed key must be used in inner block");
    }

    // ==================== 链式返回值类型传播 ====================

    private void registerChainedGroup() {
        EventGroup group = EventGroup.of("ChainedEvents");
        group.server("started", ChainedEvent.class);
        EventSchemaRegistry.registerGroup(group);
        ScriptBindingSchema.register(ScriptType.SERVER, Map.of(
                "ChainedEvents", new ScriptBindingSchema.BindingMembers(Set.of("started"))));
    }

    @Test
    void chainedMethodReturnTypePropagates() {
        registerChainedGroup();
        EventCallbackSourceValidator.validate(serverScript("chain-ok"),
                "ChainedEvents.started((e) => { e.getPlayer().getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void chainedSecondHopTypoReported() {
        registerChainedGroup();
        EventCallbackSourceValidator.validate(serverScript("chain-typo"),
                "ChainedEvents.started((e) => { e.getPlayer().getServeer() })");
        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.getFirst());
    }

    @Test
    void chainedGetterPropertyPropagates() {
        registerChainedGroup();
        EventCallbackSourceValidator.validate(serverScript("chain-prop"),
                "ChainedEvents.started((e) => { e.player.getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void chainedPublicFieldPropagates() {
        registerChainedGroup();
        EventCallbackSourceValidator.validate(serverScript("chain-field"),
                "ChainedEvents.started((e) => { e.directPlayer.getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void overloadedCallUnionAcceptsMemberOnAnyCandidate() {
        registerChainedGroup();
        // find(String) 与 find(int) 返回类型相同（PlayerJS），getServer 都支持
        EventCallbackSourceValidator.validate(serverScript("chain-overload"),
                "ChainedEvents.started((e) => { e.find('x').getServer(); e.find(1).getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void varargsCallPropagatesReturnType() {
        registerChainedGroup();
        // varargsJoin(sep, parts...) 返回 String：链式到 String 成员不深入（无对象成员），不误报
        EventCallbackSourceValidator.validate(serverScript("chain-varargs"),
                "ChainedEvents.started((e) => { e.varargsJoin('|').length })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void chainedCallToMissingMethodOnRootReported() {
        registerChainedGroup();
        EventCallbackSourceValidator.validate(serverScript("chain-root-typo"),
                "ChainedEvents.started((e) => { e.getPlayr().getServer() })");
        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("getPlayr"), reported.getFirst());
    }

    @Test
    void optionalChainedComputedKeyChecked() {
        registerChainedGroup();
        EventCallbackSourceValidator.validate(serverScript("opt-chain"),
                "ChainedEvents.started((e) => { const k = 'getPlayer'; e?.[k]().getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }
}
