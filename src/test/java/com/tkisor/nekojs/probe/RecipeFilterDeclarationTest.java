package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.js.type_adapter.RecipeFilterAdapter;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeFilterDeclarationTest {
    private static AdapterAliasGenerator generator() {
        RecipeFilterAdapter adapter = new RecipeFilterAdapter();
        AdapterAliasGenerator generator = new AdapterAliasGenerator(new TypeAliasRegistry());
        var ingredient = new com.tkisor.nekojs.js.type_adapter.IngredientAdapter();
        var stack = new com.tkisor.nekojs.js.type_adapter.ItemStackAdapter();
        generator.prepare(List.of(new AdapterCatalogEntry(adapter.getTargetClass(), adapter.inputShapes(), adapter.getPrecedence(), Optional.empty()),
                new AdapterCatalogEntry(ingredient.getTargetClass(), ingredient.inputShapes(), ingredient.getPrecedence(), ingredient.syntaxDoc()),
                new AdapterCatalogEntry(stack.getTargetClass(), stack.inputShapes(), stack.getPrecedence(), stack.syntaxDoc())),
                Set.of(RecipeFilter.class.getName(), ingredient.getTargetClass().getName(), stack.getTargetClass().getName()));
        return generator;
    }

    private static AdapterAliasGenerator.AdapterAlias actualAlias() {
        var alias = generator().getAlias(RecipeFilter.class.getName());
        assertNotNull(alias);
        return alias;
    }

    //? if neoforge {
    private static com.tkisor.nekojs.testfixture.RecipeFilterPlatformFixture platformScope;

    @Test @org.junit.jupiter.api.Tag("native-recipe-probe")
    void generateStrictConsumerFromCanonicalAliasesAndActualNativeContextDeclarations() throws Exception {
        requireNativeCatalogMetadata();
        var generator = generator();
        var aliases = new TypeAliasRegistry();
        var modules = new java.util.TreeMap<String, StringBuilder>();
        var hosts = new java.util.TreeSet<String>();
        hosts.add(RecipeFilter.class.getName());
        hosts.add(net.minecraft.world.item.crafting.Ingredient.class.getName());
        hosts.add(net.minecraft.world.item.ItemStack.class.getName());
        var targets = List.of(RecipeFilter.class, net.minecraft.world.item.crafting.Ingredient.class, net.minecraft.world.item.ItemStack.class);
        for (Class<?> target : targets) {
            var alias = generator.getAlias(target.getName());
            var module = modules.computeIfAbsent(moduleOf(target.getName()), ignored -> new StringBuilder());
            module.append("    import { RegistryTypes } from \"@special/types\";\n");
            for (String imported : new java.util.TreeSet<>(alias.importFqns())) {
                var dependency = generator.getAlias(imported);
                String names = hostName(imported) + (dependency == null ? "" : ", " + dependency.aliasName());
                module.append("    import { ").append(names).append(" } from \"").append(moduleOf(imported)).append("\";\n");
            }
            hosts.addAll(alias.referenceFqns());
            module.append(alias.declarations("    "));
        }
        var reflector = new com.tkisor.nekojs.probe.ir.TypeReflector();
        var renderer = new com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer(aliases);
        for (Class<?> context : List.of(com.tkisor.nekojs.api.recipe.RecipeMatchContext.class, com.tkisor.nekojs.api.recipe.RecipeMatchRecipe.class)) {
            String pkg = moduleOf(context.getName());
            var module = modules.computeIfAbsent(pkg, ignored -> new StringBuilder());
            var references = new java.util.TreeSet<String>();
            var scoped = aliases.withReferences((fqn, name) -> { references.add(fqn); return name; });
            String declaration = renderer.render(reflector.reflect(context), scoped);
            for (String reference : references) {
                if (!moduleOf(reference).equals(pkg)) module.append("    import { ").append(hostName(reference)).append(" } from \"").append(moduleOf(reference)).append("\";\n");
            }
            hosts.addAll(references);
            module.append(declaration);
        }
        hosts.remove(com.tkisor.nekojs.api.recipe.RecipeMatchContext.class.getName());
        hosts.remove(com.tkisor.nekojs.api.recipe.RecipeMatchRecipe.class.getName());
        for (String host : hosts) {
            Class<?> type = Class.forName(host, false, RecipeFilter.class.getClassLoader());
            int arity = type.getTypeParameters().length;
            String parameters = arity == 0 ? "" : "<" + java.util.stream.IntStream.range(0, arity).mapToObj(i -> "T" + i + " = any").collect(java.util.stream.Collectors.joining(", ")) + ">";
            modules.computeIfAbsent(moduleOf(host), ignored -> new StringBuilder())
                    .append("    export class ").append(hostName(host)).append(parameters).append(" { private readonly nativeIdentity: never; }\n");
        }
        var catalog = com.tkisor.nekojs.js.type_adapter.RegistryTypeCatalogs.snapshot(
                com.tkisor.nekojs.js.type_adapter.RegistryTypeCatalogs.builtInSources(), List.of(), registry -> List.of());
        var snapshot = new com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), catalog, List.of(), null, java.util.Map.of(), List.of());
        var config = new ProbeConfig(true, ".probe", new ProbeConfig.ScanConfig(List.of(), List.of(), List.of(), List.of(), 1, "SMART"));
        var probe = new ProbeContext.Of(snapshot, List.of(), config, com.tkisor.nekojs.core.fs.NekoJSPaths.fromGameDir(java.nio.file.Path.of("build/probe-ts-recipe-filter")),
                "typescript", java.nio.file.Path.of("build/probe-ts-recipe-filter"), List.of());
        String special = new com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend().render(probe).get("@special/types/index.d.ts");
        assertNotNull(special);
        assertTrue(special.contains("type RecipeSerializer =") && special.contains("type RecipeType ="), special);
        StringBuilder declarations = new StringBuilder(special.substring(0, special.indexOf("\nexport * as types")));
        modules.forEach((module, body) -> {
            declarations.append("declare module \"").append(module).append("\" {\n");
            var imports = new java.util.HashSet<String>();
            body.toString().lines().filter(line -> !line.stripLeading().startsWith("import ") || imports.add(line))
                    .forEach(line -> declarations.append(line).append("\n"));
            declarations.append("}\n");
        });
        java.nio.file.Path output = java.nio.file.Path.of("build/probe-ts-recipe-filter");
        java.nio.file.Files.createDirectories(output);
        java.nio.file.Files.writeString(output.resolve("declarations.d.ts"), declarations);
        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath();
        while (!java.nio.file.Files.isRegularFile(root.resolve("common/src/test/probe-ts/recipe-filter.ts"))) {
            root = root.getParent();
            if (root == null) throw new IllegalStateException("Recipe filter strict consumer fixture is missing");
        }
        java.nio.file.Files.copy(root.resolve("common/src/test/probe-ts/recipe-filter.ts"), output.resolve("exercise.ts"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        java.nio.file.Files.writeString(output.resolve("tsconfig.json"), "{\"compilerOptions\":{\"strict\":true,\"noEmit\":true,\"skipLibCheck\":false,\"target\":\"ES2022\",\"module\":\"ESNext\",\"moduleResolution\":\"Bundler\",\"types\":[]},\"files\":[\"declarations.d.ts\",\"exercise.ts\"]}");
        assertTrue(declarations.toString().contains("getOrCreateId(): string"), declarations.toString());
    }

    @Test @org.junit.jupiter.api.Tag("native-recipe-probe")
    void realPlatformCatalogPublishesSerializerAndRecipeTypeFromTheirDistinctNativeRegistries() throws ReflectiveOperationException {
        requireNativeCatalogMetadata();
        var sources = com.tkisor.nekojs.js.type_adapter.RegistryTypeCatalogs.builtInSources();
        assertSame(net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER, sources.get("RecipeSerializer"));
        assertSame(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE, sources.get("RecipeType"));
        var snapshot = com.tkisor.nekojs.js.type_adapter.RegistryTypeCatalogs.snapshot(sources, List.of(), registry -> List.of());
        var serializers = snapshot.stream().filter(entry -> entry.typeName().equals("RecipeSerializer")).findFirst().orElseThrow();
        var recipeTypes = snapshot.stream().filter(entry -> entry.typeName().equals("RecipeType")).findFirst().orElseThrow();
        assertTrue(serializers.entries().contains("minecraft:crafting_shaped"));
        assertTrue(serializers.entries().contains("minecraft:crafting_shapeless"));
        assertFalse(serializers.entries().contains("minecraft:crafting"));
        assertTrue(recipeTypes.entries().contains("minecraft:crafting"));
        assertFalse(recipeTypes.entries().contains("minecraft:crafting_shaped"));
    }

    private static void requireNativeCatalogMetadata() {
        if (platformScope == null) platformScope = com.tkisor.nekojs.testfixture.RecipeFilterPlatformFixture.open();
        com.tkisor.nekojs.testfixture.NativeLoadingServicesTestFixture.initialize();
        boolean available = com.tkisor.nekojs.testfixture.VanillaRegistryProbe.registryMetadataAvailable();
        String failure = "Native registry catalog bootstrap failed: " + com.tkisor.nekojs.testfixture.VanillaRegistryProbe.metadataFailure();
        if (com.tkisor.nekojs.testfixture.NativeLoadingServicesTestFixture.requiresIsolatedRegistryMetadata()) assertTrue(available, failure);
        else org.junit.jupiter.api.Assumptions.assumeTrue(available, failure);
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreOwnedPlatform() {
        if (platformScope != null) platformScope.close();
    }
    //?}

    private static String moduleOf(String fqn) { return "java:" + fqn.substring(0, fqn.lastIndexOf('.')).replace('.', '/'); }
    private static String hostName(String fqn) { return "$" + fqn.substring(fqn.lastIndexOf('.') + 1); }

    @Test void canonicalRecipeFilterIndexUsesReadableNamedHelpersInsteadOfExpandingSelectorFields() {
        var aliases = generator();
        var alias = aliases.getAlias(RecipeFilter.class.getName());
        assertNotNull(alias);
        assertTrue(alias.union().contains("$RecipeFilterConditions_"),
                "The main input union must reference its named conditions instead of embedding every selector: " + alias.union());
        var index = new com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator(
                new com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer(new TypeAliasRegistry()), aliases);
        index.predeclareClass(RecipeFilter.class.getName(), new com.tkisor.nekojs.probe.ir.TypeReflector().reflect(RecipeFilter.class), Set.of());
        String declaration = index.generate(RecipeFilter.class.getPackageName(), List.of("RecipeFilter"), List.of(),
                Set.of(RecipeFilter.class.getName()));
        assertEquals(4, alias.namedAliases().size(), "Only the four canonical filter helpers must be exposed");
        String conditions = alias.namedAliases().stream()
                .filter(helper -> helper.aliasName().equals("$RecipeFilterConditions_"))
                .findFirst().orElseThrow().type();
        for (String helper : List.of("Expression", "Conditions", "Input", "Output")) {
            String name = "$RecipeFilter" + helper + "_";
            assertTrue(declaration.contains("export type " + name + " ="), "Missing canonical helper " + name + ": " + declaration);
            assertEquals(1, declaration.split(java.util.regex.Pattern.quote("export type " + name + " ="), -1).length - 1,
                    "Each helper must be emitted once by the canonical index pathway");
        }
        int start = declaration.indexOf("export type $RecipeFilter_ =");
        assertTrue(start >= 0, declaration);
        String main = declaration.substring(start, declaration.indexOf(';', start));
        assertTrue(main.contains("\n"), "The main alias must be readable across multiple lines: " + main);
        assertTrue(main.contains("$RecipeFilterExpression_") && main.contains("$RecipeFilterConditions_"), main);
        assertFalse(main.contains("id?:") || main.contains("output?:") || main.contains("recipeType?:"),
                "Selector fields belong in the named conditions, not an expanded main union: " + main);
        for (String key : List.of("not", "and", "or", "output", "input", "mod", "group", "id",
                "idStartsWith", "idEndsWith", "idContains", "type", "recipeType")) {
            assertTrue(conditions.contains(key + "?:"), "The named conditions must retain selector " + key + ": " + conditions);
        }
        assertTrue(declaration.contains("exact?: false") && declaration.contains("exact?: boolean"),
                "Naming must preserve the exact-output Ingredient/stack restriction: " + declaration);
        assertTrue(declaration.contains("RegistryTypes.RecipeSerializer") && declaration.contains("RegistryTypes.RecipeType"), declaration);
        assertFalse(declaration.contains("[key: string]: any"), "Naming must not add a permissive conditions index signature");
    }

    @Test void canonicalAdapterDeclaresNativeSelfRegexAndTypedContextPredicate() {
        var alias = actualAlias();
        String union = alias.declarations("");
        assertTrue(alias.union().contains("$RecipeFilter |"), "Native filters must remain valid input values: " + union);
        assertTrue(union.contains("RegExp"), "Raw guest regular expressions must be declared: " + union);
        assertTrue(union.contains("$RecipeMatchContext") && union.contains("=> boolean"),
                "The canonical adapter must declare its actual native predicate context and boolean return: " + union);
        assertTrue(alias.referenceFqns().contains("com.tkisor.nekojs.api.recipe.RecipeMatchContext"),
                "The callback context must participate in declaration closure, not an opaque raw string");
        assertFalse(union.contains("RecipeId"), "Recipe IDs have no backing special registry");
        assertFalse(union.contains("Rhino") || union.contains("KubeRecipe"));
    }

    @Test void canonicalAdapterSeparatesSerializerAndNativeRecipeTypeAndAdaptedIngredientStackInputs() {
        var alias = actualAlias();
        String union = alias.declarations("");
        assertTrue(union.contains("type?:") && union.contains("RegistryTypes.RecipeSerializer"),
                "The type selector must complete recipe serializer IDs: " + union);
        assertTrue(union.contains("recipeType?:") && union.contains("RegistryTypes.RecipeType"),
                "Native recipe type matching needs its explicit migration key: " + union);
        assertTrue(union.contains("$Ingredient_") && union.contains("$ItemStack_"),
                "Ingredient and stack filter fields must retain the actual adapters' input aliases: " + union);
        assertTrue(union.contains("$RecipeFilter_[]"), "Recursive filter arrays must retain the input alias: " + union);
        assertTrue(alias.usesRegistry());
    }
}
