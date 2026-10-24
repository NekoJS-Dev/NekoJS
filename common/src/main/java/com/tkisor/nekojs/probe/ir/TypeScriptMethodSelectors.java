package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;

/**
 * Separates real instance fields from same-name methods using Graal's existing signature selectors.
 * Field-bearing branches and their method-bearing ancestors omit the ordinary method spelling.
 * Other branches retain ordinary calls, including operations previously inherited from an ancestor.
 * The projection uses this render's edited hierarchy and never changes the shared declaration IR.
 */
final class TypeScriptMethodSelectors {
    private record Member(String owner, String name) {}
    private record InheritedMethod(TypeDecl owner, MethodDecl method, Map<String, ApiTypeRef> bindings) {}

    private final Map<String, TypeDecl> declarations;
    private final Map<String, List<String>> parents = new LinkedHashMap<>();
    private final Map<String, List<TypeSlot>> parentSlots = new LinkedHashMap<>();
    private final Map<String, List<String>> children = new LinkedHashMap<>();
    private final Map<Member, Boolean> callable = new HashMap<>();
    private final Set<Member> affected = new LinkedHashSet<>();
    private final Set<Member> unsafe = new LinkedHashSet<>();
    private final Map<String, List<MethodDecl>> materialized = new LinkedHashMap<>();
    private final Map<MethodDecl, InheritedMethod> origins = new LinkedHashMap<>();
    private final Map<String, List<MethodDecl>> methods = new LinkedHashMap<>();
    private final Map<String, List<MethodDecl>> bridgeContracts = new LinkedHashMap<>();
    private final BiPredicate<TypeDecl, MethodDecl> emitsMethod;

    TypeScriptMethodSelectors(Map<String, TypeDecl> declarations) {
        this(declarations, (owner, method) -> true);
    }

    TypeScriptMethodSelectors(Map<String, TypeDecl> declarations, BiPredicate<TypeDecl, MethodDecl> emitsMethod) {
        this.emitsMethod = emitsMethod;
        Map<String, TypeDecl> visible = new TreeMap<>();
        declarations.forEach((name, declaration) -> {
            if (!declaration.hidden) visible.put(name, declaration);
        });
        // Keep the sorted iteration order: it seeds family discovery and downstream declaration order.
        this.declarations = Collections.unmodifiableMap(visible);
        visible.keySet().forEach(name -> children.put(name, new ArrayList<>()));
        for (TypeDecl declaration : visible.values()) {
            List<String> inherited = new ArrayList<>();
            if (declaration.superType != null) addParent(inherited, declaration.superType);
            for (TypeSlot slot : declaration.interfaces) addParent(inherited, slot);
            parents.put(declaration.fqn, List.copyOf(inherited));
            List<TypeSlot> slots = new ArrayList<>();
            if (declaration.superType != null) slots.add(declaration.superType);
            slots.addAll(declaration.interfaces);
            parentSlots.put(declaration.fqn, List.copyOf(slots));
            for (String parent : inherited) children.get(parent).add(declaration.fqn);
        }
        // Limit inherited-copy normalization to actual field/method families. A same-name field in
        // an unrelated class must not alter this hierarchy's authored declarations.
        Set<Member> fields = fieldConflicts();
        Set<Member> projectionCandidates = new LinkedHashSet<>();
        for (Member field : fields) collectFamily(field, projectionCandidates);
        for (TypeDecl declaration : visible.values()) {
            List<MethodDecl> projected = new ArrayList<>();
            for (MethodDecl method : declaration.methods) {
                MethodDecl replacement = method;
                if (method.sourceMethod != null && !method.isStatic && !method.explicitlyEdited
                        && method.sourceMethod.getDeclaringClass() != declaration.sourceClass) {
                    InheritedMethod inherited = inheritedDeclaration(declaration.fqn, method.sourceMethod,
                            Map.of(), new LinkedHashSet<>());
                    if (inherited != null && (projectionCandidates.contains(new Member(declaration.fqn, method.effectiveName()))
                            || projectionCandidates.contains(new Member(declaration.fqn, inherited.method().effectiveName())))) {
                        replacement = TypeScriptInheritedMethods.specialize(declaration,
                                inherited.owner(), inherited.method(), inherited.bindings());
                        origins.put(replacement, inherited);
                    }
                }
                projected.add(replacement);
            }
            methods.put(declaration.fqn, List.copyOf(projected));
        }
        for (Member field : fields) collectFamily(field, affected);
        Set<Member> fieldActive = new LinkedHashSet<>(fields);
        ArrayDeque<Member> queue = new ArrayDeque<>(fields);
        while (!queue.isEmpty()) {
            Member current = queue.removeFirst();
            for (String child : children.get(current.owner())) {
                Member next = new Member(child, current.name());
                if (affected.contains(next) && fieldActive.add(next)) queue.addLast(next);
            }
        }
        unsafe.addAll(fieldActive);
        queue.addAll(fieldActive);
        while (!queue.isEmpty()) {
            Member current = queue.removeFirst();
            for (String parent : parents.get(current.owner())) {
                Member next = new Member(parent, current.name());
                if (affected.contains(next) && unsafe.add(next)) queue.addLast(next);
            }
        }
        for (Member member : affected) {
            TypeDecl declaration = visible.get(member.owner());
            addBridgeContracts(declaration, member);
            if (unsafe.contains(member) || methods(declaration).stream().anyMatch(method -> !method.isStatic
                    && !method.isConstructor && member.name().equals(method.effectiveName()))
                    || parents.get(member.owner()).stream()
                    .anyMatch(parent -> keepsOrdinary(new Member(parent, member.name())))) continue;
            for (InheritedMethod inherited : inheritedMethods(member, Map.of(), new LinkedHashSet<>())) {
                MethodDecl method = TypeScriptInheritedMethods.specialize(declaration, inherited.owner(), inherited.method(), inherited.bindings());
                materialized.computeIfAbsent(member.owner(), ignored -> new ArrayList<>()).add(method);
                origins.put(method, inherited);
            }
        }
    }

