package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.event.ScriptMemberAccessScanner;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 加载时全局绑定成员校验器：在模块编译阶段扫描脚本源码，对全局绑定标识符
 * （{@code Utils} / {@code Platform} / {@code Items} 等）的成员访问，对照 Java 反射
 * （{@link JavaMemberIndex#allMembersOf(Class)}）校验该成员是否存在；不存在则通过
 * {@link ScriptErrorReporter} 上报到游戏内错误面板（带拼写建议）。
 *
 * <p>全局绑定是 Graal host object（{@code HostAccess.ALL}），访问不存在成员默认返回
 * {@code undefined}，运行时无法拦截，故走加载时静态扫描，让 {@code Utils.badMethod()} 这类
 * 错误在脚本加载时（事件触发前）就暴露。事件对象同样走原生 host access，由
 * {@code EventCallbackSourceValidator} 在回调注册时静态校验首参成员。
 *
 * <p>只覆盖字面量成员访问（{@code Utils.foo}、{@code Utils["foo"]}）；不覆盖变量中转
 * （{@code const u = Utils; u.foo}）与链式深层推导（{@code event.recipes.ns.method}），
 * 后者依赖完整类型推导，作为后续增强。
 *
 * <p>校验只报告错误，不阻止编译 / 执行（不抛异常），与 {@code EventCallbackSourceValidator} 一致。
 */
public final class GlobalBindingMemberValidator {

    private GlobalBindingMemberValidator() {}

    /**
     * 扫描单个模块源码，对全局绑定的非法成员访问上报错误。
     *
     * @param file   模块文件路径（用于推断 {@link com.tkisor.nekojs.script.ScriptType} 与错误定位）
     * @param source 模块源码
     */
    public static void validate(Path file, String source) {
        if (file == null || source == null || source.isEmpty()) {
            return;
        }
        Map<String, Class<?>> schema = ScriptBindingSchema.schemaForPath(file);
        if (schema.isEmpty()) {
            return;
        }

        Set<String> names = schema.keySet();
        Set<String> reported = new HashSet<>();
        Set<String> locals = collectLocalDeclarations(source);
        ScriptMemberAccessScanner.scan(source, names, (identifier, member, offset) -> {
            if (locals.contains(identifier)) {
                return; // 局部声明（enum/class/const 等）遮蔽同名全局 binding，不应按 binding 校验
            }
            if (member == null || member.isEmpty()) {
                return;
            }
            Class<?> type = schema.get(identifier);
            if (type == null) {
                return;
            }
            Set<String> all = JavaMemberIndex.allMembersOf(type);
            if (all.contains(member)) {
                return;
            }
            if (!reported.add(identifier + "." + member)) {
                return;
            }
            report(file, source, offset, identifier, member, type, all);
        });
    }

    private static void report(Path file, String source, int offset, String identifier, String member, Class<?> type, Set<String> all) {
        try {
            String suggest = JavaMemberIndex.suggestMember(all, member);
            String message = "Binding '" + identifier + "' (" + type.getSimpleName() + ") has no member '" + member + "'."
                    + (suggest != null ? " Did you mean '" + suggest + "'?" : "");
            int[] lc = lineColumn(source, offset);
            NekoEsmDiagnostic diagnostic = new NekoEsmDiagnostic(
                    file,
                    new NekoEsmSpan(offset, offset + member.length()),
                    lc[0],
                    lc[1],
                    message);
            String kind = "binding-preflight name=" + identifier;
            ScriptErrorReporter.recordCallbackError(ScriptBindingSchema.inferType(file), kind, new NekoEsmLinkException(diagnostic));
        } catch (Throwable ignored) {
            // 校验绝不应中断编译
        }
    }

    private static int[] lineColumn(String source, int offset) {
        int line = 1;
        int column = 1;
        int end = Math.min(Math.max(offset, 0), source.length());
        for (int i = 0; i < end; i++) {
            if (source.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new int[] {line, column};
    }

    /**
     * 收集脚本内局部声明的标识符名（{@code enum/class/namespace/function/const/let/var} 的名字），
     * 用于在 binding 校验时跳过——局部声明遮蔽同名全局 binding（如局部 {@code enum Direction}
     * 不应被当作 Java {@code Direction} 类校验成员）。纯词法扫描，跳过字符串/模板/注释。
     */
    private static Set<String> collectLocalDeclarations(String source) {
        Set<String> decls = new HashSet<>();
        int n = source.length();
        int i = 0;
        while (i < n) {
            char c = source.charAt(i);
            if (c == '\'' || c == '"') { i = skipLitStr(source, i, c, n); continue; }
            if (c == '`') { i = skipLitTpl(source, i, n); continue; }
            if (c == '/' && i + 1 < n) {
                if (source.charAt(i + 1) == '/') { i = skipLitLn(source, i + 2, n); continue; }
                if (source.charAt(i + 1) == '*') { i = skipLitBlk(source, i + 2, n); continue; }
            }
            if (isLitIdentStart(c)) {
                int s = i;
                while (i < n && isLitIdentPart(source.charAt(i))) i++;
                String kw = source.substring(s, i);
                if (isDeclKeyword(kw)) {
                    int j = i;
                    while (j < n && Character.isWhitespace(source.charAt(j))) j++;
                    if (j < n && isLitIdentStart(source.charAt(j))) {
                        int ns = j;
                        while (j < n && isLitIdentPart(source.charAt(j))) j++;
                        decls.add(source.substring(ns, j));
                        i = j;
                        continue;
                    }
                }
            }
            i++;
        }
        return decls;
    }

    private static boolean isDeclKeyword(String w) {
        return w.equals("enum") || w.equals("class") || w.equals("namespace") || w.equals("module")
                || w.equals("function") || w.equals("const") || w.equals("let") || w.equals("var");
    }

    private static int skipLitStr(String s, int i, char q, int n) {
        i++;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') { i += 2; continue; }
            if (c == q) return i + 1;
            i++;
        }
        return n;
    }

    private static int skipLitTpl(String s, int i, int n) {
        i++;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') { i += 2; continue; }
            if (c == '`') return i + 1;
            i++;
        }
        return n;
    }

    private static int skipLitLn(String s, int i, int n) {
        int e = s.indexOf('\n', i);
        return e < 0 ? n : e + 1;
    }

    private static int skipLitBlk(String s, int i, int n) {
        int e = s.indexOf("*/", i);
        return e < 0 ? n : e + 2;
    }

    private static boolean isLitIdentStart(char c) {
        return c == '_' || c == '$' || Character.isLetter(c);
    }

    private static boolean isLitIdentPart(char c) {
        return c == '_' || c == '$' || Character.isLetterOrDigit(c);
    }
}
