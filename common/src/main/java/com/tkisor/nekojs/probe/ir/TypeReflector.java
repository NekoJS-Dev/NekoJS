package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.annotation.ScriptInput;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.api.annotation.ScriptOutput;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;
import com.tkisor.nekojs.core.reflect.JavaGenericTypes;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reflects script-visible Java members into {@link TypeDecl}, retaining remapping and Bean accessor pairing.
 * Class and interface declarations retain callable inherited overloads with contextual generic types.
 *
 * <p>Each {@link TypeSlot} retains Java signature metadata for contextual input aliases and a language-neutral
 * {@link ApiTypeRef} for rendering and type edits.
 */
public final class TypeReflector {

    public TypeDecl reflect(Class<?> cls) {
        TypeDecl.Kind kind = cls.isEnum() ? TypeDecl.Kind.ENUM
                : (cls.isInterface() ? TypeDecl.Kind.INTERFACE : TypeDecl.Kind.CLASS);
        TypeDecl decl = new TypeDecl(kind, cls, cls.getName());
        // Class-level @Doc becomes JSDoc; without the annotation docs stays empty and rendering emits nothing
        decl.docs.addAll(AnnotatedDocs.typeDocs(cls));

        decl.typeParams.addAll(typeParametersOf(cls));

        // A missing public signature poisons the JVM's entire method enumeration. Retain only
        // independently reflected members; heritage could otherwise restore unknown hidden overrides.
        MethodScan scan = scanDeclarationMethods(cls);
        if (scan.omission() != null) {
            decl.memberOmissions.add(scan.omission());
            reflectIndependentMembers(cls, decl);
            decl.constructors.sort(Comparator.comparing(TypeReflector::constructorKey));
            decl.fields.sort(Comparator.comparing(field -> field.name));
            return decl;
        }

        // Enums also retain Enum<Self>; each backend chooses how to expose the instance heritage.
        if (kind != TypeDecl.Kind.INTERFACE) {
            Class<?> sc = cls.getSuperclass();
            if (sc != null && sc != Object.class) {
                Type genericSuperclass = cls.getGenericSuperclass();
                decl.superType = TypeSlot.of(genericSuperclass, toRef(genericSuperclass));
            }
        }

        // A wholly hidden implementation contract must not restore its methods through heritage.
        for (Type iface : cls.getGenericInterfaces()) {
            if (!hidesAllInterfaceMethods(cls, iface)) {
                decl.interfaces.add(TypeSlot.of(iface, toRef(iface)));
            }
        }

        switch (kind) {
            case CLASS -> reflectClassMembers(cls, decl, scan.methods());
            case INTERFACE -> reflectInterfaceMembers(cls, decl, scan.methods());
            case ENUM -> reflectEnumMembers(cls, decl, scan.methods());
        }

        // Deterministic order: the JVM does not guarantee the order of getDeclaredMethods or
        // getDeclaredFields, so member order drifts between processes. Sort by name (plus parameter
        // and return types) to keep probe output reproducible. Renderers split getters, static and
        // instance members by flags, so list order does not matter to them.
        decl.constructors.sort(Comparator.comparing(TypeReflector::constructorKey));
        decl.fields.sort(Comparator.comparing(f -> f.name));
        decl.methods.sort(Comparator.comparing(TypeReflector::methodKey));
        return decl;
    }

    /** Retains marker and partially visible interfaces; only concrete, wholly hidden contracts disappear. */
    private static boolean hidesAllInterfaceMethods(Class<?> owner, Type interfaceType) {
        Class<?> contract = FunctionalInterfaceResolver.rawClass(interfaceType);
        if (contract == null) return false;
        boolean hasInstanceMethod = false;
        for (Method method : contract.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            hasInstanceMethod = true;
            try {
                Method resolved = owner.getMethod(method.getName(), method.getParameterTypes());
                if (Modifier.isAbstract(resolved.getModifiers()) || jsName(resolved) != null) return false;
            } catch (NoSuchMethodException unresolved) {
                return false;
            }
        }
        return hasInstanceMethod;
    }

    private static String constructorKey(MethodDecl c) {
        return paramsKey(c);
    }

    private static String methodKey(MethodDecl m) {
        return m.name + "|" + paramsKey(m) + "→" + typeKey(m.returnType);
    }

