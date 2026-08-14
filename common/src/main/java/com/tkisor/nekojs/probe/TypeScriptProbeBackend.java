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
import com.tkisor.nekojs.probe.ir.TypeReflector;
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
    private final AdapterAliasGenerator adapterAliasGenerator = new AdapterAliasGenerator(aliasRegistry);
    // IR 唯一渲染路径（Phase 2.7）：所有类声明与 import 均由 TypeReflector → IR → renderer 产出，
    // 旧的 ClassDeclGenerator 直接反射渲染已删除
    private final TypeScriptClassRenderer tsClassRenderer = new TypeScriptClassRenderer(typeConverter);
    private final IndexFileGenerator indexFileGenerator = new IndexFileGenerator(tsClassRenderer, typeConverter, adapterAliasGenerator);
    private final EventDeclarationGenerator eventGenerator = new EventDeclarationGenerator(typeConverter, adapterAliasGenerator);
    private final BindingDeclarationGenerator bindingGenerator = new BindingDeclarationGenerator();
    private final RecipeEventDeclarationGenerator recipeEventGenerator = new RecipeEventDeclarationGenerator(aliasRegistry);
    private final ManagedApiDeclarationGenerator managedDeclGenerator = new ManagedApiDeclarationGenerator();

    // RecipeEventJS.recipes getter 覆盖的单一数据源：唯一渲染路径（IR renderer）从这里取字面量
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

    /**
     * TS backend 永远需要共享 IR：IR 是唯一渲染源（Phase 2.7 起），收集到的类由
     * {@link ProbeCoordinator} 统一反射为 IR，本 backend 仅补齐 TS 专属的适配器目标类/宿主类型。
     */
    @Override
    public boolean requiresIr() {
        return true;
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
                // 方法参数）+ 别名表（就近发声明）。必须在 predeclareDeclarations 之前，因为参数渲染依赖已注册的别名。
                // 每次运行先清空 TypeAliasRegistry（恢复默认表），防止上一轮注册的适配器别名在目标类缺席时泄漏。
                aliasRegistry.clear();
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

                // 3. 单次反射多产物：共享 IR + TS 专属额外类 → 逐类渲染声明与 import 集合，
                //    全部走唯一 IR 渲染路径（TypeReflector → TypeScriptClassRenderer）。
                //    ctx.ir() 由 ProbeCoordinator 构建（TS 声明 requiresIr，共享层总是反射一次）。
                //    线程池优先复用 ProbeCoordinator 的共享池（整个 probe 运行单池）；测试直连
                //    构造的 ProbeContext 没有共享池时，本 backend 自建并负责关闭。
                ExecutorService provided = ctx.sharedPool();
                ExecutorService pool = provided != null
                        ? provided
                        : Executors.newFixedThreadPool(parallelism());
                try {
                    predeclareClasses(ctx.ir(), classesToGenerate, pool);

                    // 4. 生成 @package Java 类型声明（写入 staging，复用同一线程池）
                    filesGenerated += generatePackageDeclarations(tree, staging, pool);
                } finally {
                    if (provided == null) {
                        pool.shutdown();
                    }
                }

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

    /**
     * 递归删除目录及其内容（深度优先逆序，先文件后目录）。walk 流用 try-with-resources 关闭
     * （文件句柄泄漏会锁住目录，Windows 上导致后续 move 失败）；删除失败的路径收集后 warn
     * 一次（典型为 Windows 文件锁），不抛出——调用方按自身语义决定是否视为失败。
     */
    private static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        List<Path> failed = new ArrayList<>();
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        if (!p.toFile().delete()) failed.add(p);
                    });
        }
        if (!failed.isEmpty()) {
            NekoJS.LOGGER.warn("Probe: failed to delete {} path(s) under {} (locked by another process?): {}",
                    failed.size(), dir, failed);
        }
    }

    /**
     * IR 唯一路径的并行预声明：共享 IR（{@code sharedIr}，可能为 null——测试直连的 {@code ProbeContext}
     * 构造）与 TS 专属额外类（适配器目标/宿主类型）统一为 {@link TypeDecl}，逐类渲染声明 + 计算
     * import 集合写入 {@link IndexFileGenerator} 缓存；同时收集被 {@code probe.modify_type} hide 的
     * 类供 import/别名过滤。每类只反射一次（共享 IR 已反射的不再反射），一次反射同时产出
     * 声明与 import 两个产物。
     */
    private void predeclareClasses(List<TypeDecl> sharedIr, Set<String> classNames, ExecutorService pool) {
        Map<String, TypeDecl> irByFqn = new LinkedHashMap<>();
        if (sharedIr != null) {
            for (TypeDecl d : sharedIr) {
                irByFqn.put(d.fqn, d);
            }
        }

        List<Future<?>> futures = new ArrayList<>();
        for (String fqn : classNames) {
            futures.add(pool.submit(() -> {
                try {
                    TypeDecl decl = irByFqn.get(fqn);
                    if (decl == null) {
                        // 共享 IR 缺失（共享层反射失败，或测试直连 ir=null）：本 backend 自行反射
                        Class<?> cls = Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
                        decl = new TypeReflector().reflect(cls);
                    }
                    Set<String> extraImports = decl.mutated
                            ? ProbeModifyTypeEventJS.collectEditedSymbolFqns(decl, pkgOf(decl.fqn))
                            : Set.of();
                    indexFileGenerator.predeclareClass(fqn, decl, extraImports);
                } catch (Throwable t) {
                    // 单个类失败不影响整体（旧行为一致）：NekoJS 平台类失败打 debug 便于排查缺失类型
                    if (fqn.startsWith("com.tkisor.nekojs.")) {
                        NekoJS.LOGGER.debug("Probe: failed to predeclare class {}: {}", fqn, t.toString());
                    }
                }
            }));
        }
        for (var f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                NekoJS.LOGGER.debug("Probe: class predeclaration failed", e.getCause());
            }
        }

        // hide 过滤：被 probe.modify_type hide 的类不产出声明/别名，其它类 import 中剔除其引用
        Set<String> hiddenFqns = new LinkedHashSet<>();
        for (TypeDecl d : irByFqn.values()) {
            if (d.hidden) hiddenFqns.add(d.fqn);
        }
        indexFileGenerator.setHiddenClasses(hiddenFqns);
    }

    private static int parallelism() {
        return Math.min(Runtime.getRuntime().availableProcessors(), 8);
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

    private int generatePackageDeclarations(PackageTree tree, Path outputDir, ExecutorService pool) throws IOException {
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

        // 2. 并行生成内容 + 写入文件（复用 generate() 的共享线程池，不另建池）
        List<PackageTree.Node> nodeList = new ArrayList<>(nodes);
        CompletionService<Void> completion =
                new ExecutorCompletionService<>(pool);

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

        // 等待所有任务完成：包文件写失败（磁盘满/权限等）计为硬失败——若吞掉，generate 仍会提交
        // staging 并报告成功，输出目录静默残缺。累计失败数并抛出，让 generate 走失败路径
        // （丢弃 staging、保留旧 outputDir），与其它硬失败的处理一致
        int failedTasks = 0;
        for (int i = 0; i < taskCount; i++) {
            try {
                completion.take().get();
            } catch (ExecutionException e) {
                failedTasks++;
                NekoJS.LOGGER.error("Probe [typescript] package generation task failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Package generation interrupted", e);
            }
        }
        if (failedTasks > 0) {
            throw new IOException("Package generation failed for " + failedTasks + " of " + taskCount + " packages");
        }

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
                    // 先转义反斜杠再转义引号：旧实现只转义引号，内嵌反斜杠会截断字符串字面量
                    String entry = entries.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
                    sb.append("\"").append(entry).append("\"");
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
