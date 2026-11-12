// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
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
 * RecipeFilterAdapter smoke test：纯逻辑，无 MC 对象依赖；只断言返回的组合子类型。
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

    @Test
    void nullIsRejectedButEmptyObjectMatchesEverything() {
        // null：test()=false，apply 不应被路由到；直接调用保持 null（历史行为）
        assertNull(adapter.apply(null));

        try (Context context = Context.newBuilder().build()) {
            // 空对象 = 无约束 = 匹配全部（And 空列表恒真），而非 apply 返回 null 导致调用方 NPE
            Value empty = context.asValue(ProxyObject.fromMap(Map.<String, Object>of()));
            assertTrue(adapter.test(empty));
            RecipeFilter filter = adapter.apply(empty);
            assertNotNull(filter, "empty object must yield a match-all filter, not null");
            assertInstanceOf(RecipeFilter.And.class, filter);
        }
    }
}
//?}
