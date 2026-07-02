package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocsRegister;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * node 模块类型声明提取器：从 node 模块源码（{@code .ts} 类型注解 / {@code .js+JSDoc} 注释）
 * 自动生成 {@code declare module 'node:xxx' {...}} 声明，使模块实现与补全声明同源。
 *
 * <p>支持两种模块写法，给用户较高自由度：
 * <ul>
 *   <li><b>.ts</b>：解析 TypeScript 类型注解（{@link #extractTS}）；加载时由
 *       {@link NekoTypeScriptCompiler#eraseTypescript} 擦除类型后求值；</li>
 *   <li><b>.js + JSDoc</b>：扫描 {@code /** *​/} 注释（{@link #extractJSDoc}）。</li>
 * </ul>
 *
 * <p>node 模块为脚本风格（IIFE + {@code globalThis.__nekoNodeDefine(ids, exports)}），
 * 因为脚本上下文求值不支持 ES {@code export}/{@code import}；类型注解由 Eraser 擦除。
 *
 * <p>{@link #registerBuiltin} 在 {@code registerNodeTypeDocs} 扩展点被调用：扫描
 * {@code nekojs/node/modules.list} 中所有 {@code .ts} 模块自动提取声明（全自动化，无手写回退）。
 *
 * <p>提取为简化版（针对 node 模块常见的类型化声明形式），非完整 {@code tsc --declaration}：
 * 复杂类型（条件/映射/infer）原样保留文本，无法解析的成员降级为 {@code unknown} 而非整体失败。
 *
 * <p>注意：导出成员必须带 {@code export}（{@code export function/const}），否则在
 * {@code declare module} 内对 {@code require()} 消费者不可见，补全失效。
 */
public final class NodeModuleTypeDocs {
    private static final String RESOURCE_ROOT = "nekojs/node/";
    private static final String MANIFEST = RESOURCE_ROOT + "modules.list";

    private NodeModuleTypeDocs() {}

    /**
     * 注册内置 node 模块声明：扫描 {@code modules.list} 中的 {@code .ts} 模块，用 {@link #extractTS}
     * 自动提取类型声明。所有内置 node 模块均已重写为带类型注解的 {@code .ts}，声明全自动提取，无手写回退。
     */
    public static void registerBuiltin(TypeDocsRegister registry) {
        scanBuiltinTypeScriptModules(registry);
    }

    /** 扫描 {@code modules.list} 中的 .ts 条目，自动提取类型声明并注册（跳过非 .ts 与无类型信息的模块）。 */
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
            String moduleId = entry.replace('/', '.').replace(".ts", "");
            registry.registerManualDeclaration(ManualDeclarationCatalogEntry.of(
                "nekojs.node-auto." + moduleId,
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
     * 从 .ts node 模块源码提取类型声明。
     *
     * <p>解析 {@code __nekoNodeDefine(ids, exports)} 获取模块 id 列表与导出对象，对导出对象的成员
     * （方法简写 {@code name(params): ret {}}、属性 {@code name: value}、简写引用 {@code name}）
     * 关联到模块内带类型注解的 function/const 声明生成签名，并收集顶层
     * {@code type}/{@code interface}/{@code enum} 声明，包成 {@code declare module 'id' { ... }}。
     * 类型语法（泛型/联合/交叉/字面量）原样保留文本。
     *
     * @param source .ts 源码（脚本风格：IIFE + {@code __nekoNodeDefine}）
     * @return 生成的 {@code declare module} 声明文本；无类型信息可提取时返回空字符串
     */
    public static String extractTS(String source) {
        if (source == null || source.isBlank()) return "";
        return new TSExtractor(source).extract();
    }

    /**
     * 从 .js node 模块源码（带 JSDoc）提取类型声明。
     *
     * <p>扫描 {@code /** * /} 注释中的 {@code @typedef} / {@code @callback} / {@code @param} /
     * {@code @returns} / {@code @type} / {@code @property} 标签，结合
     * {@code __nekoNodeDefine(ids, exports)} 的导出结构生成 {@code declare module}。
     *
     * @param source .js 源码（带 JSDoc 注释）
     * @return 生成的 {@code declare module} 声明文本；无 JSDoc 可提取时返回空字符串
     */
    public static String extractJSDoc(String source) {
        if (source == null || source.isBlank()) return "";
        return new JSDocExtractor(source).extract();
    }

    /** __nekoNodeDefine 调用的解析结果：模块 id 列表 + 第二参数（exports 表达式）的源码范围。 */
    private record DefineCall(List<String> ids, int exportsStart, int exportsEnd) {}

    /** 单个导出成员的解析结果：签名文本（null 表示跳过）与成员结束位置。 */
    private record Member(String sig, int next) {}

    // ============================ .ts 提取器 ============================

    /**
     * 只读扫描 .ts 源码，定位 {@code __nekoNodeDefine} 调用与导出成员，生成 declare module。
     * 所有扫描原语跳过字符串/模板/注释，匹配括号/泛型对，不修改源码。
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

        // ---- 定位 __nekoNodeDefine 调用 ----

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
                if (parenOpen >= n || s.charAt(parenOpen) != '(') continue; // 非调用（如赋值）
                int parenClose = matchParen(parenOpen);
                if (parenClose < 0) continue;
                int[] args = splitTopLevelArgs(parenOpen, parenClose);
                if (args == null) continue;
                List<String> ids = parseIds(args[0], args[1]);
                if (ids.isEmpty()) continue;
                calls.add(new DefineCall(ids, args[2], args[3]));
            }
        }

        /** 拆分参数列表为前两个顶层参数的范围 [s1,e1, s2,e2]（exclusive end）；不足两个返回 null。 */
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
            if (firstEnd < 0) return null; // 只有一个参数
            return new int[] { firstStart, firstEnd, secondStart, trimEnd(close) };
        }

        /** 解析 id 参数：数组字面量 {@code ['a', "b"]} 或单个字符串字面量 {@code 'a'}。 */
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

        // ---- 解析 exports 表达式 ----

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
            }
            return List.of();
        }

        /** 查找 {@code (const|let|var) <name> = { ... }} 的对象字面量 `{` 位置。 */
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

        // ---- 对象字面量成员解析 ----

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
                // 计算属性，跳过
                int close = matchBracket(i);
                return new Member(null, close + 1);
            } else if (isIdentStart(c)) {
                int ke = readIdent(i);
                String first = s.substring(i, ke);
                // async 前缀：取后者为 key；get/set → getterSetterMember
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
            // 泛型方法简写：key<Gen>(...)
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
            // 简写属性：引用 key 本身
            String sig = resolveReferenceSig(key);
            return new Member(sig, afterKey);
        }

        /** 方法简写 {@code key<G>(params): ret { body }} → {@code export function key<G>(params): ret;} */
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
         * 属性 {@code key: value}。value 为 arrow/function → {@code export function key(...)...}；
         * 否则 {@code export const key: <inferred>;}。
         */
        private Member propertyMember(String key, int valueStart) {
            int i = skipWsAndComments(valueStart);
            // async 前缀
            if (i < n && isIdentStart(s.charAt(i))) {
                int ke = readIdent(i);
                if (s.substring(i, ke).equals("async")) i = skipWsAndComments(ke);
            }
            Member fn = functionValueSig(key, i, valueStart);
            if (fn != null) return fn;
            if (i < n && s.charAt(i) == '{') {
                return inlineObjectMember(key, i);
            }
            // 字面量 / 引用
            int ve = valueEnd(valueStart);
            String type = inferValueType(valueStart, ve);
            return new Member("export const " + key + ": " + type + ";", ve);
        }

        /** 嵌套对象字面量 → export const key: { a: T; method(): R; ... };（递归，空对象→Record<string,unknown>）*/
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

        /** 对象字面量 get/set → export const prop: T;（get 返回类型 / set 首参类型；get+set 同名由 seen 去重）*/
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

        /** arrow {@code (params): Ret => body} 或 function 值 → export function 签名；否则 null。 */
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
                    if (p < n && isIdentStart(s.charAt(p))) p = readIdent(p); // 可选名字
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

        // ---- 引用解析 ----

        /** 解析简写引用的标识符：function → export function；const/let/var → export const name: type；否则 unknown。 */
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

        // ---- class 成员提取（constructor/方法/static/字段/get-set）----

        /** 查找 {@code class name<Gen> extends Base implements I { ... }}，生成 export declare class 签名；不存在返回 null。 */
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
                return withLeadingDoc(i, "export declare class " + name + generics + (headStr.isEmpty() ? "" : " " + headStr) + " {}");
            }
            int braceClose = matchBrace(braceOpen);
            if (braceClose < 0) return null;
            List<String> members = parseClassMembers(braceOpen + 1, braceClose);
            StringBuilder sb = new StringBuilder("export declare class ").append(name).append(generics);
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
                    String params = s.substring(afterKey, parenClose + 1);
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

        /** 查找 {@code function <name><G>(params): ret}，返回完整 export function 签名；不存在返回 null。 */
        private String funcSigOf(String name) {
            int i = findKeywordThenIdent("function", name);
            if (i < 0) return null;
            int after = skipWsAndComments(i + "function".length());
            if (after < n && s.charAt(after) == '*') after = skipWsAndComments(after + 1); // generator
            int ne = readIdent(after); // 跳过 name
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

        /** 查找 {@code (const|let|var) <name>} 定义，返回其类型注解或字面量推断类型；不存在返回 null。 */
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

        /** 查找 {@code const|let|var <name>} 中 name 的起始位置；不存在返回 -1。 */
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

        /** 查找 {@code <keyword> <name>} 中 keyword 的起始位置（keyword 与 name 之间仅空白/注释）。 */
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

        // ---- 值类型推断 ----

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

        // ---- 顶层 type/interface/enum 收集 ----

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
                    if ((w.equals("type") || w.equals("interface") || w.equals("enum"))
                            && isDeclAfter(we)) {
                        int declEnd = w.equals("type") ? typeDeclEnd(i) : blockDeclEnd(i);
                        sb.append(s.substring(i, declEnd).trim()).append('\n');
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

        /** keyword 后是否跟标识符或字符串（确认是声明而非变量名等）。 */
        private boolean isDeclAfter(int afterKw) {
            int p = skipWsAndComments(afterKw);
            return p < n && (isIdentStart(s.charAt(p)) || s.charAt(p) == '\'' || s.charAt(p) == '"');
        }

        /**
         * type 声明结束：顶层（depth=0）的 {@code ;}，或无分号时换行结束（联合/交叉跨行用 {@code |}/{@code &} 续行）。
         *
         * <p>{@code <}/{@code >} 用独立 angle 计数（与 {@link #valueEnd}/{@link #typeEnd} 一致），
         * 避免 {@code =>} 箭头的 {@code >} 被误判为泛型闭合而提前归零 depth —— 否则
         * {@code type X = { m: (a: T) => R; ... }} 会在首个 {@code ;} 截断，丢失闭合 {@code }}，
         * 导致外层 {@code declare module} 借用闭合 {@code }}，TS 报 "Expected '}'"。
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

        /** interface/enum 声明结束：第一个 `{` 的匹配 `}` 后（无体则到 `;`）。 */
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

        /** 若 pos 前紧贴 JSDoc 块注释，把它作为 sig 的前导文档输出（注释 + 换行 + sig）；否则原样返回 sig。 */
        private String withLeadingDoc(int pos, String sig) {
            String doc = docCommentBefore(pos);
            return doc == null ? sig : doc + "\n" + sig;
        }

        /** 返回紧贴 pos 之前（仅隔空白）的 JSDoc 块注释原文；无则 null。块体内不含块尾分隔符，故 lastIndexOf 可定位块首。 */
        private String docCommentBefore(int pos) {
            int i = pos - 1;
            while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
            if (i < 3 || s.charAt(i) != '/' || s.charAt(i - 1) != '*') return null;
            int start = s.lastIndexOf("/**", i - 1);
            if (start < 0) return null;
            return s.substring(start, i + 1);
        }

        // ---- 扫描原语 ----

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

        /** 类型表达式结束（复刻 Eraser.typeExpressionEnd 语义）。 */
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
            i++;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\') { i += 2; continue; }
                if (c == quote) return i + 1;
                i++;
            }
            return n;
        }

        private int skipTemplate(int i) {
            i++;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\') { i += 2; continue; }
                if (c == '`') return i + 1;
                i++;
            }
            return n;
        }

        private int skipLineComment(int i) {
            while (i < n && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++;
            return i;
        }

        private int skipBlockComment(int i) {
            while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
            return Math.min(n, i + 2);
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
            int i = Math.min(idx, n - 1);
            while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
            return i;
        }

        /** 返回包含位置 pb（ident 尾）的标识符起始位置；非 ident 返回 -1。 */
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
            while (i < n && isIdentPart(s.charAt(i))) i++;
            return i;
        }

        private static boolean isIdentStart(char c) {
            return Character.isUnicodeIdentifierStart(c) || c == '$' || c == '_';
        }

        private static boolean isIdentPart(char c) {
            return Character.isUnicodeIdentifierPart(c) || c == '$' || c == '_';
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

    // ============================ .js+JSDoc 提取器 ============================

    /**
     * 扫描 .js+JSDoc 源码：解析 {@code /** *​/} 注释的 @typedef/@callback 生成类型声明，
     * 解析 {@code __nekoNodeDefine} 导出对象成员的紧邻前导 JSDoc（@param/@returns/@type）
     * 生成 export 签名。函数签名完全来自 JSDoc 标签（不解析运行时参数，鼓励显式 JSDoc）。
     */
    static final class JSDocExtractor {
        private final String s;
        private final int n;

        JSDocExtractor(String src) {
            this.s = src;
            this.n = src.length();
        }

        record JSDoc(int start, int end, List<Tag> tags) {}
        record Tag(String name, String type, String param, String desc) {}
        record Member(String sig, int next) {}

        String extract() {
            int[] call = findDefineCall();
            if (call == null) return "";
            List<String> ids = parseIds(call[0], call[1]);
            if (ids.isEmpty()) return "";
            List<JSDoc> docs = parseAllJSDocs();
            String typeDecls = collectTypedefs(docs);
            List<String> members = resolveExports(call[2], call[3]);
            if (members.isEmpty() && typeDecls.isBlank()) return "";
            StringBuilder body = new StringBuilder();
            for (String m : members) {
                body.append("    ").append(m).append('\n');
            }
            if (!typeDecls.isBlank()) {
                body.append(indentBlock(typeDecls));
            }
            StringBuilder out = new StringBuilder();
            for (String id : ids) {
                out.append("declare module '").append(id).append("' {\n").append(body).append("}\n");
            }
            return out.toString();
        }

        // ---- JSDoc 块解析 ----

        private List<JSDoc> parseAllJSDocs() {
            List<JSDoc> docs = new ArrayList<>();
            int i = 0;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 2 < n && s.charAt(i + 1) == '*' && s.charAt(i + 2) == '*') {
                    int end = skipBlockComment(i + 2);
                    String content = s.substring(i + 3, Math.max(i + 3, end - 2));
                    docs.add(new JSDoc(i, end, parseTags(content)));
                    i = end;
                    continue;
                }
                i++;
            }
            return docs;
        }

        private List<Tag> parseTags(String content) {
            List<Tag> tags = new ArrayList<>();
            for (String raw : content.split("\n")) {
                String line = stripStar(raw).trim();
                if (line.startsWith("@")) {
                    Tag t = parseTag(line);
                    if (t != null) tags.add(t);
                }
            }
            return tags;
        }

        private String stripStar(String line) {
            int i = 0;
            while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
            if (i < line.length() && line.charAt(i) == '*') i++;
            return line.substring(i);
        }

        private Tag parseTag(String line) {
            int sp = indexOfSpace(line);
            String head = sp < 0 ? line : line.substring(0, sp);
            String tag = head.substring(1);
            String rest = (sp < 0 ? "" : line.substring(sp + 1)).trim();
            String type = null;
            if (rest.startsWith("{")) {
                int close = matchBraceString(rest, 0);
                if (close > 0) {
                    type = rest.substring(1, close).trim();
                    rest = rest.substring(close + 1).trim();
                }
            }
            String param = null;
            String desc = rest;
            switch (tag) {
                case "param", "arg", "argument", "property", "prop" -> {
                    String nt = firstToken(rest);
                    if (nt != null) {
                        param = stripBrackets(nt);
                        int ni = rest.indexOf(nt);
                        desc = rest.substring(ni + nt.length()).replaceFirst("^\\s*(-)?\\s*", "").trim();
                    }
                }
                case "typedef", "callback" -> {
                    param = firstToken(rest);
                    desc = "";
                }
                default -> {
                    // returns/return/type 等：type 已提取，rest 为描述
                }
            }
            return new Tag(tag, type, param, desc);
        }

        /** 在子串中匹配 `{` 到对应 `}`（JSDoc {Type} 可能含嵌套对象类型 {{...}}）。 */
        private static int matchBraceString(String str, int open) {
            int depth = 0;
            for (int i = open; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) return i; }
            }
            return -1;
        }

        private int indexOfSpace(String line) {
            for (int i = 0; i < line.length(); i++) {
                if (Character.isWhitespace(line.charAt(i))) return i;
            }
            return -1;
        }

        private String firstToken(String rest) {
            if (rest == null || rest.isEmpty()) return null;
            int i = 0;
            while (i < rest.length() && Character.isWhitespace(rest.charAt(i))) i++;
            int start = i;
            while (i < rest.length() && !Character.isWhitespace(rest.charAt(i))) i++;
            return i > start ? rest.substring(start, i) : null;
        }

        private String stripBrackets(String t) {
            if (t == null) return null;
            String x = t;
            if (x.startsWith("[")) x = x.substring(1);
            if (x.endsWith("]")) x = x.substring(0, x.length() - 1);
            return x;
        }

        // ---- typedef / callback → type 声明 ----

        private String collectTypedefs(List<JSDoc> docs) {
            StringBuilder sb = new StringBuilder();
            for (JSDoc d : docs) {
                Tag td = findTag(d, "typedef");
                if (td != null && td.type() != null && td.param() != null) {
                    sb.append("type ").append(td.param()).append(" = ").append(mapType(td.type())).append(";\n");
                    continue;
                }
                Tag cb = findTag(d, "callback");
                if (cb != null && cb.param() != null) {
                    sb.append("type ").append(cb.param()).append(" = ").append(buildFunctionType(d)).append(";\n");
                }
            }
            return sb.toString();
        }

        private String buildFunctionType(JSDoc d) {
            StringBuilder params = new StringBuilder("(");
            boolean first = true;
            for (Tag t : d.tags()) {
                if (isParamTag(t) && t.param() != null) {
                    if (!first) params.append(", ");
                    params.append(t.param()).append(": ").append(t.type() != null ? mapType(t.type()) : "unknown");
                    first = false;
                }
            }
            params.append(")");
            Tag r = findTag(d, "returns");
            if (r == null) r = findTag(d, "return");
            String ret = (r != null && r.type() != null) ? mapType(r.type()) : "void";
            return params + " => " + ret;
        }

        // ---- exports 解析（成员关联紧邻前导 JSDoc）----

        private List<String> resolveExports(int start, int end) {
            int i = skipWsAndComments(start);
            if (i >= end) return List.of();
            char c = s.charAt(i);
            int bodyStart;
            int bodyEnd;
            if (c == '{') {
                int close = matchBrace(i);
                bodyStart = i + 1;
                bodyEnd = close < 0 ? end : close;
            } else if (isIdentStart(c)) {
                int ne = readIdent(i);
                String name = s.substring(i, ne);
                int defOpen = findExportsVarDef(name);
                if (defOpen < 0) return List.of();
                int close = matchBrace(defOpen);
                bodyStart = defOpen + 1;
                bodyEnd = close < 0 ? end : close;
            } else {
                return List.of();
            }
            return parseMembersWithJSDoc(bodyStart, bodyEnd);
        }

        private List<String> parseMembersWithJSDoc(int bodyStart, int bodyEnd) {
            List<String> sigs = new ArrayList<>();
            int i = bodyStart;
            JSDoc cur = null;
            while (i < bodyEnd) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == ',') { i++; continue; }
                if (c == '}') break;
                // JSDoc 块 → 作为下一个成员的前导注释
                if (c == '/' && i + 2 < n && s.charAt(i + 1) == '*' && s.charAt(i + 2) == '*') {
                    int e = skipBlockComment(i + 2);
                    cur = new JSDoc(i, e, parseTags(s.substring(i + 3, Math.max(i + 3, e - 2))));
                    i = e;
                    continue;
                }
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                    while (i < bodyEnd && s.charAt(i) != '\n') i++;
                    continue;
                }
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                    i = skipBlockComment(i + 2);
                    continue;
                }
                Member m = parseOneMember(i, bodyEnd, cur);
                cur = null; // 消费前导注释
                if (m == null) { i++; continue; }
                if (m.sig() != null) sigs.add(m.sig());
                i = m.next();
            }
            return sigs;
        }

        private Member parseOneMember(int start, int limit, JSDoc doc) {
            int i = start;
            char c = s.charAt(i);
            String key;
            if (c == '\'' || c == '"') {
                int q = skipString(i, c);
                key = s.substring(i + 1, q - 1);
                i = q;
            } else if (c == '[') {
                return new Member(null, matchBracket(i) + 1);
            } else if (isIdentStart(c)) {
                int ke = readIdent(i);
                String first = s.substring(i, ke);
                if (first.equals("async") || first.equals("get") || first.equals("set")) {
                    int nx = skipWs(ke);
                    if (nx < limit && isIdentStart(s.charAt(nx))) {
                        i = nx;
                        ke = readIdent(i);
                    }
                }
                key = s.substring(i, ke);
                i = ke;
            } else {
                return new Member(null, i + 1);
            }
            int afterKey = skipWs(i);
            if (afterKey < n && s.charAt(afterKey) == '(') {
                return new Member(methodSigFromDoc(key, doc), skipMemberAfterParen(afterKey));
            }
            if (afterKey < n && s.charAt(afterKey) == ':') {
                int vs = skipWs(afterKey + 1);
                int ve = valueEnd(vs);
                Tag typeTag = doc != null ? findTag(doc, "type") : null;
                String t = (typeTag != null && typeTag.type() != null) ? mapType(typeTag.type()) : inferJsValue(vs, ve);
                return new Member("export const " + key + ": " + t + ";", ve);
            }
            // 简写引用：.js 无类型注解，降级 unknown
            return new Member("export const " + key + ": unknown;", afterKey);
        }

        private int skipMemberAfterParen(int parenOpen) {
            int pc = matchParen(parenOpen);
            if (pc < 0) return parenOpen + 1;
            int j = skipWs(pc + 1);
            if (j < n && s.charAt(j) == '{') return matchBrace(j) + 1;
            if (j < n && s.charAt(j) == ';') return j + 1;
            return pc + 1;
        }

        private String methodSigFromDoc(String key, JSDoc doc) {
            StringBuilder params = new StringBuilder("(");
            if (doc != null) {
                boolean first = true;
                for (Tag t : doc.tags()) {
                    if (isParamTag(t) && t.param() != null) {
                        if (!first) params.append(", ");
                        params.append(t.param()).append(": ").append(t.type() != null ? mapType(t.type()) : "unknown");
                        first = false;
                    }
                }
            }
            params.append(")");
            String ret = "void";
            if (doc != null) {
                Tag r = findTag(doc, "returns");
                if (r == null) r = findTag(doc, "return");
                if (r != null && r.type() != null) ret = mapType(r.type());
            }
            return "export function " + key + params + ": " + ret + ";";
        }

        private boolean isParamTag(Tag t) {
            return t.name().equals("param") || t.name().equals("arg") || t.name().equals("argument");
        }

        private Tag findTag(JSDoc d, String name) {
            for (Tag t : d.tags()) {
                if (t.name().equals(name)) return t;
            }
            return null;
        }

        private String inferJsValue(int vs, int ve) {
            int i = skipWs(vs);
            if (i >= ve) return "unknown";
            char c = s.charAt(i);
            if (c == '\'' || c == '"' || c == '`') return "string";
            if (Character.isDigit(c)) return "number";
            String tok = readTokenStr(i, ve);
            if (tok.equals("true") || tok.equals("false")) return "boolean";
            if (tok.equals("null")) return "null";
            return "unknown";
        }

        private String readTokenStr(int start, int end) {
            StringBuilder sb = new StringBuilder();
            int i = start;
            while (i < end && isIdentPart(s.charAt(i))) {
                sb.append(s.charAt(i));
                i++;
            }
            return sb.toString();
        }

        private String mapType(String t) {
            if (t == null) return "unknown";
            String tt = t.trim();
            if (tt.isEmpty() || tt.equals("*") || tt.equals("any") || tt.equals("?")) return "unknown";
            if (tt.equals("Object") || tt.equals("object")) return "Record<string, unknown>";
            if (tt.equals("Array") || tt.equals("array")) return "unknown[]";
            if (tt.equals("String")) return "string";
            if (tt.equals("Number")) return "number";
            if (tt.equals("Boolean")) return "boolean";
            if (tt.startsWith("Array.<") && tt.endsWith(">")) return tt.substring(7, tt.length() - 1) + "[]";
            if (tt.startsWith("Array<") && tt.endsWith(">")) return tt.substring(6, tt.length() - 1) + "[]";
            if (tt.startsWith("Object<") && tt.endsWith(">")) return "Record<" + tt.substring(7, tt.length() - 1) + ">";
            return tt;
        }

        // ---- __nekoNodeDefine 定位 ----

        private int[] findDefineCall() {
            String needle = "__nekoNodeDefine";
            int from = 0;
            while (true) {
                int idx = s.indexOf(needle, from);
                if (idx < 0) return null;
                from = idx + needle.length();
                if (idx > 0 && isIdentPart(s.charAt(idx - 1))) continue;
                int po = skipWsAndComments(idx + needle.length());
                if (po >= n || s.charAt(po) != '(') continue;
                int pc = matchParen(po);
                if (pc < 0) continue;
                int[] args = splitTopLevelArgs(po, pc);
                if (args == null) continue;
                List<String> ids = parseIds(args[0], args[1]);
                if (ids.isEmpty()) continue;
                return new int[] { args[0], args[1], args[2], args[3] };
            }
        }

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
            if (firstEnd < 0) return null;
            return new int[] { firstStart, firstEnd, secondStart, trimEnd(close) };
        }

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

        // ---- 扫描原语 ----

        private int skipWs(int i) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            return i;
        }

        private int skipString(int i, char q) {
            i++;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\') { i += 2; continue; }
                if (c == q) return i + 1;
                i++;
            }
            return n;
        }

        private int skipTemplate(int i) {
            i++;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\') { i += 2; continue; }
                if (c == '`') return i + 1;
                i++;
            }
            return n;
        }

        private int skipLineComment(int i) {
            while (i < n && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++;
            return i;
        }

        private int skipBlockComment(int i) {
            while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
            return Math.min(n, i + 2);
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

        private int matchParen(int open) { return matchPair(open, '(', ')'); }
        private int matchBrace(int open) { return matchPair(open, '{', '}'); }
        private int matchBracket(int open) { return matchPair(open, '[', ']'); }

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

        private int valueEnd(int start) {
            int i = start;
            int depth = 0;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/' && i + 1 < n) {
                    if (s.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (s.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                }
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') { if (depth == 0) return i; depth--; }
                else if (c == ',' && depth == 0) return i;
                i++;
            }
            return n;
        }

        private int readIdent(int i) {
            while (i < n && isIdentPart(s.charAt(i))) i++;
            return i;
        }

        private static boolean isIdentStart(char c) {
            return Character.isUnicodeIdentifierStart(c) || c == '$' || c == '_';
        }

        private static boolean isIdentPart(char c) {
            return Character.isUnicodeIdentifierPart(c) || c == '$' || c == '_';
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
