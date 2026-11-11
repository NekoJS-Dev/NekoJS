package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.recipe.RecipeBuilderSession;
import com.tkisor.nekojs.api.recipe.RecipeExecutionFactory;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldRole;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeExecutionFactoryTest {
    @Test
    void factorySeesPreparedFieldsAndNativeAndCustomSelfReturnsKeepTheSchemaChain() {
        try (Context context = Context.newBuilder().allowAllAccess(false).build()) {
            Host host = new Host();
            RecipeExecutionFactory factory = (builder, owner, session) -> {
                assertSame(host, owner);
                assertSame(host.session, session);
                assertEquals(3, session.snapshot().get("count").getAsInt());
                return new Target(builder);
            };
            var definition = definition(Map.of("amount", "count"), factory);
            var registry = RecipeTypeDefinitionRegistry.builder().add(definition).build();
            var create = (ProxyExecutable) new RecipeNamespaceProxy(host, "test", null, registry).getMember("process");
            var proxy = (ProxyObject) create.execute();
            assertFalse(proxy.hasMember("count"));
            assertFalse(proxy.hasMember("alias"));
            assertFalse(proxy.hasMember("hidden"));
            assertFalse(proxy.hasMember("serialize"));
            assertFalse(proxy.hasMember("finish"));
            assertNull(proxy.getMember("serialize"));
            assertNull(proxy.getMember("count"));
            assertSame(proxy, ((ProxyExecutable) proxy.getMember("boost")).execute(context.asValue(8)));
            assertEquals(8, host.builder.json.get("count").getAsInt());
            assertSame(proxy, ((ProxyExecutable) proxy.getMember("setPath")).execute(
                    context.asValue("count"), context.asValue(new RecipeJsonValue(new JsonPrimitive(9)))));
            assertSame(proxy, ((ProxyExecutable) proxy.getMember("amount")).execute(context.asValue(10)));
            assertEquals(10, host.builder.json.get("count").getAsInt());
            assertThrows(IllegalStateException.class, () -> ((ProxyExecutable) proxy.getMember("fail")).execute());
        }
    }

    @Test
    void explicitEmptyFieldFunctionsRemoveAutomaticSettersAndProbeRetainsOnlyDeclaredTargets() {
        RecipeExecutionFactory factory = new RecipeExecutionFactory() {
            @Override public Object create(RecipeBuilder builder, RecipeSchemaHost host, RecipeBuilderSession session) {
                return new Target(builder);
            }
            @Override public Class<?> exposedType() { return Target.class; }
        };
        var definition = definition(Map.of(), factory);
        var schema = RecipeSchemaTypeEntry.from(definition);
        assertEquals(Map.of(), schema.fieldFunctions());
        assertEquals(Target.class, schema.executionType());
        var namespace = new RecipeNamespaceCatalogEntry("test", null, List.of("process"), true, List.of(), List.of(), List.of(schema));
        String declaration = new RecipeEventDeclarationGenerator(new TypeAliasRegistry()).generate(List.of(namespace), com.tkisor.nekojs.api.ScriptType.SERVER);
        assertTrue(declaration.contains("boost("));
        assertTrue(declaration.contains(": number): this;"));
        assertFalse(declaration.contains("count(count:"));
        assertFalse(declaration.contains("alias(count:"));
        assertFalse(declaration.contains("hidden("));
        assertFalse(declaration.contains("serialize("));
        assertFalse(declaration.contains("finish("));
        try (Context context = Context.newBuilder().allowAllAccess(false).build()) {
            var proxy = SchemaRecipeBuilder.createPrepared(definition, new Host(), Map.of(), Map.of());
            assertFalse(proxy.hasMember("count"));
            assertTrue(proxy.hasMember("boost"));
        }
    }

    private static RecipeTypeDefinition definition(Map<String, String> functions, RecipeExecutionFactory factory) {
        var field = new RecipeFieldDefinition("count", "count", RecipeFieldKind.INT, false,
                true, new JsonPrimitive(3), RecipeFieldRole.OTHER);
        return new RecipeTypeDefinition("test", "process", "test:process", null, List.of(List.of()),
                Map.of("count", field), List.of(), Map.of("alias", "count"), Map.of(), Map.of(), null, functions, factory);
    }

    public interface HiddenHook {
        @HideFromJS void finish();
    }

    public static class BaseTarget {
        @HideFromJS public void serialize() {}
    }

    public static final class Target extends BaseTarget implements HiddenHook {
        private final RecipeBuilder builder;
        Target(RecipeBuilder builder) { this.builder = builder; }
        public Target boost(int count) {
            builder.setPath("count", new RecipeJsonValue(new JsonPrimitive(count)));
            return this;
        }
        @Override public void serialize() { throw new AssertionError("Overridden lifecycle hook was exposed"); }
        @Override public void finish() { throw new AssertionError("Interface lifecycle hook was exposed"); }
        @HideFromJS public void hidden() { throw new AssertionError("Internal method was exposed"); }
        public void fail() { throw new IllegalStateException("target failure"); }
    }

    public static final class RecordingBuilder implements RecipeBuilder {
        final JsonObject json = new JsonObject();
        @Override public RecordingBuilder setPath(String path, RecipeJsonValue value) {
            json.add(path, (JsonElement) value.value());
            return this;
        }
    }

    private static final class Host implements RecipeSchemaHost {
        final RecordingBuilder builder = new RecordingBuilder();
        final RecipeBuilderSession session = new RecipeBuilderSession() {
            @Override public String currentId() { return "test:recipe"; }
            @Override public JsonObject snapshot() { return builder.json.deepCopy(); }
            @Override public void beforeEncode(Runnable action) { throw new AssertionError(); }
        };
        @Override public RecipeBuilder builder(String type, String prefix) { return builder; }
        @Override public RecipeBuilderSession session(RecipeBuilder recipe) { assertSame(builder, recipe); return session; }
        @Override public Object custom(JsonObject json) { throw new AssertionError(); }
        @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) { return new JsonPrimitive(value.asInt()); }
        @Override public JsonElement toJson(Value value) { throw new AssertionError(); }
    }

    @Test
    void canonicalExecutionFactoryBorrowsTheNativeBuilderSessionWithoutChangingOldConstructors() throws Exception {
        Class<?> session = Class.forName("com.tkisor.nekojs.api.recipe.RecipeBuilderSession");
        Class<?> factory = Class.forName("com.tkisor.nekojs.api.recipe.RecipeExecutionFactory");
        assertTrue(session.isInterface());
        assertTrue(factory.isInterface());
        assertNotNull(factory.getAnnotation(FunctionalInterface.class));
        assertEquals(Object.class, factory.getMethod("create", RecipeBuilder.class, RecipeSchemaHost.class, session).getReturnType());
        assertEquals(String.class, session.getMethod("currentId").getReturnType());
        assertEquals(JsonObject.class, session.getMethod("snapshot").getReturnType());
        assertEquals(void.class, session.getMethod("beforeEncode", Runnable.class).getReturnType());
        assertEquals(session, RecipeSchemaHost.class.getMethod("session", RecipeBuilder.class).getReturnType());
        assertTrue(RecipeSchemaHost.class.getMethod("session", RecipeBuilder.class).isDefault());
        assertEquals(Map.class, RecipeTypeDefinition.class.getMethod("fieldFunctions").getReturnType());
        assertEquals(factory, RecipeTypeDefinition.class.getMethod("executionFactory").getReturnType());
        assertNotNull(RecipeTypeDefinition.class.getConstructor(String.class, String.class, String.class,
                String.class, List.class, Map.class, List.class));
        assertNotNull(RecipeTypeDefinition.class.getConstructor(String.class, String.class, String.class,
                String.class, List.class, Map.class, List.class, Map.class, Map.class, Map.class, String.class));
    }
}