    Set<String> affectedTypes() {
        Set<String> types = affected.stream().map(Member::owner).collect(java.util.stream.Collectors.toSet());
        methods.forEach((name, projected) -> {
            if (!projected.equals(declarations.get(name).methods)) types.add(name);
        });
        return Set.copyOf(types);
    }

    List<MethodDecl> methods(TypeDecl declaration) {
        return methods.getOrDefault(declaration.fqn, declaration.methods);
    }

    List<String> names(TypeDecl declaration, MethodDecl method) {
        Member member = new Member(declaration.fqn, method.effectiveName());
        if (!visibleMethod(declaration, method) || !affected.contains(member)) return List.of(method.effectiveName());
        String selector = selector(method.sourceMethod);
        return unsafe.contains(member) ? List.of(selector) : List.of(method.effectiveName(), selector);
    }

    List<MethodDecl> materializedMethods(TypeDecl declaration) {
        return List.copyOf(materialized.getOrDefault(declaration.fqn, List.of()));
    }

    ApiTypeRef inheritedOwner(MethodDecl method) {
        InheritedMethod inherited = origins.get(method);
        return inherited == null ? null : new ApiTypeRef(ApiTypeRef.Kind.SYMBOL, "java:" + inherited.owner().fqn,
                inherited.owner().typeParams.stream().map(parameter -> inherited.bindings().get(parameter.name)).toList(), null);
    }

    String inheritedName(MethodDecl method) {
        InheritedMethod inherited = origins.get(method);
        List<String> names = names(inherited.owner(), inherited.method());
        return names.getLast();
    }

    List<MethodDecl> bridgeContracts(TypeDecl declaration) {
        return List.copyOf(bridgeContracts.getOrDefault(declaration.fqn, List.of()));
    }

    private Set<Member> fieldConflicts() {
        Set<Member> fields = new LinkedHashSet<>();
        for (TypeDecl declaration : declarations.values()) {
            if (declaration.kind != TypeDecl.Kind.CLASS) continue;
            for (FieldDecl field : inheritedFields(declaration, new LinkedHashSet<>())) {
                Member member = new Member(declaration.fqn, field.effectiveName());
                if (visibleField(field) && hasMethod(member, new LinkedHashSet<>())) fields.add(member);
            }
        }
        return fields;
    }

