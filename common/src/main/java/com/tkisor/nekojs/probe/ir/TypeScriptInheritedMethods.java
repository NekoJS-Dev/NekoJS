package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Specializes an existing edited ancestor method without discovering or restoring other Java members. */
final class TypeScriptInheritedMethods {
    private record TypePair(Type original, Type resolved) {}

    private TypeScriptInheritedMethods() {}

    static MethodDecl specialize(TypeDecl receiver, TypeDecl owner, MethodDecl method,
                                 Map<String, ApiTypeRef> classBindings) {
        Map<String, ApiTypeRef> bindings = new LinkedHashMap<>(classBindings);
        Set<String> classNames = new LinkedHashSet<>();
        receiver.typeParams.forEach(parameter -> classNames.add(parameter.name));
        classBindings.values().forEach(value -> collectVariables(value, classNames));
        Set<String> reserved = new LinkedHashSet<>(classNames);
        reserved.addAll(method.typeParams);
        List<String> names = method.typeParams.stream().map(original -> {
            String name = original;
            if (classNames.contains(name)) {
                int suffix = 1;
                while (reserved.contains(name + suffix)) suffix++;
                name += suffix;
            }
            reserved.add(name);
            bindings.put(original, ApiTypeRef.typeVariable(name));
            return name;
        }).toList();

        Map<TypeVariable<?>, Type> sourceBindings = new LinkedHashMap<>();
        if (receiver.sourceClass != null && owner.sourceClass != null && method.sourceMethod != null) {
            Method source = method.sourceMethod;
            var before = FunctionalInterfaceResolver.resolve(owner.sourceClass, source);
            Map<TypeVariable<?>, String> requestedNames = new LinkedHashMap<>();
            TypeVariable<?>[] variables = source.getTypeParameters();
            for (int index = 0; index < variables.length; index++) {
                String oldName = before == null ? variables[index].getName() : before.typeParameters().get(index).getName();
                int position = method.typeParams.indexOf(oldName);
                if (position < 0) position = method.typeParams.indexOf(variables[index].getName());
                if (position >= 0) requestedNames.put(variables[index], names.get(position));
            }
            var after = FunctionalInterfaceResolver.resolve(receiver.sourceClass, source, requestedNames);
            if (before != null && after != null) {
                Set<TypePair> visited = new LinkedHashSet<>();
                pair(before.returnType(), after.returnType(), sourceBindings, visited);
                for (int index = 0; index < before.parameterTypes().size(); index++) {
                    pair(before.parameterTypes().get(index), after.parameterTypes().get(index), sourceBindings, visited);
                }
                for (int index = 0; index < variables.length; index++) {
                    sourceBindings.put(variables[index], after.typeParameters().get(index));
                    pair(before.typeParameters().get(index), after.typeParameters().get(index), sourceBindings, visited);
                }
            }
        }

        MethodDecl copy = new MethodDecl(method.name);
        copy.sourceMethod = method.sourceMethod;
        copy.explicitlyEdited = method.explicitlyEdited;
        copy.renameTo = method.renameTo;
        copy.hidden = method.hidden;
        copy.isGetter = method.isGetter;
        copy.isSetter = method.isSetter;
        copy.property = method.property;
        copy.returnType = slot(method.returnType, bindings, sourceBindings);
        copy.setterParamType = slot(method.setterParamType, bindings, sourceBindings);
        copy.typeParams.addAll(names);
        for (int index = 0; index < method.typeParams.size(); index++) {
            List<TypeSlot> bounds = method.typeParameterBounds.getOrDefault(method.typeParams.get(index), List.of());
            copy.typeParameterBounds.put(names.get(index), bounds.stream()
                    .map(bound -> slot(bound, bindings, sourceBindings)).toList());
        }
        for (MethodDecl.MethodParam parameter : method.params) {
            var projected = new MethodDecl.MethodParam(parameter.name,
                    slot(parameter.type, bindings, sourceBindings), parameter.varargs);
            projected.optional = parameter.optional;
            copy.params.add(projected);
        }
        copy.docs.addAll(method.docs);
        copy.overloads.addAll(method.overloads);
        return copy;
    }

