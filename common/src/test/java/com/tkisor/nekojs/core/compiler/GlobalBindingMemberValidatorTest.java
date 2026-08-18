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

    @BeforeEach
    void setUp() {
        ScriptBindingSchema.clearAll();
        TestPlatformInit.ensureInitialized();
        ScriptErrorReporter.set((type, kind, t) -> reported.add(String.valueOf(t.getMessage())));
        ScriptBindingSchema.register(ScriptType.SERVER, Map.of(
                "Utils", new ScriptBindingSchema.BindingMembers(JavaMemberIndex.allMembersOf(UtilsJS.class)),
                "Item", new ScriptBindingSchema.BindingMembers(
                        JavaMemberIndex.allMembersOf(TestItemJS.class), Set.of(TestItemJS.class)),
                "ServerEvents", new ScriptBindingSchema.BindingMembers(Set.of("recipes", "started"))));
        // 生产环境由 ScriptEnvironmentFactory 从运行中 Context 收割；测试给出最小内置集
        ScriptBindingSchema.registerGlobals(ScriptType.SERVER,
                Set.of("console", "Math", "JSON", "globalThis", "this", "arguments", "super"));
    }

    @AfterEach
    void tearDown() {
        ScriptErrorReporter.set(ScriptErrorReporter.Reporter.NOOP);
    }

    private static Path file(String name) {
        return ScriptType.SERVER.path.resolve(name + ".js");
    }

    @Test
    void flagsTypoedBindingMemberAnywhereInTheFile() {
        GlobalBindingMemberValidator.validate(file("typo"),
                "Utils.serverTel('hi')\r\nServerEvents.recipes(event => {\r\n  Utils.serverTel2('x')\r\n})\r\n");

        assertTrue(reported.stream().anyMatch(m -> m.contains("'Utils' has no member 'serverTel'")),
                "top-level typo must be reported: " + reported);
        assertTrue(reported.stream().anyMatch(m -> m.contains("'serverTel2'")),
                "typos inside callbacks must be reported too: " + reported);
    }

    @Test
    void flagsTypoedEventNameOnGroupBinding() {
        GlobalBindingMemberValidator.validate(file("evt"), "ServerEvents.recipez(event => {})\r\n");

        assertTrue(reported.stream().anyMatch(m -> m.contains("'ServerEvents' has no member 'recipez'")),
                "event-name typos on group bindings must be reported: " + reported);
    }

    @Test
    void knownMembersAndConstAliasAreNotFlagged() {
        GlobalBindingMemberValidator.validate(file("ok"),
                "Utils.serverTell('hi')\r\nconst u = Utils\r\nu.getServer()\r\nServerEvents.recipes(event => {})\r\n");

        assertTrue(reported.isEmpty(), "known members (direct + const alias) must not be reported: " + reported);
    }

    @Test
    void chainedMemberTypoIsFlaggedViaTypeFlow() {
        GlobalBindingMemberValidator.validate(file("chain"),
                "const s = Item.of('minecraft:stone')\r\ns.withCont(3)\r\nItem.empty().getIdd()\r\n");

        assertTrue(reported.stream().anyMatch(m -> m.contains("no member 'withCont'")),
                "second-level typo via local must be reported: " + reported);
        assertTrue(reported.stream().anyMatch(m -> m.contains("no member 'getIdd'")),
                "chained typo on call result must be reported: " + reported);
    }

    @Test
    void chainedLegitimateAccessIsNotFlagged() {
        GlobalBindingMemberValidator.validate(file("chainok"),
                "Item.of('minecraft:stone').withCount(3).getId()\r\n");

        assertTrue(reported.isEmpty(), "legit chains must not be reported: " + reported);
    }

    @Test
    void unknownIdentifierAsObjectOrCalleeIsFlagged() {
        GlobalBindingMemberValidator.validate(file("unknown"),
                "Util.serverTell('hi')\r\nqwq()\r\n");

        assertTrue(reported.stream().anyMatch(m -> m.contains("Unknown identifier 'Util'")),
                "typoed binding name must be reported: " + reported);
        assertTrue(reported.stream().anyMatch(m -> m.contains("Unknown identifier 'qwq'")),
                "unknown call target must be reported: " + reported);
    }

    @Test
    void typeofAndBareIdentifiersAreNotFlagged() {
        // typeof 的操作数会被 ValParser 泄漏为独立语句（裸标识符），报了必误报
        GlobalBindingMemberValidator.validate(file("typeof"),
                "if (typeof Java !== 'undefined') {\r\n  console.log('has java')\r\n}\r\n");

        assertTrue(reported.isEmpty(), "typeof guard and bare identifiers must not be reported: " + reported);
    }

    @Test
    void importAndCatchAndGlobalsAreNotFlagged() {
        GlobalBindingMemberValidator.validate(file("scope"),
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
        GlobalBindingMemberValidator.validate(file("entity"),
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
        GlobalBindingMemberValidator.validate(file("builder"),
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
        GlobalBindingMemberValidator.validate(file("fn"),
                "function assertEventGroup(group, keys) {\r\n"
                + "  keys.forEach(k => console.log(k))\r\n"
                + "}\r\n"
                + "assertEventGroup(ClientEvents, ['tick'])\r\n");

        assertTrue(reported.stream().noneMatch(m -> m.contains("'assertEventGroup'")
                        || m.contains("'group'") || m.contains("'keys'") || m.contains("'k'")),
                "named function decl and its params must not be reported: " + reported);
    }
}