    private boolean keepsOrdinary(Member member) {
        TypeDecl declaration = declarations.get(member.owner());
        List<MethodDecl> own = methods(declaration).stream().filter(method -> !method.isStatic
                && !method.isConstructor && member.name().equals(method.effectiveName())).toList();
        if (own.stream().anyMatch(method -> !method.hidden && emitsMethod.test(declaration, method)
                && names(declaration, method).contains(member.name()))) return true;
        // A safe parent without its own overload restores the operation at its first safe level.
        // Its descendants inherit that declaration instead of repeatedly materializing it.
        return own.isEmpty() && !unsafe.contains(member)
                && !inheritedMethods(member, Map.of(), new LinkedHashSet<>()).isEmpty();
    }

    private List<FieldDecl> inheritedFields(TypeDecl declaration, Set<String> visiting) {
        if (!visiting.add(declaration.fqn)) return List.of();
        List<FieldDecl> fields = new ArrayList<>(declaration.fields);
        for (String parent : parents.get(declaration.fqn)) {
            TypeDecl ancestor = declarations.get(parent);
            if (ancestor.kind == TypeDecl.Kind.CLASS) fields.addAll(inheritedFields(ancestor, visiting));
        }
        return fields;
    }

    private void addBridgeContracts(TypeDecl declaration, Member member) {
        if (declaration.sourceClass == null || declaration.kind != TypeDecl.Kind.CLASS) return;
        Set<String> selectors = new LinkedHashSet<>();
        methods(declaration).stream().filter(method -> visibleMethod(declaration, method)
                && member.name().equals(method.effectiveName())).forEach(method -> selectors.add(selector(method.sourceMethod)));
        if (selectors.isEmpty()) return;
        for (InheritedMethod inherited : inheritedMethods(member, Map.of(), new LinkedHashSet<>())) {
            Method source = inherited.method().sourceMethod;
            String name = selector(source);
            if (selectors.contains(name)) continue;
            // A real compiler bridge proves that the erased ancestor selector exists on this implementation.
            // The signature still comes from visible edited IR, never from an undiscovered bridge member.
            boolean bridge = Arrays.stream(declaration.sourceClass.getMethods()).anyMatch(method -> method.isBridge()
                    && method.getName().equals(source.getName())
                    && Arrays.equals(method.getParameterTypes(), source.getParameterTypes()));
            if (bridge && selectors.add(name)) {
                MethodDecl method = TypeScriptInheritedMethods.specialize(declaration,
                        inherited.owner(), inherited.method(), inherited.bindings());
                bridgeContracts.computeIfAbsent(declaration.fqn, ignored -> new ArrayList<>()).add(method);
                origins.put(method, inherited);
            }
        }
    }

    private void addParent(List<String> inherited, TypeSlot slot) {
        if (slot.ref == null || slot.ref.kind() != ApiTypeRef.Kind.SYMBOL) return;
        String symbol = slot.ref.name();
        String name = symbol.substring(symbol.indexOf(':') + 1);
        if (declarations.containsKey(name) && !inherited.contains(name)) inherited.add(name);
    }

    private boolean hasMethod(Member member, Set<Member> visiting) {
        Boolean known = callable.get(member);
        if (known != null) return known;
        TypeDecl declaration = declarations.get(member.owner());
        if (!visiting.add(member)) return false;
        boolean found = methods(declaration).stream().anyMatch(method -> visibleMethod(declaration, method)
                && member.name().equals(method.effectiveName()));
        if (!found) {
            for (String parent : parents.get(member.owner())) {
                if (hasMethod(new Member(parent, member.name()), new LinkedHashSet<>(visiting))) {
                    found = true;
                    break;
                }
            }
        }
        callable.put(member, found);
        return found;
    }

    private void collectFamily(Member seed, Set<Member> accumulator) {
        ArrayDeque<Member> queue = new ArrayDeque<>();
        queue.add(seed);
        while (!queue.isEmpty()) {
            Member current = queue.removeFirst();
            if (!accumulator.add(current)) continue;
            for (String parent : parents.get(current.owner())) {
                Member next = new Member(parent, current.name());
                if (hasMethod(next, new LinkedHashSet<>())) queue.addLast(next);
            }
            if (hasMethod(current, new LinkedHashSet<>())) {
                for (String child : children.get(current.owner())) {
                    queue.addLast(new Member(child, current.name()));
                }
            }
        }
    }

