package com.tkisor.nekojs.probe.ir;

import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.api.catalog.BeanMethodProjectionCatalogEntry;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.JavaTypeScriptProjection;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeScriptTypeExpressions;
import com.tkisor.nekojs.probe.types.TypeScriptDeclarationFormatter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders shared declarations as TypeScript class, interface and enum blocks.
 * A slot renders through its neutral {@link ApiTypeRef}; a contextual callback input alias keeps
 * the signature metadata. Input aliases apply where a value is converted to Java, while ordinary
 * host containers keep their type arguments. Bean aliases supplement the real accessors and give
 * way to an existing instance field or method. Getter overrides still apply when a Probe edit
 * renders the declaration again.
 */
public final class TypeScriptClassRenderer {
    private final TypeAliasRegistry aliases;
    private Set<String> interfaceClassBindings = Set.of();
    private Set<BeanAliasExclusionCatalogEntry> beanAliasExclusions = Set.of();
    private Set<BeanMethodProjectionCatalogEntry> beanMethodProjections = Set.of();
    private Map<String, Set<String>> covariantTypeParameters = Map.of();
    private TypeScriptStaticInheritance staticInheritance = new TypeScriptStaticInheritance(Map.of());
    private TypeScriptMethodSelectors methodSelectors = new TypeScriptMethodSelectors(Map.of());
    private Map<String, TypeDecl> selectorDeclarations = Map.of();
    private TypeScriptCaptureReturns captureReturns = new TypeScriptCaptureReturns(Map.of());
    /** Shared across scoped render copies so module generation can observe capture-view usage. */
    private Set<String> captureViewMarks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private TypeScriptCaptureViews captureViews = new TypeScriptCaptureViews(Map.of(), this);
    private boolean declarationsPrepared;
    private TypeScriptAuthoringEmission authoringEmission;
    private boolean authoringStaticValue;

    /** Uses this render's edited declarations; parent lookups never reflect over hidden or authored types. */
    public Set<String> setClassDeclarations(Map<String, TypeDecl> declarations) {
        staticInheritance = new TypeScriptStaticInheritance(declarations);
        selectorDeclarations = Map.copyOf(declarations);
        captureReturns = new TypeScriptCaptureReturns(selectorDeclarations);
        refreshMethodSelectors();
        captureViews = new TypeScriptCaptureViews(selectorDeclarations, this);
        declarationsPrepared = true;
        captureViewMarks.clear();
        Set<String> selectorTypes = methodSelectors.affectedTypes();
        Set<String> captureTypes = captureReturns.affectedTypes();
        return declarations.values().stream().filter(declaration -> staticInheritance.project(declaration) != null
                        || hasInheritedBeanProjection(declaration) || selectorTypes.contains(declaration.fqn)
                        || captureTypes.contains(declaration.fqn) || captureOutputs(declaration)
                        || beanMethodProjections.stream().anyMatch(rule -> rule.targetType() == declaration.sourceClass))
                .map(declaration -> declaration.fqn).collect(Collectors.toUnmodifiableSet());
    }

