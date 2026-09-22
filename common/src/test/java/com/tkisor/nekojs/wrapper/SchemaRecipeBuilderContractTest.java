package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldRole;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 23 AC2 contract fixture for the common recipe schema/type/namespace seam: a
 * representative {@link RecipeTypeDefinition} (minecraft-style smelting + a namespaced custom
 * type) drives {@link SchemaRecipeBuilder} over a recording {@link RecipeBuilder} — required
 * fields must be supplied, defaults are applied, schema-field setters convert values through
 * the {@link com.tkisor.nekojs.api.recipe.RecipeSchemaHost} (scalar kinds + array wrapping),
 * and unknown members stay unknown. All assertions observe the JSON the builder produced or
 * the host received; no platform (Minecraft/loader) types are involved.
 */
class SchemaRecipeBuilderContractTest {

    private static Context context;

    @BeforeAll
    static void openContext() {
        context = Context.newBuilder().allowAllAccess(false).build();
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    // ---------------------------------------------------------- fixture schema

    private static RecipeFieldDefinition field(String name, String path, RecipeFieldKind kind,
            boolean array, boolean optional, JsonElement defaultValue, RecipeFieldRole role) {
        return new RecipeFieldDefinition(name, path, kind, array, optional, defaultValue, role);
    }

    /** Representative schema of {@code minecraft:smelting} (INPUT/OUTPUT roles included). */
    private static RecipeTypeDefinition smeltingDefinition() {
        Map<String, RecipeFieldDefinition> fields = new LinkedHashMap<>();
        fields.put("ingredient", field("ingredient", "ingredient", RecipeFieldKind.INGREDIENT,
                false, false, null, RecipeFieldRole.INPUT));
        fields.put("result", field("result", "result", RecipeFieldKind.ITEM_STACK,
                false, false, null, RecipeFieldRole.OUTPUT));
        fields.put("experience", field("experience", "experience", RecipeFieldKind.NUMBER,
                false, true, new JsonPrimitive(0.1f), RecipeFieldRole.OTHER));
        fields.put("cookingtime", field("cookingtime", "cookingtime", RecipeFieldKind.INT,
                false, true, new JsonPrimitive(200), RecipeFieldRole.OTHER));
        return new RecipeTypeDefinition("minecraft", "smelting", "minecraft:smelting",
                "smelting", List.of(), fields, List.of());
    }

    /** A namespaced custom type: array-of-input + JSON passthrough field. */
    private static RecipeTypeDefinition customDefinition() {
        Map<String, RecipeFieldDefinition> fields = new LinkedHashMap<>();
        fields.put("inputs", field("inputs", "inputs", RecipeFieldKind.INGREDIENT,
                true, false, null, RecipeFieldRole.INPUT));
        fields.put("data", field("data", "data", RecipeFieldKind.JSON,
                false, true, null, RecipeFieldRole.OTHER));
        return new RecipeTypeDefinition("nekojs", "assembly", "nekojs:assembly",
                null, List.of(), fields, List.of());
    }

    private static RecipeTypeDefinitionRegistry registry() {
        return RecipeTypeDefinitionRegistry.builder()
                .add(smeltingDefinition())
                .add(customDefinition())
                .build();
    }

    // ---------------------------------------------------------- fakes

    /** Records every setPath into a JsonObject (fixture schema paths are all top level). */
    private static final class RecordingBuilder implements RecipeBuilder {
        final JsonObject json = new JsonObject();
        final List<String> paths = new ArrayList<>();

        @Override
        public RecipeBuilder setPath(String path, RecipeJsonValue value) {
            paths.add(path);
            json.add(path, value.value() instanceof JsonElement element
                    ? element
                    : new JsonPrimitive(String.valueOf(value.value())));
            return this;
        }
    }

    /** Minimal host: scalar kinds convert natively, INGREDIENT/ITEM_STACK via a tagged JSON form. */
    private static final class FakeHost implements com.tkisor.nekojs.api.recipe.RecipeSchemaHost {
        @Override
        public RecipeBuilder builder(String type, String prefix) {
            return new RecordingBuilder();
        }

        @Override
        public Object custom(JsonObject json) {
            return json;
        }

        @Override
        public JsonElement encodeField(RecipeFieldKind kind, Value value) {
            return switch (kind) {
                case STRING -> new JsonPrimitive(value.asString());
                case INT -> new JsonPrimitive(value.asInt());
                case NUMBER -> new JsonPrimitive(value.asDouble());
                case BOOLEAN -> new JsonPrimitive(value.asBoolean());
                case INGREDIENT -> ingredientJson(value.asString());
                case ITEM_STACK -> stackJson(value.asString());
                default -> toJson(value);
            };
        }

        private static JsonElement ingredientJson(String id) {
            JsonObject json = new JsonObject();
            json.addProperty("item", id);
            return json;
        }

        private static JsonElement stackJson(String id) {
            JsonObject json = new JsonObject();
            json.addProperty("item", id);
            json.addProperty("count", 1);
            return json;
        }

        @Override
        public JsonElement toJson(Value value) {
            if (value.isString()) return new JsonPrimitive(value.asString());
            if (value.isBoolean()) return new JsonPrimitive(value.asBoolean());
            if (value.isNumber()) return new JsonPrimitive(value.asDouble());
            throw new IllegalArgumentException("unsupported test value");
        }
    }

    private static Value value(Object javaValue) {
        return context.asValue(javaValue);
    }

    // ---------------------------------------------------------- namespace/type contract

    @Test
    void namespaceAndTypeLookupFollowsTheRegistryContract() {
        RecipeTypeDefinitionRegistry registry = registry();
        assertTrue(registry.hasNamespace("minecraft"));
        assertTrue(registry.hasNamespace("nekojs"));
        assertFalse(registry.hasNamespace("unknown"));
        assertEquals(java.util.Set.of("smelting"), registry.types("minecraft"));
        assertEquals(java.util.Set.of("assembly"), registry.types("nekojs"));
        assertEquals("minecraft:smelting", registry.get("minecraft", "smelting").key());
        assertEquals("nekojs:assembly", registry.get("nekojs", "assembly").key());
        // idPrefix fallback: absent prefix derives from namespace + name (generated id namespace)
        assertEquals("smelting", registry.get("minecraft", "smelting").prefix());
        assertEquals("nekojs_assembly", registry.get("nekojs", "assembly").prefix());
        assertNull(registry.get("minecraft", "assembly"));
    }

    // ---------------------------------------------------------- builder construction contract

    @Test
    void requiredFieldsMustBeSuppliedAndDefaultsAreApplied() {
        RecordingBuilder delegate = new RecordingBuilder();
        Map<String, Value> initial = new LinkedHashMap<>();
        initial.put("ingredient", value("minecraft:iron_ore"));
        initial.put("result", value("minecraft:iron_ingot"));

        new SchemaRecipeBuilder(delegate, smeltingDefinition(), new FakeHost(), initial);

        assertEquals("{\"ingredient\":{\"item\":\"minecraft:iron_ore\"},"
                        + "\"result\":{\"item\":\"minecraft:iron_ingot\",\"count\":1},"
                        + "\"experience\":0.1,\"cookingtime\":200}",
                delegate.json.toString(),
                "initial values applied, defaults filled, order = schema field order");
    }

    @Test
    void missingRequiredFieldFailsTheWholeConstruction() {
        Map<String, Value> initial = new LinkedHashMap<>();
        initial.put("ingredient", value("minecraft:iron_ore"));
        // result missing

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SchemaRecipeBuilder(new RecordingBuilder(), smeltingDefinition(),
                        new FakeHost(), initial));
        assertTrue(error.getMessage().contains("result"), error.getMessage());
        assertTrue(error.getMessage().contains("minecraft:smelting"), error.getMessage());
    }