    private List<InheritedMethod> inheritedMethods(Member member, Map<String, ApiTypeRef> bindings, Set<Member> visiting) {
        if (!visiting.add(member)) return List.of();
        List<InheritedMethod> methods = new ArrayList<>();
        Set<Method> seen = new LinkedHashSet<>();
        for (TypeSlot slot : parentSlots.get(member.owner())) {
            if (slot.ref == null || slot.ref.kind() != ApiTypeRef.Kind.SYMBOL) continue;
            ApiTypeRef type = TypeScriptInheritedMethods.substitute(slot.ref, bindings);
            String parent = type.name().substring(type.name().indexOf(':') + 1);
            TypeDecl declaration = declarations.get(parent);
            if (declaration == null) continue;
            Map<String, ApiTypeRef> arguments = parentArguments(declaration, type);
            List<MethodDecl> own = methods(declaration).stream().filter(method -> !method.isStatic
                    && !method.isConstructor && member.name().equals(method.effectiveName())).toList();
            List<InheritedMethod> candidates = own.isEmpty()
                    ? inheritedMethods(new Member(parent, member.name()), arguments, new LinkedHashSet<>(visiting))
                    : own.stream().map(method -> new InheritedMethod(declaration, method, arguments)).toList();
            for (InheritedMethod candidate : candidates) {
                MethodDecl method = candidate.method();
                // A hidden nearest declaration still blocks the same inherited Java signature.
                if (method.sourceMethod != null && seen.add(method.sourceMethod)
                        && visibleMethod(candidate.owner(), method)) methods.add(candidate);
            }
        }
        return List.copyOf(methods);
    }

    private boolean visibleMethod(TypeDecl declaration, MethodDecl method) {
        return !method.hidden && !method.isStatic && !method.isConstructor && method.sourceMethod != null
                && declaration.kind != TypeDecl.Kind.ENUM && emitsMethod.test(declaration, method);
    }

    private static boolean visibleField(FieldDecl field) {
        return !field.hidden && !field.isStatic && field.sourceField != null
                && JavaMemberIndex.remapName(field.sourceField, null, field.sourceField.getName()) != null;
    }

    private InheritedMethod inheritedDeclaration(String owner, Method source, Map<String, ApiTypeRef> bindings,
                                                  Set<String> visiting) {
        if (!visiting.add(owner)) return null;
        for (TypeSlot slot : parentSlots.get(owner)) {
            if (slot.ref == null || slot.ref.kind() != ApiTypeRef.Kind.SYMBOL) continue;
            ApiTypeRef type = TypeScriptInheritedMethods.substitute(slot.ref, bindings);
            String parent = type.name().substring(type.name().indexOf(':') + 1);
            TypeDecl declaration = declarations.get(parent);
            if (declaration == null) continue;
            Map<String, ApiTypeRef> arguments = parentArguments(declaration, type);
            for (MethodDecl method : declaration.methods) {
                if (source.equals(method.sourceMethod) && (method.explicitlyEdited
                        || source.getDeclaringClass() == declaration.sourceClass)) {
                    return new InheritedMethod(declaration, method, arguments);
                }
            }
            InheritedMethod inherited = inheritedDeclaration(parent, source, arguments, new LinkedHashSet<>(visiting));
            if (inherited != null) return inherited;
        }
        return null;
    }

    private static Map<String, ApiTypeRef> parentArguments(TypeDecl declaration, ApiTypeRef type) {
        Map<String, ApiTypeRef> arguments = new LinkedHashMap<>();
        for (int index = 0; index < declaration.typeParams.size(); index++) {
            String name = declaration.typeParams.get(index).name;
            arguments.put(name, type.arguments().isEmpty() ? ApiTypeRef.primitive("any")
                    : index < type.arguments().size() ? type.arguments().get(index) : ApiTypeRef.typeVariable(name));
        }
        return arguments;
    }

    static String selector(Method method) {
        return method.getName() + "(" + String.join(",", Arrays.stream(method.getParameterTypes())
                .map(Class::getTypeName).toList()) + ")";
    }
}
