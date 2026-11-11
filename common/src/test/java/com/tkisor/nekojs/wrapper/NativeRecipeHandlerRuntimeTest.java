package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeHandlerRuntimeTest {
    public record Payload(String id) {}
    public record Box<T>(T value) {}

    public static class Handler {
        int calls;
        Object received;
        final RuntimeException failure = new ClassCastException("handler execution failed");

        public String list(List<Payload> values) { calls++; received = values; return ids(values); }
        public String collection(Collection<Payload> values) { calls++; received = values; return ids(values); }
        public String iterable(Iterable<Payload> values) { calls++; received = values; return ids(values); }
        public String nested(List<List<Payload>> rows) { calls++; return ids(rows.getFirst()); }
        public String map(Map<String, Payload> values) { calls++; received = values; return values.get("A").id(); }
        public String keyed(Map<Payload, List<Payload>> values) {
            calls++;
            var entry = values.entrySet().iterator().next();
            return entry.getKey().id() + ":" + ids(entry.getValue());
        }
        public String shaped(Payload result, List<String> pattern, Map<String, Payload> keys, Optional<String> group) {
            calls++;
            return "key:" + result.id() + ":" + pattern.getFirst() + ":" + keys.get("A").id() + ":" + group.orElse("none");
        }
        public String shaped(Payload result, List<List<Payload>> rows, Optional<String> group) {
            calls++;
            return "inline:" + result.id() + ":" + ids(rows.getFirst()) + ":" + group.orElse("none");
        }
        public String optional(Optional<Payload> value) { calls++; received = value; return value.map(Payload::id).orElse("empty"); }
        public String optionalThenRequired(Optional<Payload> value, String suffix) { calls++; return value.map(Payload::id).orElse("empty") + suffix; }
        public String box(Box<Payload> value) { calls++; received = value; return value.value().id(); }
        public String fail(List<Payload> values) { calls++; values.getFirst().id(); throw failure; }
        public String varargs(Payload... values) { calls++; return values[0].id(); }
        private static String ids(Iterable<Payload> values) {
            var result = new ArrayList<String>();
            for (Payload value : values) result.add(value.id());
            return String.join(",", result);
        }
    }

    public static class HiddenParent {
        @HideFromJS public String inheritedHidden(String value) { return value; }
    }
    public static class VisibleHandler extends HiddenParent {
        @Override public String inheritedHidden(String value) { return value; }
        @HideFromJS public String hidden(String value) { return value; }
        public String visible(String value) { return value; }
    }
    public static class GenericParent<T> { public T echo(T value) { return value; } }
    public static class PayloadHandler extends GenericParent<Payload> {
        @Override public Payload echo(Payload value) { return value; }
    }

    @Test
    void guestListsCollectionsAndNestedListsConsumeConvertedElements() {
        try (Context context = context()) {
            Handler handler = new Handler();
            var proxy = proxy(new RecordingHost(), handler);
            Value values = context.eval("js", "['p:a','p:b']");
            assertEquals("a,b", call(proxy, "list", values));
            assertEquals("a,b", call(proxy, "collection", values));
            assertEquals("a,b", call(proxy, "iterable", values));
            assertEquals("a,b", call(proxy, "nested", context.eval("js", "[['p:a','p:b']]")));
            assertEquals(4, handler.calls);
        }
    }

    @Test
    void guestMemberMapsAndHashMapsConsumeConvertedKeysAndValues() {
        try (Context context = context()) {
            Handler handler = new Handler();
            var proxy = proxy(new RecordingHost(), handler);
            assertEquals("a", call(proxy, "map", context.eval("js", "({A:'p:a'})")));
            assertEquals("a", call(proxy, "map", context.eval("js", "new Map([['A','p:a']])")));
            assertEquals("key:a,b", call(proxy, "keyed", context.eval("js", "({'p:key':['p:a','p:b']})")));
            assertEquals("key:a,b", call(proxy, "keyed", context.eval("js", "new Map([['p:key',['p:a','p:b']]])")));
            assertEquals(4, handler.calls);
        }
    }

    @Test
    void overlappingShapedAritiesConsumeBothGenericFormsRegardlessOfReflectionOrder() {
        try (Context context = context()) {
            Handler handler = new Handler();
            var proxy = proxy(new RecordingHost(), handler);
            Value result = context.asValue("p:out");
            assertEquals("key:out:A:in:none", call(proxy, "shaped", result,
                    context.eval("js", "['A']"), context.eval("js", "({A:'p:in'})")));
            assertEquals("inline:out:in:group", call(proxy, "shaped", result,
                    context.eval("js", "[['p:in']]"), context.asValue("group")));
            assertEquals(2, handler.calls);
        }
    }

    @Test
    void typedHostCollectionsAndGenericBoxesRetainTheirOriginalIdentity() {
        try (Context context = context()) {
            Handler handler = new Handler();
            var proxy = proxy(new RecordingHost(), handler);
            List<Payload> list = new ArrayList<>(List.of(new Payload("host")));
            assertEquals("host", call(proxy, "list", context.asValue(list)));
            assertSame(list, handler.received);
            Map<String, Payload> map = new LinkedHashMap<>(Map.of("A", new Payload("map")));
            assertEquals("map", call(proxy, "map", context.asValue(map)));
            assertSame(map, handler.received);
            Box<Payload> box = new Box<>(new Payload("box"));
            assertEquals("box", call(proxy, "box", context.asValue(box)));
            assertSame(box, handler.received);
        }
    }

    @Test
    void optionalPayloadsUseTheExistingLeafAdapterAndSupportNullUndefinedAndOmission() {
        try (Context context = context()) {
            Handler handler = new Handler();
            var proxy = proxy(new RecordingHost(), handler);
            assertEquals("value", call(proxy, "optional", context.asValue("p:value")));
            assertEquals("empty", call(proxy, "optional", context.eval("js", "null")));
            assertEquals("empty", call(proxy, "optional", context.eval("js", "undefined")));
            assertEquals("empty", call(proxy, "optional"));
            Optional<Payload> host = Optional.of(new Payload("host"));
            assertEquals("host", call(proxy, "optional", context.asValue(host)));
            assertSame(host, handler.received);
        }
    }

    @Test
    void anOptionalBeforeARequiredParameterDoesNotPermitOmittingTheRequiredTail() throws Exception {
        var method = Handler.class.getMethod("optionalThenRequired", Optional.class, String.class);
        assertEquals(2, RecipeReflectionUtil.requiredParamCount(method));
        try (Context context = context()) {
            assertNull(RecipeReflectionUtil.convertArgs(method, new Value[]{context.eval("js", "null")}, 2));
            Handler handler = new Handler();
            assertEquals("emptytail", call(proxy(new RecordingHost(), handler), "optionalThenRequired",
                    context.eval("js", "undefined"), context.asValue("tail")));
        }
    }

    @Test
    void onlyConversionMismatchesPermitRawFallbackAndExecutionFailuresStayFailures() {
        try (Context context = context()) {
            Handler handler = new Handler();
            RecordingHost host = new RecordingHost();
            var proxy = proxy(host, handler);
            call(proxy, "list", context.eval("js", "['not-a-payload']"));
            assertEquals(0, handler.calls);
            assertEquals(1, host.rawCalls);
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> call(proxy, "fail", context.eval("js", "['p:valid']")));
            assertSame(handler.failure, failure);
            assertEquals(1, handler.calls);
            assertEquals(1, host.rawCalls);
        }
    }

    @Test
    void nullListReachesTheHandlerAndItsBodyFailureDoesNotFallBack() {
        try (Context context = context()) {
            Handler handler = new Handler();
            RecordingHost host = new RecordingHost();
            Value nullList = context.eval("js", "null");
            assertNull(nullList.as(List.class));
            assertThrows(NullPointerException.class, () -> call(proxy(host, handler), "list", nullList));
            assertEquals(1, handler.calls, "The handler body must consume the converted null");
            assertNull(handler.received);
            assertEquals(0, host.rawCalls);
        }
    }

    @Test
    void nestedAdapterExecutionFailuresAreNotConversionMismatchFallback() {
        try (Context context = context()) {
            Handler handler = new Handler();
            RecordingHost host = new RecordingHost();
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> call(proxy(host, handler), "nested", context.eval("js", "[['p:boom']]")));
            Throwable hostFailure = failure instanceof PolyglotException polyglot && polyglot.isHostException()
                    ? polyglot.asHostException() : failure;
            assertSame(ADAPTER_FAILURE, hostFailure.getCause());
            assertTrue(hostFailure.getMessage().contains("NEKO-5202"));
            assertEquals(0, handler.calls);
            assertEquals(0, host.rawCalls);
        }
    }

    @Test
    void hiddenInheritedAndBridgeMethodsAreNotIndexed() {
        var visible = RecipeReflectionUtil.reflectMethods(VisibleHandler.class);
        assertTrue(visible.containsKey("visible"));
        assertFalse(visible.containsKey("hidden"));
        assertFalse(visible.containsKey("inheritedHidden"));
        var methods = RecipeReflectionUtil.reflectMethods(PayloadHandler.class).get("echo");
        assertEquals(1, methods.size());
        assertFalse(methods.getFirst().isBridge());
        assertFalse(methods.getFirst().isSynthetic());
    }

    @Test
    void javaVarargsKeepTheirExistingSingleArrayArgumentContract() {
        try (Context context = context()) {
            Handler handler = new Handler();
            RecordingHost host = new RecordingHost();
            var proxy = proxy(host, handler);
            assertEquals("array", call(proxy, "varargs", context.eval("js", "['p:array']")));
            assertNull(call(proxy, "varargs"));
            assertNull(call(proxy, "varargs", context.asValue("p:a"), context.asValue("p:b")));
            assertEquals(1, handler.calls);
            assertEquals(0, host.rawCalls);
        }
    }

    private static final ClassCastException ADAPTER_FAILURE = new ClassCastException("nested adapter execution failed");
    private static Context context() {
        JSTypeAdapter<Payload> adapter = new JSTypeAdapter<>() {
            @Override public Class<Payload> getTargetClass() { return Payload.class; }
            @Override public boolean test(Value value) { return value.isString() && value.asString().startsWith("p:"); }
            @Override public Payload apply(Value value) {
                String id = value.asString().substring(2);
                if (id.equals("boom")) throw ADAPTER_FAILURE;
                return new Payload(id);
            }
        };
        return Context.newBuilder("js").allowHostAccess(new NekoSharedHostAccess(List.of(adapter)).get()).build();
    }
    private static RecipeNamespaceProxy proxy(RecordingHost host, Object handler) {
        return new RecipeNamespaceProxy(host, "test", handler, RecipeTypeDefinitionRegistry.EMPTY);
    }
    private static Object call(RecipeNamespaceProxy proxy, String name, Value... arguments) {
        return ((ProxyExecutable) proxy.getMember(name)).execute(arguments);
    }
    private static class RecordingHost implements RecipeSchemaHost {
        int rawCalls;
        @Override public RecipeBuilder builder(String type, String prefix) { throw new AssertionError("Unexpected schema fallback"); }
        @Override public Object custom(JsonObject json) { rawCalls++; return json; }
        @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) { return toJson(value); }
        @Override public JsonElement toJson(Value value) { return new JsonObject(); }
    }
}
