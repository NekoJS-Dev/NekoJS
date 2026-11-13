package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.probe.EditorConfigContributor;
import com.tkisor.nekojs.probe.EventPayloadDeclarations;
import com.tkisor.nekojs.probe.FileEditorConfigContributor;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeConfigLoader;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.ProbeOutputCommitter;
import com.tkisor.nekojs.NekoJS;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.events.GlobalDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Renders catalog and shared IR as TypeScript, completing referenced declarations after the primary scan. */
public final class TypeScriptProbeBackend implements ProbeBackend {

    private final TypeAliasRegistry aliasRegistry = new TypeAliasRegistry();
    private final AdapterAliasGenerator adapterAliasGenerator = new AdapterAliasGenerator(aliasRegistry);
    private final FunctionalInterfaceAliasGenerator functionalInterfaceAliasGenerator =
            new FunctionalInterfaceAliasGenerator(aliasRegistry);
    // IR unique rendering path (Phase 2.7): all class declarations and imports are produced by TypeReflector → IR → renderer,
    // old ClassDeclGenerator direct reflection rendering has been removed
    private final TypeScriptClassRenderer tsClassRenderer = new TypeScriptClassRenderer(aliasRegistry);
    private final IndexFileGenerator indexFileGenerator = new IndexFileGenerator(tsClassRenderer,
            adapterAliasGenerator, functionalInterfaceAliasGenerator);
    private final EventDeclarationGenerator eventGenerator = new EventDeclarationGenerator(aliasRegistry, adapterAliasGenerator);
    private final BindingDeclarationGenerator bindingGenerator = new BindingDeclarationGenerator();
    private final RecipeEventDeclarationGenerator recipeEventGenerator = new RecipeEventDeclarationGenerator(aliasRegistry);
    private final ManagedApiDeclarationGenerator managedDeclGenerator = new ManagedApiDeclarationGenerator();

    /** Render-time degradations (e.g. omitted functional aliases) surfaced through GenerateResult warnings. */
    private final Set<String> renderWarnings = new LinkedHashSet<>();

    // RecipeEventJS.recipes getter override single data source: the unique rendering path (IR renderer) takes literals from here
    private static final String RECIPE_EVENT_RECIPES_GETTER = "recipes";
    private static final String RECIPE_EVENT_RECIPES_RETURN_TYPE = "DocumentedRecipes";
    private static final String RECIPE_EVENT_RECIPES_IMPORT =
            "import { DocumentedRecipes } from \"@side-only/server/events/recipes\";";

    /** Built-in getter override applied only once (instance is a registry singleton, render called every round). */
    private volatile boolean builtinOverridesApplied;
    private boolean generatingAuthoringIndex;
    private com.google.gson.JsonObject pendingAuthoringSnapshot;

    @Override
    public String languageId() {
        return "typescript";
    }

    @Override
    public String name() {
        return "builtin";
    }

    /**
     * TS backend always needs shared IR: IR is the only rendering source (since Phase 2.7); collected classes are
     * uniformly reflected into IR by {@link ProbeCoordinator}, this backend only complements TS-specific adapter target classes/host types.
     */
    @Override
    public boolean requiresIr() {
        return true;
    }

    /** Merges render-time degradations into the result's warning channel; failures keep their message. */
    @Override
    public GenerateResult generate(ProbeContext ctx) {
        generatingAuthoringIndex = true;
        pendingAuthoringSnapshot = null;
        try {
            GenerateResult result = ProbeBackend.super.generate(ctx);
            if (result.success() && pendingAuthoringSnapshot != null) {
                try {
                    var authoringPaths = ctx.authoringPaths();
                    com.tkisor.nekojs.authoring.AuthoringSnapshotFiles.publish(
                            authoringPaths.verify(authoringPaths.snapshots()), pendingAuthoringSnapshot);
                } catch (IOException | RuntimeException failure) {
                    NekoJS.LOGGER.warn("[NEKO-1019] Authoring snapshot publication failed; the previous snapshot remains current", failure);
                    result = GenerateResult.withWarnings(result, List.of("Authoring snapshot publication failed; the previous snapshot remains current."));
                }
            }
            return result.success() && !renderWarnings.isEmpty()
                    ? GenerateResult.withWarnings(result, List.copyOf(renderWarnings)) : result;
        } finally {
            generatingAuthoringIndex = false;
            pendingAuthoringSnapshot = null;
        }
    }

