package com.tkisor.nekojs.core.context;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import graal.graalvm.polyglot.Engine;

/**
 * 核心运行时横切依赖的不可变载体。
 *
 * <p>只承载 core-wide、与 config/engine/error 同生命周期的 4 个字段：
 * {@code engine}、{@code sandboxConfig}、{@code classFilter}、{@code errorTracker}。
 * 路径通过 {@link com.tkisor.nekojs.core.fs.NekoJSPaths#get()} 单例访问。
 *
 * <ul>
 *   <li>不提供 {@code get()} / {@code current()} 等服务查找能力。</li>
 *   <li>普通业务类构造器默认不接收 {@code NekoCoreContext}。</li>
 *   <li>接收 {@code NekoCoreContext} 的类只能作为 terminal consumer / factory input。</li>
 * </ul>
 */
public record NekoCoreContext(
        Engine engine,
        SandboxConfig sandboxConfig,
        ClassFilter classFilter,
        ErrorTracker errorTracker
) {
}