    /** Records measured result variance for specific library interfaces, without changing input parameters. */
    public void setCovariantTypeParameters(Map<String, Set<String>> parameters) {
        covariantTypeParameters = parameters.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    /** Replaces the per-snapshot rules; matching never expands to subclasses or same-name properties. */
    public void setBeanAliasExclusions(List<BeanAliasExclusionCatalogEntry> exclusions) {
        beanAliasExclusions = Set.copyOf(exclusions);
        refreshMethodSelectors();
    }

    /** Replaces exact method-to-Bean rules for this snapshot without editing shared declarations. */
    public void setBeanMethodProjections(List<BeanMethodProjectionCatalogEntry> projections) {
        beanMethodProjections = Set.copyOf(projections);
        refreshMethodSelectors();
    }

    private void refreshMethodSelectors() {
        methodSelectors = new TypeScriptMethodSelectors(selectorDeclarations, this::emitsInstanceMethod);
    }

    private boolean excludesBeanAlias(TypeDecl declaration, MethodDecl getter) {
        return beanAliasExclusions.stream().anyMatch(entry -> entry.targetType() == declaration.sourceClass
                && entry.getterName().equals(getter.name) && entry.propertyName().equals(getter.property));
    }

    private boolean emitsInstanceMethod(TypeDecl declaration, MethodDecl method) {
        return !projectsBeanMethod(declaration, method) && (declaration.kind == TypeDecl.Kind.ENUM || !method.isGetter
                || !isValidTsIdentifier(method.property) || beanPropertyConflicts(declaration).contains(method.property)
                || excludesBeanAlias(declaration, method)
                || !getterOverrides.getOrDefault(declaration.fqn, Map.of()).containsKey(method.property));
    }

    /** The retained getter must come from the visible edited lineage, never fresh reflection. */
    private boolean projectsBeanMethod(TypeDecl declaration, MethodDecl method) {
        if (beanMethodProjections.isEmpty() || method.hidden || method.isStatic || method.isConstructor || method.isGetter || method.explicitlyEdited
                || method.sourceMethod == null || method.sourceMethod.getParameterCount() != 0
                || !method.params.isEmpty() || !method.typeParams.isEmpty() || !method.overloads.isEmpty()
                || !method.name.equals(method.effectiveName())) return false;
        for (var rule : beanMethodProjections) {
            if (rule.targetType() != declaration.sourceClass || !rule.methodName().equals(method.name)
                    || method.sourceMethod.getDeclaringClass() != rule.targetType() || !unchangedResult(method)) continue;
            List<TypeDecl> lineage = new java.util.ArrayList<>();
            lineage.add(declaration);
            lineage.addAll(staticInheritance.parents(declaration));
            for (TypeDecl owner : lineage) {
                if (owner.fields.stream().anyMatch(field -> !field.hidden && !field.isStatic
                        && rule.methodName().equals(field.effectiveName()))) return false;
                for (MethodDecl getter : owner.methods) {
                    if (getter.sourceMethod == null && !getter.hidden && !getter.isStatic
                            && (getter.isGetter && rule.methodName().equals(getter.property)
                                || getter.params.isEmpty() && rule.getterName().equals(getter.effectiveName()))) return false;
                    if (getter.sourceMethod == null || getter.sourceMethod.getParameterCount() != 0
                            || !getter.sourceMethod.getName().equals(rule.getterName())) continue;
                    // Unedited inherited copies defer to the declaring ancestor's edited IR.
                    if (getter.sourceMethod.getDeclaringClass() != owner.sourceClass && !getter.explicitlyEdited) continue;
                    return !getter.hidden && !getter.isStatic && getter.isGetter && !getter.explicitlyEdited
                            && rule.getterName().equals(getter.effectiveName()) && rule.methodName().equals(getter.property)
                            && !beanPropertyConflicts(owner).contains(getter.property) && !excludesBeanAlias(owner, getter)
                            && !getterOverrides.getOrDefault(owner.fqn, Map.of()).containsKey(getter.property)
                            && getter.params.isEmpty() && getter.typeParams.isEmpty() && getter.overloads.isEmpty()
                            && unchangedResult(getter)
                            && method.returnType.ref.equals(getter.returnType.ref);
                }
            }
        }
        return false;
    }

    private static boolean unchangedResult(MethodDecl method) {
        return method.returnType != null && !method.returnType.overridden && method.returnType.outputTypeScript == null
                && method.returnType.sourceType != null && method.sourceMethod != null
                && method.returnType.sourceType.equals(method.sourceMethod.getGenericReturnType())
                && method.returnType.ref.equals(TypeReflector.toRef(method.sourceMethod.getGenericReturnType()));
    }

    // Overrides getter return types (FQN -> property name -> { returnType, importStatement })
    // Keys are property names (e.g., "recipes"), matching ClassDeclGenerator lookup conventions.
    private final Map<String, Map<String, GetterOverride>> getterOverrides = new LinkedHashMap<>();

    public TypeScriptClassRenderer(TypeAliasRegistry aliases) {
        this.aliases = aliases;
    }

    /** Requires interface class values referenced by direct or delegated Bindings, even without static members. */
    public void setInterfaceClassBindings(Set<String> names) {
        interfaceClassBindings = Set.copyOf(names);
    }

    /** Shares value presence with canonical exports so renamed Bindings reference the same actual class value. */
    public boolean hasClassValue(TypeDecl declaration) {
        return !declaration.hidden && (declaration.kind != TypeDecl.Kind.INTERFACE
                || interfaceClassBindings.contains(declaration.fqn)
                || declaration.methods.stream().anyMatch(method -> !method.hidden && method.isStatic)
                || declaration.fields.stream().anyMatch(field -> !field.hidden && field.isStatic && field.isFinal));
    }

    /** Getter override entry: return type string and accompanying import statement (merged by IndexFileGenerator; not consumed by this class). */
    public record GetterOverride(String returnType, String importStatement) {}

    /**
     * Overrides a Bean property's read type, keyed by the exposed property name.
     * An ordinary Bean alias emits only the overridden getter. A field-backed projection retains
     * its actual getter method and write type while overriding the property's read type.
     */
    public void overrideGetter(Class<?> cls, String getterName, String returnType, String importStatement) {
        getterOverrides.computeIfAbsent(cls.getName(), k -> new LinkedHashMap<>())
                .put(getterName, new GetterOverride(returnType, importStatement));
        refreshMethodSelectors();
    }

    /**
     * Returns all getter overrides and their imports for a class (merged by IndexFileGenerator when writing package index.d.ts).
     * Mirrors legacy ClassDeclGenerator#getExtraImports.
     */
    public Set<String> getExtraImports(String className) {
        Map<String, GetterOverride> overrides = getterOverrides.get(className);
        if (overrides == null || overrides.isEmpty()) return Set.of();
        Set<String> imports = new LinkedHashSet<>();
        for (GetterOverride override : overrides.values()) {
            if (override.importStatement() != null && !override.importStatement().isEmpty()) {
                imports.add(override.importStatement());
            }
        }
        return imports;
    }

    public String render(TypeDecl decl) {
        if (decl.hidden) return "";
        return switch (decl.kind) {
            case INTERFACE -> renderInterface(decl);
            case ENUM -> renderEnum(decl);
            default -> {
                TypeScriptStaticInheritance.Projection projection = staticInheritance.project(decl);
                yield projection == null ? renderClass(decl) : renderStaticShadowingClass(decl, projection);
            }
        };
    }

    /** Renders with module-local symbol names without changing shared getter overrides or aliases. */
    public String render(TypeDecl decl, TypeAliasRegistry moduleAliases) {
        return scoped(moduleAliases).render(decl);
    }

    /** Captures member records at the same emission sites as the returned declaration text. */
    public TypeScriptAuthoringEmission.Declaration renderIndexed(TypeDecl declaration, TypeAliasRegistry names) {
        TypeScriptClassRenderer scoped = scoped(names);
        scoped.authoringEmission = new TypeScriptAuthoringEmission(declaration.fqn);
        if (declaration.kind == TypeDecl.Kind.ENUM) scoped.authoringEmission.incomplete();
        String text = scoped.render(declaration);
        return scoped.authoringEmission.finish(text);
    }

    private TypeScriptClassRenderer scoped(TypeAliasRegistry moduleAliases) {
        TypeScriptClassRenderer scoped = new TypeScriptClassRenderer(moduleAliases);
        scoped.getterOverrides.putAll(getterOverrides);
        scoped.interfaceClassBindings = interfaceClassBindings;
        scoped.beanAliasExclusions = beanAliasExclusions;
        scoped.beanMethodProjections = beanMethodProjections;
        scoped.covariantTypeParameters = covariantTypeParameters;
        scoped.staticInheritance = staticInheritance;
        scoped.methodSelectors = methodSelectors;
        scoped.selectorDeclarations = selectorDeclarations;
        scoped.captureReturns = captureReturns;
        scoped.captureViews = captureViews;
        scoped.declarationsPrepared = declarationsPrepared;
        scoped.captureViewMarks = captureViewMarks;
        return scoped;
    }

    String captureInput(TypeSlot slot, TypeAliasRegistry names) {
        return scoped(names).renderSlot(slot, true);
    }

    List<String> captureMethodNames(TypeDecl owner, MethodDecl method) {
        return methodSelectors.names(owner, method);
    }

    String captureMethod(MethodDecl method, String name, TypeAliasRegistry names,
                         List<String> parameters, String result) {
        return scoped(names).formatMethod(method, false, name, parameters, result);
    }

    private String renderClass(TypeDecl d) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "    ", d.docs);
        sb.append("    export class $").append(effectiveClassName(d));
        appendTypeParameters(sb, d);

        if (d.superType != null) {
            String superTs = renderHeritageSlot(d.superType, aliases);
            // Parent slots may map to primitive TS types via TypeConverter (e.g., Number subclasses → number, $Double extends number),
            // or via Probe edits (changeSuper/assign_type) to unions/callbacks (extends string | number).
            // These cannot serve as heritage; omit the entire extends clause (implements clauses remain as is).
            if (isHeritageSafe(superTs)) {
                sb.append(" extends ").append(superTs);
            }
        }
        // Implements entries may be rewritten by probe.assign_type to arbitrary refs (primitives/unions/callbacks).
        // Omit unsafe entries (see isHeritageSafe); retain other entries and the clause itself.
        List<String> implemented = renderHeritageEntries(d.interfaces);
        if (!implemented.isEmpty()) {
            sb.append(" implements ").append(String.join(", ", implemented));
        }
        sb.append(" {\n");

