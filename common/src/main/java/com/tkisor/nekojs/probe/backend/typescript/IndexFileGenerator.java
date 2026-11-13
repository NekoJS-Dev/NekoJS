package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;

import java.lang.reflect.*;
import java.util.*;

/**
 * Generates the index.d.ts files: one module declaration file per package.
 *
 * <p>The layout follows ProbeJS:
 * <pre>
 * import { $ClassA } from "java:other/package";
 * export * as subpackage from "java:package/subpackage";
 *
 * declare module "java:package" {
 *     export class $ClassA { ... }
 * }
 * </pre>
 */
public final class IndexFileGenerator {
    private final TypeScriptClassRenderer irRenderer;
    private final AdapterAliasGenerator adapterAliasGenerator;
    private final FunctionalInterfaceAliasGenerator functionalInterfaceAliasGenerator;

    // Caches used for performance. They are thread-safe, so generation can run in parallel.
    private final java.util.concurrent.ConcurrentHashMap<String, Class<?>> classCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, String> declCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Set<String>> importCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, TypeDecl> irCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Set<String>> referenceCache = new java.util.concurrent.ConcurrentHashMap<>();
    /** fqn → "host->exportedName" rendering references; drives view-name imports precisely. */
    private final java.util.concurrent.ConcurrentHashMap<String, Set<String>> nameReferenceCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> registryShapeClasses = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean authoringIndexEnabled;
    private final java.util.concurrent.ConcurrentHashMap<String, com.tkisor.nekojs.probe.ir.TypeScriptAuthoringEmission.Declaration> authoringDeclarations = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> authoredClasses = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /**
     * Cache of enum input aliases: FQN to the alias name plus the declaration line emitted next to the
     * enum. predeclareClass computes an entry from the {@link TypeDecl}, and generate() emits it inside
     * the module of the package that holds the enum ({@code $Color_ = $Color | "RED" | ...}). Parameter
     * widening is handled by the lazy alias resolution that
     * {@link com.tkisor.nekojs.probe.types.TypeAliasRegistry} performs for enum FQNs, the same path
     * adapter aliases take through registerClassAlias.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, EnumAlias> enumAliasCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * One enum input alias: the alias name (such as {@code $DyeColor_}) plus the complete declaration
     * line, including the trailing newline.
     */
    private record EnumAlias(String aliasName, String declaration) {}

    /**
     * FQNs of the classes that {@code probe.modify_type} hides. generate filters the imports and type
     * aliases of these classes so other classes cannot keep a dangling reference to a hidden class
     * through the imports collected by reflection. The backend sets the set through
     * {@link #setHiddenClasses(Set)} before every generate; {@link #clearCaches()} does not clear it, the
     * backend sets it again.
     */
    private volatile Set<String> hiddenClasses = Set.of();

    public IndexFileGenerator(TypeScriptClassRenderer irRenderer, AdapterAliasGenerator adapterAliasGenerator) {
        this(irRenderer, adapterAliasGenerator,
                new FunctionalInterfaceAliasGenerator(adapterAliasGenerator.aliasRegistry()));
    }

    public IndexFileGenerator(TypeScriptClassRenderer irRenderer, AdapterAliasGenerator adapterAliasGenerator,
                              FunctionalInterfaceAliasGenerator functionalInterfaceAliasGenerator) {
        this.irRenderer = irRenderer;
        this.adapterAliasGenerator = adapterAliasGenerator;
        this.functionalInterfaceAliasGenerator = functionalInterfaceAliasGenerator;
    }

    public void prepareFunctionalInterfaceAliases(Set<String> generatedClasses, Set<String> hiddenClasses) {
        functionalInterfaceAliasGenerator.prepare(generatedClasses, hiddenClasses);
    }

