//? if neoforge {
package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.js.type_adapter.RecipeFilterAdapter;
import com.tkisor.nekojs.testfixture.NativeLoadingServicesTestFixture;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import graal.graalvm.polyglot.Context;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises recipe filter selection against native decoded recipes, not class-shape fixtures. */
@Tag("native-recipe-probe")
class RecipeFilterCompatibilityTest {
    @BeforeAll
    static void bootstrapNativeMetadata() {
        NativeLoadingServicesTestFixture.initialize();
        boolean metadata = VanillaRegistryProbe.registryMetadataAvailable();
        String failure = "Native filter metadata bootstrap failed: " + VanillaRegistryProbe.metadataFailure();
        if (NativeLoadingServicesTestFixture.requiresIsolatedRegistryMetadata()) assertTrue(metadata, failure);
        else org.junit.jupiter.api.Assumptions.assumeTrue(metadata, failure);
    }

    @Test
    void typeSelectsSerializerRatherThanTheBroaderRecipeType() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        RecipeHolder<?> holder = shaped(registries);
        try (Context context = productionContext()) {
            RecipeFilter filter = new RecipeFilterAdapter().apply(context.eval("js", "({type:'minecraft:crafting_shaped'})"));
            assertTrue(filter.test(holder, registries), "A shaped serializer must match its exact serializer id");
            assertTrue(new RecipeFilterAdapter().apply(context.eval("js", "({type:'crafting_shaped'})")).test(holder, registries));
            var shapelessJson = JsonParser.parseString("""
                    {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:stone"}],
                     "result":{"id":"minecraft:stone","count":1}}
                    """);
            Recipe<?> shapeless = Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), shapelessJson).getOrThrow();
            RecipeHolder<?> shapelessHolder = holder(shapeless);
            assertFalse(filter.test(shapelessHolder, registries), "A shaped serializer must not match shapeless crafting");
            assertTrue(new RecipeFilterAdapter().apply(context.eval("js", "({type:'crafting_shapeless'})")).test(shapelessHolder, registries));
            assertTrue(new RecipeFilterAdapter().apply(context.eval("js", "({recipeType:'crafting'})")).test(shapelessHolder, registries));
            assertFalse(new RecipeFilterAdapter().apply(context.eval("js", "({type:'minecraft:crafting'})")).test(holder, registries),
                    "The broad recipe type must not masquerade as a serializer id");
        }
    }

    @Test
    void callbackReceivesOneRecipeContextAndWildcardSelectsTheRecipe() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        RecipeHolder<?> holder = shaped(registries);
        try (Context context = productionContext()) {
            RecipeFilter filter = new RecipeFilterAdapter().apply(context.eval("js", """
                    match => match.recipe === match.getRecipe()
                        && match.recipe.getOrCreateId() === 'nekojs:filter_compat'
                        && match.recipe.getMod() === 'nekojs'
                        && match.recipe.getType() === 'minecraft:crafting_shaped'
                        && match.recipe.getGroup() === 'compat_group'
                    """));
            assertTrue(filter.test(holder, registries));
            assertFalse(new RecipeFilterAdapter().apply(context.eval("js", "match => false")).test(holder, registries));
            assertTrue(new RecipeFilterAdapter().apply(context.asValue("*")).test(holder, registries));
        }
    }

    @Test
    void explicitRecipeTypeAndIdSentinelsRemainDistinctFromSerializerMatching() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var holder = shaped(registries);
        try (Context context = productionContext()) {
            var adapter = new RecipeFilterAdapter();
            assertTrue(adapter.apply(context.eval("js", "({recipeType:'minecraft:crafting'})")).test(holder, registries));
            assertFalse(adapter.apply(context.eval("js", "({recipeType:'minecraft:crafting_shaped'})")).test(holder, registries));
            assertTrue(adapter.apply(context.asValue("*")).test(holder, registries));
            assertFalse(adapter.apply(context.asValue("-")).test(holder, registries));
            assertTrue(adapter.apply(context.eval("js", "({and:['*',{not:'-'}]})")).test(holder, registries));
            assertFalse(adapter.apply(context.eval("js", "({or:['-',{not:'*'}]})")).test(holder, registries));
        }
    }

    @Test
    void regexesUseNativeSemanticsWithoutLeakingLastIndexAcrossRepeatedMatches() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var holder = shaped(registries);
        try (Context context = productionContext()) {
            var adapter = new RecipeFilterAdapter();
            for (String source : java.util.List.of("/filter_/g", "/^nekojs:/y")) {
                var regex = context.eval("js", source);
                regex.putMember("lastIndex", 7);
                RecipeFilter filter = adapter.apply(regex);
                assertTrue(filter.test(holder, registries), source);
                assertTrue(filter.test(holder, registries), source);
                assertEquals(7, regex.getMember("lastIndex").asInt(), "Caller-owned RegExp state must be restored");
            }
            for (String source : java.util.List.of("({id:/FILTER_/i})", "({mod:/NEKO/i})", "({group:/GROUP/i})")) {
                assertTrue(adapter.apply(context.eval("js", source)).test(holder, registries), source);
            }
            assertTrue(adapter.apply(context.asValue("/FILTER_/i")).test(holder, registries));
            assertFalse(adapter.apply(context.asValue("/no_such_recipe/")).test(holder, registries));
            assertTrue(adapter.apply(context.asValue(java.util.regex.Pattern.compile("COMPAT", java.util.regex.Pattern.CASE_INSENSITIVE)))
                    .test(holder, registries));
        }
    }

    @Test
    void callbackFailuresAndNonBooleanResultsAreNotSilentlyConvertedToNoMatch() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var holder = shaped(registries);
        try (Context context = productionContext()) {
            var adapter = new RecipeFilterAdapter();
            RecipeFilter arity = adapter.apply(context.eval("js", "(function(match) { return arguments.length === 1; })"));
            assertTrue(arity.test(holder, registries));
            for (String source : java.util.List.of("match => 1", "match => 'yes'", "match => undefined")) {
                RecipeFilter filter = adapter.apply(context.eval("js", source));
                var failure = assertThrows(com.tkisor.nekojs.api.data.ValueConversionException.class,
                        () -> filter.test(holder, registries), source);
                assertTrue(failure.getMessage().contains("NEKO-4054"));
                assertNotNull(failure.getCause());
            }
            RecipeFilter throwing = adapter.apply(context.eval("js", "match => { throw new Error('filter callback failed'); }"));
            var failure = assertThrows(graal.graalvm.polyglot.PolyglotException.class,
                    () -> throwing.test(holder, registries));
            assertTrue(failure.getMessage().contains("filter callback failed"));
            var badRegex = assertThrows(com.tkisor.nekojs.api.data.ValueConversionException.class,
                    () -> adapter.apply(context.asValue("/[/")));
            assertNotNull(badRegex.getCause());
            assertThrows(com.tkisor.nekojs.api.data.ValueConversionException.class,
                    () -> adapter.apply(context.eval("js", "({test: value => true})")),
                    "An arbitrary object with test() is not a RegExp");
        }
    }

    private static Context productionContext() {
        return Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new com.tkisor.nekojs.core.NekoSharedHostAccess(java.util.List.of()).get())
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.foreign-object-prototype", "true")
                .build();
    }

    private static RecipeHolder<?> shaped(net.minecraft.core.HolderLookup.Provider registries) {
        var json = JsonParser.parseString("""
                {"type":"minecraft:crafting_shaped","group":"compat_group","category":"misc",
                 "pattern":["A"],"key":{"A":{"item":"minecraft:stone"}},
                 "result":{"id":"minecraft:stone","count":1}}
                """);
        Recipe<?> recipe = Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        return holder(recipe);
    }

    private static RecipeHolder<?> holder(Recipe<?> recipe) {
//? if >=26 {
        return new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("nekojs:filter_compat")), recipe);
//?} else {
        /*return new RecipeHolder<>(Identifier.parse("nekojs:filter_compat"), recipe);
*///?}
    }
}
//?}
