package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;
import com.tkisor.nekojs.core.reflect.JavaGenericTypes;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps a narrow Java return alongside the capture contracts inherited by that receiver. */
final class TypeScriptCaptureReturns {
    private record Target(TypeDecl receiver, MethodDecl method) {}
    private final Map<TypeSlot, List<ApiTypeRef>> bounds = new IdentityHashMap<>();
    private final Set<String> affectedTypes = new LinkedHashSet<>();
    private final Map<String, TypeDecl> declarations;

    TypeScriptCaptureReturns(Map<String, TypeDecl> declarations) {
        this.declarations = declarations;
        for (TypeDecl receiver : declarations.values()) {
            if (receiver.hidden) continue;
            for (MethodDecl method : receiver.methods) {
                if (!eligible(method) || optionalArgument(method.returnType) == null) continue;
                Set<ApiTypeRef> inherited = new LinkedHashSet<>();
                collect(receiver, new Target(receiver, method), Map.of(), new LinkedHashSet<>(), inherited);
                inherited.remove(optionalArgument(method.returnType));
                if (!inherited.isEmpty()) {
                    bounds.put(method.returnType, List.copyOf(inherited));
                    affectedTypes.add(receiver.fqn);
                }
            }
        }
    }

    Set<String> affectedTypes() { return Set.copyOf(affectedTypes); }

    List<ApiTypeRef> bounds(TypeSlot slot) { return bounds.getOrDefault(slot, List.of()); }

    private void collect(TypeDecl receiver, Target target, Map<String, ApiTypeRef> bindings,
                         Set<String> visiting, Set<ApiTypeRef> result) {
        if (!visiting.add(receiver.fqn)) return;
        List<TypeSlot> parents = new ArrayList<>(receiver.interfaces);
        if (receiver.superType != null) parents.add(receiver.superType);
        for (TypeSlot slot : parents) {
            ApiTypeRef parentType = TypeScriptInheritedMethods.substitute(slot.ref, bindings);
            if (parentType.kind() != ApiTypeRef.Kind.SYMBOL) continue;
            TypeDecl parent = declarations.get(parentType.name().substring(parentType.name().indexOf(':') + 1));
            // Authored and hidden declarations have no recoverable reflected contract here.
            if (parent == null || parent.hidden) continue;
            Map<String, ApiTypeRef> arguments = new LinkedHashMap<>();
            for (int index = 0; index < parent.typeParams.size(); index++) {
                String name = parent.typeParams.get(index).name;
                arguments.put(name, index < parentType.arguments().size()
                        ? parentType.arguments().get(index) : ApiTypeRef.primitive("unknown"));
            }
            boolean declares = false;
            for (MethodDecl method : parent.methods) {
                if (method.sourceMethod != null && !method.explicitlyEdited
                        && method.sourceMethod.getDeclaringClass() != parent.sourceClass) continue;
                if (!sameSignature(target, method)) continue;
                declares = true;
                if (!eligible(method) || !target.method().effectiveName().equals(method.effectiveName())) continue;
                ApiTypeRef bound = captureBound(method.returnType);
                if (bound == null) {
                    // A concrete intermediate override still inherits its own capture facets.
                    if (optionalArgument(method.returnType) != null) {
                        collect(parent, target, arguments, new LinkedHashSet<>(visiting), result);
                    }
                    continue;
                }
                Map<String, ApiTypeRef> methodArguments = new LinkedHashMap<>(arguments);
                for (int index = 0; index < method.typeParams.size(); index++) {
                    if (index < target.method().typeParams.size()) methodArguments.put(method.typeParams.get(index),
                            ApiTypeRef.typeVariable(target.method().typeParams.get(index)));
                    else methodArguments.remove(method.typeParams.get(index));
                }
                result.add(TypeScriptInheritedMethods.substitute(bound, methodArguments));
                // A wildcard override also carries the capture facets of its own ancestors.
                collect(parent, target, arguments, new LinkedHashSet<>(visiting), result);
            }
            // The nearest declaration owns its signature, including hides and explicit edits.
            if (!declares) collect(parent, target, arguments, new LinkedHashSet<>(visiting), result);
        }
    }

    private static boolean eligible(MethodDecl method) {
        return !method.hidden && !method.isStatic && !method.isConstructor && method.sourceMethod != null
                && method.returnType != null && !method.returnType.overridden
                && method.params.stream().noneMatch(parameter -> parameter.type.overridden);
    }

    private static boolean sameSignature(Target target, MethodDecl right) {
        MethodDecl left = target.method();
        if (left.sourceMethod == null || right.sourceMethod == null
                || !left.sourceMethod.getName().equals(right.sourceMethod.getName())) return false;
        Class<?>[] parameters = left.sourceMethod.getParameterTypes();
        if (Arrays.equals(parameters, right.sourceMethod.getParameterTypes())) return true;
        // A generic parent parameter can erase to Object while its implementation accepts Base.
        // Resolve only an already-visible source signature; the edited IR still owns the contract.
        var resolved = target.receiver().sourceClass == null ? null
                : FunctionalInterfaceResolver.resolve(target.receiver().sourceClass, right.sourceMethod);
        return resolved != null && Arrays.equals(parameters,
                resolved.parameterTypes().stream().map(JavaGenericTypes::erased).toArray(Class<?>[]::new));
    }

    private static ApiTypeRef optionalArgument(TypeSlot slot) {
        if (slot == null || slot.overridden || slot.ref.kind() != ApiTypeRef.Kind.SYMBOL
                || !slot.ref.name().equals("java:java.util.Optional") || slot.ref.arguments().size() != 1) return null;
        return slot.ref.arguments().getFirst();
    }

    /** Uses reflection only to distinguish a wildcard; its effective bound comes from the edited IR. */
    private static ApiTypeRef captureBound(TypeSlot slot) {
        ApiTypeRef bound = optionalArgument(slot);
        if (bound == null || !(slot.sourceType instanceof ParameterizedType source)
                || source.getRawType() != java.util.Optional.class
                || source.getActualTypeArguments().length != 1
                || !(source.getActualTypeArguments()[0] instanceof WildcardType wildcard)
                || wildcard.getLowerBounds().length != 0 || wildcard.getUpperBounds().length != 1
                || wildcard.getUpperBounds()[0] == Object.class) return null;
        return bound;
    }
}
