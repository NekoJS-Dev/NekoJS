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
 * 全局绑定成员 preflight（事件外场景）：脚本任意位置对 schema 绑定（Utils/Item/…含事件组）
 * 的成员访问检查。三层：
 * <ol>
 *   <li><b>直接成员</b>：{@code Utils.serverTel()}（拼错）按 {@link ScriptBindingSchema.BindingMembers#memberNames()}
 *       检查（含 DelegatingBinding 的 helper + 目标类静态）；</li>
 *   <li><b>链式类型流</b>：{@code Item.of(x).witherCont(3)}——绑定经调用/属性取值后的后续成员
 *       按 {@link BindingMembers#valueClasses()} 的反射成员检查（第二级及更深）；</li>
 *   <li><b>未定义标识符</b>：作为成员访问对象（{@code Util.xxx}——绑定名拼错）或调用目标
 *       （{@code qwq()}）的未知标识符。已知集合 = 文件内声明（含参数）+ import/class/catch/
 *       解构/for-of 的源级补收 + schema 绑定名 + {@link ScriptBindingSchema#knownGlobals 运行时收割的
 *       全局全集}。globals 未登记（测试/降级）时该项整体跳过。裸标识符表达式（{@code qwq} 单独
 *       成句）**不报**：ValParser 会把 {@code typeof x} 的操作数泄漏为独立语句，无法与真未定义区分。</li>
 * </ol>
 *
 * <p>报告不阻止编译/执行；ValParser 解析失败的文件整文件跳过（warn 日志）。
 */
public final class GlobalBindingMemberValidator {

    /**
     * JS 关键字黑名单：ValParser 把 {@code if (x)} / {@code catch (e)} 等关键字前缀结构
     * 解析成 CallExpr(关键字, 参数)，未定义标识符检查必须放行这些「伪调用目标」。
     */
    private static final Set<String> JS_KEYWORDS = Set.of(
            "if", "else", "for", "while", "do", "switch", "case", "default", "try", "catch",
            "finally", "return", "throw", "break", "continue", "typeof", "instanceof", "in",
            "of", "new", "delete", "void", "await", "yield", "class", "function", "import",
            "export", "extends", "const", "let", "var", "enum");

    private GlobalBindingMemberValidator() {}

    public static void validate(Path file, String source) {
        if (file == null || source == null || source.isEmpty()) return;
        Map<String, ScriptBindingSchema.BindingMembers> schema = ScriptBindingSchema.schemaForPath(file);
        if (schema.isEmpty()) return;

        ValNode.Block ast;
        try {
            ast = ValParser.parse(source);
        } catch (Throwable e) {
            // 与 EventCallbackSourceValidator 一致：不静默跳过，记日志提示该文件未获 preflight 保护
            com.tkisor.nekojs.NekoJS.LOGGER.warn(
                    "Binding preflight skipped for {}: source not parseable by ValParser ({}: {})",
                    file, e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        Map<String, String> remap = new HashMap<>();
        collectRemaps(ast, remap);

        // 局部变量类型流：const s = Item.of(x) → s 的后续成员按返回类型检查（两遍传播声明间依赖）
        Map<String, Set<Class<?>>> localTypes = new HashMap<>();
        collectLocalTypes(ast, schema, remap, localTypes);
        collectLocalTypes(ast, schema, remap, localTypes);

        Set<String> known = collectKnownIdentifiers(ast, source, schema, ScriptBindingSchema.inferType(file));

        Set<String> reported = new HashSet<>();
        checkBlock(ast, schema, remap, localTypes, known, file, source, reported);
    }

    // ==================== 已知标识符集合 ====================

    /**
     * 文件内可解析的标识符全集。AST 侧取扁平并集（所有声明名 + 所有函数/箭头参数——跨作用域
     * 并集只会漏报不会误报）；AST 覆盖不了的声明形态（import/class/catch/解构/for-of）用源级
     * 正则补收——ValParser 是有损解析器，这些语法在 AST 里会碎成无法辨识的片段。
     */
    private static Set<String> collectKnownIdentifiers(ValNode node, String source,
                                                       Map<String, ScriptBindingSchema.BindingMembers> schema,
                                                       ScriptType type) {
        Set<String> known = new HashSet<>(schema.keySet());
        collectDeclaredNames(node, known);
        collectSourceLevelNames(source, known);
        Set<String> globals = ScriptBindingSchema.knownGlobals(type);
        if (globals.isEmpty()) {
            // 运行时全局全集未登记：未定义标识符检查无法安全进行，跳过该项（成员检查照常）
            return null;
        }
        known.addAll(globals);
        return known;
    }

    private static void collectDeclaredNames(ValNode node, Set<String> out) {
        if (node == null) return;
        if (node instanceof ValNode.VarDecl decl) out.add(decl.name());
        else if (node instanceof ValNode.FuncDecl fd) {
            if (!fd.name().isEmpty()) out.add(fd.name());
            out.addAll(fd.params());
        } else if (node instanceof ValNode.ArrowFunc af) out.addAll(af.params());
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) collectDeclaredNames(s, out);
        if (node instanceof ValNode.CallExpr c) {
            collectDeclaredNames(c.callee(), out);
            for (ValNode a : c.args()) collectDeclaredNames(a, out);
        }
        if (node instanceof ValNode.MemberAccess m) collectDeclaredNames(m.object(), out);
        if (node instanceof ValNode.ComputedMemberAccess m) {
            collectDeclaredNames(m.object(), out);
            collectDeclaredNames(m.key(), out);
        }
    }

    /** AST 覆盖不了的声明：ESM import 名、class 名、catch 参数、解构声明、for-of/in 变量。 */
    private static void collectSourceLevelNames(String source, Set<String> out) {
        Matcher imports = Pattern.compile("\\bimport\\s+([^;='\"`]{1,200}?)\\s*from", Pattern.MULTILINE).matcher(source);
        while (imports.find()) {
            addIdentifiersIn(imports.group(1), out);
        }
        matchAll(source, Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)"), out);
        matchAll(source, Pattern.compile("\\bcatch\\s*\\(\\s*[A-Za-z_$][\\w$]*\\s*[,)]\\s*([A-Za-z_$][\\w$]*)?"), out);
        // catch (e) 单参数形态（上一条只覆盖多参可选绑定形态）
        matchAll(source, Pattern.compile("\\bcatch\\s*\\(\\s*([A-Za-z_$][\\w$]*)\\s*\\)"), out);
        Matcher destructure = Pattern.compile("\\b(?:const|let|var)\\s*[\\{\\[]([^\\}\\]]{1,300})[\\}\\]]").matcher(source);
        while (destructure.find()) {
            addIdentifiersIn(destructure.group(1), out);
        }
        matchAll(source, Pattern.compile("\\(\\s*(?:const|let|var)?\\s*([A-Za-z_$][\\w$]*)\\s+(?:of|in)\\b"), out);
    }

    private static void matchAll(String source, Pattern pattern, Set<String> out) {
        Matcher m = pattern.matcher(source);
        while (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) out.add(m.group(i));
            }
        }
    }

    /** 从 import 子句 / 解构模式片段中提取标识符（覆盖 {@code a as b}、{@code a: c}、默认值、rest）。 */
    private static void addIdentifiersIn(String clause, Set<String> out) {
        Matcher m = Pattern.compile("[A-Za-z_$][\\w$]*").matcher(clause);
        while (m.find()) out.add(m.group());
    }

    private static void collectRemaps(ValNode node, Map<String, String> remap) {
        if (node instanceof ValNode.VarDecl decl && decl.init() instanceof ValNode.Identifier init) {
            remap.put(decl.name(), init.name());
        }
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) collectRemaps(s, remap);
        if (node instanceof ValNode.CallExpr c) for (ValNode a : c.args()) collectRemaps(a, remap);
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) collectRemaps(s, remap);
    }

    /** 局部变量 → 其初始化表达式的类型（沿绑定/其它已解析局部传播；调用 collectLocalTypes 两遍覆盖声明间依赖）。 */
    private static void collectLocalTypes(ValNode node, Map<String, ScriptBindingSchema.BindingMembers> schema,
                                          Map<String, String> remap, Map<String, Set<Class<?>>> out) {
        if (node instanceof ValNode.VarDecl decl && decl.init() != null) {
            Set<Class<?>> types = resolveType(decl.init(), schema, remap, out);
            if (types != null && !types.isEmpty()) out.put(decl.name(), types);
        }
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

    // ==================== 检查主体 ====================

    private static void checkBlock(ValNode node,
                                   Map<String, ScriptBindingSchema.BindingMembers> schema,
                                   Map<String, String> remap,
                                   Map<String, Set<Class<?>>> localTypes,
                                   Set<String> known,
                                   Path file, String source, Set<String> reported) {
        if (node instanceof ValNode.MemberAccess access) {
            checkMemberAccess(access, schema, remap, localTypes, known, file, source, reported);
        }
        if (node instanceof ValNode.CallExpr call && call.callee() instanceof ValNode.Identifier id) {
            checkUnknownIdentifier(id, schema, remap, known, file, source, reported);
        }
        if (node instanceof ValNode.Block b) for (ValNode s : b.stmts()) checkBlock(s, schema, remap, localTypes, known, file, source, reported);
        if (node instanceof ValNode.CallExpr c) {
            for (ValNode a : c.args()) checkBlock(a, schema, remap, localTypes, known, file, source, reported);
            checkBlock(c.callee(), schema, remap, localTypes, known, file, source, reported);
        }
        if (node instanceof ValNode.ArrowFunc af) for (ValNode s : af.body()) checkBlock(s, schema, remap, localTypes, known, file, source, reported);
        if (node instanceof ValNode.FuncDecl fd) for (ValNode s : fd.body()) checkBlock(s, schema, remap, localTypes, known, file, source, reported);
        // 链式中间节点：外层 MemberAccess 只查自己这级，object() 侧的每一级也要被独立访问到
        if (node instanceof ValNode.MemberAccess m) {
            checkBlock(m.object(), schema, remap, localTypes, known, file, source, reported);
        }
        if (node instanceof ValNode.ComputedMemberAccess computed) {
            checkBlock(computed.object(), schema, remap, localTypes, known, file, source, reported);
            checkBlock(computed.key(), schema, remap, localTypes, known, file, source, reported);
        }
    }

    /**
     * 成员访问检查：对象侧能走类型流（schema 绑定的 {@code valueClasses}）时按反射成员查
     * （链式第二级及更深）；否则回退到既有的「直接绑定名 + 成员名表」检查；对象是未知标识符时
     * 报未定义标识符（globals 已登记时）。
     */
    private static void checkMemberAccess(ValNode.MemberAccess access,
                                          Map<String, ScriptBindingSchema.BindingMembers> schema,
                                          Map<String, String> remap,
                                          Map<String, Set<Class<?>>> localTypes,
                                          Set<String> known,
                                          Path file, String source, Set<String> reported) {
        String member = access.member();
        if (member == null || member.isEmpty()) return;

        Set<Class<?>> objectClasses = resolveType(access.object(), schema, remap, localTypes);
        if (objectClasses != null && !objectClasses.isEmpty()) {
            Set<String> knownMembers = new LinkedHashSet<>();
            List<Class<?>> next = new ArrayList<>();
            boolean anyKnown = false;
            for (Class<?> cls : objectClasses) {
                JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(cls);
                knownMembers.addAll(exposed.methods().keySet());
                knownMembers.addAll(exposed.propertyGetters().keySet());
                knownMembers.addAll(exposed.fields().keySet());
                if (exposed.hasMember(member)) {
                    anyKnown = true;
                    for (Type type : exposed.propertyTypes(member)) {
                        next.addAll(JavaMemberIndex.typeClasses(type));
                    }
                }
            }
            if (!anyKnown) {
                report(file, source, reported, access.start(), member.length(),
                        "Type chain: no member '" + member + "' on " + classNames(objectClasses)
                                + suggestion(knownMembers, member), "chain-member " + member);
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
                                    + suggestion(bm.memberNames(), member), "binding-member " + resolved + "." + member);
                }
                return;
            }
            checkUnknownIdentifier(id, schema, remap, known, file, source, reported);
        }
    }

    /** 链式类型流：标识符 → schema 绑定的 valueClasses；属性取值/调用的返回类型沿链传播。 */
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

    /** 未定义标识符：仅在成员访问对象 / 调用目标位置报（裸标识符因 typeof 泄漏不报）。 */
    private static void checkUnknownIdentifier(ValNode.Identifier id,
                                               Map<String, ScriptBindingSchema.BindingMembers> schema,
                                               Map<String, String> remap,
                                               Set<String> known,
                                               Path file, String source, Set<String> reported) {
        if (known == null || id.name().isEmpty() || JS_KEYWORDS.contains(id.name())) return;
        String resolved = remap.getOrDefault(id.name(), id.name());
        if (schema.containsKey(resolved) || known.contains(resolved) || known.contains(id.name())) return;
        String suggest = JavaMemberIndex.suggestMember(known, resolved);
        report(file, source, reported, id.start(), id.name().length(),
                "Unknown identifier '" + id.name() + "'."
                        + (suggest != null ? " Did you mean '" + suggest + "'?" : ""),
                "unknown-identifier " + id.name());
    }

    // ==================== 报告 ====================

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
                               int offset, int length, String msg, String dedupKey) {
        if (!reported.add(dedupKey)) return;
        try {
            int[] lc = lc(source, offset);
            ScriptErrorReporter.recordCallbackError(
                    ScriptBindingSchema.inferType(file),
                    "binding-preflight " + dedupKey,
                    new NekoEsmLinkException(new NekoEsmDiagnostic(
                            file, new NekoEsmSpan(offset, offset + length), lc[0], lc[1], msg)));
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
