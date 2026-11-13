package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Node module type declaration extractor: automatically generates {@code declare module 'node:xxx' {...}}
 * declarations from Node module {@code .ts} source code to keep implementation and declaration in sync.
 *
 * <p>Node modules are script-style (IIFE + {@code globalThis.__nekoNodeDefine(ids, exports)}),
 * because script context evaluation does not support ES {@code export}/{@code import}. Type annotations are
 * removed by Eraser.
 *
 * <p>{@link #registerBuiltin} is called at the {@code registerNodeTypeDocs} extension point: it scans all
 * {@code .ts} modules in {@code nekojs/node/modules.list} and automatically extracts declarations (fully
 * automated, no manual fallback).
 *
 * <p>Extracts a simplified version (targeted at common typed declaration forms in Node modules), not a
 * complete {@code tsc --declaration}: complex types (conditional/mapped/infer) are kept as-is, and
 * unparsable members are downgraded to {@code unknown} instead of failing entirely.
 *
 * <p>Note: exported members must have {@code export} (e.g., {@code export function/const}), otherwise
 * invisible to {@code require()} consumers inside {@code declare module}, causing completion to fail.
 */
public final class NodeModuleTypeDocs {
    private static final String RESOURCE_ROOT = "nekojs/node/";
    private static final String MANIFEST = RESOURCE_ROOT + "modules.list";

    private NodeModuleTypeDocs() {}

    /**
     * Registers built-in Node module declarations: scans .ts modules in {@code modules.list} and uses {@link #extractTS}
     * to automatically extract type declarations. All built-in Node modules have been rewritten as typed .ts files,
     * with declarations fully automated and no manual fallback.
     */
    public static void registerBuiltin(TypeDocsRegister registry) {
        scanBuiltinTypeScriptModules(registry);
        registerCommonJsGlobals(registry);
        registerConsoleGlobal(registry);
    }

    /** Declares the GraalJS console supplied by every context; Neko's warn/debug wrappers retain these calls. */
    private static void registerConsoleGlobal(TypeDocsRegister registry) {
        String declaration = """
                interface Console {
                    log(...values: unknown[]): void;
                    info(...values: unknown[]): void;
                    debug(...values: unknown[]): void;
                    dir(...values: unknown[]): void;
                    error(...values: unknown[]): void;
                    warn(...values: unknown[]): void;
                    assert(condition?: unknown, ...values: unknown[]): void;
                    clear(): void;
                    count(label?: unknown): void;
                    countReset(label?: unknown): void;
                    group(...values: unknown[]): void;
                    groupCollapsed(...values: unknown[]): void;
                    groupEnd(): void;
                    time(label?: unknown): void;
                    timeEnd(label?: unknown): void;
                    timeLog(label?: unknown, ...values: unknown[]): void;
                }
                declare var console: Console;
                """;
        registry.registerManualDeclaration(ManualDeclarationCatalogEntry.of(
                "nekojs.node-auto.console-global", declaration,
                "GraalJS console methods available in script contexts.", List.of("console.info('ready')")));
    }

    /**
     * CommonJS interop globals ({@code require}/{@code module}/{@code exports}). The runtime engine
     * provides these shims, so the type side must declare them too. jsconfig disables Automatic Type
     * Acquisition (ATA only fetches @types packages from the network by dependency name and stalls the
     * language service offline or behind a proxy). Without this declaration {@code require()} reports
     * TS2580 in .ts scripts and has no completion.
     */
    private static void registerCommonJsGlobals(TypeDocsRegister registry) {
        String decl = """
                declare function require(id: string): any;
                declare const module: { exports: any };
                declare const exports: any;
                """;
        registry.registerManualDeclaration(ManualDeclarationCatalogEntry.of(
            "nekojs.node-auto.cjs-globals",
            decl,
            "CommonJS interop globals provided by the script engine (require/module/exports).",
            List.of()));
    }

    /** Scans the .ts entries of {@code modules.list}, extracts their type declarations and registers
     *  them, skipping anything that is not .ts and modules with no type information. */
    private static void scanBuiltinTypeScriptModules(TypeDocsRegister registry) {
        String manifest = readResource(MANIFEST);
        if (manifest == null) return;
        for (String raw : manifest.split("\\R")) {
            String entry = raw.trim();
            if (entry.isEmpty() || entry.startsWith("#") || !entry.endsWith(".ts")) continue;
            String source = readResource(RESOURCE_ROOT + entry);
            if (source == null) continue;
            String decl = extractTS(source);
            if (decl.isBlank()) continue;
            String moduleId = switch (entry) {
                case "modules/jsx-runtime.ts" -> "nekojs/jsx-runtime";
                case "modules/jsx-workspace.ts" -> "nekojs/jsx-workspace";
                default -> entry.replace('/', '.').replace(".ts", "");
            };
            String declarationId = "nekojs.node-auto." + moduleId.replace('/', '.');
            registry.registerManualDeclaration(ManualDeclarationCatalogEntry.of(
                declarationId,
                decl,
                "Auto-extracted from '" + entry + "' (TypeScript source).",
                List.of()));
        }
    }

    private static String readResource(String path) {
        try (InputStream in = NodeModuleTypeDocs.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Extracts type declarations from the source of a .ts node module.
     *
     * <p>Parses {@code __nekoNodeDefine(ids, exports)} to get the module id list and the exports
     * object. It maps the exports object's members (method shorthand {@code name(params): ret {}},
     * property {@code name: value}, shorthand reference {@code name}) to the typed function/const
     * declarations in the module to build signatures, and collects top-level
     * {@code type}/{@code interface}/{@code enum}/{@code namespace} declarations, wrapping them in
     * {@code declare module 'id' { ... }}. Type syntax (generics, unions, intersections, literals) is
     * kept as text.
     *
     * @param source .ts source (script style: an IIFE plus {@code __nekoNodeDefine})
     * @return the generated {@code declare module} text; an empty string when no type information
     *         can be extracted
     */
    public static String extractTS(String source) {
        if (source == null || source.isBlank()) return "";
        return new TSExtractor(source).extract();
    }

    /** Parsed result of a __nekoNodeDefine call: the module id list and the source range of the
     *  second argument (the exports expression). */
    private record DefineCall(List<String> ids, int exportsStart, int exportsEnd) {}

    /** Parsed result of one exported member: the signature text (null means skipped) and where the
     *  member ends. */
    private record Member(String sig, int next) {}

    // ============================ .ts extractor ============================

    /**
     * Scans .ts source read-only, finds the {@code __nekoNodeDefine} call and its exported members,
     * and generates a declare module. All scanning primitives skip strings, templates and comments,
     * match bracket and generic pairs, and never modify the source.
     */
    static final class TSExtractor {
        private final String s;
        private final int n;

        TSExtractor(String source) {
            this.s = source;
            this.n = source.length();
        }

        String extract() {
            List<DefineCall> calls = findDefineCalls();
            if (calls.isEmpty()) return "";
            String typeDecls = collectTypeDeclarations();
            StringBuilder out = new StringBuilder();
            for (DefineCall call : calls) {
                if (call.ids().isEmpty()) continue;
                List<String> members = resolveExports(call.exportsStart(), call.exportsEnd());
                if (members.isEmpty() && typeDecls.isBlank()) continue;
                StringBuilder body = new StringBuilder();
                for (String m : members) body.append(indentBlock(m));
                if (!typeDecls.isBlank()) body.append(indentBlock(typeDecls));
                for (String id : call.ids()) {
                    out.append("declare module '").append(id).append("' {\n").append(body).append("}\n");
                }
            }
            return out.toString();
        }

        // ---- Locate the __nekoNodeDefine call ----

        private List<DefineCall> findDefineCalls() {
            List<DefineCall> calls = new ArrayList<>();
            String needle = "__nekoNodeDefine";
            int from = 0;
            while (true) {
                int idx = s.indexOf(needle, from);
                if (idx < 0) return calls;
                from = idx + needle.length();
                if (idx > 0 && isIdentPart(s.charAt(idx - 1))) continue; // __nekoNodeDefineX
                int parenOpen = skipWsAndComments(idx + needle.length());
                if (parenOpen >= n || s.charAt(parenOpen) != '(') continue; // Not a call (e.g. an assignment)
                int parenClose = matchParen(parenOpen);
                if (parenClose < 0) continue;
                int[] args = splitTopLevelArgs(parenOpen, parenClose);
                if (args == null) continue;
                List<String> ids = parseIds(args[0], args[1]);
                if (ids.isEmpty()) continue;
                calls.add(new DefineCall(ids, args[2], args[3]));
            }
        }

        /** Splits the argument list into ranges for the first two top-level arguments [s1,e1, s2,e2]
         *  (end exclusive). Returns null when there are fewer than two. */
        private int[] splitTopLevelArgs(int open, int close) {
            int i = open + 1;
            int firstStart = skipWsAndComments(i);
            int firstEnd = -1;
            int secondStart = -1;
            int depth = 0;
            while (i < close) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 1 < close) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                }
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth--;
                else if (c == ',' && depth == 0 && firstEnd < 0) {
                    firstEnd = trimEnd(i);
                    secondStart = skipWsAndComments(i + 1);
                }
                i++;
            }
            if (firstEnd < 0) return null; // only one parameter
            return new int[] { firstStart, firstEnd, secondStart, trimEnd(close) };
        }

        /** Parses the id argument: an array literal {@code ['a', "b"]} or a single string literal {@code 'a'}. */
        private List<String> parseIds(int start, int end) {
            List<String> ids = new ArrayList<>();
            if (start >= end) return ids;
            char c = s.charAt(start);
            if (c == '[') {
                int i = start + 1;
                while (i < end) {
                    i = skipWsAndComments(i);
                    if (i >= end) break;
                    char ch = s.charAt(i);
                    if (ch == '\'' || ch == '"') {
                        int q = skipString(i, ch);
                        if (q <= end) ids.add(s.substring(i + 1, q - 1));
                        i = q;
                    } else {
                        i++;
                    }
                }
            } else if (c == '\'' || c == '"') {
                int q = skipString(start, c);
                if (q <= end) ids.add(s.substring(start + 1, q - 1));
            }
            return ids;
        }

        // ---- Resolve the exports expression ----

        private List<String> resolveExports(int start, int end) {
            int i = skipWsAndComments(start);
            if (i >= end) return List.of();
            char c = s.charAt(i);
            if (c == '{') {
                int close = matchBrace(i);
                if (close < 0) close = end - 1;
                return parseObjectMembers(i + 1, close);
            }
            if (isIdentStart(c)) {
                int nameEnd = readIdent(i);
                String name = s.substring(i, nameEnd);
                int defOpen = findExportsVarDef(name);
                if (defOpen >= 0) {
                    int close = matchBrace(defOpen);
                    if (close >= 0) return parseObjectMembers(defOpen + 1, close);
                }
                if (funcSigOf(name) != null) {
                    List<String> members = new ArrayList<>(assignedFunctionMembers(name));
                    String defaultType = namedExportCast(nameEnd, end);
                    if (defaultType != null) {
                        String local = "__nekoNodeDefaultExport";
                        while (s.contains(local)) local += "_";
                        members.add("const " + local + ": " + defaultType + ";");
                        members.add("export default " + local + ";");
                    }
                    return members;
                }
            }
            return List.of();
        }

        /** Uses an explicit named type on the actual callable export, without inferring arbitrary expressions. */
        private String namedExportCast(int start, int end) {
            int cast = skipWsAndComments(start);
            if (cast >= end || !isIdentStart(s.charAt(cast))) return null;
            int castEnd = readIdent(cast);
            if (!s.substring(cast, castEnd).equals("as")) return null;
            int type = skipWsAndComments(castEnd);
            if (type >= end || !isIdentStart(s.charAt(type))) return null;
            int typeEnd = readIdent(type);
            if (skipWsAndComments(typeEnd) != end) return null;
            String name = s.substring(type, typeEnd);
            if (findKeywordThenIdent("interface", name) < 0 && findKeywordThenIdent("type", name) < 0
                    && findKeywordThenIdent("class", name) < 0) return null;
            return name;
        }

        /** Resolves the literal members attached to a callable module by Object.assign. */
        private List<String> assignedFunctionMembers(String name) {
            List<String> members = new ArrayList<>();
            int i = 0;
            while (i < n) {
                i = skipWsAndComments(i);
                if (i >= n) break;
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (!isIdentStart(c)) { i++; continue; }
                int end = readIdent(i);
                String token = s.substring(i, end);
                i = end;
                if (!token.equals("Object")) continue;
                int dot = skipWsAndComments(end);
                if (dot >= n || s.charAt(dot) != '.') continue;
                int method = skipWsAndComments(dot + 1);
                int methodEnd = readIdent(method);
                if (!s.substring(method, methodEnd).equals("assign")) continue;
                int open = skipWsAndComments(methodEnd);
                if (open >= n || s.charAt(open) != '(') continue;
                int close = matchParen(open);
                if (close < 0) continue;
                int[] args = splitTopLevelArgs(open, close);
                if (args == null || !s.substring(args[0], args[1]).trim().equals(name)) continue;
                int object = skipWsAndComments(args[2]);
                if (object >= n || s.charAt(object) != '{') continue;
                int objectEnd = matchBrace(object);
                // Only one literal source is resolved; arbitrary assign expressions remain unsupported.
                if (objectEnd < 0 || skipWsAndComments(objectEnd + 1) != close) continue;
                members.addAll(parseObjectMembers(object + 1, objectEnd));
                i = close + 1;
            }
            return members;
        }

        /** Finds the `{` of the object literal in {@code (const|let|var) <name> = { ... }}. */
        private int findExportsVarDef(String name) {
            int i = 0;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 1 < n) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                }
                if (isIdentStart(c)) {
                    int we = readIdent(i);
                    String w = s.substring(i, we);
                    if (w.equals("const") || w.equals("let") || w.equals("var")) {
                        int p = skipWsAndComments(we);
                        int ne = readIdent(p);
                        if (ne > p && s.substring(p, ne).equals(name)) {
                            int eq = skipWsAndComments(ne);
                            if (eq < n && s.charAt(eq) == '=') {
                                int ob = skipWsAndComments(eq + 1);
                                if (ob < n && s.charAt(ob) == '{') return ob;
                            }
                        }
                    }
                    i = we;
                    continue;
                }
                i++;
            }
            return -1;
        }

        // ---- Object literal member parsing ----

        private List<String> parseObjectMembers(int bodyStart, int bodyEnd) {
            List<String> sigs = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int i = bodyStart;
            while (i < bodyEnd) {
                i = skipWsAndComments(i);
                if (i >= bodyEnd) break;
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == '}') break;
                Member m = parseOneMember(i, bodyEnd, seen);
                if (m == null) {
                    i++;
                    continue;
                }
                if (m.sig() != null) sigs.add(m.sig());
                i = m.next();
            }
            return sigs;
        }

        private Member parseOneMember(int start, int limit, Set<String> seen) {
            int i = skipWsAndComments(start);
            if (i >= limit) return null;
            char c = s.charAt(i);
            String key;
            if (c == '\'' || c == '"') {
                int q = skipString(i, c);
                key = s.substring(i + 1, q - 1);
                i = q;
            } else if (c == '[') {
                // Computed property, skip it
                int close = matchBracket(i);
                return new Member(null, close + 1);
            } else if (isIdentStart(c)) {
                int ke = readIdent(i);
                String first = s.substring(i, ke);
                // async prefix: take the following name as the key; get/set goes to getterSetterMember
                if (first.equals("async")) {
                    int next = skipWsAndComments(ke);
                    if (next < limit && isIdentStart(s.charAt(next))) { i = next; ke = readIdent(i); }
                } else if (first.equals("get") || first.equals("set")) {
                    int next = skipWsAndComments(ke);
                    if (next < limit && isIdentStart(s.charAt(next))) {
                        return getterSetterMember(first, next, limit, seen);
                    }
                }
                key = s.substring(i, ke);
                i = ke;
            } else {
                return new Member(null, i + 1);
            }
            int afterKey = skipWsAndComments(i);
            // Generic method shorthand: key<Gen>(...)
            String generics = "";
            if (afterKey < n && s.charAt(afterKey) == '<') {
                int ac = matchAngle(afterKey);
                if (ac > 0) {
                    generics = s.substring(afterKey, ac + 1);
                    afterKey = skipWsAndComments(ac + 1);
                }
            }
            if (afterKey < n && s.charAt(afterKey) == '(') {
                return methodMember(key, afterKey, generics);
            }
            if (afterKey < n && s.charAt(afterKey) == ':') {
                return propertyMember(key, skipWsAndComments(afterKey + 1));
            }
            // Shorthand property: references the key itself
            String sig = resolveReferenceSig(key);
            return new Member(sig, afterKey);
        }

        /** Method shorthand {@code key<G>(params): ret { body }} becomes
         * {@code export function key<G>(params): ret;} */
        private Member methodMember(String key, int parenOpen, String generics) {
            int parenClose = matchParen(parenOpen);
            if (parenClose < 0) return new Member(null, parenOpen + 1);
            String params = s.substring(parenOpen, parenClose + 1);
            int j = skipWsAndComments(parenClose + 1);
            String ret = "";
            if (j < n && s.charAt(j) == ':') {
                int rs = skipWsAndComments(j + 1);
                int re = typeEnd(rs);
                ret = ": " + s.substring(rs, re).trim();
                j = re;
            }
            j = skipWsAndComments(j);
            if (j < n && s.charAt(j) == '{') j = matchBrace(j) + 1;
            else if (j < n && s.charAt(j) == ';') j++;
            return new Member("export function " + key + generics + params + ret + ";", Math.max(j, parenClose + 1));
        }

        /**
         * Property {@code key: value}. An arrow or function value becomes
         * {@code export function key(...)...}; any other value becomes {@code export const key: <inferred>;}.
         */
        private Member propertyMember(String key, int valueStart) {
            int i = skipWsAndComments(valueStart);
            // async prefix
            if (i < n && isIdentStart(s.charAt(i))) {
                int ke = readIdent(i);
                if (s.substring(i, ke).equals("async")) i = skipWsAndComments(ke);
            }
            Member fn = functionValueSig(key, i, valueStart);
            if (fn != null) return fn;
            if (i < n && s.charAt(i) == '{') {
                return inlineObjectMember(key, i);
            }
            // Literal or reference
            int ve = valueEnd(valueStart);
            String type = inferValueType(valueStart, ve);
            return new Member("export const " + key + ": " + type + ";", ve);
        }

        /** Nested object literal becomes export const key: { a: T; method(): R; ... }. Members are
         * handled recursively, and an empty object becomes Record<string, unknown>. */
        private Member inlineObjectMember(String key, int open) {
            int close = matchBrace(open);
            if (close < 0) return new Member("export const " + key + ": Record<string, unknown>;", open + 1);
            String inline = inlineObjectType(open + 1, close);
            return new Member("export const " + key + ": " + inline + ";", close + 1);
        }

        private String inlineObjectType(int bodyStart, int bodyEnd) {
            List<String> members = parseObjectMembers(bodyStart, bodyEnd);
            if (members.isEmpty()) return "Record<string, unknown>";
            List<String> parts = new ArrayList<>();
            for (String m : members) {
                String p = m;
                if (p.startsWith("export function ")) p = p.substring(16);
                else if (p.startsWith("export const ")) p = p.substring(13);
                if (p.endsWith(";")) p = p.substring(0, p.length() - 1);
                parts.add(p);
            }
            return "{ " + String.join("; ", parts) + " }";
        }

        /** Object literal get/set becomes export const prop: T;. The type is the getter's return type or
         * the setter's first parameter, and seen dedupes a get and a set with the same name. */
        private Member getterSetterMember(String kind, int propStart, int limit, Set<String> seen) {
            int ke = readIdent(propStart);
            String prop = s.substring(propStart, ke);
            int parenOpen = skipWsAndComments(ke);
            if (parenOpen >= n || s.charAt(parenOpen) != '(') return new Member(null, ke);
            int parenClose = matchParen(parenOpen);
            if (parenClose < 0) return new Member(null, ke);
            String params = s.substring(parenOpen, parenClose + 1);
            String ret = "";
            int j = skipWsAndComments(parenClose + 1);
            if (j < n && s.charAt(j) == ':') {
                int ts = skipWsAndComments(j + 1);
                int te = typeEnd(ts);
                ret = s.substring(ts, te).trim();
                j = te;
            }
            int next = skipMemberBody(j, limit);
            if (seen.contains(prop)) return new Member(null, next);
            seen.add(prop);
            String t = kind.equals("get") ? (ret.isBlank() ? "unknown" : ret) : firstParamType(params);
            return new Member("export const " + prop + ": " + t + ";", next);
        }

        /** Arrow {@code (params): Ret => body} or function value becomes an export function signature;
         * otherwise null. */
        private Member functionValueSig(String key, int i, int valueStart) {
            if (i < n && s.charAt(i) == '(') {
                int parenClose = matchParen(i);
                if (parenClose > 0) {
                    String params = s.substring(i, parenClose + 1);
                    int j = skipWsAndComments(parenClose + 1);
                    String ret = "";
                    if (j < n && s.charAt(j) == ':') {
                        int rs = skipWsAndComments(j + 1);
                        int re = typeEnd(rs);
                        ret = ": " + s.substring(rs, re).trim();
                    }
                    return new Member("export function " + key + params + ret + ";", valueEnd(valueStart));
                }
                return null;
            }
            if (i < n && isIdentStart(s.charAt(i))) {
                int ke = readIdent(i);
                if (s.substring(i, ke).equals("function")) {
                    int p = skipWsAndComments(ke);
                    if (p < n && isIdentStart(s.charAt(p))) p = readIdent(p); // Optional name
                    p = skipWsAndComments(p);
                    if (p < n && s.charAt(p) == '(') {
                        int parenClose = matchParen(p);
                        if (parenClose > 0) {
                            String params = s.substring(p, parenClose + 1);
                            int j = skipWsAndComments(parenClose + 1);
                            String ret = "";
                            if (j < n && s.charAt(j) == ':') {
                                int rs = skipWsAndComments(j + 1);
                                int re = typeEnd(rs);
                                ret = ": " + s.substring(rs, re).trim();
                            }
                            return new Member("export function " + key + params + ret + ";", valueEnd(valueStart));
                        }
                    }
                }
            }
            return null;
        }

        // ---- Reference resolution ----

        /** Resolves a shorthand reference by identifier: a function becomes export function, a
         * const/let/var becomes export const name: type, and anything else becomes unknown. */
        private String resolveReferenceSig(String name) {
            String cl = classDeclSig(name);
            if (cl != null) return cl;
            String f = funcSigOf(name);
            if (f != null) return f;
            String vt = varTypeOf(name);
            if (vt != null) {
                int vp = findVarNamePos(name);
                return withLeadingDoc(vp, "export const " + name + ": " + vt + ";");
            }
            return "export const " + name + ": unknown;";
        }

        // ---- Class member extraction (constructor/method/static/field/get-set) ----

        /** Emits an exported class within its enclosing ambient module; returns null if no class is found. */
        private String classDeclSig(String name) {
            int i = findKeywordThenIdent("class", name);
            if (i < 0) return null;
            int after = skipWsAndComments(i + 5);
            int ne = readIdent(after);
            int p = skipWsAndComments(ne);
            String generics = "";
            if (p < n && s.charAt(p) == '<') {
                int ac = matchAngle(p);
                if (ac > 0) { generics = s.substring(p, ac + 1); p = skipWsAndComments(ac + 1); }
            }
            StringBuilder head = new StringBuilder();
            int braceOpen = -1;
            while (p < n) {
                char c = s.charAt(p);
                if (c == '\'' || c == '"') { int e = skipString(p, c); head.append(s, p, e); p = e; continue; }
                if (c == '{') { braceOpen = p; break; }
                if (c == ';' || c == '\n') break;
                head.append(c);
                p++;
            }
            String headStr = head.toString().trim();
            if (braceOpen < 0) {
                return withLeadingDoc(i, "export class " + name + generics + (headStr.isEmpty() ? "" : " " + headStr) + " {}");
            }
            int braceClose = matchBrace(braceOpen);
            if (braceClose < 0) return null;
            List<String> members = parseClassMembers(braceOpen + 1, braceClose);
            StringBuilder sb = new StringBuilder("export class ").append(name).append(generics);
            if (!headStr.isEmpty()) sb.append(' ').append(headStr);
            sb.append(" {\n");
            for (String m : members) sb.append(m).append('\n');
            sb.append("}");
            return withLeadingDoc(i, sb.toString());
        }

        private List<String> parseClassMembers(int bodyStart, int bodyEnd) {
            List<String> sigs = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int i = bodyStart;
            while (i < bodyEnd) {
                i = skipWsAndComments(i);
                if (i >= bodyEnd) break;
                char c = s.charAt(i);
                if (c == ';' || c == ',') { i++; continue; }
                if (c == '}') break;
                boolean isStatic = false;
                while (i < bodyEnd && isIdentStart(s.charAt(i))) {
                    int ke = readIdent(i);
                    String w = s.substring(i, ke);
                    if (w.equals("static")) { isStatic = true; i = skipWsAndComments(ke); continue; }
                    if (isClassMod(w)) { i = skipWsAndComments(ke); continue; }
                    break;
                }
                if (i >= bodyEnd) break;
                Member m = parseClassMember(i, bodyEnd, isStatic, seen);
                if (m == null) { i++; continue; }
                if (m.sig() != null && !m.sig().isEmpty()) sigs.add(m.sig());
                i = m.next();
            }
            return sigs;
        }

        private Member parseClassMember(int start, int limit, boolean isStatic, Set<String> seen) {
            int i = skipWsAndComments(start);
            if (i >= limit) return null;
            char c = s.charAt(i);
            if (isIdentStart(c)) {
                int ke = readIdent(i);
                String w = s.substring(i, ke);
                if (w.equals("get") || w.equals("set")) {
                    int p = skipWsAndComments(ke);
                    if (p < limit && isIdentStart(s.charAt(p))) {
                        int ke2 = readIdent(p);
                        String prop = s.substring(p, ke2);
                        int parenOpen = skipWsAndComments(ke2);
                        if (parenOpen < n && s.charAt(parenOpen) == '(') {
                            int parenClose = matchParen(parenOpen);
                            if (parenClose < 0) return null;
                            String params = s.substring(parenOpen, parenClose + 1);
                            String ret = "";
                            int j = skipWsAndComments(parenClose + 1);
                            if (j < n && s.charAt(j) == ':') {
                                int ts = skipWsAndComments(j + 1);
                                int te = typeEnd(ts);
                                ret = s.substring(ts, te).trim();
                                j = te;
                            }
                            int next = skipMemberBody(j, limit);
                            if (seen.contains(prop)) return new Member(null, next);
                            seen.add(prop);
                            String t = w.equals("get") ? (ret.isBlank() ? "unknown" : ret) : firstParamType(params);
                            String prefix = isStatic ? "static " : "";
                            return new Member(prefix + prop + ": " + t + ";", next);
                        }
                    }
                }
                String key = w;
                i = ke;
                int afterKey = skipWsAndComments(i);
                String generics = "";
                if (afterKey < n && s.charAt(afterKey) == '<') {
                    int ac = matchAngle(afterKey);
                    if (ac > 0) { generics = s.substring(afterKey, ac + 1); afterKey = skipWsAndComments(ac + 1); }
                }
                if (afterKey < n && s.charAt(afterKey) == '(') {
                    int parenClose = matchParen(afterKey);
                    if (parenClose < 0) return null;
                    String params = ambientClassParameters(afterKey, parenClose);
                    int j = skipWsAndComments(parenClose + 1);
                    String ret = "";
                    if (j < n && s.charAt(j) == ':') {
                        int ts = skipWsAndComments(j + 1);
                        int te = typeEnd(ts);
                        ret = ": " + s.substring(ts, te).trim();
                        j = te;
                    }
                    int next = skipMemberBody(j, limit);
                    String prefix = isStatic ? "static " : "";
                    if (key.equals("constructor")) return new Member("constructor" + params + ";", next);
                    return new Member(prefix + key + generics + params + ret + ";", next);
                }
                String type = "unknown";
                int j = afterKey;
                if (afterKey < n && s.charAt(afterKey) == ':') {
                    int ts = skipWsAndComments(afterKey + 1);
                    int te = typeEnd(ts);
                    type = s.substring(ts, te).trim();
                    if (type.isBlank()) type = "unknown";
                    j = te;
                }
                int next = skipMemberBody(j, limit);
                String prefix = isStatic ? "static " : "";
                return new Member(prefix + key + ": " + type + ";", next);
            }
            if (c == '[') return new Member(null, matchBracket(i) + 1);
            return new Member(null, i + 1);
        }

        private static boolean isClassMod(String w) {
            return w.equals("public") || w.equals("private") || w.equals("protected")
                || w.equals("readonly") || w.equals("abstract") || w.equals("override") || w.equals("declare");
        }

        /** Removes class parameter initializers while retaining whether callers may omit each argument. */
        private String ambientClassParameters(int open, int close) {
            List<String> parameters = new ArrayList<>();
            List<Boolean> initialized = new ArrayList<>();
            int start = open + 1, initializer = -1, depth = 0, angle = 0;
            for (int i = start; i <= close; i++) {
                char c = s.charAt(i);
                if (i == close || (c == ',' && depth == 0 && angle == 0)) {
                    String parameter = s.substring(start, initializer < 0 ? i : initializer).trim();
                    if (!parameter.isEmpty()) {
                        if (initializer >= 0 && parameter.indexOf(':') < 0) {
                            parameter += ": " + inferValueType(skipWsAndComments(initializer + 1), i);
                        }
                        parameters.add(parameter);
                        initialized.add(initializer >= 0);
                    }
                    start = i + 1;
                    initializer = -1;
                    continue;
                }
                if (c == '\'' || c == '"') { i = skipString(i, c) - 1; continue; }
                if (c == '`') { i = skipTemplate(i) - 1; continue; }
                if (c == '/' && i + 1 < close) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2) - 1; continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2) - 1; continue; }
                }
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth--;
                else if (initializer < 0 && c == '<') angle++;
                else if (initializer < 0 && c == '>' && angle > 0) angle--;
                else if (c == '=' && i + 1 < close && s.charAt(i + 1) != '>'
                        && depth == 0 && angle == 0 && initializer < 0) {
                    initializer = i;
                    i = Math.min(close, valueEnd(skipWsAndComments(i + 1))) - 1;
                }
            }
            if (!initialized.contains(true)) return s.substring(open, close + 1);
            boolean trailingOptional = true;
            for (int index = parameters.size() - 1; index >= 0; index--) {
                String parameter = parameters.get(index);
                int colon = parameter.indexOf(':');
                String name = colon < 0 ? parameter : parameter.substring(0, colon).trim();
                boolean optional = name.endsWith("?") || name.startsWith("...");
                if (initialized.get(index) && !optional) {
                    String type = parameter.substring(colon + 1).trim();
                    parameter = trailingOptional ? name + "?: " + type : name + ": (" + type + ") | undefined";
                    parameters.set(index, parameter);
                    optional = trailingOptional;
                }
                trailingOptional &= optional;
            }
            return "(" + String.join(", ", parameters) + ")";
        }

        private int skipMemberBody(int j, int limit) {
            j = skipWsAndComments(j);
            if (j >= n) return j;
            char c = s.charAt(j);
            if (c == '{') { int bc = matchBrace(j); return bc < 0 ? limit : bc + 1; }
            if (c == '=') { return valueEndClass(skipWsAndComments(j + 1), limit); }
            if (c == ';') return j + 1;
            return j;
        }

        private int valueEndClass(int start, int limit) {
            int i = start, depth = 0;
            while (i < limit) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') { if (depth == 0) return i; depth--; }
                else if (c == ';' && depth == 0) return i + 1;
                i++;
            }
            return limit;
        }

        private String firstParamType(String params) {
            int d = 1;
            while (d < params.length() && Character.isWhitespace(params.charAt(d))) d++;
            int ne = d;
            while (ne < params.length() && (isIdentPart(params.charAt(ne)) || params.charAt(ne) == '?')) ne++;
            int colon = ne;
            while (colon < params.length() && Character.isWhitespace(params.charAt(colon))) colon++;
            if (colon < params.length() && params.charAt(colon) == ':') {
                int te = colon + 1;
                while (te < params.length() && Character.isWhitespace(params.charAt(te))) te++;
                int depth = 0, e = te;
                while (e < params.length()) {
                    char c = params.charAt(e);
                    if (c == '(' || c == '[' || c == '{' || c == '<') depth++;
                    else if (c == ')' || c == ']' || c == '}' || c == '>') { if (depth == 0) break; depth--; }
                    else if (c == ',' && depth == 0) break;
                    e++;
                }
                String t = params.substring(te, e).trim();
                return t.isEmpty() ? "unknown" : t;
            }
            return "unknown";
        }

        /** Finds {@code function <name><G>(params): ret} and returns the full export function
         * signature; returns null if there is none. */
        private String funcSigOf(String name) {
            int i = findKeywordThenIdent("function", name);
            if (i < 0) return null;
            int after = skipWsAndComments(i + "function".length());
            if (after < n && s.charAt(after) == '*') after = skipWsAndComments(after + 1); // generator
            int ne = readIdent(after); // Skip the name
            int p = skipWsAndComments(ne);
            String generics = "";
            if (p < n && s.charAt(p) == '<') {
                int ac = matchAngle(p);
                if (ac > 0) {
                    generics = s.substring(p, ac + 1);
                    p = skipWsAndComments(ac + 1);
                }
            }
            if (p >= n || s.charAt(p) != '(') return null;
            int parenClose = matchParen(p);
            if (parenClose < 0) return null;
            String params = s.substring(p, parenClose + 1);
            int j = skipWsAndComments(parenClose + 1);
            String ret = "";
            if (j < n && s.charAt(j) == ':') {
                int rs = skipWsAndComments(j + 1);
                int re = typeEnd(rs);
                ret = ": " + s.substring(rs, re).trim();
            }
            return withLeadingDoc(i, "export function " + name + generics + params + ret + ";");
        }

        /** Finds the {@code (const|let|var) <name>} declaration and returns its type annotation or the
         * type inferred from its literal; returns null if there is none. */
        private String varTypeOf(String name) {
            int i = findVarNamePos(name);
            if (i < 0) return null;
            int j = skipWsAndComments(i + name.length());
            if (j < n && s.charAt(j) == ':') {
                int ts = skipWsAndComments(j + 1);
                int te = typeEnd(ts);
                return s.substring(ts, te).trim();
            }
            if (j < n && s.charAt(j) == '=') {
                int vs = skipWsAndComments(j + 1);
                return inferValueType(vs, valueEnd(vs));
            }
            return "unknown";
        }

        /** Returns the position where name starts in {@code const|let|var <name>}, or -1 if there is no
         * such declaration. */
        private int findVarNamePos(String name) {
            int from = 0;
            while (true) {
                int idx = indexOfIdent(name, from);
                if (idx < 0) return -1;
                from = idx + name.length();
                int pb = prevNonWs(idx - 1);
                if (pb < 0) continue;
                int ps = identStartBefore(pb + 1);
                if (ps < 0) continue;
                String kw = s.substring(ps, pb + 1);
                if (kw.equals("const") || kw.equals("let") || kw.equals("var")) return idx;
            }
        }

        /** Returns the position of keyword in {@code <keyword> <name>}. Only whitespace and comments may
         * sit between the keyword and the name. */
        private int findKeywordThenIdent(String keyword, String name) {
            int from = 0;
            while (true) {
                int idx = indexOfIdent(keyword, from);
                if (idx < 0) return -1;
                from = idx + keyword.length();
                int p = skipWsAndComments(idx + keyword.length());
                int ne = readIdent(p);
                if (ne > p && s.substring(p, ne).equals(name)) return idx;
            }
        }

        // ---- Value type inference ----

        private String inferValueType(int valueStart, int valueEnd) {
            int i = skipWsAndComments(valueStart);
            if (i >= valueEnd) return "unknown";
            char c = s.charAt(i);
            if (c == '\'' || c == '"') return "string";
            if (c == '`') return "string";
            if (Character.isDigit(c) || (c == '-' && i + 1 < valueEnd && (Character.isDigit(s.charAt(i + 1)) || s.charAt(i + 1) == '.'))) return "number";
            String token = readToken(i, valueEnd);
            if (token.equals("true") || token.equals("false")) return "boolean";
            if (token.equals("null")) return "null";
            if (isIdentStart(c)) {
                String vt = varTypeOf(token);
                if (vt != null) return vt;
                if (isDeclaredTypeName(token)) return token;
                return "unknown";
            }
            return "unknown";
        }

        private String readToken(int start, int end) {
            StringBuilder sb = new StringBuilder();
            int i = start;
            while (i < end && isIdentPart(s.charAt(i))) {
                sb.append(s.charAt(i));
                i++;
            }
            return sb.toString();
        }

        // ---- Top-level type/interface/enum collection ----

        private String collectTypeDeclarations() {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 1 < n) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                }
                if (isIdentStart(c)) {
                    int we = readIdent(i);
                    String w = s.substring(i, we);
                    if ((w.equals("type") || w.equals("interface") || w.equals("enum") || w.equals("namespace"))
                            && isDeclAfter(we)) {
                        int declEnd = w.equals("type") ? typeDeclEnd(i) : blockDeclEnd(i);
                        String declaration = s.substring(i, declEnd).trim();
                        if (w.equals("namespace")) declaration = "export " + declaration;
                        sb.append(declaration).append('\n');
                        i = declEnd;
                        continue;
                    }
                    i = we;
                    continue;
                }
                i++;
            }
            return sb.toString();
        }

        /** Whether an identifier or string follows the keyword (confirming a declaration rather than a variable name). */
        private boolean isDeclAfter(int afterKw) {
            int p = skipWsAndComments(afterKw);
            return p < n && (isIdentStart(s.charAt(p)) || s.charAt(p) == '\'' || s.charAt(p) == '"');
        }

        /**
         * Finds the end of a type declaration: the top-level (depth=0) {@code ;}, or when no semicolon
         * is present, the end of the line. A union or intersection written across lines continues
         * through {@code |}/{@code &}.
         *
         * <p>{@code <} and {@code >} use a separate angle counter, the same as {@link #valueEnd} and
         * {@link #typeEnd}. Otherwise the {@code >} of an {@code =>} arrow looks like a closed generic
         * and resets depth too early: {@code type X = { m: (a: T) => R; ... }} would stop at the first
         * {@code ;}, lose the closing {@code }} and make the enclosing {@code declare module} borrow a
         * closing {@code }}, so TypeScript reports "Expected '}'".
         */
        private int typeDeclEnd(int start) {
            int i = start, depth = 0, angle = 0;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 1 < n) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                }
                if (c == '<') angle++;
                else if (c == '>' && angle > 0) angle--;
                else if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth = Math.max(0, depth - 1);
                else if (c == ';' && depth == 0 && angle == 0) return i + 1;
                else if ((c == '\n' || c == '\r') && depth == 0 && angle == 0) {
                    int nxt = skipWsAndComments(i + 1);
                    if (nxt >= n || (s.charAt(nxt) != '|' && s.charAt(nxt) != '&')) return i + 1;
                }
                i++;
            }
            return n;
        }

        /** End of an interface/enum declaration: after the `}` matching the first `{` (or at the `;` when there is no body). */
        private int blockDeclEnd(int start) {
            int i = start;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 1 < n) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                }
                if (c == '{') {
                    int close = matchBrace(i);
                    return close < 0 ? n : close + 1;
                }
                if (c == ';') return i + 1;
                i++;
            }
            return n;
        }

        private boolean isDeclaredTypeName(String name) {
            return findKeywordThenIdent("type", name) >= 0
                || findKeywordThenIdent("interface", name) >= 0
                || findKeywordThenIdent("enum", name) >= 0;
        }

        /** When a JSDoc block comment sits right before pos, emits it as leading documentation of sig (comment + newline + sig); otherwise returns sig unchanged. */
        private String withLeadingDoc(int pos, String sig) {
            String doc = docCommentBefore(pos);
            return doc == null ? sig : doc + "\n" + sig;
        }

        /** Returns the raw JSDoc block comment right before pos (separated only by whitespace), or null. A block body holds no closing delimiter, so lastIndexOf finds the block start. */
        private String docCommentBefore(int pos) {
            int i = pos - 1;
            while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
            if (i < 3 || s.charAt(i) != '/' || s.charAt(i - 1) != '*') return null;
            int start = s.lastIndexOf("/**", i - 1);
            if (start < 0) return null;
            return s.substring(start, i + 1);
        }

        // ---- Scanning primitives ----

        private int valueEnd(int start) {
            int i = start, depth = 0, angle = 0;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 1 < n) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                }
                if (c == '<') angle++;
                else if (c == '>' && angle > 0) angle--;
                else if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') { if (depth == 0) return i; depth--; }
                else if (c == ',' && depth == 0 && angle == 0) return i;
                i++;
            }
            return n;
        }

        /** Ends the type expression (replicates Eraser.typeExpressionEnd semantics). */
        private int typeEnd(int start) {
            int i = start, angle = 0, paren = 0, bracket = 0, brace = 0;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 1 < n) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                }
                if (c == '<') angle++;
                else if (c == '>' && angle > 0) angle--;
                else if (c == '(') paren++;
                else if (c == ')' && paren > 0) paren--;
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                else if (c == '{') {
                    if (angle == 0 && paren == 0 && bracket == 0 && brace == 0) return i;
                    brace++;
                } else if (c == '}' && brace > 0) brace--;
                if (angle == 0 && paren == 0 && bracket == 0 && brace == 0) {
                    if (c == '=' || c == ',' || c == ';' || c == ')' || c == '{' || c == '\n' || c == '\r') return i;
                }
                i++;
            }
            return i;
        }

        private int matchParen(int open) { return matchPair(open, '(', ')'); }
        private int matchBrace(int open) { return matchPair(open, '{', '}'); }
        private int matchBracket(int open) { return matchPair(open, '[', ']'); }
        private int matchAngle(int open) { return matchPair(open, '<', '>'); }

        private int matchPair(int open, char openCh, char closeCh) {
            int depth = 0;
            int i = open;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 1 < n) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                }
                if (c == openCh) depth++;
                else if (c == closeCh) { depth--; if (depth == 0) return i; }
                i++;
            }
            return -1;
        }

        private int skipString(int i, char quote) {
            return NekoSourceLexerBase.skipString(s, n, i, quote);
        }

        private int skipTemplate(int i) {
            return NekoSourceLexerBase.skipTemplate(s, n, i);
        }

        private int skipLineComment(int i) {
            return NekoSourceLexerBase.skipLineComment(s, n, i);
        }

        private int skipBlockComment(int i) {
            return NekoSourceLexerBase.skipBlockComment(s, n, i);
        }

        private int skipWsAndComments(int i) {
            while (i < n) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                break;
            }
            return i;
        }

        private int trimEnd(int exclusiveEnd) {
            int e = exclusiveEnd;
            while (e > 0 && Character.isWhitespace(s.charAt(e - 1))) e--;
            return e;
        }

        private int prevNonWs(int idx) {
            return NekoSourceLexerBase.previousNonWhitespace(s, n, idx);
        }

        /** Returns the identifier start position containing position pb (ident end); returns -1 if not an identifier. */
        private int identStartBefore(int exclusiveEnd) {
            int i = exclusiveEnd - 1;
            if (i < 0 || !isIdentPart(s.charAt(i))) return -1;
            while (i > 0 && isIdentPart(s.charAt(i - 1))) i--;
            return i;
        }

        private int indexOfIdent(String name, int from) {
            int idx = s.indexOf(name, from);
            while (idx >= 0) {
                boolean before = idx == 0 || !isIdentPart(s.charAt(idx - 1));
                boolean after = idx + name.length() >= n || !isIdentPart(s.charAt(idx + name.length()));
                if (before && after) return idx;
                idx = s.indexOf(name, idx + 1);
            }
            return -1;
        }

        private int readIdent(int i) {
            return NekoSourceLexerBase.readIdentifierEnd(s, n, i);
        }

        private static boolean isIdentStart(char c) {
            return NekoSourceLexerBase.isIdentifierStart(c);
        }

        private static boolean isIdentPart(char c) {
            return NekoSourceLexerBase.isIdentifierPart(c);
        }

        private static String indentBlock(String text) {
            StringBuilder sb = new StringBuilder();
            for (String line : text.split("\n", -1)) {
                if (line.isEmpty()) {
                    if (sb.length() > 0) sb.append('\n');
                } else {
                    sb.append("    ").append(line).append('\n');
                }
            }
            return sb.toString();
        }
    }
}
