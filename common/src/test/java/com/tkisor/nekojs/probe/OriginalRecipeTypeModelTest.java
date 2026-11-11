package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

class OriginalRecipeTypeModelTest {
    @Test
    void nominalComponentTypesDriveListsNestedInputsAndOptionalTailWithoutDomainWrappers() {
        var aliases = aliases();
        var element = ApiTypeRef.union(List.of(symbol(Date.class), symbol(Duration.class)));
        var list = ApiTypeRef.symbol(new ApiSymbolId("java", List.class.getName()), List.of(element));
        var namespace = namespace(list);
        var generator = new RecipeEventDeclarationGenerator(aliases);
        String output = generator.generate(List.of(namespace), ScriptType.SERVER);
        assertTrue(output.contains("results: $List_<"), output);
        assertTrue(output.contains("$DateInput"), output);
        assertTrue(output.contains("$Duration"), output);
        assertTrue(output.contains("processingTime?: number"), output);
        assertEquals(2, output.lines().filter(line -> line.stripLeading().startsWith("cutting(")).count(), output);
        assertFalse(output.contains("fallback-only"), output);
        assertFalse(output.contains("SingleOrArray"), output);
        assertFalse(output.contains("export type Create$DateInput"), output);
        assertEquals(output, generator.generate(List.of(namespace), ScriptType.SERVER));
        assertTrue(generator.referencedClasses(List.of(namespace)).containsAll(
                List.of(List.class.getName(), Date.class.getName(), Duration.class.getName(),
                        "com.tkisor.nekojs.api.recipe.RecipeJsonBuilder")));
    }

    @Test
    void explicitSingleInputIsPreservedOnlyWhenTheNominalModelIncludesIt() {
        var element = symbol(Date.class);
        var list = ApiTypeRef.symbol(new ApiSymbolId("java", List.class.getName()), List.of(element));
        String output = new RecipeEventDeclarationGenerator(aliases()).generate(
                List.of(namespace(ApiTypeRef.union(List.of(list, element)))), ScriptType.SERVER);
        assertTrue(output.contains("$List_<$DateInput>"), output);
        assertTrue(output.contains("$DateInput | $List_") || output.contains("$List_<$DateInput> | $DateInput"), output);
        assertFalse(output.contains("SingleOrArray"), output);
    }

    @Test
    void holderAndFunctionalPayloadArgumentsKeepTheirNominalIdentity() {
        var aliases = aliases();
        String holder = "net.minecraft.core.Holder";
        aliases.registerAdapterAlias(holder, "$Holder_", 1);
        aliases.registerAdapterAlias(java.util.function.Function.class.getName(), "$Function_", 2);
        var payload = symbol(Date.class);
        var holderType = ApiTypeRef.symbol(new ApiSymbolId("java", holder), List.of(payload));
        var function = ApiTypeRef.symbol(new ApiSymbolId("java", java.util.function.Function.class.getName()),
                List.of(payload, symbol(Duration.class)));
        for (ApiTypeRef type : List.of(holderType, function)) {
            String output = new RecipeEventDeclarationGenerator(aliases).generate(List.of(namespace(type)), ScriptType.SERVER);
            assertFalse(output.contains("$DateInput"), output);
            assertTrue(output.contains("$Date"), output);
        }
    }

    @Test
    void omittedRecipeModulesHaveNoImportsOrForcedDeclarationRoots() {
        var generator = new RecipeEventDeclarationGenerator(aliases());
        assertTrue(generator.referencedClasses(List.of()).isEmpty());
        assertEquals("", generator.generate(List.of(), ScriptType.SERVER));
        var emptyNamespace = RecipeNamespaceCatalogEntry.of("unrelated", null);
        assertTrue(generator.referencedClasses(List.of(emptyNamespace)).isEmpty());
        assertFalse(generator.generate(List.of(emptyNamespace), ScriptType.SERVER).contains("RecipeJsonBuilder"));
        assertEquals("", generator.generate(List.of(namespace(symbol(Date.class))), ScriptType.CLIENT));
        assertTrue(generator.referencedClasses(List.of(namespace(symbol(Date.class))))
                .contains("com.tkisor.nekojs.api.recipe.RecipeJsonBuilder"));
    }

    @Test
    void unrelatedNamespacesUseTheSameSharedInputRules() {
        var input = ApiTypeRef.symbol(new ApiSymbolId("java", List.class.getName()), List.of(symbol(Date.class)));
        var fields = List.of(new RecipeSchemaTypeEntry.SchemaField("parts", "JSON", true, true, "parts",
                literal("unused-structural-fallback"), input));
        var namespaces = List.of("foundry", "tooling", "native_custom").stream().map(name -> {
            var schema = new RecipeSchemaTypeEntry(name, "assemble", name + "_assemble", fields,
                    List.of(List.of("parts")), Map.of(), List.of(), null);
            return new RecipeNamespaceCatalogEntry(name, null, List.of("assemble"), true,
                    List.of(), List.of(), List.of(schema));
        }).toList();
        String output = new RecipeEventDeclarationGenerator(aliases()).generate(namespaces, ScriptType.SERVER);
        assertEquals(3, output.lines().filter(line -> line.contains("assemble(parts: $List_<$DateInput>)")).count(), output);
        assertFalse(output.contains("unused-structural-fallback"), output);
        assertFalse(output.contains("SingleOrArray"), output);
    }

    private static TypeAliasRegistry aliases() {
        var aliases = new TypeAliasRegistry();
        aliases.registerClassAlias(Date.class.getName(), "$DateInput");
        return aliases;
    }

    private static ApiTypeRef symbol(Class<?> type) {
        return ApiTypeRef.symbol(new ApiSymbolId("java", type.getName()));
    }

    private static RecipeNamespaceCatalogEntry namespace(ApiTypeRef input) {
        var fields = List.of(new RecipeSchemaTypeEntry.SchemaField("results", "JSON", true, true, "results",
                        AdapterInputShape.literal("fallback-only"), input),
                new RecipeSchemaTypeEntry.SchemaField("processing_time", "INT", false, false, "processing_time",
                        number(), ApiTypeRef.primitive("number")));
        var schema = new RecipeSchemaTypeEntry("create", "cutting", "create_cutting", fields,
                List.of(List.of("results"), List.of("results", "processing_time")),
                Map.of("processingTime", "processing_time"), List.of(), null);
        return new RecipeNamespaceCatalogEntry("create", null, List.of("cutting"), true,
                List.of(), List.of(), List.of(schema));
    }
}