    /**
     * Renders all TS artifacts to memory (relative output directory path → content). Writing to disk and atomic commit are defaultly
     * handled by {@code generate} via {@link ProbeOutputCommitter}; this method does not touch the disk.
     */
    @Override
    public Map<String, String> render(ProbeContext ctx) {
        renderWarnings.clear();
        if (generatingAuthoringIndex) indexFileGenerator.enableAuthoringIndex();
        applyBuiltinGetterOverrides();
        tsClassRenderer.setCovariantTypeParameters(fastutilResultVariance());
        NekoScriptCatalogSnapshot snapshot = ctx.snapshot();
        tsClassRenderer.setBeanAliasExclusions(snapshot.beanAliasExclusions());
        tsClassRenderer.setBeanMethodProjections(snapshot.beanMethodProjections());
        Set<String> interfaceClassBindings = new LinkedHashSet<>();
        for (BindingCatalogEntry binding : allBindings(snapshot)) {
            if (!binding.emit()) continue;
            if (binding.staticClass() && binding.javaType() != null && binding.javaType().isInterface()
                    && (binding.typeOverride() == null || binding.typeOverride().isEmpty())) {
                interfaceClassBindings.add(binding.javaType().getName());
            }
            // Delegating Bindings always intersect their helper with each target's static class value.
            for (Class<?> extra : binding.extraDocTypes()) {
                if (extra.isInterface()) interfaceClassBindings.add(extra.getName());
            }
        }
        tsClassRenderer.setInterfaceClassBindings(interfaceClassBindings);
        List<String> platformPkgs = ProbeConfigLoader.platformDefaultPackages();
        Map<String, String> files = new LinkedHashMap<>();
        if (!snapshot.moduleBindings().isEmpty() || !snapshot.moduleEvents().isEmpty()) {
            String profile = snapshot.managedApis().values().stream().map(api -> api.environmentKey())
                    .map(key -> key.loaderId() + "@" + key.loaderVersionRaw() + "/" + key.minecraftVersion() + "/" + key.dist())
                    .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
            files.put("@special/api-modules.json", com.tkisor.nekojs.api.catalog.ApiModuleBaseline
                    .capture(profile.isEmpty() ? "unresolved" : profile, snapshot).toJson());
        }
        try {
            // 1. Seed classes from shared collection (ProbeCoordinator BFS results)
            Set<String> classesToGenerate = new LinkedHashSet<>();
            for (Class<?> c : ctx.collectedClasses()) {
                classesToGenerate.add(c.getName());
            }

            // Relevant adapter targets remain roots for their package modules and input aliases.
            // Targets outside the primary prefixes are discovered when an emitted signature references them.
            for (AdapterCatalogEntry adapter : snapshot.adapters()) {
                String name = adapter.targetType().getName();
                if (ctx.config().isRelevantClass(name, platformPkgs)) {
                    classesToGenerate.add(name);
                }
            }

            // The primary scan selects roots; emitted signatures own a separate declaration dependency closure.
            AuthoredClassDeclarations authored = AuthoredClassDeclarations.prepare(ctx, classesToGenerate);
            aliasRegistry.clear();
            Set<String> hiddenFqns = hiddenClasses(ctx.ir());
            indexFileGenerator.setHiddenClasses(hiddenFqns);
            TypeScriptDeclarationClosure declarations = new TypeScriptDeclarationClosure(ctx, indexFileGenerator,
                    reflectionLoader(ctx));
            ManualDeclarationReferences manualReferences = ManualDeclarationReferences.prepare(
                    ctx, classesToGenerate, declarations);
            for (String dependency : bindingGenerator.referencedClasses(allBindings(snapshot).stream()
                    .filter(BindingCatalogEntry::emit).toList())) {
                declarations.includeReference(classesToGenerate, dependency);
            }
            for (String dependency : eventGenerator.referencedClasses(java.util.stream.Stream.concat(snapshot.events().stream(),
                    snapshot.moduleEvents().stream().map(com.tkisor.nekojs.api.catalog.ModuleEventCatalogEntry::event)).toList(), snapshot.registryBuilderSurfaces())) {
                declarations.includeReference(classesToGenerate, dependency);
            }
            for (String dependency : recipeEventGenerator.referencedClasses(snapshot.recipeNamespaces())) {
                declarations.includeReference(classesToGenerate, dependency);
            }
            boolean closureChanged;
            do {
                int previousSize = classesToGenerate.size();
                Set<String> aliasEligibleClasses = new LinkedHashSet<>(classesToGenerate);
                aliasEligibleClasses.removeAll(hiddenFqns);
                adapterAliasGenerator.prepare(snapshot.adapters(), aliasEligibleClasses, ctx.ir());
                for (String host : adapterAliasGenerator.hostImports()) {
                    declarations.includeReference(classesToGenerate, host);
                }

                functionalInterfaceAliasGenerator.prepare(classesToGenerate, hiddenFqns, renderWarnings);
                for (String host : functionalInterfaceAliasGenerator.hostImports()) {
                    declarations.includeReference(classesToGenerate, host);
                }
                declarations.expand(classesToGenerate);
                closureChanged = classesToGenerate.size() != previousSize;
            } while (closureChanged);

            indexFileGenerator.prepareClassInheritance();

            manualReferences.validateGeneratedTypes(indexFileGenerator);

            Map<String, String> unavailable = new TreeMap<>(declarations.unavailable());
            Map<String, List<String>> incompleteMembers = new TreeMap<>(declarations.incompleteMembers());
            for (var entry : incompleteMembers.entrySet()) {
                renderWarnings.add("[NEKO-4066] Probe declaration retained: class="
                        + entry.getKey() + "; entire public method set unavailable; omissions="
                        + String.join("; ", entry.getValue()));
            }
            Map<String, Object> dependencyReport = new LinkedHashMap<>();
            dependencyReport.put("complete", unavailable.isEmpty() && incompleteMembers.isEmpty());
            dependencyReport.put("primaryClassCount", ctx.collectedClasses().size());
            dependencyReport.put("referencedClassCount", classesToGenerate.size());
            dependencyReport.put("unavailable", unavailable);
            dependencyReport.put("incompleteMembers", incompleteMembers);
            files.put("@special/dependencies.json", new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    .toJson(dependencyReport) + "\n");
            if (!unavailable.isEmpty()) {
                NekoJS.LOGGER.warn("[NEKO-4048] Probe TypeScript dependency closure incomplete: {} unavailable types; "
                        + "declarations may contain unresolved references; see @special/dependencies.json", unavailable.size());
            }

            NekoJS.LOGGER.info("Probe [typescript]: {} classes to generate", classesToGenerate.size());

            // 2. Build package tree
            PackageTree tree = new PackageTree();
            for (String fqn : classesToGenerate) {
                if (indexFileGenerator.hasDeclaration(fqn) || hiddenFqns.contains(fqn)) tree.addClass(fqn);
            }

            // Package rendering reuses cached declarations and the coordinator's pool when available.
            ExecutorService provided = ctx.sharedPool();
            ExecutorService pool = provided != null
                    ? provided
                    : Executors.newFixedThreadPool(parallelism());
            try {
                authored.validateGeneratedImports(indexFileGenerator);

                // 4. Render @package Java type declarations (parallel rendering, artifacts into memory, reuse same thread pool)
                renderPackageDeclarations(tree, files, pool);
            } finally {
                if (provided == null) {
                    pool.shutdown();
                }
            }

            // 4. Render event declarations
            renderEventDeclarations(snapshot, files);

            // 5. Render recipe event declarations (event.recipes.<namespace>.<type>(...))
            renderRecipeEventDeclarations(snapshot, files);

            // 6. Render binding declarations
            renderBindingDeclarations(snapshot, files);
            files.put("@mixin/index.d.ts", MixinDeclarationDeclarations.render(ctx.collectedClasses(), renderWarnings));

            // 6. Render @side-only/{side}/index.d.ts (re-export events and bindings)
            renderSideRootIndexes(files);

            // 7. Render @special registry literal types
            renderSpecialTypes(snapshot, files);

            // 8. Render @manual manual declarations (node:xxx modules, helper types, plugin modules)
            renderManualDeclarations(snapshot, files);
            String referenceTypes = manualReferences.render(aliasRegistry);
            if (!referenceTypes.isEmpty()) files.put("@manual/references.d.ts", referenceTypes);

            // 9. Render managed declarations
            renderManagedDeclarations(snapshot, files);

            // 9b. Render the typed Builder declarations, from the same structured input as the Python backend.
            renderRegistryBuilderSurfaces(snapshot, files);

            // 10. Render probe.add_global global declarations (@manual/globals.d.ts, covered by jsconfig include)
            renderGlobalsDeclarations(ctx.overrides().globals(), files);
            if (generatingAuthoringIndex) {
                var authoringDeclarations = indexFileGenerator.takeAuthoringDeclarations();
                // Rendering is complete; retain emitted records without keeping the reflection and render caches alive.
                indexFileGenerator.clearCaches();
                declarations.clearCaches();
                pendingAuthoringSnapshot = TypeScriptAuthoringIndex.consumeOwnedEmissions(ctx, files, authoringDeclarations);
            }

            return files;
        } finally {
            // Clear caches accumulated during generation to release memory
            indexFileGenerator.clearCaches();
        }
    }

