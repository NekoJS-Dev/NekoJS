package com.tkisor.nekujs;

import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.component.DataComponentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the original metadata traversal without initializing Mekanism's registry holders. */
@Tag("fixtures")
class FixtureMekanismMetadataTest {
    @Test
    void unchangedHolderSignaturesRetainWildcardAndConcretePayloadMetadata() throws Exception {
        Path jar = Path.of(System.getProperty("nekujs.fixtures.dir"), "Mekanism-1.21.1-10.7.19.85.jar");
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> owner = Class.forName("mekanism.common.registries.MekanismDataComponents", false, loader);
            Class<?> holder = Class.forName("mekanism.common.registration.MekanismDeferredHolder", false, loader);
            assertSame(loader, owner.getClassLoader());
            assertEquals(jar.toUri().toURL(), owner.getProtectionDomain().getCodeSource().getLocation());
            var descriptions = new LinkedHashMap<String, TypeInfo>();
            for (var field : owner.getDeclaredFields()) {
                if (field.getType() != holder || !Modifier.isPublic(field.getModifiers())
                        || !Modifier.isStatic(field.getModifiers())) continue;
                var nativeType = assertInstanceOf(ParameterizedType.class, field.getGenericType());
                var baseType = assertInstanceOf(ParameterizedType.class, nativeType.getActualTypeArguments()[0]);
                assertInstanceOf(WildcardType.class, baseType.getActualTypeArguments()[0]);
                TypeInfo metadata = TypeInfo.of(field.getGenericType());
                assertSame(holder, metadata.asClass());
                assertSame(DataComponentType.class, metadata.param(0).asClass());
                assertSame(Object.class, metadata.param(0).param(0).asClass());
                TypeInfo component = metadata.param(1);
                assertTrue(component.is(TypeInfo.of(DataComponentType.class)), field.getName());
                var componentType = assertInstanceOf(ParameterizedType.class, nativeType.getActualTypeArguments()[1]);
                assertSame(TypeInfo.of(componentType.getActualTypeArguments()[0]).asClass(), component.param(0).asClass(), field.getName());
                descriptions.put(field.getName(), component.param(0));
            }
            assertFalse(descriptions.isEmpty());
            assertEquals("mekanism.common.content.gear.ModuleContainer", descriptions.get("MODULE_CONTAINER").asClass().getName());
            assertSame(Boolean.class, descriptions.get("DEFAULT_MANUALLY_SELECTED").asClass());
            assertSame(Integer.class, descriptions.get("RADIUS").asClass());
        }
    }
}
