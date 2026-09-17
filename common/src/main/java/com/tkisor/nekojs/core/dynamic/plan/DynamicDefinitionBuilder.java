package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;

import java.util.Locale;

/**
 * 动态定义 builder 的共同基底（ticket 16）：只承载注册 mode。校验与规范化发生在
 * <b>setter 方法体内</b>——显式 {@code setMode(...)} 调用与 JavaBean-style property 写入
 * （{@code b.mode = 'reloadable'}）经 {@link DynamicBuilderSurface} 转发到<b>同一个</b>
 * {@code Method}，因此两种写法进入同一条校验/规范化/fingerprint 路径（AC4）。
 *
 * <p><b>成员命名约定</b>（设计决策，REPORT 记录）：动态 builder 采用 JavaBean 双形态
 * （{@code setXxx}/{@code getXxx} + property 写入）——与 spec 08 user story 13 的示例
 * （{@code item.setMaxStackSize(...)} ≡ {@code item.maxStackSize = ...}）及票 15 启动期
 * builder 契约一致。旧 {@code DynamicRegistryJS} 绑定的 fluent 链式形态
 * （{@code b.maxStackSize(64)}）属于旧路径（本票不动、迁移表说明）；新 facade 面不引入
 * 「属性名＝方法名」的双重语义（Graal invokeMember 与属性读值冲突，interop 语义不可靠）。
 */
public abstract class DynamicDefinitionBuilder {

    private DynamicRegisterMode mode = DynamicRegisterMode.WORLD;

    /** 注册 mode（{@code world} 默认 / {@code reloadable}）；{@code global} 在运行期不可用。 */
    public DynamicDefinitionBuilder setMode(String mode) {
        this.mode = DynamicRegisterMode.parse(DynamicDefinitionType.normalizeToken(mode, "register mode"));
        return this;
    }

    /** 当前 mode 的规范名（读面与 fingerprint 读数用）。 */
    public String getMode() {
        return mode == null ? null : mode.name().toLowerCase(Locale.ROOT);
    }

    /** 解析后的 mode（计划/Adapter 请求用；已在上游 setter 校验）。 */
    DynamicRegisterMode modeValue() {
        return mode;
    }
}
