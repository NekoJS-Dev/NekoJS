package com.tkisor.nekojs.probe.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * 语言中性的类型声明 IR 节点（mutable）。由 {@code TypeReflector} 从 Java {@code Class<?>} 反射产出，
 * 供 {@code TypeScriptClassRenderer}（TS）/ Python renderer（Phase 3）渲染，并由 {@code probe.modify_type}
 * 事件就地编辑。
 *
 * <p>字段直接暴露（包内 + 同 IR 子系统访问）；命名（如 TS 的 {@code $Parent$Child}）由各 renderer
 * 从 {@link #sourceClass} 自行计算，本 IR 不固化任何语言特定的命名。
 */
public final class TypeDecl {
    public enum Kind { CLASS, INTERFACE, ENUM }

    public Kind kind;
    public final Class<?> sourceClass;     // 反射源类；纯合成声明可为 null
    public final String fqn;               // 全限定名（sourceClass.getName() 或合成名）
    public final List<TypeParam> typeParams = new ArrayList<>();   // 类级泛型 <T extends Bound>
    public TypeSlot superType;             // 父类；null = Object/无
    public final List<TypeSlot> interfaces = new ArrayList<>();
    public final List<MethodDecl> constructors = new ArrayList<>();
    public final List<MethodDecl> methods = new ArrayList<>();     // 含 getter/setter（按标志区分）
    public final List<FieldDecl> fields = new ArrayList<>();
    public final List<String> docs = new ArrayList<>();
    public boolean hidden;
    /**
     * 是否被 {@code probe.modify_type} 事件触及过。由 {@code ClassEditor} 的任意编辑操作置 true；
     * probe backend 据此决定哪些类需要经 renderer 重新渲染并覆盖声明缓存。未触及 → 走旧路径（零回归）。
     */
    public boolean mutated;

    public TypeDecl(Kind kind, Class<?> sourceClass, String fqn) {
        this.kind = kind;
        this.sourceClass = sourceClass;
        this.fqn = fqn;
    }

    /** 类级泛型参数（名字 + 可选上界 TypeSlot）。 */
    public static final class TypeParam {
        public final String name;
        public TypeSlot bound;       // null = 无上界

        public TypeParam(String name, TypeSlot bound) {
            this.name = name;
            this.bound = bound;
        }
    }
}
