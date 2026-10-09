package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prepares deterministic callable-plus-host aliases for generated functional interfaces. */
public final class FunctionalInterfaceAliasGenerator {
    private static final int MAX_EXPANSIONS_PER_INTERFACE = 12;

    private final TypeAliasRegistry registry;
    private final Map<String, TypeAliasRegistry.FunctionalInterfaceAlias> aliases = new LinkedHashMap<>();
    private final Set<String> hostImports = new LinkedHashSet<>();

    public FunctionalInterfaceAliasGenerator(TypeAliasRegistry registry) {
        this.registry = registry;
    }

    /** Registers aliases only for visible interfaces that will have declarations in this output. */
    public void prepare(Set<String> generatedClasses, Set<String> hiddenClasses) {
        aliases.clear();
        hostImports.clear();
        registry.clearFunctionalInterfaceAliases();

        Set<String> visibleClasses = new LinkedHashSet<>(generatedClasses);
        visibleClasses.removeAll(hiddenClasses);
        List<String> ordered = new ArrayList<>(visibleClasses);
        Collections.sort(ordered);
        for (String fqn : ordered) {
            if (registry.getRegisteredAlias(fqn) != null) continue;
            try {
                Class<?> cls = load(fqn);
                if (cls == null || !cls.isInterface()) continue;
                // Graal converts Iterable through iterator interop rather than executable-to-SAM conversion.
                if (cls == Iterable.class) continue;
                FunctionalInterfaceResolver.Signature signature = FunctionalInterfaceResolver.resolve(cls);
                if (signature == null) continue;

                Set<String> signatureImports = new LinkedHashSet<>();
                for (Type parameter : signature.parameterTypes()) collectClasses(parameter, signatureImports);
                collectClasses(signature.returnType(), signatureImports);
                String aliasName = "$" + tsClassName(cls) + "_";
                TypeAliasRegistry.FunctionalInterfaceAlias alias =
                        new TypeAliasRegistry.FunctionalInterfaceAlias(aliasName, cls, signature, visibleClasses);
                aliases.put(fqn, alias);
                registry.registerFunctionalInterfaceAlias(fqn, alias);
                hostImports.addAll(signatureImports);
            } catch (RuntimeException | LinkageError unavailable) {
                // Loading an interface can succeed while a signature dependency is rejected by the loader.
                // Match shared Probe reflection: retain other types, report the omission, and propagate VM failures.
                NekoJS.LOGGER.warn("[NEKO-4029] Probe functional alias omitted for {}; its signature is unavailable ({})",
                        fqn, unavailable.toString(), unavailable);
            }
        }
        hostImports.removeIf(name -> name.equals(Object.class.getName()));
    }

    public TypeAliasRegistry.FunctionalInterfaceAlias getAlias(String fqn) {
        return aliases.get(fqn);
    }

