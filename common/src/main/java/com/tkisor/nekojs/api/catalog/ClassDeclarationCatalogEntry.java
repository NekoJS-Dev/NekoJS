package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptTypePredicate;

import java.util.List;
import java.util.Set;

/**
 * 类声明替换：插件用它把某个 Java 类的 probe 生成结果整体换成手写声明。
 *
 * <p>反射表达不了全部声明——泛型被擦除、只在运行期约定里存在的类型（如链式 builder
 * 累积的形状）编译期不存在。生成的 {@code .d.ts} 因此丢信息，脚本侧拿到 {@code any}。
 * 而已生成的声明无法从 TypeScript 侧覆盖：类不能重复声明，interface 增强又会输给原有重载。
 * 本条目让插件在<b>生成端</b>替换。
 *
 * <h2>与既有 API 的分工</h2>
 *
 * <ul>
 *   <li>{@link TypeDocCatalogEntry}：绑定级的 typeOverride，只改「某个名字指向什么类型」</li>
 *   <li>{@link ManualDeclarationCatalogEntry}：往 {@code @manual/index.d.ts} <b>追加</b>文本，
 *       碰不到 {@code @package/.../index.d.ts} 里的类声明</li>
 *   <li>本条目：<b>替换</b>指定类的声明（含其所在模块的 import 合并）</li>
 * </ul>
 *
 * <h2>用法</h2>
 *
 * <pre>{@code
 * registry.registerClassDeclaration(ClassDeclarationCatalogEntry.of(
 *     "com.example.RpcBuilder",
 *     """
 *     export class $RpcBuilder<T extends string = never> {
 *         schema<const S extends Record<string, string>>(sch: S): $RpcBuilder<keyof S & string>;
 *         fn(impl: (arg: { [K in T]: string }) => void): void;
 *     }
 *     """,
 *     Set.of("com.example.Entry")));
 * }</pre>
 *
 * @param targetFqn   被替换的类全限定名（须是本次会被生成的类，否则静默跳过）
 * @param scriptType  生效的脚本类型（与 {@link ManualDeclarationCatalogEntry} 同义）
 * @param declaration 手写声明。写<b>裸</b>文本即可（顶格、不含 {@code declare module} 外壳）——
 *                    模块体缩进由生成器统一补，调用方不必关心自己会被放进模块里
 * @param importFqns  声明引用到的外部类型 FQN。<b>必须列全</b>：类被接管后反射 import
 *                    收集被跳过（成员已不渲染，扫出来的是死 import），漏列会让 {@code $Foo} 悬空。
 *                    给<b>真实类</b>即可——发射 import 时会自己查该类有无输入别名，
 *                    有则一并导入 {@code $Foo_}（适配器与枚举两套来源都查），
 *                    调用方不必判断该写 {@code Foo} 还是 {@code Foo_}
 */
public record ClassDeclarationCatalogEntry(
        String targetFqn,
        ScriptTypePredicate scriptType,
        String declaration,
        Set<String> importFqns,
        String description,
        List<String> examples,
        int priority
) {
    public ClassDeclarationCatalogEntry {
        importFqns = Set.copyOf(importFqns == null ? Set.of() : importFqns);
        examples = List.copyOf(examples == null ? List.of() : examples);
    }

    /** 全脚本类型生效、无附加 import 的替换。 */
    public static ClassDeclarationCatalogEntry of(String targetFqn, String declaration,
                                                  String description, List<String> examples) {
        return new ClassDeclarationCatalogEntry(targetFqn, ScriptTypePredicate.any(), declaration,
                Set.of(), description, examples, 0);
    }

    /** 带 import 的替换（声明引用了别的类型时必用，否则名字会悬空）。 */
    public static ClassDeclarationCatalogEntry of(String targetFqn, String declaration,
                                                  Set<String> importFqns,
                                                  String description, List<String> examples) {
        return new ClassDeclarationCatalogEntry(targetFqn, ScriptTypePredicate.any(), declaration,
                importFqns, description, examples, 0);
    }
}
