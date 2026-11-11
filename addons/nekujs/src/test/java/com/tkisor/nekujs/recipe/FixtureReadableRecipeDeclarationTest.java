package com.tkisor.nekujs.recipe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekujs.bridge.OriginalRecipeInputFixture;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekujs.plugin.LegacyPluginDiscovery;
import com.tkisor.nekujs.recipe.schema.ProgrammaticRecipeSchemas;
import com.tkisor.nekujs.recipe.schema.RecipeSchemaGraphCompiler;
import com.tkisor.nekujs.recipe.value.LegacyRecipeComponents;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.schema.RecipeFactoryRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Renders a fresh preview from unchanged addon jars, their real codecs, and the canonical generator. */
@Tag("expanded-profile")
class FixtureReadableRecipeDeclarationTest {
    private static final String CREATE = "kubejs-create-neoforge-2101.3.1-build.18.jar";
    private static final String MEKANISM = "kubejs-mekanism-neoforge-2101.1.7-build.18.jar";
    private static final String ENDERIO = "kubejs_enderio-neoforge-1.21.1-0.13.0.jar";

    @BeforeAll
    static void bootstrap(@TempDir Path gameDirectory) {
        FixtureEnderIORecipeTest.bootstrap(gameDirectory);
    }

    @Test
    void originalAddonSchemasRenderReadableNativeDeclarations(@TempDir Path directory) throws Exception {
        Path fixtures = Path.of(System.getProperty("nekujs.fixtures.dir"));
        try (var loader = fixtureLoader(fixtures, directory)) {
            var registries = new RegistryAccessContainer(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            var storage = new RecipeSchemaStorage(registries);
            var plugins = List.of(
                    originalPlugin(loader, fixtures.resolve(CREATE), "dev.latvian.mods.kubejs.create.KubeJSCreatePlugin"),
                    originalPlugin(loader, fixtures.resolve(MEKANISM), "dev.latvian.mods.kubejs.mekanism.MekanismKubeJSPlugin"),
                    originalPlugin(loader, fixtures.resolve(ENDERIO), "com.almostreliable.kubeio.KubePlugin"));
            plugins.forEach(plugin -> plugin.registerRecipeComponents(storage));
            storage.freezeComponents();
            plugins.forEach(plugin -> plugin.registerRecipeFactories(new RecipeFactoryRegistry(storage)));
            plugins.forEach(plugin -> plugin.registerRecipeSchemas(new RecipeSchemaRegistry(storage)));
            var sources = List.of(new LegacyPluginDiscovery.Source("kubejs_create", fixtures.resolve(CREATE)),
                    new LegacyPluginDiscovery.Source("kubejs_mekanism", fixtures.resolve(MEKANISM)),
                    new LegacyPluginDiscovery.Source("kubejs_enderio", fixtures.resolve(ENDERIO)));
            var resources = new RecipeSchemaGraphCompiler().compile(LegacyRecipeSchemaSources.read(sources),
                    new LegacyRecipeComponents(storage, registries));
            Map<String, RecipeTypeDefinition> definitions = new LinkedHashMap<>(resources.definitions());
            ProgrammaticRecipeSchemas.compile(storage).forEach((id, definition) ->
                    assertNull(definitions.putIfAbsent(id, definition), "Duplicate original profile: " + id));
            assertEquals(71, definitions.size());
            assertEquals(57, definitions.values().stream().filter(definition -> definition.unsupportedReason() == null).count());
            for (String id : List.of("mekanism:bin_extract", "mekanism:bin_insert", "mekanism:clear_configuration",
                    "mekanism:mek_data", "create:mechanical_crafting", "mekanismtools:paxel", "mekanismtools:banner_shield")) {
                assertNotNull(definitions.get(id), id);
                assertNull(definitions.get(id).unsupportedReason(), id);
                assertEquals(id, definitions.get(id).type(), id);
            }

            var namespaces = definitions.values().stream().collect(Collectors.groupingBy(RecipeTypeDefinition::namespace,
                    TreeMap::new, Collectors.toList())).entrySet().stream().map(entry -> {
                        var schemas = entry.getValue().stream().sorted(Comparator.comparing(RecipeTypeDefinition::name))
                                .map(RecipeSchemaTypeEntry::from).toList();
                        return new RecipeNamespaceCatalogEntry(entry.getKey(), null,
                                schemas.stream().map(RecipeSchemaTypeEntry::type).toList(), true, List.of(), List.of(), schemas);
                    }).toList();
            var catalog = OriginalRecipeInputFixture.catalog(OriginalRecipeInputFixture.adapters(plugins.getFirst()));
            var aliases = new TypeAliasRegistry();
            var inputAliases = new AdapterAliasGenerator(aliases);
            inputAliases.prepare(catalog, catalog.stream().map(entry -> entry.targetType().getName()).collect(Collectors.toSet()));
            var generator = new RecipeEventDeclarationGenerator(aliases);
            String declarations = generator.generate(namespaces, ScriptType.SERVER);
            assertEquals(declarations, generator.generate(namespaces, ScriptType.SERVER));
            Path preview = Path.of("build/reports/compatibility/recipes-readable/index.d.ts");
            Files.createDirectories(preview.getParent());
            Files.writeString(preview, declarations);

            var cutting = signatures(declarations, "cutting");
            assertEquals(2, cutting.size(), declarations);
            assertTrue(cutting.stream().anyMatch(line -> line.contains("cutting(options: Create$CuttingOptions)")), declarations);
            String positional = cutting.stream().filter(line -> !line.contains("options:")).findFirst().orElseThrow();
            String last = definitions.get("create:cutting").constructors().stream().max(Comparator.comparingInt(List::size))
                    .orElseThrow().getLast();
            assertEquals("processing_time", last, "The unchanged fixture retains its canonical JSON field key");
            assertTrue(positional.contains("processingTime?: $TickDuration_"), positional);
            assertTrue(positional.contains("results: $List_<"), positional);
            assertTrue(positional.contains("$FluidStack_"), positional);
            assertTrue(positional.contains("$ProcessingOutput"), positional);
            assertFalse(positional.contains("$ProcessingOutput_"), positional);
            assertTrue(positional.contains("$SizedFluidIngredient_"), positional);
            assertTrue(positional.contains("$Ingredient_"), positional);
            assertFalse(declarations.contains("SingleOrArray"), declarations);
            assertFalse(declarations.contains("Create$FluidStackInput"), declarations);
            assertFalse(declarations.contains("Create$ProcessingOutputInput"), declarations);
            assertFalse(declarations.contains("export type Create$ResultsInput = "), declarations);
            assertFalse(declarations.contains("export type Create$IngredientsInput = "), declarations);
            var processing = catalog.stream().filter(entry -> entry.targetType().getSimpleName().equals("ProcessingOutput"))
                    .findFirst().orElseThrow();
            assertTrue(processing.shapes().isEmpty(), "Opaque converters must not acquire guessed declarations");
            assertNull(inputAliases.getAlias(processing.targetType().getName()));
            String fluidInput = inputAliases.getAlias(net.neoforged.neoforge.fluids.FluidStack.class.getName()).union();
            assertTrue(fluidInput.contains("amount?: number"), fluidInput);
            String timeInput = inputAliases.getAlias(dev.latvian.mods.kubejs.util.TickDuration.class.getName()).union();
            assertTrue(timeInput.contains("string") && timeInput.contains("$Duration"), timeInput);
            String canonicalInputs = catalog.stream().map(entry -> inputAliases.getAlias(entry.targetType().getName()))
                    .filter(java.util.Objects::nonNull).map(alias -> "export type " + alias.aliasName()
                            + alias.typeParameters() + " = " + alias.union() + ";")
                    .collect(Collectors.joining("\n"));
            Files.writeString(preview.resolveSibling("input-aliases.txt"), canonicalInputs + "\n");
            assertTrue(declarations.contains("from \"java:java/util\""), declarations);
            assertTrue(declarations.contains("from \"java:dev/latvian/mods/kubejs/util\""), declarations);
            assertTrue(declarations.contains("emptying(): this;"), declarations);
        }
    }

    private static List<String> signatures(String declarations, String method) {
        List<String> signatures = new ArrayList<>();
        StringBuilder current = null;
        for (String line : declarations.lines().toList()) {
            String text = line.strip();
            if (current == null) {
                if (!text.startsWith(method + "(")) continue;
                current = new StringBuilder();
            }
            current.append(text).append(' ');
            if (text.endsWith(";")) {
                signatures.add(current.toString().stripTrailing());
                current = null;
            }
        }
        assertNull(current, "Unterminated generated recipe signature");
        return List.copyOf(signatures);
    }

    private static KubeJSPlugin originalPlugin(URLClassLoader loader, Path jar, String className) throws Exception {
        Class<?> type = Class.forName(className, true, loader);
        assertSame(loader, type.getClassLoader());
        assertEquals(jar.toUri().toURL(), type.getProtectionDomain().getCodeSource().getLocation());
        return assertInstanceOf(KubeJSPlugin.class, type.getConstructor().newInstance());
    }

    private static URLClassLoader fixtureLoader(Path fixtures, Path directory) throws Exception {
        var urls = new ArrayList<URL>();
        try (var files = Files.list(fixtures)) {
            for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) {
                urls.add(jar.toUri().toURL());
            }
        }
        for (String source : List.of("create-1.21.1-6.0.10.jar", "enderio-8.2.12-beta.jar")) {
            Path libraries = Files.createDirectories(directory.resolve(source));
            try (var jar = new JarFile(fixtures.resolve(source).toFile())) {
                for (var entry : jar.stream().filter(value -> value.getName().startsWith("META-INF/jarjar/")
                        && value.getName().endsWith(".jar")).toList()) {
                    Path library = libraries.resolve(Path.of(entry.getName()).getFileName().toString());
                    try (var input = jar.getInputStream(entry)) { Files.copy(input, library); }
                    urls.add(library.toUri().toURL());
                }
            }
        }
        return new URLClassLoader(urls.toArray(URL[]::new), FixtureReadableRecipeDeclarationTest.class.getClassLoader());
    }
}
