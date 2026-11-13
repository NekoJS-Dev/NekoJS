// The fabric side has no implementation yet; remove this whole-file guard once the port lands.
//? if neoforge {
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.recipe.RecipeFilter;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyArray;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.tkisor.nekojs.testfixture.NativeLoadingServicesTestFixture;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.tkisor.nekojs.api.data.ValueConversionException;

/**
 * Smoke test for {@link RecipeFilterAdapter}: pure logic with no MC objects; it only asserts the
 * combinator type returned.
 */
class RecipeFilterAdapterTest {

    private final RecipeFilterAdapter adapter = new RecipeFilterAdapter();

    @Test
    void stringBecomesByIdFilter() {
        Value value = Value.asValue("minecraft:stick");

        assertTrue(adapter.test(value));
        assertInstanceOf(RecipeFilter.ById.class, adapter.apply(value));
    }

    @Test
    void arrayBecomesOrFilter() {
        try (Context context = Context.newBuilder().build()) {
            Value value = context.asValue(ProxyArray.fromArray("minecraft:stick", "minecraft:planks"));

            assertTrue(adapter.test(value));
            assertInstanceOf(RecipeFilter.Or.class, adapter.apply(value));
        }
    }

    @Test
    void singleObjectKeyReturnsThatFilter() {
        try (Context context = Context.newBuilder().build()) {
            Value value = context.asValue(ProxyObject.fromMap(Map.<String, Object>of("mod", "minecraft")));

            assertTrue(adapter.test(value));
            assertInstanceOf(RecipeFilter.ByMod.class, adapter.apply(value));
        }
    }

    @Test
    void multipleObjectKeysCombineWithAnd() {
        try (Context context = Context.newBuilder().build()) {
            Value value = context.asValue(ProxyObject.fromMap(
                    Map.<String, Object>of("mod", "minecraft", "output", "stick")));

            assertInstanceOf(RecipeFilter.And.class, adapter.apply(value));
        }
    }

    @Test
    void notKeyWrapsSubFilter() {
        try (Context context = Context.newBuilder().build()) {
            Value value = context.asValue(ProxyObject.fromMap(Map.<String, Object>of("not", "minecraft:stick")));

            assertInstanceOf(RecipeFilter.Not.class, adapter.apply(value));
        }
    }

    @Test
    void idPrefixKeyBecomesIdStartsWithFilter() {
        try (Context context = Context.newBuilder().build()) {
            Value value = context.asValue(ProxyObject.fromMap(Map.<String, Object>of("idStartsWith", "minecraft:chest")));

            assertInstanceOf(RecipeFilter.ByIdStartsWith.class, adapter.apply(value));
        }
    }

    @Test
    void andArrayCreatesConjunctionInsteadOfGenericArrayDisjunction() {
        try (Context context = Context.newBuilder().build()) {
            ProxyArray children = ProxyArray.fromArray(
                    ProxyObject.fromMap(Map.of("mod", "minecraft")),
                    ProxyObject.fromMap(Map.of("idContains", "chest")));
            RecipeFilter.And conjunction = assertInstanceOf(RecipeFilter.And.class,
                    adapter.apply(context.asValue(ProxyObject.fromMap(Map.of("and", children)))));
            assertEquals(2, conjunction.filters().size());
            assertInstanceOf(RecipeFilter.ByMod.class, conjunction.filters().get(0));
            assertInstanceOf(RecipeFilter.ByIdContains.class, conjunction.filters().get(1));
            assertInstanceOf(RecipeFilter.Or.class, adapter.apply(context.asValue(children)),
                    "A top-level array must remain OR");
            assertInstanceOf(RecipeFilter.Or.class,
                    adapter.apply(context.asValue(ProxyObject.fromMap(Map.of("or", children)))),
                    "An explicit OR array must remain OR");
        }
    }

