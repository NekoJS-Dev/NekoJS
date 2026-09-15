package com.tkisor.nekojs.api.catalog;

import java.util.List;

/**
 * typed Builder 面的结构化目录条目（ticket 15，AC7/AC9）：
 * 由版本树对 builder 类的契约反射（{@code RegistryBuilderContract}）派生，
 * 作为<b>同一契约输入</b>同时驱动 TS declaration（probe TS backend）与
 * Python declaration（probe Python backend）——两个后端只做渲染，不再各自维护成员表。
 *
 * <p>与 {@link ManualDeclarationCatalogEntry}（手写字符串，legacy 迁移观察面）不同，
 * 本条目是结构化事实：成员名、种类（可写属性/只读属性/方法）与两侧类型标注来自
 * 同一份反射结果。common 不引入 Minecraft/loader/Graal 类型（ADR-0007 L1），
 * 反射输入在版本树完成、此处只承载数据。
 *
 * @param builderName    builder 简名（如 {@code ItemBuilder}，两侧声明的类型名）
 * @param registryKey    注册表完整键（如 {@code minecraft:item}）
 * @param typeName       注册表类型名（如 {@code basic}）
 * @param sugarName      脚本糖方法名（如 {@code item}；default 类型才有）
 * @param members        契约成员（字典序）
 * @param description    描述（probe 文档用）
 */
public record RegistryBuilderSurfaceEntry(
        String builderName,
        String registryKey,
        String typeName,
        String sugarName,
        List<Member> members,
        String description) {

    public RegistryBuilderSurfaceEntry {
        members = List.copyOf(members == null ? List.of() : members);
    }

    /** 成员种类：可写属性（setter 双形态）/ 只读属性 / 方法（含显式 setter）。 */
    public enum MemberKind { WRITABLE_PROPERTY, READ_ONLY_PROPERTY, METHOD }

    /**
     * 单个成员。
     *
     * @param name        成员名（属性名或方法名）
     * @param kind        种类
     * @param tsType      TS 形状：属性为类型标注；方法为完整签名行（如 {@code food(cb: (f: any) => void): void}）
     * @param pyType      Python 形状：属性为类型标注；方法为完整签名行——与 TS 同一反射输入派生
     */
    public record Member(String name, MemberKind kind, String tsType, String pyType) {}
}
