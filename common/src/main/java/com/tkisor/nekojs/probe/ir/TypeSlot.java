package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.Type;

/**
 * 类型槽 IR：同时持有原始 Java {@link Type} 与语言中性的 {@link ApiTypeRef}。
 *
 * <p>双轨设计的关键意义：
 * <ul>
 *   <li><b>TS 默认渲染</b>（未编辑）：用 {@code TypeConverter.toTypeScript(sourceType)}，
 *       与旧 {@code ClassDeclGenerator} 逐字一致 —— TS 产物零回归风险。</li>
 *   <li><b>Python 渲染 / modify_type 编辑后</b>：用 {@link #ref}（{@link ApiTypeRef}），
 *       语言中性、可被事件改写。</li>
 * </ul>
 * 当 {@link #overridden} 为 true（modify_type 改写过本槽），renderer 渲染 {@link #ref}；否则渲染 {@link #sourceType}。
 * 合成成员（modify_type addMethod 等）的 {@code sourceType} 为 null，必走 {@code ref}。
 */
public final class TypeSlot {
    public final Type sourceType;   // 原始 Java 类型；合成时为 null
    public ApiTypeRef ref;          // 语言中性类型（始终非 null）
    public boolean overridden;      // modify_type 改写过 ref

    public TypeSlot(Type sourceType, ApiTypeRef ref) {
        this.sourceType = sourceType;
        this.ref = ref;
    }

    public static TypeSlot of(Type sourceType, ApiTypeRef ref) {
        return new TypeSlot(sourceType, ref);
    }
}
