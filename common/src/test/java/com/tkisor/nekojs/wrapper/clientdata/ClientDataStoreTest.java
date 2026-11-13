package com.tkisor.nekojs.wrapper.clientdata;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.bindings.static_access.ClientDataJS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for JSON round-tripping and the storage semantics of {@link ClientDataStore}:
 * a script-side object (GraalJS's default mapping for an {@code Object} parameter, so
 * Map/List/boxed primitives) goes through {@link ClientDataStore#toJsonElement}, JSON text (the
 * wire format), {@link JsonParser} and {@link ClientDataStore#accept}, and comes back out of
 * {@link ClientDataStore#get} as Map/List/boxed primitives. Plain Java, no Platform, Graal or
 * Minecraft fixture.
 */
class ClientDataStoreTest {

    private ClientDataStore store;

    @BeforeEach
    void setUp() {
        store = new ClientDataStore();
    }

    @AfterEach
    void tearDown() {
        ClientDataStore.SHARED.clear();
    }

    /** Simulates the network path: JsonElement to JSON text and back, matching the wire format of platform payloads. */
    private static JsonElement overTheWire(JsonElement element) {
        return JsonParser.parseString(element.toString());
    }

    @Test
    void jsonTypesRoundTripThroughWire() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("str", "hello");
        original.put("int", 42);
        original.put("long", 9_000_000_000L);
        original.put("double", 1.25);
        original.put("bool", true);
        original.put("null", null);
        original.put("nested", Map.of("a", List.of(1, 2, 3)));
        original.put("array", List.of("x", false, 3.5));

        JsonElement sent = ClientDataStore.toJsonElement(original);
        store.accept("cfg", overTheWire(sent));
        assertTrue(store.has("cfg"));

        Object received = store.get("cfg");
        assertTrue(received instanceof Map, "object 应还原为 Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) received;
        assertEquals("hello", map.get("str"));
        assertEquals(42L, map.get("int"), "整数统一还原为 Long");
        assertEquals(9_000_000_000L, map.get("long"));
        assertEquals(1.25, (Double) map.get("double"), 1e-12);
        assertEquals(Boolean.TRUE, map.get("bool"));
        assertNull(map.get("null"), "JSON null 还原为 null");
        assertEquals(List.of(1L, 2L, 3L), ((Map<?, ?>) map.get("nested")).get("a"), "嵌套 object/list 完整还原");
        assertEquals("x", ((List<?>) map.get("array")).get(0));
        assertEquals(Boolean.FALSE, ((List<?>) map.get("array")).get(1));
        assertEquals(3.5, (Double) ((List<?>) map.get("array")).get(2), 1e-12);
    }

    @Test
    void directPrimitivesRoundTrip() {
        store.accept("s", overTheWire(ClientDataStore.toJsonElement("text")));
        assertEquals("text", store.get("s"));

        store.accept("n", overTheWire(ClientDataStore.toJsonElement(7)));
        assertEquals(7L, store.get("n"));

        store.accept("d", overTheWire(ClientDataStore.toJsonElement(2.5)));
        assertEquals(2.5, (Double) store.get("d"), 1e-12);

        store.accept("b", overTheWire(ClientDataStore.toJsonElement(false)));
        assertEquals(Boolean.FALSE, store.get("b"));

        store.accept("nil", overTheWire(ClientDataStore.toJsonElement(null)));
        assertNull(store.get("nil"), "JSON null 取出为 null");
        assertTrue(store.has("nil"), "但 has 区分得出 null 值与缺失");
    }

    @Test
    void jsonElementAndNullValuePassThrough() {
        // A value that is already a JsonElement passes straight through; this is the path taken after a
        // platform handler parses the payload.
        JsonObject object = new JsonObject();
        object.addProperty("k", "v");
        store.accept("raw", object);
        assertEquals(Map.of("k", "v"), store.get("raw"));

        assertThrows(IllegalArgumentException.class, () -> store.accept("bad", null),
                "存储层不接受 Java null（JSON null 用 JsonNull.INSTANCE）");
        store.accept("jsonNull", JsonNull.INSTANCE);
        assertNull(store.get("jsonNull"));
    }

    @Test
    void sameKeyOverwritesAndClearResets() {
        store.accept("k", new JsonPrimitive(1));
        store.accept("k", new JsonPrimitive(2));
        assertEquals(2L, store.get("k"), "v1 语义：同 key 后到者覆盖");
        assertEquals(Set.of("k"), store.keys());
        assertEquals(1, store.size());

        store.clear();
        assertEquals(0, store.size());
        assertFalse(store.has("k"));
        assertNull(store.get("k"));
    }

    @Test
    void nonJsonValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ClientDataStore.toJsonElement(new Object()),
                "任意宿主对象不可序列化");
        assertThrows(IllegalArgumentException.class, () -> ClientDataStore.toJsonElement(System.out),
                "非 JSON 类型应显式拒绝");
    }

    @Test
    void keyValidation() {
        assertThrows(IllegalArgumentException.class, () -> store.accept(" ", new JsonPrimitive(1)));
        assertThrows(IllegalArgumentException.class, () -> store.accept(null, new JsonPrimitive(1)));
        assertThrows(IllegalArgumentException.class, () -> store.accept("x".repeat(257), new JsonPrimitive(1)));
        assertThrows(IllegalArgumentException.class, () -> store.get(null));
        assertThrows(IllegalArgumentException.class, () -> store.has(""));
    }

    @Test
    void clientDataBindingReadsSharedStore() {
        // The clientData binding is a read-only view of the SHARED store.
        ClientDataJS binding = new ClientDataJS();
        ClientDataStore.SHARED.accept("hud:mana", overTheWire(ClientDataStore.toJsonElement(Map.of("cur", 12))));
        assertTrue(binding.has("hud:mana"));
        assertEquals(12L, ((Map<?, ?>) binding.get("hud:mana")).get("cur"));
        assertEquals(Set.of("hud:mana"), binding.keys());
        assertEquals(1, binding.size());
        assertFalse(binding.has("missing"));
        assertNull(binding.get("missing"));
    }
}
