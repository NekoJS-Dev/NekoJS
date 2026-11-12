//? if neoforge {
package com.tkisor.nekojs.js.type_adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.testfixture.NativeLoadingServicesTestFixture;
import com.tkisor.nekojs.testfixture.RecipeFilterPlatformFixture;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import graal.graalvm.polyglot.Context;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.tkisor.nekojs.api.data.ValueConversionException;

@Tag("native-recipe-probe")
class RecipeFilterItemMatchTest {
    private static RegistryAccess registries;
    private static RecipeFilterPlatformFixture platformScope;

    @BeforeAll static void bootstrapRegistries() throws ReflectiveOperationException {
        NativeLoadingServicesTestFixture.initialize();
        boolean metadata = VanillaRegistryProbe.registryMetadataAvailable();
        String failure = "Native recipe matching needs registry metadata: " + VanillaRegistryProbe.metadataFailure();
        if (NativeLoadingServicesTestFixture.requiresIsolatedRegistryMetadata()) assertTrue(metadata, failure);
        else org.junit.jupiter.api.Assumptions.assumeTrue(metadata, failure);
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        platformScope = RecipeFilterPlatformFixture.open();
    }

    @AfterAll static void restorePlatform() throws ReflectiveOperationException {
        if (platformScope != null) platformScope.close();
    }

