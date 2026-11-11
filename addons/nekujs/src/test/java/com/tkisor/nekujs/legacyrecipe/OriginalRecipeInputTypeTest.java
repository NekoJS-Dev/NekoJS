package com.tkisor.nekujs.legacyrecipe;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldValueCodec;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekujs.recipe.schema.ProgrammaticRecipeSchemas;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.SimpleRecipeComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.kubejs.util.IntBounds;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.rhino.type.TypeInfo;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OriginalRecipeInputTypeTest {
    private static RegistryAccessContainer registries;
    private record Box<T>(T value) {}

    @BeforeAll static void bootstrap() {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = new RegistryAccessContainer(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    @Test void listMetadataAddsSelfOnlyWhenTheOriginalFlagAllowsIt() {
        var element = box(TypeInfo.of(Box.class).withParams(TypeInfo.of(String.class)));
        TypeInfo expectedList = TypeInfo.of(List.class).withParams(element.typeInfo());
        assertEquals(expectedList, element.asList().typeInfo());
        assertEquals(expectedList.or(element.typeInfo()), element.asListOrSelf().typeInfo());
        assertEquals(expectedList, element.asConditionalList().typeInfo());
        assertEquals(expectedList.or(element.typeInfo()), element.asConditionalListOrSelf().withBounds(IntBounds.of(1, 3)).typeInfo());
        assertEquals(List.of(2), NumberComponent.INT.asList().wrap(null, 2),
                "Original wrap lenience is independent of the list-only declared input type");
    }

    @Test void programmaticCodecsKeepOriginalGenericUnionAndListMetadata() {
        TypeInfo generic = TypeInfo.of(Box.class).withParams(TypeInfo.of(String.class));
        var boxed = box(generic);
        ApiTypeRef nominal = nominal(Box.class, ApiTypeRef.primitive("string"));
        ApiTypeRef list = nominal(List.class, nominal);
        assertEquals(nominal, field(boxed).inputType());
        assertEquals(list, field(boxed.asList()).inputType());
        assertEquals(ApiTypeRef.union(List.of(list, nominal)), field(boxed.asListOrSelf()).inputType());
        assertEquals(ApiTypeRef.union(List.of(nominal, ApiTypeRef.primitive("number"))),
                field(box(generic.or(TypeInfo.of(Integer.class)))).inputType());
    }

    @Test void rawGenericsUnknownSentinelAndUnrecognizedTypeInfoDoNotNeedDescriptors() {
        assertEquals(nominal(Box.class, ApiTypeRef.primitive("any")), field(box(TypeInfo.of(Box.class))).inputType());
        assertEquals(ApiTypeRef.primitive("any"), field(box(TypeInfo.NONE)).inputType());
        TypeInfo originalExtension = new TypeInfo() {
            @Override public Class<?> asClass() { return Box.class; }
        };
        assertEquals(nominal(Box.class, ApiTypeRef.primitive("any")), field(box(originalExtension)).inputType());
    }

    @Test void declaredListOrSelfMetadataDoesNotWeakenElementCodecsOrStrictArrayInputs() {
        var strict = field(NumberComponent.intRange(1, 3).asList().withBounds(IntBounds.of(1, 2)));
        var self = field(NumberComponent.intRange(1, 3).asListOrSelf().withBounds(IntBounds.of(1, 2)));
        assertEquals(nominal(List.class, ApiTypeRef.primitive("number")), strict.inputType());
        assertEquals(ApiTypeRef.union(List.of(nominal(List.class, ApiTypeRef.primitive("number")), ApiTypeRef.primitive("number"))), self.inputType());
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            assertEquals(JsonParser.parseString("[2,3]"), strict.encoder().encode(context.eval("js", "[2,3]"), new Host()));
            assertEquals(new JsonPrimitive(2), self.encoder().encode(context.eval("js", "2"), new Host()));
            assertThrows(IllegalArgumentException.class, () -> strict.encoder().encode(context.eval("js", "2"), new Host()));
            assertThrows(IllegalArgumentException.class, () -> self.encoder().encode(context.eval("js", "[2,4]"), new Host()));
            assertThrows(IllegalArgumentException.class, () -> self.encoder().encode(context.eval("js", "[2,'bad']"), new Host()));
            assertThrows(IllegalArgumentException.class, () -> self.encoder().encode(context.eval("js", "[1,2,3]"), new Host()));
        }
    }

    @Test void genericNominalMetadataKeepsTheOriginalTypedHostCodec() {
        var codec = field(box(TypeInfo.of(Box.class).withParams(TypeInfo.of(String.class))));
        assertEquals(new JsonPrimitive("native"), codec.encoder().encode(Value.asValue(new Box<>("native")), new Host()));
        assertThrows(IllegalArgumentException.class, () -> codec.encoder().encode(Value.asValue("native"), new Host()));
    }

    private static RecipeComponent<Box<String>> box(TypeInfo typeInfo) {
        Codec<Box<String>> codec = Codec.STRING.xmap(Box::new, Box::value);
        return RecipeComponentType.<Box<String>>unit(ResourceLocation.parse("test:original_box"),
                type -> new SimpleRecipeComponent<>(type, codec, typeInfo)).instance();
    }

    private static RecipeFieldValueCodec field(RecipeComponent<?> component) {
        var storage = new RecipeSchemaStorage(registries);
        RecipeKey<?> key = component.otherKey("value");
        storage.registerSchema(ResourceLocation.parse("test:original_input"), new RecipeSchema(key));
        return ProgrammaticRecipeSchemas.compile(storage).get("test:original_input").fields().get("value").valueCodec();
    }

    private static ApiTypeRef nominal(Class<?> raw, ApiTypeRef... arguments) {
        return ApiTypeRef.symbol(new ApiSymbolId("java", raw.getName()), List.of(arguments));
    }

    private static final class Host implements RecipeSchemaHost {
        @Override public Object registryAccess() { return registries.access(); }
        @Override public RecipeBuilder builder(String type, String prefix) { throw new AssertionError("Field conversion must not create a builder"); }
        @Override public Object custom(JsonObject value) { throw new AssertionError("Field conversion must not publish a recipe"); }
        @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) { throw new AssertionError("No alternate native converter is expected"); }
        @Override public JsonElement toJson(Value value) {
            if (value.isNumber() && value.fitsInInt()) return new JsonPrimitive(value.asInt());
            if (value.isString()) return new JsonPrimitive(value.asString());
            throw new AssertionError("Unexpected raw field representation");
        }
    }
}
