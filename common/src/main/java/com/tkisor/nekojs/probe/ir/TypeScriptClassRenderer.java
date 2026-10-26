package com.tkisor.nekojs.probe.ir;

import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.JavaTypeScriptProjection;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;

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
 * Slots use their neutral {@link ApiTypeRef}; contextual callback input aliases retain signature metadata.
 * Input aliases apply at conversion boundaries while ordinary host containers keep their type arguments.
 * Bean aliases supplement real accessors, yielding to existing instance fields or methods.
 * Getter overrides remain available when Probe edits trigger rendering again.
 */
public final class TypeScriptClassRenderer {
    private final TypeAliasRegistry aliases;
    private Set<String> interfaceClassBindings = Set.of();
    private Set<BeanAliasExclusionCatalogEntry> beanAliasExclusions = Set.of();
    private Map<String, Set<String>> covariantTypeParameters = Map.of();
    private TypeScriptStaticInheritance staticInheritance = new TypeScriptStaticInheritance(Map.of());
    private TypeScriptMethodSelectors methodSelectors = new TypeScriptMethodSelectors(Map.of());
    private Map<String, TypeDecl> selectorDeclarations = Map.of();
    private TypeScriptCaptureReturns captureReturns = new TypeScriptCaptureReturns(Map.of());
    /** Shared across scoped render copies so module generation can observe capture-view usage. */
    private Set<String> captureViewMarks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Per-run reflection cache of the capture view source declarations; package generation is parallel. */
    private final Map<String, TypeDecl> captureSourceDecls = new java.util.concurrent.ConcurrentHashMap<>();

    /** Uses this render's edited declarations; parent lookups never reflect over hidden or authored types. */
    public Set<String> setClassDeclarations(Map<String, TypeDecl> declarations) {
        staticInheritance = new TypeScriptStaticInheritance(declarations);
        selectorDeclarations = Map.copyOf(declarations);
        captureReturns = new TypeScriptCaptureReturns(selectorDeclarations);
        refreshMethodSelectors();
        Set<String> selectorTypes = methodSelectors.affectedTypes();
        Set<String> captureTypes = captureReturns.affectedTypes();
        return declarations.values().stream().filter(declaration -> staticInheritance.project(declaration) != null
                        || hasInheritedBeanProjection(declaration) || selectorTypes.contains(declaration.fqn)
                        || captureTypes.contains(declaration.fqn))
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

    private void refreshMethodSelectors() {
        methodSelectors = new TypeScriptMethodSelectors(selectorDeclarations, this::emitsInstanceMethod);
    }

    private boolean excludesBeanAlias(TypeDecl declaration, MethodDecl getter) {
        return beanAliasExclusions.stream().anyMatch(entry -> entry.targetType() == declaration.sourceClass
                && entry.getterName().equals(getter.name) && entry.propertyName().equals(getter.property));
    }

    private boolean emitsInstanceMethod(TypeDecl declaration, MethodDecl method) {
        return declaration.kind != TypeDecl.Kind.CLASS || !method.isGetter
                || !isValidTsIdentifier(method.property) || beanPropertyConflicts(declaration).contains(method.property)
                || excludesBeanAlias(declaration, method)
                || !getterOverrides.getOrDefault(declaration.fqn, Map.of()).containsKey(method.property);
    }

    // 覆盖 getter 的返回类型 (fqn -> getter 属性名 -> { returnType, importStatement })
    // 键是 getter 的**属性名**（如 "recipes"），与 ClassDeclGenerator 的 lookup 口径一致
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

    /** getter 覆盖条目：返回类型字符串 + 附带 import 语句（import 由 IndexFileGenerator 合并，本类不消费）。 */
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
     * 某类所有 getter 覆盖附带的 import 语句（供 IndexFileGenerator 在写包级 index.d.ts 时合并）。
     * 镜像旧 {@code ClassDeclGenerator#getExtraImports}。
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
        TypeScriptClassRenderer scoped = new TypeScriptClassRenderer(moduleAliases);
        scoped.getterOverrides.putAll(getterOverrides);
        scoped.interfaceClassBindings = interfaceClassBindings;
        scoped.beanAliasExclusions = beanAliasExclusions;
        scoped.covariantTypeParameters = covariantTypeParameters;
        scoped.staticInheritance = staticInheritance;
        scoped.methodSelectors = methodSelectors;
        scoped.selectorDeclarations = selectorDeclarations;
        scoped.captureReturns = captureReturns;
        scoped.captureViewMarks = captureViewMarks;
        return scoped.render(decl);
    }

    private String renderClass(TypeDecl d) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "    ", d.docs);
        sb.append("    export class $").append(effectiveClassName(d));
        appendTypeParameters(sb, d);

