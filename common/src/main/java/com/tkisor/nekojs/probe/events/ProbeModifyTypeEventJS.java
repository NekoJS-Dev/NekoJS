package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code probe.modify_type} 事件对象。持有本次 probe 收集到的全部 {@link TypeDecl} IR（fqn → decl），
 * 供脚本经 {@link #forClass(String)} 取得 {@link ClassEditor} 后就地编辑。
 *
 * <p>本类同时提供类型构造辅助（{@link #type}/{@link #array}/{@link #union}）——编辑方法接受字符串
 * 或 {@link ApiTypeRef}：含 {@code .} 的字符串当作 Java 全限定名（SYMBOL），否则当作原始类型名
 * （{@code "string"}、{@code "number"}、{@code "boolean"}、{@code "int"} …）。
 */
public final class ProbeModifyTypeEventJS {
    private final Map<String, TypeDecl> decls;

    public ProbeModifyTypeEventJS(Map<String, TypeDecl> decls) {
        this.decls = decls;
    }

    /** 本次 probe 是否收集到了指定类（即可编辑）。 */
    public boolean hasClass(String fqn) {
        return decls.containsKey(fqn);
    }

    /**
     * 获取指定类的编辑器；类不在本次 probe 收集范围时返回 {@code null}（脚本应判空）。
     */
    public ClassEditor forClass(String fqn) {
        TypeDecl d = decls.get(fqn);
        return d == null ? null : new ClassEditor(d);
    }

    // ------------------------------------------------------------------
    //  类型构造辅助
    // ------------------------------------------------------------------

    /** 把类型描述符（字符串/ApiTypeRef）解析为 {@link ApiTypeRef}。 */
    public ApiTypeRef type(Object desc) {
        return resolveType(desc);
    }

    /** 数组类型。 */
    public ApiTypeRef array(Object element) {
        return ApiTypeRef.array(resolveType(element));
    }

    /** 联合类型（≥2 个成员）。 */
    public ApiTypeRef union(Object a, Object b, Object... rest) {
        List<ApiTypeRef> members = new ArrayList<>();
        members.add(resolveType(a));
        members.add(resolveType(b));
        for (Object r : rest) members.add(resolveType(r));
        return ApiTypeRef.union(members);
    }

    /** 字符串/ApiTypeRef → ApiTypeRef（编辑方法共用）。 */
    static ApiTypeRef resolveType(Object desc) {
        if (desc instanceof ApiTypeRef ref) return ref;
        if (desc instanceof String s) {
            if (s.isBlank()) throw new IllegalArgumentException("type must not be blank");
            if (s.indexOf('.') >= 0) return ApiTypeRef.symbol(new ApiSymbolId("java", s));
            return ApiTypeRef.primitive(s);
        }
        throw new IllegalArgumentException("type must be a String or ApiTypeRef, got: "
                + (desc == null ? "null" : desc.getClass().getName()));
    }

    /** 用新类型覆盖一个类型槽（保留原 sourceType 作存档，置 overridden=true 强制走 ref 渲染）。 */
    static TypeSlot override(TypeSlot original, Object newType) {
        ApiTypeRef ref = resolveType(newType);
        TypeSlot s = new TypeSlot(original == null ? null : original.sourceType, ref);
        s.overridden = true;
        return s;
    }

    // ------------------------------------------------------------------
    //  import 收集（供 backend 在覆盖声明缓存时合并 import）
    // ------------------------------------------------------------------

    /**
     * 收集被编辑（overridden/合成）槽位引用的 SYMBOL 全限定名，供 {@code IndexFileGenerator} 合并
     * import。同包类型（会是自导入）已剔除；未被触及的成员由反射侧 import 收集覆盖，无需在此处理。
     */
    public static Set<String> collectEditedSymbolFqns(TypeDecl d, String classPackage) {
        Set<String> out = new LinkedHashSet<>();
        if (d.superType != null && d.superType.overridden) collectSyms(d.superType.ref, out, classPackage);
        for (TypeSlot s : d.interfaces) {
            if (s.overridden) collectSyms(s.ref, out, classPackage);
        }
        for (MethodDecl c : d.constructors) collectEditedParams(c, out, classPackage);
        for (MethodDecl m : d.methods) {
            if (m.returnType != null && m.returnType.overridden) collectSyms(m.returnType.ref, out, classPackage);
            if (m.setterParamType != null && m.setterParamType.overridden) collectSyms(m.setterParamType.ref, out, classPackage);
            collectEditedParams(m, out, classPackage);
        }
        for (FieldDecl f : d.fields) {
            if (f.type != null && f.type.overridden) collectSyms(f.type.ref, out, classPackage);
        }
        return out;
    }

    private static void collectEditedParams(MethodDecl m, Set<String> out, String pkg) {
        for (MethodDecl.MethodParam p : m.params) {
            if (p.type != null && p.type.overridden) collectSyms(p.type.ref, out, pkg);
        }
    }

    private static void collectSyms(ApiTypeRef ref, Set<String> out, String classPackage) {
        if (ref == null) return;
        switch (ref.kind()) {
            case SYMBOL -> {
                String name = ref.name();                 // 形如 "java:net.minecraft.Foo"
                int colon = name.indexOf(':');
                String fqn = colon >= 0 ? name.substring(colon + 1) : name;
                int dot = fqn.lastIndexOf('.');
                String pkg = dot >= 0 ? fqn.substring(0, dot) : "";
                if (!pkg.equals(classPackage)) out.add(fqn);   // 剔除同包自导入
            }
            case ARRAY -> collectSyms(ref.arguments().get(0), out, classPackage);
            case UNION -> {
                for (ApiTypeRef a : ref.arguments()) collectSyms(a, out, classPackage);
            }
            default -> { /* PRIMITIVE/VOID/TYPE_VARIABLE/CALLBACK 不产生 SYMBOL import */ }
        }
    }
}