    /** Types used by generated alias signatures and therefore required in the package tree. */
    public Set<String> hostImports() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(hostImports));
    }

    public String declaration(String fqn) {
        TypeAliasRegistry.FunctionalInterfaceAlias alias = aliases.get(fqn);
        if (alias == null) return null;
        Class<?> cls = alias.interfaceClass();
        TypeVariable<?>[] variables = cls.getTypeParameters();
        List<String> typeParameters = new ArrayList<>();
        Map<TypeVariable<?>, ApiTypeRef> defaults = new HashMap<>();
        for (int index = 0; index < variables.length; index++) {
            String name = "Host" + index;
            typeParameters.add(name + " = any");
            defaults.put(variables[index], ApiTypeRef.typeVariable(name));
        }

        for (int index = 0; index < alias.signature().parameterTypes().size(); index++) {
            ApiTypeRef callbackArgument = toRef(alias.signature().parameterTypes().get(index), cls,
                    true, false, defaults, alias.generatedTypes(), alias.signature().method());
            String defaultType = TypeScriptClassRenderer.renderTypeRef(callbackArgument, registry, false,
                    declarationExpansionPath(cls));
            typeParameters.add("CallbackArg" + index + " = " + defaultType);
        }
        boolean returnsVoid = alias.signature().returnType() == void.class
                || alias.signature().returnType() == Void.class;
        if (!returnsVoid) {
            ApiTypeRef callbackResult = toRef(alias.signature().returnType(), cls, false, true,
                    defaults, alias.generatedTypes(), alias.signature().method());
            String defaultType = renderCallbackResult(callbackResult, registry,
                    declarationExpansionPath(cls));
            typeParameters.add("CallbackResult = " + defaultType);
        }

        StringBuilder callback = new StringBuilder("(");
        for (int index = 0; index < alias.signature().parameterTypes().size(); index++) {
            if (index > 0) callback.append(", ");
            callback.append("arg").append(index).append(": CallbackArg").append(index);
        }
        callback.append(") => ").append(returnsVoid ? "void" : "CallbackResult");

        StringBuilder host = new StringBuilder("$").append(tsClassName(cls));
        if (variables.length > 0) {
            host.append('<');
            for (int index = 0; index < variables.length; index++) {
                if (index > 0) host.append(", ");
                host.append("Host").append(index);
            }
            host.append('>');
        }

        StringBuilder declaration = new StringBuilder("    export type ").append(alias.aliasName());
        if (!typeParameters.isEmpty()) {
            declaration.append('<').append(String.join(", ", typeParameters)).append('>');
        }
        declaration.append(" = (").append(callback).append(") | ").append(host);
        declaration.append(";\n");
        return declaration.toString();
    }

    /** Renders a functional alias use while keeping host arguments and callback positions distinct. */
    public static String renderInputType(Type declared, TypeAliasRegistry.FunctionalInterfaceAlias alias,
                                         TypeAliasRegistry registry, Set<String> expanding) {
        String fqn = alias.interfaceClass().getName();
        String expansionKey = expansionKey(alias.interfaceClass(), declared);
        if (!enterExpansion(fqn, expansionKey, expanding)) {
            return renderHostType(declared, alias.interfaceClass(), registry, alias.generatedTypes());
        }
        try {
            FunctionalInterfaceResolver.Signature signature = FunctionalInterfaceResolver.resolve(declared);
            if (signature == null) signature = alias.signature();

            List<String> arguments = new ArrayList<>();
            TypeVariable<?>[] variables = alias.interfaceClass().getTypeParameters();
            Type[] declaredArguments = declared instanceof ParameterizedType parameterized
                    ? parameterized.getActualTypeArguments() : new Type[0];
            Map<TypeVariable<?>, ApiTypeRef> substitutions = new HashMap<>();
            if (declaredArguments.length == 0) {
                for (TypeVariable<?> variable : variables) {
                    substitutions.put(variable, ApiTypeRef.primitive("any"));
                }
            }
            for (int index = 0; index < variables.length; index++) {
                ApiTypeRef hostArgument = index < declaredArguments.length
                        ? toRef(declaredArguments[index], alias.interfaceClass(), true, false, Map.of(),
                                alias.generatedTypes(), null)
                        : ApiTypeRef.primitive("any");
                if (declaredArguments.length == 0) substitutions.put(variables[index], hostArgument);
                arguments.add(TypeScriptClassRenderer.renderTypeRef(hostArgument, registry, false, expanding));
            }
            for (Type parameter : signature.parameterTypes()) {
                ApiTypeRef callbackArgument = toRef(parameter, alias.interfaceClass(), true, false, substitutions,
                        alias.generatedTypes(), signature.method());
                arguments.add(TypeScriptClassRenderer.renderTypeRef(callbackArgument, registry, false, expanding));
            }
            if (signature.returnType() != void.class && signature.returnType() != Void.class) {
                ApiTypeRef callbackResult = toRef(signature.returnType(), alias.interfaceClass(), false, true,
                        substitutions, alias.generatedTypes(), signature.method());
                arguments.add(renderCallbackResult(callbackResult, registry, expanding));
            }

            return alias.aliasName() + (arguments.isEmpty() ? "" : "<" + String.join(", ", arguments) + ">");
        } finally {
            expanding.remove(expansionKey);
        }
    }

    public static String renderInputType(ApiTypeRef declared, TypeAliasRegistry.FunctionalInterfaceAlias alias,
                                         TypeAliasRegistry registry, Set<String> expanding) {
        String fqn = alias.interfaceClass().getName();
        String expansionKey = expansionKey(alias.interfaceClass(), declared);
        if (!enterExpansion(fqn, expansionKey, expanding)) {
            return renderHostType(declared, alias.interfaceClass(), registry);
        }
        try {
            TypeVariable<?>[] variables = alias.interfaceClass().getTypeParameters();
            Map<TypeVariable<?>, ApiTypeRef> substitutions = new HashMap<>();
            List<String> arguments = new ArrayList<>();
            for (int index = 0; index < variables.length; index++) {
                ApiTypeRef hostArgument = index < declared.arguments().size()
                        ? declared.arguments().get(index) : ApiTypeRef.primitive("any");
                substitutions.put(variables[index], hostArgument);
                arguments.add(TypeScriptClassRenderer.renderTypeRef(hostArgument, registry, false, expanding));
            }
            FunctionalInterfaceResolver.Signature signature = alias.signature();
            for (Type parameter : signature.parameterTypes()) {
                ApiTypeRef callbackArgument = toRef(parameter, alias.interfaceClass(), true, false, substitutions,
                        alias.generatedTypes(), signature.method());
                arguments.add(TypeScriptClassRenderer.renderTypeRef(callbackArgument, registry, false, expanding));
            }
            if (signature.returnType() != void.class && signature.returnType() != Void.class) {
                ApiTypeRef callbackResult = toRef(signature.returnType(), alias.interfaceClass(), false, true,
                        substitutions, alias.generatedTypes(), signature.method());
                arguments.add(renderCallbackResult(callbackResult, registry, expanding));
            }
            return alias.aliasName() + (arguments.isEmpty() ? "" : "<" + String.join(", ", arguments) + ">");
        } finally {
            expanding.remove(expansionKey);
        }
    }

    /** Allows distinct nested instantiations, then bounds recursive generic growth for the same raw interface. */
    private static boolean enterExpansion(String fqn, String expansionKey, Set<String> expanding) {
        if (expanding.contains(fqn + "#*")) return false;
        if (expanding.contains(expansionKey)) return false;
        String aliasPrefix = fqn + '#';
        long sameAliasDepth = expanding.stream().filter(key -> key.startsWith(aliasPrefix)).count();
        if (sameAliasDepth >= MAX_EXPANSIONS_PER_INTERFACE) return false;
        expanding.add(expansionKey);
        return true;
    }

    private static String expansionKey(Class<?> raw, Type declared) {
        Type[] arguments = declared instanceof ParameterizedType parameterized
                ? parameterized.getActualTypeArguments() : new Type[0];
        return expansionKey(raw.getName(), Arrays.stream(arguments).map(Type::getTypeName).toList());
    }

    private static String expansionKey(Class<?> raw, ApiTypeRef declared) {
        return expansionKey(raw.getName(), declared.arguments().stream()
                .map(ApiTypeRef::compatibilityKey).toList());
    }

    private static String expansionKey(String fqn, List<String> arguments) {
        StringBuilder key = new StringBuilder(fqn).append('#');
        for (String argument : arguments) key.append(argument.length()).append(':').append(argument);
        return key.toString();
    }

    private static Set<String> declarationExpansionPath(Class<?> raw) {
        return new LinkedHashSet<>(Set.of(raw.getName() + "#*"));
    }

    /**
     * Renders an input reflection type while retaining source generic arguments until nested functional
     * interfaces have been contextualized. Ordinary wildcard handling still follows {@link TypeReflector#toRef(Type)}'s
     * existing upper-bound mapping; only a wildcard that is a SAM parameter is projected by the alias logic.
     */
    public static String renderJavaTypeInput(Type declared, TypeAliasRegistry registry, Set<String> expanding) {
        if (declared instanceof WildcardType) {
            return TypeScriptClassRenderer.renderTypeRef(
                    TypeReflector.toRef(declared), registry, true, expanding);
        }
        if (declared instanceof TypeVariable<?>) {
            return TypeScriptClassRenderer.renderTypeRef(
                    TypeReflector.toRef(declared), registry, true, expanding);
        }
        if (declared instanceof GenericArrayType array) {
            return renderJavaTypeInput(array.getGenericComponentType(), registry, expanding) + "[]";
        }
        if (declared instanceof Class<?> cls && cls.isArray()) {
            return renderJavaTypeInput(cls.getComponentType(), registry, expanding) + "[]";
        }

        Class<?> raw = FunctionalInterfaceResolver.rawClass(declared);
        if (raw != null) {
            String explicitAlias = registry.getRegisteredAlias(raw.getName());
            if (explicitAlias != null) return explicitAlias;
            TypeAliasRegistry.FunctionalInterfaceAlias functionalAlias =
                    registry.getFunctionalInterfaceAlias(raw.getName());
            if (functionalAlias != null) {
                return renderInputType(declared, functionalAlias, registry, expanding);
            }

            Type[] arguments = declared instanceof ParameterizedType parameterized
                    ? parameterized.getActualTypeArguments() : new Type[0];
            if (arguments.length > 0) {
                String[] renderedArguments = Arrays.stream(arguments)
                        .map(argument -> renderJavaTypeInput(argument, registry, expanding))
                        .toArray(String[]::new);
                String collectionAlias = registry.getCollectionAlias(raw, renderedArguments);
                if (collectionAlias != null) return collectionAlias;
                return "$" + tsClassName(raw) + "<" + String.join(", ", renderedArguments) + ">";
            }
        }
        return TypeScriptClassRenderer.renderTypeRef(
                TypeReflector.toRef(declared), registry, true, expanding);
    }

    private static String renderCallbackResult(ApiTypeRef result, TypeAliasRegistry registry, Set<String> expanding) {
        // The legacy Iterator input mapping denotes an element, which cannot implement iterator().
        // Preserve the actual host iterator contract for newly generated callback returns.
        boolean iterator = result.kind() == ApiTypeRef.Kind.SYMBOL
                && result.name().equals("java:java.util.Iterator");
        return TypeScriptClassRenderer.renderTypeRef(result, registry, !iterator, expanding);
    }

    private static String renderHostType(Type declared, Class<?> raw, TypeAliasRegistry registry,
                                         Set<String> generatedTypes) {
        StringBuilder host = new StringBuilder("$").append(tsClassName(raw));
        Type[] arguments = declared instanceof ParameterizedType parameterized
                ? parameterized.getActualTypeArguments() : new Type[0];
        if (raw.getTypeParameters().length > 0) {
            host.append('<');
            for (int index = 0; index < raw.getTypeParameters().length; index++) {
                if (index > 0) host.append(", ");
                ApiTypeRef argument = index < arguments.length
                        ? toRef(arguments[index], raw, true, false, Map.of(), generatedTypes, null)
                        : ApiTypeRef.primitive("any");
                host.append(TypeScriptClassRenderer.renderTypeRef(argument, registry, false));
            }
            host.append('>');
        }
        return host.toString();
    }

    private static String renderHostType(ApiTypeRef declared, Class<?> raw, TypeAliasRegistry registry) {
        StringBuilder host = new StringBuilder("$").append(tsClassName(raw));
        if (raw.getTypeParameters().length > 0) {
            host.append('<');
            for (int index = 0; index < raw.getTypeParameters().length; index++) {
                if (index > 0) host.append(", ");
                ApiTypeRef argument = index < declared.arguments().size()
                        ? declared.arguments().get(index) : ApiTypeRef.primitive("any");
                host.append(TypeScriptClassRenderer.renderTypeRef(argument, registry, false));
            }
            host.append('>');
        }
        return host.toString();
    }

    private static ApiTypeRef toRef(Type type, Class<?> interfaceClass, boolean callbackArgument,
                                    boolean callbackReturn, Map<TypeVariable<?>, ApiTypeRef> substitutions,
                                    Set<String> generatedTypes, Method samMethod) {
        if (type instanceof TypeVariable<?> variable) {
            if (samMethod != null && variable.getGenericDeclaration() instanceof Method method
                    && method.equals(samMethod)) return ApiTypeRef.primitive("any");
            ApiTypeRef replacement = substitutions.get(variable);
            if (replacement != null) return replacement;
            return ApiTypeRef.typeVariable(variable.getName());
        }
        if (type instanceof WildcardType wildcard) {
            Type[] lower = wildcard.getLowerBounds();
            Type[] upper = wildcard.getUpperBounds();
            if (callbackReturn && lower.length > 0) return ApiTypeRef.primitive("any");
            if (callbackArgument && lower.length > 0) {
                return toRef(lower[0], interfaceClass, true, false, substitutions, generatedTypes, samMethod);
            }
            if (upper.length > 0 && upper[0] != Object.class) {
                return toRef(upper[0], interfaceClass, callbackArgument, callbackReturn, substitutions,
                        generatedTypes, samMethod);
            }
            return ApiTypeRef.primitive("any");
        }
        if (type instanceof ParameterizedType parameterized) {
            if (!(parameterized.getRawType() instanceof Class<?> raw)) return ApiTypeRef.primitive("any");
            if (!generatedTypes.contains(raw.getName())) return ApiTypeRef.primitive("any");
            List<ApiTypeRef> arguments = Arrays.stream(parameterized.getActualTypeArguments())
                    .map(argument -> toRef(argument, interfaceClass, callbackArgument, callbackReturn,
                            substitutions, generatedTypes, samMethod))
                    .toList();
            return ApiTypeRef.symbol(new com.tkisor.nekojs.api.surface.ApiSymbolId("java", raw.getName()), arguments);
        }
        if (type instanceof GenericArrayType array) {
            return ApiTypeRef.array(toRef(array.getGenericComponentType(), interfaceClass,
                    callbackArgument, callbackReturn, substitutions, generatedTypes, samMethod));
        }
        if (type instanceof Class<?> cls && cls.isArray()) {
            return ApiTypeRef.array(toRef(cls.getComponentType(), interfaceClass, callbackArgument,
                    callbackReturn, substitutions, generatedTypes, samMethod));
        }
        ApiTypeRef ref = com.tkisor.nekojs.probe.ir.TypeReflector.toRef(type);
        if (ref.kind() == ApiTypeRef.Kind.SYMBOL && !generatedTypes.contains(ref.name().substring(
                ref.name().indexOf(':') + 1))) return ApiTypeRef.primitive("any");
        return ref;
    }

    private static void collectClasses(Type type, Set<String> imports) {
        if (type instanceof Class<?> cls) {
            if (cls.isArray()) {
                collectClasses(cls.getComponentType(), imports);
            } else if (!cls.isPrimitive() && cls != Object.class) {
                ApiTypeRef ref = com.tkisor.nekojs.probe.ir.TypeReflector.toRef(cls);
                if (ref.kind() == ApiTypeRef.Kind.SYMBOL) imports.add(cls.getName());
            }
        } else if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getRawType() instanceof Class<?> raw) {
                collectClasses(raw, imports);
            }
            for (Type argument : parameterized.getActualTypeArguments()) collectClasses(argument, imports);
        } else if (type instanceof GenericArrayType array) {
            collectClasses(array.getGenericComponentType(), imports);
        } else if (type instanceof WildcardType wildcard) {
            for (Type upper : wildcard.getUpperBounds()) collectClasses(upper, imports);
            for (Type lower : wildcard.getLowerBounds()) collectClasses(lower, imports);
        }
    }

    private static Class<?> load(String fqn) {
        try {
            return Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return null;
        }
    }

    private static String tsClassName(Class<?> cls) {
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return tsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }
}
