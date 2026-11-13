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
 * Focused tests for the event callback member preflight, which runs on the reflection path and on
 * the contract events injection path.
 *
 * <p>Covers:
 * <ol>
 *   <li>Reflection only: after {@code EventSchemaRegistry} registers an event group,
 *       {@code e.getServer()} passes and {@code e.getServeer()} is reported with a spelling
 *       suggestion</li>
 *   <li>Contract injection: after
 *       {@code ManagedCallbackSchemaRegistry.installContractEvents}, contract payload fields such
 *       as {@code server} pass even when the reflective class lacks them; the contract is
 *       authoritative</li>
 *   <li>Union: contract fields and reflective method names both pass, with no false report</li>
 *   <li>Alias: {@code const x = e; x.getServer()} behaves like the direct call</li>
 *   <li>Syntax ValParser cannot parse: no exception and no hang (loop guard plus degraded
 *       logging)</li>
 * </ol>
 */
class EventCallbackSourceValidatorTest {

    /** Event class for the reflection path: it has getServer() (property server) and no message. */
    public static class TestStartedEvent {
        public Object getServer() {
            return new Object();
        }
    }

    /** Fixture for chained calls: getPlayer() returns a PlayerJS. */
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

    /** Binding class for builder callback signature inference: plain methods that take a callback. */
    public static class RegistryLikeBinding {
        public Object item(String id, java.util.function.Consumer<ItemBuilderLike> config) {
            return null;
        }

        public Object soundEvent(String id, java.util.function.Consumer<SoundBuilderLike> config) {
            return null;
        }

        /** The parameter is not generic, so the signature cannot infer the callback parameter type
         * and the whole check must be skipped. */
        public Object opaque(String id, Object config) {
            return null;
        }
    }

    public static class ItemBuilderLike {
        public ItemBuilderLike maxStackSize(int size) {
            return this;
        }

        public ItemBuilderLike rarity(String rarity) {
            return this;
        }
    }

    public static class SoundBuilderLike {
        public SoundBuilderLike fixedRange(float range) {
            return this;
        }
    }

    /** Builds ContractEvent values for the contract tests. */
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
    private ScriptBindingSchema.View view;

    @BeforeEach
    void setUp() {
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

    private void registerBindingGroup(String group) {
        view = new ScriptBindingSchema.View(Map.of(
                group, new ScriptBindingSchema.BindingMembers(Set.of("started"))), Set.of());
    }

    /** Non-event (builder) callback path: valueClasses carries the binding classes, so the
     * parameter type comes from the method signature. */
    private void registerRegistryBinding() {
        view = new ScriptBindingSchema.View(Map.of(
                "DynamicRegistry", new ScriptBindingSchema.BindingMembers(
                        Set.of("item", "soundEvent", "opaque"), Set.of(RegistryLikeBinding.class))), Set.of());
    }

    private static Path serverScript(String name) {
        return com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve(name + ".js");
    }

    private void validate(Path file, String source) {
        EventCallbackSourceValidator.validate(file, source, view);
    }

    private void validateGlobal(Path file, String source) {
        GlobalBindingMemberValidator.validate(file, source, view);
    }

    @Test
    void reflectionPathAllowsKnownGetterAndProperty() {
        registerBindingGroup("TestEvents");

        validate(serverScript("ok"),
                "TestEvents.started((e) => { e.getServer(); e.server })");

        assertTrue(reported.isEmpty(), "known members must not be reported: " + reported);
    }

    @Test
    void reflectionPathRejectsUnknownMemberWithSuggestion() {
        registerBindingGroup("TestEvents");

        validate(serverScript("typo"),
                "TestEvents.started((e) => { e.getServeer() })");

        assertEquals(1, reported.size(), "typo must be reported exactly once");
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.getFirst());
    }

    @Test
    void arrayReceiversPreserveEventPayloadValidationInsideAndOutsideElements() {
        registerBindingGroup("TestEvents");
        for (String source : List.of(
                "TestEvents.started(e => [e.getServer(), e.getServeer()].includes(null));",
                "[TestEvents.started(e => { e.getServeer(); })].length;")) {
            reported.clear();
            validate(serverScript("array-payload"), source);
            assertEquals(1, reported.size(), "The payload typo must remain visible: " + reported);
            assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
        }
    }