        if (d.superType != null) {
            String superTs = renderHeritageSlot(d.superType, aliases);
            // 父类槽经 TypeConverter 可能映射为 TS 原始类型（Number 子类 → number，即 $Double extends number），
            // 经 probe 编辑（changeSuper/assign_type）还可能是联合/回调（extends string | number）——
            // 这些都不能作 heritage，省略整个 extends 子句（implements 等照常保留）
            if (isHeritageSafe(superTs)) {
                sb.append(" extends ").append(superTs);
            }
        }
        // implements 条目经 probe.assign_type 可被重写为任意 ref（primitive/union/回调）——不安全条目逐条
        // 省略（见 isHeritageSafe），其余条目与整个子句的存续照常保留
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
                sb.append("        get ").append(tsMemberName(f.effectiveName())).append("(): ")
                        .append(readType).append(";\n");
                if (writeType != null) {
                    sb.append("        set ").append(tsMemberName(f.effectiveName())).append("(value: ")
                            .append(renderSlot(writeType, true)).append(");\n");
                }
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
                }
                continue;
            }
            String type = renderSlot(m.returnType, false);
            boolean fieldProperty = appendInheritedBeanProperty(sb, d, m.property, type, m.setterParamType);
            if (!fieldProperty) sb.append("        get ").append(tsMemberName(m.property)).append("(): ").append(type).append(";\n");
            for (String name : methodSelectors.names(d, m)) {
                sb.append("        ").append(tsMemberName(name)).append("(): ").append(type).append(";\n");
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
        sb.append("        ").append(writeType == null ? "readonly " : "")
                .append(tsMemberName(name)).append(": ").append(readType).append(";\n");
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
                && !method.isConstructor && !method.isStatic && !method.isGetter).toList());
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
            // 接口 extends 列表与 class implements 同样经 assign_type 可被注入不安全 ref（extends string），
            // 逐条过滤（见 isHeritageSafe）
            List<String> extended = renderHeritageEntries(d.interfaces);
            if (!extended.isEmpty()) {
                sb.append(" extends ").append(String.join(", ", extended));
            }
        }
        sb.append(" {\n");
        appendProjectedMethods(sb, d, methodSelectors.methods(d).stream().filter(method -> !method.hidden && !method.isStatic).toList());
        sb.append("    }\n");

        if (!hasClassValue(d)) return sb.toString();

        // TypeScript interfaces describe instances; Java.type exposes static members on a separate value.
        // A merged object declaration retains quoted names and overloads without inheriting interface factories.
        sb.append("    export const $").append(effectiveClassName(d)).append(": {\n");
        for (MethodDecl m : d.methods) {
            if (!m.hidden && m.isStatic) sb.append(formatMethod(m, false));
        }
        for (FieldDecl f : d.fields) {
            if (!f.hidden && f.isStatic && f.isFinal) {
                appendDoc(sb, "        ", f.docs);
                sb.append("        readonly ").append(tsMemberName(f.effectiveName())).append(": ")
                        .append(renderSlot(f.type, false)).append(";\n");
            }
        }
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
        sb.append("        constructor(");
        appendParameters(sb, c.params, true);
        sb.append(");\n");
        appendOverloads(sb, c, true, false);
        return sb.toString();
    }

    private String formatField(FieldDecl f, boolean isStatic) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "        ", f.docs);
        sb.append("        ");
        if (isStatic) sb.append("static ");
        sb.append(tsMemberName(f.effectiveName())).append(": ").append(renderSlot(f.type, false)).append(";\n");
        return sb.toString();
    }

    private String formatMethod(MethodDecl m, boolean isStatic) {
        return formatMethod(m, isStatic, m.effectiveName());
    }

    private String formatMethod(MethodDecl m, boolean isStatic, String emittedName) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "        ", m.docs);
        sb.append("        ");
        if (isStatic) sb.append("static ");
        if (emittedName != null) sb.append(tsMemberName(emittedName));
        if (!m.typeParams.isEmpty()) {
            sb.append("<").append(m.typeParams.stream().map(name ->
                    renderTypeParameter(name, m.typeParameterBounds.getOrDefault(name, List.of()), aliases))
                    .collect(Collectors.joining(", "))).append(">");
        }
        sb.append("(");
        appendParameters(sb, m.params);
        sb.append("): ").append(renderSlot(m.returnType, false)).append(";\n");
        appendOverloads(sb, m, false, isStatic, emittedName);
        return sb.toString();
    }

    /**
     * 发射手写重载（{@code @Overload}）：同名（构造器则 constructor）附加声明，参数条目为
     * TypeScript 片段原样拼接；returns 为空沿用反射返回类型。参数与返回类型不经 renderSlot，
     * 也不参与 import 收集——引用的类型须已在本模块可见（{@code $Foo} 形式），由注解作者保证。
     */
    private void appendOverloads(StringBuilder sb, MethodDecl m, boolean constructor, boolean isStatic) {
        appendOverloads(sb, m, constructor, isStatic, m.effectiveName());
    }

    private void appendOverloads(StringBuilder sb, MethodDecl m, boolean constructor, boolean isStatic, String name) {
        for (MethodDecl.Overload o : m.overloads) {
            appendDoc(sb, "        ", o.docs);
            sb.append("        ");
            if (constructor) {
                sb.append("constructor(");
            } else {
                if (isStatic) sb.append("static ");
                if (name != null) sb.append(tsMemberName(name));
                sb.append("(");
            }
            sb.append(String.join(", ", o.params)).append(")");
            if (!constructor) {
                sb.append(": ");
                sb.append(o.returns.isEmpty() ? renderSlot(m.returnType, false) : o.returns);
            }
            sb.append(";\n");
        }
    }

    private void appendParameters(StringBuilder sb, List<MethodDecl.MethodParam> params) {
        appendParameters(sb, params, false);
    }

    private void appendParameters(StringBuilder sb, List<MethodDecl.MethodParam> params, boolean constructor) {
        for (int i = 0; i < params.size(); i++) {
            MethodDecl.MethodParam p = params.get(i);
            if (i > 0) sb.append(", ");
            sb.append(tsParamName(p.name));
            if (p.varargs || p.optional) sb.append("?");
            sb.append(": ");
            boolean unionElement = p.varargs && projectedRef(p.type).kind() == ApiTypeRef.Kind.UNION;
            if (unionElement) sb.append('(');
            if (constructor && !p.type.overridden && p.type.sourceType != null) {
                // TypeScript class constructors cannot declare their own type parameters.
                // Project only those local variables; ordinary class variables and edited slots keep their scope.
                sb.append(FunctionalInterfaceAliasGenerator.renderJavaTypeInput(
                        ConstructorInputTypes.project(p.type.sourceType), aliases, new LinkedHashSet<>()));
            } else {
                sb.append(renderSlot(p.type, true));
            }
            if (unionElement) sb.append(')');
            if (p.varargs) sb.append("[]");
        }
    }

    /** Preserve member names, separating ordinary constructor members from class constructor syntax. */
    private static String tsMemberName(String name) {
        // Quoted constructor methods still parse as constructors; computed names retain ordinary members.
        if ("constructor".equals(name)) return "[\"constructor\"]";
        return isValidTsIdentifier(name) ? name : new JsonPrimitive(name).toString();
    }

    /** TS 参数名转义：JS/TS 保留字不能作参数名（如 Java 参数 {@code function}），追加 {@code _}。 */
    private static String tsParamName(String name) {
        if (name == null || name.isEmpty()) return "arg";
        return TS_RESERVED_WORDS.contains(name) ? name + "_" : name;
    }

    /** getter/setter 属性名是否为合法 TS 标识符（首字符须字母/下划线/$；数字开头如 {@code 2DigitYearStart} 非法）。 */
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

    /** JS/TS 中不能作为参数名的保留字（含严格模式/上下文关键字，保守集合）。 */
    private static final Set<String> TS_RESERVED_WORDS = Set.of(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "new", "null", "return", "super",
            "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with",
            "let", "static", "yield", "await", "implements", "interface", "package", "private",
            "protected", "public", "arguments", "eval");

    /**
     * 渲染出的类型字符串是否是 TS 原始类型关键字（number/string/boolean 等）——这些不能出现在
     * {@code extends} 子句里（TypeConverter 会把 Number 子类映射为 number、String/Boolean 同理）。
     */
    private static boolean isTsPrimitiveKeyword(String tsType) {
        return switch (tsType) {
            case "number", "string", "boolean", "void", "object", "unknown", "any" -> true;
            default -> false;
        };
    }

    /**
     * 渲染 interfaces 槽列表并逐条剔除 heritage 不安全条目；全被剔除时返回空列表（调用方省略整个子句）。
     */
    private List<String> renderHeritageEntries(List<TypeSlot> slots) {
        return slots.stream()
                .map(s -> renderHeritageSlot(s, aliases))
                .filter(TypeScriptClassRenderer::isHeritageSafe)
                .toList();
    }

    /**
     * 渲染出的类型字符串能否安全地出现在 heritage 位置（class 的 extends/implements、interface 的 extends）。
     * heritage 只接受普通类型引用，以下形态不安全：
     * <ul>
     *   <li>TS 原始类型关键字（含 any/void/object）——{@link #isTsPrimitiveKeyword}</li>
     *   <li>联合类型（含 {@code |}，渲染为 {@code A | B}）</li>
     *   <li>回调类型（含 {@code =>}，渲染为 {@code (...args: any[]) => any}）</li>
     * </ul>
     *
     * <p>可达路径：{@code probe.assign_type} 可把任一 SYMBOL 槽（含 implements / 接口 extends 条目）
     * 重写为任意 ref（如 {@code probe.assign("java.lang.CharSequence", "string")}）；
     * {@code probe.modify_type.changeSuper} 可把父类槽换成 union/回调/原始类型。
     *
     * <p><b>策略：省略（OMIT）而非失败</b>——不安全条目被丢弃、其余条目及声明主体照常渲染，
     * 保证产出声明语法有效。本 renderer 是纯函数、无日志通道，故以此注释记录该静默策略。
     */
    private static boolean isHeritageSafe(String renderedTs) {
        if (renderedTs == null || renderedTs.isBlank()) return false;
        if (isTsPrimitiveKeyword(renderedTs)) return false;
        return !renderedTs.contains("|") && !renderedTs.contains("=>");
    }

    /** Renders edits from IR; ordinary input slots retain reflection generics for callback substitution. */
    private String renderSlot(TypeSlot slot, boolean input) {
        if (slot == null) return "any";
        if (input && !slot.overridden && slot.sourceType != null) {
            return FunctionalInterfaceAliasGenerator.renderJavaTypeInput(
                    slot.sourceType, aliases, new LinkedHashSet<>());
        }
        if (!input && !slot.overridden && slot.sourceType != null) {
            String bound = renderOptionalCaptureBound(slot.sourceType);
            if (bound != null) {
                captureViewMarks.add("java.util.Optional");
                return appendCaptureReturnFacets(slot,
                        aliases.reference("java.util.Optional", "$OptionalView") + "<" + bound + ">");
            }
        }
        String rendered = renderTypeRef(projectedRef(slot), slot.overridden ? aliases.withRawValueReferences() : aliases, input);
        return input ? rendered : appendCaptureReturnFacets(slot, rendered);
    }

    private String appendCaptureReturnFacets(TypeSlot slot, String rendered) {
        if (!captureReturns.bounds(slot).isEmpty()) {
            captureViewMarks.add("java.util.Optional");
            // Keep the concrete return first: direct callers retain their narrower element type.
            String facets = captureReturns.bounds(slot).stream().map(bound ->
                    aliases.reference("java.util.Optional", "$OptionalView") + "<"
                            + renderTypeRef(bound, aliases, false) + ">")
                    .collect(Collectors.joining(" & "));
            return rendered + " & " + facets;
        }
        return rendered;
    }

    /**
     * Bound of an unedited output {@code Optional<? extends Bound>} use site, or null.
     * The wildcard must have a single non-Object upper bound and no lower bound; everything
     * else keeps the ordinary flattened rendering.
     */
    private String renderOptionalCaptureBound(Type source) {
        if (!(source instanceof ParameterizedType parameterized)) return null;
        if (!(parameterized.getRawType() instanceof Class<?> raw) || raw != java.util.Optional.class) return null;
        List<Type> arguments = com.tkisor.nekojs.core.reflect.JavaGenericTypes.arguments(parameterized);
        if (arguments.size() != 1 || !(arguments.getFirst() instanceof WildcardType wildcard)) {
            return null;
        }
        if (List.of(wildcard.getLowerBounds()).size() > 0
                || wildcard.getUpperBounds().length != 1
                || wildcard.getUpperBounds()[0] == Object.class) {
            return null;
        }
        Type bound = wildcard.getUpperBounds()[0];
        return renderTypeRef(JavaTypeScriptProjection.project(bound,
                TypeReflector.toRef(bound)), aliases, false);
    }

    /** True when any rendered class used the {@code $OptionalView} capture projection. */
    public boolean captureViewUsed() {
        return !captureViewMarks.isEmpty();
    }

    /**
     * Member-driven capture view declarations for output sites of {@code Optional<? extends Bound>}.
     *
     * <p>Java hierarchies legally declare such wildcard returns on a base type while implementations
     * narrow the element ({@code Constable.describeConstable()} vs {@code Boolean}). The flattened
     * {@code $Optional<$Bound>} is invariant, so every narrowing override fails TS2416. Instead of a
     * hand-truncated shape, the view re-renders the actual instance surface of {@code java.util.Optional}
     * (and, through {@code stream()}, of {@code java.util.stream.Stream} plus {@code BaseStream}) with the
     * owner type variable instantiated at the view parameter. Returns of {@code Optional<X>} and
     * {@code Stream<X>} re-enter the views, so navigation recurses (for example
     * {@code stream().findAny().stream()}); parameters keep the concrete containers of method-local
     * variables exactly as the source class renders them.
     *
     * <p>Members and overloads whose capture semantics have no provably compatible TypeScript shape are
     * excluded by experimentally pinned rules (every branch verified against the real five-node
     * declaration trees; see the F1 evidence pages): (1a) a parameter nesting the capture container over
     * the bound — {@code or(Supplier<? extends Optional<? extends T>>)} — fails the SAM host branch in
     * both bivariant directions; (1b) inside a multi-signature group an overload whose functional alias
     * returns the bare bound — {@code reduce(BinaryOperator<B>)} — has an unrepresentable host return,
     * while the same shape stays fine on single signatures ({@code orElseGet}) through method-parameter
     * bivariance; (2) multi-signature groups keep at most the zero-argument overload (emitted first)
     * plus the first remaining overload when they mix bound-SAM overloads with a zero-argument one
     * ({@code sorted}), or just their first overload otherwise ({@code collect}); groups without
     * bound-SAM overloads keep everything ({@code toArray}, {@code orElseThrow}); (3) a return of
     * another generic container instantiated at the bound that TypeScript measures invariant
     * ({@code toList(): $List<B>}), with {@code $Iterator<B>} and {@code $Spliterator<B>} whitelisted
     * because their element positions stay covariant. Excluded members remain available on the concrete
     * implementations; wrong element types stay rejected because {@code get()} returns the upper bound.
     */
    public String renderOptionalCaptureViewDeclaration(TypeAliasRegistry moduleAliases) {
        StringBuilder body = new StringBuilder();
        String optionalView = moduleAliases.reference("java.util.Optional", "$OptionalView");
        String streamView = moduleAliases.reference("java.util.stream.Stream", "$StreamView");
        appendCaptureViewMembers(body, java.util.Optional.class.getName(),
                Map.of("T", "B"), optionalView, streamView, moduleAliases);
        return "    export interface " + optionalView + "<B> {\n" + body + "    }\n";
    }

    /** Stream half of the capture views; declared in the stream module next to its source interface. */
    public String renderStreamCaptureViewDeclaration(TypeAliasRegistry moduleAliases) {
        String optionalView = moduleAliases.reference("java.util.Optional", "$OptionalView");
        String streamView = moduleAliases.reference("java.util.stream.Stream", "$StreamView");
        StringBuilder view = new StringBuilder("    export interface " + streamView + "<B> {\n");
        // BaseStream only contributes inherited members; identical re-declarations would still be
        // legal overload syntax, so a plain concatenation keeps every overload intact.
        appendCaptureViewMembers(view, java.util.stream.Stream.class.getName(),
                Map.of("T", "B"), optionalView, streamView, moduleAliases);
        appendCaptureViewMembers(view, java.util.stream.BaseStream.class.getName(),
                Map.of("T", "B", "S", streamView + "<B>"), optionalView, streamView, moduleAliases);
        return view + "    }\n";
    }

    /** First identifier of a rendered member line; groups overloads across source classes. */
    private static String captureMemberToken(String line) {
        String trimmed = line.trim();
        int end = 0;
        while (end < trimmed.length() && (Character.isLetterOrDigit(trimmed.charAt(end))
                || "[$]_<>".indexOf(trimmed.charAt(end)) >= 0)) {
            if (trimmed.charAt(end) == '(' || trimmed.charAt(end) == '<') break;
            end++;
        }
        return trimmed.substring(0, end);
    }

    private void appendCaptureViewMembers(StringBuilder output, String className,
                                          Map<String, String> substitutions, String optionalView,
                                          String streamView, TypeAliasRegistry moduleAliases) {
        TypeDecl declaration = captureSourceDecls.computeIfAbsent(className, name -> {
            try {
                return new TypeReflector().reflect(Class.forName(name, false,
                        Thread.currentThread().getContextClassLoader()));
            } catch (ClassNotFoundException failure) {
                throw new IllegalStateException("[NEKO-4039] Capture view source unavailable: " + name, failure);
            }
        });
        String rendered = render(declaration, moduleAliases);
        List<String> candidates = new java.util.ArrayList<>();
        boolean inBody = false;
        for (String line : rendered.split("\n", -1)) {
            if (!inBody) {
                // Only the instance body of the declaration: interface statics live in a separate
                // `export const` object block whose members share the member indent.
                inBody = line.startsWith("    export ") && line.endsWith(" {");
                continue;
            }
            if (line.equals("    }") || line.equals("    };")) break;
            if (!line.startsWith("        ")) continue;
            String stripped = line.strip();
            // Doc comments (openers and continuation lines) are not members; docs stay on the concrete classes.
            if (stripped.startsWith("*") || stripped.startsWith("/*")) continue;
            String member = line.stripTrailing();
            String trimmed = member.strip();
            if (trimmed.startsWith("constructor") || trimmed.startsWith("static ")) continue;
            for (Map.Entry<String, String> substitution : substitutions.entrySet()) {
                member = member.replaceAll("\\b" + java.util.regex.Pattern.quote(substitution.getKey())
                                + "\\b", java.util.regex.Matcher.quoteReplacement(substitution.getValue()));
            }
            candidates.add(member);
        }
        for (String member : selectCaptureViewMembers(candidates)) {
            String transformed = transformCaptureViewMember(member, optionalView, streamView);
            if (transformed != null) output.append(transformed).append('\n');
        }
    }

    /**
     * Applies the overload-level exclusion and selection rules to the collected member lines.
     * Every rule branch is pinned by a real-tree tsc experiment (see the F1 evidence pages):
     * single-signature members keep method-parameter bivariance, so a SAM returning the bare bound
     * still passes ({@code orElseGet}); inside a multi-signature group the comparison is strict,
     * so such overloads ({@code reduce(BinaryOperator)}) cannot be represented, and the remaining
     * overload list must keep at most the zero-argument overload (first) plus the first remaining
     * overload when the group mixes bound-SAM overloads with a zero-argument one
     * ({@code sorted}), or just the first overload otherwise ({@code collect}). Groups without
     * bound-SAM overloads keep every overload in source order ({@code toArray}, {@code orElseThrow}).
     */
    private static List<String> selectCaptureViewMembers(List<String> candidates) {
        record Entry(String line, String token, String params, boolean zeroArg, boolean boundSam, boolean unsafe) {}
        List<Entry> entries = new java.util.ArrayList<>();
        java.util.regex.Pattern boundSamArgument =
                java.util.regex.Pattern.compile("\\$[\\w$]+_<[^()]*\\bB\\b");
        for (String line : candidates) {
            if (line.strip().startsWith("get ") || line.strip().startsWith("set ")) {
                entries.add(new Entry(line, "", "", false, false, false));
                continue;
            }
            int open = line.indexOf('(');
            int close = line.lastIndexOf(')');
            String params = open >= 0 && close > open ? line.substring(open + 1, close) : "";
            boolean zeroArg = params.isBlank();
            boolean boundSam = boundSamArgument.matcher(params).find();
            entries.add(new Entry(line, captureMemberToken(line), params, zeroArg, boundSam, false));
        }
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (!entry.token().isEmpty()) groups.computeIfAbsent(entry.token(), k -> new java.util.ArrayList<>()).add(entry);
        }
        // Rule 1b: within multi-signature groups a functional alias whose type arguments are all the
        // bare bound (for example $BinaryOperator_<B, B, B, B>) has an unrepresentable host branch:
        // every slot compares the narrowed element, and the callback-result slot has no bivariant
        // rescue inside overload-set comparison. Mixed-argument aliases stay ($BiConsumer_<R, B, R, B>).
        for (List<Entry> group : groups.values()) {
            if (group.size() <= 1) continue;
            for (int index = 0; index < group.size(); index++) {
                if (allBoundSamAlias(group.get(index).params())) {
                    group.set(index, new Entry(group.get(index).line(), group.get(index).token(),
                            group.get(index).params(), group.get(index).zeroArg(), group.get(index).boundSam(), true));
                }
            }
        }
        for (List<Entry> group : groups.values()) {
            List<Entry> kept = group.stream().filter(entry -> !entry.unsafe()).toList();
            if (kept.size() > 1) {
                boolean anyBoundSam = kept.stream().anyMatch(Entry::boundSam);
                Entry zero = kept.stream().filter(Entry::zeroArg).findFirst().orElse(null);
                if (anyBoundSam && zero != null) {
                    // Zero-argument overload first, then the first remaining overload in source order.
                    List<Entry> selected = new java.util.ArrayList<>();
                    selected.add(zero);
                    for (Entry entry : kept) {
                        if (entry != zero && selected.size() == 1) selected.add(entry);
                    }
                    kept = selected;
                } else if (anyBoundSam || zero == null) {
                    kept = List.of(kept.getFirst());
                }
                // Groups with neither a bound-SAM overload nor a zero-argument overload keep everything.
            }
            group.clear();
            group.addAll(kept);
        }
        // A group renders once, at its first source position, in the selected order.
        List<String> result = new java.util.ArrayList<>();
        Set<String> rendered = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (entry.token().isEmpty()) {
                result.add(entry.line());
                continue;
            }
            if (!rendered.add(entry.token())) continue;
            for (Entry selected : groups.get(entry.token())) result.add(selected.line());
        }
        return result;
    }

    /** True when a functional alias's every type argument is the bare bound, e.g. {@code $BinaryOperator_<B, B, B, B>}. */
    private static boolean allBoundSamAlias(String params) {
        java.util.regex.Matcher alias = java.util.regex.Pattern.compile("\\$[\\w$]+_<").matcher(params);
        while (alias.find()) {
            int depth = 1;
            int index = alias.end();
            List<String> arguments = new java.util.ArrayList<>();
            StringBuilder current = new StringBuilder();
            while (index < params.length() && depth > 0) {
                char c = params.charAt(index);
                if (c == '<') depth++;
                else if (c == '>') {
                    depth--;
                    if (depth == 0) break;
                }
                if (depth == 1 && c == ',') {
                    arguments.add(current.toString().strip());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
                index++;
            }
            arguments.add(current.toString().strip());
            if (!arguments.isEmpty() && arguments.stream().allMatch(argument -> argument.equals("B"))) {
                return true;
            }
        }
        return false;
    }

    /** Applies the container-return rewrite plus the parameter and return exclusions; null drops the member. */
    private static String transformCaptureViewMember(String member, String optionalView, String streamView) {
        if (member.strip().startsWith("get ") || member.strip().startsWith("set ")) return member;
        int paramsStart = member.indexOf('(');
        int paramsEnd = member.lastIndexOf(')');
        if (paramsStart < 0 || paramsEnd < paramsStart) return member;
        String head = member.substring(0, paramsStart);
        String params = member.substring(paramsStart + 1, paramsEnd);
        String result = member.substring(paramsEnd + 1);
        boolean returns = result.startsWith(": ");
        String value = returns ? result.substring(2) : "";
        if (value.endsWith(";")) value = value.substring(0, value.length() - 1);
        // Rule 1a: the parameter nests the capture container over the view bound; the SAM host branch
        // then compares the narrowed element in both bivariant directions.
        if (params.contains("$Optional<B>") || params.contains("$Stream<B>")) return null;
        if (returns) {
            // Returns re-enter the capture views; parameters keep the source's concrete containers.
            if (value.startsWith("$Optional<")) value = optionalView + value.substring("$Optional".length());
            else if (value.startsWith("$Stream<")) value = streamView + value.substring("$Stream".length());
            else {
                // Rule 3: a non-view generic container of the bound that TypeScript measures invariant
                // (toList(): $List<B>). $Iterator<B> and $Spliterator<B> are measured covariant and stay.
                boolean bare = value.equals("B") || value.equals("B[]");
                boolean view = value.startsWith(optionalView + "<") || value.startsWith(streamView + "<");
                boolean whitelisted = value.equals("$Iterator<B>") || value.equals("$Spliterator<B>");
                if (!bare && !view && !whitelisted
                        && java.util.regex.Pattern.compile("\\bB\\b").matcher(value).find()) {
                    return null;
                }
            }
            return head + "(" + params + "): " + value + ";";
        }
        return member;
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

    /** 简单 ApiTypeRef → TS（无别名表；probe.add_global 的全局声明类型等声明位使用）。 */
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
     * ApiTypeRef → TS。{@code input=true} 且提供别名表时应用输入别名放宽——语义与旧
     * TypeConverter 逐项对齐：参数化符号先查集合别名（结构化实参数组，防嵌套泛型被
     * {@code ", "} 拆坏），非参数化符号查类别名（适配器/枚举惰性别名）。
     */
    public static String renderTypeRef(ApiTypeRef ref, TypeAliasRegistry aliases, boolean input) {
        return renderTypeRef(ref, aliases, input, new LinkedHashSet<>());
    }

    /** Internal recursive renderer carrying functional-alias recursion state. */
    public static String renderTypeRef(ApiTypeRef ref, TypeAliasRegistry aliases, boolean input,
                                      Set<String> expandingFunctionalAliases) {
        if (ref == null) return "any";
        return switch (ref.kind()) {
            case VOID -> "void";
            case PRIMITIVE -> mapPrimT(ref.name());
            case TYPE_VARIABLE -> ref.name();
            case ARRAY -> {
                ApiTypeRef element = ref.arguments().getFirst();
                String rendered = renderTypeRef(element, aliases, input, expandingFunctionalAliases);
                yield (element.kind() == ApiTypeRef.Kind.UNION ? "(" + rendered + ")" : rendered) + "[]";
            }
            case UNION -> ref.arguments().stream()
                    .map(a -> renderTypeRef(a, aliases, input, expandingFunctionalAliases))
                    .collect(Collectors.joining(" | "));
            case SYMBOL -> renderSymbol(ref, aliases, input, expandingFunctionalAliases);
            case CALLBACK -> "(...args: any[]) => any";
        };
    }

    /** Applies conversion at the outer symbol while ordinary host type arguments retain their identity. */
    private static String renderSymbol(ApiTypeRef ref, TypeAliasRegistry aliases, boolean input,
                                       Set<String> expandingFunctionalAliases) {
        String fqn = fqnOfSymbol(ref.name());
        if (input && aliases != null) {
            String registeredAlias = aliases.getRegisteredAlias(fqn);
            if (registeredAlias != null) {
                return aliases.renderRegisteredAlias(fqn, ref.arguments().stream()
                        .map(argument -> renderTypeRef(argument, aliases, false, expandingFunctionalAliases)).toList());
            }
        }
        List<ApiTypeRef> scalarValues = ref.arguments().isEmpty() && (aliases == null || aliases.projectsScalarValues())
                ? JavaTypeScriptProjection.scalarAlternatives(fqn) : List.of();
        if (!scalarValues.isEmpty()) {
            TypeAliasRegistry.FunctionalInterfaceAlias functionalAlias = input && aliases != null
                    ? aliases.getFunctionalInterfaceAlias(fqn) : null;
            String host = functionalAlias == null ? renderHostSymbol(ref, aliases, expandingFunctionalAliases)
                    : FunctionalInterfaceAliasGenerator.renderInputType(ref, functionalAlias, aliases, expandingFunctionalAliases);
            // Keep identity until this boundary: an explicit input adapter above replaces the whole value domain.
            // Parentheses also keep array and varargs element unions intact at their callers.
            return "(" + scalarValues.stream().map(value -> renderTypeRef(value, aliases, false, expandingFunctionalAliases))
                    .collect(Collectors.joining(" | ")) + " | " + host + ")";
        }
        if (input && aliases != null) {
            TypeAliasRegistry.FunctionalInterfaceAlias functionalAlias =
                    aliases.getFunctionalInterfaceAlias(fqn);
            if (functionalAlias != null) {
                return FunctionalInterfaceAliasGenerator.renderInputType(ref, functionalAlias, aliases,
                        expandingFunctionalAliases);
            }
            if (!ref.arguments().isEmpty()) {
                String[] hostArgs = ref.arguments().stream()
                        .map(a -> renderTypeRef(a, aliases, false, expandingFunctionalAliases))
                        .toArray(String[]::new);
                String[] renderedArgs = ref.arguments().stream()
                        .map(a -> renderTypeRef(a, aliases, true, expandingFunctionalAliases))
                        .toArray(String[]::new);
                String alias = aliases.getCollectionAlias(fqn, hostArgs, renderedArgs);
                if (alias != null) return alias;
            } else if (aliases.hasAlias(fqn)) {
                return aliases.getAlias(fqn);
            }
        }
        // Graal exposes boxed characters as string values, including returns and generic contents.
        // This is an intrinsic value projection; ordinary host identities and input adapters stay separate.
        if (fqn.equals("java.lang.Character")) return "string";
        return renderHostSymbol(ref, aliases, expandingFunctionalAliases);
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

    /** symbol name 形如 {@code "java:java.util.List"} → 取 FQN 部分。 */
    private static String fqnOfSymbol(String symbolName) {
        int colon = symbolName.indexOf(':');
        return colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
    }

    /**
     * 渲染 {@code docs} 列表为 JSDoc 块（行前用 {@code indent} 缩进）。单行 doc 折叠为一行形式的
     * 块注释，多行（含条目内嵌换行）逐行以星号前缀展开。docs 为空时不产出任何内容，
     * 因此 TypeReflector 的默认产出（docs 为空）渲染结果与旧实现逐字一致。
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

    /** symbol name 形如 {@code "java:java.util.List"} → 取最后一段点分作为简单名。 */
    private static String simpleSymbolName(String symbolName) {
        int colon = symbolName.indexOf(':');
        String qn = colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
        int dot = qn.lastIndexOf('.');
        return dot >= 0 ? qn.substring(dot + 1) : qn;
    }

    /** 渲染用的类名：renameTo（modify_type 改名）优先，否则回退 sourceClass 原名。 */
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
