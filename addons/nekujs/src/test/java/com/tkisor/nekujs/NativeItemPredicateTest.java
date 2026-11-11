package com.tkisor.nekujs;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekujs.bridge.LegacyItemPredicateAdapter;
import dev.latvian.mods.kubejs.item.ItemPredicate;
import graal.graalvm.polyglot.Context;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeItemPredicateTest {
    @BeforeAll
    static void bootstrap() {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exposesOriginalItemPredicateDescriptorsWithoutTheIngredientMixinBranch() throws Exception {
        assertTrue(ItemPredicate.class.isInterface());
        assertTrue(Predicate.class.isAssignableFrom(ItemPredicate.class));
        var stream = ItemPredicate.class.getMethod("kjs$getItemStream");
        assertEquals(Stream.class, stream.getReturnType());
        assertEquals("()Ljava/util/stream/Stream;", MethodType.methodType(stream.getReturnType(), stream.getParameterTypes()).descriptorString());
        var test = ItemPredicate.class.getMethod("test", ItemStack.class);
        assertEquals("(Lnet/minecraft/world/item/ItemStack;)Z", MethodType.methodType(test.getReturnType(), test.getParameterTypes()).descriptorString());
        assertEquals(ItemPredicate.class, ItemPredicate.TYPE_INFO.asClass());
        assertFalse(Ingredient.class.isAssignableFrom(ItemPredicate.ALL.getClass()));
    }

    @Test
    void nativeIngredientsSelectAndEnumerateTheSameItemTypes() {
        var adapter = new LegacyItemPredicateAdapter();
        try (Context context = newContext(adapter)) {
            var nativeIngredient = Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT);
            ItemPredicate predicate = adapter.apply(context.asValue(nativeIngredient));
            assertTrue(predicate.test(new ItemStack(Items.IRON_INGOT)));
            assertTrue(predicate.test(new ItemStack(Items.GOLD_INGOT)));
            assertFalse(predicate.test(new ItemStack(Items.DIAMOND)));
            assertEquals(java.util.Set.of(Items.IRON_INGOT, Items.GOLD_INGOT), predicate.kjs$getItemStream().collect(java.util.stream.Collectors.toSet()));
            assertTrue(adapter.test(context.asValue(predicate)));
            assertSame(predicate, adapter.apply(context.asValue(predicate)));
            assertTrue(adapter.test(null));
            assertTrue(adapter.test(context.eval("js", "null")));
            assertSame(ItemPredicate.NONE, adapter.apply(null));
            assertSame(ItemPredicate.NONE, adapter.apply(context.eval("js", "null")));
            assertSame(ItemPredicate.NONE, adapter.apply(context.asValue("-")));
            assertSame(ItemPredicate.NONE, adapter.apply(context.asValue("  ")));
            assertSame(ItemPredicate.ALL, adapter.apply(context.asValue("*")));
            assertEquals(0, ItemPredicate.NONE.kjs$getItemStream().count());
            assertTrue(ItemPredicate.ALL.kjs$getItemStream().anyMatch(item -> item == Items.DIAMOND));
        }
    }

    @Test
    void managedGraalObjectsAndCallablesUseNativeConversionAndRetainCallbackFailures() {
        var adapter = new LegacyItemPredicateAdapter();
        try (Context context = newContext(adapter)) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            context.enter();
            try {
                ItemPredicate declarative = context.eval("js", "({item: 'minecraft:iron_ingot'})").as(ItemPredicate.class);
                assertTrue(declarative.test(new ItemStack(Items.IRON_INGOT)));
                assertFalse(declarative.test(new ItemStack(Items.GOLD_INGOT)));
                context.getBindings("js").putMember("selected", Items.GOLD_INGOT);
                ItemPredicate callable = context.eval("js", "stack => stack.is(selected)").as(ItemPredicate.class);
                assertTrue(callable.test(new ItemStack(Items.GOLD_INGOT)));
                assertFalse(callable.test(new ItemStack(Items.IRON_INGOT)));
                assertEquals(List.of(Items.GOLD_INGOT), callable.kjs$getItemStream().toList());
                ItemPredicate throwing = context.eval("js", "stack => { throw new Error('predicate failure'); }").as(ItemPredicate.class);
                assertThrows(graal.graalvm.polyglot.PolyglotException.class, () -> throwing.test(new ItemStack(Items.DIAMOND)));
            } finally {
                context.leave();
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    @Test
    void delegatedInputShapesRetainIngredientIdentityInsideContainers() {
        var shapes = new LegacyItemPredicateAdapter().inputShapes();
        assertInstanceOf(AdapterInputShape.SelfValue.class, shapes.getFirst());
        var delegated = shapes.subList(1, shapes.size());
        assertTrue(delegated.contains(AdapterInputShape.host(Ingredient.class)));
        assertTrue(delegated.contains(AdapterInputShape.arrayOf(AdapterInputShape.host(Ingredient.class))));
        var object = delegated.stream().filter(AdapterInputShape.ObjectValue.class::isInstance)
                .map(AdapterInputShape.ObjectValue.class::cast).findFirst().orElseThrow();
        for (String name : List.of("any", "all")) {
            var slot = object.slots().stream().filter(entry -> entry.name().equals(name)).findFirst().orElseThrow();
            assertEquals(AdapterInputShape.arrayOf(AdapterInputShape.host(Ingredient.class)), slot.shape());
        }
        var not = object.slots().stream().filter(slot -> slot.name().equals("not")).findFirst().orElseThrow();
        assertEquals(AdapterInputShape.host(Ingredient.class), not.shape());
        delegated.forEach(NativeItemPredicateTest::assertNoPredicateSelfReference);
    }

    private static void assertNoPredicateSelfReference(AdapterInputShape shape) {
        assertFalse(shape instanceof AdapterInputShape.SelfValue, "Delegated shape must not refer to ItemPredicate");
        switch (shape) {
            case AdapterInputShape.ArrayOfValue array -> assertNoPredicateSelfReference(array.element());
            case AdapterInputShape.ObjectValue object -> object.slots().forEach(slot -> assertNoPredicateSelfReference(slot.shape()));
            case AdapterInputShape.UnionValue union -> union.members().forEach(NativeItemPredicateTest::assertNoPredicateSelfReference);
            case AdapterInputShape.TemplateValue template -> assertNoPredicateSelfReference(template.hole());
            default -> { }
        }
    }

    private static Context newContext(LegacyItemPredicateAdapter adapter) {
        return Context.newBuilder("js").allowHostAccess(new NekoSharedHostAccess(List.of(adapter)).get())
                .option("engine.WarnInterpreterOnly", "false").build();
    }
}
