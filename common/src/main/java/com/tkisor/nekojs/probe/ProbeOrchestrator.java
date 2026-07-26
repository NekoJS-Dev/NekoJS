package com.tkisor.nekojs.probe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.CurrentSurfaceReport;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import com.tkisor.nekojs.api.ScriptType;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * 探针编排器：协调所有生成器，管理完整的探针生成流程。
 */
public final class ProbeOrchestrator implements ProbeGenerator {
    private final TypeAliasRegistry aliasRegistry = new TypeAliasRegistry();
    private final TypeConverter typeConverter = new TypeConverter(aliasRegistry);
    private final ClassDeclGenerator classDeclGenerator = new ClassDeclGenerator(typeConverter);
    private final AdapterAliasGenerator adapterAliasGenerator = new AdapterAliasGenerator(aliasRegistry);
    private final IndexFileGenerator indexFileGenerator = new IndexFileGenerator(classDeclGenerator, typeConverter, adapterAliasGenerator);
    private final EventDeclarationGenerator eventGenerator = new EventDeclarationGenerator(typeConverter, adapterAliasGenerator);
    private final BindingDeclarationGenerator bindingGenerator = new BindingDeclarationGenerator();
    private final RecipeEventDeclarationGenerator recipeEventGenerator = new RecipeEventDeclarationGenerator(aliasRegistry);
    private final ManagedApiDeclarationGenerator managedDeclGenerator = new ManagedApiDeclarationGenerator();
    private final ProbeExternalArtifacts externalArtifacts;

    public ProbeOrchestrator() {
        this(ProbeExternalArtifacts.DEFAULT);
    }

    ProbeOrchestrator(ProbeExternalArtifacts externalArtifacts) {
        this.externalArtifacts = externalArtifacts;
    }

    {
        // RecipeEventJS.recipes getter 由 RecipeEventDeclarationGenerator 提供，
        // 让它返回 DocumentedRecipes（来自 @side-only/server/events/recipes）
        try {
            Class<?> recipeEventClass = Class.forName(
                    "com.tkisor.nekojs.wrapper.event.server.RecipeEventJS",
                    false,
                    Thread.currentThread().getContextClassLoader());
            classDeclGenerator.overrideGetter(
                    recipeEventClass,
                    "recipes",
                    "DocumentedRecipes",
                    "import { DocumentedRecipes } from \"@side-only/server/events/recipes\";"
            );
        } catch (ClassNotFoundException ignored) {
        }
    }

    @Override
    public String name() {
        return "NekoJS Builtin Probe";
    }