        for (MethodDecl c : d.constructors) {
            if (!c.hidden) sb.append(formatConstructor(c));
        }
        for (FieldDecl f : d.fields) {
            if (!f.hidden && f.isStatic) sb.append(formatField(f, true));
        }
        appendInstanceFieldsAndBeans(sb, d);
        for (MethodDecl m : d.methods) {
            if (m.hidden || m.isConstructor || !m.isStatic) continue;
            sb.append(formatMethod(m, true));
        }
        appendInstanceMethods(sb, d);
        sb.append("    }\n");
        return sb.toString();
    }

    private String renderStaticShadowingClass(TypeDecl d, TypeScriptStaticInheritance.Projection projection) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "    ", d.docs);
        sb.append("    export class $").append(effectiveClassName(d));
        appendTypeParameters(sb, d);
        sb.append(" {\n");
        for (MethodDecl constructor : d.constructors) {
            if (!constructor.hidden) sb.append(formatConstructor(constructor));
        }
        if (d.sourceClass != null && d.constructors.stream().noneMatch(constructor -> !constructor.hidden)) {
            // A class without an extends clause otherwise acquires an implicit public zero-argument constructor.
            boolean protectedConstructor = java.util.Arrays.stream(d.sourceClass.getDeclaredConstructors())
                    .anyMatch(constructor -> java.lang.reflect.Modifier.isProtected(constructor.getModifiers()));
            sb.append(protectedConstructor ? "        protected constructor();\n" : "        private constructor();\n");
        }
        for (FieldDecl field : d.fields) {
            if (!field.hidden && field.isStatic) sb.append(formatField(field, true));
        }
        for (MethodDecl method : projection.methods()) sb.append(formatMethod(method, true));
        sb.append("    }\n");
        // Merging retains the instance hierarchy without requiring Java's hidden static fields to be covariant.
        sb.append("    export interface $").append(effectiveClassName(d));
        appendTypeParameters(sb, d);
        List<TypeSlot> parents = new java.util.ArrayList<>();
        if (d.superType != null) parents.add(d.superType);
        parents.addAll(d.interfaces);
        List<String> inherited = renderHeritageEntries(parents);
        if (!inherited.isEmpty()) sb.append(" extends ").append(String.join(", ", inherited));
        sb.append(" {\n");
        appendInstanceFieldsAndBeans(sb, d);
        appendInstanceMethods(sb, d);
        sb.append("    }\n");
        return sb.toString();
    }

    private void appendInstanceFieldsAndBeans(StringBuilder sb, TypeDecl d) {
        for (FieldDecl f : d.fields) {
            if (f.hidden || f.isStatic) continue;
            MethodDecl getter = d.beanGetterForField(f);
            if (getter == null) {
                sb.append(formatField(f, false));
            } else {
                appendDoc(sb, "        ", f.docs);
                GetterOverride override = getterOverrides.getOrDefault(d.fqn, Map.of()).get(getter.property);
                String readType = override == null ? renderSlot(getter.returnType, false) : override.returnType();
                TypeSlot writeType = d.beanFieldWriteType(f, getter);
                if (appendInheritedBeanProperty(sb, d, f.effectiveName(), readType, writeType)) continue;
                int propertyStart = sb.length();
                sb.append("        get ").append(tsMemberName(f.effectiveName())).append("(): ")
                        .append(readType).append(";\n");
                if (writeType != null) {
                    sb.append("        set ").append(tsMemberName(f.effectiveName())).append("(value: ")
                            .append(renderSlot(writeType, true)).append(");\n");
                }
                if (authoringEmission != null) authoringEmission.property(f.effectiveName(), false, readType,
                        writeType == null ? null : renderSlot(writeType, true), sb.substring(propertyStart));
            }
        }
        Set<String> propertyConflicts = beanPropertyConflicts(d);
        // Bean aliases supplement the real accessor methods unless an instance member owns the name.
        for (MethodDecl m : methodSelectors.methods(d)) {
            if (m.hidden || !m.isGetter) continue;
            // Invalid identifiers and occupied property names retain their actual accessor methods.
            if (!isValidTsIdentifier(m.property) || propertyConflicts.contains(m.property) || excludesBeanAlias(d, m)) {
                appendInstanceMethod(sb, d, m);
                continue;
            }
            appendDoc(sb, "        ", m.docs);
            // An explicit getter override emits only the read property, preserving the existing contract.
            GetterOverride ov = getterOverrides.getOrDefault(d.fqn, Map.of()).get(m.property);
            if (ov != null) {
                if (!appendInheritedBeanProperty(sb, d, m.property, ov.returnType(), null)) {
                    sb.append("        get ").append(tsMemberName(m.property)).append("(): ").append(ov.returnType()).append(";\n");
                    if (authoringEmission != null) authoringEmission.property(m.property, false, ov.returnType(), null, "get " + tsMemberName(m.property) + "(): " + ov.returnType() + ";");
                }
                continue;
            }
            String type = renderSlot(m.returnType, false);
            boolean fieldProperty = appendInheritedBeanProperty(sb, d, m.property, type, m.setterParamType);
            if (!fieldProperty && authoringEmission != null) authoringEmission.property(m.property, false, type,
                    m.setterParamType == null ? null : renderSlot(m.setterParamType, true), "get " + tsMemberName(m.property) + "(): " + type + ";");
            if (!fieldProperty) sb.append("        get ").append(tsMemberName(m.property)).append("(): ").append(type).append(";\n");
            for (String name : methodSelectors.names(d, m)) {
                String signature = "        " + tsMemberName(name) + "(): " + type + ";\n";
                sb.append(signature);
                if (authoringEmission != null) authoringEmission.method(m, name, false, List.of(), type, signature);
            }
            if (!fieldProperty && m.setterParamType != null) {
                sb.append("        set ").append(tsMemberName(m.property)).append("(value: ")
                  .append(renderSlot(m.setterParamType, true)).append(");\n");
            }
        }
    }

    private boolean appendInheritedBeanProperty(StringBuilder sb, TypeDecl declaration, String name,
                                                String readType, TypeSlot writeType) {
        if (!inheritsFieldProperty(declaration, name, readType, writeType)) return false;
        int propertyStart = sb.length();
        sb.append("        ").append(writeType == null ? "readonly " : "")
                .append(tsMemberName(name)).append(": ").append(readType).append(";\n");
        if (authoringEmission != null) authoringEmission.property(name, false, readType,
                writeType == null ? null : renderSlot(writeType, true), sb.substring(propertyStart));
        return true;
    }

    private boolean hasInheritedBeanProjection(TypeDecl declaration) {
        if (declaration.kind != TypeDecl.Kind.CLASS) return false;
        Set<String> conflicts = beanPropertyConflicts(declaration);
        for (MethodDecl getter : declaration.methods) {
            if (getter.hidden || !getter.isGetter || !isValidTsIdentifier(getter.property)) continue;
            FieldDecl field = declaration.fields.stream().filter(candidate -> !candidate.hidden && !candidate.isStatic
                    && getter.property.equals(candidate.effectiveName())).findFirst().orElse(null);
            if (field == null && (conflicts.contains(getter.property) || excludesBeanAlias(declaration, getter))) continue;
            GetterOverride override = getterOverrides.getOrDefault(declaration.fqn, Map.of()).get(getter.property);
            String read = override == null ? renderSlot(getter.returnType, false) : override.returnType();
            TypeSlot write = field != null ? declaration.beanFieldWriteType(field, getter)
                    : override == null ? getter.setterParamType : null;
            if (inheritsFieldProperty(declaration, getter.property, read, write)) return true;
        }
        return false;
    }

    /**
     * A Bean can use property syntax only when the edited ancestor chain already emits a same-type
     * data property. Each intervening Bean must admit that same projection; an accessor with different
     * read/write types or a real method blocks it. Missing or hidden parents never trigger reflection.
     */
    private boolean inheritsFieldProperty(TypeDecl declaration, String name, String readType, TypeSlot writeType) {
        if (writeType != null && !readType.equals(renderSlot(writeType, true))) return false;
        for (TypeDecl parent : staticInheritance.parents(declaration)) {
            FieldDecl field = parent.fields.stream().filter(candidate -> !candidate.hidden && !candidate.isStatic
                    && name.equals(candidate.effectiveName())).findFirst().orElse(null);
            MethodDecl getter = field == null ? parent.methods.stream().filter(method -> !method.hidden
                    && method.isGetter && name.equals(method.property) && isValidTsIdentifier(method.property)
                    && !beanPropertyConflicts(parent).contains(name) && !excludesBeanAlias(parent, method))
                    .findFirst().orElse(null) : parent.beanGetterForField(field);
            if (field != null && getter == null) return readType.equals(renderSlot(field.type, false));
            if (getter != null) {
                GetterOverride override = getterOverrides.getOrDefault(parent.fqn, Map.of()).get(name);
                String parentRead = override == null ? renderSlot(getter.returnType, false) : override.returnType();
                TypeSlot parentWrite = field != null ? parent.beanFieldWriteType(field, getter)
                        : override == null ? getter.setterParamType : null;
                if (!readType.equals(parentRead) || parentWrite != null && !readType.equals(renderSlot(parentWrite, true))) return false;
            } else if (parent.methods.stream().anyMatch(method -> !method.hidden && !method.isStatic
                    && !method.isConstructor && name.equals(method.effectiveName()))) {
                return false;
            }
        }
        return false;
    }

    private void appendInstanceMethods(StringBuilder sb, TypeDecl d) {
        // Explicit setters remain callable even when their Bean alias conflicts or no getter exists.
        appendProjectedMethods(sb, d, methodSelectors.methods(d).stream().filter(method -> !method.hidden
                && !method.isConstructor && !method.isStatic && !method.isGetter && !projectsBeanMethod(d, method)).toList());
    }

    private record NamedMethod(String name, MethodDecl method) {}

    private void appendProjectedMethods(StringBuilder sb, TypeDecl declaration, List<MethodDecl> methods) {
        List<NamedMethod> entries = new java.util.ArrayList<>();
        for (MethodDecl method : methods) {
            for (String name : methodSelectors.names(declaration, method)) entries.add(new NamedMethod(name, method));
        }
        for (MethodDecl method : methodSelectors.materializedMethods(declaration)) {
            entries.add(new NamedMethod(method.effectiveName(), method));
        }
        for (MethodDecl method : methodSelectors.bridgeContracts(declaration)) {
            entries.add(new NamedMethod(TypeScriptMethodSelectors.selector(method.sourceMethod), method));
        }
        Map<String, List<NamedMethod>> groups = entries.stream()
                .collect(Collectors.groupingBy(NamedMethod::name, LinkedHashMap::new, Collectors.toList()));
        Set<String> indexed = groups.entrySet().stream().filter(entry -> entry.getValue().stream()
                .anyMatch(overload -> !overload.method().overloads.isEmpty()
                        && methodSelectors.inheritedOwner(overload.method()) != null))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        Set<String> emitted = new LinkedHashSet<>();
        for (NamedMethod entry : entries) {
            if (!indexed.contains(entry.name())) {
                sb.append(formatMethod(entry.method(), false, entry.name()));
                continue;
            }
            if (!emitted.add(entry.name())) continue;
            List<NamedMethod> overloads = groups.get(entry.name());
            // Keep literal overload fragments in their authored module and generic scope. Indexed access
            // specializes the existing ancestor operation without parsing or guessing TypeScript strings.
            sb.append("        ").append(tsMemberName(entry.name())).append(": ");
            if (authoringEmission != null) authoringEmission.incomplete();
            sb.append(overloads.stream().map(overload -> {
                MethodDecl method = overload.method();
                ApiTypeRef owner = methodSelectors.inheritedOwner(method);
                return !method.overloads.isEmpty() && owner != null
                        ? renderTypeRef(owner, aliases, false) + "[" + new JsonPrimitive(methodSelectors.inheritedName(method)) + "]"
                        : "{\n" + formatMethod(method, false, null) + "        }";
            })
                    .distinct().collect(Collectors.joining(" & "))).append(";\n");
        }
    }

    private void appendInstanceMethod(StringBuilder sb, TypeDecl declaration, MethodDecl method) {
        for (String name : methodSelectors.names(declaration, method)) sb.append(formatMethod(method, false, name));
    }

    private static Set<String> beanPropertyConflicts(TypeDecl declaration) {
        Set<String> names = new LinkedHashSet<>();
        for (FieldDecl field : declaration.fields) {
            if (!field.hidden && !field.isStatic) names.add(field.effectiveName());
        }
        for (MethodDecl method : declaration.methods) {
            if (!method.hidden && !method.isConstructor && !method.isStatic) names.add(method.effectiveName());
        }
        return names;
    }

    private String renderInterface(TypeDecl d) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "    ", d.docs);
        sb.append("    export interface $").append(effectiveClassName(d));
        appendTypeParameters(sb, d);
        if (!d.interfaces.isEmpty()) {
            // Interface extends lists, like class implements, may be injected with unsafe refs (extends string) via assign_type.
            // Filter entries individually (see isHeritageSafe).
            List<String> extended = renderHeritageEntries(d.interfaces);
            if (!extended.isEmpty()) {
                sb.append(" extends ").append(String.join(", ", extended));
            }
        }
        sb.append(" {\n");
        appendInstanceFieldsAndBeans(sb, d);
        appendInstanceMethods(sb, d);
        sb.append("    }\n");

        if (!hasClassValue(d)) return sb.toString();

        // TypeScript interfaces describe instances; Java.type exposes static members on a separate value.
        // A merged object declaration retains quoted names and overloads without inheriting interface factories.
        sb.append("    export const $").append(effectiveClassName(d)).append(": {\n");
        authoringStaticValue = true;
        for (MethodDecl m : d.methods) {
            if (!m.hidden && m.isStatic) sb.append(formatMethod(m, false));
        }
        for (FieldDecl f : d.fields) {
            if (!f.hidden && f.isStatic && f.isFinal) {
                appendDoc(sb, "        ", f.docs);
                String type = renderSlot(f.type, false);
                sb.append("        readonly ").append(tsMemberName(f.effectiveName())).append(": ")
                        .append(type).append(";\n");
                if (authoringEmission != null) authoringEmission.property(f.effectiveName(), true, type, null, "readonly " + tsMemberName(f.effectiveName()) + ": " + type + ";");
            }
        }
        authoringStaticValue = false;
        sb.append("    };\n");
        return sb.toString();
    }

    private String renderEnum(TypeDecl d) {
        StringBuilder sb = new StringBuilder();
        String name = "$" + effectiveClassName(d);
        appendDoc(sb, "    ", d.docs);
        sb.append("    export class ").append(name).append(" {\n");
        for (FieldDecl f : d.fields) {
            if (!f.hidden && f.isEnumConstant) {
                appendDoc(sb, "        ", f.docs);
                sb.append("        static ").append(tsMemberName(f.effectiveName())).append(": ").append(name).append(";\n");
            }
        }
        sb.append("        name(): string;\n");
        sb.append("        ordinal(): number;\n");
        sb.append("        toString(): string;\n");
        sb.append("        static values(): ").append(name).append("[];\n");
        sb.append("        static valueOf(name: string): ").append(name).append(";\n");
        sb.append("    }\n");
        TypeAliasRegistry enumAliases = d.effectiveTypeName() == null ? aliases
                : aliases.withReferences((host, exported) -> host.equals(d.fqn)
                        ? name : aliases.reference(host, exported));
        List<TypeSlot> parentSlots = new java.util.ArrayList<>();
        if (d.superType != null) parentSlots.add(d.superType);
        parentSlots.addAll(d.interfaces);
        List<String> parents = parentSlots.stream()
                .map(slot -> renderHeritageSlot(slot, enumAliases))
                .filter(TypeScriptClassRenderer::isHeritageSafe).toList();
        if (!parents.isEmpty()) {
            // Merging inherits Java interface defaults and Enum<Self> without inheriting static factories.
            sb.append("    export interface ").append(name).append(" extends ")
                    .append(String.join(", ", parents)).append(" {}\n");
        }
        return sb.toString();
    }

    private void appendTypeParameters(StringBuilder sb, TypeDecl d) {
        if (d.typeParams.isEmpty()) return;
        sb.append("<");
        sb.append(d.typeParams.stream().map(tp -> {
            // Edited IR remains authoritative; a library's original variance may no longer describe it.
            boolean covariant = d.kind == TypeDecl.Kind.INTERFACE && !d.mutated
                    && covariantTypeParameters.getOrDefault(d.fqn, Set.of()).contains(tp.name);
            return (covariant ? "out " : "") + renderTypeParameter(tp, aliases);
        }).collect(Collectors.joining(", ")));
        sb.append(">");
    }

    /** Renders the effective class constraint shared by host declarations and their aliases. */
    public static String renderTypeParameter(TypeDecl.TypeParam parameter, TypeAliasRegistry aliases) {
        return renderTypeParameter(parameter.name, parameter.bounds(), aliases);
    }

    private static String renderTypeParameter(String name, List<TypeSlot> bounds, TypeAliasRegistry aliases) {
        return name + (bounds.isEmpty() ? "" : " extends " + bounds.stream()
                .map(bound -> renderTypeParameterBound(bound, name, aliases, new LinkedHashSet<>()))
                // An edited union must remain one operand of the intersection.
                .map(rendered -> bounds.size() > 1 ? "(" + rendered + ")" : rendered)
                .collect(Collectors.joining(" & ")));
    }

    private String formatConstructor(MethodDecl c) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "        ", c.docs);
        TypeScriptDeclarationFormatter.appendCallable(sb, 8, "constructor",
                parameterDeclarations(c.params, true, null), ";");
        if (authoringEmission != null) authoringEmission.method(c, "constructor", false,
                renderedParameters(c.params, true, null), null, sb.toString());
        appendOverloads(sb, c, true, false);
        return sb.toString();
    }

    private String formatField(FieldDecl f, boolean isStatic) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "        ", f.docs);
        sb.append("        ");
        if (isStatic) sb.append("static ");
        String type = renderSlot(f.type, false);
        sb.append(tsMemberName(f.effectiveName())).append(": ").append(type).append(";\n");
        if (authoringEmission != null) authoringEmission.field(f, isStatic, type, sb.toString());
        return sb.toString();
    }

    private String formatMethod(MethodDecl m, boolean isStatic) {
        return formatMethod(m, isStatic, m.effectiveName());
    }

    private String formatMethod(MethodDecl m, boolean isStatic, String emittedName) {
        return formatMethod(m, isStatic, emittedName, null, null);
    }

    private String formatMethod(MethodDecl m, boolean isStatic, String emittedName,
                                List<String> parameters, String result) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "        ", m.docs);
        StringBuilder head = new StringBuilder(isStatic ? "static " : "");
        if (emittedName != null) head.append(tsMemberName(emittedName));
        if (!m.typeParams.isEmpty()) {
            head.append("<").append(m.typeParams.stream().map(name ->
                    renderTypeParameter(name, m.typeParameterBounds.getOrDefault(name, List.of()), aliases))
                    .collect(Collectors.joining(", "))).append(">");
        }
        String returnType = result == null ? renderSlot(m.returnType, false) : result;
        TypeScriptDeclarationFormatter.appendCallable(sb, 8, head.toString(),
                parameterDeclarations(m.params, false, parameters), ": " + returnType + ";");
        if (authoringEmission != null) authoringEmission.method(m, emittedName, isStatic || authoringStaticValue,
                renderedParameters(m.params, false, parameters), returnType, sb.toString());
        appendOverloads(sb, m, false, isStatic, emittedName);
        return sb.toString();
    }

    /**
     * Emits handwritten overloads (@Overload): appends declarations with the same name (or "constructor").
     * Param types are spliced verbatim; returns reuse reflected types if empty. Params/returns are not
     * passed through renderSlot and do not participate in import collection—referenced types must be
     * visible in the current module (e.g., $Foo format), a constraint guaranteed by the annotation author.
     */
    private void appendOverloads(StringBuilder sb, MethodDecl m, boolean constructor, boolean isStatic) {
        appendOverloads(sb, m, constructor, isStatic, m.effectiveName());
    }

    private void appendOverloads(StringBuilder sb, MethodDecl m, boolean constructor, boolean isStatic, String name) {
        for (MethodDecl.Overload o : m.overloads) {
            appendDoc(sb, "        ", o.docs);
            String head = constructor ? "constructor" : (isStatic ? "static " : "")
                    + (name == null ? "" : tsMemberName(name));
            String suffix = constructor ? ";" : ": "
                    + (o.returns.isEmpty() ? renderSlot(m.returnType, false) : o.returns) + ";";
            TypeScriptDeclarationFormatter.appendCallable(sb, 8, head, o.params, suffix);
        }
    }

    private List<String> parameterDeclarations(List<MethodDecl.MethodParam> parameters, boolean constructor,
                                               List<String> renderedTypes) {
        List<String> declarations = new java.util.ArrayList<>();
        for (int index = 0; index < parameters.size(); index++) {
            MethodDecl.MethodParam parameter = parameters.get(index);
            declarations.add(tsParamName(parameter.name) + (parameter.varargs || parameter.optional ? "?" : "")
                    + ": " + renderParameterType(parameter, index, constructor, renderedTypes));
        }
        return declarations;
    }

    private List<String> renderedParameters(List<MethodDecl.MethodParam> parameters, boolean constructor,
                                            List<String> renderedTypes) {
        List<String> result = new java.util.ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) result.add(renderParameterType(parameters.get(i), i, constructor, renderedTypes));
        return result;
    }

    private String renderParameterType(MethodDecl.MethodParam parameter, int index, boolean constructor,
                                       List<String> renderedTypes) {
        String type;
        if (!parameter.type.overridden && parameter.type.inputShape != null) type = renderSlot(parameter.type, true);
        else if (renderedTypes != null) type = renderedTypes.get(index);
        else if (constructor && !parameter.type.overridden && parameter.type.inputShape == null
                && parameter.type.sourceType != null) {
            type = FunctionalInterfaceAliasGenerator.renderJavaTypeInput(
                    ConstructorInputTypes.project(parameter.type.sourceType), aliases, new LinkedHashSet<>());
        } else type = renderSlot(parameter.type, true);
        if (parameter.varargs && projectedRef(parameter.type).kind() == ApiTypeRef.Kind.UNION) type = "(" + type + ")";
        return parameter.varargs ? type + "[]" : type;
    }

    /** Preserve member names, separating ordinary constructor members from class constructor syntax. */
    private static String tsMemberName(String name) {
        // Quoted constructor methods still parse as constructors; computed names retain ordinary members.
        if ("constructor".equals(name)) return "[\"constructor\"]";
        return isValidTsIdentifier(name) ? name : new JsonPrimitive(name).toString();
    }

    /** Escapes TS parameter names: JS/TS reserved words cannot be used (e.g., Java parameter "function"); append "_" if necessary. */
    private static String tsParamName(String name) {
        if (name == null || name.isEmpty()) return "arg";
        return TS_RESERVED_WORDS.contains(name) ? name + "_" : name;
    }

    /** Checks if getter/setter property names are valid TS identifiers (must start with letter, underscore, or $; numeric starts like "2DigitYearStart" are invalid). */
    private static boolean isValidTsIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        char first = name.charAt(0);
        if (!(Character.isLetter(first) || first == '_' || first == '$')) return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$')) return false;
        }
        return true;
    }

    /** Conservative set of JS/TS reserved words that cannot be used as parameter names (includes strict mode/contextual keywords). */
    private static final Set<String> TS_RESERVED_WORDS = Set.of(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "new", "null", "return", "super",
            "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with",
            "let", "static", "yield", "await", "implements", "interface", "package", "private",
            "protected", "public", "arguments", "eval");

    /**
     * Checks if a rendered type string is a TS primitive keyword (number/string/boolean, etc.)—these
     * cannot appear in "extends" clauses (TypeConverter maps Number subclasses to number; String/Boolean analogously).
     */
    private static boolean isTsPrimitiveKeyword(String tsType) {
        return switch (tsType) {
            case "number", "string", "boolean", "void", "object", "unknown", "any" -> true;
            default -> false;
        };
    }

    /**
     * Renders the interfaces slot list, removing heritage-unsafe entries; returns an empty list if all are removed (callers omit the entire clause).
     */
    private List<String> renderHeritageEntries(List<TypeSlot> slots) {
        return slots.stream()
                .map(s -> renderHeritageSlot(s, aliases))
                .filter(TypeScriptClassRenderer::isHeritageSafe)
                .toList();
    }

    /**
     * Checks if a rendered type string is safe for heritage positions (class extends/implements, interface extends).
     * Heritage only accepts standard type references; unsafe forms:
     * <ul>
     *   <li>TS primitive keywords (including any/void/object) — {@link #isTsPrimitiveKeyword}</li>
     *   <li>Union types (containing "|"; rendered as "A | B")</li>
     *   <li>Callback types (containing "=>"; rendered as "(...args: any[]) => any")</li>
     * </ul>
     *
     * <p>Paths: probe.assign_type can rewrite any SYMBOL slot (including implements/interface extends entries)
     * to arbitrary refs (e.g., probe.assign("java.lang.CharSequence", "string"));
     * probe.modify_type.changeSuper can replace parent slots with unions/callbacks/primitive types.
     *
     * <p><b>Strategy: Omit, do not fail</b>—unsafe entries are dropped, while others and the declaration body
     * render as usual, ensuring valid generated declarations. This renderer is a pure function without logging channels, thus this comment records this silent strategy.
     */
    private static boolean isHeritageSafe(String renderedTs) {
        if (renderedTs == null || renderedTs.isBlank()) return false;
        if (isTsPrimitiveKeyword(renderedTs)) return false;
        return !renderedTs.contains("|") && !renderedTs.contains("=>");
    }

    /** Renders edits from IR; ordinary input slots retain reflection generics for callback substitution. */
    private String renderSlot(TypeSlot slot, boolean input) {
        if (slot == null) return "any";
        if (!input && !slot.overridden && slot.outputTypeScript != null) return slot.outputTypeScript;
        if (input && !slot.overridden && slot.inputShape != null) {
            return com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator
                    .renderShape(slot.inputShape, aliases, true).type();
        }
        if (input && !slot.overridden && slot.sourceType != null) {
            return FunctionalInterfaceAliasGenerator.renderJavaTypeInput(
                    slot.sourceType, aliases, new LinkedHashSet<>());
        }
        if (!input && !slot.overridden && slot.sourceType != null) {
            String container = TypeScriptCaptureReturns.container(slot);
            String bound = renderCaptureBound(slot);
            if (bound != null && (!declarationsPrepared || captureViews.available(container))) {
                captureViewMarks.add(container);
                return appendCaptureReturnFacets(slot,
                        aliases.reference(container, captureViews.name(container)) + "<" + bound + ">");
            }
        }
        String rendered = renderTypeRef(projectedRef(slot), slot.overridden ? aliases.withRawValueReferences() : aliases, input);
        return input ? rendered : appendCaptureReturnFacets(slot, rendered);
    }

    private String appendCaptureReturnFacets(TypeSlot slot, String rendered) {
        String container = TypeScriptCaptureReturns.container(slot);
        if (container != null && (!declarationsPrepared || captureViews.available(container)) && !captureReturns.bounds(slot).isEmpty()) {
            captureViewMarks.add(container);
            // Keep the concrete return first: direct callers retain their narrower element type.
            String facets = captureReturns.bounds(slot).stream().map(bound ->
                    aliases.reference(container, captureViews.name(container)) + "<"
                            + renderTypeRef(bound, aliases, false) + ">")
                    .collect(Collectors.joining(" & "));
            return rendered + " & " + facets;
        }
        return rendered;
    }

    /**
     * Bound of an unedited Optional or Stream output capture, or null.
     * The wildcard must have a single non-Object upper bound and no lower bound; everything
     * else keeps the ordinary flattened rendering.
     */
    private String renderCaptureBound(TypeSlot slot) {
        ApiTypeRef reference = TypeScriptCaptureReturns.captureBound(slot);
        if (reference == null) return null;
        var parameterized = (ParameterizedType) slot.sourceType;
        var wildcard = (WildcardType) parameterized.getActualTypeArguments()[0];
        Type bound = wildcard.getUpperBounds()[0];
        return renderTypeRef(JavaTypeScriptProjection.project(bound,
                reference), aliases, false);
    }

    /** True when a declaration references one of the complete capture views. */
    public boolean captureViewUsed() {
        return !captureViewMarks.isEmpty();
    }

    private boolean captureOutputs(TypeDecl declaration) {
        return declaration.methods.stream().anyMatch(method -> captureOutput(method.returnType))
                || declaration.fields.stream().anyMatch(field -> captureOutput(field.type));
    }

    private boolean captureOutput(TypeSlot slot) {
        return TypeScriptCaptureReturns.captureBound(slot) != null;
    }

    public Map<String, String> captureViewNames() {
        return captureViewUsed() ? captureViews.names() : Map.of();
    }

    public Set<String> captureLocalNames(String packageName) {
        return captureViewUsed() ? captureViews.localNames(packageName) : Set.of();
    }

    public String renderCaptureViews(String packageName, TypeAliasRegistry names) {
        return captureViewUsed() ? captureViews.render(packageName, names) : "";
    }

    private static ApiTypeRef projectedRef(TypeSlot slot) {
        return !slot.overridden && slot.sourceType != null
                ? JavaTypeScriptProjection.project(slot.sourceType, slot.ref) : slot.ref;
    }

    private String renderHeritageSlot(TypeSlot slot, TypeAliasRegistry registry) {
        ApiTypeRef reference = projectedRef(slot);
        TypeAliasRegistry names = slot.overridden ? registry.withRawValueReferences() : registry;
        if (reference.kind() == ApiTypeRef.Kind.SYMBOL
                && !JavaTypeScriptProjection.scalarAlternatives(fqnOfSymbol(reference.name())).isEmpty()) {
            // Inheritance names the host itself; its generic arguments are still value positions.
            return renderHostSymbol(reference, names, new LinkedHashSet<>());
        }
        return renderTypeRef(reference, names, false);
    }

    /** Simple ApiTypeRef → TS (no alias registry; used for declarations like probe.add_global). */
    public static String renderTypeRef(ApiTypeRef ref) {
        return renderTypeRef(ref, null, false);
    }

    /** Retains self-comparable Java bounds after boxed values project to JavaScript primitives. */
    public static String renderTypeParameterBound(TypeSlot slot, String variableName,
                                                  TypeAliasRegistry aliases, Set<String> expanding) {
        if (slot.overridden) aliases = (aliases == null ? new TypeAliasRegistry() : aliases).withRawValueReferences();
        if (!slot.overridden && slot.sourceType == Comparable.class
                && slot.ref.kind() == ApiTypeRef.Kind.SYMBOL
                && slot.ref.name().equals("java:java.lang.Comparable") && slot.ref.arguments().isEmpty()) {
            return primitiveComparableBound(renderTypeRef(projectedRef(slot), aliases, false, expanding));
        }
        return renderTypeParameterBound(projectedRef(slot), variableName, aliases, expanding);
    }

    /** Renders explicit bounds and bounds already contextualized by a functional alias. */
    public static String renderTypeParameterBound(ApiTypeRef ref, String variableName,
                                                  TypeAliasRegistry aliases, Set<String> expanding) {
        String hostType = renderTypeRef(ref, aliases, false, expanding);
        if (ref != null && ref.kind() == ApiTypeRef.Kind.SYMBOL
                && "java:java.lang.Comparable".equals(ref.name()) && ref.arguments().size() == 1) {
            ApiTypeRef argument = ref.arguments().get(0);
            if (argument.kind() == ApiTypeRef.Kind.TYPE_VARIABLE && variableName.equals(argument.name())) {
                // String, numeric and boolean wrappers all implement Comparable of their own Java type.
                // Other Comparable bounds retain their exact host constraint.
                return primitiveComparableBound(hostType);
            }
        }
        return hostType;
    }

    private static String primitiveComparableBound(String hostType) {
        return "(" + hostType + " | string | number | boolean)";
    }

    /**
     * ApiTypeRef → TS. With input=true and alias registry, applies input alias relaxation—semantic alignment with
     * TypeConverter: parameterized symbols consult collection aliases (structured argument arrays to prevent nested
     * generic corruption by ", "), while non-parameterized symbols consult type aliases (adapters/enum lazy aliases).
     */
    public static String renderTypeRef(ApiTypeRef ref, TypeAliasRegistry aliases, boolean input) {
        return renderTypeRef(ref, aliases, input, new LinkedHashSet<>());
    }

    private enum InputProjection { NATIVE_PARAMETER, DECLARED_INPUT }

    /** Converter-owned declarations recursively project inputs; erased Java adapters retain host arguments. */
    public static String renderDeclaredInputType(ApiTypeRef ref, TypeAliasRegistry aliases) {
        return renderTypeRef(ref, aliases, true, new LinkedHashSet<>(), InputProjection.DECLARED_INPUT);
    }

    /** Internal recursive renderer carrying functional-alias recursion state. */
    public static String renderTypeRef(ApiTypeRef ref, TypeAliasRegistry aliases, boolean input,
                                      Set<String> expandingFunctionalAliases) {
        return renderTypeRef(ref, aliases, input, expandingFunctionalAliases, InputProjection.NATIVE_PARAMETER);
    }

    private static String renderTypeRef(ApiTypeRef ref, TypeAliasRegistry aliases, boolean input,
                                       Set<String> expandingFunctionalAliases, InputProjection projection) {
        if (ref == null) return "any";
        return switch (ref.kind()) {
            case VOID -> "void";
            case PRIMITIVE -> mapPrimT(ref.name());
            case TYPE_VARIABLE -> aliases == null ? ref.name() : aliases.typeVariable(ref.name());
            case ARRAY -> {
                ApiTypeRef element = ref.arguments().getFirst();
                String rendered = renderTypeRef(element, aliases, input, expandingFunctionalAliases, projection);
                yield TypeScriptTypeExpressions.arrayOf(rendered);
            }
            case UNION -> ref.arguments().stream()
                    .map(a -> renderTypeRef(a, aliases, input, expandingFunctionalAliases, projection))
                    .collect(Collectors.joining(" | "));
            case SYMBOL -> renderSymbol(ref, aliases, input, expandingFunctionalAliases, projection);
            case CALLBACK -> "(...args: any[]) => any";
        };
    }

    private static String renderSymbol(ApiTypeRef ref, TypeAliasRegistry aliases, boolean input,
                                       Set<String> expandingFunctionalAliases, InputProjection projection) {
        String fqn = fqnOfSymbol(ref.name());
        boolean projectArguments = input && projection == InputProjection.DECLARED_INPUT
                && !ref.arguments().isEmpty() && !preservesDeclaredPayload(fqn, aliases);
        if (input && aliases != null) {
            String registeredAlias = aliases.getRegisteredAlias(fqn);
            if (registeredAlias != null) {
                return aliases.renderRegisteredAlias(fqn, ref.arguments().stream()
                        .map(argument -> renderTypeRef(argument, aliases, projectArguments,
                                expandingFunctionalAliases, projection)).toList());
            }
        }
        List<ApiTypeRef> scalarValues = ref.arguments().isEmpty() && (aliases == null || aliases.projectsScalarValues())
                ? JavaTypeScriptProjection.scalarAlternatives(fqn) : List.of();
        if (!scalarValues.isEmpty()) {
            TypeAliasRegistry.FunctionalInterfaceAlias functionalAlias = input && aliases != null
                    ? aliases.getFunctionalInterfaceAlias(fqn) : null;
            String host = functionalAlias == null ? renderHostSymbol(ref, aliases, expandingFunctionalAliases)
                    : FunctionalInterfaceAliasGenerator.renderInputType(ref, functionalAlias, aliases, expandingFunctionalAliases);
            return "(" + scalarValues.stream().map(value -> renderTypeRef(value, aliases, false, expandingFunctionalAliases))
                    .collect(Collectors.joining(" | ")) + " | " + host + ")";
        }
        if (input && aliases != null) {
            TypeAliasRegistry.FunctionalInterfaceAlias functionalAlias = aliases.getFunctionalInterfaceAlias(fqn);
            if (functionalAlias != null) {
                return FunctionalInterfaceAliasGenerator.renderInputType(ref, functionalAlias, aliases, expandingFunctionalAliases);
            }
            if (!ref.arguments().isEmpty()) {
                String[] hostArgs = ref.arguments().stream()
                        .map(a -> renderTypeRef(a, aliases, false, expandingFunctionalAliases)).toArray(String[]::new);
                String[] inputArgs = ref.arguments().stream()
                        .map(a -> renderTypeRef(a, aliases, projection == InputProjection.NATIVE_PARAMETER || projectArguments,
                                expandingFunctionalAliases, projection)).toArray(String[]::new);
                String declaredAlias = TypeAliasRegistry.collectionInputAlias(fqn);
                if (projection == InputProjection.DECLARED_INPUT && declaredAlias != null) {
                    return aliases.reference(fqn, declaredAlias) + "<" + String.join(", ", inputArgs) + ">";
                }
                String alias = aliases.renderCollectionInput(fqn, hostArgs, inputArgs);
                if (alias != null) return alias;
            } else if (aliases.hasAlias(fqn)) {
                return aliases.getAlias(fqn);
            }
        }
        if (fqn.equals("java.lang.Character")) return "string";
        if (projectArguments) {
            ApiTypeRef raw = new ApiTypeRef(ApiTypeRef.Kind.SYMBOL, ref.name(), List.of(), null);
            return renderHostSymbol(raw, aliases, expandingFunctionalAliases) + "<" + ref.arguments().stream()
                    .map(argument -> renderTypeRef(argument, aliases, true, expandingFunctionalAliases, projection))
                    .collect(Collectors.joining(", ")) + ">";
        }
        return renderHostSymbol(ref, aliases, expandingFunctionalAliases);
    }

    private static boolean preservesDeclaredPayload(String fqn, TypeAliasRegistry aliases) {
        if (Set.of("net.minecraft.core.Holder", "net.minecraft.core.HolderSet", "net.minecraft.tags.TagKey",
                "net.minecraft.resources.ResourceKey").contains(fqn)
                || (aliases != null && aliases.getFunctionalInterfaceAlias(fqn) != null)) return true;
        try {
            Class<?> raw = Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
            return com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver.resolve(raw) != null;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            // Missing metadata must not broaden a callback payload into an input value domain.
            return true;
        }
    }

    private static String renderHostSymbol(ApiTypeRef ref, TypeAliasRegistry aliases, Set<String> expandingFunctionalAliases) {
        String fqn = fqnOfSymbol(ref.name());
        String name = "$" + simpleSymbolName(ref.name());
        StringBuilder sb = new StringBuilder(aliases == null ? name : aliases.reference(fqn, name));
        if (!ref.arguments().isEmpty()) {
            sb.append('<');
            sb.append(ref.arguments().stream()
                    .map(a -> renderTypeRef(a, aliases, false, expandingFunctionalAliases))
                    .collect(Collectors.joining(", ")));
            sb.append('>');
        }
        return sb.toString();
    }

    /** Symbol name like "java:java.util.List" → extract FQN part. */
    private static String fqnOfSymbol(String symbolName) {
        int colon = symbolName.indexOf(':');
        return colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
    }

    /**
     * Renders docs list as a JSDoc block (indented by "indent"). Single-line docs collapse to a single-line
     * block comment, while multi-line (including embedded newlines) expand with asterisk prefixes. If docs are empty,
     * produces no content, preserving output consistency with TypeReflector's default behavior.
     */
    private static void appendDoc(StringBuilder sb, String indent, List<String> docs) {
        if (docs == null || docs.isEmpty()) return;
        boolean singleLine = docs.size() == 1 && !docs.get(0).contains("\n");
        if (singleLine) {
            sb.append(indent).append("/** ").append(docs.get(0)).append(" */\n");
            return;
        }
        sb.append(indent).append("/**\n");
        for (String doc : docs) {
            for (String line : doc.split("\n", -1)) {
                sb.append(indent).append(" * ").append(line).append("\n");
            }
        }
        sb.append(indent).append(" */\n");
    }

    private static String mapPrimT(String name) {
        return switch (name) {
            case "boolean" -> "boolean";
            case "int", "byte", "short", "long", "float", "double", "number" -> "number";
            case "char", "string" -> "string";
            case "void" -> "void";
            case "object" -> "object";
            case "unknown" -> "unknown";
            default -> "any";
        };
    }

    /** Symbol name like "java:java.util.List" → extract the last dot-separated part as simple name. */
    private static String simpleSymbolName(String symbolName) {
        int colon = symbolName.indexOf(':');
        String qn = colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
        int dot = qn.lastIndexOf('.');
        return dot >= 0 ? qn.substring(dot + 1) : qn;
    }

    /** Class name for rendering: prioritize renameTo (modify_type), otherwise fallback to sourceClass simple name. */
    private static String effectiveClassName(TypeDecl d) {
        return d.effectiveTypeName() != null ? d.effectiveTypeName() : tsClassName(d.sourceClass);
    }

    private static String tsClassName(Class<?> cls) {
        if (cls == null) return "Unknown";
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return tsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }
}