    @Test
    void emptyGroupArraysRetainTheirDistinctBooleanIdentities() {
        try (Context context = Context.newBuilder().build()) {
            RecipeFilter.And conjunction = assertInstanceOf(RecipeFilter.And.class,
                    adapter.apply(context.asValue(ProxyObject.fromMap(Map.of("and", ProxyArray.fromArray())))));
            RecipeFilter.Or disjunction = assertInstanceOf(RecipeFilter.Or.class,
                    adapter.apply(context.asValue(ProxyObject.fromMap(Map.of("or", ProxyArray.fromArray())))));
            assertTrue(conjunction.filters().isEmpty());
            assertTrue(disjunction.filters().isEmpty());
            assertTrue(conjunction.test(null, null));
            assertFalse(disjunction.test(null, null));
        }
    }

    @Test
    void explicitGroupArraysStillRejectUnknownChildKeysAndIgnoreNullChildren() {
        try (Context context = Context.newBuilder().build()) {
            for (String group : java.util.List.of("and", "or")) {
                Value invalid = context.asValue(ProxyObject.fromMap(Map.of(group,
                        ProxyArray.fromArray(ProxyObject.fromMap(Map.of("imput", "minecraft:stone"))))));
                assertThrows(ValueConversionException.class, () -> adapter.apply(invalid));
                RecipeFilter filter = adapter.apply(context.asValue(ProxyObject.fromMap(Map.of(group,
                        ProxyArray.fromArray(null, ProxyObject.fromMap(Map.of("mod", "minecraft")))))));
                if (filter instanceof RecipeFilter.And and) assertEquals(1, and.filters().size());
                else assertEquals(1, assertInstanceOf(RecipeFilter.Or.class, filter).filters().size());
            }
        }
    }

    /** Uses the established isolated node bootstrap policy, without inventing a modern loader. */
    @Nested
    @Tag("native-recipe-probe")
    class NativePredicateSelection {
        @BeforeAll
        static void bootstrapRegistries() {
            NativeLoadingServicesTestFixture.initialize();
            boolean metadata = VanillaRegistryProbe.registryMetadataAvailable();
            String failure = "Native recipe-holder metadata bootstrap failed: " + VanillaRegistryProbe.metadataFailure();
            if (NativeLoadingServicesTestFixture.requiresIsolatedRegistryMetadata()) assertTrue(metadata, failure);
            else org.junit.jupiter.api.Assumptions.assumeTrue(metadata, failure);
        }

        @Test
        void actualParsedGroupsSelectRecipesWithoutBroadeningConjunctions() {
            try (Context context = Context.newBuilder("js").build()) {
                RecipeFilter conjunction = adapter.apply(context.eval("js",
                        "({and:[{mod:'minecraft'},{idContains:'chest'}]})"));
                assertTrue(conjunction.test(holder("minecraft:chest"), null));
                assertFalse(conjunction.test(holder("minecraft:stone"), null), "A mod-only match must not be selected");
                assertFalse(conjunction.test(holder("create:chest"), null), "An id-only match must not be selected");
                assertFalse(conjunction.test(holder("create:stone"), null));
                for (String expression : java.util.List.of(
                        "[{mod:'minecraft'},{idContains:'chest'}]",
                        "({or:[{mod:'minecraft'},{idContains:'chest'}]})")) {
                    RecipeFilter disjunction = adapter.apply(context.eval("js", expression));
                    assertTrue(disjunction.test(holder("minecraft:stone"), null), expression);
                    assertTrue(disjunction.test(holder("create:chest"), null), expression);
                    assertFalse(disjunction.test(holder("create:stone"), null), expression);
                }
                for (String expression : java.util.List.of(
                        "({mod:'minecraft',idContains:'chest'})",
                        "({and:{mod:'minecraft',idContains:'chest'}})",
                        "({and:[{mod:'minecraft'},{not:{idContains:'stone'}}]})")) {
                    RecipeFilter filter = adapter.apply(context.eval("js", expression));
                    assertTrue(filter.test(holder("minecraft:chest"), null), expression);
                    assertFalse(filter.test(holder("minecraft:stone"), null), expression);
                    assertFalse(filter.test(holder("create:chest"), null), expression);
                }
                assertTrue(adapter.apply(context.eval("js", "({and:[]})")).test(holder("minecraft:chest"), null));
                assertFalse(adapter.apply(context.eval("js", "({or:[]})")).test(holder("minecraft:chest"), null));
                assertFalse(adapter.apply(context.eval("js", "({not:{and:[]}})")).test(holder("minecraft:chest"), null));
                assertTrue(adapter.apply(context.eval("js", "({not:{or:[]}})")).test(holder("minecraft:chest"), null));
            }
        }

