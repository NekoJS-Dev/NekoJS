package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import com.tkisor.nekojs.probe.EditorConfigContributor;
import com.tkisor.nekojs.probe.FileEditorConfigContributor;
import com.tkisor.nekojs.probe.events.GlobalDecl;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeGenerator;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 内置 Python probe backend：把共享 IR 渲染成 PEP 484/561 的 {@code .pyi} stub 包，
 * 让 pyright/pylance/Jedi 为 NekoJS Python 脚本提供补全。
 *
 * <p>布局（输出目录 = {@code <baseDir>/python}）：
 * <pre>
 * nekojs/
 *   __init__.pyi     # 全局绑定（`from nekojs import *` 的目标），__all__ 框定
 *   py.typed         # PEP 561 marker
 *   README.md        # 用法说明
 *   _java/           # Java 类型 stub（按 Java 包组织）
 *     __init__.pyi
 *     net/minecraft/.../__init__.pyi
 *   _events/         # 事件声明（按 script side 组织）
 *     __init__.pyi
 *     server/__init__.pyi
 * </pre>
 *
 * <p>用法：脚本顶部写 {@code from nekojs import *}——转译器剥离之（无 JS 输出、source map 不变），
 * IDE 经 pyright 的 {@code extraPaths}（自动写入 {@code nekojs/pyrightconfig.json}）解析到本 stub 包。
 *
 * <p>{@link #requiresIr} 恒为 true：Python 无「旧路径」，必须由 {@code ProbeCoordinator} 构建共享 IR。
 */
public final class PythonProbeBackend implements ProbeBackend {

    @Override
    public String languageId() {
        return "python";
    }

    @Override
    public String name() {
        return "builtin";
    }

    @Override
    public boolean requiresIr() {
        return true;
    }

    /**
     * 把本 backend 的输出目录（{@code .neko_probe/python}）合并进 pyrightconfig.json 的
     * {@code extraPaths}（幂等、去重；fresh 文件附带默认）。
     *
     * <p>**每个可能被当作工作区打开的目录都写一份**：Pylance 只读取「工作区根」的
     * pyrightconfig.json（pyright CLI 才会从源文件就近向上发现），单一嵌套配置在用户打开
     * 游戏目录 / 脚本目录时会被忽略，导致 {@code from nekojs import *} 无法解析、无补全。
     * 与 TS 侧 jsconfig「每个脚本目录一份」的策略对齐：nekojs/ 根、四个脚本目录、游戏根目录。
     */
    @Override
    public void contributeEditorConfig(EditorConfigContributor contributor, ProbeContext ctx) {
        com.tkisor.nekojs.core.fs.NekoJSPaths paths = ctx.paths();
        Path out = ctx.languageDir();
        contributor.mergePyrightExtraPaths(paths.root().resolve("pyrightconfig.json"),
                List.of(FileEditorConfigContributor.relativePosix(paths.root(), out)));
        for (Path scriptDir : List.of(paths.startupScripts(), paths.serverScripts(),
                paths.clientScripts(), paths.testScripts())) {
            contributor.mergePyrightExtraPaths(scriptDir.resolve("pyrightconfig.json"),
                    List.of(FileEditorConfigContributor.relativePosix(scriptDir, out)));
            mergePyrightConfigsForNestedPythonDirs(contributor, scriptDir, out);
        }
        contributor.mergePyrightExtraPaths(paths.gameDir().resolve("pyrightconfig.json"),
                List.of(FileEditorConfigContributor.relativePosix(paths.gameDir(), out)));
    }

    /**
     * 为脚本根目录下每个「实际包含 .py 脚本」的子目录写一份 pyrightconfig.json。
     *
     * <p>Pylance 只读取工作区根的配置，而用户既可能打开游戏目录 / nekojs 目录 / 四个脚本根，
     * 也可能直接把某个 {@code server_scripts/src} 之类的子目录打开为工作区。给每个含 .py 的
     * 目录就近放置一份配置（extraPaths 按该目录到 stub 输出的真实相对深度计算），任一目录被
     * 当作工作区根时都能解析 {@code from nekojs import *}。JS-only 目录不写（Python backend
     * 无需为其贡献 pyright 配置）。
     */
    private static void mergePyrightConfigsForNestedPythonDirs(EditorConfigContributor contributor,
                                                                Path scriptDir, Path out) {
        if (scriptDir == null || !Files.isDirectory(scriptDir)) {
            return;
        }
        Set<Path> pythonDirs = new TreeSet<>();
        try (var stream = Files.walk(scriptDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        Path name = path.getFileName();
                        return name != null && name.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".py");
                    })
                    .forEach(path -> {
                        Path parent = path.getParent();
                        if (parent != null && !parent.equals(scriptDir)) {
                            pythonDirs.add(parent);
                        }
                    });
        } catch (IOException e) {
            NekoJS.LOGGER.debug("Probe [python]: failed to scan {} for nested pyright configs", scriptDir, e);
            return;
        }
        for (Path dir : pythonDirs) {
            contributor.mergePyrightExtraPaths(dir.resolve("pyrightconfig.json"),
                    List.of(FileEditorConfigContributor.relativePosix(dir, out)));
        }
    }

    @Override
    public ProbeGenerator.GenerateResult generate(ProbeContext ctx) {
        long start = System.currentTimeMillis();
        List<TypeDecl> ir = ctx.ir();
        if (ir == null || ir.isEmpty()) {
            return ProbeGenerator.GenerateResult.failure("python probe requires shared IR; no classes collected");
        }

        Path outputDir = ctx.languageDir();
        Path staging = outputDir.resolveSibling(outputDir.getFileName().toString() + ".staging");
        Path backup = outputDir.resolveSibling(outputDir.getFileName().toString() + ".old");

        try {
            deleteRecursive(staging);
            if (!Files.exists(outputDir) && Files.exists(backup)) {
                Files.move(backup, outputDir);
            }
            Files.createDirectories(staging);

            try {
                int files = doGenerate(staging, ir, ctx.snapshot(), ctx.overrides().globals());

                commitProbeOutput(staging, outputDir, backup);

                long duration = System.currentTimeMillis() - start;
                NekoJS.LOGGER.info("Probe [python] generated: {} files in {}ms", files, duration);
                return ProbeGenerator.GenerateResult.success(files, duration);
            } catch (Exception genFailure) {
                deleteRecursive(staging);
                throw genFailure;
            }
        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe [python] generation failed", e);
            return ProbeGenerator.GenerateResult.failure(e.getMessage());
        }
    }

    // ============================== 生成主体 ==============================

    private int doGenerate(Path staging, List<TypeDecl> ir, NekoScriptCatalogSnapshot snapshot,
                           List<GlobalDecl> globals) throws IOException {
        Path nekojsDir = staging.resolve("nekojs");
        Path javaBase = nekojsDir.resolve("_java");
        Files.createDirectories(javaBase);

        // 1. 按 Java 包分组 + 可用 FQN 集
        Map<String, List<TypeDecl>> byPkg = new TreeMap<>();
        Set<String> availableFqns = new HashSet<>();
        for (TypeDecl d : ir) {
            String pkg = pkgOf(d.fqn);
            if (pkg.isEmpty()) continue; // 默认包不产出（收集到的类均在具名包内）
            byPkg.computeIfAbsent(pkg, k -> new ArrayList<>()).add(d);
            availableFqns.add(d.fqn);
        }
        // C5a：隐藏类从 availableFqns 剔除（在构造类型渲染器之前）——其他模块引用它时不再
        // import（防悬空），渲染器也会把它当作未收集 SYMBOL 降级为 Any；其自身模块渲染为空串。
        for (TypeDecl d : ir) {
            if (d.hidden) availableFqns.remove(d.fqn);
        }
        // 祖先包（含自身），保证 import 路径上每层都有 __init__.pyi
        Set<String> allPkgs = new TreeSet<>();
        for (String pkg : byPkg.keySet()) allPkgs.addAll(ancestorsOf(pkg));

        ApiTypeRefPyRenderer typeR = new ApiTypeRefPyRenderer(availableFqns);
        PythonClassRenderer classR = new PythonClassRenderer(typeR);

        // 1.5 适配器输入别名（B4）：目标 FQN → <Simple>_ = <Simple> | <输入类型们>
        // 注册表字面量查表（typeName → 条目）：RegistryValue 形状 → Literal[...]（小注册表）/ str（大注册表）
        Map<String, RegistryTypeCatalogEntry> registries = new LinkedHashMap<>();
        if (snapshot.registryTypes() != null) {
            for (RegistryTypeCatalogEntry r : snapshot.registryTypes()) {
                registries.put(r.typeName(), r);
            }
        }
        Map<String, PyAdapterAlias> adapterAliases = buildAdapterAliases(snapshot.adapters(), availableFqns, registries);
        // 枚举字面量别名：Color_ = Color | Literal["RED", ...]（镜像 TS 侧 $Color_ 输入别名）
        Map<String, PyAdapterAlias> enumAliases = buildEnumAliases(ir, availableFqns);
        // dispatch key 放宽视图：适配器 + 枚举别名合并（枚举 key 经 PythonEventRenderer 放宽为 Enum_）
        Map<String, PyAdapterAlias> wideningAliases = new LinkedHashMap<>(adapterAliases);
        wideningAliases.putAll(enumAliases);

        int files = 0;
        // _java/__init__.pyi（namespace marker）
        Files.writeString(javaBase.resolve("__init__.pyi"), "# Auto-generated namespace marker.\n");
        files++;

        // 2. 每个包模块
        for (String pkg : allPkgs) {
            Path pkgDir = javaBase;
            for (String seg : pkg.split("\\.")) pkgDir = pkgDir.resolve(seg);
            Files.createDirectories(pkgDir);
            List<TypeDecl> classes = byPkg.getOrDefault(pkg, List.of());
            if (classes.isEmpty()) {
                Files.writeString(pkgDir.resolve("__init__.pyi"), "# Auto-generated namespace marker.\n");
            } else {
                Files.writeString(pkgDir.resolve("__init__.pyi"),
                        renderPackageModule(pkg, classes, classR, typeR, availableFqns, adapterAliases, enumAliases));
            }
            files++;
        }

        // 3. 事件声明（B3）：nekojs/_events/<side>/__init__.pyi
        files += writeEventStubs(nekojsDir, snapshot, typeR, availableFqns, wideningAliases);

        // 4. nekojs/__init__.pyi（全局绑定 + 事件组入口 + probe.add_global 全局声明）
        files += writeBindingsInit(nekojsDir, snapshot, availableFqns, typeR, globals);

        // 5. py.typed (PEP 561) + README
        Files.writeString(nekojsDir.resolve("py.typed"), "");
        Files.writeString(nekojsDir.resolve("README.md"), README_TEXT);
        files += 2;

        return files;
    }

    /** 渲染单个 Java 包的 __init__.pyi：跨包 import + 本包所有类 + 本包适配器/枚举输入别名。 */
    private String renderPackageModule(String javaPkg, List<TypeDecl> classes,
                                       PythonClassRenderer classR, ApiTypeRefPyRenderer typeR,
                                       Set<String> availableFqns, Map<String, PyAdapterAlias> adapterAliases,
                                       Map<String, PyAdapterAlias> enumAliases) {
        Set<String> refFqns = new LinkedHashSet<>();
        for (TypeDecl d : classes) collectDeclSymbolFqns(d, refFqns);
        // 适配器输入别名：跨包的 host 输入类型需要 import；别名行在类之后输出
        List<String> aliasLines = new ArrayList<>();
        for (TypeDecl d : classes) {
            PyAdapterAlias alias = adapterAliases.get(d.fqn);
            if (alias == null) continue;
            refFqns.addAll(alias.importFqns());
            String line = alias.aliasName() + " = " + ApiTypeRefPyRenderer.simplePyName(d.fqn)
                    + " | " + String.join(" | ", alias.inputTypes());
            // 大注册表（>=512 条目）的 RegistryValue 形状缩略为 str，行尾注释标注来源注册表
            if (alias.note() != null) line += "  # " + alias.note();
            aliasLines.add(line);
        }
        // 枚举字面量别名：Color_ = Color | Literal["RED", ...]（常量序与 PythonClassRenderer.renderEnum 一致）
        for (TypeDecl d : classes) {
            PyAdapterAlias alias = enumAliases.get(d.fqn);
            if (alias == null) continue;
            aliasLines.add(alias.aliasName() + " = " + effectivePyName(d)
                    + " | " + String.join(" | ", alias.inputTypes()));
        }

        // 跨包 import（仅可用的、跨包的；按简单名去重）
        Map<String, String> importByName = new TreeMap<>();
        for (String fqn : refFqns) {
            if (!availableFqns.contains(fqn)) continue;
            String otherPkg = pkgOf(fqn);
            if (otherPkg.equals(javaPkg)) continue; // 同模块已在作用域内
            importByName.put(ApiTypeRefPyRenderer.simplePyName(fqn), fqn);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Auto-generated by NekoJS probe — Python stubs for Java package ").append(javaPkg).append(".\n");
        sb.append("# Do not edit; regenerate with `/nekojs probe python`.\n\n");
        // Literal 仅在本模块别名确实用到字面量联合时导入（枚举别名 / 小注册表 RegistryValue）
        boolean usesLiteral = aliasLines.stream().anyMatch(l -> l.contains("Literal["));
        sb.append("from typing import Any, Callable, ClassVar").append(usesLiteral ? ", Literal" : "").append("\n");
        for (var e : importByName.entrySet()) {
            sb.append("from nekojs._java.").append(pkgOf(e.getValue())).append(" import ").append(e.getKey()).append("\n");
        }
        sb.append("\n");
        for (TypeDecl d : classes) {
            String body = classR.render(d);
            if (!body.isEmpty()) sb.append(body).append("\n");
        }
        for (String line : aliasLines) sb.append(line).append("\n");
        return sb.toString();
    }

    /** nekojs/__init__.pyi：全局绑定 + 事件组入口 + {@code probe.add_global} 全局声明（{@code from nekojs import *} 的目标）。 */
    private int writeBindingsInit(Path nekojsDir, NekoScriptCatalogSnapshot snapshot, Set<String> availableFqns,
                                  ApiTypeRefPyRenderer typeR, List<GlobalDecl> globals) throws IOException {
        List<BindingCatalogEntry> bindings = snapshot.bindings().stream()
                .filter(BindingCatalogEntry::emit)
                .toList();

        // 事件组名（按 side）；绑定名命中组名时，绑定类型指向 nekojs._events/<side> 的 <Group>Type 类
        Map<ScriptType, List<String>> groupsBySide = eventGroupsBySide(snapshot.events());

        Map<String, String> importByName = new TreeMap<>(); // simpleName → fqn（nekojs._java 侧）
        Set<String> eventImports = new LinkedHashSet<>();   // "from nekojs._events.<side> import <TypeName>"
        List<String[]> lines = new ArrayList<>();            // [name, typeExpr]
        List<String> allNames = new ArrayList<>();

        for (BindingCatalogEntry b : bindings) {
            String type = "Any";
            List<String> groupNames = groupsBySide.getOrDefault(b.scriptType(), List.of());
            if (b.scriptType() != null && groupNames.contains(b.name())) {
                // 事件组绑定：类型 = _events/<side> 的 <Group>Type
                String typeName = PythonEventRenderer.groupTypeName(b.name());
                type = typeName;
                eventImports.add("from nekojs._events." + b.scriptType().name + " import " + typeName);
            } else if (b.typeOverride() != null && !b.typeOverride().isEmpty()) {
                // typeOverride（如 "NekoItemHelper"）：若存在已收集 FQN 的简单名与之相等，
                // 用该类的简单名作为绑定类型并 import（优先于 javaType）
                String fqn = findFqnBySimpleName(availableFqns, b.typeOverride());
                if (fqn != null) {
                    type = ApiTypeRefPyRenderer.simplePyName(fqn);
                    importByName.put(type, fqn);
                }
            } else if (b.javaType() != null && availableFqns.contains(b.javaType().getName())) {
                String fqn = b.javaType().getName();
                type = ApiTypeRefPyRenderer.simplePyName(fqn);
                importByName.put(type, fqn);
            }
            lines.add(new String[]{b.name(), type});
            allNames.add(b.name());
        }
        // 事件组入口：binding 目录里没有对应条目的组名（DefaultScriptEventBridge 在运行时暴露事件组全局），
        // 补发指向 _events 类，保证 `ServerEvents.recipes(...)` 有补全
        for (var e : groupsBySide.entrySet()) {
            ScriptType side = e.getKey();
            for (String group : e.getValue()) {
                if (allNames.contains(group)) continue;
                String typeName = PythonEventRenderer.groupTypeName(group);
                lines.add(new String[]{group, typeName});
                allNames.add(group);
                eventImports.add("from nekojs._events." + side.name + " import " + typeName);
            }
        }
        // probe.add_global 全局声明：类型走 py renderer；SYMBOL 类型按可用性 import
        if (globals != null) {
            for (GlobalDecl g : globals) {
                Set<String> fqns = new LinkedHashSet<>();
                ApiTypeRefPyRenderer.collectSymbolFqns(g.type(), fqns);
                for (String fqn : fqns) {
                    if (availableFqns.contains(fqn)) {
                        importByName.put(ApiTypeRefPyRenderer.simplePyName(fqn), fqn);
                    }
                }
                lines.add(new String[]{g.name(), typeR.render(g.type())});
                allNames.add(g.name());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Auto-generated by NekoJS probe. Global bindings for `from nekojs import *`.\n");
        sb.append("# The transpiler strips that magic import; pyright resolves globals via this stub.\n");
        sb.append("# Do not edit; regenerate with `/nekojs probe python`.\n\n");
        sb.append("from typing import Any\n");
        for (var e : importByName.entrySet()) {
            sb.append("from nekojs._java.").append(pkgOf(e.getValue())).append(" import ").append(e.getKey()).append("\n");
        }
        for (String line : eventImports) sb.append(line).append("\n");
        sb.append("\n");
        for (String[] l : lines) sb.append(l[0]).append(": ").append(l[1]).append("\n");
        sb.append("\n__all__ = [");
        for (int i = 0; i < allNames.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(allNames.get(i)).append("\"");
        }
        sb.append("]\n");
        Files.writeString(nekojsDir.resolve("__init__.pyi"), sb.toString());
        return 1;
    }

    /** 每个 side 的事件组名（按事件出现顺序去重）；无事件的 side 不出现。 */
    private static Map<ScriptType, List<String>> eventGroupsBySide(List<EventCatalogEntry> events) {
        Map<ScriptType, List<String>> out = new LinkedHashMap<>();
        if (events == null) return out;
        for (ScriptType side : ScriptType.all()) {
            List<String> names = events.stream()
                    .filter(e -> e.scriptType().test(side))
                    .map(EventCatalogEntry::group)
                    .distinct()
                    .toList();
            if (!names.isEmpty()) out.put(side, names);
        }
        return out;
    }

    /** 在可用 FQN 集合里找简单名等于 {@code simpleName} 的类（确定性：按 FQN 排序取第一个）。 */
    private static String findFqnBySimpleName(Set<String> availableFqns, String simpleName) {
        for (String fqn : new TreeSet<>(availableFqns)) {
            if (ApiTypeRefPyRenderer.simplePyName(fqn).equals(simpleName)) return fqn;
        }
        return null;
    }

    // ============================== 事件声明（B3） ==============================

    /** 为每个 side 写 {@code nekojs/_events/<side>/__init__.pyi}（无事件的 side 跳过）；root marker 一并产出。 */
    private int writeEventStubs(Path nekojsDir, NekoScriptCatalogSnapshot snapshot,
                                ApiTypeRefPyRenderer typeR, Set<String> availableFqns,
                                Map<String, PyAdapterAlias> adapterAliases) throws IOException {
        List<EventCatalogEntry> events = snapshot.events();
        if (events == null || events.isEmpty()) return 0;

        PythonEventRenderer eventR = new PythonEventRenderer(typeR, availableFqns);
        int count = 0;
        Path eventsBase = nekojsDir.resolve("_events");
        for (ScriptType side : ScriptType.all()) {
            List<EventCatalogEntry> sideEvents = events.stream()
                    .filter(e -> e.scriptType().test(side))
                    .toList();
            if (sideEvents.isEmpty()) continue;
            Path dir = eventsBase.resolve(side.name);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("__init__.pyi"), eventR.render(side, sideEvents, adapterAliases));
            count++;
        }
        if (count > 0) {
            Files.writeString(eventsBase.resolve("__init__.pyi"), "# Auto-generated namespace marker.\n");
            count++;
        }
        return count;
    }

    // ============================== 适配器输入别名（B4） ==============================

    /**
     * 适配器输入别名（Python 版）：{@code <Simple>_ = <Simple> | <输入类型们>}。
     * {@code note} 非空时别名行尾附加 {@code # note} 注释（大注册表缩略为 str 时标注注册表名）。
     */
    record PyAdapterAlias(String aliasName, List<String> inputTypes, Set<String> importFqns, String note) {}

    /**
     * 从适配器目录构建输入别名（目标 FQN → 别名）。仅处理已被收集（IR 里有声明）的目标类，
     * 避免别名悬空；无法映射到 Python 的形状（未收集 host/RawValue/快照缺失的注册表）跳过并 debug 日志。
     *
     * @param registries 注册表字面量查表（typeName → 条目），供 RegistryValue 形状渲染 Literal 联合
     */
    private Map<String, PyAdapterAlias> buildAdapterAliases(List<AdapterCatalogEntry> adapters,
                                                            Set<String> availableFqns,
                                                            Map<String, RegistryTypeCatalogEntry> registries) {
        Map<String, PyAdapterAlias> out = new LinkedHashMap<>();
        if (adapters == null) return out;
        for (AdapterCatalogEntry entry : adapters) {
            if (entry.shapes() == null || entry.shapes().isEmpty()) continue;
            Class<?> target = entry.targetType();
            if (target == null) continue;
            String fqn = target.getName();
            if (!availableFqns.contains(fqn)) continue;
            String simple = ApiTypeRefPyRenderer.simplePyName(fqn);
            Set<String> inputFqns = new LinkedHashSet<>();
            Set<String> inputs = new LinkedHashSet<>();
            List<String> largeRegistries = new ArrayList<>();
            for (AdapterInputShape shape : entry.shapes()) {
                String rendered = renderInputShape(shape, simple, inputFqns, availableFqns, registries, largeRegistries);
                if (rendered != null) inputs.add(rendered);
            }
            inputs.remove(simple); // Self 形状 = 目标自身，已在别名左侧
            if (inputs.isEmpty()) continue;
            String note = largeRegistries.isEmpty() ? null
                    : String.join(", ", largeRegistries) + " registry ids abbreviated as str ("
                      + REGISTRY_LITERAL_LIMIT + "+ entries)";
            // TreeSet：确定性迭代（Set.copyOf 迭代序无规范保证，跨 JVM 会抖动 .pyi 输出）
            out.put(fqn, new PyAdapterAlias(simple + "_", List.copyOf(inputs), new java.util.TreeSet<>(inputFqns), note));
        }
        return out;
    }

    /**
     * 枚举字面量输入别名：{@code Color_ = Color | Literal["RED", ...]}。
     * 常量顺序 = {@code decl.fields} 序（TypeReflector 已按名字稳定排序，与 renderEnum 发射序一致）。
     * 仅处理已收集且非隐藏的枚举；空枚举（无常量）跳过。
     */
    private Map<String, PyAdapterAlias> buildEnumAliases(List<TypeDecl> ir, Set<String> availableFqns) {
        Map<String, PyAdapterAlias> out = new LinkedHashMap<>();
        for (TypeDecl d : ir) {
            if (d.hidden || d.kind != TypeDecl.Kind.ENUM) continue;
            if (!availableFqns.contains(d.fqn)) continue;
            List<String> literals = new ArrayList<>();
            for (FieldDecl f : d.fields) {
                if (!f.hidden && f.isEnumConstant) literals.add("\"" + f.effectiveName() + "\"");
            }
            if (literals.isEmpty()) continue;
            out.put(d.fqn, new PyAdapterAlias(effectivePyName(d) + "_",
                    List.of("Literal[" + String.join(", ", literals) + "]"), Set.of(), null));
        }
        return out;
    }

    /** 枚举渲染名：renameTo 优先，否则 FQN 的 Python 简单名（与 PythonClassRenderer.effectiveClassName 一致）。 */
    private static String effectivePyName(TypeDecl d) {
        return d.effectiveTypeName() != null ? d.effectiveTypeName() : ApiTypeRefPyRenderer.simplePyName(d.fqn);
    }

    /** RegistryValue 形状的 Literal 联合截断阈值：>= 该条目数的注册表缩略为 str（行尾注释标注）。 */
    private static final int REGISTRY_LITERAL_LIMIT = 512;

    /**
     * RegistryValue 形状 → Python 类型字符串：复用 TS {@code @special/types} 的同一数据源
     * （snapshot.registryTypes 的条目列表）。小注册表（&lt;512 条目）→ 排序后的
     * {@code Literal["id", ...]}（转义对齐 TS generateSpecialTypes：先反斜杠后引号）；
     * 大注册表 → {@code str} 并把注册表名记入 {@code largeRegistries} 供行尾注释；
     * 快照缺失的注册表 → null（跳过该形状，与旧行为一致）。
     */
    private String renderRegistryInput(String typeName, Map<String, RegistryTypeCatalogEntry> registries,
                                       List<String> largeRegistries) {
        RegistryTypeCatalogEntry entry = registries.get(typeName);
        if (entry == null) {
            NekoJS.LOGGER.debug("Python probe: skip adapter registry input {} (registry not in snapshot)", typeName);
            return null;
        }
        List<String> entries = new ArrayList<>(entry.entries());
        if (entries.isEmpty()) return "str"; // TS 侧 @special 对空注册表同样回退 string
        if (entries.size() >= REGISTRY_LITERAL_LIMIT) {
            largeRegistries.add(typeName);
            return "str";
        }
        Collections.sort(entries); // 确定性：不依赖快照条目顺序
        StringBuilder sb = new StringBuilder("Literal[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('"').append(entries.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    /** 单个输入形状 → Python 类型字符串；无法映射（原始 TS 片段/未收集的 host）返回 null。 */
    private String renderInputShape(AdapterInputShape shape, String selfSimple,
                                    Set<String> inputFqns, Set<String> availableFqns,
                                    Map<String, RegistryTypeCatalogEntry> registries,
                                    List<String> largeRegistries) {
        return switch (shape) {
            case AdapterInputShape.StringValue v -> "str";
            case AdapterInputShape.NumberValue v -> "float";
            case AdapterInputShape.BooleanValue v -> "bool";
            case AdapterInputShape.SelfValue v -> selfSimple;
            case AdapterInputShape.HostValue v -> {
                Class<?> cls = v.cls();
                if (cls == null) yield null;
                String fqn = cls.getName();
                if (!availableFqns.contains(fqn)) {
                    NekoJS.LOGGER.debug("Python probe: skip adapter host input {}, class not collected", fqn);
                    yield null;
                }
                inputFqns.add(fqn);
                yield ApiTypeRefPyRenderer.simplePyName(fqn);
            }
            case AdapterInputShape.ArrayOfValue v -> {
                String elem = renderInputShape(v.element(), selfSimple, inputFqns, availableFqns, registries, largeRegistries);
                yield elem == null ? null : "list[" + elem + "]";
            }
            case AdapterInputShape.ObjectValue v -> "dict[str, Any]";
            case AdapterInputShape.RegistryValue v ->
                    renderRegistryInput(v.typeName(), registries, largeRegistries);
            case AdapterInputShape.RawValue v -> {
                NekoJS.LOGGER.debug("Python probe: skip adapter raw TS input '{}'", v.ts());
                yield null;
            }
        };
    }

    // ============================== import 收集 ==============================

    private static void collectDeclSymbolFqns(TypeDecl d, Set<String> out) {
        if (d.superType != null) ApiTypeRefPyRenderer.collectSymbolFqns(d.superType.ref, out);
        for (TypeSlot i : d.interfaces) ApiTypeRefPyRenderer.collectSymbolFqns(i.ref, out);
        for (MethodDecl c : d.constructors) collectMethodRefs(c, out);
        for (MethodDecl m : d.methods) collectMethodRefs(m, out);
        for (FieldDecl f : d.fields) ApiTypeRefPyRenderer.collectSymbolFqns(f.type.ref, out);
        for (TypeDecl.TypeParam tp : d.typeParams) {
            if (tp.bound != null) ApiTypeRefPyRenderer.collectSymbolFqns(tp.bound.ref, out);
        }
    }

    private static void collectMethodRefs(MethodDecl m, Set<String> out) {
        if (m.returnType != null) ApiTypeRefPyRenderer.collectSymbolFqns(m.returnType.ref, out);
        if (m.setterParamType != null) ApiTypeRefPyRenderer.collectSymbolFqns(m.setterParamType.ref, out);
        for (MethodDecl.MethodParam p : m.params) ApiTypeRefPyRenderer.collectSymbolFqns(p.type.ref, out);
    }

    // ============================== IO 工具（镜像 TS backend）==============================

    private static void commitProbeOutput(Path staging, Path outputDir, Path backup) throws IOException {
        deleteRecursive(backup);
        if (Files.exists(outputDir)) Files.move(outputDir, backup);
        try {
            Files.move(staging, outputDir);
        } catch (IOException e) {
            if (!Files.exists(outputDir) && Files.exists(backup)) {
                try { Files.move(backup, outputDir); } catch (IOException ignored) {}
            }
            throw e;
        }
        deleteRecursive(backup);
    }

    /**
     * 递归删除目录及其内容（深度优先逆序，先文件后目录）。walk 流用 try-with-resources 关闭
     * （文件句柄泄漏会锁住目录，Windows 上导致后续 move 失败）；删除失败的路径收集后 warn
     * 一次（典型为 Windows 文件锁），不抛出——与 TS backend 的同名工具保持一致。
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

    private static String pkgOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(0, dot) : "";
    }

    private static List<String> ancestorsOf(String pkg) {
        List<String> out = new ArrayList<>();
        StringBuilder acc = new StringBuilder();
        for (String seg : pkg.split("\\.")) {
            if (acc.length() > 0) acc.append('.');
            acc.append(seg);
            out.add(acc.toString());
        }
        return out;
    }

    private static final String README_TEXT = """
            # NekoJS Python type stubs

            This package provides type information (PEP 484/561 `.pyi` stubs) so that
            **pyright / Pylance / Jedi** can offer completion for NekoJS Python scripts.

            ## Enable completion

            Add this magic line at the **top** of your `.py` script:

            ```python
            from nekojs import *
            ```

            - At runtime, the NekoJS transpiler **strips** this line (it emits no JavaScript
              and does not affect the source map). It exists purely for the type checker.
            - The type checker resolves `nekojs` to this stub package via the `extraPaths`
              entries that NekoJS writes into pyrightconfig.json files (game root,
              `nekojs/`, and each script directory). Note Pylance only honors the config
              at the **workspace root** - open the game directory (or `nekojs/`) in your
              editor, not a parent folder of it.

            After `from nekojs import *`, the global bindings (`Item`, `ServerEvents`,
            `Utils`, ...) and the Java types they expose become visible for completion.

            ## Regenerate

            Run `/nekojs probe python` in-game to refresh these stubs.

            ## Notes

            - Java types are organized under `nekojs._java.<java.package>`.
            - Generics are flattened (type variables render as `Any`); member names, parameter
              counts, and field types are preserved — sufficient for day-to-day completion.
            - `py.typed` marks this as a typed package (PEP 561).
            """;
}
