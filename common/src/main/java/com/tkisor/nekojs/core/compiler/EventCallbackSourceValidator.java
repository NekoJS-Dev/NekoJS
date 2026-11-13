package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventSchemaRegistry;
import com.tkisor.nekojs.api.event.ManagedCallbackSchemaRegistry;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;
import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public final class EventCallbackSourceValidator {

    private EventCallbackSourceValidator() {}

    public static void validate(Path file, String source, ScriptBindingSchema.View view) {
        validate(file, source, view, new ScriptSourcePreflight.ParsedSource(source));
    }

    static void validate(Path file, String source, ScriptBindingSchema.View view,
                         ScriptSourcePreflight.ParsedSource parsed) {
        if (file == null || source == null || source.isEmpty()) return;
        Map<String, ScriptBindingSchema.BindingMembers> schema = ScriptBindingSchema.schemaForPath(file, view);
        if (schema.isEmpty()) return;

        ValNode.Block ast;
        try {
            ast = parsed.ast();
        } catch (Throwable e) {
            // ValParser is a simplified parser: static checking is not possible if the script
            // contains unsupported syntax. Do not silent—log at least one warning so the user
            // knows this file is not protected by preflight.
            com.tkisor.nekojs.NekoJS.LOGGER.warn(
                    "Event preflight skipped for {}: source not parseable by ValParser ({}: {})",
                    file, e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        Set<String> reported = new HashSet<>();
        scanNode(ast, schema, file, source, reported, view);
    }

    private static void scanNode(ValNode node,
                                  Map<String, ScriptBindingSchema.BindingMembers> schema,
                                  Path file, String source, Set<String> reported,
                                  ScriptBindingSchema.View view) {
        if (node == null) return;
        if (node instanceof ValNode.CallExpr call) {
            if (call.callee() instanceof ValNode.MemberAccess access
                    && access.object() instanceof ValNode.Identifier ident
                    && schema.containsKey(ident.name())) {
                        checkCallbackArgs(call, ident.name(), schema, file, source, reported, view);
            }
        }
        // DEFECT-D9: recurse into ALL block-bearing and expression-bearing children.
        // Previously only Block.stmts, ArrowFunc.body, and CallExpr.args were visited,
        // which missed callbacks nested inside function declarations, nested member
        // accesses, computed keys, variable initializers, and call callees. ValNode's
        // sealed hierarchy only exposes these node types (no if/for/while/try—the
        // simplified ValParser does not model them), so we exhaustively visit every
        // child the hierarchy actually defines.
        if (node instanceof ValNode.Block b) {
            for (ValNode s : b.stmts()) scanNode(s, schema, file, source, reported, view);
        } else if (node instanceof ValNode.ArrowFunc af) {
            for (ValNode s : af.body()) scanNode(s, schema, file, source, reported, view);
        } else if (node instanceof ValNode.FuncDecl fd) {
            for (ValNode s : fd.body()) scanNode(s, schema, file, source, reported, view);
        } else if (node instanceof ValNode.CallExpr call) {
            scanNode(call.callee(), schema, file, source, reported, view);
            for (ValNode a : call.args()) scanNode(a, schema, file, source, reported, view);
        } else if (node instanceof ValNode.MemberAccess access) {
            scanNode(access.object(), schema, file, source, reported, view);
        } else if (node instanceof ValNode.ComputedMemberAccess computed) {
            scanNode(computed.object(), schema, file, source, reported, view);
            scanNode(computed.key(), schema, file, source, reported, view);
        } else if (node instanceof ValNode.VarDecl decl) {
            scanNode(decl.init(), schema, file, source, reported, view);
        }
    }

    /**
     * Checks the members an event callback uses, taking the union of the reflected schema and
     * platform reflection.
     *
     * <p>Reflected fields ({@link ManagedCallbackSchemaRegistry}, derived from the runtime
     * {@code EventGroup} by {@code EventContractReflector}) are the payload views every platform
     * promises, so they are allowed even when a specific platform event class fails to reflect
     * the member. Platform reflection collections ({@code EventSchemaRegistry} →
     * {@link JavaMemberIndex}) cover every visible member of the event class (method names, getter
     * property names and field names), so method forms such as {@code e.getServer()} are not
     * reported as errors.
     *
     * <p>Bindings can also accept callbacks, such as builder configuration methods. Their input
     * types are derived from the functional interface's abstract method, including generic
     * substitutions. Only inputs with known concrete types are checked; unresolved inputs
     * remain unknown rather than being inferred from the callback's return type.
     */
    private static void checkCallbackArgs(ValNode.CallExpr call, String group,
                                          Map<String, ScriptBindingSchema.BindingMembers> schema,
                                          Path file, String src, Set<String> reported,
                                          ScriptBindingSchema.View view) {
        String member = ((ValNode.MemberAccess) call.callee()).member();
        for (int argIndex = 0; argIndex < call.args().size(); argIndex++) {
            ValNode arg = call.args().get(argIndex);
            List<String> params = null;
            List<ValNode> body = null;
            if (arg instanceof ValNode.ArrowFunc af) { params = af.params(); body = af.body(); }
            else if (arg instanceof ValNode.FuncDecl fd) { params = fd.params(); body = fd.body(); }
            if (params == null || params.isEmpty()) continue;

            ManagedCallbackSchemaRegistry.CallbackSchema managed =
                    ManagedCallbackSchemaRegistry.resolve(group, member);
            Class<?> eventClass = EventSchemaRegistry.resolve(group, member);

            String displayName;
            Map<String, Set<Class<?>>> callbackParamTypes = Map.of();
            if (managed != null) {
                displayName = managed.displayName();
            } else if (eventClass != null && eventClass != Object.class) {
                displayName = eventClass.getSimpleName();
            } else {
                callbackParamTypes = callbackParamTypes(schema, group, member, call.args().size(), argIndex, params);
                if (callbackParamTypes.isEmpty()) continue;
                Set<Class<?>> first = callbackParamTypes.values().iterator().next();
                displayName = classNames(first);
            }

            Env env = new Env();
            for (ValNode s : body) {
                collectDecls(s, params.getFirst(), env);
            }
            TypeContext context = new TypeContext(managed, eventClass, group, displayName,
                    callbackParamTypes, file, src, reported, view);
            for (ValNode s : body) {
                resolveStatement(s, params.getFirst(), env, context);
            }
        }
    }

    /** Derives callback inputs from the native SAM signature, substituting declared interface arguments. */
    private static Map<String, Set<Class<?>>> callbackParamTypes(
            Map<String, ScriptBindingSchema.BindingMembers> schema, String group, String member,
            int argCount, int argIndex, List<String> params) {
        ScriptBindingSchema.BindingMembers binding = schema.get(group);
        Map<String, Set<Class<?>>> out = new HashMap<>();
        for (Class<?> valueClass : binding.valueClasses()) {
            List<Method> candidates = JavaMemberIndex.exposedMembersOf(valueClass).methods().get(member);
            if (candidates == null) continue;
            for (Method candidate : candidates) {
                int fixed = candidate.getParameterCount();
                if (candidate.isVarArgs() ? argCount < fixed - 1 : argCount != fixed) continue;
                if (argIndex >= fixed) continue;
                Type declared = candidate.getGenericParameterTypes()[argIndex];
                FunctionalInterfaceResolver.Signature sam = FunctionalInterfaceResolver.resolve(declared);
                if (sam == null) continue;
                for (int index = 0; index < params.size() && index < sam.parameterTypes().size(); index++) {
                    Class<?> inputClass = FunctionalInterfaceResolver.rawClass(sam.parameterTypes().get(index));
                    if (inputClass != null && inputClass != Object.class) {
                        out.computeIfAbsent(params.get(index), ignored -> new LinkedHashSet<>()).add(inputClass);
                    }
                }
            }
        }
        return out;
    }

    private static String classNames(Set<Class<?>> classes) {
        StringJoiner joiner = new StringJoiner("/");
        for (Class<?> cls : classes) joiner.add(cls.getSimpleName());
        return joiner.toString();
    }

    // ==================== Lexical environment: const aliases and constant strings ====================

    /** Block-scoped lexical binding: only const bindings whose initializer can be evaluated directly
     *  (payload alias, string literal, or forwarding another variable). */
    private sealed interface Binding permits Binding.Alias, Binding.ConstantString, Binding.Unknown {
        record Alias(String target) implements Binding {}
        record ConstantString(String value) implements Binding {}
        record Unknown() implements Binding {}
    }

    private static final class Env {
        private final Map<String, Binding> bindings = new HashMap<>();

        Binding lookup(String name) {
            return bindings.get(name);
        }

        void put(String name, Binding binding) {
            bindings.put(name, binding);
        }
    }

    private static void collectDecls(ValNode node, String param, Env env) {
        if (node instanceof ValNode.VarDecl decl) {
            if (decl.kind() == ValNode.DeclarationKind.CONST) {
                env.put(decl.name(), constBindingOf(decl.init()));
            } else {
                env.put(decl.name(), new Binding.Unknown());
            }
        } else if (node instanceof ValNode.Block b) {
            for (ValNode s : b.stmts()) collectDecls(s, param, env);
        } else if (node instanceof ValNode.CallExpr call) {
            for (ValNode a : call.args()) collectDecls(a, param, env);
        } else if (node instanceof ValNode.ArrowFunc af) {
            for (ValNode s : af.body()) collectDecls(s, param, env);
        }
    }

    private static Binding constBindingOf(ValNode init) {
        if (init instanceof ValNode.Identifier id) {
            return new Binding.Alias(id.name());
        }
        if (init instanceof ValNode.StringLiteral literal) {
            return new Binding.ConstantString(literal.value());
        }
        return new Binding.Unknown();
    }

    /** Resolves a computed key to a string literal or a const string variable. Returns null when
     *  the key cannot be determined statically. */
    private static String resolveStringKey(ValNode key, Env env) {
        if (key instanceof ValNode.StringLiteral literal) {
            return literal.value();
        }
        if (key instanceof ValNode.Identifier id) {
            Binding b = env.lookup(id.name());
            if (b instanceof Binding.ConstantString cs) {
                return cs.value();
            }
        }
        return null;
    }

    // ==================== Expression type flow ====================

    /** Result of resolving an expression: a set of known Java types (empty means no concrete type,
     *  and nothing is reported), a set of API member names, or unknown. */
    private sealed interface ResolvedValue permits ResolvedValue.JavaTypes, ResolvedValue.ApiMembers, ResolvedValue.WideApiMembers, ResolvedValue.Unknown {
        record JavaTypes(Set<Class<?>> classes) implements ResolvedValue {}
        record ApiMembers(Set<String> memberNames) implements ResolvedValue {}
        /** Union of root members (contract fields plus reflected members). Checked by member name only;
         *  types are not resolved further. */
        record WideApiMembers(Set<String> memberNames) implements ResolvedValue {}
        record Unknown() implements ResolvedValue {}
    }

    /** Check context: the contract schema, the platform event class, parameter types of non-event
     *  callbacks, and the report sink. */
    private static final class TypeContext {
        final ManagedCallbackSchemaRegistry.CallbackSchema managed;
        final Class<?> eventClass;
        final String group;
        final String displayName;
        /** Non-event callback parameter name -> types derived from its signature. Always an empty map
         *  on the event path. */
        final Map<String, Set<Class<?>>> callbackParamTypes;
        final Path file;
        final String source;
        final Set<String> reported;
        final ScriptBindingSchema.View schemaView;

        TypeContext(ManagedCallbackSchemaRegistry.CallbackSchema managed, Class<?> eventClass,
                     String group, String displayName, Map<String, Set<Class<?>>> callbackParamTypes,
                     Path file, String source, Set<String> reported, ScriptBindingSchema.View schemaView) {
            this.managed = managed;
            this.eventClass = eventClass;
            this.group = group;
            this.displayName = displayName;
            this.callbackParamTypes = callbackParamTypes;
            this.file = file;
            this.source = source;
            this.reported = reported;
            this.schemaView = schemaView;
        }
    }

    private static void resolveStatement(ValNode node, String param, Env env, TypeContext context) {
        if (node == null) return;
        resolveExpr(node, param, env, context);
        // DEFECT-D9: recurse into every child-bearing node so callbacks nested inside
        // function declarations, member accesses, computed keys, variable initializers,
        // and call callees are also validated — not just Block/CallExpr/ArrowFunc.
        if (node instanceof ValNode.Block b) {
            for (ValNode s : b.stmts()) resolveStatement(s, param, env, context);
        } else if (node instanceof ValNode.CallExpr call) {
            resolveStatement(call.callee(), param, env, context);
            for (ValNode a : call.args()) resolveStatement(a, param, env, context);
        } else if (node instanceof ValNode.ArrowFunc af) {
            for (ValNode s : af.body()) resolveStatement(s, param, env, context);
        } else if (node instanceof ValNode.FuncDecl fd) {
            for (ValNode s : fd.body()) resolveStatement(s, param, env, context);
        } else if (node instanceof ValNode.MemberAccess access) {
            resolveStatement(access.object(), param, env, context);
        } else if (node instanceof ValNode.ComputedMemberAccess computed) {
            resolveStatement(computed.object(), param, env, context);
            resolveStatement(computed.key(), param, env, context);
        } else if (node instanceof ValNode.VarDecl decl) {
            resolveStatement(decl.init(), param, env, context);
        }
    }

    /** Resolves an expression and returns its type flow, reporting any unknown member access found
     *  in it. */
    private static ResolvedValue resolveExpr(ValNode node, String param, Env env, TypeContext context) {
        if (node instanceof ValNode.Identifier id) {
            // Parameter of a non-event callback (derived from the signature). Checked before the event root
            // type; the two paths never occur together
            Set<Class<?>> typed = context.callbackParamTypes.get(id.name());
            if (typed != null && !typed.isEmpty()) {
                return new ResolvedValue.JavaTypes(typed);
            }
            if (param.equals(id.name())) {
                return rootValue(context);
            }
            Binding b = env.lookup(id.name());
            if (b instanceof Binding.Alias alias) {
                if (alias.target().equals(param)) {
                    return rootValue(context);
                }
                Set<Class<?>> aliasTyped = context.callbackParamTypes.get(alias.target());
                if (aliasTyped != null && !aliasTyped.isEmpty()) {
                    return new ResolvedValue.JavaTypes(aliasTyped);
                }
                return new ResolvedValue.Unknown();
            }
            return new ResolvedValue.Unknown();
        }
        if (node instanceof ValNode.StringLiteral || node instanceof ValNode.ArrowFunc || node instanceof ValNode.FuncDecl) {
            return new ResolvedValue.Unknown();
        }
        if (node instanceof ValNode.MemberAccess access) {
            ResolvedValue obj = resolveExpr(access.object(), param, env, context);
            return resolveMemberAccess(obj, access.member(), access.start(), access.bracket(), context);
        }
        if (node instanceof ValNode.ComputedMemberAccess computed) {
            String key = resolveStringKey(computed.key(), env);
            if (key == null) {
                // Dynamic key at runtime: nothing can be proven statically, so nothing is reported
                return new ResolvedValue.Unknown();
            }
            ResolvedValue obj = resolveExpr(computed.object(), param, env, context);
            return resolveMemberAccess(obj, key, computed.start(), true, context);
        }
        if (node instanceof ValNode.CallExpr call) {
            if (call.callee() instanceof ValNode.MemberAccess access) {
                ResolvedValue obj = resolveExpr(access.object(), param, env, context);
                if (obj instanceof ResolvedValue.Unknown) {
                    return new ResolvedValue.Unknown();
                }
                return resolveCall(obj, access.member(), call.args().size(), access.start(), context);
            }
            if (call.callee() instanceof ValNode.ComputedMemberAccess computed) {
                String key = resolveStringKey(computed.key(), env);
                if (key == null) {
                    // Dynamic key at runtime: nothing can be proven statically, so nothing is reported
                    return new ResolvedValue.Unknown();
                }
                ResolvedValue obj = resolveExpr(computed.object(), param, env, context);
                if (obj instanceof ResolvedValue.Unknown) {
                    return new ResolvedValue.Unknown();
                }
                return resolveCall(obj, key, call.args().size(), computed.start(), context);
            }
            return new ResolvedValue.Unknown();
        }
        if (node instanceof ValNode.VarDecl decl) {
            if (decl.init() != null) {
                return resolveExpr(decl.init(), param, env, context);
            }
            return new ResolvedValue.Unknown();
        }
        if (node instanceof ValNode.Block b) {
            for (ValNode s : b.stmts()) resolveStatement(s, param, env, context);
            return new ResolvedValue.Unknown();
        }
        return new ResolvedValue.Unknown();
    }

    private static ResolvedValue rootValue(TypeContext context) {
        // Members of a ProxyObject payload are dynamic, for example the registry_types sugar methods of
        // RegistryEventJS. Reflection only sees the interface methods, and the member table the
        // contract reflector derives from it is just as incomplete, so checking against the static
        // table would report every sugar method as "not in RegistryEventJS". Unknown is returned for
        // the whole value when dynamic members cannot be known statically (when in doubt, do not
        // report); a runtime getMember failure carries its own member catalog, so message quality
        // does not drop.
        if (context.eventClass != null
                && graal.graalvm.polyglot.proxy.ProxyObject.class.isAssignableFrom(context.eventClass)) {
            return new ResolvedValue.Unknown();
        }
        Set<Class<?>> classes = new LinkedHashSet<>();
        if (context.eventClass != null && context.eventClass != Object.class) {
            classes.add(context.eventClass);
        }
        Set<String> apiMembers = new LinkedHashSet<>();
        if (context.managed != null) {
            apiMembers.addAll(context.managed.memberNames());
        }
        // Both sources empty means the callback parameter type is completely unknown, so Unknown must be
        // returned. An empty WideApiMembers set contains no member at all, so it would turn every
        // member access in the callback into an error report; that is one of the root causes of the
        // false reports on DynamicRegistry builder callbacks.
        if (classes.isEmpty() && apiMembers.isEmpty()) {
            return new ResolvedValue.Unknown();
        }
        if (!classes.isEmpty() && apiMembers.isEmpty()) {
            return new ResolvedValue.JavaTypes(classes);
        }
        if (classes.isEmpty()) {
            return new ResolvedValue.ApiMembers(apiMembers);
        }
        // Union of contract fields and reflected members. Contract fields pass as API members, and
        // reflected members are checked by Java class. When both are present the check uses the wide
        // set, matching how root members are checked.
        Set<String> wide = new LinkedHashSet<>(apiMembers);
        for (Class<?> c : classes) {
            wide.addAll(JavaMemberIndex.propertyMembersOf(c));
        }
        return new ResolvedValue.WideApiMembers(wide);
    }

    private static ResolvedValue resolveMemberAccess(ResolvedValue obj, String member, int offset, boolean bracket, TypeContext context) {
        if (obj instanceof ResolvedValue.JavaTypes javaTypes) {
            Set<Class<?>> classes = javaTypes.classes();
            if (classes.isEmpty()) {
                return new ResolvedValue.Unknown();
            }
            Set<String> known = new LinkedHashSet<>();
            List<Class<?>> nextClasses = new ArrayList<>();
            boolean anyKnown = false;
            for (Class<?> c : classes) {
                JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(c);
                Set<String> members = new LinkedHashSet<>(exposed.methods().keySet());
                members.addAll(exposed.propertyGetters().keySet());
                members.addAll(exposed.fields().keySet());
                known.addAll(members);
                if (exposed.hasMember(member)) {
                    anyKnown = true;
                    for (Type type : exposed.propertyTypes(member)) {
                        nextClasses.addAll(JavaMemberIndex.typeClasses(type));
                    }
                }
            }
            if (!anyKnown) {
                if (!context.reported.contains(member)) {
                    context.reported.add(member);
                    String suggest = JavaMemberIndex.suggestMember(known, member);
                    report(context, offset, context.group + " callback: '" + member
                            + "' not in " + context.displayName
                            + (suggest != null ? ". Did you mean '" + suggest + "'?" : ""));
                }
                return new ResolvedValue.Unknown();
            }
            return new ResolvedValue.JavaTypes(new LinkedHashSet<>(nextClasses));
        }
        if (obj instanceof ResolvedValue.ApiMembers apiMembers) {
            if (!apiMembers.memberNames().contains(member)) {
                if (!context.reported.contains(member)) {
                    context.reported.add(member);
                    String suggest = JavaMemberIndex.suggestMember(apiMembers.memberNames(), member);
                    report(context, offset, context.group + " callback: '" + member
                            + "' not in " + context.displayName
                            + (suggest != null ? ". Did you mean '" + suggest + "'?" : ""));
                }
                return new ResolvedValue.Unknown();
            }
            // An API symbol type carries no deeper type information, so chained access is not resolved
            return new ResolvedValue.Unknown();
        }
        if (obj instanceof ResolvedValue.WideApiMembers wide) {
            if (!wide.memberNames().contains(member)) {
                if (!context.reported.contains(member)) {
                    context.reported.add(member);
                    String suggest = JavaMemberIndex.suggestMember(wide.memberNames(), member);
                    report(context, offset, context.group + " callback: '" + member
                            + "' not in " + context.displayName
                            + (suggest != null ? ". Did you mean '" + suggest + "'?" : ""));
                }
                return new ResolvedValue.Unknown();
            }
            // Contract field types live in fieldTypes; the root union path has that detail, so it returns
            // unknown
            return new ResolvedValue.Unknown();
        }
        return new ResolvedValue.Unknown();
    }

    private static ResolvedValue resolveCall(ResolvedValue obj, String member, int argCount, int offset, TypeContext context) {
        if (obj instanceof ResolvedValue.JavaTypes javaTypes) {
            Set<Class<?>> classes = javaTypes.classes();
            if (classes.isEmpty()) {
                return new ResolvedValue.Unknown();
            }
            Set<String> known = new LinkedHashSet<>();
            List<Class<?>> nextClasses = new ArrayList<>();
            boolean anyKnown = false;
            for (Class<?> c : classes) {
                JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(c);
                known.addAll(exposed.methods().keySet());
                List<Type> returns = exposed.callReturnTypes(member, argCount);
                if (!returns.isEmpty()) {
                    anyKnown = true;
                    for (Type t : returns) {
                        nextClasses.addAll(JavaMemberIndex.typeClasses(t));
                    }
                }
            }
            if (!anyKnown) {
                if (!context.reported.contains(member)) {
                    context.reported.add(member);
                    String suggest = JavaMemberIndex.suggestMember(known, member);
                    report(context, offset, context.group + " callback: '" + member
                            + "' not in " + context.displayName
                            + (suggest != null ? ". Did you mean '" + suggest + "'?" : ""));
                }
                return new ResolvedValue.Unknown();
            }
            return new ResolvedValue.JavaTypes(new LinkedHashSet<>(nextClasses));
        }
        if (obj instanceof ResolvedValue.ApiMembers apiMembers) {
            if (!apiMembers.memberNames().contains(member)) {
                if (!context.reported.contains(member)) {
                    context.reported.add(member);
                    report(context, offset, context.group + " callback: '" + member
                            + "' not in " + context.displayName);
                }
                return new ResolvedValue.Unknown();
            }
            return new ResolvedValue.Unknown();
        }
        if (obj instanceof ResolvedValue.WideApiMembers wide) {
            if (!wide.memberNames().contains(member)) {
                if (!context.reported.contains(member)) {
                    context.reported.add(member);
                    report(context, offset, context.group + " callback: '" + member
                            + "' not in " + context.displayName);
                }
                return new ResolvedValue.Unknown();
            }
            return new ResolvedValue.Unknown();
        }
        return new ResolvedValue.Unknown();
    }

    private static void report(TypeContext context, int offset, String msg) {
        try {
            int[] lc = lc(context.source, offset);
            ScriptType type = ScriptBindingSchema.inferType(context.file);
            NekoEsmLinkException error = new NekoEsmLinkException(new NekoEsmDiagnostic(
                    context.file, new NekoEsmSpan(offset, offset), lc[0], lc[1], msg));
            if (context.schemaView == null) {
                ScriptErrorReporter.recordCallbackError(type, "event-cb-preflight", error);
            } else {
                context.schemaView.report(type, "event-cb-preflight", error);
            }
        } catch (Throwable ignored) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn("Event callback preflight report failed", ignored);
        }
    }

    private static int[] lc(String src, int o) {
        int c = Math.min(Math.max(o, 0), src.length());
        String p = NekoSourceLexerBase.position(src, src.length(), c);
        int col = p.indexOf(':');
        return new int[] {Integer.parseInt(p.substring(0, col)), Integer.parseInt(p.substring(col + 1))};
    }
}