        private RecipeHolder<?> holder(String id) {
//? if >=26 {
            return new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.RECIPE, Identifier.parse(id)), null);
//?} else {
            /*return new RecipeHolder<>(Identifier.parse(id), null);
*///?}
        }
    }

    @Nested
    @Tag("native-recipe-probe")
    class EventCallbackSelection {
        @BeforeAll
        static void requireNativeMetadata() {
            NativeLoadingServicesTestFixture.initialize();
            boolean metadata = VanillaRegistryProbe.registryMetadataAvailable();
            if (NativeLoadingServicesTestFixture.requiresIsolatedRegistryMetadata()) assertTrue(metadata);
            else org.junit.jupiter.api.Assumptions.assumeTrue(metadata);
        }

        @Test
        void callbackFailuresPropagateThroughTheActualEventRatherThanBecomingNonMatches() {
            var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                    net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            var recipe = com.google.gson.JsonParser.parseString("""
                    {"type":"minecraft:crafting_shaped","pattern":["A"],
                     "key":{"A":{"item":"minecraft:stone"}},"result":{"id":"minecraft:stone"}}
                    """);
            var event = new com.tkisor.nekojs.wrapper.event.server.RecipeEventJS(
                    Map.of(Identifier.parse("nekojs:callback_failure"), recipe), registries);
            try (var context = Context.newBuilder("js").allowExperimentalOptions(true).allowHostAccess(
                    new com.tkisor.nekojs.core.NekoSharedHostAccess(java.util.List.of(new RecipeFilterAdapter())).get())
                    .option("js.nashorn-compat", "true").option("js.ecmascript-version", "latest").build()) {
                var throwing = new RecipeFilterAdapter().apply(context.eval("js",
                        "match => { throw new Error('visible callback failure'); }"));
                var failure = assertThrows(graal.graalvm.polyglot.PolyglotException.class, () -> event.count(throwing));
                assertTrue(failure.getMessage().contains("visible callback failure"));
                assertEquals(0, event.count(new RecipeFilterAdapter().apply(context.eval("js", "match => false"))));
                assertEquals(1, event.count(new RecipeFilterAdapter().apply(context.asValue("*"))));
                context.getBindings("js").putMember("event", event);
                assertEquals(0, context.eval("js", "event.count(match => false)").asInt());
                assertEquals(1, context.eval("js", "event.count(match => true)").asInt());
                assertEquals(1, context.eval("js", """
                        event.count(match => match.recipe === match.getRecipe()
                            && match.recipe.mod === 'nekojs'
                            && match.recipe.type === 'minecraft:crafting_shaped'
                            && match.recipe.group === ''
                            && match.recipe.getOrCreateId() === 'nekojs:callback_failure')
                        """).asInt(), "The real host boundary must use the native context adapter, not the two-argument SAM");
                var hostFailure = assertThrows(graal.graalvm.polyglot.PolyglotException.class, () -> context.eval("js",
                        "event.count(match => { throw Error('host callback failure'); })"));
                assertTrue(hostFailure.getMessage().contains("host callback failure"));
            }
        }
    }

    @Test
    void nullIsRejectedButEmptyObjectMatchesEverything() {
        // null: test() is false so apply is never routed here; calling it directly still returns null
        assertNull(adapter.apply(null));

        try (Context context = Context.newBuilder().build()) {
            // An empty object means no constraint, so it matches everything (an empty And list is always true);
            // returning null from apply would make the caller throw NPE
            Value empty = context.asValue(ProxyObject.fromMap(Map.<String, Object>of()));
            assertTrue(adapter.test(empty));
            RecipeFilter filter = adapter.apply(empty);
            assertNotNull(filter, "empty object must yield a match-all filter, not null");
            assertInstanceOf(RecipeFilter.And.class, filter);
        }
    }
}
//?}
