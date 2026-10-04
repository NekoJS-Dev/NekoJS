package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局绑定成员 preflight（事件外场景）的聚焦测试：脚本任意位置对 schema 绑定
 * （Utils/Item/…含事件组）的成员访问拼写检查。生产 schema 由
 * {@code ScriptEnvironmentFactory} 注册（环境绑定 + managed 全局 + 事件组）。
 */
class GlobalBindingMemberValidatorTest {

    public static class UtilsJS {
        public void serverTell(String message) {
        }

        public Object getServer() {
            return new Object();
        }
    }

    /** 链式类型流用：of 返回 TestStackJS。 */
    public static class TestItemJS {
        public static TestStackJS of(String id) {
            return new TestStackJS();
        }

        public static TestStackJS empty() {
            return new TestStackJS();
        }
    }

    public static class TestStackJS {
        public TestStackJS withCount(int count) {
            return this;
        }

        public String getId() {
            return "";
        }
    }

    private final List<String> reported = new ArrayList<>();
    private ScriptBindingSchema.View view;

    @BeforeEach
    void setUp() {
        TestPlatformInit.ensureInitialized();
        ScriptErrorReporter.set((type, kind, t) -> reported.add(String.valueOf(t.getMessage())));
        view = new ScriptBindingSchema.View(Map.of(
                "Utils", new ScriptBindingSchema.BindingMembers(JavaMemberIndex.allMembersOf(UtilsJS.class)),
                "Item", new ScriptBindingSchema.BindingMembers(
                        JavaMemberIndex.allMembersOf(TestItemJS.class), Set.of(TestItemJS.class)),
                 "ServerEvents", new ScriptBindingSchema.BindingMembers(Set.of("recipes", "started"))),
        // 生产环境由 ScriptEnvironmentFactory 从运行中 Context 收割；测试给出最小内置集
                Set.of("console", "Math", "JSON", "globalThis", "this", "arguments", "super"));
    }

    @AfterEach
    void tearDown() {
        ScriptErrorReporter.set(ScriptErrorReporter.Reporter.NOOP);
    }

    private static Path file(String name) {
        return com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve(name + ".js");
    }

    private void validate(Path file, String source) {
        GlobalBindingMemberValidator.validate(file, source, view);
    }

    @Test
    void flagsTypoedBindingMemberAnywhereInTheFile() {
        validate(file("typo"),
                "Utils.serverTel('hi')\r\nServerEvents.recipes(event => {\r\n  Utils.serverTel2('x')\r\n})\r\n");

        assertTrue(reported.stream().anyMatch(m -> m.contains("'Utils' has no member 'serverTel'")),
                "top-level typo must be reported: " + reported);
        assertTrue(reported.stream().anyMatch(m -> m.contains("'serverTel2'")),
                "typos inside callbacks must be reported too: " + reported);
    }

    @Test
    void flagsTypoedEventNameOnGroupBinding() {
        validate(file("evt"), "ServerEvents.recipez(event => {})\r\n");

        assertTrue(reported.stream().anyMatch(m -> m.contains("'ServerEvents' has no member 'recipez'")),
                "event-name typos on group bindings must be reported: " + reported);
    }

    @Test
    void knownMembersAndConstAliasAreNotFlagged() {
        validate(file("ok"),
                "Utils.serverTell('hi')\r\nconst u = Utils\r\nu.getServer()\r\nServerEvents.recipes(event => {})\r\n");

        assertTrue(reported.isEmpty(), "known members (direct + const alias) must not be reported: " + reported);
    }

    @Test
    void chainedMemberTypoIsFlaggedViaTypeFlow() {
        validate(file("chain"),
                "const s = Item.of('minecraft:stone')\r\ns.withCont(3)\r\nItem.empty().getIdd()\r\n");

        assertTrue(reported.stream().anyMatch(m -> m.contains("no member 'withCont'")),
                "second-level typo via local must be reported: " + reported);
        assertTrue(reported.stream().anyMatch(m -> m.contains("no member 'getIdd'")),
                "chained typo on call result must be reported: " + reported);
    }

    @Test
    void chainedLegitimateAccessIsNotFlagged() {
        validate(file("chainok"),
                "Item.of('minecraft:stone').withCount(3).getId()\r\n");

        assertTrue(reported.isEmpty(), "legit chains must not be reported: " + reported);
    }

    @Test
    void unknownIdentifierAsObjectOrCalleeIsFlagged() {
        validate(file("unknown"),
                "Util.serverTell('hi')\r\nqwq()\r\n");

        assertTrue(reported.stream().anyMatch(m -> m.contains("Unknown identifier 'Util'")),
                "typoed binding name must be reported: " + reported);
        assertTrue(reported.stream().anyMatch(m -> m.contains("Unknown identifier 'qwq'")),
                "unknown call target must be reported: " + reported);
    }

