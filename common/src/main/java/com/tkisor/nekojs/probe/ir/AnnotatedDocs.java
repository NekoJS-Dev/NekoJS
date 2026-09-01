package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.DeprecatedNekojs;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Overload;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads {@link Doc}/{@link Param}/{@link Return}/{@link DeprecatedNekojs}
 * annotations into IR doc lines, and {@link Overload} into IR overload nodes.
 *
 * <p>Method/constructor docs compose as: {@code @Doc} paragraphs first, then
 * {@code @param} lines (annotation declaration order), then a single
 * {@code @returns} line, then the {@code @deprecated} line — matching JSDoc
 * conventions so editors render them in signature help. Empty results leave
 * the IR {@code docs} list empty, which the renderers skip entirely
 * (byte-identical output for unannotated classes).
 */
final class AnnotatedDocs {

    private AnnotatedDocs() {}

    /** Type-level {@code @Doc} paragraphs + {@code @deprecated} line. */
    static List<String> typeDocs(Class<?> cls) {
        List<String> out = new ArrayList<>();
        for (Doc doc : cls.getAnnotationsByType(Doc.class)) {
            out.add(doc.value());
        }
        addDeprecation(out, cls.getAnnotation(DeprecatedNekojs.class));
        return out;
    }

    /** Field / enum-constant {@code @Doc} paragraphs + {@code @deprecated} line. */
    static List<String> fieldDocs(Field field) {
        List<String> out = new ArrayList<>();
        for (Doc doc : field.getAnnotationsByType(Doc.class)) {
            out.add(doc.value());
        }
        addDeprecation(out, field.getAnnotation(DeprecatedNekojs.class));
        return out;
    }

    /** Method / constructor docs: paragraphs + {@code @param} lines + {@code @returns} + {@code @deprecated}. */
    static List<String> executableDocs(Executable exec) {
        List<String> out = new ArrayList<>();
        for (Doc doc : exec.getAnnotationsByType(Doc.class)) {
            out.add(doc.value());
        }
        for (Param param : exec.getAnnotationsByType(Param.class)) {
            out.add("@param " + param.name() + " " + param.value());
        }
        Return returns = exec.getAnnotation(Return.class);
        if (returns != null) {
            out.add("@returns " + returns.value());
        }
        addDeprecation(out, exec.getAnnotation(DeprecatedNekojs.class));
        return out;
    }

    /** {@code @Overload} 注解 → IR 重载节点（声明顺序）。 */
    static List<MethodDecl.Overload> overloads(Executable exec) {
        List<MethodDecl.Overload> out = new ArrayList<>();
        for (Overload overload : exec.getAnnotationsByType(Overload.class)) {
            List<String> docs = overload.doc().isEmpty() ? List.of() : List.of(overload.doc());
            out.add(new MethodDecl.Overload(Arrays.asList(overload.value()), overload.returns(), docs));
        }
        return out;
    }

    private static void addDeprecation(List<String> out, DeprecatedNekojs dep) {
        if (dep == null) return;
        String reason = dep.value().trim();
        String use = dep.replacedBy().trim().isEmpty() ? "" : "Use " + dep.replacedBy().trim() + " instead.";
        if (reason.isEmpty()) {
            out.add(use.isEmpty() ? "@deprecated" : "@deprecated " + use);
        } else {
            out.add(use.isEmpty() ? "@deprecated " + reason : "@deprecated " + reason + " " + use);
        }
    }
}
