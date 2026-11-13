package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.RecipeHandlerMethodEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeScriptDeclarationFormatter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.wrapper.RecipeReflectionUtil;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;

import java.util.*;

/**
 * Generates the TypeScript declarations for recipe events.
 *
 * <p>Following ProbeJS, it defines these in @side-only/server/events/recipes/index.d.ts:
 * <ul>
 *   <li>a builder class for every recipe type (such as Minecraft$Smelting extends $RecipeJsonBuilder)</li>
 *   <li>the DocumentedRecipes class, which organizes all namespaces into nested objects</li>
 * </ul>
 *
 * <p>TypeScriptClassRenderer then uses its overrideGetter mechanism so the $RecipeEventJS.recipes getter
 * returns DocumentedRecipes instead of $RecipeRegistryProxy.
 */
public final class RecipeEventDeclarationGenerator {
    private final TypeAliasRegistry aliasRegistry;
    private final InputAliases inputAliases;

    public RecipeEventDeclarationGenerator(TypeAliasRegistry aliasRegistry) {
        this(aliasRegistry, new InputAliases(Map.of()));
    }

    private RecipeEventDeclarationGenerator(TypeAliasRegistry aliasRegistry, InputAliases inputAliases) {
        this.aliasRegistry = aliasRegistry;
        this.inputAliases = inputAliases;
    }

    private record SchemaKey(String namespace, String name) {}
    private record InputAliases(Map<SchemaKey, String> options) {}

    /** Reserves only named-options declarations; component types use the canonical input alias registry. */
    private static InputAliases allocateInputAliases(List<RecipeNamespaceCatalogEntry> namespaces, Set<String> occupied) {
        Map<SchemaKey, String> options = new LinkedHashMap<>();
        for (var namespace : namespaces.stream().sorted(Comparator.comparing(RecipeNamespaceCatalogEntry::namespace)).toList()) {
            for (var schema : namespace.schemaTypes().stream().sorted(Comparator.comparing(RecipeSchemaTypeEntry::type)).toList()) {
                if (!hasBuilder(schema) || schema.fields().stream().noneMatch(field ->
                        field.inputType() != null || isStructuralInput(field.inputShape()))) continue;
                options.put(new SchemaKey(namespace.namespace(), schema.type()), allocateName(typeName(namespace.namespace())
                        + "$" + typeName(schema.type()) + "Options", occupied));
            }
        }
        return new InputAliases(Collections.unmodifiableMap(options));
    }

    private static boolean isStructuralInput(AdapterInputShape shape) {
        if (shape instanceof AdapterInputShape.NamedValue named) return isStructuralInput(named.shape());
        return shape instanceof AdapterInputShape.UnionValue || shape instanceof AdapterInputShape.ObjectValue
                || shape instanceof AdapterInputShape.ArrayOfValue;
    }

    private static String allocateName(String base, Set<String> occupied) {
        String name = base;
        for (int suffix = 2; !occupied.add(name); suffix++) name = base + suffix;
        return name;
    }

