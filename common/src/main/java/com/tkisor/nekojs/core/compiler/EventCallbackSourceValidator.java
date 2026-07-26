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
        try { ast = ValParser.parse(source); } catch (Throwable ignored) { return; }

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
                ManagedCallbackSchemaRegistry.CallbackSchema managed =
                        ManagedCallbackSchemaRegistry.resolve(ident.name(), access.member());
                if (managed != null) {
                    checkCallbackArgsManaged(call, managed, ident.name(), file, source, reported);
                } else {
                    Class<?> eventClass = EventSchemaRegistry.resolve(ident.name(), access.member());
                    if (eventClass != null && eventClass != Object.class) {
                        checkCallbackArgs(call, eventClass, ident.name(), file, source, reported);
                    }
                }
            }
        }
        // recurse
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) scanNode(s, schema, file, source, reported);
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) scanNode(s, schema, file, source, reported);
        if (node instanceof ValNode.CallExpr call) {
            for (ValNode a : call.args()) scanNode(a, schema, file, source, reported);
        }
    }

    private static void checkCallbackArgsManaged(ValNode.CallExpr call,
                                                  ManagedCallbackSchemaRegistry.CallbackSchema schema,
                                                  String group, Path file, String src, Set<String> reported) {
        for (ValNode arg : call.args()) {
            List<String> params = null;
            List<ValNode> body = null;
            if (arg instanceof ValNode.ArrowFunc af) { params = af.params(); body = af.body(); }
            else if (arg instanceof ValNode.FuncDecl fd) { params = fd.params(); body = fd.body(); }
            if (params != null && !params.isEmpty()) {
                Set<String> known = schema.memberNames();
                for (ValNode s : body) checkBodyManaged(s, params.get(0), known, schema.displayName(), group, file, src, reported);
            }
        }
    }

    private static void checkBodyManaged(ValNode node, String param, Set<String> known,
                                          String displayName, String group, Path file, String src, Set<String> reported) {
        if (node instanceof ValNode.MemberAccess access
                && access.object() instanceof ValNode.Identifier id
                && id.name().equals(param)) {
            String m = access.member();
            if (!known.contains(m)) {
                if (!reported.add(param + "." + m)) return;
                report(file, src, access.start(), group + " callback: '" + m
                        + "' not in " + displayName);
            }
        }
        if (node instanceof ValNode.Block b) for (ValNode st : b.stmts()) checkBodyManaged(st, param, known, displayName, group, file, src, reported);
        if (node instanceof ValNode.CallExpr call) for (ValNode a : call.args()) checkBodyManaged(a, param, known, displayName, group, file, src, reported);
        if (node instanceof ValNode.ArrowFunc af) for (ValNode st : af.body()) checkBodyManaged(st, param, known, displayName, group, file, src, reported);
        if (node instanceof ValNode.MemberAccess ma) checkBodyManaged(ma.object(), param, known, displayName, group, file, src, reported);
    }

    private static void checkCallbackArgs(ValNode.CallExpr call, Class<?> eventClass,
                                          String group, Path file, String src, Set<String> reported) {
        for (ValNode arg : call.args()) {
            List<String> params = null;
            List<ValNode> body = null;
            if (arg instanceof ValNode.ArrowFunc af) { params = af.params(); body = af.body(); }
            else if (arg instanceof ValNode.FuncDecl fd) { params = fd.params(); body = fd.body(); }
            if (params != null && !params.isEmpty()) {
                Set<String> known = JavaMemberIndex.propertyMembersOf(eventClass);
                for (ValNode s : body) checkBody(s, params.get(0), known, eventClass, group, file, src, reported);
            }
        }
    }

    private static void checkBody(ValNode node, String param, Set<String> known,
                                  Class<?> eventClass, String group, Path file, String src, Set<String> reported) {
        if (node instanceof ValNode.MemberAccess access
                && access.object() instanceof ValNode.Identifier id
                && id.name().equals(param)) {
            String m = access.member();
            if (!known.contains(m)) {
                if (!reported.add(param + "." + m)) return;
                String s = JavaMemberIndex.suggestMember(known, m);
                report(file, src, access.start(), group + " callback: '" + m
                        + "' not in " + eventClass.getSimpleName()
                        + (s != null ? ". Did you mean '" + s + "'?" : ""));
            }
        }
        if (node instanceof ValNode.Block b) for (ValNode st : b.stmts()) checkBody(st, param, known, eventClass, group, file, src, reported);
        if (node instanceof ValNode.CallExpr call) for (ValNode a : call.args()) checkBody(a, param, known, eventClass, group, file, src, reported);
        if (node instanceof ValNode.ArrowFunc af) for (ValNode st : af.body()) checkBody(st, param, known, eventClass, group, file, src, reported);
        if (node instanceof ValNode.MemberAccess ma) checkBody(ma.object(), param, known, eventClass, group, file, src, reported);
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