    @Test
    void contractFieldsPassEvenWhenReflectionClassLacksThem() {
        ManagedCallbackSchemaRegistry.installContractEvents(List.of(
                contractEvent("ContractEvents", "chat", "message", "username")));
        registerBindingGroup("ContractEvents");

        // message and username are in the contract payload but have no matching event class; the
        // contract wins, so they pass
        validate(serverScript("contract"),
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

        // server comes from the contract and getServer comes from reflection; the union check
        // accepts both.
        validate(serverScript("union"),
                "ContractEvents.started((e) => { e.server; e.getServer() })");

        assertTrue(reported.isEmpty(), "union of contract+reflection must pass: " + reported);
    }

    @Test
    void unknownMemberReportedEvenWithContractInstalled() {
        ManagedCallbackSchemaRegistry.installContractEvents(List.of(
                contractEvent("ContractEvents", "started", "server")));
        registerBindingGroup("ContractEvents");

        validate(serverScript("unknown"),
                "ContractEvents.started((e) => { e.notAMember })");

        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("notAMember"), reported.getFirst());
    }

    @Test
    void aliasRemapChecksThroughLocalVariable() {
        registerBindingGroup("TestEvents");

        validate(serverScript("alias"),
                "TestEvents.started((e) => { const x = e; x.getServeer() })");

        assertEquals(1, reported.size(), "alias member access must be checked: " + reported);
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
    }

    @Test
    void aliasToKnownMemberPasses() {
        registerBindingGroup("TestEvents");

        validate(serverScript("alias-ok"),
                "TestEvents.started((e) => { const x = e; x.getServer() })");

        assertTrue(reported.isEmpty(), "alias to known member must pass: " + reported);
    }

    /**
     * Regression for the false report on builder callbacks:
     * {@code DynamicRegistry.item('id', b => b.maxStackSize(64))} was reported as
     * {@code 'maxStackSize' not in DynamicRegistry}. The cause: a builder callback is not an event
     * registration, so when managed and eventClass are both null, rootValue falls back to an empty
     * member table and every member access is flagged. Now the signature
     * ({@code Consumer<ItemBuilder>}) supplies the parameter type, and members are checked against
     * ItemBuilder.
     */
    @Test
    void builderCallbackParamTypedFromBindingSignature() {
        registerRegistryBinding();

        validate(serverScript("builder-ok"),
                "DynamicRegistry.item('mymod:cool_gem', (b) => { b.maxStackSize(64); b.rarity('epic') })");

        assertTrue(reported.isEmpty(), "builder callback members must pass: " + reported);
    }

