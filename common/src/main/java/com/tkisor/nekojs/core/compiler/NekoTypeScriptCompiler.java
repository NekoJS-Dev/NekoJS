package com.tkisor.nekojs.core.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NekoTypeScriptCompiler {
    private NekoTypeScriptCompiler() {}

    static TypeScriptTransformResult erasePreservingSourceMap(Path file, String source, String sourceMap) {
        String original = source == null ? "" : source;
        return new TypeScriptTransformResult(new Eraser(file, original).erase(), sourceMap);
    }

    static TypeScriptTransformResult eraseDetailed(Path file, String source) {
        String original = source == null ? "" : source;
        String erased = new Eraser(file, original).erase();
        return new TypeScriptTransformResult(erased, NekoSourceMapBuilder.identity(file, original, erased));
    }

    /**
     * Erases TypeScript type annotations and returns JS source code ready for evaluation (synchronizing enum/namespace/parameter property downgrades).
     *
     * <p>Used for "script-style .ts to JS" scenarios, such as the node module loader: .ts modules use a script-compatible syntax
     * (IIFE + {@code __nekoNodeDefine}), and type annotations erased by this method allow for evaluation within the script context.
     *
     * @param file   File path used for error location (can be a virtual path).
     * @param source TypeScript source code.
     * @return JS source code with types erased.
     */
    public static String eraseTypescript(Path file, String source) {
        String original = source == null ? "" : source;
        return new Eraser(file, original).erase();
    }

    record TypeScriptTransformResult(String code, String sourceMap) {}

    private static final class Eraser {
        private record ClassBodyRange(int headerStart, int open, int close) {}
        private record BraceRange(int open, int close) {}

        private final Path file;
        private final String source;
        private final StringBuilder out;
        private final int length;
        private final List<ClassBodyRange> classBodies = new ArrayList<>();
        private final List<BraceRange> braceRanges = new ArrayList<>();
        private final java.util.Map<Integer, Integer> parenOpens = new java.util.HashMap<>();
        /** Pre-scan: The ':' at this offset is a switch case/default or label colon (must be rejected by typeAnnotationAt). */
        private final boolean[] caseOrLabelColon;
        /** Pre-scan: The offset is within an import/export value alias clause (named {...} or * as X), where 'as' is a native JS alias. */
        private final boolean[] aliasClause;

        private Eraser(Path file, String source) {
            this.file = file;
            this.source = source;
            this.out = new StringBuilder(source);
            this.length = source.length();
            this.caseOrLabelColon = new boolean[length];
            this.aliasClause = new boolean[length];
            scanStructure();
            scanClassBodies();
            scanBraceRanges();
        }

        private String erase() {
            int i = 0;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') {
                    i = skipString(i, c);
                    continue;
                }
                if (c == '`') {
                    i = skipTemplate(i);
                    continue;
                }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) {
                        i = skipped;
                        continue;
                    }
                }
                if (isIdentifierStart(c)) {
                    int end = readIdentifierEnd(i + 1);
                   String word = source.substring(i, end);
                    if (("interface".equals(word) || "type".equals(word)) && typeDeclAfter(end)) {
                        i = eraseTypeDeclaration(i);
                        continue;
                    }
                    if ("abstract".equals(word)) {
                        if (startsWithKeyword(nextNonWhitespace(end), "class")) {
                            // abstract class: Erase only the modifier; keep the class declaration as is.
                            eraseRange(i, end);
                            i = end;
                        } else {
                            // Abstract members in a class body (abstract m(): void; / abstract field: T;):
                            // TS semantics act as a placeholder without implementation. Residual methods without bodies or uninitialized fields in a JS class are SyntaxErrors,
                            // so erase the entire member (consistent with TS official elide behavior).
                            int memberEnd = statementOrBlockDeclarationEnd(i);
                            eraseRange(i, memberEnd);
                            i = memberEnd;
                        }
                        continue;
                    }
                    // enum/namespace/module: Do not throw an exception; skip as a normal identifier and leave for the transformation stage (transformEnums/Namespaces) to handle.
                    if ("declare".equals(word)) {
                        i = eraseDeclare(i);
                        continue;
                    }
                    if ("import".equals(word) && typeKeywordAfter(end)) {
                        i = eraseStatement(i);
                        continue;
                    }
                    if ("import".equals(word) && hasInlineTypeSpecifier(end)) {
                        i = eraseInlineTypeSpecifiers(i, end);
                        continue;
                    }
                    if ("export".equals(word) && typeExportAfter(end)) {
                        i = eraseExportTypeDeclaration(i);
                        continue;
                    }
                    if ("export".equals(word) && startsWithKeyword(nextNonWhitespace(end), "declare")) {
                        // export declare const/function/...: The entire statement is an environment declaration; erase the whole sentence including 'export'.
                        // (Erasing only 'declare' would leave a naked 'export' + uninitialized declaration -> SyntaxError).
                        int end2 = statementOrBlockDeclarationEnd(i);
                        eraseRange(i, end2);
                        i = end2;
                        continue;
                    }
                    if ("export".equals(word) && hasInlineTypeSpecifier(end)) {
                        i = eraseInlineTypeSpecifiers(i, end);
                        continue;
                    }
                    if ("implements".equals(word)) {
                        i = eraseImplements(i, end);
                        continue;
                    }
                    if ("as".equals(word) || "satisfies".equals(word)) {
                        i = eraseAssertion(i, end);
                        continue;
                    }
                    if ("function".equals(word)) {
                        int overloadEnd = functionOverloadEnd(end);
                        if (overloadEnd > 0) {
                            eraseRange(i, overloadEnd);
                            i = overloadEnd;
                            continue;
                        }
                    }
                    int overloadEnd = methodOverloadEnd(i, end);
                    if (overloadEnd > 0) {
                        eraseRange(i, overloadEnd);
                        i = overloadEnd;
                        continue;
                    }
                    i = end;
                    continue;
                }
                if (c == '@' && decoratorAt(i)) {
                    // Decorators (@Component / @Log(...)): NekoJS is a script engine, not a TS framework, and does not support decorators.
                    // Explicitly report an error instead of passing it through as invalid JS (a naked '@' is illegal in JS; GraalJS would report an error but with an unclear message).
                    throw unsupported("decorator (@)", i);
                }
                if (c == '<' && genericTypeArgumentsAt(i)) {
                    int end = matchingAngle(i);
                    eraseRange(i, end + 1);
                    i = end + 1;
                    continue;
                }
                if (c == ':' && typeAnnotationAt(i)) {
                    int end = typeAnnotationEnd(i + 1);
                    // Optional parameter 'name?: T' -> also erase '?'
                    int q = previousNonWhitespace(i - 1);
                    int start = (q >= 0 && source.charAt(q) == '?') ? q : i;
                    int thisParameter = thisParameterStart(i);
                    if (thisParameter >= 0) {
                        start = thisParameter;
                        int comma = nextNonWhitespace(end);
                        if (comma < length && source.charAt(comma) == ',') end = comma + 1;
                    }
                    eraseRange(start, end);
                    i = end;
                    continue;
                }
                if (c == '!') {
                    if (definiteAssignmentAt(i)) {
                        eraseRange(i, i + 1);
                        // After erasing the '!' in a definite assignment assertion 'x!: T', the '!' remains in the source.
                        // 'typeAnnotationAt' would misinterpret it as a non-type annotation because ':' is preceded by '!'. -> Therefore, erase ': T' along with it.
                        int colon = nextNonWhitespace(i + 1);
                        if (colon < length && source.charAt(colon) == ':') {
                            int tend = typeAnnotationEnd(colon + 1);
                            eraseRange(colon, tend);
                            i = tend;
                        } else {
                            i++;
                        }
                        continue;
                    }
                    if (nonNullAssertionAt(i)) {
                        eraseRange(i, i + 1);
                        i++;
                        continue;
                    }
                }
                if (c == '?' && bareOptionalParameterAt(i)) {
                    // Naked optional parameter 'b?' (when there is no ': T' annotation, '?' is not erased by anyone; if it remains, it is a syntax error).
                    eraseRange(i, i + 1);
                    i++;
                    continue;
                }
                i++;
            }
            transform();
            return out.toString();
        }

        // ===== Stage 0: Statement-level structure pre-scan =====

        /**
         * Performs a linear scan to record the statement-level context for the erasure predicate lookup table (the eraser itself still advances character by character):
         * <ul>
         *   <li>':' for case/default/label -- {@code typeAnnotationAt} used to mistake the colon in {@code case 1:}
         *       (numbers are also identifier characters) and label colons for type annotations, emptying the entire case body/label statement;</li>
         *   <li>Value alias clause for import/export -- 'as' in {@code import { A as B }} is a native JS alias,
         *       and {@code assertionContext} used to erase it as a type assertion, causing a ReferenceError at runtime.</li>
         * </ul>
         * The identification rule is conservative: labels require 'an identifier at the statement header + ":" followed by the full word "for"/"while"/"do"/"switch"/"if"/"try"',
         * avoiding misidentification of class fields {@code x: { a: T }} (colon followed by object type) and object literal properties--mislabeling one that
         * shouldn't have been erased (object property) has no behavioral impact, but missing it would break the erasure.
         */
        private void scanStructure() {
            boolean statementHead = true;
            int i = 0;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (statementHead && isIdentifierStart(c)) {
                    int end = readIdentifierEnd(i + 1);
                    String word = source.substring(i, end);
                    if (word.equals("import") || word.equals("export")) {
                        i = markAliasClauses(end);
                        statementHead = false;
                        continue;
                    }
                    if (word.equals("case")) {
                        int colon = findCaseColon(end);
                        if (colon >= 0) caseOrLabelColon[colon] = true;
                        i = end;
                        statementHead = false;
                        continue;
                    }
                    if (word.equals("default")) {
                        int p = nextNonWhitespace(end);
                        if (p < length && source.charAt(p) == ':') caseOrLabelColon[p] = true;
                        i = end;
                        statementHead = false;
                        continue;
                    }
                    int p = nextNonWhitespace(end);
                    if (p < length && source.charAt(p) == ':' && labelStatementAfter(p + 1)) {
                        caseOrLabelColon[p] = true;
                    }
                    statementHead = false;
                    i = end;
                    continue;
                }
                // Reset the statement header at statement boundaries; blanks (space/Tab/\r) maintain the current state--in "{\n  case",
                // the indentation spaces after a newline cannot consume the statement header flag.
                if (c == ';' || c == '{' || c == '}' || c == '\n') {
                    statementHead = true;
                } else if (!Character.isWhitespace(c)) {
                    statementHead = false;
                }
                i++;
            }
        }

        /** The label colon must be followed by a loop/switch/if/try statement header (full word match; only break/continue targets require labels). */
        private boolean labelStatementAfter(int from) {
            int i = nextNonWhitespace(from);
            if (i >= length || !isIdentifierStart(source.charAt(i))) return false;
            int end = readIdentifierEnd(i + 1);
            String word = source.substring(i, end);
            return word.equals("for") || word.equals("while") || word.equals("do")
                || word.equals("switch") || word.equals("if") || word.equals("try");
        }

        /**
         * Scans for a matching ':' after the 'case' keyword (skipping strings/templates/content within parentheses; ternary '?:' depth 0 pairing count,
         * excluding '?.' and '??'). If {@code ;}/{@code {} is encountered, the input form is abnormal; return -1 and do not treat it as a case.
         */
        private int findCaseColon(int from) {
            int parenDepth = 0, ternary = 0, i = from;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '(' || c == '[') { parenDepth++; i++; continue; }
                if (c == ')' || c == ']') { if (parenDepth > 0) parenDepth--; i++; continue; }
                if (c == '?' && parenDepth == 0
                        && !(i + 1 < length && (source.charAt(i + 1) == '.' || source.charAt(i + 1) == '?'))) {
                    ternary++;
                    i++;
                    continue;
                }
                if (c == ':' && parenDepth == 0) {
                    if (ternary > 0) { ternary--; i++; continue; }
                    return i;
                }
                if (c == ';' || c == '{') return -1;
                i++;
            }
            return -1;
        }

        /**
         * Marks value alias clauses in import/export statements (named {...} and {@code * as X}) as
         * aliasClause. Scanning stops at a declaration body (keywords like {@code =}/{@code (}/
         * const/function/class/default): {@code export const x = y as any}'s 'as' is still a type
         * assertion and must be erased as usual.
         */
        private int markAliasClauses(int from) {
            int i = from;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '{') {
                    int close = matchingCloseBracket(i, '{', '}');
                    if (close < 0) return i;
                    for (int k = i; k <= close; k++) aliasClause[k] = true;
                    i = close + 1;
                    continue;
                }
                if (c == '*') {
                    int asKw = nextNonWhitespace(i + 1);
                    if (asKw >= 0 && asKw + 2 <= length && startsWithKeyword(asKw, "as")) {
                        int name = nextNonWhitespace(asKw + 2);
                        if (name < length && isIdentifierStart(source.charAt(name))) {
                            int nameEnd = readIdentifierEnd(name + 1);
                            for (int k = i; k < nameEnd; k++) aliasClause[k] = true;
                            i = nameEnd;
                            continue;
                        }
                    }
                    i++;
                    continue;
                }
                if (isIdentifierStart(c)) {
                    int end = readIdentifierEnd(i + 1);
                    String word = source.substring(i, end);
                    if (word.equals("from") || word.equals("type")) { i = end; continue; }
                    // After default/declaration keywords is the value area (which may contain 'as' assertions); the alias clause has ended.
                    if (word.equals("default") || word.equals("const") || word.equals("let")
                            || word.equals("var") || word.equals("function") || word.equals("class")
                            || word.equals("async") || word.equals("abstract") || word.equals("enum")
                            || word.equals("interface") || word.equals("namespace") || word.equals("module")) {
                        return i;
                    }
                    i = end;
                    continue;
                }
                if (c == '=' || c == '(') return i;
                if (c == ';' || c == '\n') return i + 1;
                i++;
            }
            return i;
        }

        // ===== Stage 2: Transformation (enum/namespace/parameter properties)——Replace on 'out', changing length; source map line numbers may shift =====

       private void transform() {
           transformEnums();
           transformNamespaces();
           transformParameterProperties();
            transformClassMemberModifiers();
       }

        // ---- Erase visibility modifiers before class fields/methods (keep 'static'; JS class fields do not support public/private/protected/readonly/abstract/override) ----
        private void transformClassMemberModifiers() {
            int i = 0;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (isIdentifierStart(c) && outKeywordAt(i, "class")) {
                    i = eraseClassModifiers(i + 5);
                    continue;
                }
                i++;
            }
        }

        /** Locates the class body and erases the member modifiers within it; returns the position after the class body ends. */
        private int eraseClassModifiers(int afterClass) {
            int i = nextOutNonWhitespace(afterClass);
            int braceOpen = -1;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (c == '{') { braceOpen = i; break; }
                if (c == ';' || c == '\n') return i + 1; // 'declare class' without body
                i++;
            }
            if (braceOpen < 0) return out.length();
            int braceClose = matchOutBrace(braceOpen);
            if (braceClose < 0) return out.length();
            eraseClassMemberModifiers(braceOpen + 1, braceClose);
            return braceClose + 1;
        }

        /** Erases public/private/protected/readonly/abstract/override within a class body (at depth=0); keeps 'static'. */
        private void eraseClassMemberModifiers(int bodyStart, int bodyEnd) {
            int i = bodyStart;
            int depth = 0;
            while (i < bodyEnd) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < bodyEnd && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < bodyEnd && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (c == '{' || c == '(' || c == '[') { depth++; i++; continue; }
                if (c == '}' || c == ')' || c == ']') { if (depth > 0) depth--; i++; continue; }
                if (depth == 0 && isIdentifierStart(c)) {
                    int end = i;
                    while (end < bodyEnd && isIdentifierPart(out.charAt(end))) end++;
                    String word = out.substring(i, end);
                    if (isClassMemberModifier(word)) eraseOutRange(i, end);
                    i = end;
                    continue;
                }
                i++;
            }
        }

        private static boolean isClassMemberModifier(String w) {
            return w.equals("public") || w.equals("private") || w.equals("protected")
                || w.equals("readonly") || w.equals("abstract") || w.equals("override");
        }

        private void eraseOutRange(int start, int end) {
            for (int i = start; i < end; i++) {
                char c = out.charAt(i);
                if (c != '\n' && c != '\r') out.setCharAt(i, ' ');
            }
        }

        // ---- enum (including const enum) -> IIFE object (bidirectional numeric mapping / unidirectional string) ----
        private void transformEnums() {
            int i = 0;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (isIdentifierStart(c)) {
                    if (outKeywordAt(i, "const")) {
                        int after = nextOutNonWhitespace(i + 5);
                        if (outKeywordAt(after, "enum")) { i = transformOneEnum(i); continue; } // Replace starting from 'const'
                    }
                    if (outKeywordAt(i, "enum")) { i = transformOneEnum(i); continue; }
                }
                i++;
            }
        }

        private int transformOneEnum(int start) {
            int i = start;
            if (outKeywordAt(i, "const")) i = nextOutNonWhitespace(i + 5); // Skip 'const' in 'const enum'
            int enumStart = i;
            i = nextOutNonWhitespace(i + 4);
            int nameStart = i;
            while (i < out.length() && isIdentifierPart(out.charAt(i))) i++;
            String name = out.substring(nameStart, i);
            if (name.isEmpty()) return start + 4;
            int braceOpen = -1;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '{') { braceOpen = i; break; }
                if (c == ';' || c == '\n') return i + 1;
                i++;
            }
            if (braceOpen < 0) return out.length();
            int braceClose = matchOutBrace(braceOpen);
            if (braceClose < 0) return out.length();
            String body = out.substring(braceOpen + 1, braceClose);
            // Phase 1 only blanks spans, so phase-2 offsets index the authored source 1:1: erasing
            // never changes length or line count before transform() starts rewriting enum/namespace.
            String iife = generateEnumIife(name, parseEnumMembers(body, braceOpen + 1));
            out.replace(start, braceClose + 1, iife);
            return start + iife.length();
        }

        /**
         * @param valueStart authored offset of the member value, or {@code -1} when the member has no
         *                   value or its position could not be established. Diagnostics use it instead
         *                   of searching the file for a coincidental token match.
         */
        private record EnumMember(String name, String valueExpr, boolean hasValue, int valueStart) {}

        private List<EnumMember> parseEnumMembers(String body, int bodyStart) {
            List<EnumMember> members = new ArrayList<>();
            int i = 0, n = body.length(), segStart = 0;
            while (i < n) {
                char c = body.charAt(i);
                if (c == '\'' || c == '"') { i = skipIn(body, i, c); continue; }
                if (c == '`') { i = skipTemplateIn(body, i); continue; }
                if (c == '/' && i + 1 < n && body.charAt(i + 1) == '/') { while (i < n && body.charAt(i) != '\n') i++; continue; }
                if (c == '/' && i + 1 < n && body.charAt(i + 1) == '*') { i += 2; while (i + 1 < n && !(body.charAt(i) == '*' && body.charAt(i + 1) == '/')) i++; i += 2; continue; }
                if (c == '(') { int d = 0; while (i < n) { char ch = body.charAt(i); if (ch == '\'') { i = skipIn(body, i, '\''); continue; } if (ch == '"') { i = skipIn(body, i, '"'); continue; } if (ch == '(') d++; else if (ch == ')') { d--; if (d == 0) { i++; break; } } i++; } continue; }
                if (c == ',') { addEnumMember(members, body, segStart, i, bodyStart); segStart = i + 1; }
                i++;
            }
            addEnumMember(members, body, segStart, n, bodyStart);
            return members;
        }

        private void addEnumMember(List<EnumMember> members, String body, int start, int end, int bodyStart) {
            String segment = body.substring(start, end);
            String raw = segment.trim();
            if (raw.isEmpty()) return;
            int segmentStart = bodyStart + start + Math.max(0, segment.indexOf(raw.charAt(0)));
            int eq = findTopLevelEq(raw);
            if (eq < 0) { members.add(new EnumMember(stripComment(raw), null, false, -1)); return; }
            String name = stripComment(raw.substring(0, eq).trim());
            String value = raw.substring(eq + 1);
            if (name.isEmpty()) return;
            int valueStart = segmentStart + eq + 1 + (value.length() - value.stripLeading().length());
            members.add(new EnumMember(name, value.trim(), true, valueStart));
        }

        private int findTopLevelEq(String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipIn(s, i, c) - 1; continue; }
                if (c == '=' && (i + 1 >= s.length() || s.charAt(i + 1) != '=')) return i;
                if (c == '=' && i + 1 < s.length() && s.charAt(i + 1) == '=') i++;
            }
            return -1;
        }

        private String stripComment(String s) {
            int i = s.indexOf("//");
            return i < 0 ? s : s.substring(0, i).trim();
        }

        private String generateEnumIife(String name, List<EnumMember> members) {
            StringBuilder sb = new StringBuilder();
            sb.append("var ").append(name).append("; (function (").append(name).append(") { ");
            double next = 0;
            // Name of the previous numeric member (used when the previous member is a "computed value", for runtime self-increment E["prev"] + 1 of the next member without a value).
            // lastNumericKnown=true means next is a compile-time known value (literal number); false means the previous numeric member is a computed value.
            String lastNumericName = null;
            boolean lastNumericKnown = true;
            for (EnumMember m : members) {
                String nm = m.name();
                if (nm.isEmpty()) continue;
                if (!m.hasValue()) {
                    if (lastNumericName != null && !lastNumericKnown) {
                        // Previous was a computed numeric member: runtime self-increment E["prev"] + 1 (compile-time does not know the value of 'prev')
                        String expr = name + "[\"" + lastNumericName + "\"] + 1";
                        sb.append(name).append("[").append(name).append("[\"").append(nm).append("\"] = " + expr + "] = \"").append(nm).append("\"; ");
                        lastNumericName = nm;
                        // This member itself is also of "computed" nature (value depends on runtime 'prev'), subsequent ones continue to +1
                    } else {
                        // Known numeric base (or the first member starts from 0): use compile-time literal
                        sb.append(name).append("[").append(name).append("[\"").append(nm).append("\"] = ").append(formatEnumNumber(next)).append("] = \"").append(nm).append("\"; ");
                        lastNumericName = nm;
                        lastNumericKnown = true;
                        next++;
                    }
                } else if (isStringLit(m.valueExpr())) {
                    sb.append(name).append("[\"").append(nm).append("\"] = ").append(m.valueExpr()).append("; ");
                    // String members do not provide a numeric base
                    lastNumericName = null;
                } else if (isNumberLit(m.valueExpr())) {
                    double num;
                    try {
                        num = parseNumberLit(m.valueExpr());
                    } catch (NumberFormatException e) {
                        throw badEnumNumberLiteral(m.valueExpr(), m.valueStart());
                    }
                    sb.append(name).append("[").append(name).append("[\"").append(nm).append("\"] = ").append(formatEnumNumber(num)).append("] = \"").append(nm).append("\"; ");
                    next = num + 1;
                    lastNumericName = nm;
                    lastNumericKnown = true;
                } else if (looksLikeNumberLiteral(m.valueExpr())) {
                    // Looks like a numeric literal but failed 'isNumberLit' (e.g., 1e, 1e+): report a compile-time error; cannot pass it through as invalid JS.
                    throw badEnumNumberLiteral(m.valueExpr(), m.valueStart());
                } else {
                    // Computed member: value is only known at runtime. As a numeric base (TS treats computed enum members as 'number'),
                    // the next member without a value uses E["thisMember"] + 1 for runtime self-increment.
                    sb.append(name).append("[").append(name).append("[\"").append(nm).append("\"] = ").append(m.valueExpr()).append("] = \"").append(nm).append("\"; ");
                    lastNumericName = nm;
                    lastNumericKnown = false;
                }
            }
            sb.append("})(").append(name).append(" || (").append(name).append(" = {}));");
            return sb.toString();
        }

        private boolean isStringLit(String v) { return !v.isEmpty() && (v.charAt(0) == '"' || v.charAt(0) == '\''); }

        private boolean isNumberLit(String v) {
            // Accept TS numeric literal forms (DEFECT-D2): optional sign, decimal int/float,
            // hex/octal/binary, exponent, and trailing bigint `n`. Excludes exponent-only / empty.
            if (v.isEmpty()) return false;
            String core = v;
            if (core.charAt(0) == '+' || core.charAt(0) == '-') core = core.substring(1);
            if (core.isEmpty() || core.equals("n")) return false;
            if (core.endsWith("n")) core = core.substring(0, core.length() - 1);
            if (core.isEmpty()) return false;
            String lower = core.toLowerCase();
            if (lower.startsWith("0x")) return lower.length() > 2 && lower.substring(2).chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
            if (lower.startsWith("0o")) return lower.length() > 2 && lower.substring(2).chars().allMatch(c -> c >= '0' && c <= '7');
            if (lower.startsWith("0b")) return lower.length() > 2 && lower.substring(2).chars().allMatch(c -> c == '0' || c == '1');
            // decimal int/float with optional exponent (single optional `.` between digits)
            boolean seenDigit = false;
            boolean seenDot = false;
            boolean seenExp = false;
            boolean seenExpDigit = false;
            for (int i = 0; i < core.length(); i++) {
                char c = core.charAt(i);
                if (c >= '0' && c <= '9') {
                    seenDigit = true;
                    if (seenExp) seenExpDigit = true;
                    continue;
                }
                if (c == '.' && !seenDot && !seenExp) { seenDot = true; continue; }
                if ((c == 'e' || c == 'E') && seenDigit && !seenExp) {
                    seenExp = true;
                    if (i + 1 < core.length() && (core.charAt(i + 1) == '+' || core.charAt(i + 1) == '-')) i++;
                    continue;
                }
                return false;
            }
            return seenDigit && (!seenExp || seenExpDigit);
        }

        /** Parses a TS numeric literal accepted by {@link #isNumberLit}. Floats keep their value (0.5 stays 0.5). */
        private double parseNumberLit(String v) {
            String core = v;
            boolean neg = false;
            if (!core.isEmpty() && (core.charAt(0) == '+' || core.charAt(0) == '-')) {
                neg = core.charAt(0) == '-';
                core = core.substring(1);
            }
            if (core.endsWith("n")) core = core.substring(0, core.length() - 1);
            String lower = core.toLowerCase();
            double value;
            if (lower.startsWith("0x")) value = (double) Long.parseUnsignedLong(lower.substring(2), 16);
            else if (lower.startsWith("0o")) value = (double) Long.parseUnsignedLong(lower.substring(2), 8);
            else if (lower.startsWith("0b")) value = (double) Long.parseUnsignedLong(lower.substring(2), 2);
            else value = Double.parseDouble(core);
            return neg ? -value : value;
        }

        /** Output form of enumeration numeric values: integer values do not include a decimal point (0, not 0.0), non-integers retain double literals. */
        private static String formatEnumNumber(double v) {
            if (v == Math.rint(v) && Math.abs(v) <= 9.007199254740992E15) {
                return String.valueOf((long) v);
            }
            return String.valueOf(v);
        }

        /**
         * Enumeration member values that look like numeric literals but failed {@link #isNumberLit} (e.g., {@code 1e}, {@code 1e+},
         * {@code 0b2}, {@code 0o8}, {@code 0xG}). The entire string must match the numeric token form (optional sign,
         * 0x/0o/0b prefix plus alphanumeric, decimal/exponent, n suffix), so {@code 1 + 2},
         * {@code (1+2)}, {@code 0xff + 1}, {@code 1 << 2} do not match. The prefix branch uses a loose character class,
         * as this method is only called after {@link #isNumberLit} returns false, and legal literals are unaffected.
         */
        private boolean looksLikeNumberLiteral(String v) {
            if (v.isEmpty()) return false;
            return v.matches("[+-]?(?:(?:0[xX][0-9a-zA-Z]*)|(?:0[oO][0-9a-zA-Z]*)|(?:0[bB][0-9a-zA-Z]*)|(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d*)?)n?");
        }

        /**
         * @param valueStart authored offset of the offending member value, or {@code -1} when it could
         *                   not be established. The position is never guessed by searching the file for
         *                   the same text: a coincidental earlier comment or token would win that search.
         */
        private NekoCompileException badEnumNumberLiteral(String literal, int valueStart) {
            return diagnostic("Invalid TypeScript enum numeric literal '" + literal + "' in " + file, valueStart);
        }

        // ---- namespace (single level) / module -> IIFE, 'export' members are converted to Name.member=member in batches at the end ----
        private void transformNamespaces() {
            int i = 0;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (isIdentifierStart(c) && (outKeywordAt(i, "namespace") || outKeywordAt(i, "module"))) {
                    i = transformOneNamespace(i);
                    continue;
                }
                i++;
            }
        }

        private int transformOneNamespace(int start) {
            int kwLen = outKeywordAt(start, "namespace") ? 9 : 6;
            int i = nextOutNonWhitespace(start + kwLen);
            int nameStart = i;
            while (i < out.length() && isIdentifierPart(out.charAt(i))) i++;
            String name = out.substring(nameStart, i);
            if (name.isEmpty()) return start + kwLen;
            int braceOpen = -1;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '{') { braceOpen = i; break; }
                if (c == ';' || c == '\n') return i + 1;
                i++;
            }
            if (braceOpen < 0) return out.length();
            int braceClose = matchOutBrace(braceOpen);
            if (braceClose < 0) return out.length();
            String body = out.substring(braceOpen + 1, braceClose);
            String wrapped = generateNamespaceIife(name, body);
            out.replace(start, braceClose + 1, wrapped);
            return transformNamespacesInRange(start, start + wrapped.length());
        }

        /** Transforms nested namespaces and adjusts the scan limit as each replacement changes the output length. */
        private int transformNamespacesInRange(int start, int endExclusive) {
            int limit = Math.min(endExclusive, out.length());
            int i = Math.max(0, start);
            while (i < limit) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (isIdentifierStart(c) && (outKeywordAt(i, "namespace") || outKeywordAt(i, "module"))) {
                    int oldLength = out.length();
                    int transformedEnd = transformOneNamespace(i);
                    if (transformedEnd > i) {
                        limit += out.length() - oldLength;
                        i = transformedEnd;
                        continue;
                    }
                }
                i++;
            }
            return limit;
        }

        private String generateNamespaceIife(String name, String body) {
            List<String> members = new ArrayList<>();
            StringBuilder cleaned = new StringBuilder();
            int i = 0, n = body.length(), lastCopy = 0;
            while (i < n) {
                char c = body.charAt(i);
                if (c == '\'' || c == '"') { i = skipIn(body, i, c); continue; }
                if (c == '`') { i = skipTemplateIn(body, i); continue; }
                if (c == '/' && i + 1 < n && body.charAt(i + 1) == '/') { while (i < n && body.charAt(i) != '\n') i++; continue; }
                if (c == '/' && i + 1 < n && body.charAt(i + 1) == '*') { i += 2; while (i + 1 < n && !(body.charAt(i) == '*' && body.charAt(i + 1) == '/')) i++; i += 2; continue; }
                if (c == '/') {
                    int regexEnd = skipBodyRegex(body, i);
                    if (regexEnd > i) { i = regexEnd; continue; }
                }
                if (bodyKeywordAt(body, i, "export")) {
                    int after = skipInWs(body, i + 6);
                    int kwLen = declKwLen(body, after);
                    if (kwLen > 0) {
                        int ms = skipInWs(body, after + kwLen);
                        int me = ms;
                        while (me < n && isIdentifierPart(body.charAt(me))) me++;
                        String member = body.substring(ms, me);
                        if (!member.isEmpty()) members.add(member);
                        int namespaceEnd = (kwLen == 9 || kwLen == 6) ? namespaceBodyEnd(body, me) : -1;
                        cleaned.append(body, lastCopy, i).append("      "); // 6 spaces to erase 'export'
                        lastCopy = i + 6;
                        i = namespaceEnd > me ? namespaceEnd : i + 6;
                        continue;
                    } else if (kwLen == -1) {
                        // interface/type: No runtime value; remove 'export' but do not export as a member.
                        // Its declaration body was already erased to spaces by phase1, leaving only naked 'export' + name; erase 'export' here too.
                        cleaned.append(body, lastCopy, i).append("      ");
                        lastCopy = i + 6;
                        i += 6;
                        continue;
                    }
                }
                i++;
            }
            cleaned.append(body, lastCopy, n);
            StringBuilder sb = new StringBuilder();
            // Preceding 'var' declaration: namespaces often appear in functions/IIFE bodies, and the original (name || (name={})) relied on an 'name' declared in the outer scope.
            // Strict mode would cause a ReferenceError in nested scopes. 'var' can be redeclared, which is compatible with multiple namespace merges.
            sb.append("var ").append(name).append(";\n");
            sb.append("(function (").append(name).append(") {\n").append(cleaned).append('\n');
            for (String m : members) sb.append(name).append('.').append(m).append(" = ").append(m).append(";\n");
            sb.append("})(").append(name).append(" || (").append(name).append(" = {}));");
            return sb.toString();
        }

        private int skipBodyRegex(CharSequence body, int slash) {
            if (!bodyRegexStartsAt(body, slash)) return slash;
            int i = slash + 1;
            boolean inClass = false;
            while (i < body.length()) {
                char c = body.charAt(i);
                if (c == '\\') { i += 2; continue; }
                if (c == '[') inClass = true;
                else if (c == ']') inClass = false;
                else if (c == '/' && !inClass) {
                    i++;
                    while (i < body.length() && isIdentifierPart(body.charAt(i))) i++;
                    return i;
                }
                i++;
            }
            return body.length();
        }

        private int namespaceBodyEnd(String body, int afterName) {
            int i = afterName;
            while (i < body.length()) {
                char c = body.charAt(i);
                if (c == '\'' || c == '"') { i = skipIn(body, i, c); continue; }
                if (c == '`') { i = skipTemplateIn(body, i); continue; }
                if (c == '/' && i + 1 < body.length() && body.charAt(i + 1) == '/') { while (i < body.length() && body.charAt(i) != '\n') i++; continue; }
                if (c == '/' && i + 1 < body.length() && body.charAt(i + 1) == '*') { i += 2; while (i + 1 < body.length() && !(body.charAt(i) == '*' && body.charAt(i + 1) == '/')) i++; i += 2; continue; }
                if (c == '/') { int regexEnd = skipBodyRegex(body, i); if (regexEnd > i) { i = regexEnd; continue; } }
                if (c == '{') {
                    int close = matchingBodyBrace(body, i);
                    return close < 0 ? -1 : close + 1;
                }
                if (c == ';' || c == '\n' || c == '\r') return -1;
                i++;
            }
            return -1;
        }

        private int matchingBodyBrace(CharSequence body, int open) {
            int depth = 0;
            int i = open;
            while (i < body.length()) {
                char c = body.charAt(i);
                if (c == '\'' || c == '"') { i = skipIn(body, i, c); continue; }
                if (c == '`') { i = skipTemplateIn(body, i); continue; }
                if (c == '/' && i + 1 < body.length() && body.charAt(i + 1) == '/') { while (i < body.length() && body.charAt(i) != '\n') i++; continue; }
                if (c == '/' && i + 1 < body.length() && body.charAt(i + 1) == '*') { i += 2; while (i + 1 < body.length() && !(body.charAt(i) == '*' && body.charAt(i + 1) == '/')) i++; i += 2; continue; }
                if (c == '/') { int regexEnd = skipBodyRegex(body, i); if (regexEnd > i) { i = regexEnd; continue; } }
                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) return i;
                i++;
            }
            return -1;
        }

        private boolean bodyRegexStartsAt(CharSequence body, int slash) {
            int previous = slash - 1;
            while (previous >= 0 && Character.isWhitespace(body.charAt(previous))) previous--;
            if (previous < 0) return true;
            char c = body.charAt(previous);
            if ("=(:,[!&|?;{}<>+-*/%\n\r".indexOf(c) >= 0) return true;
            if (!isIdentifierPart(c)) return false;
            int start = previous;
            while (start > 0 && isIdentifierPart(body.charAt(start - 1))) start--;
            String word = body.subSequence(start, previous + 1).toString();
            return word.equals("return") || word.equals("throw") || word.equals("case")
                || word.equals("delete") || word.equals("void") || word.equals("typeof")
                || word.equals("instanceof") || word.equals("in") || word.equals("of");
        }

        private boolean bodyKeywordAt(String body, int i, String kw) {
            int n = body.length();
            if (i < 0 || i + kw.length() > n || !body.startsWith(kw, i)) return false;
            boolean before = i == 0 || !isIdentifierPart(body.charAt(i - 1));
            boolean after = i + kw.length() >= n || !isIdentifierPart(body.charAt(i + kw.length()));
            return before && after;
        }
        private int skipInWs(String body, int i) { int n = body.length(); while (i < n && Character.isWhitespace(body.charAt(i))) i++; return i; }
        private int declKwLen(String body, int i) {
            if (bodyKeywordAt(body, i, "function")) return 8;
            if (bodyKeywordAt(body, i, "const")) return 5;
            if (bodyKeywordAt(body, i, "class")) return 5;
            if (bodyKeywordAt(body, i, "namespace")) return 9;
            if (bodyKeywordAt(body, i, "module")) return 6;
            if (bodyKeywordAt(body, i, "let")) return 3;
            if (bodyKeywordAt(body, i, "var")) return 3;
            // interface/type: No value member produced at runtime, but phase1 has already erased their declaration bodies to spaces.
            // Return the length to remove their 'export' keyword and avoid naked 'export' in the IIFE body.
            // Note: Do not add these members to the 'members' list (they have no runtime binding to export).
            if (bodyKeywordAt(body, i, "interface")) return -1; // Sentinel: remove 'export' but do not treat as a value member
            if (bodyKeywordAt(body, i, "type")) return -1;
            return 0;
        }

        // ---- Parameter properties constructor(public name) -> erase modifiers + insert this.name=name into constructor body ----
        private void transformParameterProperties() {
            int i = 0;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (isIdentifierStart(c) && outKeywordAt(i, "constructor")) {
                    int next = transformOneConstructor(i);
                    if (next > i) { i = next; continue; }
                }
                i++;
            }
        }

        private int transformOneConstructor(int start) {
            ClassContext classContext = enclosingClassForTopLevelConstructor(start);
            if (classContext == null) return start + 11;
            int i = nextOutNonWhitespace(start + 11); // Skip 'constructor'
            if (i >= out.length() || out.charAt(i) != '(') return start + 11;
            int parenClose = matchOutParen(i);
            if (parenClose < 0) return start + 11;
            String params = out.substring(i + 1, parenClose);
            List<String> assigned = new ArrayList<>();
            String cleanedParams = cleanParamProperties(params, assigned);
            if (assigned.isEmpty()) return parenClose + 1; // No parameter properties, skip
            int j = parenClose + 1;
            int braceOpen = -1;
            while (j < out.length()) {
                char c = out.charAt(j);
                if (c == '\'' || c == '"') { j = skipOutString(j, c); continue; }
                if (c == '`') { j = skipOutTemplate(j); continue; }
                if (c == '/' && j + 1 < out.length() && out.charAt(j + 1) == '/') { j = skipOutLine(j + 2); continue; }
                if (c == '/' && j + 1 < out.length() && out.charAt(j + 1) == '*') { j = skipOutBlock(j + 2); continue; }
                if (c == '{') { braceOpen = j; break; }
                if (c == ';') return j + 1; // Declaration without body
                j++;
            }
            if (braceOpen < 0) return parenClose + 1;

            int insertionPoint = braceOpen + 1;
            if (classContext.derived()) {
                int superCallEnd = findTopLevelSuperCallEnd(braceOpen);
                if (superCallEnd < 0) {
                    throw diagnostic("Cannot transform derived constructor parameter properties in "
                        + file + ": constructor has no legal top-level super(...) call.", braceOpen);
                }
                insertionPoint = superCallEnd;
            }

            out.replace(i + 1, parenClose, cleanedParams); // Erase parameter modifiers
            int delta = cleanedParams.length() - (parenClose - i - 1);
            insertionPoint += delta; // Parameter replacement occurs before the constructor body and insertion point
            StringBuilder assigns = new StringBuilder();
            if (classContext.derived()) assigns.append(';');
            for (String name : assigned) assigns.append(" this.").append(name).append(" = ").append(name).append(";");
            out.insert(insertionPoint, assigns.toString());
            return insertionPoint + assigns.length();
        }

        private record ClassContext(boolean derived, int bodyOpen) {}

        /** Recognize only the 'constructor' at the first level of the class body, and determine if the class header contains a top-level 'extends'. */
        private ClassContext enclosingClassForTopLevelConstructor(int constructorStart) {
            ClassContext innermost = null;
            int i = 0;
            while (i < constructorStart) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (isIdentifierStart(c) && outKeywordAt(i, "class")) {
                    int bodyOpen = findClassBodyOpen(i + 5);
                    if (bodyOpen < 0 || bodyOpen >= constructorStart) { i += 5; continue; }
                    int bodyClose = matchOutBrace(bodyOpen);
                    if (bodyClose >= constructorStart && classBodyDepthAt(bodyOpen, constructorStart) == 0) {
                        innermost = new ClassContext(classHeaderHasExtends(i + 5, bodyOpen), bodyOpen);
                    }
                }
                i++;
            }
            return innermost;
        }

        private int findClassBodyOpen(int from) {
            int paren = 0, bracket = 0, i = from;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < out.length() && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (c == '(') paren++;
                else if (c == ')' && paren > 0) paren--;
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                else if (c == '{' && paren == 0 && bracket == 0) return i;
                else if (c == ';' && paren == 0 && bracket == 0) return -1;
                i++;
            }
            return -1;
        }

        private boolean classHeaderHasExtends(int from, int bodyOpen) {
            int paren = 0, bracket = 0, i = from;
            while (i < bodyOpen) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < bodyOpen && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < bodyOpen && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (c == '(') paren++;
                else if (c == ')' && paren > 0) paren--;
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                else if (paren == 0 && bracket == 0 && isIdentifierStart(c) && outKeywordAt(i, "extends")) return true;
                i++;
            }
            return false;
        }

        private int classBodyDepthAt(int bodyOpen, int position) {
            int depth = 0, i = bodyOpen + 1;
            while (i < position) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/' && i + 1 < position && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (c == '/' && i + 1 < position && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                if (c == '{') depth++;
                else if (c == '}' && depth > 0) depth--;
                i++;
            }
            return depth;
        }

        /** Return the position after the semicolon of a legal, independent top-level {@code super(...);} statement. */
        private int findTopLevelSuperCallEnd(int bodyOpen) {
            int bodyClose = matchOutBrace(bodyOpen);
            if (bodyClose < 0) return -1;
            int brace = 0, paren = 0, bracket = 0, i = bodyOpen + 1;
            while (i < bodyClose) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipOutSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '{') brace++;
                else if (c == '}' && brace > 0) brace--;
                else if (c == '(') paren++;
                else if (c == ')' && paren > 0) paren--;
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                else if (brace == 0 && paren == 0 && bracket == 0 && isIdentifierStart(c) && outKeywordAt(i, "super")
                    && standaloneStatementStart(i, bodyOpen)) {
                    int callOpen = nextOutTrivia(i + 5);
                    if (callOpen < bodyClose && out.charAt(callOpen) == '(') {
                        int callClose = matchOutParen(callOpen);
                        if (callClose >= 0 && callClose < bodyClose) {
                            int statementEnd = nextOutTrivia(callClose + 1);
                            if (statementEnd < bodyClose && out.charAt(statementEnd) == ';') return statementEnd + 1;
                            if (statementEnd == bodyClose) return callClose + 1;
                            if (hasOutLineTerminator(callClose + 1, statementEnd)
                                && !continuesSuperCallExpression(statementEnd)) return callClose + 1;
                        }
                    }
                }
                i++;
            }
            return -1;
        }

        private boolean standaloneStatementStart(int keywordStart, int bodyOpen) {
            int previous = keywordStart - 1;
            boolean crossedLine = false;
            while (previous > bodyOpen && Character.isWhitespace(out.charAt(previous))) {
                crossedLine |= out.charAt(previous) == '\n' || out.charAt(previous) == '\r';
                previous--;
            }
            if (previous == bodyOpen || out.charAt(previous) == ';' || out.charAt(previous) == '}') return true;
            return crossedLine && ".?=,+-*/%&|!<>([{".indexOf(out.charAt(previous)) < 0;
        }

        private boolean hasOutLineTerminator(int from, int to) {
            for (int i = from; i < to; i++) {
                char c = out.charAt(i);
                if (c == '\n' || c == '\r') return true;
            }
            return false;
        }

        private boolean continuesSuperCallExpression(int tokenStart) {
            char c = out.charAt(tokenStart);
            return c == '.' || c == '?' || c == '[' || c == '(' || c == '`';
        }

        private String cleanParamProperties(String params, List<String> assigned) {
            StringBuilder cleaned = new StringBuilder();
            int i = 0, n = params.length(), segStart = 0;
            while (i < n) {
                char c = params.charAt(i);
                if (c == '\'' || c == '"') { i = skipIn(params, i, c); continue; }
                if (c == '(' || c == '[' || c == '{') { i = skipPair(params, i); continue; }
                if (c == ',') {
                    if (segStart < i) processParam(params.substring(segStart, i), cleaned, assigned);
                    cleaned.append(',');
                    segStart = i + 1;
                }
                i++;
            }
            if (segStart < n) processParam(params.substring(segStart, n), cleaned, assigned);
            return cleaned.toString();
        }

        private void processParam(String seg, StringBuilder cleaned, List<String> assigned) {
            int i = 0, n = seg.length();
            int firstNonMod = -1;
            String paramName = null;
            boolean parameterProperty = false;
            while (i < n) {
                while (i < n && Character.isWhitespace(seg.charAt(i))) i++;
                if (i >= n || !isIdentifierPart(seg.charAt(i))) break;
                int ws = i;
                while (i < n && isIdentifierPart(seg.charAt(i))) i++;
                String word = seg.substring(ws, i);
                boolean isMod = word.equals("public") || word.equals("private") || word.equals("protected") || word.equals("readonly");
                if (isMod) {
                    parameterProperty = true;
                    continue;
                }
                firstNonMod = ws;
                paramName = word;
                break;
            }
            if (firstNonMod >= 0 && paramName != null && parameterProperty) {
                assigned.add(paramName);
                cleaned.append(seg, firstNonMod, n);
            } else {
                cleaned.append(seg);
            }
        }

        private int skipPair(String s, int open) {
            char oc = s.charAt(open);
            char cc = oc == '(' ? ')' : (oc == '[' ? ']' : '}');
            int depth = 0, i = open, n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipIn(s, i, c); continue; }
                if (c == oc) depth++;
                else if (c == cc) { depth--; if (depth == 0) return i + 1; }
                i++;
            }
            return n;
        }

        private int matchOutParen(int open) {
            int depth = 0, i = open;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipOutSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '(') depth++;
                else if (c == ')') { depth--; if (depth == 0) return i; }
                i++;
            }
            return -1;
        }

        // ---- 'out' (StringBuilder) scanning aids ----
        private int skipOutString(int start, char quote) {
            int i = start + 1;
            while (i < out.length()) { char c = out.charAt(i); if (c == '\\') { i += 2; continue; } if (c == quote) return i + 1; i++; }
            return out.length();
        }
        private int skipOutTemplate(int start) {
            return skipTemplateIn(out, start);
        }

        /** Skips a complete template, including escapes and nested templates inside substitutions. */
        private int skipTemplateIn(CharSequence text, int start) {
            int i = start + 1;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\\') { i += 2; continue; }
                if (c == '`') return i + 1;
                if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    int close = matchingBodyBrace(text, i + 1);
                    if (close < 0) return text.length();
                    i = close + 1;
                    continue;
                }
                i++;
            }
            return text.length();
        }
        private int skipOutLine(int start) { while (start < out.length() && out.charAt(start) != '\n') start++; return start; }
        private int skipOutBlock(int start) {
            while (start + 1 < out.length() && !(out.charAt(start) == '*' && out.charAt(start + 1) == '/')) start++;
            return Math.min(out.length(), start + 2);
        }
        private int skipOutSlash(int slash) {
            if (slash + 1 >= out.length()) return slash;
            char next = out.charAt(slash + 1);
            if (next == '/') return skipOutLine(slash + 2);
            if (next == '*') return skipOutBlock(slash + 2);
            if (!looksLikeOutRegexStart(slash)) return slash;
            int i = slash + 1;
            boolean inClass = false;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\\') { i += 2; continue; }
                if (c == '[') inClass = true;
                else if (c == ']') inClass = false;
                else if (c == '/' && !inClass) {
                    i++;
                    while (i < out.length() && isIdentifierPart(out.charAt(i))) i++;
                    return i;
                }
                if (c == '\n' || c == '\r') return slash;
                i++;
            }
            return out.length();
        }
        private boolean looksLikeOutRegexStart(int slash) {
            int previous = previousOutNonTrivia(slash - 1);
            if (previous < 0) return true;
            char c = out.charAt(previous);
            if ("=(:,[!&|?;{}\n\r".indexOf(c) >= 0) return true;
            if (!isIdentifierPart(c)) return false;
            int start = previous;
            while (start > 0 && isIdentifierPart(out.charAt(start - 1))) start--;
            String word = out.substring(start, previous + 1);
            return word.equals("return") || word.equals("throw") || word.equals("case")
                || word.equals("delete") || word.equals("void") || word.equals("typeof")
                || word.equals("instanceof") || word.equals("in") || word.equals("of");
        }
        private int previousOutNonTrivia(int i) {
            while (i >= 0) {
                while (i >= 0 && Character.isWhitespace(out.charAt(i))) i--;
                if (i > 0 && out.charAt(i) == '/' && out.charAt(i - 1) == '*') {
                    i -= 2;
                    while (i > 0 && !(out.charAt(i - 1) == '/' && out.charAt(i) == '*')) i--;
                    i -= 2;
                    continue;
                }
                int lineStart = i;
                while (lineStart >= 0 && out.charAt(lineStart) != '\n' && out.charAt(lineStart) != '\r') lineStart--;
                int comment = out.substring(lineStart + 1, i + 1).lastIndexOf("//");
                if (comment >= 0) { i = lineStart - 1; continue; }
                return i;
            }
            return -1;
        }
        private int nextOutNonWhitespace(int i) { while (i < out.length() && Character.isWhitespace(out.charAt(i))) i++; return i; }
        private int nextOutTrivia(int i) {
            while (i < out.length()) {
                if (Character.isWhitespace(out.charAt(i))) { i++; continue; }
                if (i + 1 < out.length() && out.charAt(i) == '/' && out.charAt(i + 1) == '/') { i = skipOutLine(i + 2); continue; }
                if (i + 1 < out.length() && out.charAt(i) == '/' && out.charAt(i + 1) == '*') { i = skipOutBlock(i + 2); continue; }
                break;
            }
            return i;
        }
        private boolean outKeywordAt(int i, String kw) {
            if (i < 0 || i + kw.length() > out.length() || !out.substring(i, i + kw.length()).equals(kw)) return false;
            boolean before = i == 0 || !isIdentifierPart(out.charAt(i - 1));
            boolean after = i + kw.length() >= out.length() || !isIdentifierPart(out.charAt(i + kw.length()));
            return before && after;
        }
        private int matchOutBrace(int open) {
            int depth = 0, i = open;
            while (i < out.length()) {
                char c = out.charAt(i);
                if (c == '\'' || c == '"') { i = skipOutString(i, c); continue; }
                if (c == '`') { i = skipOutTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipOutSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) return i; }
                i++;
            }
            return -1;
        }
        private int skipIn(CharSequence s, int i, char quote) {
            i++;
            int n = s.length();
            while (i < n) { char c = s.charAt(i); if (c == '\\') { i += 2; continue; } if (c == quote) return i + 1; i++; }
            return n;
        }

        /** Whether 'type'/'interface' is followed by an identifier (declaration name), to distinguish between type alias and property/variable name 'type'. */
        private boolean typeDeclAfter(int afterKw) {
            int p = nextNonWhitespace(afterKw);
            return p < length && isIdentifierStart(source.charAt(p));
        }

        private int eraseTypeDeclaration(int start) {
            int end = statementOrBlockDeclarationEnd(start);
            eraseRange(start, end);
            return end;
        }

        private int eraseDeclare(int start) {
            int after = nextNonWhitespace(start + "declare".length());
            if (startsWithKeyword(after, "global") || startsWithKeyword(after, "module") || startsWithKeyword(after, "namespace")
                    || startsWithKeyword(after, "const") || startsWithKeyword(after, "let") || startsWithKeyword(after, "var")
                    || startsWithKeyword(after, "class") || startsWithKeyword(after, "abstract") || startsWithKeyword(after, "function")
                    || startsWithKeyword(after, "enum")) {
                // Erase the entire environment declaration: 'declare const x: T' - erasing only 'declare' would leave an uninitialized 'const' -> SyntaxError
                int end = statementOrBlockDeclarationEnd(start);
                eraseRange(start, end);
                return end;
            }
            eraseRange(start, after);
            return after;
        }

        private int eraseStatement(int start) {
            int end = statementEnd(start);
            eraseRange(start, end);
            return end;
        }

        /**
         * Erases only the {@code export type ...} / {@code export interface ...} declaration itself,
         * unlike {@link #eraseStatement}, which scans until a newline or semicolon. Another statement
         * may follow {@code export interface I { ... }} closely, especially inside a namespace, and
         * 'statementEnd' would run past it and swallow the following statements.
         * 'statementOrBlockDeclarationEnd' is used instead: it stops after matching the interface's
         * {...} body.
         */
        private int eraseExportTypeDeclaration(int start) {
            int typeStart = nextNonWhitespace(start + "export".length());
            int typeEnd = readIdentifierEnd(typeStart);
            int clauseStart = nextNonWhitespace(typeEnd);
            if (startsWithKeyword(typeStart, "type") && clauseStart < length
                    && (source.charAt(clauseStart) == '{' || source.charAt(clauseStart) == '*')) {
                int reexportEnd = statementEnd(start);
                eraseRange(start, reexportEnd);
                return reexportEnd;
            }
            int end = statementOrBlockDeclarationEnd(start);
            eraseRange(start, end);
            return end;
        }

        private int eraseImplements(int start, int wordEnd) {
            int end = wordEnd;
            while (end < length) {
                char c = source.charAt(end);
                if (c == '\'' || c == '"') {
                    end = skipString(end, c);
                    continue;
                }
                if (c == '`') {
                    end = skipTemplate(end);
                    continue;
                }
                if (c == '{') break;
                end++;
            }
            eraseRange(start, end);
            return end;
        }

        private int eraseAssertion(int start, int wordEnd) {
            if (!assertionContext(start)) {
                return wordEnd;
            }
            int end = typeExpressionEnd(wordEnd);
            eraseRange(start, end);
            return end;
        }

        private boolean assertionContext(int start) {
            if (aliasClause[start]) {
                // 'as' in import/export value alias clause (import { A as B }): native JS alias, keep as is.
                return false;
            }
            int previous = previousNonWhitespace(start - 1);
            return previous >= 0 && ")]}'\"`abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_$".indexOf(source.charAt(previous)) >= 0;
        }

        private boolean typeKeywordAfter(int index) {
            int i = nextNonWhitespace(index);
            return startsWithKeyword(i, "type");
        }

        private boolean typeExportAfter(int index) {
            int i = nextNonWhitespace(index);
            return startsWithKeyword(i, "type") || startsWithKeyword(i, "interface");
        }

        /** Whether the named import/export block of an import/export statement contains an inline 'type' modifier (TS 4.5+: { real, type T }). */
        private boolean hasInlineTypeSpecifier(int afterKeyword) {
            int brace = findNamedClauseBrace(afterKeyword);
            if (brace < 0) return false;
            int close = matchingCloseBracket(brace, '{', '}');
            if (close < 0) return false;
            return findInlineTypeInBlock(brace + 1, close) >= 0;
        }

        /**
         * Locate the named clause '{' after the import/export statement header (skipping default binding, commas, and the 'type' keyword:
         * {@code import def, { type A, B } from '...'} ). If a declaration body boundary (keywords like =、(、const/function/
         * class) or a semicolon/newline is encountered, there is no named clause; same applies to module strings (import '...').
         */
        private int findNamedClauseBrace(int from) {
            int i = from;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') return -1;
                if (c == '{') return i;
                if (c == '*' || c == ',') { i++; continue; }
                if (c == '=' || c == '(' || c == ';' || c == '\n') return -1;
                if (isIdentifierStart(c)) {
                    int end = readIdentifierEnd(i + 1);
                    String word = source.substring(i, end);
                    if (word.equals("type") || word.equals("as")) { i = end; continue; }
                    if (word.equals("const") || word.equals("let") || word.equals("var")
                            || word.equals("function") || word.equals("class") || word.equals("default")
                            || word.equals("async") || word.equals("abstract") || word.equals("enum")
                            || word.equals("interface") || word.equals("namespace") || word.equals("module")) {
                        return -1;
                    }
                    i = end;
                    continue;
                }
                i++;
            }
            return -1;
        }

        /** Finds the 'type X' specifier at the top level (comma depth 0) within a {...} block; returns the start of the 'type' keyword, or -1 if not found. */
        private int findInlineTypeInBlock(int from, int to) {
            int depth = 0, i = from;
            while (i < to) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '{' || c == '[' || c == '(') { depth++; i++; continue; }
                if (c == '}' || c == ']' || c == ')') { if (depth > 0) depth--; i++; continue; }
                if (depth == 0 && isIdentifierStart(c)) {
                    int end = readIdentifierEnd(i + 1);
                    String word = source.substring(i, end);
                    if (word.equals("type")) {
                        // Confirm it is a specifier: followed by an identifier (type X), not a property named 'type'.
                        int after = nextNonWhitespace(end);
                        if (after < to && isIdentifierStart(source.charAt(after))) return i;
                    }
                }
                i++;
            }
            return -1;
        }

        /** Erases all inline 'type X' specifiers (including trailing commas) in an import/export named block, keeping value bindings and the entire statement. */
        private int eraseInlineTypeSpecifiers(int start, int afterKeyword) {
            int brace = findNamedClauseBrace(afterKeyword);
            if (brace < 0) return afterKeyword;
            int close = matchingCloseBracket(brace, '{', '}');
            if (close < 0) return afterKeyword;
            // Repeatedly erase inline 'type' specifiers within the block.
            int scanFrom = brace + 1;
            while (true) {
                int typeStart = findInlineTypeInBlock(scanFrom, close);
                if (typeStart < 0) break;
                // 'type' specifier range: from typeStart to before the next value binding (including trailing comma and whitespace)
                int specEnd = readIdentifierEnd(typeStart + 1); // end of the 'type' keyword
                int nameStart = nextNonWhitespace(specEnd);
                int nameEnd = readIdentifierEnd(nameStart + 1); // end of X
                int eraseTo = nameEnd;
                // Consume following comma (if any)
                int afterComma = nextNonWhitespace(nameEnd);
                if (afterComma < close && source.charAt(afterComma) == ',') {
                    eraseTo = afterComma + 1;
                } else {
                    // No trailing comma: consume preceding comma ('type X' is the last one)
                    int before = previousNonWhitespace(typeStart - 1);
                    if (before >= brace + 1 && source.charAt(before) == ',') {
                        typeStart = before;
                    }
                }
                eraseRange(typeStart, eraseTo);
                scanFrom = eraseTo; // eraseRange preserves length, close remains unchanged
            }
            return close + 1;
        }

        /** Matches the pair of closing brackets starting from 'open' (skipping strings/templates), returns -1 if not found. */
        private int matchingCloseBracket(int open, char openCh, char closeCh) {
            int depth = 0, i = open;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == openCh) depth++;
                else if (c == closeCh) { depth--; if (depth == 0) return i; }
                i++;
            }
            return -1;
        }

        private boolean genericTypeArgumentsAt(int start) {
            int previous = previousNonWhitespace(start - 1);
            if (previous < 0) return false;
            char previousChar = source.charAt(previous);
            // Valid lead-in for generic arguments or a generic arrow: an identifier/`)`/`]` (foo<T>, (a)<T>), or the
            // contexts a generic arrow can follow: assignment (`=` in const id = <T>...), argument (`,`/`(`),
            // statement start (`return`/`=>`/`{`/`;`). In none of them can `<...>(` or `<...> =>` be a comparison
            // (a comparison never continues with a `(` call), so erasing the angle group is safe.
            boolean arrowContext = previousChar == '=' || previousChar == ','
                || previousChar == '(' || previousChar == '{' || previousChar == ';'
                || previousChar == '\n' || previousChar == '\r' || previousChar == ':';
            if (!isIdentifierPart(previousChar) && previousChar != ')' && previousChar != ']' && previousChar != '.' && !arrowContext) {
                return false;
            }
            // After a statement-start keyword (return/throw/yield/await and the like) a <T>( is also a generic arrow
            if (isIdentifierPart(previousChar)) {
                int ws = previous;
                while (ws > 0 && isIdentifierPart(source.charAt(ws - 1))) ws--;
                String word = source.substring(ws, previous + 1);
                if (word.equals("return") || word.equals("throw") || word.equals("yield")
                    || word.equals("await") || word.equals("default") || word.equals("case")) {
                    arrowContext = true;
                }
            }
            int close = matchingAngle(start);
            if (close < 0) return false;
            int next = nextNonWhitespace(close + 1);
            if (next >= length) return false;
            char nextChar = source.charAt(next);
            if (startsWithKeyword(next, "extends") || startsWithKeyword(next, "implements")) {
                for (ClassBodyRange range : classBodies) {
                    if (range.headerStart() < start && start < range.open()) return true;
                }
            }
            // <T>(  or  <T> =>  -- generic arrow or generic call
            if (nextChar == '(' || nextChar == '{') return true;
            if (nextChar == '=' && next + 1 < length && source.charAt(next + 1) == '>') return true;
            // Outside an arrow context (identifier/) / ] lead-in) <T>( is still a generic call (e.g. foo<T>(x))
            if ((isIdentifierPart(previousChar) || previousChar == ')' || previousChar == ']' || previousChar == '.') && nextChar == '(') {
                return true;
            }
            // Generic arguments in an expression that do not continue into a call: foo<T>, / foo<T>) / foo<T>] / foo<T>;
            // (typical case: the Foo<number> expression left after lowering a TSX generic component <Foo<number>/>).
            // Only accepted when the lead-in is an identifier/`)`/`]`; a `,`/`=` lead-in is arrow context, covered above.
            if ((isIdentifierPart(previousChar) || previousChar == ')' || previousChar == ']' || previousChar == '.')
                && (nextChar == ',' || nextChar == ')' || nextChar == ']' || nextChar == ';'
                    || nextChar == '\n' || nextChar == '\r')) {
                return true;
            }
            return arrowContext && (nextChar == '(' || (nextChar == '=' && next + 1 < length && source.charAt(next + 1) == '>'));
        }

        private boolean typeAnnotationAt(int colon) {
            // A case/default/label colon is not a type annotation (the digit before `case 1:` also counts as an
            // identifier character; the label colon in `outer: for` would otherwise erase the whole loop as a type).
            if (caseOrLabelColon[colon]) return false;
            // The `:` of a ternary cond ? a : b is not a type annotation. Checked first, so every lead-in is covered,
            // including )/]/}/digit/string; isTernaryColon already excludes ?: optional parameters and ?. optional chaining.
            if (isTernaryColon(colon)) return false;
            int previous = previousNonWhitespace(colon - 1);
            if (previous < 0) return false;
            char previousChar = source.charAt(previous);
            // Optional parameter name?: T -> `?` before `:`, an identifier before `?`
            if (previousChar == '?') {
                int beforeQ = previousNonWhitespace(previous - 1);
                return beforeQ >= 0 && isIdentifierPart(source.charAt(beforeQ)) && !objectLiteralPropertyColon(colon);
            }
            if (!isIdentifierPart(previousChar) && previousChar != ')' && previousChar != ']' && previousChar != '}') return false;
            // A `:` after `)` must be told apart: a function declaration parameter list (return type) vs. a method call/ternary
            if (previousChar == ')' && !parenIsReturnType(previous)) return false;
            int next = nextNonWhitespace(colon + 1);
            if (next >= length) return false;
            char nextChar = source.charAt(next);
            if (nextChar == ':' || nextChar == ',' || nextChar == ';' || nextChar == ')') return false;
            return !objectLiteralPropertyColon(colon);
        }

        private int thisParameterStart(int colon) {
            int end = previousNonWhitespace(colon - 1);
            if (end < 0 || !isIdentifierPart(source.charAt(end))) return -1;
            int start = end;
            while (start > 0 && isIdentifierPart(source.charAt(start - 1))) start--;
            if (!source.substring(start, end + 1).equals("this")) return -1;
            int before = previousNonWhitespace(start - 1);
            return before >= 0 && source.charAt(before) == '(' ? start : -1;
        }

        /** Whether `)` closes a function declaration parameter list (a valid return-type position); returns false for a method call or ternary. */
        private boolean parenIsReturnType(int closeParen) {
            int openParen = backwardMatchParen(closeParen);
            if (openParen < 0) return false;
            int before = previousNonWhitespace(openParen - 1);
            if (before < 0) return true;
            char bc = source.charAt(before);
            if (bc == '.') return false; // obj.method() call
            if (bc == '>' || bc == ')') return true; // <T>(params) or nested ))
            if (!isIdentifierPart(bc)) return true; // = ( , ; { [ + - etc. -> arrow/method
            int start = before;
            while (start > 0 && isIdentifierPart(source.charAt(start - 1))) start--;
            if (source.substring(start, before + 1).equals("function")) return true;
            int beforeName = previousNonWhitespace(start - 1);
            if (beforeName >= 0) {
                char bic = source.charAt(beforeName);
                if (bic == '.') return false; // a.b() call
                // Named calls in initializers and conditional branches cannot declare a return type.
                if (bic == '=' || bic == '?') return false;
                if (isIdentifierPart(bic)) {
                    int bs = beforeName;
                    while (bs > 0 && isIdentifierPart(source.charAt(bs - 1))) bs--;
                    String prev = source.substring(bs, beforeName + 1);
                    if (prev.equals("function")) return true;
                    if (prev.equals("if") || prev.equals("while") || prev.equals("for") || prev.equals("switch") || prev.equals("catch") || prev.equals("with") || prev.equals("return")) return false;
                }
                return true; // { ; , etc. -> method shorthand
            }
            return false;
        }

        /** Uses lexical pairs so literal and comment parentheses cannot change return-type detection. */
        private int backwardMatchParen(int closeParen) {
            return parenOpens.getOrDefault(closeParen, -1);
        }

        /** Whether the `:` sits in a ternary expression (a matching `?` at the same level in front, excluding `?:` optional parameters and `?.` optional chaining). */
        private boolean isTernaryColon(int colon) {
            int depth = 0;
            int i = colon - 1;
            while (i >= 0) {
                char c = source.charAt(i);
                if (c == ')' || c == ']' || c == '}') depth++;
                else if (c == '(' || c == '[' || c == '{') {
                    if (depth == 0) return false;
                    depth--;
                } else if (c == ';' || c == '\n' || c == '\r') return false;
                else if (depth == 0 && c == '?') {
                    int after = nextNonWhitespace(i + 1);
                    if (after < length && (source.charAt(after) == ':' || source.charAt(after) == '.')) { i--; continue; }
                    int before = previousNonWhitespace(i - 1);
                    if (before < 0) return false;
                    char bc = source.charAt(before);
                    if (bc == '.') return false;
                    if (isIdentifierPart(bc) || bc == ')' || bc == ']' || Character.isDigit(bc) || bc == '\'' || bc == '"') return true;
                    return false;
                }
                i--;
            }
            return false;
        }

        private boolean objectLiteralPropertyColon(int colon) {
            int previous = previousNonWhitespace(colon - 1);
            int next = nextNonWhitespace(colon + 1);
            if (previous < 0 || next >= length) return false;
            int beforeProperty = propertyStart(previous) - 1;
            int previousToken = previousNonWhitespace(beforeProperty);
            if (previousToken < 0) return false;
            char c = source.charAt(previousToken);
            if (c != '{' && c != ',') return false;
            int objectStart = enclosingOpenBrace(previousToken);
            if (objectStart < 0 || !objectLiteralContext(objectStart)) return false;
            // Method shorthand parameter list: the `,` before a property name sits inside a `(` that follows
            // the object literal's `{` and is still open (e.g. the comma before b in
            // { m(a: T, b: U) {} }). That comma separates parameters, not properties, and the `:`
            // after it is a parameter type annotation that must go through normal erasure.
            if (c == ',' && parenDepthBetween(objectStart + 1, previousToken) > 0) return false;
            return true;
        }

        /** Depth of unclosed `(` in the range [from, to); strings, templates and comments are skipped so
         *  parentheses inside literals do not interfere. */
        private int parenDepthBetween(int from, int to) {
            int depth = 0;
            int i = Math.max(0, from);
            while (i < to && i < length) {
                char ch = source.charAt(i);
                if (ch == '\'' || ch == '"') {
                    i = skipString(i, ch);
                    continue;
                }
                if (ch == '`') {
                    i = skipTemplate(i);
                    continue;
                }
                if (ch == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) {
                        i = skipped;
                        continue;
                    }
                }
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
                i++;
            }
            return depth;
        }

        private int enclosingOpenBrace(int before) {
            int depth = 0;
            for (int i = before; i >= 0; i--) {
                char c = source.charAt(i);
                if (c == '}') {
                    depth++;
                } else if (c == '{') {
                    if (depth == 0) return i;
                    depth--;
                }
            }
            return -1;
        }

        private boolean objectLiteralContext(int openBrace) {
            int previous = previousNonWhitespace(openBrace - 1);
            if (previous < 0) return true;
            char c = source.charAt(previous);
            // When an identifier precedes the `{`: `{ }` after return/throw is an object literal; after else/do/try/finally or a function name it is a block
            if (isIdentifierPart(c)) {
                int end = previous + 1;
                int start = previous;
                while (start > 0 && isIdentifierPart(source.charAt(start - 1))) start--;
                String kw = source.substring(start, end);
                if (kw.equals("return") || kw.equals("throw")) return true;
                // key: { } -- an object literal after a property name (a `:` before the key), e.g. constants: { ... }
                int beforeKw = previousNonWhitespace(start - 1);
                return beforeKw >= 0 && source.charAt(beforeKw) == ':';
            }
            // A `{` after => opens an arrow function body block, not an object literal, e.g. (() => { function f(a: T) {} })
            if (c == '>' && previous - 1 >= 0 && source.charAt(previous - 1) == '=') return false;
            if (c == '>' && classBodyHeader(openBrace)) return false;
            return "=(:,[!&|?;{}<>+-*/%".indexOf(c) >= 0;
        }

        private boolean classBodyHeader(int bodyOpen) {
            int angle = 0;
            int paren = 0;
            int bracket = 0;
            for (int i = bodyOpen - 1; i >= 0; i--) {
                char c = source.charAt(i);
                if (c == '>') angle++;
                else if (c == '<' && angle > 0) angle--;
                else if (c == ')') paren++;
                else if (c == '(' && paren > 0) paren--;
                else if (c == ']') bracket++;
                else if (c == '[' && bracket > 0) bracket--;
                if (angle == 0 && paren == 0 && bracket == 0) {
                    if (c == ';' || c == '{' || c == '}') return false;
                    if (isIdentifierStart(c) && startsWithKeyword(i, "class")) return true;
                }
            }
            return false;
        }

        private int propertyStart(int endInclusive) {
            int i = endInclusive;
            if (source.charAt(i) == '\'' || source.charAt(i) == '"') {
                char quote = source.charAt(i);
                i--;
                while (i >= 0) {
                    if (source.charAt(i) == quote) return i;
                    i--;
                }
                return endInclusive;
            }
            while (i >= 0 && isIdentifierPart(source.charAt(i))) i--;
            return i + 1;
        }

        private int typeAnnotationEnd(int start) {
            return typeExpressionEnd(start);
        }

        private int typeExpressionEnd(int start) {
            int i = start;
            int angle = 0;
            int paren = 0;
            int bracket = 0;
            int brace = 0;
            int conditionalQuestions = 0;
            boolean conditionalType = false;
            while (i < length) {
                char c = source.charAt(i);
                boolean closedTypeParen = false;
                if (c == '\'' || c == '"') {
                    i = skipString(i, c);
                    continue;
                }
                if (c == '`') {
                    i = skipTemplate(i);
                    continue;
                }
                if (c == '/' && i + 1 < length) {
                    if (source.charAt(i + 1) == '/') { i = NekoSourceLexerBase.skipLineComment(source, length, i + 2); continue; }
                    if (source.charAt(i + 1) == '*') { i = NekoSourceLexerBase.skipBlockComment(source, length, i + 2); continue; }
                }
                if (c == '<') angle++;
                else if (c == '>' && angle > 0) angle--;
                else if (c == '(') paren++;
                else if (c == ')' && paren > 0) {
                    paren--;
                    closedTypeParen = paren == 0;
                }
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                else if (c == '{') {
                    if (angle == 0 && paren == 0 && bracket == 0 && brace == 0
                            && !typeObjectBraceAt(i, start, conditionalType)) return i;
                    brace++;
                } else if (c == '}' && brace > 0) {
                    brace--;
                    i++;
                    continue;
                }
                if (angle == 0 && paren == 0 && bracket == 0 && brace == 0) {
                    if (startsWithKeyword(i, "extends")) conditionalType = true;
                    if (c == ')' && closedTypeParen) {
                        int afterClose = nextSourceTrivia(i + 1);
                        if (afterClose + 1 < length && source.charAt(afterClose) == '=' && source.charAt(afterClose + 1) == '>') {
                            i = afterClose + 2;
                            continue;
                        }
                        if (afterClose < length && ("|&[.?".indexOf(source.charAt(afterClose)) >= 0
                                || startsWithKeyword(afterClose, "extends"))) {
                            i++;
                            continue;
                        }
                    }
                    if (conditionalType && c == '?' && !(i + 1 < length && (source.charAt(i + 1) == '.' || source.charAt(i + 1) == '?'))) {
                        conditionalQuestions++;
                        i++;
                        continue;
                    }
                    if (conditionalType && c == ':' && conditionalQuestions > 0) {
                        conditionalQuestions--;
                        i++;
                        continue;
                    }
                    // A `:` at the top level (not inside an object, function or generics) must separate a ternary
                    // (e.g. expr as T : fallback), so the type ends here. Object type { a: T } colons have
                    // brace>0 and function/arrow type (a: T)=>R colons have paren>0; neither reaches this
                    // branch.
                    if (c == '=' || c == ',' || c == ';' || c == ')' || c == '}' || c == ':' || c == '\n' || c == '\r') {
                        return i;
                    }
                }
                i++;
            }
            return i;
        }

        private boolean typeObjectBraceAt(int braceStart, int typeStart, boolean conditionalType) {
            if (braceStart == nextSourceTrivia(typeStart)) return true;
            int previous = previousNonWhitespace(braceStart - 1);
            if (previous < 0) return false;
            char c = source.charAt(previous);
            if (c == '&' || c == '|') return true;
            if (conditionalType && (c == '?' || c == ':')) return true;
            if (c == '>' && previous > 0 && source.charAt(previous - 1) == '=') return true;
            if (isIdentifierPart(c)) {
                int wordStart = previous;
                while (wordStart > 0 && isIdentifierPart(source.charAt(wordStart - 1))) wordStart--;
                return source.substring(wordStart, previous + 1).equals("keyof");
            }
            return false;
        }

        private boolean definiteAssignmentAt(int bang) {
            int previous = previousNonWhitespace(bang - 1);
            int next = nextNonWhitespace(bang + 1);
            // Only `:` (x!: T) or `;` (x!;) is accepted, never `=`, so the `!` in `a != b` is not mistaken for a
            // definite assignment assertion and erased.
            return previous >= 0 && next < length && isIdentifierPart(source.charAt(previous)) && (source.charAt(next) == ':' || source.charAt(next) == ';');
        }

        /** Non-null assertion `a!.x` / `a!`: the previous non-whitespace char is an identifier part, `)` or
         *  `]`, and the char after `!` is not `=` (excludes `!=` and `!==`). */
        private boolean nonNullAssertionAt(int bang) {
            int previous = previousNonWhitespace(bang - 1);
            if (previous < 0) return false;
            char pc = source.charAt(previous);
            if (!isIdentifierPart(pc) && pc != ')' && pc != ']') return false;
            if (bang + 1 >= length || source.charAt(bang + 1) != '=') {
                // A `!` after a keyword such as `return !!x` or `typeof !!x` is logical negation, not an
                // assertion (an assertion can only follow an expression). Without this exclusion
                // return !!x would be erased to return !x, inverting the logic
                return !keywordImmediatelyBefore(previous);
            }
            return false;
        }

        /** Whether the identifier ending at previous (inclusive) is a statement or operator keyword; an
         *  assertion can never directly follow one. */
        private boolean keywordImmediatelyBefore(int end) {
            int start = end;
            while (start > 0 && isIdentifierPart(source.charAt(start - 1))) start--;
            if (start == end) return false;
            switch (source.substring(start, end + 1)) {
                case "return", "typeof", "case", "delete", "void",
                     "throw", "new", "do", "else", "yield", "await" -> { return true; }
                default -> { return false; }
            }
        }

        /**
         * A bare optional parameter {@code b?} with no {@code : T} type annotation: the {@code ?} is
         * preceded by an identifier and followed (ignoring whitespace) by `,` or `)`. The {@code ?} of a
         * ternary {@code x ? a : b} is followed by an expression, and optional chaining {@code a?.} and
         * nullish coalescing {@code a??} do not match either, so none of them are affected.
         */
        private boolean bareOptionalParameterAt(int question) {
            int previous = previousNonWhitespace(question - 1);
            if (previous < 0 || !isIdentifierPart(source.charAt(previous))) return false;
            int next = nextNonWhitespace(question + 1);
            return next < length && (source.charAt(next) == ',' || source.charAt(next) == ')');
        }

        /**
         * Whether this is a decorator ({@code @Component} / {@code @Log(...)}).
         * The criterion: {@code @} is directly followed by an identifier start character, and the
         * previous non-whitespace character starts a declaration ({@code \n} / {@code }} / {@code ;} /
         * {@code {} or the start of the file). A decorator always comes before a declaration (class,
         * method, property, parameter) and never follows a value expression.
         */
        private boolean decoratorAt(int at) {
            if (at + 1 >= length) return false;
            char next = source.charAt(at + 1);
            if (!isIdentifierStart(next)) return false;
            int previous = previousNonWhitespace(at - 1);
            if (previous < 0) return true; // Start of the file
            char pc = source.charAt(previous);
            return pc == '\n' || pc == '\r' || pc == '}' || pc == ';' || pc == '{' || pc == ')';
        }

        private int statementOrBlockDeclarationEnd(int start) {
            int bodyStart = -1;
            int i = start;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') {
                    i = skipString(i, c);
                    continue;
                }
                if (c == '`') {
                    i = skipTemplate(i);
                    continue;
                }
                if (c == '{') {
                    bodyStart = i;
                    break;
                }
                if (c == ';' || c == '\n' || c == '\r') return i + 1;
                i++;
            }
            if (bodyStart < 0) return statementEnd(start);
            if (conditionalTypeAliasBefore(start, bodyStart)) return statementEnd(start);
            int bodyEnd = matchingCloseBrace(bodyStart);
            if (bodyEnd < 0) return length;
            // A type alias may continue past the matched body (e.g. `type A = { .. } | B`
            // or `{ .. }[]`): keep consuming to the statement end so no type residue leaks
            // into the erased JS. A real statement boundary (newline, `}`) ends as before.
            int after = bodyEnd + 1;
            while (after < length && (source.charAt(after) == ' ' || source.charAt(after) == '\t')) after++;
            if (after < length) {
                char c = source.charAt(after);
                if (c == '|' || c == '&' || c == '[' || c == '.' || c == '(' || c == ';') {
                    return statementEnd(after);
                }
            }
            return bodyEnd + 1;
        }

        private boolean conditionalTypeAliasBefore(int start, int before) {
            int keyword = nextNonWhitespace(start);
            if (startsWithKeyword(keyword, "export")) keyword = nextNonWhitespace(keyword + "export".length());
            if (!startsWithKeyword(keyword, "type")) return false;

            int angle = 0;
            int paren = 0;
            int bracket = 0;
            boolean sawExtends = false;
            int i = start;
            while (i < before) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '<') angle++;
                else if (c == '>' && angle > 0) angle--;
                else if (c == '(') paren++;
                else if (c == ')' && paren > 0) paren--;
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                if (angle == 0 && paren == 0 && bracket == 0) {
                    if (startsWithKeyword(i, "extends")) sawExtends = true;
                    if (sawExtends && c == '?' && !(i + 1 < before && (source.charAt(i + 1) == '.' || source.charAt(i + 1) == '?'))) {
                        return true;
                    }
                }
                i++;
            }
            return false;
        }

        private int statementEnd(int start) {
            int i = start;
            int paren = 0;
            int bracket = 0;
            int brace = 0;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') {
                    i = skipString(i, c);
                    continue;
                }
                if (c == '`') {
                    i = skipTemplate(i);
                    continue;
                }
                if (c == '(') paren++;
                else if (c == ')' && paren > 0) paren--;
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                else if (c == '{') brace++;
                else if (c == '}' && brace > 0) brace--;
                if (paren == 0 && bracket == 0 && brace == 0 && (c == ';' || c == '\n' || c == '\r')) return i + 1;
                i++;
            }
            return i;
        }

        private int matchingCloseBrace(int open) {
            int depth = 0;
            int i = open;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') {
                    i = skipString(i, c);
                    continue;
                }
                if (c == '`') {
                    i = skipTemplate(i);
                    continue;
                }
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
                i++;
            }
            return -1;
        }

        private int matchingAngle(int open) {
            int depth = 0;
            int i = open;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') {
                    i = skipString(i, c);
                    continue;
                }
                if (c == '`') {
                    i = skipTemplate(i);
                    continue;
                }
                if (c == '<') depth++;
                else if (c == '>') {
                    depth--;
                    if (depth == 0) return i;
                }
                if ((c == ';' || c == '\n' || c == '\r') && depth > 0) return -1;
                i++;
            }
            return -1;
        }

        /** Returns the end of a bodyless function signature, including newline-terminated overloads. */
        private int functionOverloadEnd(int wordEnd) {
            int i = nextNonWhitespace(wordEnd);
            if (i < length && source.charAt(i) == '*') i = nextNonWhitespace(i + 1); // generator
            if (i < length && isIdentifierStart(source.charAt(i))) i = readIdentifierEnd(i + 1); // the name
            i = nextNonWhitespace(i);
            if (i < length && source.charAt(i) == '<') { // generic parameters <T>
                int closeAngle = matchingAngle(i);
                if (closeAngle < 0) return -1;
                i = nextNonWhitespace(closeAngle + 1);
            }
            if (i >= length || source.charAt(i) != '(') return -1;
            int close = matchingParen(i);
            if (close < 0) return -1;
            int after = nextNonWhitespace(close + 1);
            if (after < length && source.charAt(after) == ':') { // return type annotation
                after = typeExpressionEnd(after + 1);
            } else after = close + 1;
            return bodylessSignatureEnd(after);
        }

        /** Returns the end of a bodyless method signature without consuming ordinary calls in class fields or methods. */
        private int methodOverloadEnd(int nameStart, int nameEnd) {
            if (!insideClassBody(nameStart) || !methodMemberPrefix(nameStart)) return -1;
            int i = nextNonWhitespace(nameEnd);
            if (i < length && source.charAt(i) == '?') i = nextNonWhitespace(i + 1);
            if (i < length && source.charAt(i) == '<') {
                int closeAngle = matchingAngle(i);
                if (closeAngle < 0) return -1;
                i = nextNonWhitespace(closeAngle + 1);
            }
            if (i >= length || source.charAt(i) != '(') return -1;
            int close = matchingParen(i);
            if (close < 0) return -1;
            int after = nextNonWhitespace(close + 1);
            boolean hasReturnType = after < length && source.charAt(after) == ':';
            if (hasReturnType && isTernaryColon(after)) return -1;
            if (hasReturnType) {
                after = typeExpressionEnd(after + 1);
            } else after = close + 1;
            return bodylessSignatureEnd(after);
        }

        /** A body on the next line remains an implementation; trivia alone can terminate an overload. */
        private int bodylessSignatureEnd(int afterSignature) {
            int next = afterSignature;
            boolean lineBreak = false;
            while (next < length) {
                char c = source.charAt(next);
                if (Character.isWhitespace(c)) {
                    lineBreak |= c == '\n' || c == '\r';
                    next++;
                } else if (c == '/' && next + 1 < length && source.charAt(next + 1) == '/') {
                    next = NekoSourceLexerBase.skipLineComment(source, length, next + 2);
                } else if (c == '/' && next + 1 < length && source.charAt(next + 1) == '*') {
                    int end = NekoSourceLexerBase.skipBlockComment(source, length, next + 2);
                    for (int i = next; i < end; i++) {
                        lineBreak |= source.charAt(i) == '\n' || source.charAt(i) == '\r';
                    }
                    next = end;
                } else break;
            }
            if (next < length && source.charAt(next) == ';') return next + 1;
            if (next < length && "{=|&[.?:".indexOf(source.charAt(next)) >= 0) return -1;
            return lineBreak ? next : -1;
        }

        private boolean methodMemberPrefix(int nameStart) {
            int previous = previousNonWhitespace(nameStart - 1);
            // A previous overload can end with a type rather than a semicolon. An expression operator
            // before the new line still belongs to a field initializer and cannot introduce a method.
            if (previous >= 0 && (isIdentifierPart(source.charAt(previous))
                    || "]>)".indexOf(source.charAt(previous)) >= 0)) {
                for (int i = previous + 1; i < nameStart; i++) {
                    if (source.charAt(i) == '\n' || source.charAt(i) == '\r') return true;
                }
            }
            while (previous >= 0 && isIdentifierPart(source.charAt(previous))) {
                int wordEnd = previous + 1;
                int wordStart = previous;
                while (wordStart > 0 && isIdentifierPart(source.charAt(wordStart - 1))) wordStart--;
                String word = source.substring(wordStart, wordEnd);
                if (!word.equals("public") && !word.equals("private") && !word.equals("protected")
                        && !word.equals("static") && !word.equals("readonly") && !word.equals("abstract")
                        && !word.equals("async") && !word.equals("override") && !word.equals("declare")
                        && !word.equals("get") && !word.equals("set")) return false;
                previous = previousNonWhitespace(wordStart - 1);
            }
            return previous >= 0 && "{};*".indexOf(source.charAt(previous)) >= 0;
        }

        /** Checks pre-scanned class-body ranges so an ordinary call inside a method is not mistaken for an overload. */
        private boolean insideClassBody(int position) {
            int open = innermostBraceOpenAt(position);
            if (open < 0) return false;
            int low = 0;
            int high = classBodies.size() - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int candidate = classBodies.get(middle).open();
                if (candidate == open) return true;
                if (candidate < open) low = middle + 1;
                else high = middle - 1;
            }
            return false;
        }

        private void scanClassBodies() {
            int i = 0;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (isIdentifierStart(c) && startsWithKeyword(i, "class")) {
                    int bodyOpen = classBodyOpenAfter(i + "class".length());
                    if (bodyOpen >= 0) {
                        int bodyClose = matchingClassBodyClose(bodyOpen);
                        if (bodyClose >= 0) classBodies.add(new ClassBodyRange(i, bodyOpen, bodyClose));
                    }
                    i += "class".length();
                    continue;
                }
                i++;
            }
            classBodies.sort((left, right) -> Integer.compare(left.open(), right.open()));
        }

        private void scanBraceRanges() {
            int[] opens = new int[16];
            int size = 0;
            java.util.ArrayDeque<Integer> parentheses = new java.util.ArrayDeque<>();
            int i = 0;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '{') {
                    if (size == opens.length) {
                        int[] expanded = new int[opens.length * 2];
                        System.arraycopy(opens, 0, expanded, 0, size);
                        opens = expanded;
                    }
                    opens[size++] = i;
                } else if (c == '}' && size > 0) {
                    braceRanges.add(new BraceRange(opens[--size], i));
                } else if (c == '(') {
                    parentheses.push(i);
                } else if (c == ')' && !parentheses.isEmpty()) {
                    parenOpens.put(i, parentheses.pop());
                }
                i++;
            }
            braceRanges.sort((left, right) -> Integer.compare(left.open(), right.open()));
        }

        private int innermostBraceOpenAt(int position) {
            int low = 0;
            int high = braceRanges.size() - 1;
            int candidate = -1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (braceRanges.get(middle).open() <= position) {
                    candidate = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            for (int i = candidate; i >= 0; i--) {
                BraceRange range = braceRanges.get(i);
                if (range.close() > position) return range.open();
            }
            return -1;
        }

        private int classBodyOpenAfter(int keywordEnd) {
            int i = nextNonWhitespace(keywordEnd);
            if (i < length && source.charAt(i) == '{') return i;
            if (i < length && isIdentifierStart(source.charAt(i)) && !startsWithKeyword(i, "extends")) {
                i = nextNonWhitespace(readIdentifierEnd(i + 1));
            } else if (i >= length || !startsWithKeyword(i, "extends")) {
                return -1;
            }

            int angle = 0;
            int paren = 0;
            int bracket = 0;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '<') angle++;
                else if (c == '>' && angle > 0) angle--;
                else if (c == '(') paren++;
                else if (c == ')' && paren > 0) paren--;
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                else if (c == '{' && angle == 0 && paren == 0 && bracket == 0) return i;
                else if ((c == ';' || c == '}') && angle == 0 && paren == 0 && bracket == 0) return -1;
                i++;
            }
            return -1;
        }

        private int matchingClassBodyClose(int bodyOpen) {
            int depth = 0;
            int i = bodyOpen;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) return i;
                i++;
            }
            return -1;
        }

        private int matchingParen(int open) {
            int depth = 0;
            int i = open;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) { i = skipped; continue; }
                }
                if (c == '(') depth++;
                else if (c == ')') { depth--; if (depth == 0) return i; }
                i++;
            }
            return -1;
        }

        private int skipSlash(int slash) {
            return NekoSourceLexerBase.skipSlash(source, length, slash);
        }

        private int skipString(int start, char quote) {
            return NekoSourceLexerBase.skipString(source, length, start, quote);
        }

        private int skipTemplate(int start) {
            return skipTemplateIn(source, start);
        }

        private int nextNonWhitespace(int index) {
            return NekoSourceLexerBase.nextNonWhitespace(source, length, index);
        }

        private int nextSourceTrivia(int index) {
            int i = nextNonWhitespace(index);
            while (i + 1 < length && source.charAt(i) == '/') {
                char next = source.charAt(i + 1);
                if (next == '/') i = NekoSourceLexerBase.skipLineComment(source, length, i + 2);
                else if (next == '*') i = NekoSourceLexerBase.skipBlockComment(source, length, i + 2);
                else break;
                i = nextNonWhitespace(i);
            }
            return i;
        }

        private int previousNonWhitespace(int index) {
            return NekoSourceLexerBase.previousNonWhitespace(source, length, index);
        }

        private int readIdentifierEnd(int start) {
            return NekoSourceLexerBase.readIdentifierEnd(source, length, start);
        }

        private boolean startsWithKeyword(int start, String keyword) {
            return NekoSourceLexerBase.startsWithKeyword(source, length, start, keyword);
        }

        private boolean isIdentifierStart(char c) {
            return NekoSourceLexerBase.isIdentifierStart(c);
        }

        private boolean isIdentifierPart(char c) {
            return NekoSourceLexerBase.isIdentifierPart(c);
        }

        private void eraseRange(int start, int end) {
            int safeEnd = Math.min(length, Math.max(start, end));
            for (int i = start; i < safeEnd; i++) {
                char c = source.charAt(i);
                if (c != '\n' && c != '\r') {
                    out.setCharAt(i, ' ');
                }
            }
        }

        private NekoCompileException unsupported(String syntax, int index) {
            String hint;
            if (syntax.contains("decorator")) {
                hint = "Decorators are not supported: NekoJS is a scripting engine, not a TypeScript framework. Replace the decorator with a plain function call (e.g. wrap your class/function with a helper instead of @Decorator).";
            } else {
                hint = "Use plain erasable TypeScript or register a compiler plugin for this syntax.";
            }
            return diagnostic("Unsupported TypeScript syntax '" + syntax + "' in " + file + " at " + position(index) + ". " + hint, index);
        }

        private String position(int index) {
            return NekoSourceLexerBase.position(source, length, index);
        }

        /**
         * Typed diagnostic: keeps the message text but also the authored line/column.
         * A negative index means "position unknown" and is published as such — never as 1:1.
         */
        private NekoCompileException diagnostic(String message, int index) {
            if (index < 0 || index > length) {
                return new NekoCompileException(message, -1, -1);
            }
            int line = 1;
            int column = 1;
            for (int i = 0; i < index; i++) {
                if (source.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new NekoCompileException(message, line, column);
        }
    }
}