    /**
     * Built-in getter override: RecipeEventJS.recipes getter provided by RecipeEventDeclarationGenerator,
     * makes it return DocumentedRecipes (from @side-only/server/events/recipes). Explicitly applied
     * before first render (replaces old instance initialization block—static side effects are hidden and hard to test);
     * wrapper class silently skipped when not on common classpath (unit tests).
     */
    private void applyBuiltinGetterOverrides() {
        if (builtinOverridesApplied) return;
        builtinOverridesApplied = true;
        try {
            Class<?> recipeEventClass = Class.forName(
                    "com.tkisor.nekojs.wrapper.event.server.RecipeEventJS",
                    false,
                    Thread.currentThread().getContextClassLoader());
            tsClassRenderer.overrideGetter(
                    recipeEventClass,
                    RECIPE_EVENT_RECIPES_GETTER,
                    RECIPE_EVENT_RECIPES_RETURN_TYPE,
                    RECIPE_EVENT_RECIPES_IMPORT
            );
        } catch (ClassNotFoundException e) {
            // RecipeEventJS in common module: missing indicates classpath exception; override not taking effect will silently provide wrong
            // event.recipes type
            com.tkisor.nekojs.core.error.Diagnostics.report(
                    "probe-typescript",
                    com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "RecipeEventJS not on classpath, event.recipes type override not effective (completion shape may be inaccurate)", e);
        }
    }