    /**
     * Generates the index.d.ts content for one package node.
     */
    public String generate(String packageName, List<String> classNames, List<String> subpackages,
                           Set<String> allClasses) {
        StringBuilder sb = new StringBuilder();
        String modulePath = "java:" + packageName.replace('.', '/');

        // Collect the classes that need an import. predeclareClass already computed them from the IR and
        // cached them; a missing cache entry means reflection failed for that class, so it is skipped.
        Set<String> importsNeeded = new LinkedHashSet<>();
        for (String simpleName : classNames) {
            String fullName = packageName + "." + simpleName;
            Set<String> cached = importCache.get(fullName);
            if (cached != null) {
                importsNeeded.addAll(cached);
            }
        }

        // Merge the cross-package types that adapter input aliases reference (such as $Item, $NekoId) and
        // record whether any of them references an @special registry literal.
        boolean moduleUsesRegistry = classNames.stream()
                .anyMatch(name -> registryShapeClasses.contains(packageName + "." + name));
        for (String simpleName : classNames) {
            AdapterAliasGenerator.AdapterAlias alias = adapterAliasGenerator.getAlias(packageName + "." + simpleName);
            if (alias == null) continue;
            importsNeeded.addAll(alias.importFqns());
            if (alias.usesRegistry()) moduleUsesRegistry = true;
        }

        // A reference to an @special registry literal needs an import of the RegistryTypes namespace.
        if (moduleUsesRegistry) {
            sb.append("import type { RegistryTypes } from \"@special/types\";\n");
        }

        // Drop the hidden classes. The reflection-based import collection of other classes still
        // references them, which would leave dangling names, so they are removed here.
        if (!hiddenClasses.isEmpty()) {
            importsNeeded.removeIf(hiddenClasses::contains);
        }

        Set<TypeScriptImportNames.Symbol> importedSymbols = new LinkedHashSet<>();
        for (String fqn : importsNeeded) {
            for (String name : exportedSymbols(fqn)) importedSymbols.add(new TypeScriptImportNames.Symbol(fqn, name));
        }
        Map<String, String> captureNames = irRenderer.captureViewNames();
        Set<String> localCaptureNames = irRenderer.captureLocalNames(packageName);
        for (String simpleName : classNames) {
            Set<String> references = nameReferenceCache.getOrDefault(packageName + "." + simpleName, Set.of());
            captureNames.forEach((fqn, name) -> {
                if (references.contains(fqn + "->" + name) && !packageOf(fqn).equals(packageName)) {
                    importedSymbols.add(new TypeScriptImportNames.Symbol(fqn, name));
                }
            });
        }
        if (!localCaptureNames.isEmpty()) {
            // Observe the same complete projection before allocating module-local import bindings.
            irRenderer.renderCaptureViews(packageName, adapterAliasGenerator.aliasRegistry().withReferences((fqn, name) -> {
                if (name.startsWith("$") && !packageOf(fqn).equals(packageName) && !hiddenClasses.contains(fqn)) {
                    importsNeeded.add(fqn);
                    importedSymbols.add(new TypeScriptImportNames.Symbol(fqn, name));
                }
                return name;
            }));
        }
        Set<String> declaredNames = new LinkedHashSet<>();
        Set<String> authoredImports = new LinkedHashSet<>();
        for (String simpleName : classNames) {
            String fqn = packageName + "." + simpleName;
            if (!hasDeclaration(fqn)) continue;
            declaredNames.addAll(exportedSymbols(fqn));
            TypeDecl decl = irCache.get(fqn);
            if (decl != null && decl.effectiveTypeName() != null) declaredNames.add("$" + decl.effectiveTypeName());
            if (authoredClasses.contains(fqn)) authoredImports.addAll(importCache.getOrDefault(fqn, Set.of()));
        }
        // Local input sections reserve symbols before imports are allocated. They are not extra host imports.
        for (String simpleName : classNames) {
            String fqn = packageName + "." + simpleName;
            if (hiddenClasses.contains(fqn)) continue;
            var adapter = adapterAliasGenerator.getAlias(fqn);
            if (adapter == null) continue;
            for (var named : adapter.namedAliases()) {
                if (!declaredNames.add(named.aliasName())) {
                    throw new IllegalArgumentException("[NEKO-4063] Named input section collides with a module declaration: "
                            + named.aliasName());
                }
            }
        }
        Set<String> extraImports = new LinkedHashSet<>();
        for (String simpleName : classNames) {
            extraImports.addAll(irRenderer.getExtraImports(packageName + "." + simpleName));
        }
        // The locally declared view names must not be stolen by an import binding.
        declaredNames.addAll(localCaptureNames);
        Set<TypeScriptImportNames.Symbol> authoredSymbols = new LinkedHashSet<>();
        for (TypeScriptImportNames.Symbol symbol : importedSymbols) {
            if (authoredImports.contains(symbol.fqn())) authoredSymbols.add(symbol);
        }
        TypeScriptImportNames importNames = new TypeScriptImportNames(
                importedSymbols, declaredNames, extraImports, authoredSymbols);
        TypeAliasRegistry moduleAliases = importNames.hasAliases()
                ? adapterAliasGenerator.aliasRegistry().withReferences(importNames::reference)
                : adapterAliasGenerator.aliasRegistry();

        // Emit the import statements, grouped by package. Symbol names inside a package are sorted
        // alphabetically so the output is identical on every JVM run: reflection gives no guaranteed
        // order for getInterfaces/getDeclaredMethods, and keeping first-insertion order makes the output
        // drift between runs.
        if (!importsNeeded.isEmpty()) {
            Map<String, List<String>> importsByPackage = new TreeMap<>();
            for (var binding : importNames.bindings().entrySet()) {
                String fqn = binding.getKey().fqn();
                int dot = fqn.lastIndexOf('.');
                if (dot < 0) continue; // Classes in the default package cannot be imported through a module path, so skip them.
                String pkg = fqn.substring(0, dot);
                List<String> names = importsByPackage.computeIfAbsent(pkg, k -> new ArrayList<>());
                String exported = binding.getKey().exportedName();
                String local = binding.getValue();
                names.add(exported.equals(local) ? exported : exported + " as " + local);
            }

            for (var entry : importsByPackage.entrySet()) {
                String importPath = "java:" + entry.getKey().replace('.', '/');
                List<String> names = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
                Collections.sort(names);
                sb.append("import { ").append(String.join(", ", names));
                sb.append(" } from \"").append(importPath).append("\";\n");
            }
            sb.append("\n");
        }

        // Extra import statements, produced by TypeScriptClassRenderer.overrideGetter.
        for (String stmt : extraImports) {
            sb.append(stmt).append("\n");
        }
        if (!extraImports.isEmpty()) {
            sb.append("\n");
        }

        // Emit the re-exports of the subpackages.
        for (String sub : subpackages) {
            sb.append("export * as ").append(sub).append(" from \"")
              .append(modulePath).append("/").append(sub).append("\";\n");
        }
        if (!subpackages.isEmpty()) {
            sb.append("\n");
        }

        // Emit the module declaration, only when the package has classes.
        if (!classNames.isEmpty()) {
            sb.append("declare module \"").append(modulePath).append("\" {\n");

            for (String simpleName : classNames) {
                String fullName = packageName + "." + simpleName;
                String cached = declCache.get(fullName);
                if (cached != null) {
                    if (authoringIndexEnabled && irCache.containsKey(fullName)) {
                        var indexed = irRenderer.renderIndexed(irCache.get(fullName), moduleAliases);
                        cached = indexed.text();
                        authoringDeclarations.put(fullName, indexed);
                    } else if (importNames.hasAliases() && irCache.containsKey(fullName)) {
                        cached = irRenderer.render(irCache.get(fullName), moduleAliases);
                    }
                    sb.append(cached);
                    appendCanonicalExports(sb, fullName, irCache.get(fullName), moduleAliases);
                    sb.append("\n");
                }
            }

            // Emit the type aliases: adapter-driven input aliases first (such as $ItemStack_), then the
            // enum literal aliases ($DyeColor_ = $DyeColor | "RED" | ...), and otherwise the collection
            // and leftover aliases.
            for (String simpleName : classNames) {
                String fullName = packageName + "." + simpleName;
                if (hiddenClasses.contains(fullName)) continue;
                AdapterAliasGenerator.AdapterAlias adapterAlias = importNames.hasAliases()
                        ? adapterAliasGenerator.getAlias(fullName, moduleAliases) : adapterAliasGenerator.getAlias(fullName);
                if (adapterAlias != null) {
                    adapterAlias.doc().ifPresent(doc ->
                        sb.append("    /** ").append(doc.replace("\n", "\n     * ")).append(" */\n"));
                    sb.append(adapterAlias.declarations("    "));
                    continue;
                }
                EnumAlias enumAlias = enumAliasCache.get(fullName);
                if (enumAlias != null) {
                    sb.append(enumAlias.declaration());
                    String canonicalAlias = "$" + simpleName + "_";
                    if (!enumAlias.aliasName().equals(canonicalAlias)) {
                        sb.append("    export type ").append(canonicalAlias).append(" = ")
                                .append(enumAlias.aliasName()).append(";\n");
                    }
                    continue;
                }
                String functionalAlias = functionalInterfaceAliasGenerator.declaration(fullName, moduleAliases);
                if (functionalAlias != null) {
                    sb.append(functionalAlias);
                    continue;
                }
                String alias = generateTypeAlias(fullName, simpleName);
                if (alias != null) {
                    sb.append(alias);
                }
            }

            if (!localCaptureNames.isEmpty()) {
                sb.append(irRenderer.renderCaptureViews(packageName, moduleAliases));
            }

            sb.append("}\n");
        }
        return sb.toString();
    }

