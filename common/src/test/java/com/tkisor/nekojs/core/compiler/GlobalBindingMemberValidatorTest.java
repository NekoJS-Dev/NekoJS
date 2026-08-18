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

    private final List<String> reported = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ScriptBindingSchema.clearAll();
        TestPlatformInit.ensureInitialized();
        ScriptErrorReporter.set((type, kind, t) -> reported.add(String.valueOf(t.getMessage())));
        ScriptBindingSchema.register(ScriptType.SERVER, Map.of(
                "Utils", new ScriptBindingSchema.BindingMembers(JavaMemberIndex.allMembersOf(UtilsJS.class)),
                "ServerEvents", new ScriptBindingSchema.BindingMembers(Set.of("recipes"))));
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
}
