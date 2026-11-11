package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeReadableInputTest {
    private static final AdapterInputShape FLUID = union(host(Date.class), union(string(),
            object(Slot.req("id", string()), Slot.req("amount", number())),
            object(Slot.req("fluid", string()), Slot.req("amount", number()))));
    private static final AdapterInputShape OUTPUT = union(host(Duration.class),
            object(Slot.req("id", string()), Slot.opt("count", number()), Slot.opt("chance", number())));

    private static final String FLUID_TYPE = "($Date | (string | { id: string, amount: number } | { fluid: string, amount: number }))";
    private static final String OUTPUT_TYPE = "($Duration | { id: string, count?: number, chance?: number })";

    @Test
    void structuralFallbackPreservesScalarAndArrayInputsWithoutInventingAliases() {
        var element = union(FLUID, OUTPUT);
        var results = union(element, arrayOf(element));
        var aliases = new TypeAliasRegistry();
        aliases.registerClassAlias(Date.class.getName(), "$BroadDate_");
        String output = render(results, aliases);
        assertFalse(output.contains("export type Create$ResultsInput"), output);
        String elementType = "(" + FLUID_TYPE + " | " + OUTPUT_TYPE + ")";
        assertTrue(output.contains("results: (" + elementType + " | " + elementType + "[])"), output);
        assertFalse(output.contains("SingleOrArray"), output);
        assertFalse(output.contains("export type Create$DateInput"), output);
        assertTrue(output.contains("amount: number"), output);
        assertTrue(output.contains("count?: number"), output);
        assertTrue(output.contains("chance?: number"), output);
        assertFalse(output.contains("$BroadDate_"), "A host leaf must not widen to an unrelated adapter: " + output);
        assertFalse(output.contains("amount?: number"), output);
    }

    @Test
    void unrelatedArrayBranchesDoNotBecomeAnArrayOfTheirUnion() {
        String output = render(union(arrayOf(FLUID), arrayOf(OUTPUT)), new TypeAliasRegistry());
        assertFalse(output.contains("SingleOrArray"), output);
        assertTrue(output.contains("results: (" + FLUID_TYPE + "[] | " + OUTPUT_TYPE + "[])"), output);
        assertFalse(output.contains("(" + FLUID_TYPE + " | " + OUTPUT_TYPE + ")[]"), output);
    }

    @Test
    void primitivesAndShortStructuralShapesRemainVisible() {
        String output = render(union(string(), arrayOf(object(Slot.req("id", string())))), new TypeAliasRegistry());
        assertFalse(output.contains("export type Create$ResultsInput"), output);
        assertTrue(output.contains("results: (string | ({ id: string })[])"), output);
    }

    @Test
    void rawCallbacksKeepTheirOwnParenthesesWhenEmbedded() {
        String callback = "((value: number) => number) | ((value: string) => string)";
        String output = render(arrayOf(raw(callback)), new TypeAliasRegistry());
        assertTrue(output.contains("results: (" + callback + ")[]"), output);
        assertFalse(output.contains("results: " + callback + "[]"), output);
    }

    @Test
    void multipleNominalConversionsKeepEveryExactHostAndStructuralAlternative() {
        var value = union(host(Date.class), host(Duration.class), object(Slot.req("count", number())));
        String output = render(union(value, arrayOf(value)), new TypeAliasRegistry());
        String valueType = "($Date | $Duration | { count: number })";
        assertTrue(output.contains("results: (" + valueType + " | " + valueType + "[])"), output);
        assertFalse(output.contains("DateOrDurationInput"), output);
        assertFalse(output.contains("SingleOrArray"), output);
    }

    @Test
    void erasedGenericHostsRetainTheirDeclaredArity() {
        String output = render(arrayOf(host(Map.class)), new TypeAliasRegistry());
        assertTrue(output.contains("results: $Map<any, any>[]"), output);
        assertFalse(output.contains("results: $Map[]"), output);
    }

    @Test
    void outerAlternativeDoesNotDiscardNestedNominalOrStructuralInputs() {
        var value = union(host(StringBuilder.class), FLUID);
        String output = render(union(value, arrayOf(value)), new TypeAliasRegistry());
        assertFalse(output.contains("export type Create$StringBuilderInput"), output);
        String valueType = "($StringBuilder | " + FLUID_TYPE + ")";
        assertTrue(output.contains("results: (" + valueType + " | " + valueType + "[])"), output);
        assertFalse(output.contains("SingleOrArray"), output);
    }

    @Test
    void callbackAlternativeKeepsItsReturnTypeSeparateFromTheOuterUnion() {
        for (String callback : List.of("((value: number) => boolean)", "(value: number) => boolean")) {
            String output = render(union(raw(callback), number()), new TypeAliasRegistry());
            assertTrue(output.contains("results: ((" + callback + ") | number)"), output);
            assertFalse(output.contains("=> boolean | number"), output);
            String arrayOutput = render(arrayOf(raw(callback)), new TypeAliasRegistry());
            assertTrue(arrayOutput.contains("results: ((value: number) => boolean)[]"), arrayOutput);
            assertFalse(arrayOutput.contains("results: (value: number) => boolean[]"), arrayOutput);
        }
    }

    private static String render(AdapterInputShape results, TypeAliasRegistry aliases) {
        var schema = new RecipeSchemaTypeEntry("create", "cutting", "cutting", List.of(
                new RecipeSchemaTypeEntry.SchemaField("results", "JSON", true, false, "results", results),
                new RecipeSchemaTypeEntry.SchemaField("ingredients", "JSON", true, false, "ingredients", arrayOf(string()))),
                List.of(List.of("results", "ingredients")), Map.of(), List.of(), null);
        var namespace = new RecipeNamespaceCatalogEntry("create", null, List.of("cutting"), true,
                List.of(), List.of(), List.of(schema));
        return new RecipeEventDeclarationGenerator(aliases).generate(List.of(namespace), ScriptType.SERVER);
    }

}