    /** Retains Java identity after an IR rename; declaration statements also work in module augmentations. */
    private void appendCanonicalExports(StringBuilder output, String fqn, TypeDecl declaration,
                                        TypeAliasRegistry aliases) {
        if (declaration == null || declaration.hidden || declaration.effectiveTypeName() == null) return;
        String canonical = "$" + fqn.substring(fqn.lastIndexOf('.') + 1);
        String renamed = "$" + declaration.effectiveTypeName();
        if (canonical.equals(renamed)) return;
        StringJoiner parameters = new StringJoiner(", ", "<", ">");
        StringJoiner arguments = new StringJoiner(", ", "<", ">");
        for (TypeDecl.TypeParam parameter : declaration.typeParams) {
            parameters.add(TypeScriptClassRenderer.renderTypeParameter(parameter, aliases));
            arguments.add(parameter.name);
        }
        output.append("    export type ").append(canonical)
                .append(declaration.typeParams.isEmpty() ? "" : parameters)
                .append(" = ").append(renamed)
                .append(declaration.typeParams.isEmpty() ? "" : arguments).append(";\n");
        if (irRenderer.hasClassValue(declaration)) {
            output.append("    export const ").append(canonical).append(": typeof ").append(renamed).append(";\n");
        }
    }

    private Set<String> exportedSymbols(String fqn) {
        Set<String> names = new LinkedHashSet<>();
        names.add("$" + fqn.substring(fqn.lastIndexOf('.') + 1));
        String collectionInput = TypeAliasRegistry.collectionInputAlias(fqn);
        if (collectionInput != null) names.add(collectionInput);
        AdapterAliasGenerator.AdapterAlias adapter = adapterAliasGenerator.getAlias(fqn);
        if (adapter != null) names.add(adapter.aliasName());
        TypeAliasRegistry.FunctionalInterfaceAlias functional = functionalInterfaceAliasGenerator.getAlias(fqn);
        if (functional != null) names.add(functional.aliasName());
        EnumAlias enumeration = enumAliasCache.get(fqn);
        if (enumeration != null) {
            names.add(enumeration.aliasName());
            names.add("$" + fqn.substring(fqn.lastIndexOf('.') + 1) + "_");
        }
        return names;
    }

