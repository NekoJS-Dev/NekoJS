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

import java.nio.file.Path;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                checkCallbackArgs(call, ident.name(), file, source, reported);
            }
        }
        // recurse
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) scanNode(s, schema, file, source, reported);
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) scanNode(s, schema, file, source, reported);
        if (node instanceof ValNode.CallExpr call) {
            for (ValNode a : call.args()) scanNode(a, schema, file, source, reported);
        }
    }

    /**
     * 事件回调成员检查：契约 schema 与平台反射**并集**。
     *
     * <p>契约字段（{@code ManagedCallbackSchemaRegistry}，来自 {@code portable-core} events）
     * 是跨平台承诺：即使某平台事件类反射缺该成员也放行。反射集合
     * （{@code EventSchemaRegistry} → {@link JavaMemberIndex}）兜底覆盖事件类全部
     * 可见成员（方法名 + getter 属性名 + 字段名），保证 {@code e.getServer()} 等方法形式不误报。
     * 两者缺一不可：只查契约会在平台实现缺口处漏检方法形式，只查反射会因平台差异漏掉承诺字段。
     */
    private static void checkCallbackArgs(ValNode.CallExpr call, String group,
                                          Path file, String src, Set<String> reported) {
        for (ValNode arg : call.args()) {
            List<String> params = null;
            List<ValNode> body = null;
            if (arg instanceof ValNode.ArrowFunc af) { params = af.params(); body = af.body(); }
            else if (arg instanceof ValNode.FuncDecl fd) { params = fd.params(); body = fd.body(); }
            if (params == null || params.isEmpty()) continue;

            ManagedCallbackSchemaRegistry.CallbackSchema managed =
                    ManagedCallbackSchemaRegistry.resolve(group, ((ValNode.MemberAccess) call.callee()).member());
            Class<?> eventClass = EventSchemaRegistry.resolve(group, ((ValNode.MemberAccess) call.callee()).member());

            String displayName;
            if (managed != null) {
                displayName = managed.displayName();
            } else if (eventClass != null && eventClass != Object.class) {
                displayName = eventClass.getSimpleName();
            } else {
                displayName = group;
            }

            Env env = new Env();
            for (ValNode s : body) {
                collectDecls(s, params.getFirst(), env);
            }
            TypeContext context = new TypeContext(managed, eventClass, group, displayName, file, src, reported);
            for (ValNode s : body) {
                resolveStatement(s, params.getFirst(), env, context);
            }
        }
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

    /** 校验上下文：契约 schema + 平台事件类 + 报告通道。 */
    private static final class TypeContext {
        final ManagedCallbackSchemaRegistry.CallbackSchema managed;
        final Class<?> eventClass;
        final String group;
        final String displayName;
        final Path file;
        final String source;
        final Set<String> reported;

        TypeContext(ManagedCallbackSchemaRegistry.CallbackSchema managed, Class<?> eventClass,
                    String group, String displayName, Path file, String source, Set<String> reported) {
            this.managed = managed;
            this.eventClass = eventClass;
            this.group = group;
            this.displayName = displayName;
            this.file = file;
            this.source = source;
            this.reported = reported;
        }
    }

    private static void resolveStatement(ValNode node, String param, Env env, TypeContext context) {
        if (node == null) return;
        resolveExpr(node, param, env, context);
        if (node instanceof ValNode.Block b) {
            for (ValNode s : b.stmts()) resolveStatement(s, param, env, context);
        } else if (node instanceof ValNode.CallExpr call) {
            for (ValNode a : call.args()) resolveStatement(a, param, env, context);
        } else if (node instanceof ValNode.ArrowFunc af) {
            for (ValNode s : af.body()) resolveStatement(s, param, env, context);
        }
    }

    /** 解析表达式并返回其类型流；同时报告其中未知成员的访问。 */
    private static ResolvedValue resolveExpr(ValNode node, String param, Env env, TypeContext context) {
        if (node instanceof ValNode.Identifier id) {
            if (param.equals(id.name())) {
                return rootValue(context);
            }
            Binding b = env.lookup(id.name());
            if (b instanceof Binding.Alias alias) {
                return alias.target().equals(param) ? rootValue(context) : new ResolvedValue.Unknown();
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
        if (!classes.isEmpty() && apiMembers.isEmpty()) {
            return new ResolvedValue.JavaTypes(classes);
        }
        if (classes.isEmpty() && !apiMembers.isEmpty()) {
            return new ResolvedValue.ApiMembers(apiMembers);
        }
        // 并集：契约字段 + 反射成员。契约字段作为 API 成员放行，反射成员由 Java 类检查。
        // 两者都出现时用"宽集合"检查（根成员检查的既有语义）。
        Set<String> wide = new LinkedHashSet<>(apiMembers);
        if (!classes.isEmpty()) {
            for (Class<?> c : classes) {
                wide.addAll(JavaMemberIndex.propertyMembersOf(c));
            }
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
        } catch (Throwable ignored) {}
    }

    private static int[] lc(String src, int o) {
        int c = Math.min(Math.max(o, 0), src.length());
        String p = NekoSourceLexerBase.position(src, src.length(), c);
        int col = p.indexOf(':');
        return new int[] {Integer.parseInt(p.substring(0, col)), Integer.parseInt(p.substring(col + 1))};
    }
}
