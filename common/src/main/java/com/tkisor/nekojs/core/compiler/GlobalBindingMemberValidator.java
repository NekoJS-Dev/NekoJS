package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.nio.file.Path;
import java.util.*;

public final class GlobalBindingMemberValidator {

    private GlobalBindingMemberValidator() {}

    public static void validate(Path file, String source) {
        if (file == null || source == null || source.isEmpty()) return;
        Map<String, ScriptBindingSchema.BindingMembers> schema = ScriptBindingSchema.schemaForPath(file);
        if (schema.isEmpty()) return;

        ValNode.Block ast;
        try { ast = ValParser.parse(source); } catch (Throwable ignored) { return; }

        Map<String, String> remap = new HashMap<>();
        collectRemaps(ast, remap);

        Set<String> reported = new HashSet<>();
        checkBlock(ast, schema, remap, file, source, reported);
    }

    private static void collectRemaps(ValNode node, Map<String, String> remap) {
        if (node instanceof ValNode.VarDecl decl && decl.init() instanceof ValNode.Identifier init) {
            remap.put(decl.name(), init.name());
        }
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) collectRemaps(s, remap);
        if (node instanceof ValNode.CallExpr c) for (ValNode a : c.args()) collectRemaps(a, remap);
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) collectRemaps(s, remap);
    }

    private static void checkBlock(ValNode node,
                                   Map<String, ScriptBindingSchema.BindingMembers> schema,
                                   Map<String, String> remap,
                                   Path file, String source, Set<String> reported) {
        if (node instanceof ValNode.MemberAccess access
                && access.object() instanceof ValNode.Identifier id) {
            String name = id.name();
            String resolved = remap.getOrDefault(name, name);
            ScriptBindingSchema.BindingMembers bm = schema.get(resolved);
            if (bm != null) {
                String member = access.member();
                if (member != null && !member.isEmpty() && !bm.contains(member)) {
                    if (!reported.add(resolved + "." + member)) return;
                    String s = JavaMemberIndex.suggestMember(bm.memberNames(), member);
                    String msg = "Binding '" + resolved + "' has no member '" + member + "'."
                            + (s != null ? " Did you mean '" + s + "'?" : "");
                    try {
                        int[] lc = lc(source, access.start());
                        ScriptErrorReporter.recordCallbackError(
                                ScriptBindingSchema.inferType(file),
                                "binding-preflight name=" + resolved,
                                new NekoEsmLinkException(new NekoEsmDiagnostic(
                                        file,
                                        new NekoEsmSpan(access.start(), access.start() + member.length()),
                                        lc[0], lc[1], msg)));
                    } catch (Throwable ignored) {}
                }
            }
        }
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) checkBlock(s, schema, remap, file, source, reported);
        if (node instanceof ValNode.CallExpr c) {
            for (ValNode a : c.args()) checkBlock(a, schema, remap, file, source, reported);
            checkBlock(c.callee(), schema, remap, file, source, reported);
        }
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) checkBlock(s, schema, remap, file, source, reported);
        if (node instanceof ValNode.FuncDecl fd) for (ValNode s : fd.body()) checkBlock(s, schema, remap, file, source, reported);
    }

    private static int[] lc(String src, int o) {
        int c = Math.min(Math.max(o, 0), src.length());
        String p = NekoSourceLexerBase.position(src, src.length(), c);
        int col = p.indexOf(':');
        return new int[] {Integer.parseInt(p.substring(0, col)), Integer.parseInt(p.substring(col + 1))};
    }
}
