package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
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
     * 把本 backend 的输出目录（{@code .neko_probe/python}，相对 {@code nekojs/} 根）合并进
     * {@code nekojs/pyrightconfig.json} 的 {@code extraPaths}（幂等、去重；fresh 文件附带默认）。
     * 让 pyright/pylance 解析到 {@code from nekojs import *} 的 stub 包。
     */
    @Override
    public void contributeEditorConfig(EditorConfigContributor contributor, ProbeContext ctx) {
        String rel = FileEditorConfigContributor.relativePosix(ctx.paths().root(), ctx.languageDir());
        contributor.mergePyrightExtraPaths(ctx.paths().root().resolve("pyrightconfig.json"), List.of(rel));
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
        Map<String, PyAdapterAlias> adapterAliases = buildAdapterAliases(snapshot.adapters(), availableFqns);

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
                        renderPackageModule(pkg, classes, classR, typeR, availableFqns, adapterAliases));
            }
            files++;
        }

        // 3. 事件声明（B3）：nekojs/_events/<side>/__init__.pyi
        files += writeEventStubs(nekojsDir, snapshot, typeR, availableFqns, adapterAliases);

        // 4. nekojs/__init__.pyi（全局绑定 + 事件组入口 + probe.add_global 全局声明）
        files += writeBindingsInit(nekojsDir, snapshot, availableFqns, typeR, globals);

        // 5. py.typed (PEP 561) + README
        Files.writeString(nekojsDir.resolve("py.typed"), "");
        Files.writeString(nekojsDir.resolve("README.md"), README_TEXT);
        files += 2;

        return files;
    }

    /** 渲染单个 Java 包的 __init__.pyi：跨包 import + 本包所有类 + 本包适配器输入别名。 */
    private String renderPackageModule(String javaPkg, List<TypeDecl> classes,
                                       PythonClassRenderer classR, ApiTypeRefPyRenderer typeR,
                                       Set<String> availableFqns, Map<String, PyAdapterAlias> adapterAliases) {
        Set<String> refFqns = new LinkedHashSet<>();
        for (TypeDecl d : classes) collectDeclSymbolFqns(d, refFqns);
        // 适配器输入别名：跨包的 host 输入类型需要 import；别名行在类之后输出
        List<String> aliasLines = new ArrayList<>();
        for (TypeDecl d : classes) {
            PyAdapterAlias alias = adapterAliases.get(d.fqn);
            if (alias == null) continue;
            refFqns.addAll(alias.importFqns());
            aliasLines.add(alias.aliasName() + " = " + ApiTypeRefPyRenderer.simplePyName(d.fqn)
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
        sb.append("from typing import Any, Callable, ClassVar\n");
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

    /** 适配器输入别名（Python 版）：{@code <Simple>_ = <Simple> | <输入类型们>}。 */
    record PyAdapterAlias(String aliasName, List<String> inputTypes, Set<String> importFqns) {}

    /**
     * 从适配器目录构建输入别名（目标 FQN → 别名）。仅处理已被收集（IR 里有声明）的目标类，
     * 避免别名悬空；无法映射到 Python 的形状（RegistryValue/RawValue）跳过并 debug 日志。
     */
    private Map<String, PyAdapterAlias> buildAdapterAliases(List<AdapterCatalogEntry> adapters,
                                                            Set<String> availableFqns) {
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
            for (AdapterInputShape shape : entry.shapes()) {
                String rendered = renderInputShape(shape, simple, inputFqns, availableFqns);
                if (rendered != null) inputs.add(rendered);
            }
            inputs.remove(simple); // Self 形状 = 目标自身，已在别名左侧
            if (inputs.isEmpty()) continue;
            out.put(fqn, new PyAdapterAlias(simple + "_", List.copyOf(inputs), Set.copyOf(inputFqns)));
        }
        return out;
    }

    /** 单个输入形状 → Python 类型字符串；无法映射（注册表/原始 TS 片段/未收集的 host）返回 null。 */
    private String renderInputShape(AdapterInputShape shape, String selfSimple,
                                    Set<String> inputFqns, Set<String> availableFqns) {
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
                String elem = renderInputShape(v.element(), selfSimple, inputFqns, availableFqns);
                yield elem == null ? null : "list[" + elem + "]";
            }
            case AdapterInputShape.ObjectValue v -> "dict[str, Any]";
            case AdapterInputShape.RegistryValue v -> {
                NekoJS.LOGGER.debug("Python probe: skip adapter registry input {} (TS RegistryTypes only)", v.typeName());
                yield null;
            }
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

    private static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
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
              entry that NekoJS writes into `nekojs/pyrightconfig.json`.

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
