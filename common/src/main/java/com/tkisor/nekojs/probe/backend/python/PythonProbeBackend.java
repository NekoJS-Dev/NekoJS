package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.probe.EditorConfigContributor;
import com.tkisor.nekojs.probe.FileEditorConfigContributor;
import com.tkisor.nekojs.probe.events.GlobalDecl;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeOutputCommitter;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Built-in Python probe backend: Renders the shared IR into PEP 484/561 compliant {@code .pyi} stub packages,
 * enabling autocompletion in pyright, pylance, and Jedi for NekoJS Python scripts.
 *
 * <p>Layout (output directory = {@code <baseDir>/python}):
 * <pre>
 * nekojs/
 *   __init__.pyi     # Global bindings (target for `from nekojs import *`), defined by __all__
 *   py.typed         # PEP 561 marker
 *   README.md        # Usage instructions
 *   _java/           # Java type stubs (organized by Java package)
 *     __init__.pyi
 *     net/minecraft/.../__init__.pyi
 *   _events/         # Event declarations (organized by script side)
 *     __init__.pyi
 *     server/__init__.pyi
 * </pre>
 *
 * <p>Usage: Include {@code from nekojs import *} at the top of scripts; the transpiler strips this line
 * (no JS output, source map remains unchanged). The IDE resolves the stub package via pyright's
 * {@code extraPaths} (automatically written to {@code nekojs/pyrightconfig.json}).
 *
 * <p>{@link #requiresIr} is always true: Python has no legacy path and requires the shared IR
 * built by {@code ProbeCoordinator}.
 */
public final class PythonProbeBackend implements ProbeBackend {
    private final Set<String> renderWarnings = new LinkedHashSet<>();

    @Override
    public GenerateResult generate(ProbeContext ctx) {
        GenerateResult result = ProbeBackend.super.generate(ctx);
        return result.success() && !renderWarnings.isEmpty()
                ? GenerateResult.withWarnings(result, List.copyOf(renderWarnings)) : result;
    }

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

    @Override
    public void contributeEditorConfig(EditorConfigContributor contributor, ProbeContext ctx) {
        PythonEditorConfig.contribute(contributor, ctx);
    }

    @Override
    public void resetEditorConfig(com.tkisor.nekojs.core.fs.NekoJSPaths paths) {
        PythonEditorConfig.reset(paths);
    }

    /**
     * Renders all Python artifacts to memory (relative path to output directory -> content). Disk writing and atomic commitment are handled by the {@code generate} interface; this method does not touch the disk.
     */
    @Override
    public Map<String, String> render(ProbeContext ctx) {
        renderWarnings.clear();
        List<TypeDecl> ir = ctx.ir();
        if (ir == null || ir.isEmpty()) {
            throw new IllegalArgumentException("python probe requires shared IR; no classes collected");
        }
        Map<String, List<String>> incompleteMembers = new TreeMap<>();
        for (TypeDecl declaration : ir) {
            if (!declaration.hidden && !pkgOf(declaration.fqn).isEmpty()
                    && !declaration.memberOmissions.isEmpty()) {
                incompleteMembers.put(declaration.fqn, List.copyOf(declaration.memberOmissions));
            }
        }
        for (var entry : incompleteMembers.entrySet()) {
            renderWarnings.add("[NEKO-4066] Probe declaration retained: class="
                    + entry.getKey() + "; entire public method set unavailable; omissions="
                    + String.join("; ", entry.getValue()));
        }
        return renderFiles(ir, ctx.snapshot(), ctx.overrides().globals());
    }

    // ============================== Generation Main Body ==============================

    private Map<String, String> renderFiles(List<TypeDecl> ir, NekoScriptCatalogSnapshot snapshot,
                                            List<GlobalDecl> globals) {
        Map<String, String> files = new LinkedHashMap<>();

        // 1. Group by Java package + set of available FQNs
        Map<String, List<TypeDecl>> byPkg = new TreeMap<>();
        Set<String> availableFqns = new HashSet<>();
        for (TypeDecl d : ir) {
            String pkg = pkgOf(d.fqn);
            if (pkg.isEmpty()) continue; // Skip default package (all collected classes are in named packages)
            byPkg.computeIfAbsent(pkg, k -> new ArrayList<>()).add(d);
            availableFqns.add(d.fqn);
        }
        // C5a: Exclude hidden classes from availableFqns (before constructing type renderers). When other modules reference them,
        // import is avoided (preventing dangling imports), and the renderer treats them as uncollected SYMBOLs, downgrading to Any; their own module renders as an empty string.
        for (TypeDecl d : ir) {
            if (d.hidden) availableFqns.remove(d.fqn);
        }
        // Ancestor packages (including self), ensuring every level in the import path has an __init__.pyi
        Set<String> allPkgs = new TreeSet<>();
        for (String pkg : byPkg.keySet()) allPkgs.addAll(ancestorsOf(pkg));

        Map<String, String> exportedNames = new LinkedHashMap<>();
        for (TypeDecl declaration : ir) {
            if (availableFqns.contains(declaration.fqn)) {
                exportedNames.put(declaration.fqn, effectivePyName(declaration));
            }
        }
        ApiTypeRefPyRenderer typeR = new ApiTypeRefPyRenderer(availableFqns, exportedNames);
        PythonClassRenderer classR = new PythonClassRenderer(typeR, ir);

        // 1.5 Adapter input aliases: Target FQN -> <Simple>_ = <Simple> | <input types>
        // Registry literal lookup (typeName -> entries): RegistryValue shape -> Literal[...] (small registry) / str (large registry)
        Map<String, RegistryTypeCatalogEntry> registries = new LinkedHashMap<>();
        if (snapshot.registryTypes() != null) {
            for (RegistryTypeCatalogEntry r : snapshot.registryTypes()) {
                registries.put(r.typeName(), r);
            }
        }
        Map<String, PyAdapterAlias> adapterAliases = buildAdapterAliases(snapshot.adapters(), availableFqns,
                registries, snapshot.modIds(), typeR);
        // Enum literal aliases: Color_ = Color | Literal["RED", ...] (mirrors $Color_ input alias on TS side)
        Map<String, PyAdapterAlias> enumAliases = buildEnumAliases(ir, availableFqns);
        reserveInputAliasNames(byPkg, adapterAliases, enumAliases);
        // Dispatch key widening view: Merge adapters + enum aliases (enum keys widened to Enum_ via PythonEventRenderer)
        Map<String, PyAdapterAlias> wideningAliases = new LinkedHashMap<>(adapterAliases);
        wideningAliases.putAll(enumAliases);

        // Reserve every module's public imports and aliases before allocating cross-package helpers.
        for (var entry : byPkg.entrySet()) {
            Set<String> names = new LinkedHashSet<>(packageImports(entry.getKey(), entry.getValue(),
                    availableFqns, adapterAliases, enumAliases, classR, typeR).localNames());
            for (TypeDecl declaration : entry.getValue()) {
                PyAdapterAlias adapter = adapterAliases.get(declaration.fqn);
                PyAdapterAlias enumAlias = enumAliases.get(declaration.fqn);
                if (adapter != null) names.add(adapter.aliasName());
                if (enumAlias != null) names.add(enumAlias.aliasName());
            }
            classR.reservePackageNames(entry.getKey(), names);
        }

        // _java/__init__.pyi（namespace marker）
        files.put("nekojs/_java/__init__.pyi", "# Auto-generated namespace marker.\n");

        // 2. Each package module
        for (String pkg : allPkgs) {
            String pkgModule = "nekojs/_java/" + pkg.replace('.', '/') + "/__init__.pyi";
            List<TypeDecl> classes = byPkg.getOrDefault(pkg, List.of());
            files.put(pkgModule, classes.isEmpty()
                    ? "# Auto-generated namespace marker.\n"
                    : renderPackageModule(pkg, classes, classR, typeR, availableFqns, adapterAliases, enumAliases,
                            snapshot, registries));
        }

        // 3. Event declarations: nekojs/_events/<side>/__init__.pyi
        writeEventStubs(files, snapshot, typeR, availableFqns, wideningAliases);

        // 3b. Typed Builder contract declarations: the same structured contract input as the TS backend
        writeRegistryBuilderStubs(files, snapshot);

        // 4. nekojs/__init__.pyi (Global bindings + event group entry + probe.add_global global declarations)
        writeBindingsInit(files, snapshot, availableFqns, typeR, globals);

        // 5. py.typed (PEP 561) + README
        files.put("nekojs/py.typed", "");
        files.put("nekojs/README.md", README_TEXT);

        return files;
    }

    /**
     * Typed Builder contract declarations: {@code nekojs/_registry_builders/__init__.pyi}.
     * Input is {@link com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry}, the same entry list as the TS
     * backend {@code @registry-builders/index.d.ts}: runtime members, TS and Python declarations derived from the
     * same contract reflection input. Member names stay verbatim to the JS side
     * ({@code maxStackSize}/{@code setMaxStackSize}).
     */
    private void writeRegistryBuilderStubs(Map<String, String> files, NekoScriptCatalogSnapshot snapshot) {
        var entries = snapshot.registryBuilderSurfaces();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        files.put("nekojs/_registry_builders/__init__.pyi", RegistryBuilderPyRenderer.render(entries));
    }

    /** Renders the __init__.pyi for a single Java package: cross-package imports + all classes in this package + adapter/enum input aliases for this package. */
    private String renderPackageModule(String javaPkg, List<TypeDecl> classes,
                                       PythonClassRenderer classR, ApiTypeRefPyRenderer typeR,
                                       Set<String> availableFqns, Map<String, PyAdapterAlias> adapterAliases,
                                       Map<String, PyAdapterAlias> enumAliases,
                                       NekoScriptCatalogSnapshot snapshot,
                                       Map<String, RegistryTypeCatalogEntry> registries) {
        PythonImports importByName = packageImports(javaPkg, classes, availableFqns, adapterAliases,
                enumAliases, classR, typeR);
        Map<String, String> symbols = new LinkedHashMap<>();
        for (TypeDecl declaration : classes) symbols.put(declaration.fqn, effectivePyName(declaration));
        symbols.putAll(importByName.typeNames());
        typeR = typeR.withSymbolNames(symbols);
        if (classes.stream().anyMatch(declaration -> !declaration.hidden
                && effectivePyName(declaration).equals("Callable"))) {
            typeR = typeR.withCallableName(classR.typingCallableName(javaPkg));
        }
        classR = classR.withTypeRenderer(typeR);
        Set<String> localTypes = classes.stream().map(declaration -> declaration.fqn)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, PyAdapterAlias> scopedAliases = buildAdapterAliases(snapshot.adapters() == null ? List.of() : snapshot.adapters().stream()
                        .filter(adapter -> adapter.targetType() != null && localTypes.contains(adapter.targetType().getName())).toList(),
                availableFqns, registries, snapshot.modIds(), typeR);
        for (var entry : scopedAliases.entrySet()) {
            PyAdapterAlias allocated = adapterAliases.get(entry.getKey());
            if (allocated != null) entry.setValue(withAliasName(entry.getValue(), allocated.aliasName()));
        }
        adapterAliases = scopedAliases;
        // Adapter input aliases: Cross-package host input types require imports; alias lines are output after classes.
        List<String> aliasLines = new ArrayList<>();
        for (TypeDecl d : classes) {
            PyAdapterAlias alias = adapterAliases.get(d.fqn);
            if (alias == null) continue;
            String line = alias.aliasName() + " = " + effectivePyName(d)
                    + " | " + String.join(" | ", alias.inputTypes());
            // RegistryValue shapes for large registries (>=512 entries) are abbreviated to str, with source registry annotated in trailing comments.
            if (alias.note() != null) line += "  # " + alias.note();
            aliasLines.add(line);
        }
        // Enum literal aliases: Color_ = Color | Literal["RED", ...] (constant order matches PythonClassRenderer.renderEnum)
        for (TypeDecl d : classes) {
            PyAdapterAlias alias = enumAliases.get(d.fqn);
            if (alias == null) continue;
            aliasLines.add(alias.aliasName() + " = " + effectivePyName(d)
                    + " | " + String.join(" | ", alias.inputTypes()));
        }

        // Cross-package imports (only available, cross-package ones; de-duplicated by simple name)

        StringBuilder sb = new StringBuilder();
        sb.append("# Auto-generated by NekoJS probe — Python stubs for Java package ").append(javaPkg).append(".\n");
        sb.append("# Do not edit; regenerate with `/nekojs probe python`.\n\n");
        // Literal imports only when alias uses literal unions in this module (enum aliases / small registry RegistryValue);
        // overload imports only if defs with the same name (Java overloaded methods/constructors) exist.
        boolean usesLiteral = aliasLines.stream().anyMatch(l -> l.contains("Literal["));
        boolean usesOverload = classes.stream().anyMatch(classR::hasRenderedOverloads);
        boolean usesProtocol = classes.stream().anyMatch(classR::hasClassBindings)
                || classes.stream().anyMatch(classR::hasFieldMethodBindings);
        sb.append("from typing import Any, Callable")
          .append(typeR.callableName().equals("Callable") ? "" : " as " + typeR.callableName())
          .append(", ClassVar")
          .append(usesLiteral ? ", Literal" : "")
          .append(usesOverload ? ", overload" : "")
          .append(usesProtocol ? ", Protocol" : "")
          .append("\n");
        importByName.appendTo(sb);
        Set<String> metaclassImports = new TreeSet<>();
        for (TypeDecl declaration : classes) {
            if (!classR.hasClassBindings(declaration)) continue;
            TypeDecl ancestor = classR.metaclassAncestor(declaration);
            if (ancestor != null && !pkgOf(ancestor.fqn).equals(javaPkg)) {
                String original = classR.metaclassName(ancestor);
                String local = classR.metaclassReference(ancestor, javaPkg);
                metaclassImports.add("from nekojs._java." + pkgOf(ancestor.fqn) + " import "
                        + original + (original.equals(local) ? "" : " as " + local));
            }
        }
        for (String statement : metaclassImports) sb.append(statement).append("\n");
        sb.append("\n");
        for (TypeDecl d : classes) {
            String body = classR.render(d);
            if (!body.isEmpty()) sb.append(body).append("\n");
        }
        for (String line : aliasLines) sb.append(line).append("\n");
        return sb.toString();
    }

    private PythonImports packageImports(String javaPkg, List<TypeDecl> classes,
                                               Set<String> availableFqns,
                                               Map<String, PyAdapterAlias> adapterAliases,
                                               Map<String, PyAdapterAlias> enumAliases,
                                               PythonClassRenderer classR, ApiTypeRefPyRenderer typeR) {
        Set<String> references = new LinkedHashSet<>();
        for (TypeDecl declaration : classes) {
            collectDeclSymbolFqns(declaration, references);
            classR.collectFieldProjectionSymbols(declaration, references);
            PyAdapterAlias alias = adapterAliases.get(declaration.fqn);
            if (alias != null) references.addAll(alias.importFqns());
        }
        Set<String> reserved = new LinkedHashSet<>(List.of("Any", "Callable", "ClassVar", "Literal", "overload", "Protocol"));
        for (TypeDecl declaration : classes) {
            if (!declaration.hidden) reserved.add(effectivePyName(declaration));
            PyAdapterAlias alias = adapterAliases.get(declaration.fqn);
            if (alias != null) reserved.add(alias.aliasName());
            PyAdapterAlias enumAlias = enumAliases.get(declaration.fqn);
            if (enumAlias != null) reserved.add(enumAlias.aliasName());
        }
        PythonImports imports = new PythonImports(reserved, typeR);
        for (String fqn : new TreeSet<>(references)) {
            if (availableFqns.contains(fqn) && !pkgOf(fqn).equals(javaPkg)) {
                imports.type(fqn);
            }
        }
        return imports;
    }

    /** Generated input aliases share each package's namespace with its actual public classes. */
    private static void reserveInputAliasNames(Map<String, List<TypeDecl>> byPkg,
                                               Map<String, PyAdapterAlias> adapters,
                                               Map<String, PyAdapterAlias> enums) {
        for (List<TypeDecl> declarations : byPkg.values()) {
            Set<String> reserved = new LinkedHashSet<>(List.of("Any", "Callable", "ClassVar", "Literal", "overload", "Protocol"));
            for (TypeDecl declaration : declarations) {
                if (!declaration.hidden) reserved.add(effectivePyName(declaration));
            }
            for (Map<String, PyAdapterAlias> aliases : List.of(adapters, enums)) {
                for (TypeDecl declaration : declarations) {
                    PyAdapterAlias alias = aliases.get(declaration.fqn);
                    if (alias == null) continue;
                    String name = alias.aliasName();
                    if (reserved.contains(name)) {
                        String prefix = "_NekoInput_" + name;
                        name = prefix;
                        for (int suffix = 1; reserved.contains(name); suffix++) name = prefix + suffix;
                    }
                    reserved.add(name);
                    aliases.put(declaration.fqn, withAliasName(alias, name));
                }
            }
        }
    }

    private static PyAdapterAlias withAliasName(PyAdapterAlias alias, String name) {
        return new PyAdapterAlias(name, alias.inputTypes(), alias.importFqns(), alias.note());
    }

    /** nekojs/__init__.pyi: Global bindings + event group entry + {@code probe.add_global} global declarations (target of {@code from nekojs import *}). */
    private void writeBindingsInit(Map<String, String> files, NekoScriptCatalogSnapshot snapshot, Set<String> availableFqns,
                                   ApiTypeRefPyRenderer typeR, List<GlobalDecl> globals) {
        List<BindingCatalogEntry> bindings = snapshot.bindings().stream()
                .filter(BindingCatalogEntry::emit)
                .toList();

        // Event group name (by side); when binding name hits group name, binding type points to <Group>Type class in nekojs._events/<side>
        Map<ScriptType, List<String>> groupsBySide = eventGroupsBySide(snapshot.events());

        Set<String> reserved = new LinkedHashSet<>(List.of("Any"));
        for (BindingCatalogEntry binding : bindings) {
            if (binding.javaType() == null || !availableFqns.contains(binding.javaType().getName())
                    || !binding.name().equals(typeR.symbolName(binding.javaType().getName()))) {
                reserved.add(binding.name());
            }
        }
        if (globals != null) globals.forEach(global -> reserved.add(global.name()));
        groupsBySide.values().forEach(groups -> groups.forEach(group -> {
            reserved.add(group);
            reserved.add(PythonEventRenderer.groupTypeName(group));
        }));
        PythonImports importByName = new PythonImports(reserved, typeR);
        Set<String> eventImports = new LinkedHashSet<>();   // "from nekojs._events.<side> import <TypeName>"
        List<String[]> lines = new ArrayList<>();            // [name, typeExpr]
        List<String> allNames = new ArrayList<>();

        for (BindingCatalogEntry b : bindings) {
            String type = "Any";
            List<String> groupNames = groupsBySide.getOrDefault(b.scriptType(), List.of());
            if (b.scriptType() != null && groupNames.contains(b.name())) {
                // Event group binding: type = <Group>Type in _events/<side>
                String typeName = PythonEventRenderer.groupTypeName(b.name());
                type = typeName;
                eventImports.add("from nekojs._events." + b.scriptType().name + " import " + typeName);
            } else if (b.typeOverride() != null && !b.typeOverride().isEmpty()) {
                // typeOverride (e.g., "NekoItemHelper"): If a collected FQN simple name equals this,
                // use this class's simple name as binding type and import (preferred over javaType)
                String fqn = findFqnBySimpleName(availableFqns, b.typeOverride());
                if (fqn != null) {
                    type = importByName.type(fqn);
                }
            } else if (b.javaType() != null && availableFqns.contains(b.javaType().getName())) {
                String fqn = b.javaType().getName();
                type = importByName.type(fqn);
            }
            // If import name equals binding name (e.g., `from ... import Items` + `Items` binding),
            // import itself completes re-export; writing `Items: Items` causes pyright self-reference error
            if (!(type.equals(b.name()) && importByName.localNames().contains(type))) {
                lines.add(new String[]{b.name(), type});
            }
            allNames.add(b.name());
        }
        // Event group entry: Group name without corresponding entry in binding directory (DefaultScriptEventBridge exposes event group globally at runtime),
        // backfill pointing to _events class to ensure `ServerEvents.recipes(...)` has completions
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
        // probe.add_global global declaration: type follows py renderer; SYMBOL type imported by availability
        if (globals != null) {
            for (GlobalDecl g : globals) {
                Set<String> fqns = new LinkedHashSet<>();
                ApiTypeRefPyRenderer.collectSymbolFqns(g.type(), fqns);
                for (String fqn : fqns) {
                    if (availableFqns.contains(fqn)) {
                        importByName.type(fqn);
                    }
                }
                lines.add(new String[]{g.name(), typeR.withSymbolNames(importByName.typeNames()).render(g.type())});
                allNames.add(g.name());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Auto-generated by NekoJS probe. Global bindings for `from nekojs import *`.\n");
        sb.append("# The transpiler strips that magic import; pyright resolves globals via this stub.\n");
        sb.append("# Do not edit; regenerate with `/nekojs probe python`.\n\n");
        sb.append("from typing import Any\n");
        importByName.appendTo(sb);
        for (String line : eventImports) sb.append(line).append("\n");
        sb.append("\n");
        for (String[] l : lines) sb.append(l[0]).append(": ").append(l[1]).append("\n");
        sb.append("\n__all__ = [");
        for (int i = 0; i < allNames.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(allNames.get(i)).append("\"");
        }
        sb.append("]\n");
        files.put("nekojs/__init__.pyi", sb.toString());
    }

    /** Event group name for each side (de-duplicated by appearance order); sides without events do not appear. */
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

    /** Finds the class with simple name equal to {@code simpleName} in the set of available FQNs (determinism: sorted by FQN, takes the first). */
    private static String findFqnBySimpleName(Set<String> availableFqns, String simpleName) {
        for (String fqn : new TreeSet<>(availableFqns)) {
            if (ApiTypeRefPyRenderer.simplePyName(fqn).equals(simpleName)) return fqn;
        }
        return null;
    }

    // ============================== Event Declarations ==============================

    /** Writes {@code nekojs/_events/<side>/__init__.pyi} for each side (skips sides without events); outputs root marker as well. */
    private void writeEventStubs(Map<String, String> files, NekoScriptCatalogSnapshot snapshot,
                                 ApiTypeRefPyRenderer typeR, Set<String> availableFqns,
                                 Map<String, PyAdapterAlias> adapterAliases) {
        List<EventCatalogEntry> events = snapshot.events();
        if (events == null || events.isEmpty()) return;

        PythonEventRenderer eventR = new PythonEventRenderer(typeR, availableFqns);
        int count = 0;
        for (ScriptType side : ScriptType.all()) {
            List<EventCatalogEntry> sideEvents = events.stream()
                    .filter(e -> e.scriptType().test(side))
                    .toList();
            if (sideEvents.isEmpty()) continue;
            files.put("nekojs/_events/" + side.name + "/__init__.pyi", eventR.render(side, sideEvents, adapterAliases, snapshot.registryBuilderSurfaces()));
            count++;
        }
        if (count > 0) {
            files.put("nekojs/_events/__init__.pyi", "# Auto-generated namespace marker.\n");
        }
    }

    // ============================== Adapter Input Aliases ==============================

    /**
     * Adapter input aliases (Python version): {@code <Simple>_ = <Simple> | <input types>}.
     * When {@code note} is non-empty, appends {@code # note} comment to alias line (annotates registry name when large registries are abbreviated to str).
     */
    record PyAdapterAlias(String aliasName, List<String> inputTypes, Set<String> importFqns, String note) {}

    /**
     * Builds input aliases (target FQN -> alias) from adapter directory. Only handles collected target classes (declared in IR),
     * avoiding dangling aliases; shapes unmappable to Python (uncollected host/RawValue/missing registry in snapshot) are skipped and logged at debug level.
     *
     * @param registries Registry literal lookup (typeName -> entries), for RegistryValue shape to render Literal unions
     * @param modIds     Loaded mod IDs, for NamespaceValue shape to render Literal unions (takes union with registry namespace)
     */
    private Map<String, PyAdapterAlias> buildAdapterAliases(List<AdapterCatalogEntry> adapters,
                                                            Set<String> availableFqns,
                                                            Map<String, RegistryTypeCatalogEntry> registries,
                                                            List<String> modIds, ApiTypeRefPyRenderer typeR) {
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
                if (shape instanceof AdapterInputShape.SelfValue
                        || shape instanceof AdapterInputShape.HostValue host && target.equals(host.cls())) continue;
                String rendered = renderInputShape(shape, fqn, inputFqns, availableFqns, registries,
                        modIds, largeRegistries, typeR);
                if (rendered != null) inputs.add(rendered);
            }
            if (inputs.isEmpty()) continue;
            String note = largeRegistries.isEmpty() ? null
                    : String.join(", ", largeRegistries) + " registry ids abbreviated as str ("
                      + REGISTRY_LITERAL_LIMIT + "+ entries)";
            // TreeSet: Deterministic iteration (Set.copyOf iteration order has no specification guarantee, causes jitter in .pyi output across JVMs)
            out.put(fqn, new PyAdapterAlias(simple + "_", List.copyOf(inputs), new java.util.TreeSet<>(inputFqns), note));
        }
        return out;
    }

    /**
     * Enum literal input aliases: {@code Color_ = Color | Literal["RED", ...]}.
     * Constant order = {@code decl.fields} order (TypeReflector already sorted stably by name, consistent with renderEnum emission order).
     * Only handles collected and non-hidden enums; empty enums (no constants) are skipped.
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

    /** Enum render name: renameTo preferred, otherwise FQN's Python simple name (consistent with PythonClassRenderer.effectiveClassName). */
    private static String effectivePyName(TypeDecl d) {
        return d.effectiveTypeName() != null ? d.effectiveTypeName() : ApiTypeRefPyRenderer.simplePyName(d.fqn);
    }

    /** Truncation threshold for Literal unions of RegistryValue shapes: Registries >= this count abbreviated to str (annotated in trailing comment). */
    private static final int REGISTRY_LITERAL_LIMIT = 512;

    /**
     * RegistryValue shape -> Python type string: Reuses the same data source as TS {@code @special/types}
     * (entry list from snapshot.registryTypes). Small registries (<512 entries) -> sorted
     * {@code Literal["id", ...]} (escapes aligned with TS generateSpecialTypes: backslash then quote);
     * Large registries -> {@code str} with registry name added to {@code largeRegistries} for trailing comments;
     * Registries missing from snapshot -> null (skips this shape, consistent with previous behavior).
     */
    private String renderRegistryInput(String typeName, Map<String, RegistryTypeCatalogEntry> registries,
                                       List<String> largeRegistries) {
        RegistryTypeCatalogEntry entry = registries.get(typeName);
        if (entry == null) {
            NekoJS.LOGGER.debug("Python probe: skip adapter registry input {} (registry not in snapshot)", typeName);
            return null;
        }
        return renderLiteralUnion(typeName, entry.entries(), largeRegistries);
    }

    /**
     * RegistryTagValue shape -> Python type string: Same source and rules as {@link #renderRegistryInput},
     * but uses {@code tagEntries}. If tags are not collected (e.g., 1.12.2 has no tag system), it falls back to {@code str}, consistent with TS
     * {@code type XTag = string} fallback.
     */
    private String renderRegistryTagInput(String typeName, Map<String, RegistryTypeCatalogEntry> registries,
                                          List<String> largeRegistries) {
        RegistryTypeCatalogEntry entry = registries.get(typeName);
        if (entry == null) {
            NekoJS.LOGGER.debug("Python probe: skip adapter registry tag input {} (registry not in snapshot)", typeName);
            return null;
        }
        return renderLiteralUnion(typeName + "Tag", entry.tagEntries(), largeRegistries);
    }

    /**
     * NamespaceValue shape -> Python type string: Same source and rules as TS {@code RegistryTypes.Namespace}
     * - takes the union of loader mod list ({@code snapshot.modIds()}) and {@code ':'} prefixes of registry entry IDs.
     * If both are empty (snapshot from platform-less unit test), falls back to {@code str}, consistent with TS.
     */
    private String renderNamespaceInput(List<String> modIds, Map<String, RegistryTypeCatalogEntry> registries,
                                        List<String> largeRegistries) {
        Set<String> namespaces = new java.util.TreeSet<>(modIds);
        for (RegistryTypeCatalogEntry entry : registries.values()) {
            for (String id : entry.entries()) {
                if (id == null || id.isBlank()) continue;
                int colon = id.indexOf(':');
                namespaces.add(colon < 0 ? id : id.substring(0, colon));
            }
        }
        namespaces.remove("");
        return renderLiteralUnion("Namespace", List.copyOf(namespaces), largeRegistries);
    }

    private String renderLiteralUnion(String typeName, List<String> rawEntries, List<String> largeRegistries) {
        List<String> entries = new ArrayList<>(rawEntries);
        if (entries.isEmpty()) return "str"; // TS @special also falls back to string for empty registries
        if (entries.size() >= REGISTRY_LITERAL_LIMIT) {
            largeRegistries.add(typeName);
            return "str";
        }
        Collections.sort(entries); // Determinism: Does not rely on snapshot entry order
        StringBuilder sb = new StringBuilder("Literal[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('"').append(entries.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    /** Single input shape -> Python type string; returns null if unmappable (raw TS fragment/uncollected host). */
    private String renderInputShape(AdapterInputShape shape, String selfFqn,
                                    Set<String> inputFqns, Set<String> availableFqns,
                                    Map<String, RegistryTypeCatalogEntry> registries,
                                    List<String> modIds, List<String> largeRegistries, ApiTypeRefPyRenderer typeR) {
        return switch (shape) {
            case AdapterInputShape.StringValue v -> "str";
            case AdapterInputShape.LiteralValue v ->
                    "Literal[\"" + v.text().replace("\\", "\\\\").replace("\"", "\\\"") + "\"]";
            case AdapterInputShape.NumberValue v -> "float";
            case AdapterInputShape.BooleanValue v -> "bool";
            case AdapterInputShape.SelfValue v -> typeR.render(ApiTypeRef.symbol(new ApiSymbolId("java", selfFqn)));
            case AdapterInputShape.HostValue v -> {
                Class<?> cls = v.cls();
                if (cls == null) yield null;
                String fqn = cls.getName();
                if (!availableFqns.contains(fqn)) {
                    NekoJS.LOGGER.debug("Python probe: skip adapter host input {}, class not collected", fqn);
                    yield null;
                }
                inputFqns.add(fqn);
                yield typeR.render(ApiTypeRef.symbol(new ApiSymbolId("java", fqn)));
            }
            case AdapterInputShape.ArrayOfValue v -> {
                String elem = renderInputShape(v.element(), selfFqn, inputFqns, availableFqns, registries,
                        modIds, largeRegistries, typeR);
                yield elem == null ? null : "list[" + elem + "]";
            }
            case AdapterInputShape.ObjectValue v -> "dict[str, Any]";
            case AdapterInputShape.UnionValue v -> {
                List<String> members = new ArrayList<>();
                for (AdapterInputShape member : v.members()) {
                    String rendered = renderInputShape(member, selfFqn, inputFqns, availableFqns,
                            registries, modIds, largeRegistries, typeR);
                    if (rendered != null) members.add(rendered);
                }
                yield members.isEmpty() ? null : "(" + String.join(" | ", members) + ")";
            }
            case AdapterInputShape.RegistryValue v ->
                    renderRegistryInput(v.typeName(), registries, largeRegistries);
            case AdapterInputShape.RegistryTagValue v ->
                    renderRegistryTagInput(v.typeName(), registries, largeRegistries);
            case AdapterInputShape.NamespaceValue v -> renderNamespaceInput(modIds, registries, largeRegistries);
            // Python lacks template literal types: Whole template degrades to str (prefix/suffix syntax cannot be expressed, field semantics already covered by doc)
            case AdapterInputShape.TemplateValue v -> "str";
            case AdapterInputShape.NamedValue v -> renderInputShape(v.shape(), selfFqn, inputFqns, availableFqns,
                    registries, modIds, largeRegistries, typeR);
            case AdapterInputShape.CallableValue v -> {
                List<String> parameters = new ArrayList<>();
                boolean complete = true;
                for (AdapterInputShape.Slot parameter : v.parameters()) {
                    String rendered = renderInputShape(parameter.shape(), selfFqn, inputFqns, availableFqns,
                            registries, modIds, largeRegistries, typeR);
                    if (rendered == null) { complete = false; break; }
                    parameters.add(rendered);
                }
                String result = renderInputShape(v.result(), selfFqn, inputFqns, availableFqns,
                        registries, modIds, largeRegistries, typeR);
                yield !complete || result == null ? null : typeR.callableName() + "[[" + String.join(", ", parameters) + "], " + result + "]";
            }
            case AdapterInputShape.RawValue v -> {
                NekoJS.LOGGER.debug("Python probe: skip adapter raw TS input '{}'", v.ts());
                yield null;
            }
        };
    }

    // ============================== Import Collection ==============================

    private static void collectDeclSymbolFqns(TypeDecl d, Set<String> out) {
        // Python enums use their dedicated surface rather than the Java superclass.
        if (d.kind != TypeDecl.Kind.ENUM && d.superType != null) {
            ApiTypeRefPyRenderer.collectSymbolFqns(d.superType.ref, out);
        }
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
