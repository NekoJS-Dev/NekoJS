package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@link Doc}/{@link Param}/{@link Return} annotations into IR doc lines.
 *
 * <p>Method/constructor docs compose as: {@code @Doc} paragraphs first, then
 * {@code @param} lines (annotation declaration order), then a single
 * {@code @returns} line — matching JSDoc conventions so editors render them in
 * signature help. Empty results leave the IR {@code docs} list empty, which the
 * renderers skip entirely (byte-identical output for unannotated classes).
 */
final class AnnotatedDocs {

    private AnnotatedDocs() {}

    /** Type-level {@code @Doc} paragraphs. */
    static List<String> typeDocs(Class<?> cls) {
        List<String> out = new ArrayList<>();
        for (Doc doc : cls.getAnnotationsByType(Doc.class)) {
            out.add(doc.value());
        }
        return out;
    }

    /** Field / enum-constant {@code @Doc} paragraphs. */
    static List<String> fieldDocs(Field field) {
        List<String> out = new ArrayList<>();
        for (Doc doc : field.getAnnotationsByType(Doc.class)) {
            out.add(doc.value());
        }
        return out;
    }

    /** Method / constructor docs: paragraphs + {@code @param} lines + {@code @returns} line. */
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
        return out;
    }
}