    /**
     * Generates the root index.d.ts, which re-exports every top-level package.
     */
    public String generateRoot(List<String> topPackages) {
        StringBuilder sb = new StringBuilder();
        sb.append("// NekoJS Probe Type Declarations\n\n");

        for (String pkg : topPackages) {
            sb.append("export * as ").append(pkg).append(" from \"java:").append(pkg).append("\";\n");
        }

        return sb.toString();
    }

    private Class<?> findClass(String fullName) {
        return classCache.computeIfAbsent(fullName, name -> {
            try {
                return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                return null;
            }
        });
    }

    private void collectTypeImports(Type type, Set<String> imports, String currentPackage) {
        if (type instanceof Class<?> cls) {
            // The class is an array: collect the component type recursively, so the "[Lnet/.../Foo;"
            // descriptor never leaks into an import.
            if (cls.isArray()) {
                collectTypeImports(cls.getComponentType(), imports, currentPackage);
                return;
            }
            // Primitive TS mappings (including String and boxed scalars) never reference a Java symbol.
            if (com.tkisor.nekojs.probe.ir.TypeReflector.toRef(cls).kind()
                    == com.tkisor.nekojs.api.surface.ApiTypeRef.Kind.SYMBOL
                    && !inSamePackage(cls, currentPackage)) {
                imports.add(cls.getName());
            }
        } else if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> rawCls) {
                if (!inSamePackage(rawCls, currentPackage)) {
                    imports.add(rawCls.getName());
                }
            }
            for (Type arg : com.tkisor.nekojs.core.reflect.JavaGenericTypes.arguments(pt)) {
                collectTypeImports(arg, imports, currentPackage);
            }
        } else if (type instanceof GenericArrayType gat) {
            collectTypeImports(gat.getGenericComponentType(), imports, currentPackage);
        } else if (type instanceof WildcardType wildcard) {
            for (Type upper : wildcard.getUpperBounds()) {
                collectTypeImports(upper, imports, currentPackage);
            }
            for (Type lower : wildcard.getLowerBounds()) {
                collectTypeImports(lower, imports, currentPackage);
            }
        }
    }

    private boolean inSamePackage(Class<?> cls, String packageName) {
        if (cls.getPackage() == null) return packageName.isEmpty();
        return cls.getPackage().getName().equals(packageName);
    }

    /** Generates fallback aliases, sharing native collection input rules with ordinary parameter rendering. */
    private String generateTypeAlias(String fullName, String simpleName) {
        // No alias is generated for a hidden class: its declaration is empty, so the alias would
        // reference $Hidden and dangle.
        if (hiddenClasses.contains(fullName)) return null;

        // Types that need an alias: the full name, the type parameter count and the alias template.
        // In the template, {0} is the first type parameter and {1} is the second one.
        String alias = TypeAliasRegistry.collectionInputAlias(fullName) != null ? "COLLECTION_INPUT" : switch (fullName) {
            // Native List/Collection/Map conversions do not extend to concrete classes or subinterfaces.
            case "java.util.ArrayList", "java.util.LinkedList", "java.util.SequencedCollection",
                 "java.util.HashSet", "java.util.TreeSet", "java.util.LinkedHashSet", "java.util.SequencedSet",
                 "java.util.HashMap", "java.util.TreeMap", "java.util.LinkedHashMap",
                 "java.util.SortedMap", "java.util.SequencedMap" -> "HOST_INPUT";
            case "java.util.Optional" ->
                    "{0} | null";
            case "java.util.Iterator", "java.util.Spliterator",
                 "java.util.stream.Stream", "java.util.stream.IntStream",
                 "java.util.stream.LongStream", "java.util.stream.DoubleStream" ->
                    "{0}[]";
            // ========== Leftover input aliases with no adapter, mapped to a fixed type ==========
            case "net.minecraft.world.item.Items" ->
                    "NON_GENERIC:$Items";
            case "net.minecraft.core.BlockPos" ->
                    "NON_GENERIC:$BlockPos | [number, number, number]";
            case "net.minecraft.world.level.block.state.BlockState" ->
                    "NON_GENERIC:$BlockState | string";
            case "net.minecraft.core.registries.BuiltInRegistries" ->
                    "NON_GENERIC:$BuiltInRegistries";
            default -> null;
        };

        if (alias == null) return null;

        Class<?> cls = findClass(fullName);
        if (cls == null) return null;

        // Non-generic adapter input aliases (such as $ItemStack_ = $ItemStack | string). They have no type
        // parameters, so the fixed type is emitted directly.
        if (alias.startsWith("NON_GENERIC:")) {
            return "    export type $" + simpleName + "_ = " + alias.substring("NON_GENERIC:".length()) + ";\n";
        }

        TypeVariable<?>[] typeParams = cls.getTypeParameters();
        if (typeParams.length == 0) return null;

        // Build the type parameter list.
        StringJoiner paramJoiner = new StringJoiner(", ");
        for (TypeVariable<?> tp : typeParams) {
            paramJoiner.add(tp.getName());
        }

        // Replace the placeholders in the template.
        String result = switch (alias) {
            case "COLLECTION_INPUT" -> adapterAliasGenerator.aliasRegistry().getCollectionAlias(fullName,
                    Arrays.stream(typeParams).map(TypeVariable::getName).toArray(String[]::new));
            case "HOST_INPUT" -> "$" + simpleName + "<" + paramJoiner + ">";
            default -> alias;
        };
        for (int i = 0; i < typeParams.length; i++) {
            result = result.replace("{" + i + "}", typeParams[i].getName());
        }

        String inputName = TypeAliasRegistry.collectionInputAlias(fullName);
        return "    export type " + (inputName == null ? "$" + simpleName + "_" : inputName)
                + "<" + paramJoiner + "> = " + result + ";\n";
    }

    // ------------------------------------------------------------------
    //  IR path: one reflection pass produces a TypeDecl, which yields the declaration and the import set.
    // ------------------------------------------------------------------

    /** Collects imports from reflected heritage, generic bounds, fields and executable signatures. */
    public Set<String> collectImportsFromIr(TypeDecl decl, String currentPackage) {
        Set<String> imports = new LinkedHashSet<>();

        // Heritage slots retain generic arguments so their referenced types need imports too.
        if (decl.superType != null) {
            collectTypeImports(decl.superType.sourceType, imports, currentPackage);
        }

        // Interfaces.
        for (TypeSlot iface : decl.interfaces) {
            collectTypeImports(iface.sourceType, imports, currentPackage);
        }
        for (TypeDecl.TypeParam parameter : decl.typeParams) {
            for (TypeSlot bound : parameter.bounds()) collectTypeImports(bound.sourceType, imports, currentPackage);
        }
        for (MethodDecl constructor : decl.constructors) {
            for (MethodDecl.MethodParam parameter : constructor.params) {
                collectTypeImports(parameter.type.sourceType, imports, currentPackage);
            }
        }

        // Public fields. The IR field set matches the public field set of the earlier implementation and
        // includes the enum constants.
        for (FieldDecl field : decl.fields) {
            collectTypeImports(field.type.sourceType, imports, currentPackage);
        }

        // Bounds and parameters share the same signature metadata as declaration rendering.
        for (MethodDecl method : decl.methods) {
            for (List<TypeSlot> bounds : method.typeParameterBounds.values()) {
                for (TypeSlot bound : bounds) collectTypeImports(bound.sourceType, imports, currentPackage);
            }
            collectTypeImports(method.returnType != null ? method.returnType.sourceType : null, imports, currentPackage);
            for (MethodDecl.MethodParam p : method.params) {
                collectTypeImports(p.type.sourceType, imports, currentPackage);
            }
        }
        return imports;
    }

    /**
     * Predeclares a class on the IR path: renders the {@link TypeDecl} with
     * {@link TypeScriptClassRenderer} and caches the declaration and the import set, so one reflection
     * pass feeds both outputs. {@code extraImportFqns} holds the extra SYMBOL fully qualified names that
     * a {@code modify_type} edit introduced, and may be empty.
     */
    public void predeclareClass(String fqn, TypeDecl decl, Set<String> extraImportFqns) {
        irCache.put(fqn, decl);
        authoredClasses.remove(fqn);
        registerEnumAlias(fqn, decl);
        String packageName = packageOf(fqn);
        Set<String> imports = new LinkedHashSet<>();
        Set<String> referencesNeeded = new LinkedHashSet<>();
        Set<String> referencedNames = new LinkedHashSet<>();
        // Contextual callback arguments can introduce host types absent from the Java parameter.
        // Observe the same rendering references used for module-local names instead of expanding a second signature.
        TypeAliasRegistry references = adapterAliasGenerator.aliasRegistry().withReferences((host, name) -> {
            if (name.startsWith("$")) {
                referencesNeeded.add(host);
                referencedNames.add(host + "->" + name);
                if (!packageOf(host).equals(packageName)) imports.add(host);
            }
            return name;
        });
        declCache.put(fqn, irRenderer.render(decl, references));
        registryShapeClasses.remove(fqn);
        if (usesRegistryInputShapes(decl)) registryShapeClasses.add(fqn);
        functionalInterfaceAliasGenerator.declaration(fqn, references);
        if (extraImportFqns != null) {
            imports.addAll(extraImportFqns);
            referencesNeeded.addAll(extraImportFqns);
        }
        importCache.put(fqn, imports);
        referenceCache.put(fqn, Set.copyOf(referencesNeeded));
        nameReferenceCache.put(fqn, Set.copyOf(referencedNames));
        if (decl.sourceClass != null) {
            classCache.put(fqn, decl.sourceClass);
        }
    }

    private boolean usesRegistryInputShapes(TypeDecl declaration) {
        List<MethodDecl> methods = new ArrayList<>(declaration.methods);
        methods.addAll(declaration.constructors);
        for (MethodDecl method : methods) {
            if (method.hidden) continue;
            for (MethodDecl.MethodParam parameter : method.params) {
                if (usesRegistryInputShape(parameter.type)) return true;
            }
            if (usesRegistryInputShape(method.setterParamType)) return true;
        }
        return false;
    }

    private boolean usesRegistryInputShape(TypeSlot slot) {
        return slot != null && !slot.overridden && slot.inputShape != null
                && AdapterAliasGenerator.renderShape(slot.inputShape, adapterAliasGenerator.aliasRegistry(), true)
                .usesRegistry();
    }

    /**
     * Computes and caches the input alias declaration for an enum {@link TypeDecl}
     * ({@code $Color_ = $Color | "RED" | ...}), so {@link #generate} can emit it next to the enum inside
     * the module of the package that holds it. The constant order matches the static constant emission
     * of {@code TypeScriptClassRenderer.renderEnum} exactly, because it uses the same
     * {@code decl.fields} order, which TypeReflector has already sorted by name. Nothing is truncated:
     * as in ProbeJS, long enums still get the full literal union.
     *
     * <p>Skipped: types that are not enums; hidden enums, whose declaration is empty so the alias would
     * dangle; and synthetic enums that have no sourceClass and were not renamed, because no stable name
     * consistent with the declaration can be derived. When an adapter targets an enum, the adapter alias
     * wins (decided in generate).
     */
    private void registerEnumAlias(String fqn, TypeDecl decl) {
        if (decl.kind != TypeDecl.Kind.ENUM || decl.hidden) return;
        String name = decl.effectiveTypeName() != null ? decl.effectiveTypeName() : tsEnumName(decl.sourceClass);
        if (name == null) return;
        String aliasName = "$" + name + "_";
        StringBuilder union = new StringBuilder(aliasName).append(" = $").append(name);
        for (FieldDecl f : decl.fields) {
            if (!f.hidden && f.isEnumConstant) {
                // Enum constant names are valid Java identifiers, so they are wrapped in double quotes as
                // is, with the same name as the static constant that renderEnum emits.
                union.append(" | \"").append(f.effectiveName()).append("\"");
            }
        }
        enumAliasCache.put(fqn, new EnumAlias(aliasName,
                "    export type " + union + ";\n"));
    }

    /**
     * Enum name used for rendering. It mirrors TypeScriptClassRenderer.tsClassName and gives
     * Parent$Child for nested classes. Returns null when there is no source class.
     */
    private static String tsEnumName(Class<?> cls) {
        if (cls == null) return null;
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return tsEnumName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }

    private static String packageOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(0, dot) : "";
    }

    /**
     * Sets the FQNs of the hidden classes to filter during this generation; an empty set clears the
     * hidden state of the previous run. {@code TypeScriptProbeBackend} calls this before each generate.
     */
    public void setHiddenClasses(Set<String> hidden) {
        this.hiddenClasses = hidden == null || hidden.isEmpty() ? Set.of() : Set.copyOf(hidden);
    }

    /** Replaces one class body while retaining the package generator and explicit dependency imports. */
    public void predeclareClassDeclaration(String fqn, String declaration, Set<String> importFqns,
                                          TypeDecl reflected) {
        irCache.remove(fqn);
        authoredClasses.add(fqn);
        if (reflected != null) registerEnumAlias(fqn, reflected);
        StringBuilder body = new StringBuilder();
        for (String line : declaration.split("\n", -1)) {
            if (!line.isBlank()) body.append("    ").append(line);
            body.append('\n');
        }
        declCache.put(fqn, body.toString());
        String currentPackage = packageOf(fqn);
        Set<String> imports = new LinkedHashSet<>();
        for (String imported : importFqns) {
            if (!packageOf(imported).equals(currentPackage)) imports.add(imported);
        }
        importCache.put(fqn, imports);
        referenceCache.put(fqn, Set.copyOf(importFqns));
    }

    /** Includes same-package references, which require declarations even though they need no import. */
    public Set<String> referencedClasses(String fqn) {
        return referenceCache.getOrDefault(fqn, Set.of());
    }

    public boolean hasDeclaration(String fqn) {
        String declaration = declCache.get(fqn);
        return declaration != null && !declaration.isBlank();
    }

    /** Freezes the completed edited IR before re-rendering inheritance-dependent class values. */
    void prepareClassInheritance() {
        Map<String, TypeDecl> declarations = Map.copyOf(irCache);
        Set<String> projected = irRenderer.setClassDeclarations(declarations);
        // Parent signatures already participated in closure discovery; only affected class caches need rendering again.
        for (String fqn : new TreeSet<>(projected)) {
            predeclareClass(fqn, declarations.get(fqn), Set.copyOf(importCache.getOrDefault(fqn, Set.of())));
        }
    }

    void enableAuthoringIndex() { authoringIndexEnabled = true; }

    /** Transfers member records to their sole owner after all package rendering has joined. */
    Map<String, com.tkisor.nekojs.probe.ir.TypeScriptAuthoringEmission.Declaration> takeAuthoringDeclarations() {
        var result = Map.copyOf(authoringDeclarations);
        authoringDeclarations.clear();
        return result;
    }

    /** Clears the declaration caches after rendering. */
    public void clearCaches() {
        irRenderer.setClassDeclarations(Map.of());
        classCache.clear();
        declCache.clear();
        importCache.clear();
        enumAliasCache.clear();
        irCache.clear();
        referenceCache.clear();
        nameReferenceCache.clear();
        registryShapeClasses.clear();
        authoredClasses.clear();
        authoringDeclarations.clear();
        authoringIndexEnabled = false;
    }
}
