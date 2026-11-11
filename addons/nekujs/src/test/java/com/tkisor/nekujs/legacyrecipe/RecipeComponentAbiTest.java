package com.tkisor.nekujs.legacyrecipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.recipe.component.ComponentRole;
import dev.latvian.mods.kubejs.recipe.component.EnumComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.SimpleRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.UniqueIdBuilder;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RecipeComponentAbiTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
    private static RecipeComponentType.Unit<String> text() {
        return RecipeComponentType.unit(id("text"), type -> new SimpleRecipeComponent<>(type, Codec.STRING, TypeInfo.of(String.class)));
    }
    private record CountComponent(RecipeComponentType<?> type, int count) implements RecipeComponent<Integer> {
        @Override public Codec<Integer> codec() { return Codec.INT; }
        @Override public TypeInfo typeInfo() { return TypeInfo.of(Integer.class); }
    }
    private enum Heat implements StringRepresentable {
        NONE, HOT;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    @Test void unitMetadataCodecAndKeyRolesAreReal() {
        var text = text();
        var storage = new RecipeSchemaStorage(null);
        storage.register(text);
        storage.freezeComponents();
        var component = text.instance();
        assertSame(component, text.instance());
        assertSame(component, storage.recipeComponentCodec.parse(JsonOps.INSTANCE, new JsonPrimitive("test:text")).getOrThrow());
        assertEquals(new JsonPrimitive("test:text"), storage.recipeComponentCodec.encodeStart(JsonOps.INSTANCE, component).getOrThrow());
        assertEquals("hello", component.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("hello")).getOrThrow());
        var key = component.inputKey("input").alt("ingredient", "value");
        assertEquals(ComponentRole.INPUT, key.role);
        assertEquals(List.of("input", "ingredient", "value"), List.copyOf(key.names));
        assertSame(component.codec(), key.codec);
        assertEquals(String.class, key.typeInfo.asClass());
        assertThrows(UnsupportedOperationException.class, () -> storage.componentTypes().clear());
    }

    @Test void dynamicCodecDispatchesItsActualMapCodec() {
        var dynamic = RecipeComponentType.dynamic(id("count"), (type, context) ->
                Codec.INT.fieldOf("count").xmap(count -> new CountComponent(type, count), CountComponent::count));
        var storage = new RecipeSchemaStorage(null);
        storage.register(dynamic);
        storage.freezeComponents();
        var json = new JsonObject();
        json.addProperty("type", "test:count");
        json.addProperty("count", 7);
        var parsed = (CountComponent) storage.recipeComponentCodec.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertSame(dynamic, parsed.type());
        assertEquals(7, parsed.count());
        assertEquals(json, storage.recipeComponentCodec.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow());
        assertTrue(storage.recipeComponentCodec.parse(JsonOps.INSTANCE, new JsonPrimitive("test:count")).error().isPresent());
        assertTrue(assertThrows(UnsupportedOperationException.class, dynamic::instance).getMessage().startsWith("NEKO-4233:"));
    }

    @Test void enumUsesSuppliedNativeCodecAndMetadata() {
        var codec = StringRepresentable.fromEnum(Heat::values);
        var type = EnumComponent.of(id("heat"), Heat.class, codec);
        assertSame(codec, type.instance().codec());
        assertEquals(Heat.class, type.instance().typeInfo().asClass());
        assertEquals(Heat.HOT, type.instance().codec().parse(JsonOps.INSTANCE, new JsonPrimitive("hot")).getOrThrow());
        var builder = new UniqueIdBuilder(new StringBuilder());
        type.instance().buildUniqueId(builder, Heat.HOT);
        assertEquals("hot", builder.build());
    }

    @Test void unsupportedExecutionFailsAndProgrammaticSchemaMetadataIsRegistered() {
        var component = text().instance();
        assertTrue(assertThrows(UnsupportedOperationException.class, () -> component.matches(null, "value", null)).getMessage().startsWith("NEKO-4230:"));
        assertTrue(assertThrows(UnsupportedOperationException.class, () -> component.replace(null, "value", null, "other")).getMessage().startsWith("NEKO-4231:"));
        assertTrue(assertThrows(UnsupportedOperationException.class, () -> component.isEmpty("")).getMessage().startsWith("NEKO-4232:"));
        var schema = new RecipeSchema(component.inputKey("input"));
        assertEquals(1, schema.minRequiredArguments());
        assertEquals(List.of("input"), schema.keys.stream().map(key -> key.name).toList());
        var storage = new RecipeSchemaStorage(null);
        var schemas = new RecipeSchemaRegistry(storage);
        schemas.namespace("test").register("native", schema);
        assertSame(schema, storage.programmaticSchemas().get(id("native")));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> schemas.namespace("test").register("native", schema))
                .getMessage().contains("NEKO-5320"));
    }

    @Test void catalogRejectsDuplicatesLateRegistrationAndUnknownTypes() {
        var storage = new RecipeSchemaStorage(null);
        var type = text();
        storage.register(type);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> storage.register(text())).getMessage().startsWith("NEKO-4238:"));
        assertTrue(assertThrows(IllegalStateException.class, () -> storage.recipeComponentCodec.parse(JsonOps.INSTANCE, new JsonPrimitive("test:text"))).getMessage().startsWith("NEKO-4242:"));
        storage.freezeComponents();
        storage.freezeComponents();
        assertTrue(assertThrows(IllegalStateException.class, () -> storage.register(text())).getMessage().startsWith("NEKO-4240:"));
        assertTrue(storage.recipeComponentCodec.parse(JsonOps.INSTANCE, new JsonPrimitive("test:missing")).error().isPresent());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> storage.recipeComponentCodec.encodeStart(JsonOps.INSTANCE, text().instance())).getMessage().startsWith("NEKO-4244:"));
    }

    @Test void uniqueIdsNormalizeResourceLocationsAndResetBuffer() {
        var ids = new UniqueIdBuilder(new StringBuilder());
        ids.append(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"));
        ids.append(ResourceLocation.fromNamespaceAndPath("test", "ore/raw"));
        ids.appendSeparator();
        ids.append("HOT  water");
        assertEquals("iron_ingot_test_ore_raw_hot_water", ids.build());
        assertNull(ids.build());
    }
}
