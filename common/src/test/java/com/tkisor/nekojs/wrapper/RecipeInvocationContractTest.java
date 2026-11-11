package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeInvocationContractTest {
    private static RecipeTypeDefinition definition() {
        return new RecipeTypeDefinition("test", "recipe", "test:recipe", "test_recipe",
                List.of(List.of("value")), Map.of(), List.of());
    }

    private static Object call(ProxyObject proxy, String member, Value... args) {
        return ((ProxyExecutable) proxy.getMember(member)).execute(args);
    }

    private static final class RecordingHost implements RecipeSchemaHost {
        int schemaCalls;
        int rawCalls;
        @Override public RecipeBuilder builder(String type, String prefix) { schemaCalls++; return new Delegate(); }
        @Override public Object custom(JsonObject json) { rawCalls++; return json; }
        @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) { return toJson(value); }
        @Override public JsonElement toJson(Value value) { return new JsonObject(); }
    }

    public static class Delegate implements RecipeBuilder {
        final RuntimeException failure = new ClassCastException("selected recipe operation failed");
        int calls;
        public Object recipe(Map<?, ?> value) { calls++; throw failure; }
        public Object recipe(Object value) { calls++; throw failure; }
        @Override public RecipeBuilder setPath(String path, com.tkisor.nekojs.api.recipe.RecipeJsonValue value) { return this; }
    }

    public static class Overloads implements RecipeBuilder {
        int calls;
        public String choose(int value) { calls++; return "integer"; }
        public String choose(boolean value) { calls++; return "boolean"; }
        public String primitive(boolean value) { calls++; return "primitive"; }
        public String primitive(byte value) { calls++; return "primitive"; }
        public String primitive(short value) { calls++; return "primitive"; }
        public String primitive(char value) { calls++; return "primitive"; }
        public String primitive(int value) { calls++; return "primitive"; }
        public String primitive(long value) { calls++; return "primitive"; }
        public String primitive(float value) { calls++; return "primitive"; }
        public String primitive(double value) { calls++; return "primitive"; }
        public Object pair(int first, int second) { calls++; return "pair"; }
        public Object fluent(String value, Optional<?> suffix) { calls++; return suffix.isEmpty() ? this : suffix.get(); }
        @Override public RecipeBuilder setPath(String path, com.tkisor.nekojs.api.recipe.RecipeJsonValue value) { return this; }
    }

    public static class FailingDelegate extends Overloads {
        final Error error = new AssertionError("recipe fatal failure");
        final Exception checked = new Exception("recipe checked failure");
        public Object fatal() { calls++; throw error; }
        public Object checked() throws Exception { calls++; throw checked; }
    }

    public static class Converted {}
    public static class ConvertingDelegate extends Overloads {
        public Object convert(Converted value) { calls++; return value; }
        public int scalar(int value) { calls++; return value; }
    }

    private static List<ProxyObject> proxies(RecordingHost host, RecipeBuilder delegate) {
        return List.of(new RecipeNamespaceProxy(host, "test", delegate, RecipeTypeDefinitionRegistry.EMPTY),
                new SchemaRecipeBuilder(delegate, definition(), host, Map.of()));
    }

    @Test
    void incompatibleConversionTriesAnotherOverloadInBothProxies() {
        try (Context context = Context.newBuilder("js").build()) {
            Value integer = context.asValue(17);
            Value bool = context.asValue(true);
            assertThrows(ClassCastException.class, () -> integer.as(boolean.class));
            assertThrows(ClassCastException.class, () -> bool.as(int.class));
            Overloads delegate = new Overloads();
            RecordingHost host = new RecordingHost();
            // Both inputs succeed regardless of the VM's reflection order; one must skip the first overload.
            for (ProxyObject proxy : proxies(host, delegate)) {
                assertEquals("integer", call(proxy, "choose", integer));
                assertEquals("boolean", call(proxy, "choose", bool));
            }
            assertEquals(4, delegate.calls);
            assertEquals(0, host.rawCalls);
        }
    }

    @Test
    void onlyGenuineMismatchPermitsSchemaOrRawFallback() {
        try (Context context = Context.newBuilder("js").build()) {
            Overloads delegate = new Overloads();
            RecordingHost host = new RecordingHost();
            RecipeTypeDefinition schema = new RecipeTypeDefinition("test", "primitive", "test:primitive", "primitive",
                    List.of(List.of("value")), Map.of(), List.of());
            ProxyObject namespace = new RecipeNamespaceProxy(host, "test", delegate,
                    RecipeTypeDefinitionRegistry.builder().add(schema).build());
            Value object = context.eval("js", "({value: 1})");
            assertInstanceOf(SchemaRecipeBuilder.class, call(namespace, "primitive", object));
            ProxyObject raw = new RecipeNamespaceProxy(host, "test", delegate, RecipeTypeDefinitionRegistry.EMPTY);
            JsonObject json = assertInstanceOf(JsonObject.class, call(raw, "primitive", object));
            assertEquals("test:primitive", json.get("type").getAsString());
            assertEquals(1, host.schemaCalls);
            assertEquals(1, host.rawCalls);
            assertEquals(0, delegate.calls);
        }
    }

    @Test
    void nullForPrimitiveIsAMismatchNotAnInvocationFailure() {
        try (Context context = Context.newBuilder("js").build()) {
            Overloads delegate = new Overloads();
            RecordingHost host = new RecordingHost();
            List<ProxyObject> proxies = proxies(host, delegate);
            Value nullValue = context.eval("js", "null");
            assertNull(nullValue.as(Integer.class));
            assertThrows(NullPointerException.class, () -> nullValue.as(int.class));
            assertNull(call(proxies.get(0), "primitive", nullValue));
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> call(proxies.get(1), "primitive", nullValue));
            assertTrue(failure.getMessage().contains("No overload"));
            assertEquals(0, delegate.calls);
        }
    }

    @Test
    void missingOptionalAndFluentSelfKeepExistingContract() {
        try (Context context = Context.newBuilder("js").build()) {
            Overloads delegate = new Overloads();
            List<ProxyObject> proxies = proxies(new RecordingHost(), delegate);
            assertSame(delegate, call(proxies.get(0), "fluent", context.asValue("value")));
            assertSame(proxies.get(1), call(proxies.get(1), "fluent", context.asValue("value")));
            assertEquals(2, delegate.calls);
        }
    }

    @Test
    void errorsPropagateByIdentityAndCheckedFailuresPreserveTheirCause() {
        FailingDelegate delegate = new FailingDelegate();
        for (ProxyObject proxy : proxies(new RecordingHost(), delegate)) {
            assertSame(delegate.error, assertThrows(AssertionError.class, () -> call(proxy, "fatal")));
            IllegalStateException checked = assertThrows(IllegalStateException.class, () -> call(proxy, "checked"));
            assertSame(delegate.checked, checked.getCause());
            assertTrue(checked.getMessage().contains("NEKO-5200"));
            assertTrue(checked.getMessage().contains("checked"));
        }
        assertEquals(4, delegate.calls);
    }

    @Test
    void closedGuestValueDoesNotBecomeAnOverloadMismatch() {
        Context closed = Context.newBuilder("js").build();
        Value object;
        try { object = closed.eval("js", "({value: 1})"); }
        finally { closed.close(); }
        try (Context open = Context.newBuilder("js").build()) {
            Overloads delegate = new Overloads();
            RecordingHost host = new RecordingHost();
            for (ProxyObject proxy : proxies(host, delegate)) {
                assertThrows(IllegalStateException.class, () -> call(proxy, "pair", object, open.asValue(1)));
            }
            assertEquals(0, delegate.calls);
            assertEquals(0, host.schemaCalls);
            assertEquals(0, host.rawCalls);
        }
    }

    private static <T> JSTypeAdapter<T> adapter(Class<T> target, Predicate<Value> accepts, Function<Value, T> convert) {
        return new JSTypeAdapter<>() {
            @Override public Class<T> getTargetClass() { return target; }
            @Override public boolean test(Value value) { return accepts.test(value); }
            @Override public T apply(Value value) { return convert.apply(value); }
            @Override public ConversionPrecedence getPrecedence() { return ConversionPrecedence.HIGHEST; }
        };
    }

    @Test
    void customConverterFailureIsNotReclassifiedAsTargetTypeMismatch() {
        assertAdapterFailureEscapes(false);
    }

    @Test
    void customAcceptanceFailureIsNotReclassifiedAsTargetTypeMismatch() {
        assertAdapterFailureEscapes(true);
    }

    private static void assertAdapterFailureEscapes(boolean failAcceptance) {
        ClassCastException failure = new ClassCastException("custom adapter failed");
        int[] acceptances = {0};
        int[] conversions = {0};
        JSTypeAdapter<Converted> adapter = adapter(Converted.class, value -> {
            acceptances[0]++;
            if (failAcceptance) throw failure;
            return value.isString();
        }, value -> {
            conversions[0]++;
            throw failure;
        });
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of(adapter)).get()).build()) {
            ConvertingDelegate delegate = new ConvertingDelegate();
            RecordingHost host = new RecordingHost();
            RecipeTypeDefinition schema = new RecipeTypeDefinition("test", "convert", "test:convert", "convert",
                    List.of(List.of("value")), Map.of(), List.of());
            List<ProxyObject> proxies = List.of(
                    new RecipeNamespaceProxy(host, "test", delegate, RecipeTypeDefinitionRegistry.EMPTY),
                    new RecipeNamespaceProxy(host, "test", delegate,
                            RecipeTypeDefinitionRegistry.builder().add(schema).build()),
                    new SchemaRecipeBuilder(delegate, definition(), host, Map.of()));
            for (ProxyObject proxy : proxies) {
                RuntimeException actual = assertThrows(RuntimeException.class,
                        () -> call(proxy, "convert", context.asValue("trigger")));
                Throwable adapterFailure = actual instanceof PolyglotException polyglot && polyglot.isHostException()
                        ? polyglot.asHostException() : actual;
                assertSame(failure, adapterFailure.getCause());
                assertTrue(adapterFailure.getMessage().contains("NEKO-5202"));
                assertTrue(adapterFailure.getMessage().contains(failAcceptance ? "acceptance" : "conversion"));
                assertTrue(adapterFailure.getMessage().contains(Converted.class.getName()));
            }
            assertTrue(acceptances[0] >= proxies.size());
            assertEquals(failAcceptance ? 0 : proxies.size(), conversions[0]);
            assertEquals(0, delegate.calls);
            assertEquals(0, host.schemaCalls);
            assertEquals(0, host.rawCalls);
        }
    }

    @Test
    void successfulCustomReferenceConversionStillInvokesSelectedMethod() {
        Converted expected = new Converted();
        int[] conversions = {0};
        JSTypeAdapter<Converted> adapter = adapter(Converted.class, Value::isString, value -> {
            conversions[0]++;
            return expected;
        });
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of(adapter)).get()).build()) {
            ConvertingDelegate delegate = new ConvertingDelegate();
            for (ProxyObject proxy : proxies(new RecordingHost(), delegate)) {
                assertSame(expected, call(proxy, "convert", context.asValue("trigger")));
            }
            assertEquals(2, conversions[0]);
            assertEquals(2, delegate.calls);
        }
    }

    @Test
    void customScalarConversionStillAppliesToPrimitiveParameters() {
        int[] conversions = {0};
        JSTypeAdapter<Integer> adapter = adapter(Integer.class, Value::isString, value -> {
            conversions[0]++;
            return 41;
        });
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of(adapter)).get()).build()) {
            ConvertingDelegate delegate = new ConvertingDelegate();
            for (ProxyObject proxy : proxies(new RecordingHost(), delegate)) {
                assertEquals(41, call(proxy, "scalar", context.asValue("trigger")));
            }
            assertEquals(2, conversions[0]);
            assertEquals(2, delegate.calls);
        }
    }

    @Test
    void customNullConversionStillAppliesToPrimitiveParameters() {
        int[] conversions = {0};
        JSTypeAdapter<Integer> adapter = adapter(Integer.class, Value::isNull, value -> {
            conversions[0]++;
            return 41;
        });
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of(adapter)).get()).build()) {
            Value nullValue = context.eval("js", "null");
            assertEquals(41, nullValue.as(int.class).intValue());
            assertEquals(41, nullValue.as(Integer.class).intValue());
            assertEquals(2, conversions[0]);
            ConvertingDelegate delegate = new ConvertingDelegate();
            RecordingHost host = new RecordingHost();
            for (ProxyObject proxy : proxies(host, delegate)) {
                assertEquals(41, call(proxy, "scalar", nullValue));
            }
            assertEquals(4, conversions[0]);
            assertEquals(2, delegate.calls);
            assertEquals(0, host.schemaCalls);
            assertEquals(0, host.rawCalls);
        }
    }

    @Test
    void failingCustomNullConversionRetainsAdapterCause() {
        ClassCastException failure = new ClassCastException("custom null adapter failed");
        int[] conversions = {0};
        JSTypeAdapter<Integer> adapter = adapter(Integer.class, Value::isNull, value -> {
            conversions[0]++;
            throw failure;
        });
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of(adapter)).get()).build()) {
            Value nullValue = context.eval("js", "null");
            RuntimeException direct = assertThrows(RuntimeException.class, () -> nullValue.as(int.class));
            assertNullAdapterCause(direct, failure);
            RuntimeException boxed = assertThrows(RuntimeException.class, () -> nullValue.as(Integer.class));
            assertNullAdapterCause(boxed, failure);
            assertEquals(2, conversions[0]);
            ConvertingDelegate delegate = new ConvertingDelegate();
            RecordingHost host = new RecordingHost();
            for (ProxyObject proxy : proxies(host, delegate)) {
                RuntimeException actual = assertThrows(RuntimeException.class, () -> call(proxy, "scalar", nullValue));
                assertNullAdapterCause(actual, failure);
            }
            assertEquals(4, conversions[0]);
            assertEquals(0, delegate.calls);
            assertEquals(0, host.schemaCalls);
            assertEquals(0, host.rawCalls);
        }
    }

    private static void assertNullAdapterCause(RuntimeException actual, ClassCastException failure) {
        Throwable adapterFailure = actual instanceof PolyglotException polyglot && polyglot.isHostException()
                ? polyglot.asHostException() : actual;
        assertSame(failure, adapterFailure.getCause());
        assertTrue(adapterFailure.getMessage().contains("NEKO-5202"));
        assertTrue(adapterFailure.getMessage().contains("conversion"));
        assertTrue(adapterFailure.getMessage().contains(Integer.class.getName()));
    }

    @Test
    void selectedBuilderFailureEscapesWithoutTryingAnotherConvertibleOverload() {
        try (Context context = Context.newBuilder("js").build()) {
            Delegate delegate = new Delegate();
            ProxyObject builder = new SchemaRecipeBuilder(delegate, definition(), new RecordingHost(), Map.of());
            RuntimeException actual = assertThrows(RuntimeException.class,
                    () -> call(builder, "recipe", context.eval("js", "({value: 1})")));
            assertSame(delegate.failure, actual);
            assertEquals(1, delegate.calls);
        }
    }

    @Test
    void selectedHandlerFailureEscapesWithoutSchemaOrRawFallback() {
        try (Context context = Context.newBuilder("js").build()) {
            for (boolean schemaPresent : List.of(false, true)) {
                RecordingHost host = new RecordingHost();
                Delegate delegate = new Delegate();
                RecipeTypeDefinitionRegistry definitions = schemaPresent
                        ? RecipeTypeDefinitionRegistry.builder().add(definition()).build()
                        : RecipeTypeDefinitionRegistry.EMPTY;
                ProxyObject namespace = new RecipeNamespaceProxy(host, "test", delegate, definitions);
                RuntimeException actual = assertThrows(RuntimeException.class,
                        () -> call(namespace, "recipe", context.eval("js", "({value: 1})")));
                assertSame(delegate.failure, actual);
                assertEquals(1, delegate.calls);
                assertEquals(0, host.schemaCalls);
                assertEquals(0, host.rawCalls);
            }
        }
    }
}
