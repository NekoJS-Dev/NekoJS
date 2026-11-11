package com.tkisor.nekojs.probe;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeHandlerMethodEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class NativeRecipeHandlerDeclarationTest {
    public static final class Payload {
        public final String name;
        public Payload(String name) { this.name = name; }
    }
    public static final class GenericBox<T> {
        private final T value;
        public GenericBox(T value) { this.value = value; }
        public T get() { return value; }
    }
    public static final class NativeBuilder {
        public NativeBuilder nativeOnly(int energy) { return this; }
    }
    public static final class SpecificBuilder {
        public SpecificBuilder specificOnly(String label) { return this; }
    }
    public static final class Handler {
        public NativeBuilder crafting_shaped(String result, List<String> pattern, Map<String, Payload> key, Optional<String> group) { return new NativeBuilder(); }
        public NativeBuilder crafting_shaped(String result, List<List<Payload>> ingredients, Optional<String> group) { return new NativeBuilder(); }
        public SpecificBuilder unrelated(Payload value) { return new SpecificBuilder(); }
        public List<NativeBuilder> history() { return List.of(new NativeBuilder()); }
        public Map<String, SpecificBuilder> indexed() { return Map.of("one", new SpecificBuilder()); }
        public GenericBox<Payload> boxed(GenericBox<Payload> value) { return value; }
    }

    record Fixture(TypeAliasRegistry aliases, AdapterAliasGenerator adapters) {}
    static Fixture fixture() {
        var aliases = new TypeAliasRegistry();
        var adapters = new AdapterAliasGenerator(aliases);
        adapters.prepare(List.of(
                new AdapterCatalogEntry(Payload.class, List.of(self(), string()), ConversionPrecedence.LOW, Optional.empty()),
                new AdapterCatalogEntry(GenericBox.class, List.of(self(), object(Slot.req("value", raw("T")))), ConversionPrecedence.LOW, Optional.empty())),
                Set.of(Payload.class.getName(), GenericBox.class.getName()));
        return new Fixture(aliases, adapters);
    }
    static RecipeNamespaceCatalogEntry namespace() {
        return RecipeNamespaceCatalogEntry.withHandlerMethods("nativefixture", Handler.class,
                List.of("crafting_shaped", "unrelated", "history", "indexed", "boxed"), true, List.of());
    }
    static String render(RecipeNamespaceCatalogEntry namespace, TypeAliasRegistry aliases) {
        return new RecipeEventDeclarationGenerator(aliases).generate(List.of(namespace), ScriptType.SERVER);
    }

    @Test void collectionRetainsEveryNativeOverloadAndItsOptionalTail() {
        var methods = namespace().handlerMethods().stream().filter(method -> method.methodName().equals("crafting_shaped")).toList();
        assertEquals(2, methods.size(), "Reflection must not replace overloads with the largest method");
        assertEquals(Set.of(2, 3), methods.stream().map(RecipeHandlerMethodEntry::minArgs).collect(java.util.stream.Collectors.toSet()));
        for (var method : methods) {
            assertTrue(method.params().getLast().optional());
            assertEquals("java.lang.String", method.params().getLast().genericType());
        }
        assertEquals(namespace().handlerMethods(), namespace().handlerMethods(), "Reflection order must be deterministic");
    }

    @Test void handlersUseSharedNativeParameterProjectionWithoutLosingNestedGenerics() throws Exception {
        var fixture = fixture();
        String output = render(namespace(), fixture.aliases());
        assertEquals(2, count(output, "crafting_shaped("), output);
        for (Method method : Handler.class.getMethods()) {
            if (!method.getName().equals("crafting_shaped")) continue;
            var types = method.getGenericParameterTypes();
            for (int index = 0; index < types.length - 1; index++) {
                String expected = FunctionalInterfaceAliasGenerator.renderJavaTypeInput(types[index], fixture.aliases(), new LinkedHashSet<>());
                assertTrue(output.contains(expected), "Native shared input projection is missing: " + expected + "\n" + output);
            }
        }
        assertTrue(output.contains("group?: string"), output);
        assertTrue(output.contains("$List<$NativeRecipeHandlerDeclarationTest$Payload>"), output);
        assertFalse(output.contains("$List<$NativeRecipeHandlerDeclarationTest$Payload_>"), "Host container identity must not use converted element inputs: " + output);
    }

    @Test void returnTypesComeFromNativeMethodsIncludingGenericResults() throws Exception {
        var fixture = fixture();
        var generator = new RecipeEventDeclarationGenerator(fixture.aliases());
        String output = generator.generate(List.of(namespace()), ScriptType.SERVER);
        for (String name : List.of("crafting_shaped", "unrelated", "history", "indexed", "boxed")) {
            Method method = java.util.Arrays.stream(Handler.class.getMethods()).filter(value -> value.getName().equals(name)).findFirst().orElseThrow();
            String expected = TypeScriptClassRenderer.renderTypeRef(TypeReflector.toRef(method.getGenericReturnType()), fixture.aliases(), false);
            assertTrue(output.contains("): " + expected + ";"), "Native return projection is missing: " + name + " -> " + expected + "\n" + output);
        }
        assertTrue(generator.referencedClasses(List.of(namespace())).contains(NativeBuilder.class.getName()));
        assertTrue(generator.referencedClasses(List.of(namespace())).contains(SpecificBuilder.class.getName()));
    }

    @Test void nativeOverloadsTakePrecedenceOverAWeakDiscoveredSchema() {
        var weak = new RecipeSchemaTypeEntry("nativefixture", "crafting_shaped", "nativefixture_crafting_shaped",
                List.of(new RecipeSchemaTypeEntry.SchemaField("ingredients", "JSON", true, true, "ingredients", string())),
                List.of(List.of("ingredients")), Map.of(), List.of(), null);
        String output = render(namespace().withSchemaTypes(List.of(weak)), fixture().aliases());
        assertEquals(2, count(output, "crafting_shaped("), output);
        assertTrue(output.contains("): $NativeRecipeHandlerDeclarationTest$NativeBuilder;"), output);
        assertFalse(output.contains("crafting_shaped(options:"), output);
    }

    @Test void oldManuallyAuthoredEntriesWithoutReturnMetadataKeepTheBaseBuilderFallback() {
        var manual = new RecipeHandlerMethodEntry("manual", List.of(new RecipeHandlerMethodEntry.HandlerParam("result", "string", null, null, false)), 1);
        var namespace = new RecipeNamespaceCatalogEntry("manualfixture", null, List.of("manual"), true, List.of(), List.of(manual));
        String output = render(namespace, new TypeAliasRegistry());
        assertTrue(output.contains("manual(result: string): $RecipeJsonBuilder;"), output);
    }


    public static final class GenericMethodHandler {
        public <T extends Number> T pick(T value) { return value; }
        public <T extends Number> List<T> numbers(List<T> value) { return value; }
        public <T extends Comparable<T>> T recursive(T value) { return value; }
        @SuppressWarnings("rawtypes") public List raw_list(List value) { return value; }
        @SuppressWarnings("rawtypes") public Map raw_map(Map value) { return value; }
    }
    public static class GenericParentHandler<T extends Number> {
        public T inherited(T value) { return value; }
        public List<T> inherited_list(List<T> value) { return value; }
    }
    public static final class InheritedGenericHandler extends GenericParentHandler<Integer> {}
    public static class UnboundedGenericParentHandler<T> {
        public T inherited_unbounded(T value) { return value; }
    }
    public static final class UnboundedInheritedHandler extends UnboundedGenericParentHandler<Integer> {}

    @Test void nativeGenericMethodsEraseUnboundVariablesToTheirRealNumericBounds() {
        var namespace = RecipeNamespaceCatalogEntry.withHandlerMethods("genericfixture", GenericMethodHandler.class, List.of(), true, List.of());
        var methods = namespace.handlerMethods().stream().collect(java.util.stream.Collectors.toMap(RecipeHandlerMethodEntry::methodName, value -> value));
        ApiTypeRef number = ApiTypeRef.primitive("int");
        assertEquals(number, methods.get("pick").params().getFirst().typeRef());
        assertEquals(number, methods.get("pick").returnType());
        assertEquals(nominal(List.class, number), methods.get("numbers").params().getFirst().typeRef());
        assertEquals(nominal(List.class, number), methods.get("numbers").returnType());
        assertNoUnboundVariables(namespace);
        String output = render(namespace, new TypeAliasRegistry());
        assertTrue(output.contains("pick(value: number): number;"), output);
        assertFalse(java.util.regex.Pattern.compile("(?<![A-Za-z0-9_$])T(?![A-Za-z0-9_$])").matcher(output).find(), output);
    }

    @Test void recursiveBoundsAndRawGenericUsesKeepFiniteDeclaredArity() {
        var namespace = RecipeNamespaceCatalogEntry.withHandlerMethods("genericfixture", GenericMethodHandler.class, List.of(), true, List.of());
        var methods = namespace.handlerMethods().stream().collect(java.util.stream.Collectors.toMap(RecipeHandlerMethodEntry::methodName, value -> value));
        ApiTypeRef any = ApiTypeRef.primitive("any");
        ApiTypeRef comparable = nominal(Comparable.class, any);
        assertEquals(comparable, methods.get("recursive").params().getFirst().typeRef());
        assertEquals(comparable, methods.get("recursive").returnType());
        assertEquals(nominal(List.class, any), methods.get("raw_list").params().getFirst().typeRef());
        assertEquals(nominal(List.class, any), methods.get("raw_list").returnType());
        assertEquals(nominal(Map.class, any, any), methods.get("raw_map").params().getFirst().typeRef());
        assertEquals(nominal(Map.class, any, any), methods.get("raw_map").returnType());
        assertNoUnboundVariables(namespace);
        String output = render(namespace, new TypeAliasRegistry());
        assertTrue(output.contains("$Comparable<any>"), output);
        assertTrue(output.contains("): $List<any>;"), output);
        assertTrue(output.contains("): $Map<any, any>;"), output);
    }

    @Test void inheritedGenericHandlerMethodsRetainTheirJvmBoundInsteadOfDanglingOwnerVariables() {
        var namespace = RecipeNamespaceCatalogEntry.withHandlerMethods("inheritedfixture", InheritedGenericHandler.class, List.of(), true, List.of());
        var methods = namespace.handlerMethods().stream().collect(java.util.stream.Collectors.toMap(RecipeHandlerMethodEntry::methodName, value -> value));
        ApiTypeRef number = ApiTypeRef.primitive("int");
        assertEquals(Set.of("inherited", "inherited_list"), methods.keySet());
        assertEquals(number, methods.get("inherited").params().getFirst().typeRef());
        assertEquals(number, methods.get("inherited").returnType());
        assertEquals(nominal(List.class, number), methods.get("inherited_list").params().getFirst().typeRef());
        assertEquals(nominal(List.class, number), methods.get("inherited_list").returnType());
        assertNoUnboundVariables(namespace);
        var unbounded = RecipeNamespaceCatalogEntry.withHandlerMethods("unboundedfixture", UnboundedInheritedHandler.class, List.of(), true, List.of());
        var inherited = unbounded.handlerMethods().getFirst();
        // The reflective Method belongs to the generic parent; the current runtime does not reify Integer from its subclass.
        assertEquals(ApiTypeRef.primitive("object"), inherited.params().getFirst().typeRef());
        assertEquals(ApiTypeRef.primitive("object"), inherited.returnType());
        assertNoUnboundVariables(unbounded);
        String output = render(namespace, new TypeAliasRegistry());
        assertTrue(output.contains("inherited(value: number): number;"), output);
        assertFalse(java.util.regex.Pattern.compile("(?<![A-Za-z0-9_$])T(?![A-Za-z0-9_$])").matcher(output).find(), output);
    }

    private static ApiTypeRef nominal(Class<?> type, ApiTypeRef... arguments) {
        return ApiTypeRef.symbol(new ApiSymbolId("java", type.getName()), List.of(arguments));
    }
    private static void assertNoUnboundVariables(RecipeNamespaceCatalogEntry namespace) {
        for (var method : namespace.handlerMethods()) {
            assertNoUnboundVariables(method.returnType());
            for (var parameter : method.params()) assertNoUnboundVariables(parameter.typeRef());
        }
    }
    private static void assertNoUnboundVariables(ApiTypeRef type) {
        assertNotNull(type, "Reflected native signatures require authoritative type metadata");
        assertNotEquals(ApiTypeRef.Kind.TYPE_VARIABLE, type.kind(), "Native handler declarations do not declare this variable: " + type);
        type.arguments().forEach(NativeRecipeHandlerDeclarationTest::assertNoUnboundVariables);
    }

    private static int count(String value, String needle) { return (value.length() - value.replace(needle, "").length()) / needle.length(); }
}
