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
 * Wiring of event groups into {@link ScriptBindingSchema}: in production, event groups
 * (ServerEvents/BlockEvents...) are bound by eventBridge.bindEvents, but the schema only collected
 * environment bindings and managed globals, so {@code schema.containsKey(groupName)} in
 * {@code EventCallbackSourceValidator} was always false and event callback preflight silently did
 * nothing. This is a real incident: a typo in {@code event.rec} produced no error on reload.
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
        // Builds the schema in the same order as ScriptEnvironmentFactory.create: environment bindings, then event groups
        Map<String, ScriptBindingSchema.BindingMembers> schema = new HashMap<>();
        ScriptEnvironmentFactory.addEventGroupSchema(schema, List.of(group), Map.of());
        ScriptBindingSchema.View view = new ScriptBindingSchema.View(schema, Set.of());

        Path file = com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("main.js");
        // Matches the file from the real incident: CRLF line endings and a dangling member access written as
        // an expression statement
        String source = "ServerEvents.recipes(event => {\r\n    event.rec\r\n})\r\n";

        EventCallbackSourceValidator.validate(file, source, view);

        assertFalse(reported.isEmpty(), "'rec' must be reported once the group is in the schema");
        assertTrue(reported.get(0).contains("'rec'"), reported.toString());
    }

    @Test
    void wiredSchemaDoesNotFlagLegitimateMemberAccess() {
        Map<String, ScriptBindingSchema.BindingMembers> schema = new HashMap<>();
        ScriptEnvironmentFactory.addEventGroupSchema(schema, List.of(group), Map.of());
        ScriptBindingSchema.View view = new ScriptBindingSchema.View(schema, Set.of());

        Path file = com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("ok.js");
        EventCallbackSourceValidator.validate(file,
                "ServerEvents.recipes(event => {\r\n    event.getLevel()\r\n})\r\n",
                view);

        assertTrue(reported.isEmpty(), "known members must not be reported: " + reported);
    }
}