    private static String paramsKey(MethodDecl m) {
        StringBuilder sb = new StringBuilder();
        for (MethodDecl.MethodParam p : m.params) {
            sb.append('|').append(typeKey(p.type));
            // varargs and optional are part of the sort key. A varargs parameter is flattened to its
            // component type in the IR, so without these flags of(int) and of(int...) share a key and
            // the stable sort keeps reflection order, which differs between JVMs.
            if (p.varargs) sb.append("[]");
            if (p.optional) sb.append("?");
        }
        return sb.toString();
    }

    private static String typeKey(TypeSlot slot) {
        if (slot == null || slot.sourceType == null) return "";
        return slot.sourceType.getTypeName();
    }

    private void reflectClassMembers(Class<?> cls, TypeDecl decl, List<Method> methods) {
        reflectIndependentMembers(cls, decl);
        reflectMethodsLikeClassDecl(cls, decl, methods);
    }

    private void reflectIndependentMembers(Class<?> cls, TypeDecl decl) {
        // Constructors
        for (var ctor : declarationConstructors(cls)) {
            decl.constructors.add(reflectConstructor(ctor));
        }
        // Fields
        for (var field : cls.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers())) continue;
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            FieldDecl f = new FieldDecl(field.getName(), TypeSlot.of(field.getGenericType(), toRef(field.getGenericType())));
            f.sourceField = field;
            f.isStatic = isStatic;
            f.isFinal = Modifier.isFinal(field.getModifiers());
            f.docs.addAll(AnnotatedDocs.fieldDocs(field));
            decl.fields.add(f);
        }

    }

    private void reflectInterfaceMembers(Class<?> cls, TypeDecl decl, List<Method> methods) {
        reflectMethodsLikeClassDecl(cls, decl, methods);
        for (var field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())) {
                FieldDecl f = new FieldDecl(field.getName(), TypeSlot.of(field.getGenericType(), toRef(field.getGenericType())));
                f.sourceField = field;
                f.isStatic = true;
                f.isFinal = true;
                f.docs.addAll(AnnotatedDocs.fieldDocs(field));
                decl.fields.add(f);
            }
        }
    }

    private void reflectEnumMembers(Class<?> cls, TypeDecl decl, List<Method> methods) {
        for (var field : cls.getDeclaredFields()) {
            if (field.isEnumConstant()) {
                FieldDecl f = new FieldDecl(field.getName(), TypeSlot.of(cls, toRef(cls)));
                f.sourceField = field;
                f.isStatic = true;
                f.isEnumConstant = true;
                f.docs.addAll(AnnotatedDocs.fieldDocs(field));
                decl.fields.add(f);
            } else if (Modifier.isPublic(field.getModifiers())) {
                // Non-constant public fields: renderEnum only emits isEnumConstant, so these fields exist
                // only so import collection also covers enum public field types.
                FieldDecl f = new FieldDecl(field.getName(), TypeSlot.of(field.getGenericType(), toRef(field.getGenericType())));
                f.sourceField = field;
                f.isStatic = Modifier.isStatic(field.getModifiers());
                f.isFinal = Modifier.isFinal(field.getModifiers());
                decl.fields.add(f);
            }
        }
        // Public enum methods: renderEnum does not emit them because the skeleton is fixed, so they are
        // kept only for import collection.
        reflectMethodsLikeClassDecl(cls, decl, methods);
    }

    /**
     * Reflects declared members and callable inherited interface methods with contextual generic types.
     * Declared accessors keep precedence; candidates are sorted before Bean pairing, and bridges are omitted.
     * Inherited overloads remain separate from a class's same-name methods instead of being hidden by them.
     */
    private void reflectMethodsLikeClassDecl(Class<?> cls, TypeDecl decl, List<Method> declared) {
        Set<String> processedProperties = new HashSet<>();
        for (var method : declared) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            // Injected accessors can be synthetic public implementations of real interface methods.
            // Keep their original Method for visibility/remapping; unrelated synthetic methods and bridges stay hidden.
            if (method.isBridge() || method.isSynthetic() && !implementsInterfaceMethod(cls, method)) continue;
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            // JS-side method name from @Remap or @RemapByPrefix; @HideFromJS makes it null and the method is skipped
            String jsName = jsName(method);
            if (jsName == null) continue;

            // Select one Bean alias per exposed property, retaining the other real accessor methods.
            if (!isStatic && isGetterName(jsName) && method.getParameterCount() == 0) {
                String propName = getPropertyName(jsName);
                if (propName != null && processedProperties.add(propName)) {
                    MethodDecl getter = reflectClassMethod(cls, method);
                    getter.isGetter = true;
                    getter.property = propName;
                    getter.setterParamType = findSetterParamSlot(cls, propName, declared);
                    decl.methods.add(getter);
                } else {
                    decl.methods.add(reflectClassMethod(cls, method));
                }
                continue;
            }

            MethodDecl m = reflectClassMethod(cls, method);
            // Mark Bean setters while retaining their real callable method signatures.
            if (!isStatic && isSetterName(jsName) && method.getParameterCount() == 1) {
                m.isSetter = true;
            }
            decl.methods.add(m);
        }
    }

    private static boolean implementsInterfaceMethod(Class<?> cls, Method method) {
        if (cls.isInterface() || cls.isEnum() || method.getDeclaringClass() != cls
                || Modifier.isStatic(method.getModifiers())) return false;
        List<Method> contracts = new ArrayList<>();
        collectInterfaceMethods(cls, new HashSet<>(), contracts);
        return contracts.stream().anyMatch(candidate -> Modifier.isPublic(candidate.getModifiers())
                && !Modifier.isStatic(candidate.getModifiers()) && !candidate.isSynthetic() && !candidate.isBridge()
                && candidate.getName().equals(method.getName()) && candidate.getReturnType() == method.getReturnType()
                && Arrays.equals(candidate.getParameterTypes(), method.getParameterTypes()));
    }

    /** Public constructors shared by declaration reflection and signature dependency traversal. */
    public static List<java.lang.reflect.Constructor<?>> declarationConstructors(Class<?> cls) {
        return Arrays.stream(cls.getConstructors())
                .filter(constructor -> !constructor.isSynthetic()
                        && !constructor.isAnnotationPresent(ScriptInternalConstructor.class))
                .toList();
    }

    /** Enumerates native methods; a missing signature hides the owner's whole method set and records why. */
    public record MethodScan(List<Method> methods, String omission) {}

    public static MethodScan scanDeclarationMethods(Class<?> cls) {
        try {
            return new MethodScan(declarationMethods(cls), null);
        } catch (NoClassDefFoundError unavailable) {
            // Enum renderers publish a fixed method skeleton, so partial enum recovery would overpromise.
            if (cls.isEnum()) throw unavailable;
            return new MethodScan(List.of(), "Public method set and method-bearing heritage unavailable: " + unavailable);
        }
    }

    /** Returns public candidates without linking private implementation signatures. */
    public static List<Method> declarationMethods(Class<?> cls) {
        // Public-only VM enumeration filters private signatures before resolving their optional types.
        Method[] visible = cls.getMethods();
        List<Method> methods = new ArrayList<>(Arrays.stream(visible)
                .filter(method -> method.getDeclaringClass() == cls).toList());
        methods.sort(TypeReflector::compareCandidates);
        if (cls.isInterface()) {
            // A child interface's own overload hides the parent's overload set in TypeScript.
            // Re-emit actual inherited instance overloads with contextual types.
            Set<String> names = methods.stream()
                    .filter(method -> Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()))
                    .map(TypeReflector::jsName).filter(name -> name != null).collect(java.util.stream.Collectors.toSet());
            // TypeScript also requires an explicit member when multiple parents inherit different contracts.
            Set<String> inheritedNames = new HashSet<>();
            for (Class<?> parent : cls.getInterfaces()) {
                Set<String> parentNames = Arrays.stream(parent.getMethods())
                        .filter(method -> !Modifier.isStatic(method.getModifiers()))
                        .map(TypeReflector::jsName).filter(name -> name != null)
                        .collect(java.util.stream.Collectors.toSet());
                for (String name : parentNames) {
                    if (!inheritedNames.add(name)) names.add(name);
                }
            }
            List<Method> inherited = Arrays.stream(cls.getMethods())
                    .filter(method -> method.getDeclaringClass() != cls && !Modifier.isStatic(method.getModifiers())
                            && !method.isSynthetic() && !method.isBridge() && names.contains(jsName(method)))
                    .sorted(TypeReflector::compareCandidates).toList();
            methods.addAll(covariantInterfaceMethods(cls, inherited));
        } else if (!cls.isEnum()) {
            // TypeScript implements clauses do not inherit Java's default interface methods or overloads.
            // Keep declared accessor precedence; getMethods selects actual overrides and excludes private members.
            Set<String> names = new HashSet<>();
            // Object methods are required only when an implemented interface explicitly redeclares them.
            Set<String> interfaceNames = new HashSet<>();
            for (Class<?> ancestor = cls; ancestor != null; ancestor = ancestor.getSuperclass()) {
                for (Class<?> iface : ancestor.getInterfaces()) {
                    List<String> required = Arrays.stream(iface.getMethods())
                            .filter(method -> !Modifier.isStatic(method.getModifiers()))
                            .map(TypeReflector::jsName).filter(name -> name != null).toList();
                    interfaceNames.addAll(required);
                    if (ancestor == cls) names.addAll(required);
                }
            }
            // Ordinary ancestor implementations remain inherited from the parent declaration.
            // Object operations need explicit declarations when an inherited interface requires them.
            Arrays.stream(Object.class.getMethods()).map(TypeReflector::jsName)
                    .filter(name -> name != null && interfaceNames.contains(name)).forEach(names::add);
            Set<String> staticNames = new HashSet<>();
            Set<String> fieldNames = Arrays.stream(cls.getDeclaredFields())
                    .filter(field -> Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()))
                    .map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet());
            methods.stream().filter(method -> Modifier.isPublic(method.getModifiers())
                            && Modifier.isStatic(method.getModifiers()))
                    .map(TypeReflector::jsName).filter(name -> name != null).forEach(staticNames::add);
            methods.stream().filter(method -> Modifier.isPublic(method.getModifiers())
                            && !Modifier.isStatic(method.getModifiers()))
                    .map(TypeReflector::jsName).filter(name -> name != null).forEach(names::add);
            Arrays.stream(visible).filter(method -> method.getDeclaringClass().isInterface()
                            && !Modifier.isStatic(method.getModifiers()))
                    .map(TypeReflector::jsName).filter(name -> name != null).forEach(names::add);
            // An inherited Bean getter can own reads of a public subclass field with a different type.
            Arrays.stream(visible).filter(method -> !Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 0)
                    .map(TypeReflector::jsName).filter(name -> name != null && isGetterName(name)
                            && fieldNames.contains(getPropertyName(name))).forEach(names::add);
            // Real methods own names that would otherwise become Bean aliases. A property can select
            // another getter, so close this finite name set before pairing writes, independent of reflection order.
            int previousNames;
            do {
                previousNames = names.size();
                for (Method method : visible) {
                    String name = jsName(method);
                    if (!Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0
                            && name != null && names.contains(name)) {
                        String property = getPropertyName(name);
                        if (property != null) {
                            names.add(property);
                            names.add(beanSetterName(property));
                        }
                    }
                }
            } while (names.size() != previousNames);
            // Declaring one overload on a TypeScript subclass hides inherited same-name overloads.
            // Include the actual superclass overloads for each emitted instance method name as well.
            List<Method> inherited = Arrays.stream(visible)
                    .filter(method -> method.getDeclaringClass() != cls
                            && (method.getDeclaringClass() != Object.class || interfaceNames.contains(jsName(method)))
                            && (Modifier.isStatic(method.getModifiers())
                                ? !method.getDeclaringClass().isInterface() && staticNames.contains(jsName(method))
                                : names.contains(jsName(method))))
                    .sorted(TypeReflector::compareCandidates)
                    .toList();
            methods.addAll(inherited);
        }
        return methods;
    }

    private record InterfaceMethodKey(String name, List<Type> parameters) {}

    /** Keeps actual overloads while selecting the most specific inherited covariant result. */
    private static List<Method> covariantInterfaceMethods(Class<?> cls, List<Method> candidates) {
        var groups = new java.util.LinkedHashMap<InterfaceMethodKey, List<Method>>();
        for (Method candidate : candidates) {
            var signature = inheritedSignature(cls, candidate);
            // Retain Java identities: distinct remapped methods and method variables with different
            // bounds are overloads, even when their exposed names or rendered parameter names coincide.
            var key = new InterfaceMethodKey(candidate.getName(), signature.parameterTypes());
            List<Method> group = groups.computeIfAbsent(key, ignored -> new ArrayList<>());
            Class<?> result = FunctionalInterfaceResolver.rawClass(signature.returnType());
            boolean covered = false;
            for (var iterator = group.iterator(); iterator.hasNext();) {
                Method previous = iterator.next();
                Type previousType = inheritedSignature(cls, previous).returnType();
                Class<?> previousResult = FunctionalInterfaceResolver.rawClass(previousType);
                if (previousType.equals(signature.returnType())
                        || result != null && previousResult != null && result != previousResult
                        && result.isAssignableFrom(previousResult)) {
                    covered = true;
                    break;
                }
                if (result != null && previousResult != null && result != previousResult
                        && previousResult.isAssignableFrom(result)) iterator.remove();
            }
            if (!covered) group.add(candidate);
        }
        return groups.values().stream().flatMap(List::stream).toList();
    }

    private MethodDecl reflectClassMethod(Class<?> cls, Method method) {
        MethodDecl declaration = reflectMethod(method);
        if (declaration.hidden) return declaration;
        if (method.getDeclaringClass() != cls) {
            applySignature(declaration, inheritedSignature(cls, method));
        }
        FunctionalInterfaceResolver.Signature contract = interfaceMethodContract(cls, method);
        if (contract != null) {
            applySignature(declaration, contract);
            return declaration;
        }
        Type result = interfaceReturnContract(cls, method, declaration.returnType.sourceType);
        declaration.returnType = returnSlot(result, declaration.returnType);
        return declaration;
    }

    private static void applySignature(MethodDecl declaration, FunctionalInterfaceResolver.Signature signature) {
        reflectTypeParameters(declaration, signature.typeParameters());
        declaration.returnType = returnSlot(signature.returnType(), declaration.returnType);
        for (int index = 0; index < declaration.params.size(); index++) {
            Type type = signature.parameterTypes().get(index);
            if (declaration.params.get(index).varargs) {
                type = type instanceof GenericArrayType array ? array.getGenericComponentType()
                        : ((Class<?>) type).getComponentType();
            }
            TypeSlot slot = TypeSlot.of(type, toRef(type));
            slot.inputShape = declaration.params.get(index).type.inputShape;
            declaration.params.get(index).type = slot;
        }
    }

    private static TypeSlot outputSlot(Type type) {
        return TypeSlot.of(type, outputRef(type));
    }

    /** Projects direct returned objects only; container arguments and mutable arrays retain native acceptance. */
    public static ApiTypeRef outputRef(Type type) {
        if (type instanceof Class<?> cls && !cls.isArray()) {
            return toRef(com.tkisor.nekojs.api.ScriptTypeViews.outputClass(cls));
        }
        return toRef(type);
    }

    private static TypeSlot returnSlot(Type type, TypeSlot previous) {
        TypeSlot slot = outputSlot(type);
        slot.outputTypeScript = previous.outputTypeScript;
        return slot;
    }

    /** Restores an erased implementation only when every matching interface agrees on the full generic contract. */
    private static FunctionalInterfaceResolver.Signature interfaceMethodContract(Class<?> cls, Method method) {
        if (cls.isInterface() || Modifier.isStatic(method.getModifiers()) || method.getTypeParameters().length != 0
                || !(method.getGenericReturnType() instanceof Class<?>)
                || !Arrays.equals(method.getGenericParameterTypes(), method.getParameterTypes())) return null;
        List<Method> contracts = new ArrayList<>();
        collectInterfaceMethods(cls, new HashSet<>(), contracts);
        FunctionalInterfaceResolver.Signature agreed = null;
        for (Method candidate : contracts) {
            if (Modifier.isStatic(candidate.getModifiers()) || !Modifier.isPublic(candidate.getModifiers())
                    || candidate.isBridge() || candidate.isSynthetic() || !candidate.getName().equals(method.getName())
                    || !Arrays.equals(candidate.getParameterTypes(), method.getParameterTypes())
                    || candidate.getReturnType() != method.getReturnType()) continue;
            FunctionalInterfaceResolver.Signature signature = FunctionalInterfaceResolver.resolve(cls, candidate);
            if (signature == null || signature.typeParameters().isEmpty() || jsName(candidate) == null) return null;
            Set<TypeVariable<?>> variables = new HashSet<>(JavaGenericTypes.parameters(cls));
            variables.addAll(signature.typeParameters());
            if (!contractInScope(signature.returnType(), variables)
                    || signature.parameterTypes().stream().anyMatch(type -> !contractInScope(type, variables))
                    || signature.typeParameters().stream().flatMap(variable -> Arrays.stream(variable.getBounds()))
                    .anyMatch(type -> !contractInScope(type, variables))) return null;
            if (agreed != null && !sameMethodContract(agreed, signature)) return null;
            agreed = signature;
        }
        return agreed;
    }

    private static boolean sameMethodContract(FunctionalInterfaceResolver.Signature left,
                                              FunctionalInterfaceResolver.Signature right) {
        if (left.typeParameters().size() != right.typeParameters().size()) return false;
        Map<TypeVariable<?>, Type> substitutions = new HashMap<>();
        for (int index = 0; index < left.typeParameters().size(); index++) {
            substitutions.put(right.typeParameters().get(index), left.typeParameters().get(index));
        }
        for (int index = 0; index < left.typeParameters().size(); index++) {
            Type[] bounds = Arrays.stream(right.typeParameters().get(index).getBounds())
                    .map(type -> FunctionalInterfaceResolver.substitute(type, substitutions)).toArray(Type[]::new);
            if (!sameContracts(left.typeParameters().get(index).getBounds(), bounds)) return false;
        }
        return sameContract(left.returnType(), FunctionalInterfaceResolver.substitute(right.returnType(), substitutions))
                && sameContracts(left.parameterTypes().toArray(Type[]::new), right.parameterTypes().stream()
                .map(type -> FunctionalInterfaceResolver.substitute(type, substitutions)).toArray(Type[]::new));
    }

    // Bytecode injection can erase an implementation's Signature attribute while retaining its typed interface.
    // Recover only an agreed, in-scope contract with the same erasure; ambiguous or method-generic contracts stay raw.
    private static Type interfaceReturnContract(Class<?> cls, Method method, Type original) {
        if (cls.isInterface() || Modifier.isStatic(method.getModifiers()) || method.getTypeParameters().length != 0
                || !(original instanceof Class<?> raw) || raw.getTypeParameters().length == 0) return original;
        List<Method> contracts = new ArrayList<>();
        collectInterfaceMethods(cls, new HashSet<>(), contracts);
        Type agreed = null;
        for (Method candidate : contracts) {
            if (Modifier.isStatic(candidate.getModifiers()) || !Modifier.isPublic(candidate.getModifiers())
                    || candidate.isBridge() || candidate.isSynthetic() || !candidate.getName().equals(method.getName())
                    || !Arrays.equals(candidate.getParameterTypes(), method.getParameterTypes())) continue;
            if (candidate.getReturnType() != raw) continue;
            FunctionalInterfaceResolver.Signature signature = FunctionalInterfaceResolver.resolve(cls, candidate);
            if (signature == null || !signature.typeParameters().isEmpty()
                    || !(signature.returnType() instanceof ParameterizedType result)
                    || result.getRawType() != raw || !contractInScope(result, cls)) return original;
            if (agreed != null && !sameContract(agreed, result)) return original;
            agreed = result;
        }
        return agreed == null ? original : agreed;
    }

    private static void collectInterfaceMethods(Class<?> cls, Set<Class<?>> visited, List<Method> methods) {
        if (cls == null || !visited.add(cls)) return;
        if (cls.isInterface()) {
            Arrays.stream(cls.getMethods()).filter(method -> method.getDeclaringClass() == cls).forEach(methods::add);
        }
        for (Class<?> parent : cls.getInterfaces()) collectInterfaceMethods(parent, visited, methods);
        collectInterfaceMethods(cls.getSuperclass(), visited, methods);
    }

    private static boolean contractInScope(Type type, Class<?> cls) {
        return contractInScope(type, new HashSet<>(JavaGenericTypes.parameters(cls)));
    }

    private static boolean contractInScope(Type type, Set<TypeVariable<?>> variables) {
        if (type instanceof TypeVariable<?> variable) return variables.contains(variable);
        if (type instanceof ParameterizedType parameterized) {
            return (parameterized.getOwnerType() == null || contractInScope(parameterized.getOwnerType(), variables))
                    && Arrays.stream(parameterized.getActualTypeArguments()).allMatch(arg -> contractInScope(arg, variables));
        }
        if (type instanceof GenericArrayType array) return contractInScope(array.getGenericComponentType(), variables);
        if (type instanceof WildcardType wildcard) {
            return Arrays.stream(wildcard.getUpperBounds()).allMatch(bound -> contractInScope(bound, variables))
                    && Arrays.stream(wildcard.getLowerBounds()).allMatch(bound -> contractInScope(bound, variables));
        }
        return type instanceof Class<?>;
    }

    private static boolean sameContract(Type left, Type right) {
        if (left == null || right == null) return left == right;
        if (left instanceof ParameterizedType a && right instanceof ParameterizedType b) {
            return a.getRawType().equals(b.getRawType()) && sameContract(a.getOwnerType(), b.getOwnerType())
                    && sameContracts(a.getActualTypeArguments(), b.getActualTypeArguments());
        }
        if (left instanceof GenericArrayType a && right instanceof GenericArrayType b) {
            return sameContract(a.getGenericComponentType(), b.getGenericComponentType());
        }
        if (left instanceof WildcardType a && right instanceof WildcardType b) {
            return sameContracts(a.getUpperBounds(), b.getUpperBounds()) && sameContracts(a.getLowerBounds(), b.getLowerBounds());
        }
        return left.equals(right);
    }

    private static boolean sameContracts(Type[] left, Type[] right) {
        if (left.length != right.length) return false;
        for (int index = 0; index < left.length; index++) {
            if (!sameContract(left[index], right[index])) return false;
        }
        return true;
    }

    private static FunctionalInterfaceResolver.Signature inheritedSignature(Class<?> cls, Method method) {
        FunctionalInterfaceResolver.Signature signature = FunctionalInterfaceResolver.resolve(cls, method);
        if (signature == null) {
            throw new IllegalStateException("[NEKO-4029] Inherited method signature is unreachable: "
                    + cls.getName() + " -> " + method.toGenericString());
        }
        return signature;
    }

    /**
     * Deterministic order for getter/setter candidates. Declared methods that are not synthetic or
     * bridge come first, so a covariant override wins over its bridge. Within the same group,
     * methods are ordered by name, parameter types and generic return type. The result does not
     * depend on the order the JVM returns methods in.
     */
    private static int compareCandidates(Method a, Method b) {
        boolean syntheticA = a.isSynthetic() || a.isBridge();
        boolean syntheticB = b.isSynthetic() || b.isBridge();
        if (syntheticA != syntheticB) return syntheticA ? 1 : -1;
        return candidateKey(a).compareTo(candidateKey(b));
    }

    private static String candidateKey(Method m) {
        StringBuilder sb = new StringBuilder(m.getName());
        for (Type p : m.getGenericParameterTypes()) sb.append('|').append(p.getTypeName());
        sb.append("→").append(m.getGenericReturnType().getTypeName());
        return sb.toString();
    }

    private MethodDecl reflectConstructor(java.lang.reflect.Constructor<?> ctor) {
        MethodDecl m = new MethodDecl(ctor.getName());
        m.isConstructor = true;
        reflectParamsInto(m, ctor);
        m.docs.addAll(AnnotatedDocs.executableDocs(ctor));
        m.overloads.addAll(AnnotatedDocs.overloads(ctor));
        return m;
    }

    private MethodDecl reflectMethod(java.lang.reflect.Method method) {
        MethodDecl m = new MethodDecl(method.getName());
        m.sourceMethod = method;
        // JS-side method name from @Remap or @RemapByPrefix, matching the runtime Graal remapper.
        // Declarations and hints use the remapped name. The Java name stays in name for sorting and
        // editing, and rendering falls back to it when renameTo is empty.
        String jsName = jsName(method);
        if (jsName == null) {
            m.hidden = true; // @HideFromJS
            return m;
        }
        if (!jsName.equals(method.getName())) {
            m.renameTo = jsName;
        }
        m.isStatic = Modifier.isStatic(method.getModifiers());
        m.returnType = outputSlot(method.getGenericReturnType());
        ScriptOutput output = method.getAnnotation(ScriptOutput.class);
        if (output != null) {
            if (output.value().isBlank()) {
                throw new IllegalArgumentException("[NEKO-4064] Script output declaration is blank: " + method);
            }
            m.returnType.outputTypeScript = output.value();
        }
        reflectTypeParameters(m, List.of(method.getTypeParameters()));
        reflectParamsInto(m, method);
        m.docs.addAll(AnnotatedDocs.executableDocs(method));
        m.overloads.addAll(AnnotatedDocs.overloads(method));
        return m;
    }

    private static void reflectTypeParameters(MethodDecl method, List<TypeVariable<?>> variables) {
        method.typeParams.clear();
        method.typeParameterBounds.clear();
        for (TypeVariable<?> variable : variables) {
            List<TypeSlot> bounds = new ArrayList<>();
            for (Type bound : variable.getBounds()) {
                if (bound == Object.class) continue;
                TypeSlot slot = TypeSlot.of(bound, toRef(bound));
                bounds.add(slot);
            }
            method.typeParams.add(variable.getName());
            if (!bounds.isEmpty()) method.typeParameterBounds.put(variable.getName(), List.copyOf(bounds));
        }
    }

    /**
     * JS-side member name, delegated to {@link JavaMemberIndex#remapName}. Passing a null
     * hideMarker makes a {@code @HideFromJS} hit return null, and the caller then skips the
     * member. Without a remap match the original name is returned.
     */
    private static String jsName(java.lang.reflect.Method method) {
        return JavaMemberIndex.remapName(method, null, method.getName());
    }

    private void reflectParamsInto(MethodDecl m, java.lang.reflect.Executable exec) {
        boolean varArgs = exec.isVarArgs();
        var params = exec.getParameters();
        for (int i = 0; i < params.length; i++) {
            var p = params[i];
            Type sourceType;
            if (varArgs && i == params.length - 1 && p.getType().isArray()) {
                // A varargs parameter must take the generic path: p.getType() gives only the raw class array,
                // which drops the component's type arguments (MemoryModuleType<?>... becomes raw
                // MemoryModuleType). getParameterizedType() returns a GenericArrayType whose component
                // is MemoryModuleType<?> and keeps the type arguments.
                Type generic = p.getParameterizedType();
                sourceType = generic instanceof GenericArrayType gat ? gat.getGenericComponentType()
                        : p.getType().getComponentType();
            } else {
                sourceType = p.getParameterizedType();
            }
            MethodDecl.MethodParam mp = new MethodDecl.MethodParam(
                    p.isNamePresent() ? p.getName() : "arg" + i,
                    parameterSlot(sourceType, p),
                    varArgs && i == params.length - 1);
            m.params.add(mp);
        }
    }

    private static TypeSlot parameterSlot(Type type, java.lang.reflect.Parameter parameter) {
        TypeSlot slot = TypeSlot.of(type, toRef(type));
        ScriptInput annotation = parameter.getAnnotation(ScriptInput.class);
        if (annotation != null) {
            try {
                slot.inputShape = java.util.Objects.requireNonNull(
                        annotation.value().getConstructor().newInstance().inputShape(), "inputShape");
            } catch (ReflectiveOperationException | RuntimeException exception) {
                throw new IllegalArgumentException("NEKO-4063: Cannot resolve script input shape for "
                        + parameter.getDeclaringExecutable() + " parameter " + parameter.getName(), exception);
            }
        }
        return slot;
    }

    /** Prefers declared setters, then pairs inherited candidates deterministically in the host class. */
    private TypeSlot findSetterParamSlot(Class<?> cls, String propName, List<Method> methods) {
        String setterName = beanSetterName(propName);
        Method best = null;
        for (Method method : methods) {
            String jsName = jsName(method);
            if (jsName == null || !jsName.equals(setterName) || method.getParameterCount() != 1
                    || !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (best == null || (method.getDeclaringClass() == cls && best.getDeclaringClass() != cls)
                    || ((method.getDeclaringClass() == cls) == (best.getDeclaringClass() == cls)
                    && compareCandidates(method, best) < 0)) {
                best = method;
            }
        }
        if (best == null) return null;
        Type t = best.getDeclaringClass() != cls
                ? inheritedSignature(cls, best).parameterTypes().get(0)
                : best.getGenericParameterTypes()[0];
        return parameterSlot(t, best.getParameters()[0]);
    }

    private static String beanSetterName(String property) {
        return "set" + property.substring(0, 1).toUpperCase(Locale.ROOT) + property.substring(1);
    }

    /** Getter detection uses the JS name after remapping, so neko$getId becomes getId and counts as a getter. */
    private static boolean isGetterName(String name) {
        return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
    }

    private static boolean isSetterName(String name) {
        return name.startsWith("set") && name.length() > 3;
    }

    /** Property name: getFoo becomes foo and isFoo becomes foo. Case mapping uses {@link Locale#ROOT}
     *  explicitly so the default locale, such as tr, cannot change the result. */
    private static String getPropertyName(String name) {
        if (name.startsWith("get") && name.length() > 3) {
            return name.substring(3, 4).toLowerCase(Locale.ROOT) + name.substring(4);
        }
        if (name.startsWith("is") && name.length() > 2) {
            return name.substring(2, 3).toLowerCase(Locale.ROOT) + name.substring(3);
        }
        return null;
    }

    /** Reflects generic parameters without loading members when no edited declaration is available. */
    public static List<TypeDecl.TypeParam> typeParametersOf(Class<?> cls) {
        List<TypeDecl.TypeParam> parameters = new ArrayList<>();
        for (TypeVariable<?> variable : JavaGenericTypes.parameters(cls)) {
            List<TypeSlot> bounds = Arrays.stream(variable.getBounds()).filter(bound -> bound != Object.class)
                    .map(bound -> TypeSlot.of(bound, toRef(bound))).toList();
            TypeSlot first = bounds.isEmpty() ? null : bounds.getFirst();
            parameters.add(new TypeDecl.TypeParam(JavaGenericTypes.parameterName(variable), first,
                    bounds.isEmpty() ? List.of() : bounds.subList(1, bounds.size())));
        }
        return parameters;
    }

    // ---- Java Type to ApiTypeRef: the one type mapping, lossless; each renderer applies its own syntax ----

    /** Converts reflection types through the same neutral model used by native handler metadata. */
    public static ApiTypeRef toRef(Type type) {
        return JavaGenericTypes.toTypeRef(type);
    }
}
