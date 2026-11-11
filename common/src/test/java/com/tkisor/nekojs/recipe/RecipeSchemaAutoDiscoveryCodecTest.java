package com.tkisor.nekojs.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.*;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import graal.graalvm.polyglot.Value;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeSchemaAutoDiscoveryCodecTest {
    @Test
    void oldKeyConstructorRetainsItsScalarContract() {
        var key = new RecipeSchemaAutoDiscovery.DiscoveredRecipeKey("result", RecipeFieldKind.ITEM_STACK, false, false);
        var definition = discover(key);
        assertNull(definition.fields().get("result").valueCodec());
        assertEquals(List.of(List.of("result")), definition.constructors());
    }

    @Test
    void discoveryPreservesWholeFieldCodecAndNominalCollectionInput() throws ReflectiveOperationException {
        var calls = new AtomicInteger();
        ApiTypeRef list = ApiTypeRef.symbol(new ApiSymbolId("java", List.class.getName()), List.of(ApiTypeRef.primitive("string")));
        var codec = new RecipeFieldValueCodec(AdapterInputShape.arrayOf(AdapterInputShape.string()), list,
                (value, host) -> { calls.incrementAndGet(); return new JsonPrimitive("converted"); });
        var constructor = RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.class.getConstructor(
                String.class, RecipeFieldKind.class, boolean.class, boolean.class, RecipeFieldValueCodec.class);
        var key = constructor.newInstance("pattern", RecipeFieldKind.JSON, false, false, codec);
        var field = discover(key).fields().get("pattern");
        assertSame(codec, field.valueCodec());
        assertEquals(list, field.valueCodec().inputType());
        RecipeSchemaHost host = new RecipeSchemaHost() {
            @Override public RecipeBuilder builder(String type, String prefix) { throw new AssertionError("No publication"); }
            @Override public Object custom(JsonObject value) { throw new AssertionError("No publication"); }
            @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) { throw new AssertionError("Explicit codec must own the field"); }
            @Override public JsonElement toJson(Value value) { throw new AssertionError("No raw fallback"); }
        };
        assertEquals(new JsonPrimitive("converted"), host.convertField(field, Value.asValue("input")));
        assertEquals(1, calls.get());
    }

    @Test
    void listHelperDoesNotDiscardExplicitWholeFieldCodec() throws ReflectiveOperationException {
        var codec = new RecipeFieldValueCodec(AdapterInputShape.arrayOf(AdapterInputShape.string()),
                (value, host) -> new JsonPrimitive("converted"));
        var constructor = RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.class.getConstructor(
                String.class, RecipeFieldKind.class, boolean.class, boolean.class, RecipeFieldValueCodec.class);
        var inner = constructor.newInstance("ingredients", RecipeFieldKind.INGREDIENT, true, false, codec);
        var key = RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.list(inner);
        var field = discover(key).fields().get("ingredients");
        assertTrue(field.array());
        assertTrue(field.optional());
        assertSame(codec, field.valueCodec());
    }

    private static RecipeTypeDefinition discover(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey key) {
        return RecipeSchemaAutoDiscovery.discover(() -> RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes.of(
                Map.of("test:recipe", List.of(key)))).get("test", "recipe");
    }
}
