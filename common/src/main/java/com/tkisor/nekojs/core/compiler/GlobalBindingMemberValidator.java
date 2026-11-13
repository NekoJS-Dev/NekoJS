package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preflight check of global binding members outside events: checks member access on schema
 * bindings (Utils/Item/… including event groups) anywhere in a script. Three layers:
 * <ol>
 *   <li><b>Direct members</b>: {@code Utils.serverTel()} (a typo) is checked against
 *       {@link ScriptBindingSchema.BindingMembers#memberNames()} (covers helper bindings on
 *       DelegatingBinding plus the target class's static members);</li>
 *   <li><b>Chained type flow</b>: {@code Item.of(x).witherCont(3)} — members reached after a
 *       call or property read on a binding are checked against the reflected members of
 *       {@link BindingMembers#valueClasses()} (second level and deeper);</li>
 *   <li><b>Undefined identifiers</b>: unknown identifiers used as the object of a member access
 *       ({@code Util.xxx}, a misspelled binding name) or as the call target ({@code qwq()}).
 *       The known set is declarations in the file (parameters included), names collected from
 *       import/class/catch/destructuring/for-of, schema binding names, and
 *       {@link ScriptBindingSchema#knownGlobals the full set of globals collected at runtime}.
 *       When globals are not registered (tests, degraded mode) this layer is skipped entirely.
 *       A bare identifier used as an expression statement ({@code qwq} on its own) is
 *       **not** reported: ValParser leaks the operand of {@code typeof x} as a separate
 *       statement, so it cannot be told apart from a real undefined name.</li>
 * </ol>
 *
 * <p>Reporting does not block compilation or execution; a file ValParser fails to parse is
 * skipped entirely (warn log).
 */
public final class GlobalBindingMemberValidator {

    /**
     * JS keyword blacklist. ValParser parses keyword-prefixed constructs such as {@code if (x)}
     * and {@code catch (e)} as CallExpr(keyword, args), so the undefined-identifier check must let
     * these pseudo call targets through.
     */
    private static final Set<String> JS_KEYWORDS = Set.of(
            "if", "else", "for", "while", "do", "switch", "case", "default", "try", "catch",
            "finally", "return", "throw", "break", "continue", "typeof", "instanceof", "in",
            "of", "new", "delete", "void", "await", "yield", "class", "function", "import",
            "export", "extends", "const", "let", "var", "enum");

    // Patterns contain no source state; each scan creates its own Matcher.
    private static final Pattern IMPORT_CLAUSE = Pattern.compile("\\bimport\\s+([^;='\"`]{1,200}?)\\s*from", Pattern.MULTILINE);
    private static final Pattern CLASS_DECLARATION = Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern FUNCTION_DECLARATION = Pattern.compile("\\bfunction\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern CATCH_BINDING = Pattern.compile("\\bcatch\\s*\\(\\s*[A-Za-z_$][\\w$]*\\s*[,)]\\s*([A-Za-z_$][\\w$]*)?");
    private static final Pattern CATCH_PARAMETER = Pattern.compile("\\bcatch\\s*\\(\\s*([A-Za-z_$][\\w$]*)\\s*\\)");
    private static final Pattern DESTRUCTURING_BINDING = Pattern.compile("\\b(?:const|let|var)\\s*[\\{\\[]([^\\}\\]]{1,300})[\\}\\]]");
    private static final Pattern LOOP_BINDING = Pattern.compile("\\(\\s*(?:const|let|var)?\\s*([A-Za-z_$][\\w$]*)\\s+(?:of|in)\\b");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

    private GlobalBindingMemberValidator() {}

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
            // Same as EventCallbackSourceValidator: do not skip silently; log that this file has no preflight protection
            com.tkisor.nekojs.NekoJS.LOGGER.warn(
                    "Binding preflight skipped for {}: source not parseable by ValParser ({}: {})",
                    file, e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        SourceFacts facts = parsed.bindingFacts(ast);
        Map<String, String> remap = facts.remap();

        // Local variable type flow: const s = Item.of(x) means later members of s are checked against its return type (two passes propagate dependencies between declarations)
        Map<String, Set<Class<?>>> localTypes = new HashMap<>();
        collectLocalTypes(ast, schema, remap, localTypes);
        collectLocalTypes(ast, schema, remap, localTypes);

        Set<String> known = collectKnownIdentifiers(facts, schema, view);

        Set<String> reported = new HashSet<>();
        checkBlock(ast, schema, remap, localTypes, known, file, source, reported, view);
    }

    // ==================== Known identifier set ====================

    /**
     * Every identifier resolvable in this file. The AST gives a flat union of all declared names and
     * all function/arrow parameters (a cross-scope union can only under-report); forms it cannot cover
     * (import/class/catch/destructuring/for-of) come from source-level regexes since ValParser is a lossy
     * parser and those constructs break up into unrecognizable fragments.
     */
    private static Set<String> collectKnownIdentifiers(SourceFacts facts,
                                                        Map<String, ScriptBindingSchema.BindingMembers> schema,
                                                        ScriptBindingSchema.View view) {
        Set<String> known = new HashSet<>(schema.keySet());
        known.addAll(facts.declaredNames());
        Set<String> globals = view == null ? Set.of() : view.knownGlobals();
        if (globals.isEmpty()) {
            // The runtime global set is not registered: the undefined-identifier check cannot run safely, so it is skipped (member checks still run)
            return null;
        }
        known.addAll(globals);
        return known;
    }

    /** Pure authored facts; host type flow and known globals remain current-schema work. */
    static SourceFacts sourceFacts(ValNode.Block ast, String source) {
        Map<String, String> remap = new HashMap<>();
        collectRemaps(ast, remap);
        Set<String> declared = new HashSet<>();
        collectDeclaredNames(ast, declared);
        collectSourceLevelNames(source, declared);
        return new SourceFacts(Map.copyOf(remap), Set.copyOf(declared));
    }

    record SourceFacts(Map<String, String> remap, Set<String> declaredNames) {}

    private static void collectDeclaredNames(ValNode node, Set<String> out) {
        if (node == null) return;
        if (node instanceof ValNode.VarDecl decl) {
            out.add(decl.name());
        } else if (node instanceof ValNode.FuncDecl fd) {
            if (!fd.name().isEmpty()) out.add(fd.name());
            out.addAll(fd.params());
        } else if (node instanceof ValNode.ArrowFunc af) {
            out.addAll(af.params());
        }
        // Name extraction and child recursion must stay independent: a declaration inside an arrow/function body
        // (a callback's const local, say) is file-visible too, so collecting parameters alone would report it as unknown.
        if (node instanceof ValNode.VarDecl decl) collectDeclaredNames(decl.init(), out);
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) collectDeclaredNames(s, out);
        if (node instanceof ValNode.CallExpr c) {
            collectDeclaredNames(c.callee(), out);
            for (ValNode a : c.args()) collectDeclaredNames(a, out);
        }
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) collectDeclaredNames(s, out);
        if (node instanceof ValNode.FuncDecl fd) for (ValNode s : fd.body()) collectDeclaredNames(s, out);
        if (node instanceof ValNode.MemberAccess m) collectDeclaredNames(m.object(), out);
        if (node instanceof ValNode.ComputedMemberAccess m) {
            collectDeclaredNames(m.object(), out);
            collectDeclaredNames(m.key(), out);
        }
    }

    /** Declarations the AST cannot cover: ESM import names, class names, catch parameters, destructuring declarations, for-of/in variables. */
    private static void collectSourceLevelNames(String source, Set<String> out) {
        Matcher imports = IMPORT_CLAUSE.matcher(source);
        while (imports.find()) {
            addIdentifiersIn(imports.group(1), out);
        }
        matchAll(source, CLASS_DECLARATION, out);
        matchAll(source, FUNCTION_DECLARATION, out);
        matchAll(source, CATCH_BINDING, out);
        // Single-parameter catch (e) form; the pattern above only covers the multi-parameter optional binding form
        matchAll(source, CATCH_PARAMETER, out);
        Matcher destructure = DESTRUCTURING_BINDING.matcher(source);
        while (destructure.find()) {
            addIdentifiersIn(destructure.group(1), out);
        }
        matchAll(source, LOOP_BINDING, out);
    }

    private static void matchAll(String source, Pattern pattern, Set<String> out) {
        Matcher m = pattern.matcher(source);
        while (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) out.add(m.group(i));
            }
        }
    }

    /** Extracts identifiers from an import clause or destructuring pattern fragment (covers {@code a as b}, {@code a: c}, default values and rest). */
    private static void addIdentifiersIn(String clause, Set<String> out) {
        Matcher m = IDENTIFIER.matcher(clause);
        while (m.find()) out.add(m.group());
    }

    private static void collectRemaps(ValNode node, Map<String, String> remap) {
        if (node instanceof ValNode.VarDecl decl && decl.init() instanceof ValNode.Identifier init) {
            remap.put(decl.name(), init.name());
        }
        if (node instanceof ValNode.VarDecl decl) collectRemaps(decl.init(), remap);
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) collectRemaps(s, remap);
        if (node instanceof ValNode.FuncDecl fd) for (ValNode s : fd.body()) collectRemaps(s, remap);
        if (node instanceof ValNode.CallExpr c) for (ValNode a : c.args()) collectRemaps(a, remap);
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) collectRemaps(s, remap);
    }

    /** Local variable -> the type of its initializer (propagated through bindings and other resolved locals; collectLocalTypes runs twice to cover dependencies between declarations). */
    private static void collectLocalTypes(ValNode node, Map<String, ScriptBindingSchema.BindingMembers> schema,
                                          Map<String, String> remap, Map<String, Set<Class<?>>> out) {
        if (node instanceof ValNode.VarDecl decl && decl.init() != null) {
            Set<Class<?>> types = resolveType(decl.init(), schema, remap, out);
            if (types != null && !types.isEmpty()) out.put(decl.name(), types);
        }
        if (node instanceof ValNode.VarDecl decl) collectLocalTypes(decl.init(), schema, remap, out);
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) collectLocalTypes(s, schema, remap, out);
        if (node instanceof ValNode.CallExpr c) {
            collectLocalTypes(c.callee(), schema, remap, out);
            for (ValNode a : c.args()) collectLocalTypes(a, schema, remap, out);
        }
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) collectLocalTypes(s, schema, remap, out);
        if (node instanceof ValNode.FuncDecl fd) for (ValNode s : fd.body()) collectLocalTypes(s, schema, remap, out);
        if (node instanceof ValNode.MemberAccess m) collectLocalTypes(m.object(), schema, remap, out);
        if (node instanceof ValNode.ComputedMemberAccess m) {
            collectLocalTypes(m.object(), schema, remap, out);
            collectLocalTypes(m.key(), schema, remap, out);
        }
    }

    // ==================== Check body ====================

    private static void checkBlock(ValNode node,
                                   Map<String, ScriptBindingSchema.BindingMembers> schema,
                                   Map<String, String> remap,
                                   Map<String, Set<Class<?>>> localTypes,
                                   Set<String> known,
                                   Path file, String source, Set<String> reported,
                                   ScriptBindingSchema.View view) {
        if (node instanceof ValNode.MemberAccess access) {
            checkMemberAccess(access, schema, remap, localTypes, known, file, source, reported, view);
        }
        if (node instanceof ValNode.CallExpr call && call.callee() instanceof ValNode.Identifier id) {
            checkUnknownIdentifier(id, schema, remap, known, file, source, reported, view);
        }
        if (node instanceof ValNode.VarDecl decl) {
            checkBlock(decl.init(), schema, remap, localTypes, known, file, source, reported, view);
        }
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) checkBlock(s, schema, remap, localTypes, known, file, source, reported, view);
        if (node instanceof ValNode.CallExpr c) {
            for (ValNode a : c.args()) checkBlock(a, schema, remap, localTypes, known, file, source, reported, view);
            checkBlock(c.callee(), schema, remap, localTypes, known, file, source, reported, view);
        }
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) checkBlock(s, schema, remap, localTypes, known, file, source, reported, view);
        if (node instanceof ValNode.FuncDecl fd) for (ValNode s : fd.body()) checkBlock(s, schema, remap, localTypes, known, file, source, reported, view);
        // Chained intermediate nodes: an outer MemberAccess only checks its own level, so every level on the object() side must be visited on its own
        if (node instanceof ValNode.MemberAccess m) {
            checkBlock(m.object(), schema, remap, localTypes, known, file, source, reported, view);
        }
        if (node instanceof ValNode.ComputedMemberAccess computed) {
            checkBlock(computed.object(), schema, remap, localTypes, known, file, source, reported, view);
            checkBlock(computed.key(), schema, remap, localTypes, known, file, source, reported, view);
        }
    }

    /**
     * Member access check: when the object side has a type flow (schema-bound {@code valueClasses}) the
     * member is looked up reflectively (second chain level and deeper); otherwise this falls back to the
     * existing binding-name + member-table check. An unknown identifier is reported when globals are registered.
     */
    private static void checkMemberAccess(ValNode.MemberAccess access,
                                          Map<String, ScriptBindingSchema.BindingMembers> schema,
                                          Map<String, String> remap,
                                          Map<String, Set<Class<?>>> localTypes,
                                           Set<String> known,
                                           Path file, String source, Set<String> reported,
                                           ScriptBindingSchema.View view) {
        String member = access.member();
        if (member == null || member.isEmpty()) return;

        Set<Class<?>> objectClasses = resolveType(access.object(), schema, remap, localTypes);
        if (objectClasses != null && !objectClasses.isEmpty()) {
            boolean anyKnown = false;
            for (Class<?> cls : objectClasses) {
                JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(cls);
                if (exposed.hasMember(member)) {
                    anyKnown = true;
                }
            }
            if (!anyKnown) {
                Set<String> knownMembers = new LinkedHashSet<>();
                for (Class<?> cls : objectClasses) {
                    JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(cls);
                    knownMembers.addAll(exposed.methods().keySet());
                    knownMembers.addAll(exposed.propertyGetters().keySet());
                    knownMembers.addAll(exposed.fields().keySet());
                }
                report(file, source, reported, access.start(), member.length(),
                        "Type chain: no member '" + member + "' on " + classNames(objectClasses)
                                + suggestion(knownMembers, member), "chain-member " + member, view);
            }
            return;
        }

        if (access.object() instanceof ValNode.Identifier id) {
            String resolved = remap.getOrDefault(id.name(), id.name());
            ScriptBindingSchema.BindingMembers bm = schema.get(resolved);
            if (bm != null) {
                if (!bm.contains(member)) {
                    report(file, source, reported, access.start(), member.length(),
                            "Binding '" + resolved + "' has no member '" + member + "'."
                                    + suggestion(bm.memberNames(), member), "binding-member " + resolved + "." + member, view);
                }
                return;
            }
            checkUnknownIdentifier(id, schema, remap, known, file, source, reported, view);
        }
    }

    /** Chained type flow: identifier -> schema-bound valueClasses; the return type of a property read or a call propagates along the chain. */
    private static Set<Class<?>> resolveType(ValNode node,
                                             Map<String, ScriptBindingSchema.BindingMembers> schema,
                                             Map<String, String> remap,
                                             Map<String, Set<Class<?>>> localTypes) {
        if (node instanceof ValNode.Identifier id) {
            Set<Class<?>> local = localTypes.get(id.name());
            if (local != null) return local;
            String resolved = remap.getOrDefault(id.name(), id.name());
            ScriptBindingSchema.BindingMembers bm = schema.get(resolved);
            if (bm != null && !bm.valueClasses().isEmpty()) {
                return bm.valueClasses();
            }
            return null;
        }
        if (node instanceof ValNode.MemberAccess access) {
            Set<Class<?>> obj = resolveType(access.object(), schema, remap, localTypes);
            if (obj == null) return null;
            Set<Class<?>> out = new LinkedHashSet<>();
            for (Class<?> cls : obj) {
                for (Type type : JavaMemberIndex.exposedMembersOf(cls).propertyTypes(access.member())) {
                    out.addAll(JavaMemberIndex.typeClasses(type));
                }
            }
            return out.isEmpty() ? null : out;
        }
        if (node instanceof ValNode.CallExpr call && call.callee() instanceof ValNode.MemberAccess access) {
            Set<Class<?>> obj = resolveType(access.object(), schema, remap, localTypes);
            if (obj == null) return null;
            Set<Class<?>> out = new LinkedHashSet<>();
            for (Class<?> cls : obj) {
                for (Type type : JavaMemberIndex.exposedMembersOf(cls).callReturnTypes(access.member(), call.args().size())) {
                    out.addAll(JavaMemberIndex.typeClasses(type));
                }
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }

    /** Undefined identifier: reported only at a member-access object / call-target position (a bare identifier is not reported because of typeof leakage). */
    private static void checkUnknownIdentifier(ValNode.Identifier id,
                                               Map<String, ScriptBindingSchema.BindingMembers> schema,
                                               Map<String, String> remap,
                                               Set<String> known,
                                               Path file, String source, Set<String> reported,
                                               ScriptBindingSchema.View view) {
        if (known == null || id.name().isEmpty() || JS_KEYWORDS.contains(id.name())) return;
        String resolved = remap.getOrDefault(id.name(), id.name());
        if (schema.containsKey(resolved) || known.contains(resolved) || known.contains(id.name())) return;
        String suggest = JavaMemberIndex.suggestMember(known, resolved);
        report(file, source, reported, id.start(), id.name().length(),
                "Unknown identifier '" + id.name() + "'."
                        + (suggest != null ? " Did you mean '" + suggest + "'?" : ""),
                "unknown-identifier " + id.name(), view);
    }

    // ==================== Reporting ====================

    private static String suggestion(Set<String> candidates, String member) {
        String s = JavaMemberIndex.suggestMember(candidates, member);
        return s != null ? " Did you mean '" + s + "'?" : "";
    }

    private static String classNames(Set<Class<?>> classes) {
        StringJoiner joiner = new StringJoiner("/");
        for (Class<?> cls : classes) joiner.add(cls.getSimpleName());
        return joiner.toString();
    }

    private static void report(Path file, String source, Set<String> reported,
                               int offset, int length, String msg, String dedupKey,
                               ScriptBindingSchema.View view) {
        if (!reported.add(dedupKey)) return;
        try {
            int[] lc = lc(source, offset);
            ScriptType type = ScriptBindingSchema.inferType(file);
            NekoEsmLinkException error = new NekoEsmLinkException(new NekoEsmDiagnostic(
                    file, new NekoEsmSpan(offset, offset + length), lc[0], lc[1], msg));
            if (view == null) {
                ScriptErrorReporter.recordCallbackError(type, "binding-preflight " + dedupKey, error);
            } else {
                view.report(type, "binding-preflight " + dedupKey, error);
            }
        } catch (Throwable ignored) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn("Binding preflight report failed for {}", dedupKey, ignored);
        }
    }

    private static int[] lc(String src, int o) {
        int c = Math.min(Math.max(o, 0), src.length());
        String p = NekoSourceLexerBase.position(src, src.length(), c);
        int col = p.indexOf(':');
        return new int[] {Integer.parseInt(p.substring(0, col)), Integer.parseInt(p.substring(col + 1))};
    }
}
