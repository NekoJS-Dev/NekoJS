package com.tkisor.nekojs.probe.types;

import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers TypeScript input aliases for values converted to Java.
 * A host container keeps its generic arguments; a converted input uses separately rendered element
 * types. Return values keep their host types and do not accept input conversions.
 */
public final class TypeAliasRegistry {
    private final Map<String, String> classAliases = new LinkedHashMap<>();
    private final Map<String, Integer> adapterTypeParameters = new LinkedHashMap<>();
    private final Map<String, CollectionAlias> collectionAliases = new LinkedHashMap<>();
    private final Map<String, FunctionalInterfaceAlias> functionalAliases = new LinkedHashMap<>();
    private java.util.function.BiFunction<String, String, String> references = (fqn, name) -> name;
    private boolean projectScalarValues = true;
    private Map<String, String> typeVariables = Map.of();
    /**
     * Lazy cache for enum input aliases: FQN to {@code $<SimpleName>_}. An empty string means the
     * class was already checked and is not an enum, or cannot be loaded.
     *
     * <p>An enum alias must be available before any parameter is rendered, like an adapter alias
     * registered once through {@code registerClassAlias}. The shared IR is, however, declared per
     * class in parallel, so registering aliases class by class would make whether a parameter gets
     * widened depend on task scheduling and change the output between runs. Instead the enum is
     * detected and cached by reflection at the query point (the input lookup in
     * {@link TypeConverter}). This is equivalent to calling
     * {@code registerClassAlias(fqn, "$" + SimpleName + "_")} for every enum FQN at startup, and it
     * does not depend on processing order.
     */
    private final Map<String, String> enumAliasCache = new ConcurrentHashMap<>();

    public TypeAliasRegistry() {
        registerDefaults();
    }

    /** Creates an independent rendering view; module-local names never mutate the shared alias registry. */
    public TypeAliasRegistry withReferences(java.util.function.BiFunction<String, String, String> references) {
        TypeAliasRegistry view = new TypeAliasRegistry();
        view.classAliases.clear();
        view.classAliases.putAll(classAliases);
        view.adapterTypeParameters.putAll(adapterTypeParameters);
        view.collectionAliases.clear();
        view.collectionAliases.putAll(collectionAliases);
        view.functionalAliases.putAll(functionalAliases);
        view.enumAliasCache.putAll(enumAliasCache);
        view.references = references;
        view.projectScalarValues = projectScalarValues;
        view.typeVariables = typeVariables;
        return view;
    }

    /** Instantiates owner variables for a derived declaration without rewriting shared Java metadata. */
    public TypeAliasRegistry withTypeVariables(Map<String, String> variables) {
        TypeAliasRegistry view = withReferences(references);
        view.typeVariables = Map.copyOf(variables);
        return view;
    }

    public String typeVariable(String name) {
        return typeVariables.getOrDefault(name, name);
    }

    /** Keeps explicit host references free of scalar alternatives while retaining names and input aliases. */
    public TypeAliasRegistry withRawValueReferences() {
        TypeAliasRegistry view = withReferences(references);
        view.projectScalarValues = false;
        return view;
    }

    public boolean projectsScalarValues() {
        return projectScalarValues;
    }

    /** Resolves one exported symbol by its Java identity within the current module. */
    public String reference(String fqn, String exportedName) {
        return references.apply(fqn, exportedName);
    }

    /** Canonical names emitted for supported builtin collection input aliases. */
    public static String collectionInputAlias(String fqn) {
        return switch (fqn) {
            case "java.util.List", "java.util.Collection", "java.util.Set", "java.lang.Iterable", "java.util.Map" ->
                    "$" + fqn.substring(fqn.lastIndexOf('.') + 1) + "_";
            default -> null;
        };
    }

