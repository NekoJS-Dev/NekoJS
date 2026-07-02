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
     * 擦除 TypeScript 类型注解，返回可直接 eval 的 JS 源码（enum/namespace/参数属性同步降级）。
     *
     * <p>供 node 模块加载器等"脚本式 .ts → JS"场景使用：.ts 模块用脚本兼容写法
     * （IIFE + {@code __nekoNodeDefine}），类型注解由本方法擦除后即可在脚本上下文求值。
     *
     * @param file   用于错误定位的文件路径（可为虚拟路径）
     * @param source TypeScript 源码
     * @return 擦除类型后的 JS 源码
     */
    public static String eraseTypescript(Path file, String source) {
        String original = source == null ? "" : source;
        return new Eraser(file, original).erase();
    }

    record TypeScriptTransformResult(String code, String sourceMap) {}

    private static final class Eraser {
        private final Path file;
        private final String source;
        private final StringBuilder out;
        private final int length;

        private Eraser(Path file, String source) {
            this.file = file;
            this.source = source;
            this.out = new StringBuilder(source);
            this.length = source.length();
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
                        eraseRange(i, end);
                        i = end;
                        continue;
                    }
                    // enum/namespace/module：不再抛异常，作为普通标识符跳过，留给转换阶段（transformEnums/Namespaces）处理
                    if ("declare".equals(word)) {
                        i = eraseDeclare(i);
                        continue;
                    }
                    if ("import".equals(word) && typeKeywordAfter(end)) {
                        i = eraseStatement(i);
                        continue;
                    }
                    if ("export".equals(word) && typeExportAfter(end)) {
                        i = eraseStatement(i);
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
                    i = end;
                    continue;
                }
                if (c == '<' && genericTypeArgumentsAt(i)) {
                    int end = matchingAngle(i);
                    eraseRange(i, end + 1);
                    i = end + 1;
                    continue;
                }
                if (c == ':' && typeAnnotationAt(i)) {
                    int end = typeAnnotationEnd(i + 1);
                    // 可选参数 name?: T → 连带擦 ?
                    int q = previousNonWhitespace(i - 1);
                    int start = (q >= 0 && source.charAt(q) == '?') ? q : i;
                    eraseRange(start, end);
                    i = end;
                    continue;
                }
                if (c == '!') {
                    if (definiteAssignmentAt(i)) {
                        eraseRange(i, i + 1);
                        // 定值断言 `x!: T` 的 `!` 擦除后，source 里 `!` 仍在，typeAnnotationAt 会因
                        // `:` 前是 `!` 而误判为非类型注解 → 这里连带擦除 `: T`
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
                i++;
            }
            transform();
            return out.toString();
        }

        // ===== 阶段2：转换（enum/namespace/参数属性）—— 在 out 上 replace，改变长度，source map 行号可能偏移 =====

       private void transform() {
           transformEnums();
           transformNamespaces();
           transformParameterProperties();
            transformClassMemberModifiers();
       }

        // ---- class 体内字段/方法前的可见性修饰符擦除（保留 static；JS class 字段不支持 public/private/protected/readonly/abstract/override）----
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

        /** 定位 class 体并擦除体内成员修饰符；返回 class 体结束后的位置。 */
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
                if (c == ';' || c == '\n') return i + 1; // declare class 无体
                i++;
            }
            if (braceOpen < 0) return out.length();
            int braceClose = matchOutBrace(braceOpen);
            if (braceClose < 0) return out.length();
            eraseClassMemberModifiers(braceOpen + 1, braceClose);
            return braceClose + 1;
        }

        /** 在 class 体内（depth=0 处）擦除 public/private/protected/readonly/abstract/override；保留 static。 */
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

        // ---- enum（含 const enum）→ IIFE 对象（数字双向映射 / 字符串单向）----
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
                        if (outKeywordAt(after, "enum")) { i = transformOneEnum(i); continue; } // 从 const 起替换
                    }
                    if (outKeywordAt(i, "enum")) { i = transformOneEnum(i); continue; }
                }
                i++;
            }
        }

        private int transformOneEnum(int start) {
            int i = start;
            if (outKeywordAt(i, "const")) i = nextOutNonWhitespace(i + 5); // 跳过 const enum 的 const
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
            String iife = generateEnumIife(name, parseEnumMembers(body));
            out.replace(start, braceClose + 1, iife);
            return start + iife.length();
        }

        private record EnumMember(String name, String valueExpr, boolean hasValue) {}

        private List<EnumMember> parseEnumMembers(String body) {
            List<EnumMember> members = new ArrayList<>();
            int i = 0, n = body.length(), segStart = 0;
            while (i < n) {
                char c = body.charAt(i);
                if (c == '\'' || c == '"') { i = skipIn(body, i, c); continue; }
                if (c == '`') { while (i < n && body.charAt(i) != '`') i++; if (i < n) i++; continue; }
                if (c == '/' && i + 1 < n && body.charAt(i + 1) == '/') { while (i < n && body.charAt(i) != '\n') i++; continue; }
                if (c == '/' && i + 1 < n && body.charAt(i + 1) == '*') { i += 2; while (i + 1 < n && !(body.charAt(i) == '*' && body.charAt(i + 1) == '/')) i++; i += 2; continue; }
                if (c == '(') { int d = 0; while (i < n) { char ch = body.charAt(i); if (ch == '\'') { i = skipIn(body, i, '\''); continue; } if (ch == '"') { i = skipIn(body, i, '"'); continue; } if (ch == '(') d++; else if (ch == ')') { d--; if (d == 0) { i++; break; } } i++; } continue; }
                if (c == ',') { addEnumMember(members, body, segStart, i); segStart = i + 1; }
                i++;
            }
            addEnumMember(members, body, segStart, n);
            return members;
        }

        private void addEnumMember(List<EnumMember> members, String body, int start, int end) {
            String raw = body.substring(start, end).trim();
            if (raw.isEmpty()) return;
            int eq = findTopLevelEq(raw);
            if (eq < 0) { members.add(new EnumMember(stripComment(raw), null, false)); return; }
            String name = stripComment(raw.substring(0, eq).trim());
            String value = raw.substring(eq + 1).trim();
            if (name.isEmpty()) return;
            members.add(new EnumMember(name, value, true));
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
            long next = 0;
            for (EnumMember m : members) {
                String nm = m.name();
                if (nm.isEmpty()) continue;
                if (!m.hasValue()) {
                    sb.append(name).append("[").append(name).append("[\"").append(nm).append("\"] = ").append(next).append("] = \"").append(nm).append("\"; ");
                    next++;
                } else if (isStringLit(m.valueExpr())) {
                    sb.append(name).append("[\"").append(nm).append("\"] = ").append(m.valueExpr()).append("; ");
                } else if (isNumberLit(m.valueExpr())) {
                    long num;
                    try { num = Long.parseLong(m.valueExpr()); } catch (NumberFormatException e) { num = next; }
                    sb.append(name).append("[").append(name).append("[\"").append(nm).append("\"] = ").append(num).append("] = \"").append(nm).append("\"; ");
                    next = num + 1;
                } else {
                    sb.append(name).append("[\"").append(nm).append("\"] = ").append(m.valueExpr()).append("; ");
                }
            }
            sb.append("})(").append(name).append(" || (").append(name).append(" = {}));");
            return sb.toString();
        }

        private boolean isStringLit(String v) { return !v.isEmpty() && (v.charAt(0) == '"' || v.charAt(0) == '\''); }

        private boolean isNumberLit(String v) {
            if (v.isEmpty()) return false;
            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (i == 0 && (c == '-' || c == '+')) continue;
                if (!Character.isDigit(c)) return false;
            }
            return true;
        }

        // ---- namespace（单层）/ module → IIFE，export 成员在末尾批量转 Name.member=member ----
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
            return start + wrapped.length();
        }

        private String generateNamespaceIife(String name, String body) {
            List<String> members = new ArrayList<>();
            StringBuilder cleaned = new StringBuilder();
            int i = 0, n = body.length(), lastCopy = 0;
            while (i < n) {
                char c = body.charAt(i);
                if (c == '\'' || c == '"') { i = skipIn(body, i, c); continue; }
                if (c == '`') { while (i < n && body.charAt(i) != '`') i++; if (i < n) i++; continue; }
                if (c == '/' && i + 1 < n && body.charAt(i + 1) == '/') { while (i < n && body.charAt(i) != '\n') i++; continue; }
                if (c == '/' && i + 1 < n && body.charAt(i + 1) == '*') { i += 2; while (i + 1 < n && !(body.charAt(i) == '*' && body.charAt(i + 1) == '/')) i++; i += 2; continue; }
                if (bodyKeywordAt(body, i, "export")) {
                    int after = skipInWs(body, i + 6);
                    int kwLen = declKwLen(body, after);
                    if (kwLen > 0) {
                        int ms = skipInWs(body, after + kwLen);
                        int me = ms;
                        while (me < n && isIdentifierPart(body.charAt(me))) me++;
                        String member = body.substring(ms, me);
                        if (!member.isEmpty()) members.add(member);
                        cleaned.append(body, lastCopy, i).append("      "); // 6 空格擦 export
                        lastCopy = i + 6;
                        i += 6;
                        continue;
                    }
                }
                i++;
            }
            cleaned.append(body, lastCopy, n);
            StringBuilder sb = new StringBuilder();
            // 前置 var 声明：namespace 常出现在函数/IIFE 体内，原 (name || (name={})) 依赖外层已声明 name，
            // 严格模式下嵌套作用域会 ReferenceError。var 可重复声明，兼容多次 namespace 合并。
            sb.append("var ").append(name).append(";\n");
            sb.append("(function (").append(name).append(") {\n").append(cleaned).append('\n');
            for (String m : members) sb.append(name).append('.').append(m).append(" = ").append(m).append(";\n");
            sb.append("})(").append(name).append(" || (").append(name).append(" = {}));");
            return sb.toString();
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
            if (bodyKeywordAt(body, i, "let")) return 3;
            if (bodyKeywordAt(body, i, "var")) return 3;
            return 0;
        }

        // ---- 参数属性 constructor(public name) → 擦修饰符 + 构造器体插入 this.name=name ----
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
            int i = nextOutNonWhitespace(start + 11); // 跳过 constructor
            if (i >= out.length() || out.charAt(i) != '(') return start + 11;
            int parenClose = matchOutParen(i);
            if (parenClose < 0) return start + 11;
            String params = out.substring(i + 1, parenClose);
            List<String> assigned = new ArrayList<>();
            String cleanedParams = cleanParamProperties(params, assigned);
            if (assigned.isEmpty()) return parenClose + 1; // 无参数属性，跳过
            int j = parenClose + 1;
            int braceOpen = -1;
            while (j < out.length()) {
                char c = out.charAt(j);
                if (c == '\'' || c == '"') { j = skipOutString(j, c); continue; }
                if (c == '{') { braceOpen = j; break; }
                if (c == ';') return j + 1; // 声明无体
                j++;
            }
            if (braceOpen < 0) return parenClose + 1;
            out.replace(i + 1, parenClose, cleanedParams); // 擦除参数修饰符
            int delta = cleanedParams.length() - (parenClose - i - 1);
            braceOpen += delta;
            StringBuilder assigns = new StringBuilder();
            for (String name : assigned) assigns.append(" this.").append(name).append(" = ").append(name).append(";");
            out.insert(braceOpen + 1, assigns.toString());
            return braceOpen + 1 + assigns.length();
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
            while (i < n) {
                while (i < n && Character.isWhitespace(seg.charAt(i))) i++;
                if (i >= n || !isIdentifierPart(seg.charAt(i))) break;
                int ws = i;
                while (i < n && isIdentifierPart(seg.charAt(i))) i++;
                String word = seg.substring(ws, i);
                boolean isMod = word.equals("public") || word.equals("private") || word.equals("protected") || word.equals("readonly");
                if (isMod) continue;
                firstNonMod = ws;
                paramName = word;
                break;
            }
            if (firstNonMod >= 0 && paramName != null) {
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
                if (c == '(') depth++;
                else if (c == ')') { depth--; if (depth == 0) return i; }
                i++;
            }
            return -1;
        }

        // ---- out（StringBuilder）扫描辅助 ----
        private int skipOutString(int start, char quote) {
            int i = start + 1;
            while (i < out.length()) { char c = out.charAt(i); if (c == '\\') { i += 2; continue; } if (c == quote) return i + 1; i++; }
            return out.length();
        }
        private int skipOutTemplate(int start) {
            int i = start + 1;
            while (i < out.length()) { char c = out.charAt(i); if (c == '\\') { i += 2; continue; } if (c == '`') return i + 1; i++; }
            return out.length();
        }
        private int skipOutLine(int start) { while (start < out.length() && out.charAt(start) != '\n') start++; return start; }
        private int skipOutBlock(int start) {
            while (start + 1 < out.length() && !(out.charAt(start) == '*' && out.charAt(start + 1) == '/')) start++;
            return Math.min(out.length(), start + 2);
        }
        private int nextOutNonWhitespace(int i) { while (i < out.length() && Character.isWhitespace(out.charAt(i))) i++; return i; }
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
                if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) return i; }
                i++;
            }
            return -1;
        }
        private int skipIn(String s, int i, char quote) {
            i++;
            int n = s.length();
            while (i < n) { char c = s.charAt(i); if (c == '\\') { i += 2; continue; } if (c == quote) return i + 1; i++; }
            return n;
        }

        /** type/interface 后是否跟标识符（声明名），区分 type 别名 vs 属性名/变量名 type。*/
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
            if (startsWithKeyword(after, "global") || startsWithKeyword(after, "module") || startsWithKeyword(after, "namespace")) {
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

        private boolean genericTypeArgumentsAt(int start) {
            int previous = previousNonWhitespace(start - 1);
            if (previous < 0) return false;
            char previousChar = source.charAt(previous);
            if (!isIdentifierPart(previousChar) && previousChar != ')' && previousChar != ']') return false;
            int close = matchingAngle(start);
            if (close < 0) return false;
            int next = nextNonWhitespace(close + 1);
            return next < length && (source.charAt(next) == '(' || source.charAt(next) == '{');
        }

        private boolean typeAnnotationAt(int colon) {
            // 三元 cond ? a : b 的 : 不是类型注解——先判，覆盖 : 前为 )/]/}/数字/字符串等所有前导
            // （isTernaryColon 已排除 ?: 可选参数与 ?. 可选链）
            if (isTernaryColon(colon)) return false;
            int previous = previousNonWhitespace(colon - 1);
            if (previous < 0) return false;
            char previousChar = source.charAt(previous);
            // 可选参数 name?: T → : 前 ?，? 前 ident
            if (previousChar == '?') {
                int beforeQ = previousNonWhitespace(previous - 1);
                return beforeQ >= 0 && isIdentifierPart(source.charAt(beforeQ)) && !objectLiteralPropertyColon(colon);
            }
            if (!isIdentifierPart(previousChar) && previousChar != ')' && previousChar != ']' && previousChar != '}') return false;
            // ) 后 : 需区分函数声明参数列表（返回类型）vs 方法调用/三元
            if (previousChar == ')' && !parenIsReturnType(previous)) return false;
            int next = nextNonWhitespace(colon + 1);
            if (next >= length) return false;
            char nextChar = source.charAt(next);
            if (nextChar == ':' || nextChar == ',' || nextChar == ';' || nextChar == ')' || nextChar == '{') return false;
            return !objectLiteralPropertyColon(colon);
        }

        /** ) 是否函数声明参数列表的闭合（返回类型合法位置）；方法调用/三元返回 false。*/
        private boolean parenIsReturnType(int closeParen) {
            int openParen = backwardMatchParen(closeParen);
            if (openParen < 0) return false;
            int before = previousNonWhitespace(openParen - 1);
            if (before < 0) return true;
            char bc = source.charAt(before);
            if (bc == '.') return false; // obj.method() 调用
            if (bc == '>' || bc == ')') return true; // <T>(params) 或 )) 嵌套
            if (!isIdentifierPart(bc)) return true; // = ( , ; { [ + - 等 → arrow/方法
            int start = before;
            while (start > 0 && isIdentifierPart(source.charAt(start - 1))) start--;
            int beforeName = previousNonWhitespace(start - 1);
            if (beforeName >= 0) {
                char bic = source.charAt(beforeName);
                if (bic == '.') return false; // a.b() 调用
                if (isIdentifierPart(bic)) {
                    int bs = beforeName;
                    while (bs > 0 && isIdentifierPart(source.charAt(bs - 1))) bs--;
                    String prev = source.substring(bs, beforeName + 1);
                    if (prev.equals("function")) return true;
                    if (prev.equals("if") || prev.equals("while") || prev.equals("for") || prev.equals("switch") || prev.equals("catch") || prev.equals("with") || prev.equals("return")) return false;
                }
                return true; // { ; , 等 → 方法简写
            }
            return false;
        }

        /** 从 closeParen 向前找匹配的 (（简化，不跳字符串）。*/
        private int backwardMatchParen(int closeParen) {
            int depth = 0;
            int i = closeParen;
            while (i >= 0) {
                char c = source.charAt(i);
                if (c == ')') depth++;
                else if (c == '(') { depth--; if (depth == 0) return i; }
                i--;
            }
            return -1;
        }

        /** : 是否处于三元表达式（前面同层有配对的 ? 三元，排除 ?: 可选参数与 ?. 可选链）。*/
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
            return objectStart >= 0 && objectLiteralContext(objectStart);
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
            // { 前是 ident 时，return/throw 后 { } 是对象字面量；else/do/try/finally/function名等是 block
            if (isIdentifierPart(c)) {
                int end = previous + 1;
                int start = previous;
                while (start > 0 && isIdentifierPart(source.charAt(start - 1))) start--;
                String kw = source.substring(start, end);
                if (kw.equals("return") || kw.equals("throw")) return true;
                // key: { } —— 属性名后跟对象字面量（key 前 : ），如 constants: { ... }
                int beforeKw = previousNonWhitespace(start - 1);
                return beforeKw >= 0 && source.charAt(beforeKw) == ':';
            }
            // => 后的 { 是箭头函数体 block（非对象字面量），如 (() => { function f(a: T) {} })
            if (c == '>' && previous - 1 >= 0 && source.charAt(previous - 1) == '=') return false;
            return "=(:,[!&|?;{}<>+-*/%".indexOf(c) >= 0;
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
                    // 顶层（非对象/函数/泛型内）的 : 必是三元分隔（如 expr as T : fallback）→ 类型到此结束；
                    // 对象类型 { a: T } 的 : 在 brace>0，函数/箭头类型 (a: T)=>R 的 : 在 paren>0，均不在此分支
                    if (c == '=' || c == ',' || c == ';' || c == ')' || c == '{' || c == '}' || c == ':' || c == '\n' || c == '\r') {
                        return i;
                    }
                }
                i++;
            }
            return i;
        }

        private boolean definiteAssignmentAt(int bang) {
            int previous = previousNonWhitespace(bang - 1);
            int next = nextNonWhitespace(bang + 1);
            // 只认 `:`（x!: T）或 `;`（x!;）；不认 `=`，以免把 `a != b` 的 `!` 误判为定值断言而擦除
            return previous >= 0 && next < length && isIdentifierPart(source.charAt(previous)) && (source.charAt(next) == ':' || source.charAt(next) == ';');
        }

        /** 非空断言 `a!.x` / `a!`：前一非空白字符为 ident 部分/`)`/`]`，且 `!` 后非 `=`（排除 `!=`/`!==`）。 */
        private boolean nonNullAssertionAt(int bang) {
            int previous = previousNonWhitespace(bang - 1);
            if (previous < 0) return false;
            char pc = source.charAt(previous);
            if (!isIdentifierPart(pc) && pc != ')' && pc != ']') return false;
            return bang + 1 >= length || source.charAt(bang + 1) != '=';
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
            int bodyEnd = matchingCloseBrace(bodyStart);
            return bodyEnd < 0 ? length : bodyEnd + 1;
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

        /** 判断 `function` 声明是否为重载签名（无函数体、以 `;` 结尾）。返回整体结束位置（`;` 后），否则 -1。 */
        private int functionOverloadEnd(int wordEnd) {
            int i = nextNonWhitespace(wordEnd);
            if (i < length && source.charAt(i) == '*') i = nextNonWhitespace(i + 1); // generator
            if (i < length && isIdentifierStart(source.charAt(i))) i = readIdentifierEnd(i + 1); // 名字
            i = nextNonWhitespace(i);
            if (i < length && source.charAt(i) == '<') { // 泛型参数 <T>
                int closeAngle = matchingAngle(i);
                if (closeAngle < 0) return -1;
                i = nextNonWhitespace(closeAngle + 1);
            }
            if (i >= length || source.charAt(i) != '(') return -1;
            int close = matchingParen(i);
            if (close < 0) return -1;
            int after = nextNonWhitespace(close + 1);
            if (after < length && source.charAt(after) == ':') { // 返回类型注解
                after = typeExpressionEnd(after + 1);
                after = nextNonWhitespace(after);
            }
            if (after < length && source.charAt(after) == ';') return after + 1; // 重载签名（无函数体）
            return -1; // 有 { 或 =，是函数实现，保留
        }

        private int matchingParen(int open) {
            int depth = 0;
            int i = open;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '(') depth++;
                else if (c == ')') { depth--; if (depth == 0) return i; }
                i++;
            }
            return -1;
        }

        private int skipSlash(int slash) {
            if (slash + 1 >= length) return slash;
            char next = source.charAt(slash + 1);
            if (next == '/') return skipLineComment(slash + 2);
            if (next == '*') return skipBlockComment(slash + 2);
            if (looksLikeRegexStart(slash)) return skipRegex(slash + 1);
            return slash;
        }

        private int skipString(int start, char quote) {
            int i = start + 1;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == quote) return i + 1;
                i++;
            }
            return length;
        }

        private int skipTemplate(int start) {
            int i = start + 1;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '`') return i + 1;
                i++;
            }
            return length;
        }

        private int skipLineComment(int start) {
            int i = start;
            while (i < length && source.charAt(i) != '\n' && source.charAt(i) != '\r') i++;
            return i;
        }

        private int skipBlockComment(int start) {
            int i = start;
            while (i + 1 < length) {
                if (source.charAt(i) == '*' && source.charAt(i + 1) == '/') return i + 2;
                i++;
            }
            return length;
        }

        private int skipRegex(int start) {
            int i = start;
            boolean inClass = false;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '[') inClass = true;
                else if (c == ']') inClass = false;
                else if (c == '/' && !inClass) {
                    i++;
                    while (i < length && isIdentifierPart(source.charAt(i))) i++;
                    return i;
                }
                i++;
            }
            return length;
        }

        private boolean looksLikeRegexStart(int slash) {
            int previous = previousNonWhitespace(slash - 1);
            if (previous < 0) return true;
            char c = source.charAt(previous);
            return "=(:,[!&|?;{}\n\r".indexOf(c) >= 0;
        }

        private int nextNonWhitespace(int index) {
            int i = Math.max(0, index);
            while (i < length && Character.isWhitespace(source.charAt(i))) i++;
            return i;
        }

        private int previousNonWhitespace(int index) {
            int i = Math.min(index, length - 1);
            while (i >= 0 && Character.isWhitespace(source.charAt(i))) i--;
            return i;
        }

        private int readIdentifierEnd(int start) {
            int i = start;
            while (i < length && isIdentifierPart(source.charAt(i))) i++;
            return i;
        }

        private boolean startsWithKeyword(int start, String keyword) {
            if (start < 0 || start + keyword.length() > length || !source.startsWith(keyword, start)) return false;
            boolean before = start == 0 || !isIdentifierPart(source.charAt(start - 1));
            boolean after = start + keyword.length() >= length || !isIdentifierPart(source.charAt(start + keyword.length()));
            return before && after;
        }

        private boolean isIdentifierStart(char c) {
            return Character.isUnicodeIdentifierStart(c) || c == '$' || c == '_';
        }

        private boolean isIdentifierPart(char c) {
            return Character.isUnicodeIdentifierPart(c) || c == '$' || c == '_';
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

        private IllegalArgumentException unsupported(String syntax, int index) {
            return new IllegalArgumentException("Unsupported TypeScript syntax '" + syntax + "' in " + file + " at " + position(index) + ". Use plain erasable TypeScript or register a compiler plugin for this syntax.");
        }

        private String position(int index) {
            int line = 1;
            int column = 1;
            for (int i = 0; i < index && i < length; i++) {
                if (source.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return line + ":" + column;
        }
    }
}