    @Test
    void everyModuleDeclaratorIsKnownInsideNamedFunctions() {
        validate(file("multi-declarator-lifecycle"),
                "let firstHost, firstRoot, secondHost, secondRoot;\n"
                        + "function replaceScreen() { secondHost.bindRoot(secondRoot); }\n"
                        + "function closeScreen() { firstRoot.close(); secondRoot.close(); }\n");
        assertTrue(reported.isEmpty(), "every module declarator must be known: " + reported);
    }

    @Test
    void laterInitializedDeclaratorsRetainBindingAndReturnTypeChecks() {
        validate(file("multi-declarator-types"),
                "const unused = 1, helper = Utils, stack = Item.of('minecraft:stone');\n"
                        + "helper.serverTel('bad'); stack.withCont(2);\n");
        assertTrue(reported.stream().noneMatch(message -> message.contains("Unknown identifier")),
                "later declarators must remain known: " + reported);
        assertTrue(reported.stream().anyMatch(message -> message.contains("no member 'serverTel'")),
                "later binding aliases must still be checked: " + reported);
        assertTrue(reported.stream().anyMatch(message -> message.contains("no member 'withCont'")),
                "later initializer return types must still be checked: " + reported);
    }

    @Test
    void nestedInitializerCommasDoNotDeclareReferencedNames() {
        validate(file("multi-declarator-nesting"),
                "let seed = (left, right), values = [left, right], later;\n"
                        + "function use() { later.bindRoot(); right.bindRoot(); }\n");
        assertTrue(reported.stream().noneMatch(message -> message.contains("Unknown identifier 'later'")),
                "declaration after nested initializers must be known: " + reported);
        assertTrue(reported.stream().anyMatch(message -> message.contains("Unknown identifier 'right'")),
                "nested comma operands must not be mistaken for declarations: " + reported);
    }

    @Test
    void compoundInitializersKeepMemberAndUnknownIdentifierDiagnostics() {
        for (String expression : List.of("[Utils.serverTel(), missing.call(), absent()]",
                "true && Utils.serverTel() && missing.call() && absent()",
                "1 + Utils.serverTel() + missing.call() + absent()",
                "true ? Utils.serverTel() : missing.call(absent())")) {
            reported.clear();
            validate(file("initializer-diagnostics"), "const value = " + expression + ", later = Utils;");
            assertTrue(reported.stream().anyMatch(message -> message.contains("no member 'serverTel'")),
                    expression + ": " + reported);
            assertTrue(reported.stream().anyMatch(message -> message.contains("Unknown identifier 'missing'")),
                    expression + ": " + reported);
            assertTrue(reported.stream().anyMatch(message -> message.contains("Unknown identifier 'absent'")),
                    expression + ": " + reported);
        }
    }

    @Test
    void initializedFunctionsAndArrowsKeepAllParametersAndDeclaratorsKnown() {
        validate(file("initializer-functions"),
                "const first = function named(firstArg, secondArg) {\n"
                        + "  let localFirst, localSecond;\n"
                        + "  firstArg.call(); secondArg.call(); localFirst.call(); localSecond.call();\n"
                        + "}, second = (arrowFirst, arrowSecond) => {\n"
                        + "  const nested = function(innerArg) { innerArg.call(); };\n"
                        + "  arrowFirst.call(); arrowSecond.call(); nested();\n"
                        + "}, third = function(lastArg) { lastArg.call(); }, compact = value=>value.call(), "
                        + "compactPair = (left,right)=>left.call(right);\n"
                        + "first(); second(); third(); named(); compact(); compactPair();\n");
        assertTrue(reported.isEmpty(), "initializer declarations and parameters must stay known: " + reported);
    }

    @Test
    void nestedInitializerBodiesStillReportMissingMembersAndIdentifiers() {
        validate(file("nested-initializer-diagnostics"),
                "const callback = (arg) => { const nested = function(inner) {\n"
                        + "  const values = [Utils.serverTel(), missing.call()];\n"
                        + "  inner.call(); arg.call();\n"
                        + "}; nested(); };\n");
        assertTrue(reported.stream().anyMatch(message -> message.contains("no member 'serverTel'")),
                "nested initializer member must be checked: " + reported);
        assertTrue(reported.stream().anyMatch(message -> message.contains("Unknown identifier 'missing'")),
                "nested initializer identifier must be checked: " + reported);
        assertTrue(reported.stream().noneMatch(message -> message.contains("Unknown identifier 'inner'")
                        || message.contains("Unknown identifier 'arg'") || message.contains("Unknown identifier 'nested'")),
                "nested parameters and declarations must be known: " + reported);
    }

    @Test
    void newStatementPrefixOperatorsDoNotHideDiagnosticsBehindInitializers() {
        for (String operator : List.of("!", "++", "--")) {
            reported.clear();
            validate(file("initializer-asi"), "let value = Utils\n" + operator + "missing.field;\n");
            assertTrue(reported.stream().anyMatch(message -> message.contains("Unknown identifier 'missing'")),
                    operator + ": " + reported);
        }
    }

