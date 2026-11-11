package com.tkisor.nekujs;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.wrap.DirectTypeWrapperFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeTypeWrapperValidatorTest {
    @Test
    void exposesExactOriginalValidatorAndKeyBindJsRegistryDescriptors() throws Exception {
        // A reflective lookup makes missing production ABI a runtime RED instead of a test compile failure.
        Class<?> validator = Class.forName("dev.latvian.mods.rhino.util.wrap.TypeWrapperValidator");
        assertTrue(validator.isInterface());
        assertTrue(validator.isAnnotationPresent(FunctionalInterface.class));
        var abstractMethods = Arrays.stream(validator.getMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers())).toList();
        assertEquals(1, abstractMethods.size());
        var isValid = validator.getMethod("isValid", Object.class, TypeInfo.class);
        assertEquals(boolean.class, isValid.getReturnType());
        assertEquals(isValid, abstractMethods.getFirst());
        var always = validator.getField("ALWAYS_VALID");
        assertEquals(validator, always.getType());
        assertTrue(Modifier.isStatic(always.getModifiers()));
        assertTrue(Modifier.isFinal(always.getModifiers()));
        Object accepts = always.get(null);
        assertEquals(true, isValid.invoke(accepts, null, TypeInfo.of(String.class)));
        assertEquals(true, isValid.invoke(accepts, "value", TypeInfo.of(String.class)));
        assertEquals(boolean.class, TypeWrapperRegistry.class.getMethod("hasTypeWrapper", Class.class).getReturnType());
        assertEquals(void.class, TypeWrapperRegistry.class.getMethod("register", Class.class, validator,
                TypeWrapperRegistry.ContextFromFunction.class).getReturnType());
        assertEquals(void.class, TypeWrapperRegistry.class.getMethod("register", Class.class, validator,
                DirectTypeWrapperFactory.class).getReturnType());
        assertEquals(void.class, TypeWrapperRegistry.class.getMethod("register", Class.class,
                TypeWrapperRegistry.ContextFromFunction.class).getReturnType());
        assertEquals(void.class, TypeWrapperRegistry.class.getMethod("register", Class.class,
                DirectTypeWrapperFactory.class).getReturnType());
        assertEquals(Map.class, TypeWrapperRegistry.class.getMethod("definitions").getReturnType());
        var registry = new TypeWrapperRegistry(ScriptType.SERVER);
        var hasWrapper = TypeWrapperRegistry.class.getMethod("hasTypeWrapper", Class.class);
        assertEquals(false, hasWrapper.invoke(registry, String.class));
        registry.register(String.class, (dev.latvian.mods.rhino.Context context, Object value) -> value.toString());
        assertEquals(true, hasWrapper.invoke(registry, String.class));
        assertEquals(false, hasWrapper.invoke(new TypeWrapperRegistry(ScriptType.CLIENT), String.class));
    }
}
