package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeInputAliasDeclarationTest {
    private static final AdapterInputShape RESULTS = union(string(), arrayOf(object(
            Slot.req("id", string()), Slot.opt("count", number()))));

    @Test
    void visibleInputsAreSharedByOptionsPositionalsAndFluentSetters() {
        String output = render(List.of(schema("cutting", RESULTS)));
        String type = "(string | ({ id: string, count?: number })[])";
        assertFalse(output.contains("export type Create$ResultsInput"), output);
        assertTrue(output.contains("export type Create$CuttingOptions = "), output);
        assertTrue(output.contains("results(results: " + type + "): this;"), output);
        assertTrue(output.contains("cutting(options: Create$CuttingOptions): Create$Cutting;"), output);
        assertTrue(output.contains("cutting(\n                results: " + type
                + ",\n                input: string,\n                processingTime?: number\n            ): Create$Cutting;"), output);
        assertTrue(output.contains("processing_time?: number, processingTime?: never"), output);
        assertTrue(output.contains("processing_time?: never, processingTime?: number"), output);
        assertEquals(2, count(output, "            cutting("), output);
    }

    @Test
    void distinctShapesKeepTheirContainersAndElementsVisible() {
        String output = render(List.of(schema("cutting", RESULTS), schema("pressing", RESULTS),
                schema("different", arrayOf(number()))));
        assertFalse(output.contains("export type Create$ResultsInput"), output);
        assertTrue(output.contains("pressing(\n                results: (string | ({ id: string, count?: number })[]),"
                + "\n                input: string,\n                processingTime?: number\n            ): Create$Pressing;"), output);
        assertTrue(output.contains("different(results: number[], input: string"), output);
    }

    @Test
    void optionsNamesAvoidBuildersWithoutMutatingSharedRegistry() {
        var aliases = new TypeAliasRegistry();
        var generator = new RecipeEventDeclarationGenerator(aliases);
        var namespaces = List.of(namespace(List.of(schema("cutting", RESULTS), schema("results_input", RESULTS),
                schema("cutting_options", RESULTS))));
        String first = generator.generate(namespaces, ScriptType.SERVER);
        assertTrue(first.contains("export class Create$ResultsInput extends"), first);
        assertFalse(first.contains("export type Create$ResultsInput ="), first);
        assertTrue(first.contains("export class Create$CuttingOptions extends"), first);
        assertTrue(first.contains("export type Create$CuttingOptions2 = "), first);
        assertEquals(first, generator.generate(namespaces, ScriptType.SERVER));
        assertNull(aliases.getAlias("Create$CuttingOptions2"));
        String isolated = generator.generate(List.of(namespace(List.of(schema("cutting", RESULTS)))), ScriptType.SERVER);
        assertTrue(isolated.contains("export type Create$CuttingOptions = "), isolated);
        assertFalse(isolated.contains("export type Create$CuttingOptions2 = "), isolated);
    }

    private static RecipeSchemaTypeEntry schema(String name, AdapterInputShape shape) {
        return new RecipeSchemaTypeEntry("create", name, "create_" + name, List.of(
                new RecipeSchemaTypeEntry.SchemaField("results", "JSON", true, false, "results", shape),
                new RecipeSchemaTypeEntry.SchemaField("input", "STRING", true, false, "ingredients", string()),
                new RecipeSchemaTypeEntry.SchemaField("processing_time", "INT", false, false, "processing_time", number())),
                List.of(List.of("results", "input"), List.of("results", "input", "processing_time")),
                Map.of("processingTime", "processing_time"), List.of(), null);
    }

    private static RecipeNamespaceCatalogEntry namespace(List<RecipeSchemaTypeEntry> schemas) {
        return new RecipeNamespaceCatalogEntry("create", null, schemas.stream().map(RecipeSchemaTypeEntry::type).toList(),
                true, List.of(), List.of(), schemas);
    }

    private static String render(List<RecipeSchemaTypeEntry> schemas) {
        return new RecipeEventDeclarationGenerator(new TypeAliasRegistry()).generate(List.of(namespace(schemas)), ScriptType.SERVER);
    }

    private static int count(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}
