package com.tkisor.nekojs.probe.ir;

import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;
import com.tkisor.nekojs.core.reflect.JavaGenericTypes;
import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects capture navigation while inheriting the complete, edited native instance surface. */
final class TypeScriptCaptureViews {
    private static final Map<String, String> DEFAULT_NAMES = Map.of(
            "java.util.Optional", "$OptionalView", "java.util.stream.Stream", "$StreamView",
            "java.util.List", "$ListView", "java.util.Collection", "$CollectionView",
            "java.util.SequencedCollection", "$SequencedCollectionView", "java.util.ListIterator", "$ListIteratorView");
    private static final Set<String> INPUTS = Set.of("java.util.Optional", "java.util.Collection", "java.util.stream.Stream");
    private static final String NON_EXECUTABLE = " & { readonly [Symbol.hasInstance]?: never }";
    private record Member(TypeDecl owner, MethodDecl method, Map<String, ApiTypeRef> bindings, String name) {}
    private record Witness(String name, ApiTypeRef value) {}

    private final Map<String, TypeDecl> declarations;
    private final Map<String, String> names = new LinkedHashMap<>();
    private final TypeScriptClassRenderer renderer;
    private final Map<String, String> inputNames = new LinkedHashMap<>();

    TypeScriptCaptureViews(Map<String, TypeDecl> declarations, TypeScriptClassRenderer renderer) {
        this.declarations = Map.copyOf(declarations);
        this.renderer = renderer;
        Map<String, Set<String>> occupied = new LinkedHashMap<>();
        for (TypeDecl declaration : declarations.values()) {
            if (declaration.hidden) continue;
            Set<String> reserved = occupied.computeIfAbsent(packageOf(declaration.fqn), key -> new LinkedHashSet<>());
            reserved.add("$" + declaration.fqn.substring(declaration.fqn.lastIndexOf('.') + 1));
            if (declaration.effectiveTypeName() != null) reserved.add("$" + declaration.effectiveTypeName());
        }
        DEFAULT_NAMES.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            TypeDecl source = declarations.get(entry.getKey());
            if (source == null || source.hidden || source.typeParams.size() != 1) return;
            Set<String> reserved = occupied.computeIfAbsent(packageOf(entry.getKey()), key -> new LinkedHashSet<>());
            String name = entry.getValue();
            for (int suffix = 1; reserved.contains(name); suffix++) name = entry.getValue() + "$" + suffix;
            reserved.add(name);
            names.put(entry.getKey(), name);
        });
        for (String pkg : names.keySet().stream().map(TypeScriptCaptureViews::packageOf).distinct().sorted().toList()) {
            Set<String> reserved = occupied.get(pkg);
            for (String input : INPUTS.stream().sorted().toList()) {
                String base = "$NekoCapture" + input.substring(input.lastIndexOf('.') + 1) + "Input";
                String name = base;
                for (int suffix = 1; reserved.contains(name); suffix++) name = base + "$" + suffix;
                reserved.add(name);
                inputNames.put(pkg + "->" + input, name);
            }
        }
    }

    Map<String, String> names() { return Map.copyOf(names); }
    boolean available(String fqn) { return names.containsKey(fqn); }
    String name(String fqn) { return names.getOrDefault(fqn, DEFAULT_NAMES.get(fqn)); }
    private static String packageOf(String fqn) { return fqn.substring(0, fqn.lastIndexOf('.')); }
    private static String fqn(ApiTypeRef ref) { return ref.name().substring(ref.name().indexOf(':') + 1); }
    private static ApiTypeRef symbol(String fqn, ApiTypeRef argument) {
        return new ApiTypeRef(ApiTypeRef.Kind.SYMBOL, "java:" + fqn, List.of(argument), null);
    }

    Set<String> localNames(String packageName) {
        Set<String> local = new LinkedHashSet<>();
        names.forEach((fqn, name) -> { if (packageOf(fqn).equals(packageName)) local.add(name); });
        if (local.isEmpty()) return Set.of();
        for (String input : INPUTS) if (witness(input, "$Value") != null) local.add(inputName(input, packageName));
        return Set.copyOf(local);
    }

    String render(String packageName, TypeAliasRegistry aliases) {
        if (localNames(packageName).isEmpty()) return "";
        StringBuilder output = new StringBuilder();
        for (String input : INPUTS.stream().sorted().toList()) {
            Witness witness = witness(input, "$Value");
            if (witness == null) continue;
            output.append("    type ").append(inputName(input, packageName)).append("<$Value> = globalThis.NoInfer<")
                    .append(TypeScriptClassRenderer.renderTypeRef(symbol(input, ApiTypeRef.primitive("any")), aliases, false))
                    .append("> & { readonly ").append(new JsonPrimitive(witness.name())).append(": () => ")
                    .append(TypeScriptClassRenderer.renderTypeRef(witness.value(), aliases, false)).append(" };\n");
        }
        for (String source : names.keySet()) {
            if (!packageOf(source).equals(packageName)) continue;
            TypeDecl declaration = declarations.get(source);
            List<Member> members = members(declaration, "B");
            Set<String> locals = new LinkedHashSet<>();
            members.forEach(member -> locals.addAll(member.method().typeParams));
            String bound = "B";
            for (int suffix = 1; locals.contains(bound); suffix++) bound = "B" + suffix;
            if (!bound.equals("B")) members = members(declaration, bound);
            output.append("    export interface ").append(aliases.reference(source, names.get(source)))
                    .append('<').append(bound).append("> extends ")
                    .append(TypeScriptClassRenderer.renderTypeRef(symbol(source, ApiTypeRef.typeVariable(bound)), aliases, false))
                    .append(" {\n");
            Map<String, List<Member>> groups = new LinkedHashMap<>();
            for (Member member : members) groups.computeIfAbsent(member.name(), key -> new ArrayList<>()).add(member);
            for (List<Member> group : groups.values()) {
                // Authored or edited groups remain intact through native inheritance, including raw overloads.
                if (group.stream().anyMatch(member -> edited(member.method()))) continue;
                List<String> rendered = new ArrayList<>();
                boolean changed = false;
                for (Member member : group.stream().sorted(Comparator.comparingInt(m -> m.method().params.isEmpty() ? 0 : 1)).toList()) {
                    Map<String, String> variables = new LinkedHashMap<>();
                    member.bindings().forEach((key, value) -> variables.put(key,
                            TypeScriptClassRenderer.renderTypeRef(value, aliases, false)));
                    member.method().typeParams.forEach(variables::remove);
                    TypeAliasRegistry scoped = aliases.withTypeVariables(variables);
                    Map<String, ApiTypeRef> bindings = new LinkedHashMap<>(member.bindings());
                    member.method().typeParams.forEach(bindings::remove);
                    TypeSlot resultSlot = member.method().returnType;
                    ApiTypeRef result = TypeScriptInheritedMethods.substitute(resultSlot.ref, bindings);
                    String returnType = TypeScriptClassRenderer.renderTypeRef(result, scoped, false);
                    if (result.kind() == ApiTypeRef.Kind.SYMBOL && available(fqn(result)) && result.arguments().size() == 1) {
                        returnType = scoped.reference(fqn(result), name(fqn(result))) + "<"
                                + TypeScriptClassRenderer.renderTypeRef(result.arguments().getFirst(), scoped, false) + ">";
                        changed = true;
                    }
                    List<List<String>> signatures = new ArrayList<>();
                    signatures.add(new ArrayList<>());
                    for (var parameter : member.method().params) {
                        String baseline = renderer.captureInput(parameter.type, scoped);
                        List<String> projected = input(parameter.type, baseline, scoped, bound, packageName);
                        changed |= projected.size() != 1 || !projected.getFirst().equals(baseline);
                        List<List<String>> next = new ArrayList<>();
                        for (List<String> signature : signatures) for (String type : projected) {
                            List<String> copy = new ArrayList<>(signature);
                            copy.add(type);
                            next.add(copy);
                        }
                        signatures = next;
                    }
                    for (List<String> parameters : signatures) {
                        rendered.add(renderer.captureMethod(member.method(), member.name(), scoped, parameters, returnType));
                    }
                }
                if (changed) rendered.forEach(output::append);
            }
            output.append("    }\n");
        }
        return output.toString();
    }

    private List<String> input(TypeSlot slot, String baseline, TypeAliasRegistry aliases, String bound, String packageName) {
        if (slot.overridden || slot.sourceType == null) return List.of(baseline);
        Class<?> raw = FunctionalInterfaceResolver.rawClass(slot.sourceType);
        if (raw != null && aliases.getRegisteredAlias(raw.getName()) != null) return List.of(baseline);
        String carrier = carrier(slot.sourceType, aliases, packageName);
        if (carrier != null) {
            String nativeType = TypeScriptClassRenderer.renderTypeRef(TypeReflector.toRef(slot.sourceType), aliases, false);
            return List.of(baseline.replace(nativeType, carrier));
        }
        ApiTypeRef declared = slot.ref;
        if (declared.kind() == ApiTypeRef.Kind.SYMBOL && fqn(declared).equals("java.util.stream.Collector")
                && declared.arguments().size() == 3) {
            String accumulator = collectorConstraint(slot.sourceType, aliases);
            if (accumulator != null) return List.of(accumulator);
        }
        var shape = FunctionalInterfaceAliasGenerator.captureInputShape(slot.sourceType, aliases);
        if (shape == null || shape.sealed()) return List.of(baseline);
        String hostName = exposed(shape.method());
        if (hostName == null) return List.of(baseline);
        List<String> hostArguments = new ArrayList<>(shape.hostArguments());
        String result = shape.result();
        var signature = FunctionalInterfaceResolver.resolve(slot.sourceType);
        String projected = signature == null ? null : carrier(signature.returnType(), aliases, packageName);
        if (projected != null) {
            result = projected;
            var rawSignature = FunctionalInterfaceResolver.resolve(raw);
            if (rawSignature != null && rawSignature.returnType() instanceof TypeVariable<?> returned) {
                for (int index = 0; index < raw.getTypeParameters().length; index++) {
                    if (raw.getTypeParameters()[index].getName().equals(returned.getName())) hostArguments.set(index, projected);
                }
            }
        }
        boolean captures = shape.callbackArguments().stream().anyMatch(value -> containsVariable(value, bound))
                || containsVariable(result, bound);
        if (!captures && projected == null) return List.of(baseline);
        var adjusted = new FunctionalInterfaceAliasGenerator.InputShape(shape.hostName(), hostArguments,
                shape.callbackArguments(), result, shape.method(), shape.sealed(), shape.returnsVoid());
        if (shape.callbackArguments().isEmpty()) {
            if (projected == null) return List.of(baseline);
            // Producer-only SAMs need no operand-domain guard. Keeping their ordinary alias also
            // preserves contextual instantiation of factories such as Optional.empty().
            var alias = aliases.getFunctionalInterfaceAlias(raw.getName());
            List<String> arguments = new ArrayList<>(hostArguments);
            arguments.add(result);
            return List.of(aliases.reference(raw.getName(), alias.aliasName()) + "<" + String.join(", ", arguments) + ">");
        }
        String callback = adjusted.callback();
        String host = adjusted.host() + " & { readonly " + new JsonPrimitive(hostName) + ": " + callback + " }" + NON_EXECUTABLE;
        String callable = "(" + callback + ") | globalThis.NoInfer<{ readonly "
                + new JsonPrimitive(shape.method().getName()) + ": " + callback + " }" + NON_EXECUTABLE + ">";
        return List.of(host, callable);
    }

    private String collectorConstraint(Type source, TypeAliasRegistry aliases) {
        TypeDecl collector = declarations.get("java.util.stream.Collector");
        if (collector == null) return null;
        List<Type> arguments = JavaGenericTypes.arguments(source);
        Map<String, String> variables = new LinkedHashMap<>();
        for (int index = 0; index < collector.typeParams.size() && index < arguments.size(); index++) {
            Type argument = arguments.get(index);
            if (argument instanceof WildcardType wildcard && wildcard.getLowerBounds().length == 1) {
                argument = wildcard.getLowerBounds()[0];
            }
            variables.put(collector.typeParams.get(index).name,
                    TypeScriptClassRenderer.renderTypeRef(TypeReflector.toRef(argument), aliases, false));
        }
        TypeAliasRegistry scoped = aliases.withTypeVariables(variables);
        for (MethodDecl method : collector.methods) {
            if (method.hidden || edited(method) || !method.params.isEmpty() || method.isStatic) continue;
            var shape = FunctionalInterfaceAliasGenerator.captureInputShape(method.returnType.sourceType, scoped);
            if (shape == null || shape.callbackArguments().size() != 2 || !shape.returnsVoid()) continue;
            String consumer = exposed(shape.method());
            if (consumer == null) continue;
            return aliases.reference(collector.fqn, "$Collector") + "<" + String.join(", ", variables.values())
                    + "> & { readonly " + new JsonPrimitive(method.effectiveName()) + ": () => { readonly "
                    + new JsonPrimitive(consumer) + ": " + shape.callback() + " } }";
        }
        return null;
    }

    private String carrier(Type source, TypeAliasRegistry aliases, String packageName) {
        if (source instanceof WildcardType wildcard) {
            if (wildcard.getLowerBounds().length != 0 || wildcard.getUpperBounds().length != 1) return null;
            source = wildcard.getUpperBounds()[0];
        }
        if (!(source instanceof ParameterizedType type) || !(type.getRawType() instanceof Class<?> raw)
                || !INPUTS.contains(raw.getName()) || aliases.getRegisteredAlias(raw.getName()) != null
                || type.getActualTypeArguments().length != 1
                || !(type.getActualTypeArguments()[0] instanceof WildcardType wildcard)
                || wildcard.getLowerBounds().length != 0 || wildcard.getUpperBounds().length != 1
                || wildcard.getUpperBounds()[0] == Object.class || witness(raw.getName(), "$Value") == null) return null;
        return inputName(raw.getName(), packageName) + "<"
                + TypeScriptClassRenderer.renderTypeRef(TypeReflector.toRef(wildcard.getUpperBounds()[0]), aliases, false) + ">";
    }

    private String inputName(String fqn, String packageName) {
        return inputNames.get(packageName + "->" + fqn);
    }

    private Witness witness(String source, String bound) {
        TypeDecl declaration = declarations.get(source);
        if (!available(source)) return null;
        for (Member member : members(declaration, bound)) {
            MethodDecl method = member.method();
            if (edited(method) || !method.params.isEmpty() || !method.typeParams.isEmpty()) continue;
            ApiTypeRef value = TypeScriptInheritedMethods.substitute(method.returnType.ref, member.bindings());
            boolean direct = value.kind() == ApiTypeRef.Kind.TYPE_VARIABLE && value.name().equals(bound);
            boolean iterator = value.kind() == ApiTypeRef.Kind.SYMBOL && Set.of("java.util.Iterator", "java.util.Spliterator").contains(fqn(value))
                    && declarations.containsKey(fqn(value)) && value.arguments().equals(List.of(ApiTypeRef.typeVariable(bound)));
            if (direct || iterator) return new Witness(member.name(), value);
        }
        return null;
    }

    private String exposed(Method source) {
        TypeDecl owner = declarations.get(source.getDeclaringClass().getName());
        if (owner == null || owner.hidden) return null;
        return owner.methods.stream().filter(method -> source.equals(method.sourceMethod) && !method.hidden && !edited(method))
                .flatMap(method -> renderer.captureMethodNames(owner, method).stream()).findFirst().orElse(null);
    }

    private List<Member> members(TypeDecl source, String bound) {
        List<Member> output = new ArrayList<>();
        collect(source, Map.of(source.typeParams.getFirst().name, ApiTypeRef.typeVariable(bound)),
                new LinkedHashSet<>(), new LinkedHashSet<>(), output);
        return output;
    }

    private void collect(TypeDecl source, Map<String, ApiTypeRef> bindings, Set<String> visiting,
                         Set<String> seen, List<Member> output) {
        if (!visiting.add(source.fqn)) return;
        for (MethodDecl method : source.methods) {
            if (method.isStatic || method.isConstructor || method.sourceMethod == null) continue;
            if (method.sourceMethod.getDeclaringClass() != source.sourceClass && !method.explicitlyEdited) continue;
            String key = method.sourceMethod.getName() + java.util.Arrays.toString(method.sourceMethod.getParameterTypes());
            if (!seen.add(key) || method.hidden) continue;
            for (String name : renderer.captureMethodNames(source, method)) output.add(new Member(source, method, bindings, name));
        }
        List<TypeSlot> parents = new ArrayList<>(source.interfaces);
        if (source.superType != null) parents.add(source.superType);
        for (TypeSlot parent : parents) {
            ApiTypeRef type = TypeScriptInheritedMethods.substitute(parent.ref, bindings);
            if (type.kind() != ApiTypeRef.Kind.SYMBOL) continue;
            TypeDecl declaration = declarations.get(fqn(type));
            if (declaration == null || declaration.hidden) continue;
            Map<String, ApiTypeRef> arguments = new LinkedHashMap<>();
            for (int index = 0; index < declaration.typeParams.size(); index++) arguments.put(declaration.typeParams.get(index).name,
                    index < type.arguments().size() ? type.arguments().get(index) : ApiTypeRef.primitive("unknown"));
            collect(declaration, arguments, visiting, seen, output);
        }
    }

    private static boolean edited(MethodDecl method) {
        // Renaming a member does not change its type protocol. Slot and authored overload edits
        // remain native-owned; incompatible arity/static changes cannot reuse the Java signature.
        return !method.overloads.isEmpty() || method.returnType == null || method.returnType.overridden
                || method.params.stream().anyMatch(parameter -> parameter.type.overridden)
                || method.sourceMethod != null && (method.params.size() != method.sourceMethod.getParameterCount()
                || java.lang.reflect.Modifier.isStatic(method.sourceMethod.getModifiers()) != method.isStatic
                || !method.sourceMethod.isVarArgs() && method.params.stream().anyMatch(parameter -> parameter.varargs));
    }
    private static boolean containsVariable(String value, String variable) {
        return java.util.regex.Pattern.compile("(?<![\\w$])" + java.util.regex.Pattern.quote(variable) + "(?![\\w$])").matcher(value).find();
    }
}