    @Test
    void hostIngredientInputMatchesRealDecodedRecipeIngredients() {
        var stone = recipe("stone-input", Ingredient.of(Items.STONE), new ItemStack(Items.DIAMOND));
        var dirt = recipe("dirt-input", Ingredient.of(Items.DIRT), new ItemStack(Items.DIAMOND));
        RecipeFilter filter;
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("nativeIngredient", Ingredient.of(Items.STONE));
            filter = new RecipeFilterAdapter().apply(context.eval("js", "({input:nativeIngredient})"));
        }
        assertTrue(filter.test(stone, registries));
        assertFalse(filter.test(dirt, registries));
    }

    @Test
    void componentBearingStructuredOutputDoesNotBroadenToEveryStackOfTheSameItem() {
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Selected output"));
        ItemStack other = new ItemStack(Items.DIAMOND);
        other.set(DataComponents.CUSTOM_NAME, Component.literal("Other output"));
        var selected = recipe("selected-name", Ingredient.of(Items.STONE), named);
        var rejected = recipe("other-name", Ingredient.of(Items.STONE), other);
        RecipeFilter filter;
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            // Use this node's native component representation rather than assuming a cross-version text JSON shape.
            var patchJson = DataComponentPatch.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries),
                    named.getComponentsPatch()).getOrThrow();
            context.getBindings("js").putMember("nativeComponents", context.eval("js", "(" + patchJson + ")"));
            ItemStack converted = new ItemStackAdapter().apply(context.eval("js",
                    "({id:'minecraft:diamond',components:nativeComponents})"));
            assertEquals(named.get(DataComponents.CUSTOM_NAME), converted.get(DataComponents.CUSTOM_NAME),
                    "the structured selector must round-trip the actual native component codec");
            filter = new RecipeFilterAdapter().apply(context.eval("js",
                    "({output:{id:'minecraft:diamond',components:nativeComponents}})"));
        }
        assertTrue(filter.test(selected, registries));
        assertFalse(filter.test(rejected, registries));
    }

    @Test
    void defaultOutputMatchesOnlyTheProvidedPatchWhileExactRequiresAllComponents() {
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Selected output"));
        ItemStack extra = named.copy();
        extra.set(DataComponents.RARITY, Rarity.EPIC);
        var plainRecipe = recipe("plain-output", Ingredient.of(Items.STONE), named);
        var extraRecipe = recipe("extra-output", Ingredient.of(Items.STONE), extra);
        RecipeFilter subset;
        RecipeFilter exact;
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("stack", named);
            subset = new RecipeFilterAdapter().apply(context.eval("js", "({output:stack})"));
            exact = new RecipeFilterAdapter().apply(context.eval("js", "({output:{match:stack,exact:true}})"));
        }
        assertTrue(subset.test(plainRecipe, registries));
        assertTrue(subset.test(extraRecipe, registries), "default comparison ignores unspecified components");
        assertTrue(exact.test(plainRecipe, registries));
        assertFalse(exact.test(extraRecipe, registries), "exact comparison checks complete native components");
    }

    @Test
    void hostStacksAreCopiedAndTheirCountsDoNotConstrainOutputMatching() {
        ItemStack stack = new ItemStack(Items.DIAMOND, 9);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Selected output"));
        var selected = recipe("count-ignored", Ingredient.of(Items.STONE), stack.copyWithCount(1));
        RecipeFilter filter;
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("stack", stack);
            filter = new RecipeFilterAdapter().apply(context.eval("js", "({output:{match:stack,exact:true}})"));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Mutated after conversion"));
        }
        assertTrue(filter.test(selected, registries), "native selector state is captured before caller mutation");
    }

    @Test
    void inputStacksAndArraysMatchOnlyRecipesWithAnAcceptedIngredientWitness() {
        var stone = recipe("stone-witness", Ingredient.of(Items.STONE), new ItemStack(Items.DIAMOND));
        var dirt = recipe("dirt-witness", Ingredient.of(Items.DIRT), new ItemStack(Items.DIAMOND));
        RecipeFilter filter;
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("stack", new ItemStack(Items.STONE));
            filter = new RecipeFilterAdapter().apply(context.eval("js", "({input:[stack,{item:'minecraft:cobblestone'}]})"));
        }
        assertTrue(filter.test(stone, registries));
        assertFalse(filter.test(dirt, registries));
    }

    @Test
    void outputIngredientAndStructuredItemAliasesUseExistingNativeConversions() {
        var diamond = recipe("diamond-alias", Ingredient.of(Items.STONE), new ItemStack(Items.DIAMOND));
        var emerald = recipe("emerald-alias", Ingredient.of(Items.STONE), new ItemStack(Items.EMERALD));
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("ingredient", Ingredient.of(Items.DIAMOND));
            for (String expression : java.util.List.of("({output:ingredient})", "({output:{item:'minecraft:diamond',count:3}})",
                    "({output:[{id:'minecraft:diamond'},'minecraft:stone']})")) {
                RecipeFilter filter = new RecipeFilterAdapter().apply(context.eval("js", expression));
                assertTrue(filter.test(diamond, registries), expression);
                assertFalse(filter.test(emerald, registries), expression);
            }
        }
    }

    @Test
    void invalidItemMatchShapesAreRejectedRatherThanMatchingEverything() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            for (String expression : java.util.List.of("({output:{id:'minecraft:diamond',unknown:true}})",
                    "({input:{item:'minecraft:stone',typo:true}})", "({output:{match:'minecraft:diamond',exact:'yes'}})",
                    "({input:{match:'minecraft:stone',componentType:'fluid'}})", "({output:{exact:true}})",
                    "({input:{any:[{item:'minecraft:stone',unknown:true}]}})", "({output:{}})", "({input:null})")) {
                assertThrows(ValueConversionException.class,
                        () -> new RecipeFilterAdapter().apply(context.eval("js", expression)), expression);
            }
        }
    }

    private static RecipeHolder<?> recipe(String path, Ingredient ingredient, ItemStack result) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        var ingredients = new JsonArray();
        ingredients.add(Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow());
        json.add("ingredients", ingredients);
        json.add("result", ItemStack.CODEC.encodeStart(ops, result).getOrThrow());
        Recipe<?> recipe = Recipe.CODEC.parse(ops, json).getOrThrow();
//? if >=26 {
        return new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("nekojs", path)), recipe);
//?} else {
        /*return new RecipeHolder<>(Identifier.fromNamespaceAndPath("nekojs", path), recipe);
*///?}
    }
}
//?}
