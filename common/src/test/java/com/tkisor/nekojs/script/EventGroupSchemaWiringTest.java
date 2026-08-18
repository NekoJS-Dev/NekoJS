package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventSchemaRegistry;
import com.tkisor.nekojs.api.event.ManagedCallbackSchemaRegistry;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.compiler.EventCallbackSourceValidator;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件组 → {@link ScriptBindingSchema} 接线回归：生产环境的事件组
 * （ServerEvents/BlockEvents…）由 eventBridge.bindEvents 绑定，但 schema 只收环境绑定与
 * managed 全局，{@code EventCallbackSourceValidator} 的 {@code schema.containsKey(groupName)}
 * 恒 false——事件回调 preflight 整体静默失效（真实事故：{@code event.rec} 拼错 reload 无报错）。
 */
class EventGroupSchemaWiringTest {

    public static class TestRecipeEvent {
        public Object getLevel() {
            return new Object();
        }
    }

    private EventGroup group;

    private final List<String> reported = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ScriptBindingSchema.clearAll();
        ManagedCallbackSchemaRegistry.clear();
        TestPlatformInit.ensureInitialized();
        ScriptErrorReporter.set((type, kind, t) -> reported.add(String.valueOf(t.getMessage())));

        group = EventGroup.of("ServerEvents");
        group.server("recipes", TestRecipeEvent.class);
        EventSchemaRegistry.registerGroup(group);
    }

    @AfterEach
    void tearDown() {
        ScriptErrorReporter.set(ScriptErrorReporter.Reporter.NOOP);
    }

    @Test
    void eventGroupEntersSchemaWithBusNamesAsMembers() {
        Map<String, ScriptBindingSchema.BindingMembers> schema = new HashMap<>();
        schema.put("Utils", new ScriptBindingSchema.BindingMembers(Set.of("serverTell")));
        ScriptEnvironmentFactory.addEventGroupSchema(schema, List.of(group), Map.of("ScriptEvents", Map.of()));

        assertTrue(schema.containsKey("ServerEvents"), "event group must enter the binding schema");
        assertTrue(schema.get("ServerEvents").contains("recipes"), "bus names are the group's members");
        assertTrue(schema.containsKey("ScriptEvents"), "script-event group must enter the schema too");
        assertTrue(schema.containsKey("Utils"), "pre-existing environment binding entries untouched");
    }

    @Test
    void wiredSchemaMakesCallbackPreflightFlagUnknownMember() {
        // 按 ScriptEnvironmentFactory.create 的真实顺序构建 schema：环境绑定 + 事件组
        Map<String, ScriptBindingSchema.BindingMembers> schema = new HashMap<>();
        ScriptEnvironmentFactory.addEventGroupSchema(schema, List.of(group), Map.of());
        ScriptBindingSchema.register(ScriptType.SERVER, schema);

        Path file = ScriptType.SERVER.path.resolve("main.js");
        // 与真实事故文件一致：CRLF 行尾 + 表达式语句形式的悬空成员访问
        String source = "ServerEvents.recipes(event => {\r\n    event.rec\r\n})\r\n";

        EventCallbackSourceValidator.validate(file, source);

        assertFalse(reported.isEmpty(), "'rec' must be reported once the group is in the schema");
        assertTrue(reported.get(0).contains("'rec'"), reported.toString());
    }

    @Test
    void wiredSchemaDoesNotFlagLegitimateMemberAccess() {
        Map<String, ScriptBindingSchema.BindingMembers> schema = new HashMap<>();
        ScriptEnvironmentFactory.addEventGroupSchema(schema, List.of(group), Map.of());
        ScriptBindingSchema.register(ScriptType.SERVER, schema);

        Path file = ScriptType.SERVER.path.resolve("ok.js");
        EventCallbackSourceValidator.validate(file,
                "ServerEvents.recipes(event => {\r\n    event.getLevel()\r\n})\r\n");

        assertTrue(reported.isEmpty(), "known members must not be reported: " + reported);
    }
}
