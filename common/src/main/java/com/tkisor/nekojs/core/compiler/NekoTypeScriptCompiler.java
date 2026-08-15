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
                    if ("import".equals(word) && hasInlineTypeSpecifier(end)) {
                        i = eraseInlineTypeSpecifiers(i, end);
                        continue;
                    }
                    if ("export".equals(word) && typeExportAfter(end)) {
                        i = eraseExportTypeDeclaration(i);
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
                if (c == '@' && decoratorAt(i)) {
                    // 装饰器（@Component / @Log(...)）：NekoJS 是脚本引擎非 TS 框架，不支持装饰器。
                    // 明确报错而非透传成坏 JS（裸 @ 在 JS 里非法，GraalJS 会报错但信息不清晰）。
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
            // 上一个数字成员的名字（用于上一个成员是「计算值」时，下一个无值成员的运行时自增 E["prev"] + 1）。
            // lastNumericKnown=true 表示 next 是编译期已知值（字面量数字）；false 表示上一个数字成员是计算值。
            String lastNumericName = null;
            boolean lastNumericKnown = true;
            for (EnumMember m : members) {
                String nm = m.name();
                if (nm.isEmpty()) continue;
                if (!m.hasValue()) {
                    if (lastNumericName != null && !lastNumericKnown) {
                        // 上一个是计算值数字成员：运行时自增 E["prev"] + 1（编译期不知 prev 的值）
                        String expr = name + "[\"" + lastNumericName + "\"] + 1";
                        sb.append(name).append("[").append(name).append("[\"").append(nm).append("\"] = " + expr + "] = \"").append(nm).append("\"; ");
                        lastNumericName = nm;
                        // 这个成员本身也是「计算」性质（值依赖运行时 prev），后续继续 +1
                    } else {
                        // 已知数字基准（或首个成员从 0 起）：用编译期字面量
                        sb.append(name).append("[").append(name).append("[\"").append(nm).append("\"] = ").append(next).append("] = \"").append(nm).append("\"; ");
                        lastNumericName = nm;
                        lastNumericKnown = true;
                        next++;
                    }
                } else if (isStringLit(m.valueExpr())) {
                    sb.append(name).append("[\"").append(nm).append("\"] = ").append(m.valueExpr()).append("; ");
                    // 字符串成员不提供数字基准
                    lastNumericName = null;
                } else if (isNumberLit(m.valueExpr())) {
                    long num;
                    try {
                        num = parseNumberLit(m.valueExpr());
                    } catch (NumberFormatException e) {
                        throw badEnumNumberLiteral(m.valueExpr());
                    }
                    sb.append(name).append("[").append(name).append("[\"").append(nm).append("\"] = ").append(num).append("] = \"").append(nm).append("\"; ");
                    next = num + 1;
                    lastNumericName = nm;
                    lastNumericKnown = true;
                } else if (looksLikeNumberLiteral(m.valueExpr())) {
                    // 形似数值字面量但未通过 isNumberLit（如 1e、1e+）：报编译期错误，不能透传成坏 JS。
                    throw badEnumNumberLiteral(m.valueExpr());
                } else {
                    // 计算成员：值运行时才知。作为数字基准（TS 视计算 enum 成员为 number），
                    // 下一个无值成员用 E["thisMember"] + 1 运行时自增。
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

        /** Parses a TS numeric literal accepted by {@link #isNumberLit}. Floats truncate toward zero. */
        private long parseNumberLit(String v) {
            String core = v;
            boolean neg = false;
            if (!core.isEmpty() && (core.charAt(0) == '+' || core.charAt(0) == '-')) {
                neg = core.charAt(0) == '-';
                core = core.substring(1);
            }
            if (core.endsWith("n")) core = core.substring(0, core.length() - 1);
            String lower = core.toLowerCase();
            long value;
            if (lower.startsWith("0x")) value = Long.parseUnsignedLong(lower.substring(2), 16);
            else if (lower.startsWith("0o")) value = Long.parseUnsignedLong(lower.substring(2), 8);
            else if (lower.startsWith("0b")) value = Long.parseUnsignedLong(lower.substring(2), 2);
            else if (lower.indexOf('.') >= 0 || lower.indexOf('e') >= 0) value = (long) Double.parseDouble(core);
            else value = Long.parseLong(core);
            return neg ? -value : value;
        }

        /**
         * 形似数值字面量但未通过 {@link #isNumberLit} 的枚举成员值（如 {@code 1e}、{@code 1e+}、
         * {@code 0b2}、{@code 0o8}、{@code 0xG}）。必须整串匹配数值记号形态（可带正负号、
         * 0x/0o/0b 前缀加字母数字、十进制小数/指数、n 后缀），因此 {@code 1 + 2}、
         * {@code (1+2)}、{@code 0xff + 1}、{@code 1 << 2} 都不匹配。前缀分支使用宽松字符类，
         * 因为本方法仅在 {@link #isNumberLit} 返回 false 之后调用，合法字面量不受影响。
         */
        private boolean looksLikeNumberLiteral(String v) {
            if (v.isEmpty()) return false;
            return v.matches("[+-]?(?:(?:0[xX][0-9a-zA-Z]*)|(?:0[oO][0-9a-zA-Z]*)|(?:0[bB][0-9a-zA-Z]*)|(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d*)?)n?");
        }

        private IllegalArgumentException badEnumNumberLiteral(String literal) {
            return new IllegalArgumentException("Invalid TypeScript enum numeric literal '" + literal + "' in " + file);
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
                    } else if (kwLen == -1) {
                        // interface/type：运行时无值，剥除 export 但不作为成员导出。
                        // 其声明体已由 phase1 擦成空格，只剩裸 export + 名字残留，这里一并擦掉 export。
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
            // interface/type：运行时不产生值成员，但 phase1 已把它们的声明体擦成空格，
            // 这里返回长度以便剥除其 export 关键字，避免 IIFE 体内残留裸 export。
            // 注意：不把这类成员加入 members 列表（它们没有运行时绑定可导出）。
            if (bodyKeywordAt(body, i, "interface")) return -1; // 哨兵：剥 export 但不作为值成员
            if (bodyKeywordAt(body, i, "type")) return -1;
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
            ClassContext classContext = enclosingClassForTopLevelConstructor(start);
            if (classContext == null) return start + 11;
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
                if (c == '`') { j = skipOutTemplate(j); continue; }
                if (c == '/' && j + 1 < out.length() && out.charAt(j + 1) == '/') { j = skipOutLine(j + 2); continue; }
                if (c == '/' && j + 1 < out.length() && out.charAt(j + 1) == '*') { j = skipOutBlock(j + 2); continue; }
                if (c == '{') { braceOpen = j; break; }
                if (c == ';') return j + 1; // 声明无体
                j++;
            }
            if (braceOpen < 0) return parenClose + 1;

            int insertionPoint = braceOpen + 1;
            if (classContext.derived()) {
                int superCallEnd = findTopLevelSuperCallEnd(braceOpen);
                if (superCallEnd < 0) {
                    throw new IllegalArgumentException("Cannot transform derived constructor parameter properties in "
                        + file + ": constructor has no legal top-level super(...) call.");
                }
                insertionPoint = superCallEnd;
            }

            out.replace(i + 1, parenClose, cleanedParams); // 擦除参数修饰符
            int delta = cleanedParams.length() - (parenClose - i - 1);
            insertionPoint += delta; // 参数替换发生在构造器体与插入点之前
            StringBuilder assigns = new StringBuilder();
            if (classContext.derived()) assigns.append(';');
            for (String name : assigned) assigns.append(" this.").append(name).append(" = ").append(name).append(";");
            out.insert(insertionPoint, assigns.toString());
            return insertionPoint + assigns.length();
        }

        private record ClassContext(boolean derived, int bodyOpen) {}

        /** 仅认 class 体第一层的 constructor，顺便判定 class 头是否含顶层 extends。 */
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

        /** 返回合法顶层独立 {@code super(...);} 语句的分号后一位。 */
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

        /**
         * 擦除 {@code export type ...} / {@code export interface ...} 声明，仅擦类型声明本身，
         * 不像 {@link #eraseStatement} 那样扫到换行/分号——因为 {@code export interface I { ... }} 后面
         * 可能紧跟其它语句（尤其 namespace 体内），用 statementEnd 会越界吞掉后续语句。
         * 用 statementOrBlockDeclarationEnd：interface 的 {...} 体匹配后即停。
         */
        private int eraseExportTypeDeclaration(int start) {
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

        /** import 语句的命名导入块里是否含内联 type 修饰符（TS 4.5+：{ real, type T }）。*/
        private boolean hasInlineTypeSpecifier(int afterImport) {
            int brace = nextNonWhitespace(afterImport);
            // 只处理命名导入：import { ... } from ...
            if (brace >= length || source.charAt(brace) != '{') return false;
            int close = matchingCloseBracket(brace, '{', '}');
            if (close < 0) return false;
            return findInlineTypeInBlock(brace + 1, close) >= 0;
        }

        /** 在 {...} 块内查找顶层（逗号深度 0）的 `type X` 说明符，返回 type 关键字起点；找不到返回 -1。*/
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
                        // 确认是说明符：后面跟标识符（type X），而非属性名 type
                        int after = nextNonWhitespace(end);
                        if (after < to && isIdentifierStart(source.charAt(after))) return i;
                    }
                }
                i++;
            }
            return -1;
        }

        /** 擦除 import 命名导入块里的所有内联 `type X` 说明符（含尾随逗号），保留值绑定与整条 import。*/
        private int eraseInlineTypeSpecifiers(int start, int afterImport) {
            int brace = nextNonWhitespace(afterImport);
            int close = matchingCloseBracket(brace, '{', '}');
            if (close < 0) return afterImport;
            // 反复擦块内的内联 type 说明符
            int scanFrom = brace + 1;
            while (true) {
                int typeStart = findInlineTypeInBlock(scanFrom, close);
                if (typeStart < 0) break;
                // type 说明符范围：从 typeStart 到下一个值绑定前（含尾随逗号与空白）
                int specEnd = readIdentifierEnd(typeStart + 1); // type 关键字尾
                int nameStart = nextNonWhitespace(specEnd);
                int nameEnd = readIdentifierEnd(nameStart + 1); // X 尾
                int eraseTo = nameEnd;
                // 吃掉后面的逗号（如果有）
                int afterComma = nextNonWhitespace(nameEnd);
                if (afterComma < close && source.charAt(afterComma) == ',') {
                    eraseTo = afterComma + 1;
                } else {
                    // 没有尾随逗号：吃掉前面的逗号（type X 是最后一个）
                    int before = previousNonWhitespace(typeStart - 1);
                    if (before >= brace + 1 && source.charAt(before) == ',') {
                        typeStart = before;
                    }
                }
                eraseRange(typeStart, eraseTo);
                scanFrom = eraseTo; // eraseRange 保长度，close 不变
            }
            return close + 1;
        }

        /** 从 open 开始匹配配对的闭括号（跳过字符串/模板），找不到返回 -1。*/
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
            // 泛型实参/泛型箭头的合法前导：标识符/`)`/`]`（foo<T>、(a)<T>），
            // 或赋值/参数/返回等上下文后的泛型箭头 <T>(x) => …：
            // `=`（const id = <T>…）、`,`/`(`（作为函数实参）、`return`/`=>`/`{`/`;` 等语句起始。
            // 这些上下文里 `<…>(` 或 `<…> =>` 不可能是比较运算（比较不会紧跟 `(` 调用），故可放心擦除。
            boolean arrowContext = previousChar == '=' || previousChar == ','
                || previousChar == '(' || previousChar == '{' || previousChar == ';'
                || previousChar == '\n' || previousChar == '\r' || previousChar == ':';
            if (!isIdentifierPart(previousChar) && previousChar != ')' && previousChar != ']' && !arrowContext) {
                return false;
            }
            // 语句起始关键字（return/throw/yield/await 等）后的 <T>( 也是泛型箭头
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
            // <T>(  或  <T> =>  —— 泛型箭头/泛型调用
            if (nextChar == '(' || nextChar == '{') return true;
            if (nextChar == '=' && next + 1 < length && source.charAt(next + 1) == '>') return true;
            // 非箭头上下文（标识符/) / ] 前导）下，<T>( 仍是泛型调用（如 foo<T>(x)）
            if ((isIdentifierPart(previousChar) || previousChar == ')' || previousChar == ']') && nextChar == '(') {
                return true;
            }
            // 泛型实参出现在表达式里但非紧接调用：foo<T>, / foo<T>) / foo<T>] / foo<T>;
            // （典型场景：TSX 泛型组件 <Foo<number>/> lowering 后的 Foo<number> 表达式）
            // 仅当前导是标识符/`)`/`]` 时认定；前导是 `,`/`=` 等属于箭头上下文（上面 arrowContext 已覆盖）
            if ((isIdentifierPart(previousChar) || previousChar == ')' || previousChar == ']')
                && (nextChar == ',' || nextChar == ')' || nextChar == ']' || nextChar == ';'
                    || nextChar == '\n' || nextChar == '\r')) {
                return true;
            }
            return arrowContext && (nextChar == '(' || (nextChar == '=' && next + 1 < length && source.charAt(next + 1) == '>'));
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

        /**
         * 是否装饰器（{@code @Component} / {@code @Log(...)}）。
         * 判据：{@code @} 后紧跟标识符起始字符，且前一非空白字符是声明起始位置
         *（{@code \n} / {@code }} / {@code ;} / {@code {} 或文件首）。
         * 装饰器总是出现在声明前（类/方法/属性/参数），不会跟在值表达式后面。
         */
        private boolean decoratorAt(int at) {
            if (at + 1 >= length) return false;
            char next = source.charAt(at + 1);
            if (!isIdentifierStart(next)) return false;
            int previous = previousNonWhitespace(at - 1);
            if (previous < 0) return true; // 文件首
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
            return NekoSourceLexerBase.skipSlash(source, length, slash);
        }

        private int skipString(int start, char quote) {
            return NekoSourceLexerBase.skipString(source, length, start, quote);
        }

        private int skipTemplate(int start) {
            return NekoSourceLexerBase.skipTemplate(source, length, start);
        }

        private int nextNonWhitespace(int index) {
            return NekoSourceLexerBase.nextNonWhitespace(source, length, index);
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

        private IllegalArgumentException unsupported(String syntax, int index) {
            String hint;
            if (syntax.contains("decorator")) {
                hint = "Decorators are not supported: NekoJS is a scripting engine, not a TypeScript framework. Replace the decorator with a plain function call (e.g. wrap your class/function with a helper instead of @Decorator).";
            } else {
                hint = "Use plain erasable TypeScript or register a compiler plugin for this syntax.";
            }
            return new IllegalArgumentException("Unsupported TypeScript syntax '" + syntax + "' in " + file + " at " + position(index) + ". " + hint);
        }

        private String position(int index) {
            return NekoSourceLexerBase.position(source, length, index);
        }
    }
}