    private void registerDefaults() {
        // String to string is handled in TypeConverter, so it is not repeated here

        // Collection type aliases
        collectionAliases.put("java.util.List", hostCollection("$List", true));
        collectionAliases.put("java.util.Collection", hostCollection("$Collection", true));
        collectionAliases.put("java.util.Set", hostCollection("$Set", false));
        collectionAliases.put("java.lang.Iterable", hostCollection("$Iterable", true));
        collectionAliases.put("java.util.Iterator", new CollectionAlias("$Iterator", ""));

        // Host maps accept arbitrary keys; object inputs are limited to TypeScript property keys.
        collectionAliases.put("java.util.Map", new CollectionAlias("$Map", "") {
            @Override
            public String getInputType(String[] hostArgs, String[] inputArgs, String hostName) {
                if (hostArgs.length == 2 && inputArgs.length == 2) {
                    String host = hostName + "<" + hostArgs[0] + ", " + hostArgs[1] + ">";
                    if (java.util.Set.of("string", "number", "symbol").contains(inputArgs[0])) {
                        return "(" + host + " | Partial<Record<" + inputArgs[0] + ", " + inputArgs[1] + ">>)";
                    }
                    // Keep the record outside the conditional: narrowing can erase its index signature.
                    return "(" + host + " | (([" + inputArgs[0] + "] extends [PropertyKey] ? unknown : never)"
                            + " & Partial<Record<Extract<" + inputArgs[0] + ", PropertyKey>, " + inputArgs[1] + ">>))";
                }
                return "{ [key: string]: any }";
            }
        });

        // Common type aliases
        classAliases.put("java.lang.String", "string");
        classAliases.put("java.lang.Boolean", "boolean");
        classAliases.put("java.lang.Byte", "number");
        classAliases.put("java.lang.Short", "number");
        classAliases.put("java.lang.Integer", "number");
        classAliases.put("java.lang.Long", "number");
        classAliases.put("java.lang.Float", "number");
        classAliases.put("java.lang.Double", "number");
        classAliases.put("java.lang.Character", "string");
        classAliases.put("java.lang.Object", "unknown");
        // UUID, Path and File retain their host identity unless a real adapter supplies an input alias.
    }

    private static CollectionAlias hostCollection(String name, boolean acceptsArray) {
        return new CollectionAlias(name, "[]") {
            @Override
            public String getInputType(String[] hostArgs, String[] inputArgs, String hostName) {
                String host = hostName + "<" + (hostArgs.length == 0 ? "any" : hostArgs[0]) + ">";
                return getInputTypeForHost(host, inputArgs);
            }

            @Override
            public String getInputTypeForHost(String host, String[] inputArgs) {
                String input = inputArgs.length == 0 ? "any" : inputArgs[0];
                return acceptsArray ? "(" + host + " | " + TypeScriptTypeExpressions.arrayOf(input) + ")" : host;
            }
        };
    }

    /**
     * Clears every registration and restores the default alias table. The backend calls it before
     * each probe run so adapter aliases registered in the previous run do not leak into this one:
     * a leftover alias for a class that is no longer generated would still widen parameter types.
     */
    public void clear() {
        typeVariables = Map.of();
        classAliases.clear();
        adapterTypeParameters.clear();
        collectionAliases.clear();
        functionalAliases.clear();
        enumAliasCache.clear();
        registerDefaults();
    }

    /**
     * Checks whether a class has a class-level input alias.
     *
     * <p>Besides explicit registration ({@link #registerClassAlias}), every enum type has the input
     * alias {@code $<SimpleName>_} (a union of the enum and string literals, declared next to the
     * enum by IndexFileGenerator).
     */
    public boolean hasAlias(String className) {
        if (classAliases.containsKey(className)) return true;
        return enumInputAlias(className) != null;
    }

    /**
     * Returns the class-level input alias.
     *
     * <p>An explicitly registered alias wins. For an enum type it falls back to
     * {@code $<SimpleName>_}; a nested class uses {@code $Parent$Child_}, matching the TS name the
     * generators produce.
     */
    public String getAlias(String className) {
        String explicit = classAliases.get(className);
        String alias = explicit != null ? explicit : enumInputAlias(className);
        return alias == null ? null : reference(className, alias);
    }

