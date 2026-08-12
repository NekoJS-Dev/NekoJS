package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code probe.modify_type} 事件中针对单个 {@link TypeDecl} 的链式编辑器。由
 * {@link ProbeModifyTypeEventJS#forClass(String)} 取得；任一编辑操作都会把所属 TypeDecl 标记为
 * {@code mutated}，probe backend 随后只对这些类重新渲染并覆盖声明缓存。
 *
 * <p><b>方法名定位</b>：按名操作作用于该类中**所有同名**方法/构造器（含重载）——这契合「整类改名」语义。
 * 找不到目标成员时为**静默 no-op**（DEBUG 日志），可用 {@link #hasMethod}/{@link #hasField} 预先判空。
 *
 * <p><b>类型入参</b>：接受字符串或 {@link com.tkisor.nekojs.api.surface.ApiTypeRef}。含 {@code .} 的字符串
 * 当 Java 全限定名（SYMBOL），否则当原始类型名（{@code "string"}/{@code "number"}/{@code "boolean"} …）。
 * 复杂类型用 {@link ProbeModifyTypeEventJS#array}/{@link ProbeModifyTypeEventJS#union} 构造后传入。
 */
public final class ClassEditor {
    private final TypeDecl decl;

    ClassEditor(TypeDecl decl) {
        this.decl = decl;
    }

    private void touched() {
        decl.mutated = true;
    }

    // ==================== 类级 ====================

    /** 隐藏整个类（渲染为空声明）。 */
    public ClassEditor hide() {
        touched();
        decl.hidden = true;
        return this;
    }

    /** 覆盖类文档。 */
    public ClassEditor setDoc(String doc) {
        touched();
        decl.docs.clear();
        if (doc != null) decl.docs.add(doc);
        return this;
    }

    /** 改父类类型。 */
    public ClassEditor changeSuper(Object type) {
        touched();
        decl.superType = ProbeModifyTypeEventJS.override(decl.superType, type);
        return this;
    }

    // ==================== 方法级 ====================

    public boolean hasMethod(String name) {
        return !matchingMethods(name).isEmpty();
    }

    /** 重命名方法（影响所有同名重载）。 */
    public ClassEditor renameMethod(String name, String newName) {
        return editMethods(name, m -> m.renameTo = newName);
    }

    /** 隐藏方法（所有同名重载）。 */
    public ClassEditor hideMethod(String name) {
        return editMethods(name, m -> m.hidden = true);
    }

    /** 覆盖方法文档（所有同名重载）。 */
    public ClassEditor setMethodDoc(String name, String doc) {
        return editMethods(name, m -> {
            m.docs.clear();
            if (doc != null) m.docs.add(doc);
        });
    }

    /** 改方法返回类型（所有同名重载）。 */
    public ClassEditor changeReturnType(String name, Object type) {
        return editMethods(name, m -> m.returnType = ProbeModifyTypeEventJS.override(m.returnType, type));
    }

    /** 按下标改方法参数类型（作用于每个有该下标参数的同名重载）。 */
    public ClassEditor changeParamType(String name, int index, Object type) {
        return editMethods(name, m -> {
            if (index >= 0 && index < m.params.size()) {
                var p = m.params.get(index);
                p.type = ProbeModifyTypeEventJS.override(p.type, type);
            }
        });
    }

    /** 按参数名改方法参数类型（所有同名重载中名字匹配的参数）。 */
    public ClassEditor changeParamType(String name, String paramName, Object type) {
        return editMethods(name, m -> {
            for (var p : m.params) {
                if (paramName.equals(p.name)) {
                    p.type = ProbeModifyTypeEventJS.override(p.type, type);
                }
            }
        });
    }

    /** 按参数名重命名方法参数。 */
    public ClassEditor renameParam(String name, String paramName, String newName) {
        return editMethods(name, m -> {
            for (var p : m.params) if (paramName.equals(p.name)) p.name = newName;
        });
    }

    /** 按下标移除方法参数。 */
    public ClassEditor removeParam(String name, int index) {
        return editMethods(name, m -> {
            if (index >= 0 && index < m.params.size()) m.params.remove(index);
        });
    }

    /** 把指定参数标记为 TS 可选（渲染为 {@code name?: type}）。 */
    public ClassEditor markOptional(String name, String paramName) {
        return editMethods(name, m -> {
            for (var p : m.params) if (paramName.equals(p.name)) p.optional = true;
        });
    }

    /** 追加一个参数到方法末尾（所有同名重载）。 */
    public ClassEditor addParam(String name, String paramName, Object type) {
        return editMethods(name, m -> {
            TypeSlot slot = ProbeModifyTypeEventJS.override(null, type);
            m.params.add(new MethodDecl.MethodParam(paramName, slot, false));
        });
    }

    // ==================== 字段级 ====================

    public boolean hasField(String name) {
        for (FieldDecl f : decl.fields) if (f.name.equals(name)) return true;
        return false;
    }

    public ClassEditor renameField(String name, String newName) {
        return editField(name, f -> f.renameTo = newName);
    }

    public ClassEditor hideField(String name) {
        return editField(name, f -> f.hidden = true);
    }

    public ClassEditor changeFieldType(String name, Object type) {
        return editField(name, f -> f.type = ProbeModifyTypeEventJS.override(f.type, type));
    }

    public ClassEditor setFieldDoc(String name, String doc) {
        return editField(name, f -> {
            f.docs.clear();
            if (doc != null) f.docs.add(doc);
        });
    }

    // ==================== 内部 ====================

    private List<MethodDecl> matchingMethods(String name) {
        List<MethodDecl> out = new ArrayList<>();
        for (MethodDecl m : decl.methods) if (m.name.equals(name)) out.add(m);
        for (MethodDecl c : decl.constructors) if (c.name.equals(name)) out.add(c);
        return out;
    }

    private ClassEditor editMethods(String name, Consumer<MethodDecl> op) {
        List<MethodDecl> matches = matchingMethods(name);
        if (matches.isEmpty()) {
            NekoJS.LOGGER.debug("probe.modify_type: method '{}' not found in {}", name, decl.fqn);
            return this;
        }
        touched();
        for (MethodDecl m : matches) op.accept(m);
        return this;
    }

    private ClassEditor editField(String name, Consumer<FieldDecl> op) {
        FieldDecl found = null;
        for (FieldDecl f : decl.fields) if (f.name.equals(name)) { found = f; break; }
        if (found == null) {
            NekoJS.LOGGER.debug("probe.modify_type: field '{}' not found in {}", name, decl.fqn);
            return this;
        }
        touched();
        op.accept(found);
        return this;
    }
}
