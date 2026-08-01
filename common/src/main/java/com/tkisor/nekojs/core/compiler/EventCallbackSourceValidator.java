package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventSchemaRegistry;
import com.tkisor.nekojs.api.event.ManagedCallbackSchemaRegistry;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
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

            Set<String> known = new HashSet<>();
            String displayName;
            if (managed != null) {
                known.addAll(managed.memberNames());
                displayName = managed.displayName();
            } else {
                displayName = group;
            }
            if (eventClass != null && eventClass != Object.class) {
                known.addAll(JavaMemberIndex.propertyMembersOf(eventClass));
                if (managed == null) {
                    displayName = eventClass.getSimpleName();
                }
            }

            // 别名映射：const x = e; x.getServer() 与 e.getServer() 等价
            Map<String, String> remap = new HashMap<>();
            collectRemaps(body, params.getFirst(), remap);

            for (ValNode s : body) {
                checkBody(s, params.getFirst(), remap, known, displayName, group, file, src, reported);
            }
        }
    }

    private static void collectRemaps(List<ValNode> body, String param, Map<String, String> remap) {
        for (ValNode node : body) {
            if (node instanceof ValNode.VarDecl decl
                    && decl.init() instanceof ValNode.Identifier init
                    && param.equals(init.name())) {
                remap.put(decl.name(), init.name());
            }
        }
    }

    private static void checkBody(ValNode node, String param, Map<String, String> remap, Set<String> known,
                                  String displayName, String group, Path file, String src, Set<String> reported) {
        if (node instanceof ValNode.MemberAccess access
                && access.object() instanceof ValNode.Identifier id) {
            String resolved = remap.getOrDefault(id.name(), id.name());
            if (param.equals(resolved)) {
                String m = access.member();
                if (!known.contains(m)) {
                    if (!reported.add(resolved + "." + m)) return;
                    String s = JavaMemberIndex.suggestMember(known, m);
                    report(file, src, access.start(), group + " callback: '" + m
                            + "' not in " + displayName
                            + (s != null ? ". Did you mean '" + s + "'?" : ""));
                }
            }
        }
        if (node instanceof ValNode.Block b) for (ValNode st : b.stmts()) checkBody(st, param, remap, known, displayName, group, file, src, reported);
        if (node instanceof ValNode.CallExpr call) {
            for (ValNode a : call.args()) checkBody(a, param, remap, known, displayName, group, file, src, reported);
            // 必须递归 callee：`e.getServer()` 解析为 CallExpr[callee=MemberAccess[e, getServer]]，
            // 只查 args 会漏掉最常见的「方法调用」形式（原实现缺陷）。
            checkBody(call.callee(), param, remap, known, displayName, group, file, src, reported);
        }
        if (node instanceof ValNode.ArrowFunc af) for (ValNode st : af.body()) checkBody(st, param, remap, known, displayName, group, file, src, reported);
        if (node instanceof ValNode.MemberAccess ma) checkBody(ma.object(), param, remap, known, displayName, group, file, src, reported);
    }

    private static void report(Path file, String src, int offset, String msg) {
        try {
            int[] lc = lc(src, offset);
            ScriptType type = ScriptBindingSchema.inferType(file);
            ScriptErrorReporter.recordCallbackError(type, "event-cb-preflight",
                    new NekoEsmLinkException(new NekoEsmDiagnostic(file, new NekoEsmSpan(offset, offset), lc[0], lc[1], msg)));
        } catch (Throwable ignored) {}
    }

    private static int[] lc(String src, int o) {
        int c = Math.min(Math.max(o, 0), src.length());
        String p = NekoSourceLexerBase.position(src, src.length(), c);
        int col = p.indexOf(':');
        return new int[] {Integer.parseInt(p.substring(0, col)), Integer.parseInt(p.substring(col + 1))};
    }
}
