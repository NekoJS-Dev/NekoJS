package com.tkisor.nekojs.probe;

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
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 内置 TypeScript probe backend：把共享收集到的类 + catalog 渲染成 {@code .d.ts}。
 *
 * <p>Phase 1：从旧 {@code ProbeOrchestrator} 逐字搬迁全部发射逻辑，仅改三处入口：
 * <ol>
 *   <li>种子类来自 {@link ProbeContext#collectedClasses()}（共享 BFS 结果），不再自带 BFS</li>
 *   <li>包过滤改用 {@link ProbeConfig#isRelevantClass(String, java.util.List)}（配置驱动）</li>
 *   <li>输出目录 = {@link ProbeContext#languageDir()}（每 backend 自管 staging/swap）</li>
 * </ol>
 * 故 TS 产物**内容**与重构前字节一致（仅目录由 {@code .neko_probe/} 迁到 {@code .neko_probe/typescript/}）。
 * 外部副作用（agent 模板 + workspace 配置）移至 {@link ProbeCoordinator} 统一执行一次。
 */
public final class TypeScriptProbeBackend implements ProbeBackend {

    private final TypeAliasRegistry aliasRegistry = new TypeAliasRegistry();
    private final TypeConverter typeConverter = new TypeConverter(aliasRegistry);
    private final ClassDeclGenerator classDeclGenerator = new ClassDeclGenerator(typeConverter);
    private final AdapterAliasGenerator adapterAliasGenerator = new AdapterAliasGenerator(aliasRegistry);
    private final IndexFileGenerator indexFileGenerator = new IndexFileGenerator(classDeclGenerator, typeConverter, adapterAliasGenerator);
    private final EventDeclarationGenerator eventGenerator = new EventDeclarationGenerator(typeConverter, adapterAliasGenerator);
    private final BindingDeclarationGenerator bindingGenerator = new BindingDeclarationGenerator();
    private final RecipeEventDeclarationGenerator recipeEventGenerator = new RecipeEventDeclarationGenerator(aliasRegistry);
    private final ManagedApiDeclarationGenerator managedDeclGenerator = new ManagedApiDeclarationGenerator();

    // IR 渲染器（Phase 2.6）：仅用于重渲染被 modify_type 编辑过的类；未编辑的类仍走旧 ClassDeclGenerator
    private final TypeScriptClassRenderer tsClassRenderer = new TypeScriptClassRenderer(typeConverter);

    // RecipeEventJS.recipes getter 覆盖的单一数据源：旧 ClassDeclGenerator 路径与 IR 重渲染路径
    // 都从这里取字面量，避免两处各写一遍导致漂移。
    private static final String RECIPE_EVENT_RECIPES_GETTER = "recipes";
    private static final String RECIPE_EVENT_RECIPES_RETURN_TYPE = "DocumentedRecipes";
    private static final String RECIPE_EVENT_RECIPES_IMPORT =
            "import { DocumentedRecipes } from \"@side-only/server/events/recipes\";";

    {
        // RecipeEventJS.recipes getter 由 RecipeEventDeclarationGenerator 提供，
        // 让它返回 DocumentedRecipes（来自 @side-only/server/events/recipes）
        try {
            Class<?> recipeEventClass = Class.forName(
                    "com.tkisor.nekojs.wrapper.event.server.RecipeEventJS",
                    false,
                    Thread.currentThread().getContextClassLoader());
            // 旧路径：ClassDeclGenerator 直接渲染时命中覆盖
            classDeclGenerator.overrideGetter(
                    recipeEventClass,
                    RECIPE_EVENT_RECIPES_GETTER,
                    RECIPE_EVENT_RECIPES_RETURN_TYPE,
                    RECIPE_EVENT_RECIPES_IMPORT
            );
            // IR 重渲染路径：modify_type/assign_type 把类标记 mutated 后改走 renderer，覆盖必须同样生效，
            // 否则 recipes getter 会退化为原始返回类型。
            tsClassRenderer.overrideGetter(
                    recipeEventClass,
                    RECIPE_EVENT_RECIPES_GETTER,
                    RECIPE_EVENT_RECIPES_RETURN_TYPE,
                    RECIPE_EVENT_RECIPES_IMPORT
            );
        } catch (ClassNotFoundException ignored) {
        }
    }

    @Override
    public String languageId() {
        return "typescript";
    }

    @Override
    public String name() {
        return "builtin";
    }

    @Override
    public ProbeGenerator.GenerateResult generate(ProbeContext ctx) {
        long start = System.currentTimeMillis();
        int filesGenerated = 0;
        NekoScriptCatalogSnapshot snapshot = ctx.snapshot();
        Path outputDir = ctx.languageDir();
        List<String> platformPkgs = ProbeConfigLoader.platformDefaultPackages();

        // 原子输出：生成到同级 staging 目录，全部成功后整体替换 outputDir。
        // 失败则丢弃 staging，旧 outputDir 完整保留，避免「先删后生成」中途失败丢声明。
        Path staging = outputDir.resolveSibling(outputDir.getFileName().toString() + ".staging");
        Path backup = outputDir.resolveSibling(outputDir.getFileName().toString() + ".old");

        try {
            // 恢复上次进程崩溃可能残留的中间态：丢弃半成品 staging；若 outputDir 缺失但有 backup，恢复 backup。
            deleteRecursive(staging);
            if (!Files.exists(outputDir) && Files.exists(backup)) {
                Files.move(backup, outputDir);
            }
            Files.createDirectories(staging);

            try {
                // 1. 种子类来自共享收集（ProbeCoordinator 的 BFS 结果）
                Set<String> classesToGenerate = new LinkedHashSet<>();
                for (Class<?> c : ctx.collectedClasses()) {
                    classesToGenerate.add(c.getName());
                }

                // 强制纳入相关前缀内的适配器目标类，确保其包模块与输入别名（$Foo_）一定生成。
                // 跳过不在相关性前缀内的目标（如 gson 的 JsonObject）：它们的依赖图无法被干净探测，
                // 强行生成会引入悬空类型引用；这类目标若被引用，按既有行为保持原样。
                for (AdapterCatalogEntry adapter : snapshot.adapters()) {
                    String name = adapter.targetType().getName();
                    if (ctx.config().isRelevantClass(name, platformPkgs)) {
                        classesToGenerate.add(name);
                    }
                }

                // 准备适配器输入别名：仅处理会被实际生成的目标，填充 TypeAliasRegistry（放宽引用该类型的
                // 方法参数）+ 别名表（就近发声明）。必须在 pregenerateDeclarations 之前，因为参数渲染依赖已注册的别名
                adapterAliasGenerator.prepare(snapshot.adapters(), classesToGenerate);

                // 别名引用的跨包 host 类型（如 NekoId、Item）也需生成声明，否则别名里的 $NekoId 等会悬空
                for (String host : adapterAliasGenerator.hostImports()) {
                    if (ctx.config().isRelevantClass(host, platformPkgs)) {
                        classesToGenerate.add(host);
                    }
                }

                NekoJS.LOGGER.info("Probe [typescript]: {} classes to generate", classesToGenerate.size());

                // 2. 构建包树
                PackageTree tree = new PackageTree();
                for (String fqn : classesToGenerate) {
                    tree.addClass(fqn);
                }

                // 3. 并行预生成所有类声明
                pregenerateDeclarations(classesToGenerate);

                // 3b. 应用共享 IR 中被 modify_type 编辑过的类（Strategy B）：
                //     ctx.ir() 由 ProbeCoordinator 在「有监听器或有 backend 需要 IR」时构建并触发事件；
                //     为 null（默认 /nekojs probe）则完全走旧 ClassDeclGenerator 路径，TS 产物零回归。
                applyMutatedOverrides(ctx.ir());

                // 4. 生成 @package Java 类型声明（写入 staging）
                filesGenerated += generatePackageDeclarations(tree, staging);

                // 4. 生成事件声明
                filesGenerated += generateEventDeclarations(snapshot, staging);

                // 5. 生成 recipe 事件声明（event.recipes.<namespace>.<type>(...)）
                filesGenerated += generateRecipeEventDeclarations(snapshot, staging);

                // 6. 生成绑定声明
                filesGenerated += generateBindingDeclarations(snapshot, staging);

                // 6. 生成 @side-only/{side}/index.d.ts（重新导出 events 和 bindings）
                filesGenerated += generateSideRootIndexes(staging);

                // 7. 生成 @special 注册表字面量类型
                filesGenerated += generateSpecialTypes(snapshot, staging);

                // 8. 生成 @manual 手动声明（node:xxx 模块、helper 类型、插件模块）
                filesGenerated += generateManualDeclarations(snapshot, staging);

                // 9. 生成 managed declarations
                filesGenerated += generateManagedDeclarations(snapshot, staging);

                // 10. 生成 probe.add_global 全局声明（@manual/globals.d.ts，已被 jsconfig include 覆盖）
                filesGenerated += generateGlobalsDeclarations(ctx.overrides().globals(), staging);

                // 全部生成成功：staging 整体替换 outputDir（外部副作用由 ProbeCoordinator 统一执行）
                commitProbeOutput(staging, outputDir, backup);

                long duration = System.currentTimeMillis() - start;
                NekoJS.LOGGER.info("Probe [typescript] generated: {} files in {}ms", filesGenerated, duration);
                return ProbeGenerator.GenerateResult.success(filesGenerated, duration);

            } catch (Exception genFailure) {
                // 生成中途失败：丢弃 staging 半成品，旧 outputDir 完整保留
                deleteRecursive(staging);
                throw genFailure;
            }

        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe [typescript] generation failed", e);
            return ProbeGenerator.GenerateResult.failure(e.getMessage());
        } finally {
            // 清理生成过程中积累的缓存，释放内存
            indexFileGenerator.clearCaches();
            typeConverter.clearCaches();
        }
    }

    /**
     * staging 整体替换 outputDir：旧 outputDir → backup，staging → outputDir，删 backup。
     * swap 中途失败时尝试把 backup 恢复为 outputDir。
     */
    private void commitProbeOutput(Path staging, Path outputDir, Path backup) throws IOException {
        deleteRecursive(backup);
        if (Files.exists(outputDir)) {
            Files.move(outputDir, backup);
        }
        try {
            Files.move(staging, outputDir);
        } catch (IOException e) {
            if (!Files.exists(outputDir) && Files.exists(backup)) {
                try {
                    Files.move(backup, outputDir);
                } catch (IOException ignored) {
                }
            }
            throw e;
        }
        deleteRecursive(backup);
    }

    /** 递归删除目录及其内容（深度优先逆序，先文件后目录）。 */
    private static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(java.io.File::delete);
    }

    /**
     * 对共享 IR 中被 {@code probe.modify_type} 触及（{@code mutated}）的类，重新渲染并覆盖
     * {@link IndexFileGenerator} 的声明缓存。{@code ir} 为 null（无监听器且无 backend 需要 IR）时
     * 直接返回，{@link #generate} 全程走旧 {@code ClassDeclGenerator} 路径（Strategy B，零回归）。
     *
     * <p>IR 构建 + 事件触发由 {@link ProbeCoordinator} 共享层统一完成（Phase 3），TS 与 Python backend
     * 复用同一份（已编辑的）IR。
     */
    private void applyMutatedOverrides(List<TypeDecl> ir) {
        if (ir == null) return;
        for (TypeDecl d : ir) {
            if (!d.mutated) continue;
            if (d.hidden) {
                indexFileGenerator.overrideDeclaration(d.fqn, "", Set.of());
                continue;
            }
            String rendered = tsClassRenderer.render(d);
            Set<String> extraImports = ProbeModifyTypeEventJS.collectEditedSymbolFqns(d, pkgOf(d.fqn));
            indexFileGenerator.overrideDeclaration(d.fqn, rendered, extraImports);
        }
    }

    private static String pkgOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(0, dot) : "";
    }

    /**
     * 生成 {@code probe.add_global} 收集的全局声明到 {@code @manual/globals.d.ts}。
     * {@code @manual} 目录下的 .d.ts 已被各脚本目录的 jsconfig include 覆盖，故全局 declare const 生效。
     */
    private int generateGlobalsDeclarations(List<GlobalDecl> globals, Path outputDir) throws IOException {
        if (globals == null || globals.isEmpty()) return 0;
        Path manualDir = outputDir.resolve("@manual");
        Files.createDirectories(manualDir);
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by NekoJS probe — global declarations from probe.add_global.\n");
        sb.append("// Do not edit; regenerate with `/nekojs probe`.\n\n");
        for (GlobalDecl g : globals) {
            sb.append("declare const ").append(g.name()).append(": ")
              .append(TypeScriptClassRenderer.renderTypeRef(g.type())).append(";\n");
        }
        Files.writeString(manualDir.resolve("globals.d.ts"), sb.toString());
        return 1;
    }

    // ------------------------------------------------------------------
    //  编辑器配置注入（Phase 4）
    // ------------------------------------------------------------------

    /**
     * 把 {@code java:*}、{@code @side-only/<env>}、{@code @special/*} 路径别名合并进每个脚本目录的
     * jsconfig.json（以及 {@code .neko_probe/jsconfig.json}），指向本 backend 真实的输出目录
     * （{@code .neko_probe/typescript/...}）。幂等合并：probe 拥有的键替换为最新值，用户自定义键保留。
     *
     * <p>修复既有 stale：WorkspaceGenerator 预写的 paths 指向 {@code .neko_probe/@package}（Phase 1 前），
     * 而产物实际在 {@code .neko_probe/typescript/@package}；本合并把 probe 拥有的键校正到正确位置。
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

        // include/typeRoots：WorkspaceGenerator.buildConfigForEnv 预写的值指向旧 .neko_probe/@package，
        // 校正 base 为 tsOut（glob 写法照抄 WorkspaceGenerator，仅 base 换成 tsOut）。
        List<String> includes = List.of(
                rel + "/@package/**/*.d.ts",
                rel + "/@manual/**/*.d.ts",
                sideBase + "/**/*.d.ts",
                rel + "/@nekojs/managed/" + env.name + "/**/*.d.ts");
        List<String> typeRoots = List.of(rel + "/@package", "../node_modules/@types");
        c.mergeJsConfigIncludes(scriptDir.resolve("jsconfig.json"), includes);
        c.mergeJsConfigTypeRoots(scriptDir.resolve("jsconfig.json"), typeRoots);
    }

    private static void contributeProbeDirJsconfig(EditorConfigContributor c, Path probeDir, Path tsOut) {
        String rel = FileEditorConfigContributor.relativePosix(probeDir, tsOut); // "typescript"
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
    }

    /**
     * 并行预生成所有类声明（重反射工作放到线程池）。
     */
    private void pregenerateDeclarations(Set<String> classNames) {
        int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), 8);
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        List<Future<?>> futures = new ArrayList<>();
        for (String fqn : classNames) {
            futures.add(executor.submit(() -> {
                try {
                    // Use initialize=false to avoid triggering <clinit> which can
                    // crash on non-OpenGL threads (e.g. client rendering classes)
                    Class<?> clazz = Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
                    indexFileGenerator.pregenerateClass(clazz);
                } catch (Throwable t) {
                    // Log failures for NekoJS platform classes so we can debug missing types
                    if (fqn.startsWith("com.tkisor.nekojs.")) {
                        NekoJS.LOGGER.debug("Probe: failed to pregenerate class {}: {}", fqn, t.toString());
                    }
                }
            }));
        }
        for (var f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                // 恢复中断标志，让上层感知
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                // worker 线程的真实失败（非 NekoJS 类的 classloader 异常等）不应静默丢失
                NekoJS.LOGGER.debug("Probe: class pregeneration failed", e.getCause());
            }
        }
        executor.shutdown();
    }

    private int generatePackageDeclarations(PackageTree tree, Path outputDir) throws IOException {
        Path packageDir = outputDir.resolve("@package");
        Files.createDirectories(packageDir);

        List<PackageTree.Node> nodes = tree.traversePackages();

        // 收集所有类的简单名（用于同名冲突检测）
        Set<String> allClassNames = new LinkedHashSet<>();
        for (PackageTree.Node node : nodes) {
            allClassNames.addAll(node.classes);
        }

        // 1. 批量创建所有目录
        for (PackageTree.Node node : nodes) {
            Files.createDirectories(packageDir.resolve(node.getPackagePath()));
        }

        // 2. 并行生成内容 + 写入文件
        List<PackageTree.Node> nodeList = new ArrayList<>(nodes);
        int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), 8);
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        CompletionService<Void> completion =
                new ExecutorCompletionService<>(executor);

        int taskCount = 0;
        for (PackageTree.Node node : nodeList) {
            completion.submit(() -> {
                String packageName = node.getPackageName();
                List<String> subpackages = node.getSubPackageNames();
                String content = indexFileGenerator.generate(packageName, node.classes, subpackages, allClassNames);
                Files.writeString(packageDir.resolve(node.getPackagePath()).resolve("index.d.ts"), content);
                return null;
            });
            taskCount++;
        }

        // 等待所有任务完成
        for (int i = 0; i < taskCount; i++) {
            try {
                completion.take().get();
            } catch (ExecutionException e) {
                NekoJS.LOGGER.debug("Package generation task failed (non-fatal)", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        executor.shutdown();

        // 3. 生成根 index.d.ts
        List<String> topPackages = new ArrayList<>();
        for (PackageTree.Node child : tree.getRoot().children.values()) {
            topPackages.add(child.name);
        }
        Files.writeString(packageDir.resolve("index.d.ts"), indexFileGenerator.generateRoot(topPackages));

        return taskCount + 1;
    }

    private int generateEventDeclarations(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        int count = 0;
        Path sideOnlyDir = outputDir.resolve("@side-only");

        for (ScriptType type : ScriptType.all()) {
            List<EventCatalogEntry> events = snapshot.events().stream()
                    .filter(e -> e.scriptType().test(type))
                    .toList();
            if (events.isEmpty()) continue;

            Path dir = sideOnlyDir.resolve(type.name).resolve("events");
            Files.createDirectories(dir);

            String content = eventGenerator.generate(events, type);
            Files.writeString(dir.resolve("index.d.ts"), content);
            count++;
        }

        return count;
    }

    private int generateRecipeEventDeclarations(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        List<RecipeNamespaceCatalogEntry> namespaces = snapshot.recipeNamespaces();
        if (namespaces.isEmpty()) return 0;

        // 只为 server 脚本生成 recipe 声明
        Path dir = outputDir.resolve("@side-only").resolve("server").resolve("events").resolve("recipes");
        Files.createDirectories(dir);

        String content = recipeEventGenerator.generate(namespaces, ScriptType.SERVER);
        Files.writeString(dir.resolve("index.d.ts"), content);
        return 1;
    }

    private int generateBindingDeclarations(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        int count = 0;
        Path sideOnlyDir = outputDir.resolve("@side-only");

        for (ScriptType type : ScriptType.all()) {
            List<BindingCatalogEntry> bindings = snapshot.bindings().stream()
                    .filter(b -> b.scriptType() == type && b.emit())
                    .toList();
            if (bindings.isEmpty()) continue;

            // 参考 ProbeJS: @side-only/server/bindings/index.d.ts（无 GlobalBindings 子目录）
            Path dir = sideOnlyDir.resolve(type.name).resolve("bindings");
            Files.createDirectories(dir);

            String content = bindingGenerator.generate(bindings, type);
            Files.writeString(dir.resolve("index.d.ts"), content);
            count++;
        }

        return count;
    }

    /**
     * 为每个 side 生成根 index.d.ts，重新导出 events 和 bindings。
     * 参考 ProbeJS: @side-only/server/index.d.ts
     */
    private int generateSideRootIndexes(Path outputDir) throws IOException {
        int count = 0;
        Path sideOnlyDir = outputDir.resolve("@side-only");

        for (ScriptType type : ScriptType.all()) {
            Path sideDir = sideOnlyDir.resolve(type.name);
            if (!Files.exists(sideDir)) continue;

            StringBuilder sb = new StringBuilder();
            Path eventsDir = sideDir.resolve("events");
            Path bindingsDir = sideDir.resolve("bindings");

            if (Files.exists(eventsDir)) {
                sb.append("export * as events from \"@side-only/").append(type.name).append("/events\";\n");
            }
            if (Files.exists(bindingsDir)) {
                sb.append("export * as bindings from \"@side-only/").append(type.name).append("/bindings\";\n");
            }

            if (sb.length() > 0) {
                Files.writeString(sideDir.resolve("index.d.ts"), sb.toString());
                count++;
            }
        }

        return count;
    }

    private int generateSpecialTypes(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        List<RegistryTypeCatalogEntry> registries = snapshot.registryTypes();
        if (registries.isEmpty()) return 0;

        Path specialDir = outputDir.resolve("@special");
        Path typesDir = specialDir.resolve("types");
        Files.createDirectories(typesDir);

        StringBuilder sb = new StringBuilder();
        sb.append("declare module \"@special/types\" {\n");
        sb.append("    export namespace RegistryTypes {\n");

        for (RegistryTypeCatalogEntry registry : registries) {
            String typeName = registry.typeName();
            if (registry.entries().isEmpty()) {
                sb.append("        type ").append(typeName).append(" = string;\n");
            } else {
                sb.append("        type ").append(typeName).append(" = ");
                List<String> entries = registry.entries();
                for (int i = 0; i < entries.size(); i++) {
                    if (i > 0) sb.append(" | ");
                    if (i > 0 && i % 8 == 0) sb.append("\n            ");
                    sb.append("\"").append(entries.get(i).replace("\"", "\\\"")).append("\"");
                }
                sb.append(";\n");
            }
        }

        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\nexport * as types from \"@special/types\";\n");

        Files.writeString(specialDir.resolve("index.d.ts"), "export * as types from \"@special/types\";\n");
        Files.writeString(typesDir.resolve("index.d.ts"), sb.toString());
        return 2;
    }

    private int generateManualDeclarations(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        List<ManualDeclarationCatalogEntry> entries = snapshot.manualDeclarations();
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        Path manualDir = outputDir.resolve("@manual");
        Files.createDirectories(manualDir);
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
        Files.writeString(manualDir.resolve("index.d.ts"), sb.toString());
        return 1;
    }

    private int generateManagedDeclarations(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = snapshot.managedApis();
        if (managedApis.isEmpty()) return 0;

        int count = 0;
        for (ScriptType type : ScriptType.all()) {
            String content = managedDeclGenerator.generate(managedApis, type);
            if (content.isEmpty()) continue;

            Path dir = outputDir.resolve("@nekojs").resolve("managed").resolve(type.name);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("index.d.ts"), content);
            count++;
        }
        return count;
    }

    // -----------------------------------------------------------------------
    //  Inner classes
    // -----------------------------------------------------------------------

    /**
     * Java 包层级树：从类全限定名构建树结构，用于生成 package 目录。
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
