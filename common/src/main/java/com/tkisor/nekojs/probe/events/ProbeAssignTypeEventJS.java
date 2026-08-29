package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code probe.assign_type} 事件：全局类型重定向。脚本调用 {@link #assign(String, Object)} 登记
 * 「Java 全限定名 → 自定义类型」映射；coordinator 在 IR 构建后用 {@link #applyTo} 把映射应用到每个
 * TypeDecl 的类型槽（凡 SYMBOL 引用了被 assign 的 FQN，替换其 {@code ref}、置 {@code overridden}，
 * 并标记所属 TypeDecl 为 {@code mutated}）。
 *
 * <p>效果：TS 会重渲染被触及的类（assigned 槽走 renderRef → 新类型）；Python 渲染所有类时直接见新类型。
 * 语义上 {@code assign_type} 改的是「反射产出的类型」；若 {@code modify_type} 之后再设类型，后者显式覆盖优先。
 *
 * <p>需要 IR：assign_type 监听器会使 coordinator 构建 IR（纳入 {@code needIr}）。
 */
public final class ProbeAssignTypeEventJS {
    private final Map<String, ApiTypeRef> assignments = new LinkedHashMap<>();

    /** 登记：把 Java 全限定名 {@code javaFqn} 处处重定向为 {@code typeDesc}（字符串/ApiTypeRef）。 */
    public void assign(String javaFqn, Object typeDesc) {
        if (javaFqn == null || javaFqn.isBlank()) return;
        assignments.put(javaFqn, ProbeModifyTypeEventJS.resolveType(typeDesc));
    }

    public boolean has(String javaFqn) {
        return assignments.containsKey(javaFqn);
    }

    public Map<String, ApiTypeRef> assignments() {
        return assignments;
    }

    /**
     * 把映射应用到一个 TypeDecl 的所有类型槽。返回替换次数（0 表示本类无受影响槽）。
     * 凡被替换的槽，其所属 TypeDecl 标记 {@code mutated}（让 TS 重渲染它）。
     */
    public static int applyTo(TypeDecl d, Map<String, ApiTypeRef> map) {
        if (map == null || map.isEmpty() || d == null) return 0;
        int count = 0;
        if (d.superType != null) count += applySlot(d, d.superType, map);
        for (TypeSlot s : d.interfaces) count += applySlot(d, s, map);
        for (MethodDecl c : d.constructors) count += applyMethod(d, c, map);
        for (MethodDecl m : d.methods) count += applyMethod(d, m, map);
        for (FieldDecl f : d.fields) if (f.type != null) count += applySlot(d, f.type, map);
        for (TypeDecl.TypeParam tp : d.typeParams) if (tp.bound != null) count += applySlot(d, tp.bound, map);
        return count;
    }

    private static int applyMethod(TypeDecl d, MethodDecl m, Map<String, ApiTypeRef> map) {
        int c = 0;
        if (m.returnType != null) c += applySlot(d, m.returnType, map);
        if (m.setterParamType != null) c += applySlot(d, m.setterParamType, map);
        for (MethodDecl.MethodParam p : m.params) if (p.type != null) c += applySlot(d, p.type, map);
        return c;
    }

    private static int applySlot(TypeDecl d, TypeSlot slot, Map<String, ApiTypeRef> map) {
        if (slot.ref == null || slot.ref.kind() != ApiTypeRef.Kind.SYMBOL) return 0;
        String fqn = extractFqn(slot.ref.name());
        ApiTypeRef replacement = map.get(fqn);
        if (replacement == null) return 0;
        slot.ref = replacement;
        slot.overridden = true;
        d.mutated = true;
        return 1;
    }

    private static String extractFqn(String symbolName) {
        int colon = symbolName.indexOf(':');
        return colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
    }
}
