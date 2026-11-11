package com.tkisor.nekujs;

import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.EnumTypeInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeRhinoFacadeTest {
    private List<String> strings;
    private List<?> unknowns;
    private List<? extends Number> numbers;
    private List<? super Integer> integers;
    private List<? extends List<String>> nested;
    private enum Sample { FIRST, SECOND }

    @Test
    void metadataRetainsGenericArgumentsAndRealEnumValues() throws Exception {
        var type = TypeInfo.of(getClass().getDeclaredField("strings").getGenericType());
        assertSame(List.class, type.asClass());
        assertSame(String.class, type.param(0).asClass());
        assertTrue(type.is(TypeInfo.of(List.class)));
        var parameterized = TypeInfo.of(List.class).withParams(TypeInfo.of(Integer.class));
        assertSame(Integer.class, parameterized.param(0).asClass());
        var enumeration = assertInstanceOf(EnumTypeInfo.class, TypeInfo.of(Sample.class));
        assertArrayEquals(Sample.values(), enumeration.enumConstants());
        assertEquals("FIRST", enumeration.getName(Sample.FIRST));
        assertThrows(UnsupportedOperationException.class, () -> TypeInfo.of(new java.lang.reflect.Type() {}));
    }

    @Test
    void wildcardMetadataUsesTheLegacyBoundOrderAndUnknownSentinel() throws Exception {
        TypeInfo unknown = TypeInfo.of(getClass().getDeclaredField("unknowns").getGenericType()).param(0);
        assertSame(TypeInfo.NONE, unknown);
        assertSame(Object.class, unknown.asClass());
        assertNotSame(TypeInfo.of(Object.class), unknown);
        assertFalse(unknown.is(TypeInfo.of(Object.class)));
        assertFalse(TypeInfo.of(Object.class).is(unknown));
        assertTrue(unknown.is(TypeInfo.NONE));
        assertSame(unknown, unknown.param(0));
        assertSame(unknown, unknown.withParams(TypeInfo.of(String.class)));
        assertEquals("?", unknown.toString());
        assertSame(Number.class, TypeInfo.of(getClass().getDeclaredField("numbers").getGenericType()).param(0).asClass());
        assertSame(Integer.class, TypeInfo.of(getClass().getDeclaredField("integers").getGenericType()).param(0).asClass());
        TypeInfo parameter = TypeInfo.of(getClass().getDeclaredField("nested").getGenericType()).param(0);
        assertSame(List.class, parameter.asClass());
        assertSame(String.class, parameter.param(0).asClass());
    }

    @Test
    void delegatesGuestConversionToExistingGraalContextWithoutAnEvaluator() throws Exception {
        var facade = new dev.latvian.mods.rhino.Context();
        Object identity = new Object();
        assertSame(identity, facade.jsToJava((Wrapper) () -> identity, Object.class));
        try (Context guest = Context.newBuilder("js").allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false").build()) {
            assertEquals(42, facade.jsToJava(guest.eval("js", "42"), Integer.class));
            assertEquals("native", facade.jsToJava(guest.eval("js", "'native'"), String.class));
            assertNull(facade.jsToJava(null, Object.class));
        }
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.latvian.mods.rhino.ScriptRuntime"));
    }
}
