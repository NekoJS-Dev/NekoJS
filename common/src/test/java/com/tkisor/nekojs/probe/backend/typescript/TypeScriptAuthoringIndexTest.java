package com.tkisor.nekojs.probe.backend.typescript;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.authoring.AuthoringJson;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptAuthoringEmission;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptAuthoringIndexTest {
    @TempDir Path game;

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void completedGeneratorTransfersItsRecordsOnceAndClassRecordsAreAdopted() {
        var rendered = render();
        JsonObject original = rendered.owned().get(Surface.class.getName()).members().stream()
                .filter(member -> member.get("name").getAsString().equals("instanceEcho"))
                .findFirst().orElseThrow();
        String originalId = original.get("symbolId").getAsString();
        var document = consume(rendered);
        JsonObject adopted = document.getAsJsonObject("resources").getAsJsonArray("members").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(member -> member.get("symbolId").getAsString().equals(originalId))
                .findFirst().orElseThrow();
        // Retain only an identity observer: subsequent reads belong to the consumed snapshot.
        assertSame(original, adopted);
        assertEquals(List.of("CLIENT", "SERVER"), strings(adopted.getAsJsonArray("scriptTypes")));
        assertEquals("class:" + AuthoringJson.hash(Surface.class.getName()), adopted.get("ownerSymbolId").getAsString());
        assertEquals(packagePath(), adopted.getAsJsonObject("source").get("path").getAsString());
    }

    @Test
    void bindingViewsKeepTheirOwnNestedRecordsAndSideSpecificMetadata() {
        JsonObject document = consume(render());
        String classOwner = "class:" + AuthoringJson.hash(Surface.class.getName());
        for (var view : List.of(new View("Instance", ScriptType.SERVER, "instanceEcho", false),
                new View("Static", ScriptType.CLIENT, "staticEcho", true))) {
            String bindingOwner = "binding:" + AuthoringJson.hash(view.side().name() + ":" + view.binding());
            JsonObject original = member(document, classOwner, view.method());
            JsonObject binding = member(document, bindingOwner, view.method());
            assertEquals(List.of(view.method()), document.getAsJsonObject("resources").getAsJsonArray("members").asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .filter(candidate -> candidate.get("ownerSymbolId").getAsString().equals(bindingOwner))
                    .map(candidate -> candidate.get("name").getAsString()).toList());
            assertNotSame(original, binding);
            assertNotSame(original.get("parameters"), binding.get("parameters"));
            JsonObject originalParameter = original.getAsJsonArray("parameters").get(0).getAsJsonObject();
            JsonObject bindingParameter = binding.getAsJsonArray("parameters").get(0).getAsJsonObject();
            assertNotSame(originalParameter, bindingParameter);
            assertNotSame(originalParameter.get("type"), bindingParameter.get("type"));
            assertNotSame(original.get("source"), binding.get("source"));
            assertEquals(view.staticMember(), original.get("isStatic").getAsBoolean());
            assertFalse(binding.get("isStatic").getAsBoolean());
            assertEquals(List.of(view.side().name()), strings(binding.getAsJsonArray("scriptTypes")));
            assertEquals("member:" + AuthoringJson.hash(bindingOwner + ":" + original.get("symbolId").getAsString()),
                    binding.get("symbolId").getAsString());
            String parameterName = originalParameter.get("name").getAsString();
            bindingParameter.addProperty("name", "binding-only");
            assertEquals(parameterName, originalParameter.get("name").getAsString());
            assertFalse(document.getAsJsonObject("resources").getAsJsonArray("members").asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .anyMatch(candidate -> candidate.get("ownerSymbolId").getAsString().equals(bindingOwner)
                            && candidate.get("isStatic").getAsBoolean()));
        }
    }

    @Test
    void freshEquivalentTransfersPreserveCompactBytesAndLegacySnapshotIdentity() {
        JsonObject first = consume(render());
        JsonObject second = consume(render());
        assertEquals(first.toString(), second.toString());
        String snapshotId = first.get("snapshotId").getAsString();
        JsonObject withoutIdentity = first.deepCopy();
        withoutIdentity.remove("snapshotId");
        assertEquals("snapshot:" + AuthoringJson.hash(withoutIdentity.toString()), snapshotId);
    }

    private Rendered render() {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        generator.enableAuthoringIndex();
        generator.predeclareClass(Surface.class.getName(), new TypeReflector().reflect(Surface.class), Set.of());
        generator.prepareClassInheritance();
        String simpleName = Surface.class.getName().substring(Surface.class.getPackageName().length() + 1);
        String declaration = generator.generate(Surface.class.getPackageName(), List.of(simpleName), List.of(),
                Set.of(Surface.class.getName()));
        var owned = generator.takeAuthoringDeclarations();
        assertEquals(Set.of(Surface.class.getName()), owned.keySet());
        assertTrue(generator.takeAuthoringDeclarations().isEmpty());
        assertThrows(UnsupportedOperationException.class, owned::clear);
        generator.clearCaches();
        assertFalse(owned.get(Surface.class.getName()).members().isEmpty());
        return new Rendered(Map.of(packagePath(), declaration,
                "@side-only/server/bindings/index.d.ts", "export {};",
                "@side-only/client/bindings/index.d.ts", "export {};"), owned);
    }

    private JsonObject consume(Rendered rendered) {
        var bindings = List.of(BindingCatalogEntry.of("Instance", ScriptType.SERVER, Surface.class, false),
                BindingCatalogEntry.of("Static", ScriptType.CLIENT, Surface.class, true));
        var snapshot = new NekoScriptCatalogSnapshot(List.of(ScriptType.CLIENT, ScriptType.SERVER), bindings,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(Surface.class.getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, List.of(Surface.class), config, NekoJSPaths.fromGameDir(game),
                "typescript", game.resolve("typescript"), List.of());
        return TypeScriptAuthoringIndex.consumeOwnedEmissions(context, rendered.files(), rendered.owned());
    }

    private static JsonObject member(JsonObject document, String owner, String name) {
        return document.getAsJsonObject("resources").getAsJsonArray("members").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(member -> member.get("ownerSymbolId").getAsString().equals(owner)
                        && member.get("name").getAsString().equals(name))
                .findFirst().orElseThrow();
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }

    private static String packagePath() {
        return "@package/" + Surface.class.getPackageName().replace('.', '/') + "/index.d.ts";
    }

    private record Rendered(Map<String, String> files, Map<String, TypeScriptAuthoringEmission.Declaration> owned) {}
    private record View(String binding, ScriptType side, String method, boolean staticMember) {}

    public static final class Surface {
        public String instanceEcho(String input) { return input; }
        public static String staticEcho(String input) { return input; }
    }
}