    @Test
    void directRegistrationCallbackWithTwoParametersKeepsBothParametersInScope() {
        view = new ScriptBindingSchema.View(Map.of(
                "ClientEvents", new ScriptBindingSchema.BindingMembers(Set.of("hudRender"))),
                Set.of("console", "Math", "JSON", "globalThis", "this", "arguments", "super"));

        validate(file("hud-two-params"),
                "ClientEvents.hudRender('demo:badge', { layer: 'foreground', priority: 100 }, (ctx, gui) => {\n"
                        + "  ctx.rect(4, 18, 96, 12, 0x40000000)\n"
                        + "  ctx.text('DASH READY', 6, 20, 0xFF55FF55)\n"
                        + "})\n");

        assertTrue(reported.isEmpty(),
                "direct registration callback parameters must stay in scope: " + reported);
    }

    @Test
    void typeofAndBareIdentifiersAreNotFlagged() {
        // typeof 的操作数会被 ValParser 泄漏为独立语句（裸标识符），报了必误报
        validate(file("typeof"),
                "if (typeof Java !== 'undefined') {\r\n  console.log('has java')\r\n}\r\n");

        assertTrue(reported.isEmpty(), "typeof guard and bare identifiers must not be reported: " + reported);
    }

    @Test
    void importAndCatchAndGlobalsAreNotFlagged() {
        validate(file("scope"),
                "import { ItemStack, Item as It } from 'nekojs:items'\r\n"
                + "try {\r\n  ItemStack.of('x')\r\n} catch (err) {\r\n  err.getMessage()\r\n}\r\n"
                + "JSON.parse('{}')\r\nMath.max(1, 2)\r\nconsole.log('hi')\r\n");

        assertTrue(reported.isEmpty(), "imports/catch params/JS builtins must not be reported: " + reported);
    }

    /**
     * 真实事故复现（test_entity_goal.js）：const 局部 + 负数实参 + if/模板串混排，
     * 局部变量 entity 不得被报未知标识符。
     */
    @Test
    void locallyDeclaredEntityWithNegativeArgsAndTemplateIsNotFlagged() {
        validate(file("entity"),
                "console.info('loaded')\r\n"
                + "ServerEvents.started(event => {\r\n"
                + "  const server = event.getServer()\r\n"
                + "  const level = server.overworld()\r\n"
                + "  const entity = level.spawnEntity('nekojs:test_script_mob', 0, -60, 0)\r\n"
                + "  if (entity == null) {\r\n"
                + "    console.error('expected to spawn')\r\n"
                + "    return\r\n"
                + "  }\r\n"
                + "  console.info(`spawned type=${entity.getType()}`)\r\n"
                + "  entity.discard()\r\n"
                + "})\r\n");

        assertTrue(reported.stream().noneMatch(m -> m.contains("'entity'")),
                "locally declared entity must not be reported: " + reported);
    }

    /** 真实事故复现（startup_scripts）：多行链式回调参数（builder/goals）不得被报未知标识符。 */
    @Test
    void multilineChainedCallbackParamsAreNotFlagged() {
        validate(file("builder"),
                "RegistryEvents.item(event => {\r\n"
                + "    event.create('mymod:cool_gem', builder => {\r\n"
                + "        builder\r\n"
                + "            .maxStackSize(16)\r\n"
                + "            .rarity('rare')\r\n"
                + "            .fireResistant()\r\n"
                + "    })\r\n"
                + "})\r\n"
                + "RegistryEvents.entityType(event => {\r\n"
                + "    event.create('nekojs:test_mob', builder => {\r\n"
                + "        builder.attributes(attributes => {\r\n"
                + "            attributes.maxHealth(10)\r\n"
                + "        })\r\n"
                + "        .goals(goals => {\r\n"
                + "            goals.floatInWater(0)\r\n"
                + "        })\r\n"
                + "    })\r\n"
                + "})\r\n");

        assertTrue(reported.stream().noneMatch(m -> m.contains("'builder'")
                        || m.contains("'goals'") || m.contains("'attributes'")),
                "chained callback params must not be reported: " + reported);
    }

    /** 真实事故复现（client_scripts）：同文件 function 声明 + 后续调用不得被报未知标识符。 */
    @Test
    void namedFunctionDeclarationAndCallAreNotFlagged() {
        validate(file("fn"),
                "function assertEventGroup(group, keys) {\r\n"
                + "  keys.forEach(k => console.log(k))\r\n"
                + "}\r\n"
                + "assertEventGroup(ClientEvents, ['tick'])\r\n");

        assertTrue(reported.stream().noneMatch(m -> m.contains("'assertEventGroup'")
                        || m.contains("'group'") || m.contains("'keys'") || m.contains("'k'")),
                "named function decl and its params must not be reported: " + reported);
    }
}