    @Test
    void schemaFieldSettersConvertValuesAndKeepTheChainOnTheWrapper() {
        RecordingBuilder delegate = new RecordingBuilder();
        Map<String, Value> initial = new LinkedHashMap<>();
        initial.put("ingredient", value("minecraft:iron_ore"));
        initial.put("result", value("minecraft:iron_ingot"));
        SchemaRecipeBuilder builder = new SchemaRecipeBuilder(delegate, smeltingDefinition(),
                new FakeHost(), initial);

        assertTrue(builder.hasMember("experience"));
        assertFalse(builder.hasMember("no_such_field"));

        Object setter = builder.getMember("experience");
        assertNotNull(setter);
        Object chained = ((ProxyExecutable) setter).execute(value(0.35));
        assertSame(builder, chained, "field setters must stay chainable on the schema builder");

        assertEquals("{\"item\":\"minecraft:iron_ore\"}", delegate.json.get("ingredient").toString());
        assertEquals(0.35, delegate.json.get("experience").getAsDouble(), 1e-9);
        assertNull(builder.getMember("no_such_field"), "unknown members resolve to null");
    }

    @Test
    void arrayFieldsWrapScalarsAndExpandArrayValues() {
        RecordingBuilder delegate = new RecordingBuilder();
        Map<String, Value> initial = new LinkedHashMap<>();
        initial.put("inputs", value(graal.graalvm.polyglot.proxy.ProxyArray
                .fromArray("minecraft:redstone", "minecraft:glowstone")));
        FakeHost host = new FakeHost();

        SchemaRecipeBuilder builder = new SchemaRecipeBuilder(delegate, customDefinition(), host, initial);
        assertEquals("{\"inputs\":[{\"item\":\"minecraft:redstone\"},{\"item\":\"minecraft:glowstone\"}]}",
                delegate.json.toString());

        // A single non-array value written through the setter is wrapped into the array shape.
        RecordingBuilder scalarDelegate = new RecordingBuilder();
        Map<String, Value> scalarInitial = new LinkedHashMap<>();
        scalarInitial.put("inputs", value("minecraft:redstone"));
        new SchemaRecipeBuilder(scalarDelegate, customDefinition(), host, scalarInitial);
        assertEquals("{\"inputs\":[{\"item\":\"minecraft:redstone\"}]}",
                scalarDelegate.json.toString());
    }

    @Test
    void builderIsReadOnlyThroughTheProxySurface() {
        SchemaRecipeBuilder builder = new SchemaRecipeBuilder(new RecordingBuilder(),
                smeltingDefinition(), new FakeHost(),
                Map.of("ingredient", value("minecraft:iron_ore"), "result", value("minecraft:iron_ingot")));
        assertThrows(UnsupportedOperationException.class,
                () -> builder.putMember("experience", value(1)));
    }
}
