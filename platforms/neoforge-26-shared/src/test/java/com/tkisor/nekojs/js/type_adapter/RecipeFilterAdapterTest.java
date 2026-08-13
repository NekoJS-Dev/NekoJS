package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.recipe.RecipeFilter;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyArray;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