    /**
     * Resolves an enum input alias lazily: an enum FQN gives {@code $<TSName>_}, a non-enum or a
     * class that cannot be loaded gives null. Results, including misses, are cached and safe to
     * read from multiple threads.
     */
    private String enumInputAlias(String className) {
        if (className == null) return null;
        String cached = enumAliasCache.get(className);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String alias = null;
        try {
            Class<?> cls = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            if (cls.isEnum()) {
                alias = "$" + tsClassName(cls) + "_";
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Not loadable, including a NoClassDefFoundError: behave as if no alias were registered, so the
            // type is not widened and renders as $Foo
        }
        enumAliasCache.put(className, alias == null ? "" : alias);
        return alias;
    }

    /** TypeScript identifier of a class; a nested class uses the Parent$Child form that the generators produce. */
    private static String tsClassName(Class<?> cls) {
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return tsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }

    /**
     * Returns the input alias for a collection type.
     *
     * @param rawClass the raw class of the parameterized type
     * @param tsArgs   the TS string of each type argument, rendered <b>independently</b>: a structured
     *                 array built by converting each entry of
     *                 {@code ParameterizedType.getActualTypeArguments()}. Do not pass a joined
     *                 string and split it on ", ": an argument can itself contain ", " (a nested
     *                 generic with several arguments), and splitting would cut the nested type.
     * @return the input type string, or null when the class is not a collection type
     */
    public String getCollectionAlias(Class<?> rawClass, String[] tsArgs) {
        return getCollectionAlias(rawClass.getName(), tsArgs);
    }

    /**
     * Collection input alias lookup by FQN, used on the ref rendering path because a ref carries
     * only the symbolic FQN and no Class instance. Behaves exactly like
     * {@link #getCollectionAlias(Class, String[])}.
     */
    public String getCollectionAlias(String rawFqn, String[] tsArgs) {
        return getCollectionAlias(rawFqn, tsArgs, tsArgs);
    }

    /** Reuses an emitted alias only when its single set of arguments describes both host and guest inputs. */
    public String renderCollectionInput(String rawFqn, String[] hostArgs, String[] inputArgs) {
        String name = collectionInputAlias(rawFqn);
        int arity = rawFqn.equals("java.util.Map") ? 2 : 1;
        if (name != null && !rawFqn.equals("java.util.Set") && hostArgs.length == arity
                && java.util.Arrays.equals(hostArgs, inputArgs)) {
            return reference(rawFqn, name) + "<" + String.join(", ", inputArgs) + ">";
        }
        return getCollectionAlias(rawFqn, hostArgs, inputArgs);
    }

    /** Renders a narrowed host carrier through the same guest conversion shape, without expanding named aliases. */
    public String renderCollectionInputWithHost(String rawFqn, String hostType, String[] inputArgs) {
        CollectionAlias alias = collectionAliases.get(rawFqn);
        return alias == null ? null : alias.getInputTypeForHost(hostType, inputArgs);
    }

    /** Host generic arguments retain identity while converted array/object inputs use their input types. */
    public String getCollectionAlias(String rawFqn, String[] hostArgs, String[] inputArgs) {
        CollectionAlias alias = collectionAliases.get(rawFqn);
        if (alias == null) return null;
        return alias.getInputType(hostArgs, inputArgs, reference(rawFqn, alias.baseType));
    }

    /**
     * Registers a custom class alias.
     */
    public void registerClassAlias(String className, String tsType) {
        classAliases.put(className, tsType);
        adapterTypeParameters.remove(className);
    }

    /** Registers an adapter's actual generic arity; ordinary aliases do not consume host arguments. */
    public void registerAdapterAlias(String className, String tsType, int parameters) {
        classAliases.put(className, tsType);
        adapterTypeParameters.put(className, parameters);
    }

    /** Returns an explicitly registered class alias without resolving the lazy enum aliases. */
    public String getRegisteredAlias(String className) {
        String alias = classAliases.get(className);
        return alias == null ? null : reference(className, alias);
    }

    /** Explicit host arguments retain their identity; an empty list selects the alias's raw Java defaults. */
    public String renderRegisteredAlias(String className, List<String> hostArguments) {
        String alias = getRegisteredAlias(className);
        if (alias == null || adapterTypeParameters.getOrDefault(className, 0) == 0 || hostArguments.isEmpty()) {
            return alias;
        }
        return alias + "<" + String.join(", ", hostArguments) + ">";
    }

    public FunctionalInterfaceAlias getFunctionalInterfaceAlias(String className) {
        return functionalAliases.get(className);
    }

    public void clearFunctionalInterfaceAliases() {
        functionalAliases.clear();
    }

    public void registerFunctionalInterfaceAlias(String className, FunctionalInterfaceAlias alias) {
        if (!classAliases.containsKey(className)) {
            functionalAliases.put(className, alias);
        }
    }

    /**
     * Registers a custom collection alias.
     */
    public void registerCollectionAlias(String className, String baseType, String suffix) {
        collectionAliases.put(className, new CollectionAlias(baseType, suffix));
    }

    public static class CollectionAlias {
        private final String baseType;
        private final String suffix;

        public CollectionAlias(String baseType, String suffix) {
            this.baseType = baseType;
            this.suffix = suffix;
        }

        public String getInputType(String[] typeArgs) {
            if (typeArgs.length == 0) return "any" + suffix;
            return typeArgs[0] + suffix;
        }

        /** Retains custom conversion implementations while allowing host names to be scoped. */
        public String getInputType(String[] typeArgs, String hostName) {
            return getInputType(typeArgs);
        }

        /** Null means this alias has no structural host-carrier projection. */
        public String getInputTypeForHost(String hostType, String[] inputArgs) {
            return null;
        }

        /** Existing custom aliases keep their conversion implementation unless they opt into host arguments. */
        public String getInputType(String[] hostArgs, String[] inputArgs, String hostName) {
            return getInputType(inputArgs, hostName);
        }
    }

    /** A SAM signature expressed with the declaring interface's type variables. */
    public record FunctionalInterfaceAlias(String aliasName, Class<?> interfaceClass,
                                           FunctionalInterfaceResolver.Signature signature,
                                           java.util.Set<String> generatedTypes) {
        public FunctionalInterfaceAlias {
            generatedTypes = java.util.Set.copyOf(generatedTypes);
        }
    }
}