    static ApiTypeRef substitute(ApiTypeRef ref, Map<String, ApiTypeRef> bindings) {
        if (ref == null) return null;
        if (ref.kind() == ApiTypeRef.Kind.TYPE_VARIABLE) return bindings.getOrDefault(ref.name(), ref);
        List<ApiTypeRef> arguments = ref.arguments().stream().map(argument -> substitute(argument, bindings)).toList();
        ApiSignature callback = ref.callbackSignature();
        if (callback != null) {
            callback = new ApiSignature(callback.parameters().stream().map(parameter -> new ApiParameter(parameter.name(),
                    substitute(parameter.type(), bindings), parameter.optional(), parameter.varargs())).toList(),
                    substitute(callback.returnType(), bindings), callback.isConstructor());
        }
        return new ApiTypeRef(ref.kind(), ref.name(), arguments, callback);
    }

    private static TypeSlot slot(TypeSlot slot, Map<String, ApiTypeRef> bindings,
                                 Map<TypeVariable<?>, Type> sourceBindings) {
        if (slot == null) return null;
        ApiTypeRef ref = substitute(slot.ref, bindings);
        Type source = slot.sourceType == null ? null : FunctionalInterfaceResolver.substitute(slot.sourceType, sourceBindings);
        TypeSlot copy = TypeSlot.of(source, ref);
        copy.inputShape = slot.inputShape;
        copy.outputTypeScript = slot.outputTypeScript;
        // Edited hierarchy arguments may intentionally disagree with the Java superclass's signature.
        copy.overridden = slot.overridden || source == null || !TypeReflector.toRef(source).equals(ref);
        return copy;
    }

    private static void collectVariables(ApiTypeRef ref, Set<String> names) {
        if (ref.kind() == ApiTypeRef.Kind.TYPE_VARIABLE) names.add(ref.name());
        ref.arguments().forEach(argument -> collectVariables(argument, names));
        if (ref.callbackSignature() != null) {
            ref.callbackSignature().parameters().forEach(parameter -> collectVariables(parameter.type(), names));
            collectVariables(ref.callbackSignature().returnType(), names);
        }
    }

    private static void pair(Type original, Type resolved, Map<TypeVariable<?>, Type> bindings, Set<TypePair> visited) {
        if (original == null || resolved == null || !visited.add(new TypePair(original, resolved))) return;
        if (original instanceof TypeVariable<?> variable) {
            bindings.put(variable, resolved);
            if (variable.getGenericDeclaration() instanceof Method && resolved instanceof TypeVariable<?> target) {
                pairArrays(variable.getBounds(), target.getBounds(), bindings, visited);
            }
        } else if (original instanceof ParameterizedType from && resolved instanceof ParameterizedType to) {
            pairArrays(from.getActualTypeArguments(), to.getActualTypeArguments(), bindings, visited);
            pair(from.getOwnerType(), to.getOwnerType(), bindings, visited);
        } else if (original instanceof GenericArrayType from) {
            Type component = resolved instanceof GenericArrayType to ? to.getGenericComponentType()
                    : resolved instanceof Class<?> raw && raw.isArray() ? raw.getComponentType() : null;
            pair(from.getGenericComponentType(), component, bindings, visited);
        } else if (original instanceof WildcardType from && resolved instanceof WildcardType to) {
            pairArrays(from.getUpperBounds(), to.getUpperBounds(), bindings, visited);
            pairArrays(from.getLowerBounds(), to.getLowerBounds(), bindings, visited);
        }
    }

    private static void pairArrays(Type[] original, Type[] resolved, Map<TypeVariable<?>, Type> bindings, Set<TypePair> visited) {
        for (int index = 0; index < Math.min(original.length, resolved.length); index++) {
            pair(original[index], resolved[index], bindings, visited);
        }
    }
}
