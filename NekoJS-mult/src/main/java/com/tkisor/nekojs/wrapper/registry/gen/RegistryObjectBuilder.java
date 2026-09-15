package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import java.util.function.Supplier;

import net.minecraft.resources.Identifier;

/**
 * 通用注册表对象构建器基类（ticket 15 单一写入语义；ADR-0005 public field 约定已废除）：
 *
 * <ul>
 *   <li><b>数据属性 = 私有字段 + JavaBean setter</b>——脚本端
 *       {@code builder.maxStackSize = 16}（经 {@code BuilderSurface} 的 putMember）与
 *       显式 {@code builder.setMaxStackSize(16)} 调用<b>同一个 setter</b>，进入同一
 *       校验、规范化、definition fingerprint 与注册收集路径；不得保留同名 public
 *       field 绕过 setter（{@code id} 是 final identity 只读例外）；</li>
 *   <li><b>implements Supplier</b>——懒互引：跨注册表的派生对象（BlockItem、流体方块、桶）
 *       全部经 {@link #get()} 引用，对象创建推迟到平台注册事件抽干期；</li>
 *   <li><b>连带注册</b>——子类按需覆写 {@link #handleAdditionalObjects(AdditionalObjectRegistry)}
 *       （预创建子 builder + {@code noXxx()} 置 null 抑制 + 本回调在主对象抽干期统一执行）。</li>
 * </ul>
 *
 * @param <T> 注册表持有的对象类型
 */
public abstract class RegistryObjectBuilder<T> implements Supplier<T> {

    /** 对象 id（含命名空间）。 */
    public final Identifier id;

    private T built;

    protected RegistryObjectBuilder(Identifier id) {
        this.id = id;
    }

    /** 构建目标对象（平台注册事件抽干期调用，恰一次）。 */
    public abstract T build();

    /** {@link Supplier} 视图：首次调用构建并缓存（懒互引的解析点）。 */
    @Override
    public final T get() {
        if (built == null) {
            built = build();
        }
        return built;
    }

    /** 连带注册回调（after-all 后置阶段统一执行；无派生对象的 builder 不覆写）。 */
    public void handleAdditionalObjects(AdditionalObjectRegistry registry) {
    }

    /** 连带注册目标抽象：向<b>其他</b>注册表追加派生对象（由事件仓库实现）。 */
    public interface AdditionalObjectRegistry {

        /**
         * 追加一条派生注册。目标以 {@link net.minecraft.resources.ResourceKey} 表达
         * （而非活 {@link net.minecraft.core.Registry} 实例）：派生条目在目标注册表
         * <b>自己的</b>平台 pass 内投递，收集期目标注册表可能尚未创建。
         */
        void additional(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> registry,
                Identifier id, Supplier<?> supplier);
    }
}