    private static String typeName(String source) {
        String name = capitalize(source);
        StringBuilder result = new StringBuilder();
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) result.append('_');
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            result.append(Character.isJavaIdentifierPart(character) ? character : '_');
        }
        return result.toString();
    }

    private void generateInputAliases(StringBuilder output, List<RecipeNamespaceCatalogEntry> namespaces) {
        for (var namespace : namespaces) {
            for (var schema : namespace.schemaTypes()) {
                String name = inputAliases.options().get(new SchemaKey(namespace.namespace(), schema.type()));
                if (name != null) output.append("    export type ").append(name).append(" = ")
                        .append(namedOptionsType(schema, namespace.namespace())).append(";\n");
            }
        }
        if (!inputAliases.options().isEmpty()) output.append('\n');
    }

    /**
     * Resolves the input alias name of a Java type (such as ItemStack to $ItemStack_). It uses the
     * adapter-driven registered alias first and falls back to {@code $simple_} when there is none.
     */
    private String resolveAlias(String pkg, String simple) {
        String fqn = pkg + "." + simple;
        if (aliasRegistry.hasAlias(fqn)) return aliasRegistry.getAlias(fqn);
        return aliasRegistry.reference(fqn, "$" + simple + "_");
    }

    public static boolean hasDeclarations(List<RecipeNamespaceCatalogEntry> namespaces, ScriptType scriptType) {
        return scriptType == ScriptType.SERVER && !namespaces.isEmpty();
    }

    /** Java references from nominal inputs, structural fallbacks and builder execution types. */
    public Set<String> referencedClasses(List<RecipeNamespaceCatalogEntry> namespaces) {
        if (!hasDeclarations(namespaces, ScriptType.SERVER)) return Set.of();
        Set<TypeScriptImportNames.Symbol> imports = new LinkedHashSet<>();
        collectImports(namespaces, imports);
        Set<String> result = new LinkedHashSet<>();
        for (var symbol : imports) result.add(symbol.fqn());
        return Collections.unmodifiableSet(result);
    }

    public String generate(List<RecipeNamespaceCatalogEntry> namespaces, ScriptType scriptType) {
        if (!hasDeclarations(namespaces, scriptType)) return "";
        Set<TypeScriptImportNames.Symbol> imports = new LinkedHashSet<>();
        collectImports(namespaces, imports);
        Set<String> declarations = new HashSet<>(Set.of("DocumentedRecipes", "RegistryTypes"));
        for (var namespace : namespaces) {
            for (var schema : namespace.schemaTypes()) {
                if (hasBuilder(schema)) declarations.add(capitalize(namespace.namespace()) + "$" + capitalize(schema.type()));
            }
        }
        InputAliases inputs = allocateInputAliases(namespaces, declarations);
        TypeScriptImportNames names = new TypeScriptImportNames(imports, declarations, Set.of());
        TypeAliasRegistry moduleAliases = names.hasAliases() ? aliasRegistry.withReferences(names::reference) : aliasRegistry;
        RecipeEventDeclarationGenerator renderer = new RecipeEventDeclarationGenerator(moduleAliases, inputs);
        Map<String, Set<String>> importsByPkg = new TreeMap<>();
        for (var symbol : imports) {
            String pkg = symbol.fqn().substring(0, symbol.fqn().lastIndexOf('.'));
            String local = names.reference(symbol.fqn(), symbol.exportedName());
            String binding = symbol.exportedName().equals(local) ? local : symbol.exportedName() + " as " + local;
            importsByPkg.computeIfAbsent(pkg, ignored -> new TreeSet<>()).add(binding);
        }
        StringBuilder sb = new StringBuilder();
        if (namespaces.stream().flatMap(namespace -> namespace.schemaTypes().stream())
                .filter(schema -> schema.unsupportedReason() == null)
                .flatMap(schema -> schema.fields().stream()).filter(field -> field.inputShape() != null)
                .anyMatch(field -> AdapterAliasGenerator.renderShape(field.inputShape(), moduleAliases, true).usesRegistry())) {
            sb.append("import type { RegistryTypes } from \"@special/types\";\n\n");
        }
        for (var entry : importsByPkg.entrySet()) {
            String importPath = "java:" + entry.getKey().replace('.', '/');
            sb.append("import { ").append(String.join(", ", entry.getValue()));
            sb.append(" } from \"").append(importPath).append("\";\n");
        }
        sb.append("\n");

        renderer.generateInputAliases(sb, namespaces);

        // 1. Emit a separate builder class with chained setters for every schema type.
        for (RecipeNamespaceCatalogEntry ns : namespaces) {
            for (RecipeSchemaTypeEntry schemaType : ns.schemaTypes()) {
                if (!hasBuilder(schemaType)) continue;
                renderer.generateSchemaClass(sb, schemaType, ns.namespace());
            }
        }

        // 2. Emit the DocumentedRecipes class. The point is to organize all namespaces into nested objects.
        sb.append("\n    /**\n");
        sb.append("     * Top-level recipe registry. Access via event.recipes.<namespace>.<type>(...)\n");
        sb.append("     */\n");
        sb.append("    export class DocumentedRecipes {\n");

        for (RecipeNamespaceCatalogEntry ns : namespaces) {
            if (ns.handlerMethods().isEmpty() && ns.schemaTypes().isEmpty()) continue;
            renderer.generateNamespaceMember(sb, ns);
        }

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Emits the members of one namespace, the properties inside DocumentedRecipes. For example:
     * minecraft: {
     *     smelting(result: $ItemStack_, ingredient: $Ingredient_, xp?: number, time?: number): Minecraft$Smelting;
     *     crafting_shaped(...): Minecraft$CraftingShaped;
     * }
     */
    private void generateNamespaceMember(StringBuilder sb, RecipeNamespaceCatalogEntry ns) {
        sb.append("        ").append(safeMethodName(ns.namespace())).append(": {\n");

        Set<String> emittedMethods = new HashSet<>();
        Set<String> rejectedMethods = new HashSet<>();
        for (var schema : ns.schemaTypes()) {
            if (schema.unsupportedReason() == null) continue;
            sb.append("            ").append(safeMethodName(schema.type())).append(": never;\n");
            emittedMethods.add(schema.type());
            rejectedMethods.add(schema.type());
        }

        // Handler methods, meaning the methods that have a Java implementation (such as shaped, smelting).
        for (RecipeHandlerMethodEntry method : ns.handlerMethods()) {
            if (rejectedMethods.contains(method.methodName())) continue;
            generateHandlerMethod(sb, method, ns.namespace());
            emittedMethods.add(method.methodName());
        }

        // Schema type methods, meaning the automatically discovered recipe types.
        for (RecipeSchemaTypeEntry schemaType : ns.schemaTypes()) {
            if (emittedMethods.contains(schemaType.type())) continue;
            generateSchemaMethod(sb, schemaType, ns.namespace());
            emittedMethods.add(schemaType.type());
        }

        sb.append("        };\n");
    }

    /**
     * Emits a handler method, one that has a Java implementation.
     */
    private void generateHandlerMethod(StringBuilder sb, RecipeHandlerMethodEntry method, String namespace) {
        List<String> parameters = new ArrayList<>();
        List<RecipeHandlerMethodEntry.HandlerParam> params = method.params();
        for (int i = 0; i < params.size(); i++) {
            RecipeHandlerMethodEntry.HandlerParam param = params.get(i);
            boolean omittedTail = i >= method.minArgs();
            String type = renderHandlerParameter(param, aliasRegistry);
            parameters.add(safeParamName(param.name(), i) + (omittedTail ? "?" : "") + ": "
                    + (param.optional() && !omittedTail ? "(" + type + ") | null | undefined" : type));
        }
        // Native results follow the Java method contract, not a similarly named schema class.
        TypeScriptDeclarationFormatter.appendCallable(sb, 12, safeMethodName(method.methodName()), parameters,
                ": " + renderHandlerReturn(method, aliasRegistry) + ";");
    }

    /**
     * Emits a schema type method, for an automatically discovered recipe type.
     */
    private void generateSchemaMethod(StringBuilder sb, RecipeSchemaTypeEntry schemaType, String namespace) {
        String returnType = hasBuilder(schemaType)
                ? capitalize(namespace) + "$" + capitalize(schemaType.type()) : baseBuilderType();
        for (var overload : RecipeConstructorOverloads.plan(schemaType.constructors(), schemaType.hasConstructorOverrides())) {
            List<String> parameters = new ArrayList<>();
            for (int i = 0; i < overload.fields().size(); i++) {
                String field = overload.fields().get(i);
                String label = safeParamName(positionalLabel(schemaType, field), i);
                parameters.add(label + (i >= overload.requiredCount() ? "?" : "") + ": "
                        + getFieldTsType(schemaType, field, namespace));
            }
            appendSchemaOverload(sb, schemaType.type(), parameters, returnType);
        }
        if (!schemaType.fields().isEmpty()) {
            String options = inputAliases.options().get(new SchemaKey(namespace, schemaType.type()));
            appendSchemaOverload(sb, schemaType.type(), List.of("options: "
                    + (options == null ? namedOptionsType(schemaType, namespace) : options)), returnType);
        }
    }

    /** Keeps long declarations readable without hiding their nominal parameter types behind field aliases. */
    private void appendSchemaOverload(StringBuilder output, String method, List<String> parameters, String result) {
        TypeScriptDeclarationFormatter.appendCallable(output, 12, safeMethodName(method), parameters, ": " + result + ";");
    }

    private static String positionalLabel(RecipeSchemaTypeEntry schema, String field) {
        StringBuilder camel = new StringBuilder();
        boolean upper = false;
        for (char character : field.toCharArray()) {
            if (character == '_') { upper = true; continue; }
            camel.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        String candidate = camel.toString();
        return field.equals(schema.fieldAliases().get(candidate)) ? candidate : field;
    }

    /**
     * Emits the builder class for a schema type.
     */
    private void generateSchemaClass(StringBuilder sb, RecipeSchemaTypeEntry schemaType, String namespace) {
        String className = capitalize(namespace) + "$" + capitalize(schemaType.type());

        sb.append("    export class ").append(className).append(" extends ").append(baseBuilderType()).append(" {\n");
        Map<String, String> setters = new LinkedHashMap<>();
        if (schemaType.fieldFunctions() == null) {
            schemaType.fields().forEach(field -> setters.put(field.name(), field.name()));
            setters.putAll(schemaType.fieldAliases());
        } else {
            setters.putAll(schemaType.fieldFunctions());
        }
        Set<String> targetNames = new HashSet<>();
        for (Method method : executionMethods(schemaType)) {
            targetNames.add(method.getName());
            appendExecutionMethod(sb, schemaType, method, aliasRegistry);
        }
        for (var setter : setters.entrySet()) {
            if (targetNames.contains(setter.getKey())) continue;
            for (var field : schemaType.fields()) {
                if (field.name().equals(setter.getValue())) appendSetter(sb, setter.getKey(), field, namespace);
            }
        }
        for (String function : schemaType.functions()) {
            if (targetNames.contains(function)) continue;
            sb.append("        ").append(safeMethodName(function)).append("(): this;\n");
        }
        sb.append("    }\n");
    }

    private static boolean hasBuilder(RecipeSchemaTypeEntry schema) {
        return schema.unsupportedReason() == null && (!schema.fields().isEmpty() || !schema.functions().isEmpty()
                || schema.executionType() != Object.class);
    }

    private static List<Method> executionMethods(RecipeSchemaTypeEntry schema) {
        return Arrays.stream(schema.executionType().getMethods())
                .filter(method -> method.getDeclaringClass() != Object.class && !method.isBridge() && !method.isSynthetic())
                .filter(method -> !RecipeReflectionUtil.isHiddenMethod(method))
                .sorted(Comparator.comparing(Method::toGenericString)).toList();
    }

    /** Uses the same Java type projections as ordinary host declarations, preserving fluent self returns. */
    private static void appendExecutionMethod(StringBuilder sb, RecipeSchemaTypeEntry schema, Method method,
                                              TypeAliasRegistry aliases) {
        StringBuilder head = new StringBuilder(safeMethodName(method.getName()));
        if (method.getTypeParameters().length > 0) {
            head.append("<");
            for (int i = 0; i < method.getTypeParameters().length; i++) {
                if (i > 0) head.append(", ");
                var variable = method.getTypeParameters()[i];
                head.append(variable.getName());
                var bounds = Arrays.stream(variable.getBounds()).filter(bound -> bound != Object.class)
                        .map(bound -> renderExecutionType(bound, aliases, false)).toList();
                if (!bounds.isEmpty()) head.append(" extends ").append(String.join(" & ", bounds));
            }
            head.append(">");
        }
        List<String> renderedParameters = new ArrayList<>();
        var parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            boolean varargs = method.isVarArgs() && i == parameters.length - 1;
            String name = (varargs ? "..." : "") + safeParamName(parameters[i].getName(), i);
            Type type = method.getGenericParameterTypes()[i];
            boolean optional = parameters[i].getType() == Optional.class;
            if (optional && type instanceof ParameterizedType parameterized) type = parameterized.getActualTypeArguments()[0];
            renderedParameters.add(name + (optional ? "?" : "") + ": " + renderExecutionType(type, aliases, true));
        }
        boolean self = method.getReturnType() != Object.class && method.getReturnType().isAssignableFrom(schema.executionType());
        TypeScriptDeclarationFormatter.appendCallable(sb, 8, head.toString(), renderedParameters,
                ": " + (self ? "this" : renderExecutionType(method.getGenericReturnType(), aliases, false)) + ";");
    }

    private static String renderExecutionType(Type type, TypeAliasRegistry aliases, boolean input) {
        return TypeScriptClassRenderer.renderTypeRef(input ? TypeReflector.toRef(type) : TypeReflector.outputRef(type), aliases, input);
    }

    private String baseBuilderType() {
        return aliasRegistry.reference("com.tkisor.nekojs.api.recipe.RecipeJsonBuilder", "$RecipeJsonBuilder");
    }

    private void appendSetter(StringBuilder sb, String name, RecipeSchemaTypeEntry.SchemaField field, String namespace) {
        TypeScriptDeclarationFormatter.appendCallable(sb, 8, safeMethodName(name),
                List.of(safeParamName(field.name(), 0) + ": " + fieldTsType(field, namespace)), ": this;");
    }

    private String fieldTsType(RecipeSchemaTypeEntry.SchemaField field, String namespace) {
        if (field.inputType() != null) return TypeScriptClassRenderer.renderDeclaredInputType(field.inputType(), aliasRegistry);
        if (field.inputShape() != null) return renderStructuralInput(field.inputShape(), aliasRegistry);
        return mapFieldKindToTs(field.kind()) + (field.array() ? "[]" : "");
    }

    private static String renderStructuralInput(AdapterInputShape shape, TypeAliasRegistry aliases) {
        return AdapterAliasGenerator.renderShape(shape, aliases.withRawValueReferences(), false).type();
    }

    private String namedOptionsType(RecipeSchemaTypeEntry schema, String namespace) {
        List<String> ordinary = new ArrayList<>();
        List<String> alternatives = new ArrayList<>();
        for (var field : schema.fields()) {
            List<String> names = new ArrayList<>(List.of(field.name()));
            schema.fieldAliases().forEach((alias, canonical) -> { if (canonical.equals(field.name())) names.add(alias); });
            String type = fieldTsType(field, namespace);
            if (names.size() == 1) {
                ordinary.add(safeMethodName(field.name()) + (field.required() ? "" : "?") + ": " + type);
                continue;
            }
            // The runtime rejects duplicate canonical/alias keys, even when their values are equal.
            List<String> choices = new ArrayList<>();
            for (String selected : names) {
                List<String> slots = new ArrayList<>();
                for (String name : names) {
                    slots.add(safeMethodName(name) + (name.equals(selected) && field.required() ? "" : "?")
                            + ": " + (name.equals(selected) ? type : "never"));
                }
                choices.add("{ " + String.join(", ", slots) + " }");
            }
            alternatives.add("(" + String.join(" | ", choices) + ")");
        }
        if (!ordinary.isEmpty() || alternatives.isEmpty()) alternatives.addFirst("{ " + String.join(", ", ordinary) + " }");
        return String.join(" & ", alternatives);
    }

    private String getFieldTsType(RecipeSchemaTypeEntry schemaType, String fieldName, String namespace) {
        for (RecipeSchemaTypeEntry.SchemaField field : schemaType.fields()) {
            if (field.name().equals(fieldName)) {
                return fieldTsType(field, namespace);
            }
        }
        return "any";
    }

    private String mapFieldKindToTs(String kind) {
        String[] pkgType = kindToImport(kind);
        if (pkgType != null) return resolveAlias(pkgType[0], pkgType[1]);
        return switch (kind) {
            case "STRING" -> "string";
            case "INT", "NUMBER" -> "number";
            case "BOOLEAN" -> "boolean";
            case "JSON" -> "object";
            default -> "any";
        };
    }

    /**
     * Collects the input alias types used by all namespaces and emits the import map for them.
     */
    private void collectImports(List<RecipeNamespaceCatalogEntry> namespaces, Set<TypeScriptImportNames.Symbol> imports) {
        boolean usesBuilder = namespaces.stream().anyMatch(namespace -> {
            Set<String> rejected = namespace.schemaTypes().stream().filter(schema -> schema.unsupportedReason() != null)
                    .map(RecipeSchemaTypeEntry::type).collect(java.util.stream.Collectors.toSet());
            return namespace.schemaTypes().stream().anyMatch(schema -> schema.unsupportedReason() == null)
                    || namespace.handlerMethods().stream().anyMatch(method -> !rejected.contains(method.methodName())
                            && method.returnType() == null);
        });
        if (usesBuilder) imports.add(new TypeScriptImportNames.Symbol(
                "com.tkisor.nekojs.api.recipe.RecipeJsonBuilder", "$RecipeJsonBuilder"));
        for (var namespace : namespaces) {
            Set<String> rejected = new HashSet<>();
            for (var schema : namespace.schemaTypes()) {
                if (schema.unsupportedReason() != null) {
                    rejected.add(schema.type());
                    continue;
                }
                TypeAliasRegistry collectingAliases = aliasRegistry.withReferences((fqn, symbol) -> {
                    imports.add(new TypeScriptImportNames.Symbol(fqn, symbol));
                    return symbol;
                });
                for (Method method : executionMethods(schema)) {
                    appendExecutionMethod(new StringBuilder(), schema, method, collectingAliases);
                }
                for (var field : schema.fields()) {
                    if (field.inputType() != null) {
                        TypeScriptClassRenderer.renderDeclaredInputType(field.inputType(), collectingAliases);
                    } else if (field.inputShape() != null) {
                        renderStructuralInput(field.inputShape(), collectingAliases);
                    } else {
                        String[] target = kindToImport(field.kind());
                        if (target != null) imports.add(new TypeScriptImportNames.Symbol(target[0] + "." + target[1], "$" + target[1] + "_"));
                    }
                }
            }
            for (var method : namespace.handlerMethods()) {
                if (rejected.contains(method.methodName())) continue;
                TypeAliasRegistry collecting = aliasRegistry.withReferences((fqn, symbol) -> {
                    imports.add(new TypeScriptImportNames.Symbol(fqn, symbol));
                    return symbol;
                });
                for (var parameter : method.params()) renderHandlerParameter(parameter, collecting);
                renderHandlerReturn(method, collecting);
            }
        }
    }

    /**
     * Maps a field kind to a (package, className) pair. The className needs the _ suffix to become an
     * input alias. The package path comes from the platform
     * ({@link com.tkisor.nekojs.platform.Platform#recipeFieldKindPackage}):
     * {@code net.minecraft.world.item*} on 1.21+, {@code net.minecraft.item*} on 1.12.2. The alias name
     * stays the same ($ItemStack_, $Ingredient_), so the package path has to match the real class;
     * otherwise the handler reflection path, which uses the real FQN, produces a duplicate import and
     * TypeScript rejects the whole recipes declaration file.
     */
    private static String[] kindToImport(String kind) {
        RecipeFieldKind k;
        try {
            k = RecipeFieldKind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return kindToPkgType(k);
    }

    private static String[] kindToPkgType(RecipeFieldKind k) {
        String pkg;
        try {
            pkg = Platform.recipeFieldKindPackage(k);
        } catch (IllegalStateException e) {
            pkg = null; // Pure test environment with no initialized platform: schema field paths never actually render.
        }
        if (pkg == null) return null;
        String simple = switch (k) {
            case INGREDIENT -> "Ingredient";
            case ITEM_STACK -> "ItemStack";
            case FLUID_STACK -> "FluidStack";
            case FLUID_INGREDIENT -> "FluidIngredient";
            case SIZED_FLUID_INGREDIENT -> "SizedFluidIngredient";
            default -> null;
        };
        return simple == null ? null : new String[]{pkg, simple};
    }

    /**
     * Maps a handler parameter type name to a (package, className) pair, using the same platform mapping
     * as the schema kinds.
     */
    private static String[] typeToImport(String type) {
        RecipeFieldKind k = switch (type) {
            case "ItemStack" -> RecipeFieldKind.ITEM_STACK;
            case "Ingredient" -> RecipeFieldKind.INGREDIENT;
            case "FluidStack" -> RecipeFieldKind.FLUID_STACK;
            case "FluidIngredient" -> RecipeFieldKind.FLUID_INGREDIENT;
            case "SizedFluidIngredient" -> RecipeFieldKind.SIZED_FLUID_INGREDIENT;
            default -> null;
        };
        return k == null ? null : kindToPkgType(k);
    }

    private String renderHandlerParameter(RecipeHandlerMethodEntry.HandlerParam parameter, TypeAliasRegistry aliases) {
        ApiTypeRef type = parameter.typeRef();
        if (type == null && parameter.genericType() != null) type = parseLegacyType(parameter.genericType());
        if (type == null && parameter.qualifiedType() != null) type = parseLegacyType(parameter.qualifiedType());
        if (type == null) {
            String[] target = typeToImport(parameter.type());
            type = target == null ? parseLegacyType(parameter.type())
                    : ApiTypeRef.symbol(new ApiSymbolId("java", target[0] + "." + target[1]));
        }
        return TypeScriptClassRenderer.renderTypeRef(type, aliases, true);
    }

    private static String renderHandlerReturn(RecipeHandlerMethodEntry method, TypeAliasRegistry aliases) {
        return method.returnType() == null
                ? aliases.reference("com.tkisor.nekojs.api.recipe.RecipeJsonBuilder", "$RecipeJsonBuilder")
                : TypeScriptClassRenderer.renderTypeRef(method.returnType(), aliases, false);
    }

    /** Older contributors may still supply Java signature strings; only parsing is local, never type rendering. */
    private static ApiTypeRef parseLegacyType(String signature) {
        String type = signature.trim();
        if (type.endsWith("[]")) return ApiTypeRef.array(parseLegacyType(type.substring(0, type.length() - 2)));
        if (type.startsWith("[")) {
            String component = type.substring(1);
            if (component.startsWith("L") && component.endsWith(";")) component = component.substring(1, component.length() - 1);
            else component = switch (component) {
                case "Z" -> "boolean"; case "B", "S", "I", "J" -> "int";
                case "F", "D" -> "float"; case "C" -> "string"; default -> component;
            };
            return ApiTypeRef.array(parseLegacyType(component));
        }
        int generic = type.indexOf('<');
        if (generic >= 0) return ApiTypeRef.symbol(new ApiSymbolId("java", type.substring(0, generic)),
                splitTopLevel(type.substring(generic + 1, type.lastIndexOf('>'))).stream()
                        .map(RecipeEventDeclarationGenerator::parseLegacyType).toList());
        return switch (type) {
            case "void", "java.lang.Void" -> ApiTypeRef.voidType();
            case "string", "char", "java.lang.String", "java.lang.Character" -> ApiTypeRef.primitive("string");
            case "boolean", "java.lang.Boolean" -> ApiTypeRef.primitive("boolean");
            case "byte", "short", "int", "long", "float", "double", "number", "java.lang.Byte", "java.lang.Short",
                    "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double" -> ApiTypeRef.primitive("number");
            case "RecipeJsonValue", "json", "object", "java.lang.Object" -> ApiTypeRef.primitive("object");
            case "any" -> ApiTypeRef.primitive("any");
            default -> ApiTypeRef.symbol(new ApiSymbolId("java", type));
        };
    }

    private static List<String> splitTopLevel(String signature) {
        List<String> arguments = new ArrayList<>();
        int depth = 0, start = 0;
        for (int index = 0; index < signature.length(); index++) {
            char current = signature.charAt(index);
            if (current == '<') depth++;
            else if (current == '>') depth--;
            else if (current == ',' && depth == 0) {
                arguments.add(signature.substring(start, index).trim());
                start = index + 1;
            }
        }
        arguments.add(signature.substring(start).trim());
        return arguments;
    }

    /**
     * Makes a parameter name safe: it must not be a TypeScript reserved word and must not collide with a
     * type name.
     */
    private static String safeParamName(String name, int index) {
        if (name == null || name.isEmpty()) return "arg" + index;
        // TypeScript reserved words.
        return switch (name) {
            case "default", "function", "class", "let", "const", "var", "if", "else",
                 "return", "for", "while", "do", "switch", "case", "break", "continue",
                 "throw", "try", "catch", "finally", "new", "delete", "typeof", "instanceof",
                 "in", "of", "this", "super", "extends", "implements", "interface",
                 "package", "import", "export", "from", "as" -> "_" + name;
            default -> name;
        };
    }

    /**
     * Makes a method name safe: a recipe type name that is not a valid identifier (it contains special
     * characters) is wrapped in quotes.
     */
    private static String safeMethodName(String name) {
        if (name == null || name.isEmpty()) return "_";
        // Check whether the name is a valid JS identifier.
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return "\"" + name + "\"";
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return "\"" + name + "\"";
        }
        return name;
    }

    /**
     * Uppercasing uses {@link Locale#ROOT} explicitly, so the default locale (such as tr, where i becomes
     * İ) cannot make the output drift.
     */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.contains("_")) {
            StringBuilder result = new StringBuilder();
            boolean capitalizeNext = true;
            for (char c : s.toCharArray()) {
                if (c == '_') {
                    capitalizeNext = true;
                } else if (capitalizeNext) {
                    result.append(String.valueOf(c).toUpperCase(Locale.ROOT));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
