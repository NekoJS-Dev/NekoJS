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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
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

    public static void validate(Path file, String source) {
        if (file == null || source == null || source.isEmpty()) return;
        Map<String, ScriptBindingSchema.BindingMembers> schema = ScriptBindingSchema.schemaForPath(file);
        if (schema.isEmpty()) return;

        ValNode.Block ast;
        try {
            ast = ValParser.parse(source);
        } catch (Throwable e) {
            // ValParser 是简化解析器：脚本含其不支持的语法时无法静态检查。
            // 不静默——至少记一条日志，让用户知道该文件没有获得 preflight 保护。
            com.tkisor.nekojs.NekoJS.LOGGER.warn(
                    "Event preflight skipped for {}: source not parseable by ValParser ({}: {})",
                    file, e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        Set<String> reported = new HashSet<>();
        scanNode(ast, schema, file, source, reported);
    }

    private static void scanNode(ValNode node,
                                 Map<String, ScriptBindingSchema.BindingMembers> schema,
                                 Path file, String source, Set<String> reported) {
        if (node == null) return;
        if (node instanceof ValNode.CallExpr call) {
            if (call.callee() instanceof ValNode.MemberAccess access
                    && access.object() instanceof ValNode.Identifier ident
                    && schema.containsKey(ident.name())) {
                checkCallbackArgs(call, ident.name(), schema, file, source, reported);
            }
        }
        // DEFECT-D9: recurse into ALL block-bearing and expression-bearing children.
        // Previously only Block.stmts, ArrowFunc.body, and CallExpr.args were visited,
        // which missed callbacks nested inside function declarations, nested member
        // accesses, computed keys, variable initializers, and call callees. ValNode's
        // sealed hierarchy only exposes these node types (no if/for/while/try — the
        // simplified ValParser does not model them), so we exhaustively visit every
        // child the hierarchy actually defines.
        if (node instanceof ValNode.Block b) {
            for (ValNode s : b.stmts()) scanNode(s, schema, file, source, reported);
        } else if (node instanceof ValNode.ArrowFunc af) {
            for (ValNode s : af.body()) scanNode(s, schema, file, source, reported);
        } else if (node instanceof ValNode.FuncDecl fd) {
            for (ValNode s : fd.body()) scanNode(s, schema, file, source, reported);
        } else if (node instanceof ValNode.CallExpr call) {
            scanNode(call.callee(), schema, file, source, reported);
            for (ValNode a : call.args()) scanNode(a, schema, file, source, reported);
        } else if (node instanceof ValNode.MemberAccess access) {
            scanNode(access.object(), schema, file, source, reported);
        } else if (node instanceof ValNode.ComputedMemberAccess computed) {
            scanNode(computed.object(), schema, file, source, reported);
            scanNode(computed.key(), schema, file, source, reported);
        } else if (node instanceof ValNode.VarDecl decl) {
            scanNode(decl.init(), schema, file, source, reported);
        }
    }

    /**
     * 事件回调成员检查：反射 schema 与平台反射**并集**。
     *
     * <p>反射字段（{@code ManagedCallbackSchemaRegistry}，由 {@code EventContractReflector}
     * 从运行时 {@code EventGroup} 派生）是跨平台承诺的 payload 视图：即使某平台事件类
     * 反射缺该成员也放行。平台反射集合（{@code EventSchemaRegistry} →
     * {@link JavaMemberIndex}）兜底覆盖事件类全部可见成员（方法名 + getter 属性名 + 字段名），
     * 保证 {@code e.getServer()} 等方法形式不误报。
     *
     * <p>回调参数不只出现在事件注册上：绑定的普通方法也收回调（如
     * {@code DynamicRegistry.item(id, b => b.maxStackSize(64))}）。这类「非事件回调」按
     * 绑定方法签名的函数式参数（{@code Consumer<ItemBuilder>} 的第一个类型实参）登记形参类型；
     * 签名推不出来（非泛型、类型实参非具体类、找不到匹配重载）就整段跳过——预检的默认答案
     * 必须是「不知道就别报」，否则 builder 回调一用就误报（2026-08-24 日志：
     * {@code 'maxStackSize' not in DynamicRegistry}）。
     */
    private static void checkCallbackArgs(ValNode.CallExpr call, String group,
                                          Map<String, ScriptBindingSchema.BindingMembers> schema,
                                          Path file, String src, Set<String> reported) {
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
                    callbackParamTypes, file, src, reported);
            for (ValNode s : body) {
                resolveStatement(s, params.getFirst(), env, context);
            }
        }
    }

    /**
     * 非事件回调的形参类型：在绑定 {@code valueClasses} 上找与方法名、调用参数数匹配的重载，
     * 取该参数位声明的泛型类型；若是函数式接口（{@code Consumer<X>}/{@code Function<X,?>}…），
     * 回调第 j 个形参 = 第 j 个类型实参。只接受具体类实参（通配/类型变量推不出唯一答案），
     * 任一环节失败即返回空表——调用方据此跳过整段检查。
     */
    private static Map<String, Set<Class<?>>> callbackParamTypes(
            Map<String, ScriptBindingSchema.BindingMembers> schema, String group, String member,
            int argCount, int argIndex, List<String> params) {
        ScriptBindingSchema.BindingMembers bm = schema.get(group);
        if (bm == null || argIndex < 0) return Map.of();
        Map<String, Set<Class<?>>> out = new HashMap<>();
        for (Class<?> cls : bm.valueClasses()) {
            List<Method> candidates = JavaMemberIndex.exposedMembersOf(cls).methods().get(member);
            if (candidates == null) continue;
            for (Method m : candidates) {
                int fixed = m.getParameterCount();
                if (m.isVarArgs() ? argCount < fixed - 1 : argCount != fixed) continue;
                if (argIndex >= fixed) continue;
                Type declared = m.getGenericParameterTypes()[argIndex];
                if (!(declared instanceof ParameterizedType pt)
                        || !(pt.getRawType() instanceof Class<?> raw)
                        || !isFunctionalShape(raw)) continue;
                Type[] typeArgs = pt.getActualTypeArguments();
                for (int j = 0; j < params.size() && j < typeArgs.length; j++) {
                    if (typeArgs[j] instanceof Class<?> argClass) {
                        out.computeIfAbsent(params.get(j), k -> new LinkedHashSet<>()).add(argClass);
                    }
                }
            }
        }
        return out;
    }

    private static boolean isFunctionalShape(Class<?> raw) {
        return raw.isAnnotationPresent(FunctionalInterface.class)
                || raw.getName().startsWith("java.util.function.");
    }

    private static String classNames(Set<Class<?>> classes) {
        StringJoiner joiner = new StringJoiner("/");
        for (Class<?> cls : classes) joiner.add(cls.getSimpleName());
        return joiner.toString();
    }

    // ==================== 词法环境：const 别名与常量字符串 ====================

    /** 块级词法绑定：仅 const 且初始化可直接求值（payload 别名 / 字符串字面量 / 变量转发）。 */
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

    /** 解析 computed key：字符串字面量或 const 字符串变量；无法静态确定返回 null。 */
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

    // ==================== 表达式类型流 ====================

    /** 表达式解析结果：已知 Java 类型集合（空 = 无具体类型，但不误报）、API 成员名集合、或未知。 */
    private sealed interface ResolvedValue permits ResolvedValue.JavaTypes, ResolvedValue.ApiMembers, ResolvedValue.WideApiMembers, ResolvedValue.Unknown {
        record JavaTypes(Set<Class<?>> classes) implements ResolvedValue {}
        record ApiMembers(Set<String> memberNames) implements ResolvedValue {}
        /** 根成员并集（契约字段 + 反射成员）：成员名检查，类型不深入。 */
        record WideApiMembers(Set<String> memberNames) implements ResolvedValue {}
        record Unknown() implements ResolvedValue {}
    }

    /** 校验上下文：契约 schema + 平台事件类 + 非事件回调的形参类型 + 报告通道。 */
    private static final class TypeContext {
        final ManagedCallbackSchemaRegistry.CallbackSchema managed;
        final Class<?> eventClass;
        final String group;
        final String displayName;
        /** 非事件回调形参名 → 签名推导的类型集合（事件路径恒为空表）。 */
        final Map<String, Set<Class<?>>> callbackParamTypes;
        final Path file;
        final String source;
        final Set<String> reported;

        TypeContext(ManagedCallbackSchemaRegistry.CallbackSchema managed, Class<?> eventClass,
                    String group, String displayName, Map<String, Set<Class<?>>> callbackParamTypes,
                    Path file, String source, Set<String> reported) {
            this.managed = managed;
            this.eventClass = eventClass;
            this.group = group;
            this.displayName = displayName;
            this.callbackParamTypes = callbackParamTypes;
            this.file = file;
            this.source = source;
            this.reported = reported;
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

    /** 解析表达式并返回其类型流；同时报告其中未知成员的访问。 */
    private static ResolvedValue resolveExpr(ValNode node, String param, Env env, TypeContext context) {
        if (node instanceof ValNode.Identifier id) {
            // 非事件回调的形参（签名推导）：优先于事件根类型——两条路径不会同时出现
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
                // 运行时动态 key：无法静态证明，不报错不误报
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
                    // 运行时动态 key：无法静态证明，不报错不误报
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
        Set<Class<?>> classes = new LinkedHashSet<>();
        if (context.eventClass != null && context.eventClass != Object.class) {
            classes.add(context.eventClass);
        }
        Set<String> apiMembers = new LinkedHashSet<>();
        if (context.managed != null) {
            apiMembers.addAll(context.managed.memberNames());
        }
        // 两个来源都空 = 该回调形参类型完全未知：必须返回 Unknown。空集 WideApiMembers 对任何
        // 成员都会「不包含」成立，等于把回调里的每次成员访问都判成错报（这正是
        // DynamicRegistry builder 回调误报的根因之一）。
        if (classes.isEmpty() && apiMembers.isEmpty()) {
            return new ResolvedValue.Unknown();
        }
        if (!classes.isEmpty() && apiMembers.isEmpty()) {
            return new ResolvedValue.JavaTypes(classes);
        }
        if (classes.isEmpty()) {
            return new ResolvedValue.ApiMembers(apiMembers);
        }
        // 并集：契约字段 + 反射成员。契约字段作为 API 成员放行，反射成员由 Java 类检查。
        // 两者都出现时用"宽集合"检查（根成员检查的既有语义）。
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
            // API 符号类型无更深类型信息：不深入链式
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
            // 契约字段类型信息在 fieldTypes 中；此处 root 并集场景保守返回 unknown
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
            ScriptErrorReporter.recordCallbackError(type, "event-cb-preflight",
                    new NekoEsmLinkException(new NekoEsmDiagnostic(context.file, new NekoEsmSpan(offset, offset), lc[0], lc[1], msg)));
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
