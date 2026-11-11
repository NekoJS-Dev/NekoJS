package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
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
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeCompatibilitySchemaTest {
    private Context context;

    @BeforeEach
    void openContext() {
        context = Context.newBuilder().allowAllAccess(false).build();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    private static class Host implements RecipeSchemaHost {
        int publications;
        int conversions;
        int customCalls;
        int encodedApplications;
        JsonObject json;

        @Override
        public RecipeBuilder builder(String type, String prefix) {
            publications++;
            json = new JsonObject();
            return (path, value) -> {
                json.add(path, (JsonElement) value.value());
                return null;
            };
        }

        @Override
        public Object custom(JsonObject value) {
            customCalls++;
            return value;
        }

        @Override
        public JsonElement encodeField(RecipeFieldKind kind, Value value) {
            conversions++;
            return new JsonPrimitive(value.asInt());
        }

        @Override
        public JsonElement toJson(Value value) {
            throw new AssertionError("Schema must not use raw JSON fallback");
        }

        @Override
        public void applyEncodedField(RecipeBuilder builder, RecipeFieldDefinition field, JsonElement value) {
            encodedApplications++;
            RecipeSchemaHost.super.applyEncodedField(builder, field, value);
        }
    }

    private Value value(Object value) {
        return context.asValue(value);
    }

    private Value named(Map<String, Object> values) {
        return value(ProxyObject.fromMap(values));
    }

    private static RecipeFieldValueCodec positiveCodec() {
        return new RecipeFieldValueCodec(AdapterInputShape.number(), (value, host) -> {
            if (!value.fitsInInt() || value.asInt() <= 0) throw new IllegalArgumentException("positive value required");
            return host.encodeField(RecipeFieldKind.INT, value);
        });
    }

    private static RecipeTypeDefinition definition() {
        var fields = new LinkedHashMap<String, RecipeFieldDefinition>();
        fields.put("count", new RecipeFieldDefinition("count", "count", RecipeFieldKind.INT, false,
                false, null, RecipeFieldRole.INPUT, positiveCodec(), true));
        fields.put("time", new RecipeFieldDefinition("time", "time", RecipeFieldKind.INT, false,
                true, new JsonPrimitive(100), RecipeFieldRole.OTHER, positiveCodec(), false));
        fields.put("heated", new RecipeFieldDefinition("heated", "heated", RecipeFieldKind.BOOLEAN, false,
                true, new JsonPrimitive(false), RecipeFieldRole.OTHER, null, true));
        return new RecipeTypeDefinition("compat", "process", "target:process", null,
                List.of(List.of("count"), List.of("count", "time")), fields, List.of("count"),
                Map.of("amount", "count"), Map.of("heat", Map.of("heated", new JsonPrimitive(true))),
                Map.of(List.of("count", "time"), Map.of("heated", new JsonPrimitive(true))), null);
    }

    private static ProxyExecutable executable(Host host, RecipeTypeDefinition definition) {
        var registry = RecipeTypeDefinitionRegistry.builder().add(definition).build();
        return (ProxyExecutable) new RecipeNamespaceProxy(host, definition.namespace(), null, registry)
                .getMember(definition.name());
    }

    @Test
    void advancedConstructorEncodesOnceBeforePublicationAndWritesOnlyRequestedDefaults() {
        Host host = new Host();
        ProxyObject builder = (ProxyObject) executable(host, definition()).execute(value(2));
        assertEquals(1, host.publications);
        assertEquals(1, host.conversions);
        assertEquals(2, host.encodedApplications);
        assertEquals("{\"count\":2,\"heated\":false}", host.json.toString());
        assertTrue(builder.hasMember("amount"));
        assertTrue(builder.hasMember("heat"));
        assertTrue(builder.hasMember("count"));
    }

    @Test
    void conversionAndRequiredFieldFailuresNeverPublishAnAdvancedBuilder() {
        Host host = new Host();
        assertThrows(IllegalArgumentException.class, () -> executable(host, definition()).execute(value(2), value(-1)));
        assertEquals(0, host.publications);
        assertEquals(1, host.conversions);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> executable(host, definition()).execute(named(Map.of("time", 20))));
        assertTrue(missing.getMessage().contains("NEKO-5213"));
        assertEquals(0, host.publications);
    }

    @Test
    void namedAliasesAndFluentSettersShareTheCanonicalFieldAndInvalidSetterKeepsJson() {
        Host host = new Host();
        ProxyObject builder = (ProxyObject) executable(host, definition()).execute(named(Map.of("amount", 3)));
        assertEquals(3, host.json.get("count").getAsInt());
        ProxyExecutable alias = (ProxyExecutable) builder.getMember("amount");
        assertSame(builder, alias.execute(value(4)));
        assertEquals(4, host.json.get("count").getAsInt());
        String before = host.json.toString();
        assertThrows(IllegalArgumentException.class, () -> alias.execute(value(0)));
        assertEquals(before, host.json.toString());
        assertSame(builder, ((ProxyExecutable) builder.getMember("time")).execute(value(100)));
        assertEquals(100, host.json.get("time").getAsInt());
    }

    @Test
    void duplicateCanonicalAndAliasArgumentsRejectBeforePublication() {
        Host host = new Host();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> executable(host, definition()).execute(named(Map.of("count", 2, "amount", 2))));
        assertTrue(failure.getMessage().contains("NEKO-5212"));
        assertEquals(0, host.publications);
        assertEquals(0, host.conversions);
    }

    @Test
    void noArgumentFunctionsAndConstructorConstantsUseAlreadyValidatedJson() {
        Host host = new Host();
        ProxyObject builder = (ProxyObject) executable(host, definition()).execute(value(2), value(20));
        assertTrue(host.json.get("heated").getAsBoolean());
        ProxyExecutable heat = (ProxyExecutable) builder.getMember("heat");
        int conversions = host.conversions;
        assertSame(builder, heat.execute());
        assertEquals(conversions, host.conversions);
        assertThrows(IllegalArgumentException.class, () -> heat.execute(value(1)));
    }

    @Test
    void constructorConstantsOverrideOnlyAfterSuppliedValuesPassValidation() {
        var field = new RecipeFieldDefinition("count", "count", RecipeFieldKind.INT, false,
                false, null, RecipeFieldRole.INPUT, positiveCodec(), true);
        var definition = new RecipeTypeDefinition("compat", "override", "target:override", null,
                List.of(List.of("count")), Map.of("count", field), List.of(), Map.of(), Map.of(),
                Map.of(List.of("count"), Map.of("count", new JsonPrimitive(9))), null);
        Host host = new Host();
        executable(host, definition).execute(value(2));
        assertEquals(1, host.conversions);
        assertEquals(9, host.json.get("count").getAsInt());
        Host invalid = new Host();
        assertThrows(IllegalArgumentException.class, () -> executable(invalid, definition).execute(value(-1)));
        assertEquals(0, invalid.publications);
    }

    @Test
    void constructorConstantsMayProvideAnOtherwiseMissingRequiredField() {
        var field = new RecipeFieldDefinition("count", "count", RecipeFieldKind.INT, false,
                false, null, RecipeFieldRole.INPUT, positiveCodec(), true);
        var definition = new RecipeTypeDefinition("compat", "constant", "target:constant", null,
                List.of(List.of()), Map.of("count", field), List.of(), Map.of(), Map.of(),
                Map.of(List.of(), Map.of("count", new JsonPrimitive(9))), null);
        Host host = new Host();
        executable(host, definition).execute();
        assertEquals(0, host.conversions);
        assertEquals(9, host.json.get("count").getAsInt());
    }

    @Test
    void wholeFieldCodecOwnsArrayShapeWithoutNativeScalarWrapping() {
        Host host = new Host();
        RecipeFieldValueCodec codec = new RecipeFieldValueCodec(AdapterInputShape.number(),
                (value, schemaHost) -> new JsonPrimitive(value.asInt() * 2));
        var field = new RecipeFieldDefinition("count", "count", RecipeFieldKind.INT, true, false, null,
                RecipeFieldRole.INPUT, codec, true);
        var definition = new RecipeTypeDefinition("compat", "whole", "target:whole", null,
                List.of(List.of("count")), Map.of("count", field), List.of());
        executable(host, definition).execute(value(3));
        assertEquals("{\"count\":6}", host.json.toString());
        assertSame(codec.inputShape(), field.valueCodec().inputShape());
    }

    public static final class CollidingHandler {
        public Object process(Value ignored) {
            throw new AssertionError("Rejected profile must precede handlers");
        }
    }

    @Test
    void unsupportedKnownProfileIsEnumerableAndTerminalBeforeHandlersOrRawFallback() {
        Host host = new Host();
        var rejected = RecipeTypeDefinition.unsupported("compat", "process", "target:process", "Missing shaped cleanup");
        var registry = RecipeTypeDefinitionRegistry.builder().add(rejected).build();
        var namespace = new RecipeNamespaceProxy(host, "compat", new CollidingHandler(), registry);
        assertTrue(List.of((String[]) namespace.getMemberKeys()).contains("process"));
        var callable = (ProxyExecutable) namespace.getMember("process");
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
                () -> callable.execute(value(1)));
        assertTrue(failure.getMessage().contains("NEKO-5211"));
        assertTrue(failure.getMessage().contains("Missing shaped cleanup"));
        assertEquals(0, host.publications);
        assertEquals(0, host.customCalls);
    }

    @Test
    void legacyConstructorsAndCustomApplyFieldKeepTheirOriginalPath() {
        var field = new RecipeFieldDefinition("count", "count", RecipeFieldKind.INT, false, false, null, RecipeFieldRole.INPUT);
        var definition = new RecipeTypeDefinition("compat", "legacy", "target:legacy", null,
                List.of(List.of("count")), Map.of("count", field), List.of());
        class LegacyHost extends Host {
            int oldHookCalls;
            @Override
            public void applyField(RecipeBuilder builder, RecipeFieldDefinition target, Value value) {
                oldHookCalls++;
                builder.setPath(target.path(), new RecipeJsonValue(new JsonPrimitive(value.asInt() + 10)));
            }
        }
        LegacyHost host = new LegacyHost();
        ProxyObject builder = (ProxyObject) executable(host, definition).execute(value(2));
        assertEquals(1, host.oldHookCalls);
        assertEquals(12, host.json.get("count").getAsInt());
        ((ProxyExecutable) builder.getMember("count")).execute(value(3));
        assertEquals(2, host.oldHookCalls);
        assertEquals(13, host.json.get("count").getAsInt());
        assertEquals(0, host.encodedApplications);
        assertFalse(definition.requiresPreparedFields());
        assertTrue(field.writeDefault());
    }

    @Test
    void invalidMetadataCannotHideFieldsOrPointConstantsAtUnknownFields() {
        var original = definition();
        assertThrows(IllegalArgumentException.class, () -> new RecipeTypeDefinition("compat", "bad", "target:bad", null,
                original.constructors(), original.fields(), List.of(), Map.of("count", "time"), Map.of(), Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new RecipeTypeDefinition("compat", "bad", "target:bad", null,
                original.constructors(), original.fields(), List.of(), Map.of(),
                Map.of("heat", Map.of("unknown", new JsonPrimitive(1))), Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new RecipeTypeDefinition("compat", "bad", "target:bad", null,
                List.of(List.of("count"), List.of("time")), original.fields(), List.of(),
                Map.of("amount", "count"), Map.of(), Map.of(), null));
    }
}
