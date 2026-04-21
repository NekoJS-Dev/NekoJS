package test;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ZZZank
 */
public class NekoAnnotationTest {
    static {
        Thread.currentThread().setContextClassLoader(FMLLoader.getCurrent().getCurrentClassLoader());
    }

    @Test
    public void testNormalField() {
        try (var cx = createTestContext()) {
            var result = cx.eval("js", "obj.field");
            Assertions.assertEquals(1, result.asInt());
        }
    }

    @Test
    public void testHiddenField() {
        try (var cx = createTestContext()) {
            // Hidden field should not be accessible from JS
            var result = cx.eval("js", "typeof obj.hiddenField");
            Assertions.assertEquals("undefined", result.asString());
        }
    }

    @Test
    public void testRemappedByPrefixField() {
        try (var cx = createTestContext()) {
            // example$remappedField should be accessible as remappedField
            var result = cx.eval("js", "obj.remappedField");
            Assertions.assertEquals(3, result.asInt());

            // Original name should not be accessible
            var originalNameResult = cx.eval("js", "typeof obj['example$remappedField']");
            Assertions.assertEquals("undefined", originalNameResult.asString());
        }
    }

    @Test
    public void testCustomRemappedField() {
        try (var cx = createTestContext()) {
            // wow$customRemappedField should be accessible as customRemappedField
            var result = cx.eval("js", "obj.customRemappedField");
            Assertions.assertEquals(4, result.asInt());

            // Original name should not be accessible
            var originalNameResult = cx.eval("js", "typeof obj['wow$customRemappedField']");
            Assertions.assertEquals("undefined", originalNameResult.asString());
        }
    }

    @Test
    public void testNormalMethod() {
        try (var cx = createTestContext()) {
            var result = cx.eval("js", "obj.method()");
            Assertions.assertEquals("method()", result.asString());
        }
    }

    @Test
    public void testHiddenMethod() {
        try (var cx = createTestContext()) {
            // Hidden method should not be accessible from JS
            var result = cx.eval("js", "typeof obj.hiddenMethod");
            Assertions.assertEquals("undefined", result.asString());
        }
    }

    @Test
    public void testRemappedByPrefixMethod() {
        try (var cx = createTestContext()) {
            // example$remappedMethod should be accessible as remappedMethod
            var result = cx.eval("js", "obj.remappedMethod()");
            Assertions.assertEquals("remappedMethod()", result.asString());

            // Original name should not be accessible
            var originalNameResult = cx.eval("js", "typeof obj['example$remappedMethod']");
            Assertions.assertEquals("undefined", originalNameResult.asString());
        }
    }

    @Test
    public void testAllHidden() {
        try (var cx = createTestContext()) {
            var methodResult = cx.eval("js", "typeof allHidden.method");
            Assertions.assertEquals("undefined", methodResult.asString());

            var fieldResult = cx.eval("js", "typeof allHidden.field");
            Assertions.assertEquals("undefined", fieldResult.asString());
        }
    }

    private static Context createTestContext() {
        var cx = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .build();

        var bindings = cx.getBindings("js");
        bindings.putMember("obj", new TestObject());
        bindings.putMember("allHidden", new AllHiddenTestObject());

        return cx;
    }

    @RemapByPrefix("example$")
    @SuppressWarnings("unused")
    public static class TestObject {

        public int field = 1;
        @HideFromJS
        public int hiddenField = 2;
        public int example$remappedField = 3;
        @Remap("customRemappedField")
        public int wow$customRemappedField = 4;

        public String method() {
            return "method()";
        }

        @HideFromJS
        public String hiddenMethod() {
            throw new AssertionError("hidden method accessed");
        }

        public String example$remappedMethod() {
            return "remappedMethod()";
        }
    }

    @HideFromJS
    @SuppressWarnings("unused")
    public static class AllHiddenTestObject {
        public int field = 1;

        public String method() {
            return "method()";
        }
    }
}
