package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldRole;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldValueCodec;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeConstructorUndefinedTest {
    private static final List<String> SHORT = List.of("count");
    private static final List<String> MEDIUM = List.of("count", "time");
    private static final List<String> LONG = List.of("count", "time", "energy");

    @Test
    void trailingUndefinedUsesTheSameDefaultsAndWritePoliciesAsOmission() {
        try (Context context = Context.newBuilder("js").build()) {
            var definition = definition(List.of(SHORT, MEDIUM, LONG), Map.of());
            JsonObject omitted = call(context, definition, "2");
            assertEquals("{\"count\":2,\"time\":100}", omitted.toString());
            assertEquals(omitted, call(context, definition, "2, undefined"));
            assertEquals(omitted, call(context, definition, "2, undefined, undefined"));
            assertEquals(call(context, definition, "2, 200"), call(context, definition, "2, 200, undefined"));
            assertEquals(5, call(context, definition, "2, 200, 5").get("energy").getAsInt());
        }
    }

    @Test
    void explicitUndefinedSelectsTheOmittedArityOverridesRatherThanTheLongerConstructor() {
        try (Context context = Context.newBuilder("js").build()) {
            var definition = definition(List.of(SHORT, MEDIUM, LONG),
                    Map.of(MEDIUM, Map.of("energy", new JsonPrimitive(99))));
            assertFalse(call(context, definition, "2, undefined").has("energy"));
            assertEquals(call(context, definition, "2"), call(context, definition, "2, undefined"));
            JsonObject two = call(context, definition, "2, 200");
            assertEquals(99, two.get("energy").getAsInt());
            assertEquals(two, call(context, definition, "2, 200, undefined"));
        }
    }

    @Test
    void explicitNullInteriorUndefinedAndMissingIntermediateAritiesRemainRejected() {
        try (Context context = Context.newBuilder("js").build()) {
            var definition = definition(List.of(SHORT, MEDIUM, LONG), Map.of());
            for (String arguments : List.of("2, null", "2, undefined, 5", "undefined", "null")) {
                Host host = new Host();
                assertThrows(IllegalArgumentException.class, () -> invoke(context, host, definition, arguments));
                assertEquals(0, host.publications, arguments);
            }
            Host host = new Host();
            var gap = definition(List.of(SHORT, LONG), Map.of());
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> invoke(context, host, gap, "2, 200, undefined"));
            assertTrue(failure.getMessage().contains("NEKO-5214"));
            assertEquals(0, host.publications);
        }
    }

    @Test
    void catalogFlagsOverridesAndExistingConstructorsKeepConservativeDefaults() {
        var plain = definition(List.of(SHORT, MEDIUM), Map.of());
        assertFalse(RecipeSchemaTypeEntry.from(plain).hasConstructorOverrides());
        assertTrue(RecipeSchemaTypeEntry.from(definition(List.of(SHORT, MEDIUM),
                Map.of(MEDIUM, Map.of("energy", new JsonPrimitive(9))))).hasConstructorOverrides());
        assertFalse(new RecipeSchemaTypeEntry("test", "recipe", null, List.of(), List.of()).hasConstructorOverrides());
        assertFalse(new RecipeSchemaTypeEntry("test", "recipe", null, List.of(), List.of(), Map.of(), List.of(), null)
                .hasConstructorOverrides());
        assertFalse(new RecipeSchemaTypeEntry("test", "recipe", null, List.of(), List.of(), Map.of(), List.of(), null,
                Map.of(), Object.class).hasConstructorOverrides());
    }

    private static JsonObject call(Context context, RecipeTypeDefinition definition, String arguments) {
        Host host = new Host();
        invoke(context, host, definition, arguments);
        assertEquals(1, host.publications);
        return host.json.deepCopy();
    }

    private static void invoke(Context context, Host host, RecipeTypeDefinition definition, String arguments) {
        var registry = RecipeTypeDefinitionRegistry.builder().add(definition).build();
        var create = (ProxyExecutable) new RecipeNamespaceProxy(host, "test", null, registry).getMember("recipe");
        Value array = context.eval("js", "[" + arguments + "]");
        Value[] values = new Value[(int) array.getArraySize()];
        for (int i = 0; i < values.length; i++) values[i] = array.getArrayElement(i);
        create.execute(values);
    }

    private static RecipeTypeDefinition definition(List<List<String>> constructors,
                                                    Map<List<String>, Map<String, JsonElement>> overrides) {
        var codec = new RecipeFieldValueCodec(AdapterInputShape.number(), (value, host) -> {
            if (!value.fitsInInt()) throw new IllegalArgumentException("Expected an integer field value");
            return new JsonPrimitive(value.asInt());
        });
        Map<String, RecipeFieldDefinition> fields = new LinkedHashMap<>();
        fields.put("count", new RecipeFieldDefinition("count", "count", RecipeFieldKind.INT, false, false, null,
                RecipeFieldRole.INPUT, codec, true));
        fields.put("time", new RecipeFieldDefinition("time", "time", RecipeFieldKind.INT, false, true, new JsonPrimitive(100),
                RecipeFieldRole.OTHER, codec, true));
        fields.put("energy", new RecipeFieldDefinition("energy", "energy", RecipeFieldKind.INT, false, true, new JsonPrimitive(20),
                RecipeFieldRole.OTHER, codec, false));
        return new RecipeTypeDefinition("test", "recipe", "test:recipe", null, constructors, fields, List.of(),
                Map.of(), Map.of(), overrides, null);
    }

    private static final class Host implements RecipeSchemaHost {
        int publications;
        JsonObject json;
        @Override public RecipeBuilder builder(String type, String prefix) {
            publications++;
            json = new JsonObject();
            return (path, value) -> { json.add(path, (JsonElement) value.value()); return null; };
        }
        @Override public Object custom(JsonObject json) { throw new AssertionError("No raw fallback"); }
        @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) { throw new AssertionError("Explicit codec required"); }
        @Override public JsonElement toJson(Value value) { throw new AssertionError("No raw fallback"); }
    }
}