    @Override
    public GenerateResult generate(NekoScriptCatalogSnapshot snapshot, Path outputDir) {
        long start = System.currentTimeMillis();
        int filesGenerated = 0;

        // 原子输出：生成到同级 staging 目录，全部成功后整体替换 outputDir。
        // 失败则丢弃 staging，旧 outputDir 完整保留，避免旧实现「先删后生成」中途失败丢声明。
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
                // 1. 收集需要生成声明的类
                Set<String> classesToGenerate = collectClasses(snapshot);

                // 强制纳入相关前缀内的适配器目标类，确保其包模块与输入别名（$Foo_）一定生成。
                // 跳过不在相关性前缀内的目标（如 gson 的 JsonObject）：它们的依赖图无法被干净探测，
                // 强行生成会引入悬空类型引用；这类目标若被引用，按既有行为保持原样。
                for (AdapterCatalogEntry adapter : snapshot.adapters()) {
                    String name = adapter.targetType().getName();
                    if (isRelevantClass(name)) {
                        classesToGenerate.add(name);
                    }
                }

                // 准备适配器输入别名：仅处理会被实际生成的目标，填充 TypeAliasRegistry（放宽引用该类型的
                // 方法参数）+ 别名表（就近发声明）。必须在 pregenerateDeclarations 之前，因为参数渲染依赖已注册的别名
                adapterAliasGenerator.prepare(snapshot.adapters(), classesToGenerate);

                // 别名引用的跨包 host 类型（如 NekoId、Item）也需生成声明，否则别名里的 $NekoId 等会悬空
                for (String host : adapterAliasGenerator.hostImports()) {
                    if (isRelevantClass(host)) {
                        classesToGenerate.add(host);
                    }
                }

                NekoJS.LOGGER.info("Probe: {} classes to generate", classesToGenerate.size());

                // 2. 构建包树
                PackageTree tree = new PackageTree();
                for (String fqn : classesToGenerate) {
                    tree.addClass(fqn);
                }

                // 3. 并行预生成所有类声明
                pregenerateDeclarations(classesToGenerate);

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

                // 10. 生成 api-manifest.json 和 current-surface.json
                filesGenerated += generateManifestAndSurface(snapshot, staging);

                // 11-12. 外部副作用（.github/agents 模板 + workspace 配置）
                externalArtifacts.generate(outputDir);

                // 全部生成成功：staging 整体替换 outputDir
                commitProbeOutput(staging, outputDir, backup);

                long duration = System.currentTimeMillis() - start;
                NekoJS.LOGGER.info("Probe generated: {} files in {}ms", filesGenerated, duration);
                return GenerateResult.success(filesGenerated, duration);

            } catch (Exception genFailure) {
                // 生成中途失败：丢弃 staging 半成品，旧 outputDir 完整保留
                deleteRecursive(staging);
                throw genFailure;
            }

        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe generation failed", e);
            return GenerateResult.failure(e.getMessage());
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
     * 收集需要生成声明的 Java 类。
     * 从多个来源发现类：
     * 1. 常用 Java 标准库类
     * 2. 事件类和绑定类型
     * 3. BFS 发现引用的类（每展开一个类 = 1 深度单位）
     */
    private Set<String> collectClasses(NekoScriptCatalogSnapshot snapshot) {
        Set<String> visited = new LinkedHashSet<>();
        java.util.Queue<Object[]> queue = new java.util.LinkedList<>();

        // 种子类：事件类型和绑定类型（depth 0）
        for (EventCatalogEntry event : snapshot.events()) {
            if (event.eventType() != null) queue.add(new Object[]{event.eventType(), 0});
            if (event.dispatchKeyType() != null) queue.add(new Object[]{event.dispatchKeyType(), 0});
        }
        for (BindingCatalogEntry binding : snapshot.bindings()) {
            if (binding.javaType() != null) queue.add(new Object[]{binding.javaType(), 0});
            // 代理绑定（如 Item）的 extraDocTypes（委托目标 MC 类）也作为种子，确保其 $Class 声明生成
            for (Class<?> extra : binding.extraDocTypes()) {
                queue.add(new Object[]{extra, 0});
            }
        }

        int maxDepth = 5;

        // 轻量 BFS：只收集类名，不做声明生成
        while (!queue.isEmpty()) {
            Object[] entry = queue.poll();
            Class<?> cls = (Class<?>) entry[0];
            int depth = (int) entry[1];

            if (depth > maxDepth) continue;
            if (cls == null || cls.isPrimitive() || cls == Object.class) continue;

            String name = cls.getName();
            if (visited.contains(name)) continue;
            if (!isRelevantClass(name)) continue;

            visited.add(name);

            int nextDepth = depth + 1;
            if (nextDepth > maxDepth) continue;

            if (cls.getSuperclass() != null) queue.add(new Object[]{cls.getSuperclass(), nextDepth});
            for (Class<?> iface : cls.getInterfaces()) queue.add(new Object[]{iface, nextDepth});

            for (java.lang.reflect.Constructor<?> ctor : cls.getDeclaredConstructors()) {
                if (java.lang.reflect.Modifier.isPublic(ctor.getModifiers())) {
                    for (java.lang.reflect.Type p : ctor.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                }
            }
            for (java.lang.reflect.Method method : cls.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    collectTypeToQueue(method.getGenericReturnType(), queue, nextDepth);
                    for (java.lang.reflect.Type p : method.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                }
            }
            for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isPublic(field.getModifiers())) collectTypeToQueue(field.getGenericType(), queue, nextDepth);
            }
        }

        return visited;
    }

    private void collectTypeToQueue(java.lang.reflect.Type type, java.util.Queue<Object[]> queue, int depth) {
        if (type instanceof Class<?> cls) {
            queue.add(new Object[]{cls, depth});
        } else if (type instanceof java.lang.reflect.ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> rawCls) queue.add(new Object[]{rawCls, depth});
            for (java.lang.reflect.Type arg : pt.getActualTypeArguments()) collectTypeToQueue(arg, queue, depth);
        } else if (type instanceof java.lang.reflect.GenericArrayType gat) {
            collectTypeToQueue(gat.getGenericComponentType(), queue, depth);
        }
    }

    /**
     * 并行预生成所有类声明（重反射工作放到线程池）。
     */
    private void pregenerateDeclarations(Set<String> classNames) {
        int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), 8);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(parallelism);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
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
        for (var f : futures) { try { f.get(); } catch (Exception ignored) {} }
        executor.shutdown();
    }

    static boolean isRelevantClass(String name) {
        return name.startsWith("java.") ||
               name.startsWith("net.minecraft.") ||
               name.startsWith("net.minecraftforge.") ||
               name.startsWith("net.neoforged.") ||
               name.startsWith("com.tkisor.nekojs.");
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
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(parallelism);
        java.util.concurrent.CompletionService<Void> completion =
                new java.util.concurrent.ExecutorCompletionService<>(executor);

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
            } catch (java.util.concurrent.ExecutionException e) {
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

    private int generateManifestAndSurface(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        int count = 0;

        // api-manifest.json
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = snapshot.managedApis();
        if (!managedApis.isEmpty()) {
            ApiManifestGenerator manifestGenerator = new ApiManifestGenerator(
                    com.tkisor.nekojs.core.api.ApiRuntimeVersionReader.read(),
                    ProbeContractSetHolder.contractSet());
            manifestGenerator.write(outputDir, managedApis);
            count++;
        }

        // current-surface.json
        String surfaceReport = CurrentSurfaceReport.generate(snapshot);
        Files.writeString(outputDir.resolve("current-surface.json"), surfaceReport);
        count++;

        return count;
    }

    // -----------------------------------------------------------------------
    //  Inner classes
    // -----------------------------------------------------------------------

    /**
     * Java 包层级树：从类全限定名构建树结构，用于生成 package 目录。
     */
    private static final class PackageTree {
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