    @Test
    void builderCallbackTypoReportedAgainstBuilderType() {
        registerRegistryBinding();

        validate(serverScript("builder-typo"),
                "DynamicRegistry.soundEvent('mymod:ding', (b) => { b.fixedRang(16) })");

        assertEquals(1, reported.size(), "builder typo must be reported exactly once: " + reported);
        assertTrue(reported.getFirst().contains("fixedRang"), reported.getFirst());
        assertTrue(reported.getFirst().contains("ItemBuilderLike") == false
                && reported.getFirst().contains("SoundBuilderLike"), reported.getFirst());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.getFirst());
    }

    /** When the signature cannot infer the parameter type (a non-generic parameter), the whole
     * check is skipped: a missed check is better than a false report. */
    @Test
    void builderCallbackWithOpaqueSignatureSkipped() {
        registerRegistryBinding();

        validate(serverScript("builder-opaque"),
                "DynamicRegistry.opaque('mymod:x', (b) => { b.anythingAtAll() })");

        assertTrue(reported.isEmpty(), "unresolvable callback param must not be reported: " + reported);
    }

    /** A builder callback nested inside an event callback: the outer one is checked against the
     * event class and the inner one against the binding signature, with no interference. */
    @Test
    void builderCallbackNestedInsideEventCallback() {
        registerBindingGroup("TestEvents");
        registerRegistryBinding();

        validate(serverScript("nested"),
                "TestEvents.started((e) => { e.getServer(); DynamicRegistry.item('mymod:x', (b) => { b.maxStackSize(8) }) })");

        assertTrue(reported.isEmpty(), "nested builder callback inside event callback must pass: " + reported);
    }

    @Test
    void unparseableInputDoesNotThrowOrHang() {
        registerBindingGroup("TestEvents");

        // Syntax ValParser cannot fully parse (unclosed brackets, stray semicolons, bare
        // operators): no exception and no infinite loop; parseProgram has a guard that advances on
        // unconsumed input.
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                validate(serverScript("weird"),
                        "TestEvents.started((e) => { ; ; @#$ { ( ( }"));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                validateGlobal(serverScript("weird"),
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

    // ==================== Dynamic member access (quoted keys / const keys) ====================

    @Test
    void quotedBracketKnownMethodPasses() {
        registerBindingGroup("TestEvents");
        validate(serverScript("br-ok"),
                "TestEvents.started((e) => { e['getServer']() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void quotedBracketTypoReportedWithSuggestion() {
        registerBindingGroup("TestEvents");
        validate(serverScript("br-typo"),
                "TestEvents.started((e) => { e['getServeer']() })");
        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.getFirst());
    }

    @Test
    void doubleQuotedBracketPropertyPasses() {
        registerBindingGroup("TestEvents");
        validate(serverScript("br-prop"),
                "TestEvents.started((e) => { e[\"server\"] })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void constStringComputedKeyChecked() {
        registerBindingGroup("TestEvents");
        validate(serverScript("ck-ok"),
                "TestEvents.started((e) => { const key = 'getServer'; e[key]() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void constStringComputedKeyTypoReported() {
        registerBindingGroup("TestEvents");
        validate(serverScript("ck-typo"),
                "TestEvents.started((e) => { const key = 'getServeer'; e[key]() })");
        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.getFirst());
    }

    @Test
    void runtimeDynamicKeyNotReported() {
        registerBindingGroup("TestEvents");
        validate(serverScript("dyn"),
                "TestEvents.started((e) => { e[keyFromNetwork]() })");
        assertTrue(reported.isEmpty(), "runtime dynamic key must not be diagnosed: " + reported);
    }

    @Test
    void payloadAliasWithConstKey() {
        registerBindingGroup("TestEvents");
        validate(serverScript("alias-key"),
                "TestEvents.started((e) => { const x = e; const key = 'getServer'; x[key]() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void letVarKeyNotPropagatedAsConstant() {
        registerBindingGroup("TestEvents");
        // A let key is mutable, so there is no constant propagation, no diagnostic and no false
        // report
        validate(serverScript("let-key"),
                "TestEvents.started((e) => { let key = 'getServeer'; e[key]() })");
        assertTrue(reported.isEmpty(), "let key must not be treated as constant: " + reported);
    }

    @Test
    void blockShadowingDoesNotLeakConstKey() {
        registerBindingGroup("TestEvents");
        validate(serverScript("shadow"),
                "TestEvents.started((e) => { const key = 'getServer'; { const key = 'getServeer'; e[key]() } })");
        assertEquals(1, reported.size(), "shadowed key must be used in inner block");
    }

    // ==================== Chained return type propagation ====================

    private void registerChainedGroup() {
        EventGroup group = EventGroup.of("ChainedEvents");
        group.server("started", ChainedEvent.class);
        EventSchemaRegistry.registerGroup(group);
        view = new ScriptBindingSchema.View(Map.of(
                "ChainedEvents", new ScriptBindingSchema.BindingMembers(Set.of("started"))), Set.of());
    }

    @Test
    void chainedMethodReturnTypePropagates() {
        registerChainedGroup();
        validate(serverScript("chain-ok"),
                "ChainedEvents.started((e) => { e.getPlayer().getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void chainedSecondHopTypoReported() {
        registerChainedGroup();
        validate(serverScript("chain-typo"),
                "ChainedEvents.started((e) => { e.getPlayer().getServeer() })");
        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("getServeer"), reported.getFirst());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.getFirst());
    }

    @Test
    void chainedGetterPropertyPropagates() {
        registerChainedGroup();
        validate(serverScript("chain-prop"),
                "ChainedEvents.started((e) => { e.player.getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void chainedPublicFieldPropagates() {
        registerChainedGroup();
        validate(serverScript("chain-field"),
                "ChainedEvents.started((e) => { e.directPlayer.getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void overloadedCallUnionAcceptsMemberOnAnyCandidate() {
        registerChainedGroup();
        // find(String) and find(int) both return PlayerJS, and both support getServer
        validate(serverScript("chain-overload"),
                "ChainedEvents.started((e) => { e.find('x').getServer(); e.find(1).getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void varargsCallPropagatesReturnType() {
        registerChainedGroup();
        // varargsJoin(sep, parts...) returns String: chaining into a String member stops there
        // (String has no object members), so nothing is reported
        validate(serverScript("chain-varargs"),
                "ChainedEvents.started((e) => { e.varargsJoin('|').length })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void chainedCallToMissingMethodOnRootReported() {
        registerChainedGroup();
        validate(serverScript("chain-root-typo"),
                "ChainedEvents.started((e) => { e.getPlayr().getServer() })");
        assertEquals(1, reported.size());
        assertTrue(reported.getFirst().contains("getPlayr"), reported.getFirst());
    }

    @Test
    void optionalChainedComputedKeyChecked() {
        registerChainedGroup();
        validate(serverScript("opt-chain"),
                "ChainedEvents.started((e) => { const k = 'getPlayer'; e?.[k]().getServer() })");
        assertTrue(reported.isEmpty(), reported.toString());
    }
}
