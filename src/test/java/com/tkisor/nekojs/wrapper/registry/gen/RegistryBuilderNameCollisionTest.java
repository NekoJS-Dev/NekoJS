package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.probe.backend.python.RegistryBuilderPyRenderer;
import com.tkisor.nekojs.probe.backend.typescript.RegistryBuilderTsRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegistryBuilderNameCollisionTest {
    public static class First {
        public static class FoodBuilder extends RegistryObjectBuilder<Object> {
            public FoodBuilder(Identifier id) { super(id); }
            public int getFirst() { return 1; }
            @Override public Object build() { return new Object(); }
        }
    }

    public static class Second {
        public static class FoodBuilder extends RegistryObjectBuilder<Object> {
            public FoodBuilder(Identifier id) { super(id); }
            public int getSecond() { return 2; }
            @Override public Object build() { return new Object(); }
        }
    }

    @Test
    void addonSimpleNamesCannotMergeWithEachOtherOrTheNestedFoodBuilder() {
        var collector = new RegistryTypesPoint.RegistryTypesCollector();
        collector.registerType(Registries.ITEM, "first", First.FoodBuilder.class, First.FoodBuilder::new);
        collector.registerType(Registries.ITEM, "second", Second.FoodBuilder.class, Second.FoodBuilder::new);
        collector.registerType(Registries.ITEM, "basic", ItemBuilder.class, ItemBuilder::new);
        collector.registerType(Registries.ITEM, "alias", ItemBuilder.class, ItemBuilder::new);
        var types = new RegistryTypesPoint.RegistryTypes(collector.byRegistry, collector.defaults, collector.builderClasses);
        var entries = RegistryBuilderSurfaces.derive(types);
        var names = entries.stream().filter(entry -> entry.typeName().equals("first") || entry.typeName().equals("second"))
                .map(entry -> entry.builderName()).toList();
        assertEquals(2, names.stream().distinct().count());
        assertFalse(names.contains("FoodBuilder"));
        String ts = RegistryBuilderTsRenderer.render(entries);
        String py = RegistryBuilderPyRenderer.render(entries);
        for (String name : names) {
            assertTrue(ts.contains("interface " + name + " {"));
            assertTrue(py.contains("class " + name + "(Protocol):"));
        }
        assertEquals(1, ts.split("interface ItemBuilder ", -1).length - 1);
        assertEquals(1, py.split("class ItemBuilder\\(Protocol\\):", -1).length - 1);
        assertEquals(1, ts.split("interface FoodBuilder ", -1).length - 1);
        assertEquals(ts, RegistryBuilderTsRenderer.render(RegistryBuilderSurfaces.derive(types)));
    }
}