    private static int parallelism() {
        return Math.min(Runtime.getRuntime().availableProcessors(), 8);
    }

    private static Map<String, Set<String>> fastutilResultVariance() {
        // These 19 interfaces have covariant V in their emitted method-based shape. Explicit results
        // avoid TS 5.8's recursive variance inference through compose/andThen and callback-or-host aliases.
        // K keeps its existing inferred variance; making it invariant would narrow valid host inputs.
        Map<String, Set<String>> results = new LinkedHashMap<>();
        results.put("it.unimi.dsi.fastutil.Function", Set.of("V"));
        for (String family : List.of("Byte", "Char", "Double", "Float", "Int", "Long", "Short", "Object", "Reference")) {
            String pkg = switch (family) {
                case "Object", "Reference" -> "objects";
                default -> family.toLowerCase(Locale.ROOT) + "s";
            };
            for (String result : List.of("Object", "Reference")) {
                results.put("it.unimi.dsi.fastutil." + pkg + "." + family + "2" + result + "Function", Set.of("V"));
            }
        }
        return results;
    }

    private static ClassLoader reflectionLoader(ProbeContext context) {
        if (context.sharedPool() == null) {
            return Thread.currentThread().getContextClassLoader();
        }
        // Only loader selection runs on the supplied pool; mutable rendering state stays with this call.
        Future<ClassLoader> task = context.sharedPool().submit(() -> Thread.currentThread().getContextClassLoader());
        try {
            return task.get();
        } catch (InterruptedException interrupted) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[NEKO-4039] Probe reflection loader selection interrupted; generation rejected", interrupted);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("[NEKO-4039] Probe reflection loader selection failed; generation rejected", failure.getCause());
        }
    }

    private static Set<String> hiddenClasses(List<TypeDecl> declarations) {
        if (declarations == null || declarations.isEmpty()) return Set.of();
        Set<String> hidden = new LinkedHashSet<>();
        for (TypeDecl declaration : declarations) {
            if (declaration.hidden) hidden.add(declaration.fqn);
        }
        return hidden;
    }

    /**
     * Renders global declarations collected by {@code probe.add_global} to {@code @manual/globals.d.ts}.
     * .d.ts files in {@code @manual} are included by jsconfig in each script directory, so global declare const takes effect.
     */
    private void renderGlobalsDeclarations(List<GlobalDecl> globals, Map<String, String> files) {
        if (globals == null || globals.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by NekoJS probe — global declarations from probe.add_global.\n");
        sb.append("// Do not edit; regenerate with `/nekojs probe`.\n\n");
        for (GlobalDecl g : globals) {
            sb.append("declare const ").append(g.name()).append(": ")
              .append(TypeScriptClassRenderer.renderTypeRef(g.type())).append(";\n");
        }
        files.put("@manual/globals.d.ts", sb.toString());
    }

    // ------------------------------------------------------------------
    //  Editor config injection (Phase 4)
    // ------------------------------------------------------------------

    /**
     * Merges path aliases like {@code java:*}, {@code @side-only/<env>}, {@code @special/*} into each script directory's
     * jsconfig.json (and {@code .neko_probe/jsconfig.json}), pointing to the backend's real output directory
     * ({@code .neko_probe/typescript/...}). Idempotent merge: probe-owned keys are replaced with the latest values, user-defined keys are retained.
     *
     * <p>Fix existing stale: paths pre-written by WorkspaceGenerator pointed to {@code .neko_probe/@package} (before Phase 1),
     * whereas artifacts are actually in {@code .neko_probe/typescript/@package}; this merge corrects probe-owned keys to the right location.
     */
    @Override
    public void contributeEditorConfig(EditorConfigContributor contributor, ProbeContext ctx) {
        NekoJSPaths paths = ctx.paths();
        Path tsOut = ctx.languageDir(); // .neko_probe/typescript
        contributeScriptDirJsconfig(contributor, paths.startupScripts(), ScriptType.STARTUP, tsOut);
        contributeScriptDirJsconfig(contributor, paths.serverScripts(), ScriptType.SERVER, tsOut);
        contributeScriptDirJsconfig(contributor, paths.clientScripts(), ScriptType.CLIENT, tsOut);
        contributeScriptDirJsconfig(contributor, paths.testScripts(), ScriptType.TEST, tsOut);
        contributeProbeDirJsconfig(contributor, paths.probeDir(), tsOut);
        contributeMixinDirJsconfig(contributor, paths.root().resolve("mixins"), tsOut);

        // probe.snippets → nekojs/.vscode/nekojs.code-snippets（TS-only，merge）
        List<com.tkisor.nekojs.probe.events.Snippet> snippets = ctx.overrides().snippets();
        if (snippets != null && !snippets.isEmpty()) {
            contributor.mergeVscodeSnippets(paths.root().resolve(".vscode").resolve("nekojs.code-snippets"), snippets);
        }
    }

    private static void contributeScriptDirJsconfig(EditorConfigContributor c, Path scriptDir,
                                                    ScriptType env, Path tsOut) {
        String rel = FileEditorConfigContributor.relativePosix(scriptDir, tsOut);
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("java:*", List.of(rel + "/@package/*"));
        String sideBase = rel + "/@side-only/" + env.name;
        aliases.put("@side-only/" + env.name, List.of(sideBase));
        aliases.put("@side-only/" + env.name + "/*", List.of(sideBase + "/*"));
        aliases.put("@special", List.of(rel + "/@special"));
        aliases.put("@special/*", List.of(rel + "/@special/*"));
        c.mergeJsConfigPaths(scriptDir.resolve("jsconfig.json"), aliases);

        // include: script file globs (jsconfig projects must include scripts themselves, otherwise IDE has no completion—
        // old implementation entirely replaced with d.ts-only, causing JS files to not be in the project) + probe declaration globs.
        // mergeJsConfigIncludes is replaced entirely per probe (idempotent), script globs must be included here.
        List<String> includes = new ArrayList<>(List.of(
                "./**/*.js", "./**/*.mjs", "./**/*.cjs",
                "./**/*.ts", "./**/*.jsx", "./**/*.tsx"));
        includes.addAll(List.of(
                rel + "/@package/**/*.d.ts",
                rel + "/@manual/**/*.d.ts",
                sideBase + "/**/*.d.ts",
                rel + "/@nekojs/managed/" + env.name + "/**/*.d.ts"));
        List<String> typeRoots = List.of(rel + "/@package", "../node_modules/@types");
        c.mergeJsConfigIncludes(scriptDir.resolve("jsconfig.json"), includes);
        c.mergeJsConfigTypeRoots(scriptDir.resolve("jsconfig.json"), typeRoots);
        c.mergeJsConfigTypeAcquisition(scriptDir.resolve("jsconfig.json"), false);
    }

    private static void contributeMixinDirJsconfig(EditorConfigContributor c, Path mixinDir, Path tsOut) {
        String rel = FileEditorConfigContributor.relativePosix(mixinDir, tsOut);
        Path config = mixinDir.resolve("jsconfig.json");
        // The declaration sandbox has no Java access, ordinary globals, or runtime event subscriptions.
        c.mergeJsConfigPaths(config, Map.of("nekojs/mixin", List.of(rel + "/@mixin/index.d.ts")));
        c.mergeJsConfigIncludes(config, List.of("./**/*.js", "./**/*.mjs", "./**/*.cjs", "./**/*.ts",
                rel + "/@mixin/**/*.d.ts"));
        c.mergeJsConfigTypeRoots(config, List.of(rel + "/@mixin"));
        c.mergeJsConfigTypeAcquisition(config, false);
    }

    private static void contributeProbeDirJsconfig(EditorConfigContributor c, Path probeDir, Path tsOut) {
        // When baseUrl is not set, paths mapping values must be relative (./ prefix)—TS 4.1+ relative to tsconfig location
        String rel = "./" + FileEditorConfigContributor.relativePosix(probeDir, tsOut); // "./typescript"
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("java:*", List.of(rel + "/@package/*"));
        for (ScriptType st : ScriptType.values()) {
            String sideBase = rel + "/@side-only/" + st.name;
            aliases.put("@side-only/" + st.name, List.of(sideBase));
            aliases.put("@side-only/" + st.name + "/*", List.of(sideBase + "/*"));
        }
        aliases.put("@special", List.of(rel + "/@special"));
        aliases.put("@special/*", List.of(rel + "/@special/*"));
        c.mergeJsConfigPaths(probeDir.resolve("jsconfig.json"), aliases);
        c.mergeJsConfigTypeAcquisition(probeDir.resolve("jsconfig.json"), false);
    }

    /**
     * Deletes managed jsconfig files for runtime scripts, MixinJS declarations, and Probe,
     * plus the managed VS Code snippets file. Shared {@code .vscode/settings.json} remains;
     * the next contribution reconciles the backend-owned settings.
     */
    @Override
    public void resetEditorConfig(NekoJSPaths paths) {
        for (Path scriptDir : List.of(paths.startupScripts(), paths.serverScripts(),
                paths.clientScripts(), paths.testScripts(), paths.root().resolve("mixins"))) {
            deleteManagedEditorFile(scriptDir.resolve("jsconfig.json"));
        }
        deleteManagedEditorFile(paths.probeDir().resolve("jsconfig.json"));
        deleteManagedEditorFile(paths.root().resolve(".vscode").resolve("nekojs.code-snippets"));
    }

    private static void deleteManagedEditorFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            com.tkisor.nekojs.core.error.Diagnostics.report("probe-editor-config", com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "Failed to delete probe-managed editor files (" + file + "): reset may be incomplete", e);
        }
    }

    private void renderPackageDeclarations(PackageTree tree, Map<String, String> files, ExecutorService pool) {
        List<PackageTree.Node> nodes = tree.traversePackages();

        // Collect simple names of all classes (for name conflict detection)
        Set<String> allClassNames = new LinkedHashSet<>();
        for (PackageTree.Node node : nodes) {
            allClassNames.addAll(node.classes);
        }

        // Parallel render index.d.ts content for each package (reuse shared thread pool from render()), main thread collects into memory artifacts table in order
        List<PackageTree.Node> nodeList = new ArrayList<>(nodes);
        CompletionService<Map.Entry<String, String>> completion =
                new ExecutorCompletionService<>(pool);

        int taskCount = 0;
        for (PackageTree.Node node : nodeList) {
            completion.submit(() -> {
                String packageName = node.getPackageName();
                List<String> subpackages = node.getSubPackageNames();
                String content = indexFileGenerator.generate(packageName, node.classes, subpackages, allClassNames);
                return Map.entry("@package/" + node.getPackagePath() + "/index.d.ts", content);
            });
            taskCount++;
        }

        // Rendering failure is treated as a hard failure—if swallowed, the default generate would sync and report success on the incomplete result, silently missing files
        int failedTasks = 0;
        for (int i = 0; i < taskCount; i++) {
            try {
                Map.Entry<String, String> rendered = completion.take().get();
                files.put(rendered.getKey(), rendered.getValue());
            } catch (ExecutionException e) {
                failedTasks++;
                NekoJS.LOGGER.error("Probe [typescript] package generation task failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Package generation interrupted", e);
            }
        }
        if (failedTasks > 0) {
            throw new IllegalStateException("Package generation failed for " + failedTasks + " of " + taskCount + " packages");
        }

        // Root index.d.ts
        List<String> topPackages = new ArrayList<>();
        for (PackageTree.Node child : tree.getRoot().children.values()) {
            topPackages.add(child.name);
        }
        files.put("@package/index.d.ts", indexFileGenerator.generateRoot(topPackages));
    }

    private void renderEventDeclarations(NekoScriptCatalogSnapshot snapshot, Map<String, String> files) {
        for (ScriptType type : ScriptType.all()) {
            List<EventCatalogEntry> events = snapshot.events().stream()
                    .filter(e -> e.scriptType().test(type))
                    .toList();
            if (events.isEmpty()) continue;

            files.put("@side-only/" + type.name + "/events/index.d.ts", eventGenerator.generate(events, type, snapshot.registryBuilderSurfaces()));
        }
    }

    private void renderRecipeEventDeclarations(NekoScriptCatalogSnapshot snapshot, Map<String, String> files) {
        List<RecipeNamespaceCatalogEntry> namespaces = snapshot.recipeNamespaces();
        if (!RecipeEventDeclarationGenerator.hasDeclarations(namespaces, ScriptType.SERVER)) return;

        // Generate recipe declarations only for server scripts
        files.put("@side-only/server/events/recipes/index.d.ts",
                recipeEventGenerator.generate(namespaces, ScriptType.SERVER));
    }

    private static List<BindingCatalogEntry> allBindings(NekoScriptCatalogSnapshot snapshot) {
        return java.util.stream.Stream.concat(snapshot.bindings().stream(),
                snapshot.moduleBindings().stream().map(com.tkisor.nekojs.api.catalog.ModuleBindingCatalogEntry::binding)).toList();
    }

    private void renderBindingDeclarations(NekoScriptCatalogSnapshot snapshot, Map<String, String> files) {
        var mixinPlans = com.tkisor.nekojs.core.mixin.MixinCarriers.discover();
        for (ScriptType type : ScriptType.all()) {
            List<BindingCatalogEntry> bindings = snapshot.bindings().stream()
                    .filter(b -> b.scriptType() == type && b.emit())
                    .toList();
            Map<String, List<BindingCatalogEntry>> modules = new TreeMap<>();
            modules.put("nekojs/api/stable", new ArrayList<>());
            modules.put("nekojs/api/version", new ArrayList<>());
            snapshot.moduleBindings().stream().filter(entry -> entry.binding().scriptType() == type)
                    .forEach(entry -> modules.computeIfAbsent(entry.moduleId(), key -> new ArrayList<>()).add(entry.binding()));
            Map<String, List<EventCatalogEntry>> moduleEvents = new TreeMap<>();
            snapshot.moduleEvents().stream().filter(entry -> entry.scriptType() == type).forEach(entry -> {
                modules.computeIfAbsent(entry.moduleId(), key -> new ArrayList<>());
                moduleEvents.computeIfAbsent(entry.moduleId(), key -> new ArrayList<>()).add(entry.event());
            });
            Map<String, String> eventNamespaces = new TreeMap<>();
            moduleEvents.forEach((id, entries) -> eventNamespaces.put(id, eventGenerator.generateModuleNamespaces(entries)));
            files.put("@side-only/" + type.name + "/bindings/api-modules.d.ts",
                    bindingGenerator.generateModules(modules, eventNamespaces));
            String mixinDeclarations = MixinHookDeclarations.render(mixinPlans, type);
            String mixinImport = "";
            if (!mixinDeclarations.isEmpty()) {
                files.put("@side-only/" + type.name + "/bindings/mixin-hooks.d.ts", mixinDeclarations);
                mixinImport = "import './mixin-hooks';\n";
            }
            if (bindings.isEmpty()) {
                files.put("@side-only/" + type.name + "/bindings/index.d.ts", mixinImport + "import './api-modules';\nexport {};\n");
                continue;
            }

            // See ProbeJS: @side-only/server/bindings/index.d.ts (no GlobalBindings subdirectory)
            files.put("@side-only/" + type.name + "/bindings/index.d.ts", mixinImport + "import './api-modules';\n" + bindingGenerator.generate(bindings, type));
        }
    }

    /**
     * Generates root index.d.ts for each side, re-exporting events and bindings.
     * See ProbeJS: @side-only/server/index.d.ts. Side events/bindings existence
     * is inferred from existing artifact keys (equivalent to old implementation's directory existence check).
     */
    private void renderSideRootIndexes(Map<String, String> files) {
        Set<String> sides = new TreeSet<>();
        for (String key : files.keySet()) {
            if (key.startsWith("@side-only/")) {
                String rest = key.substring("@side-only/".length());
                int slash = rest.indexOf('/');
                if (slash > 0) sides.add(rest.substring(0, slash));
            }
        }
        for (String side : sides) {
            boolean hasEvents = files.keySet().stream().anyMatch(k -> k.startsWith("@side-only/" + side + "/events"));
            boolean hasBindings = files.keySet().stream().anyMatch(k -> k.startsWith("@side-only/" + side + "/bindings"));
            if (!hasEvents && !hasBindings) continue;

            StringBuilder sb = new StringBuilder();
            if (hasEvents) {
                sb.append("export * as events from \"@side-only/").append(side).append("/events\";\n");
            }
            if (hasBindings) {
                sb.append("export * as bindings from \"@side-only/").append(side).append("/bindings\";\n");
            }
            files.put("@side-only/" + side + "/index.d.ts", sb.toString());
        }
    }

    private void renderSpecialTypes(NekoScriptCatalogSnapshot snapshot, Map<String, String> files) {
        List<RegistryTypeCatalogEntry> registries = snapshot.registryTypes();
        List<String> modIds = snapshot.modIds();
        if (registries.isEmpty() && modIds.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("declare module \"@special/types\" {\n");
        sb.append("    export namespace RegistryTypes {\n");

        for (RegistryTypeCatalogEntry registry : registries) {
            appendLiteralUnion(sb, registry.typeName(), registry.entries());
            // Tag union is always emitted: syntax like "#minecraft:planks" in scripts depends on it; platforms without collected tags
            // (e.g., 1.12.2 without tag system) fallback to string, avoiding template type degradation to never, which causes false positives
            appendLiteralUnion(sb, registry.typeName() + "Tag", registry.tagEntries());
        }
        // Namespace union: for prefix completion like "@mod". Mod list and registry namespaces are unioned—mod list overrides
        // mods that are "installed but registered nothing to this registry"; entry id prefixes override namespaces created by scripts/datapacks.
        // Independent of specific registries, so emit only once
        appendLiteralUnion(sb, "Namespace", namespacesOf(modIds, registries));

        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\nexport * as types from \"@special/types\";\n");

        files.put("@special/index.d.ts", "export * as types from \"@special/types\";\n");
        files.put("@special/types/index.d.ts", sb.toString());
    }

    /**
     * Union of mod IDs and registry entry ID namespaces (e.g., {@code "minecraft:stone"} → {@code "minecraft"}).
     *
     * <p>Entries without {@code ':'} are counted as-is (a few registry IDs in 1.12.2 lack namespaces); TreeSet guarantees
     * deterministic order across JVMs.
     */
    private static List<String> namespacesOf(List<String> modIds, List<RegistryTypeCatalogEntry> registries) {
        Set<String> namespaces = new TreeSet<>(modIds);
        for (RegistryTypeCatalogEntry registry : registries) {
            for (String entry : registry.entries()) {
                if (entry == null || entry.isBlank()) continue;
                int colon = entry.indexOf(':');
                namespaces.add(colon < 0 ? entry : entry.substring(0, colon));
            }
        }
        namespaces.remove("");
        return List.copyOf(namespaces);
    }

    /** {@code type Name = "a" | "b";}; fallback to {@code type Name = string;} when entries are empty. */
    private static void appendLiteralUnion(StringBuilder sb, String typeName, List<String> entries) {
        if (entries.isEmpty()) {
            sb.append("        type ").append(typeName).append(" = string;\n");
            return;
        }
        sb.append("        type ").append(typeName).append(" = ");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(" | ");
            if (i > 0 && i % 8 == 0) sb.append("\n            ");
            // Escape backslash before quotes: old implementation only escaped quotes, nested backslashes would truncate string literals
            String entry = entries.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\"").append(entry).append("\"");
        }
        sb.append(";\n");
    }

    /**
     * Typed Builder declarations for {@code @registry-builders/index.d.ts}. The input is
     * {@link com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry}, derived by contract reflection
     * over the builder classes in the version tree. The Python backend renders
     * {@code nekojs/_registry_builders/__init__.pyi} from the <b>same</b> entry list, so members mean
     * the same thing on both sides.
     *
     * <p>Event dependencies use only the explicitly associated entries from the same facts. The complete
     * legacy file remains available without merging unrelated globals into the default events.
     */
    private void renderRegistryBuilderSurfaces(NekoScriptCatalogSnapshot snapshot, Map<String, String> files) {
        var entries = snapshot.registryBuilderSurfaces();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        files.put("@registry-builders/index.d.ts", RegistryBuilderTsRenderer.render(entries));
        var payloads = EventPayloadDeclarations.resolve(snapshot.events(), entries);
        var eventBuilders = entries.stream().filter(entry -> entry.eventPayload() != null
                && payloads.containsKey(entry.eventPayload().eventType())).distinct().toList();
        if (!eventBuilders.isEmpty()) {
            files.put("@event-builders/index.d.ts", RegistryBuilderTsRenderer.renderEventDependencies(eventBuilders));
        }
    }

    private void renderManualDeclarations(NekoScriptCatalogSnapshot snapshot, Map<String, String> files) {
        List<ManualDeclarationCatalogEntry> entries = snapshot.manualDeclarations();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by NekoJS probe. Manual declarations: node:xxx modules, helper types, plugin modules.\n");
        sb.append("// Do not edit; regenerate with /nekojs probe.\n\n");
        for (ManualDeclarationCatalogEntry entry : entries) {
            String decl = entry.declaration();
            if (decl == null || decl.isBlank()) {
                continue;
            }
            sb.append("// ").append(entry.id()).append('\n');
            sb.append(decl.trim()).append("\n\n");
        }
        files.put("@manual/index.d.ts", sb.toString());
    }

    private void renderManagedDeclarations(NekoScriptCatalogSnapshot snapshot, Map<String, String> files) {
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = snapshot.managedApis();
        if (managedApis.isEmpty()) return;

        for (ScriptType type : ScriptType.all()) {
            String content = managedDeclGenerator.generate(managedApis, type);
            if (content.isEmpty()) continue;

            files.put("@nekojs/managed/" + type.name + "/index.d.ts", content);
        }
    }

    // -----------------------------------------------------------------------
    //  Inner classes
    // -----------------------------------------------------------------------

    /**
     * Java package hierarchy tree: built from class fully qualified names to generate package directories.
     */
    static final class PackageTree {
        private final Node root = new Node("", null);

        void addClass(String fullyQualifiedName) {
            String[] parts = fullyQualifiedName.split("\\.");
            Node current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                final Node parent = current;
                current = current.children.computeIfAbsent(parts[i], k -> new Node(k, parent));
            }
            current.classes.add(parts[parts.length - 1]);
        }

        Node getRoot() {
            return root;
        }

        List<Node> traversePackages() {
            List<Node> result = new ArrayList<>();
            Deque<Node> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                Node node = stack.pop();
                if (node != root) {
                    result.add(node);
                }
                List<Node> childList = new ArrayList<>(node.children.values());
                Collections.reverse(childList);
                for (Node child : childList) {
                    stack.push(child);
                }
            }
            return result;
        }

        static final class Node {
            final String name;
            final Node parent;
            final Map<String, Node> children = new LinkedHashMap<>();
            final List<String> classes = new ArrayList<>();

            Node(String name, Node parent) {
                this.name = name;
                this.parent = parent;
            }

            String getPackageName() {
                List<String> path = new ArrayList<>();
                Node current = this;
                while (current != null && !current.name.isEmpty()) {
                    path.add(current.name);
                    current = current.parent;
                }
                Collections.reverse(path);
                return String.join(".", path);
            }

            String getPackagePath() {
                return getPackageName().replace('.', '/');
            }

            List<String> getSubPackageNames() {
                List<String> result = new ArrayList<>();
                for (Node child : children.values()) {
                    if (!child.children.isEmpty() || !child.classes.isEmpty()) {
                        result.add(child.name);
                    }
                }
                return result;
            }
        }
    }
}
