package com.tkisor.nekojs.core.context;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import graal.graalvm.polyglot.Engine;

/**
 * 核心运行时横切依赖的不可变载体。
 *
 * <p>只承载 core-wide、与 paths/config/engine/error 同生命周期的 5 个字段：
 * {@code paths}、{@code engine}、{@code sandboxConfig}、{@code classFilter}、{@code errorTracker}。
 *
 * <p>约束（见 {@code ai_arch/architecture.md} §3.1 与 {@code plan.md} Phase 2.1）：
 * <ul>
 *   <li>不提供 {@code get()} / {@code current()} / {@code init()} / {@code lookup(Class)} 等服务查找能力。</li>
 *   <li>普通业务类构造器默认不接收 {@code NekoCoreContext}；只有 architecture allowlist 中列出的
 *       少数 core factory / infrastructure 类型可接收，且必须记录为什么不能直接注入具体字段。</li>
 *   <li>接收 {@code NekoCoreContext} 的类只能作为 terminal consumer / factory input；可以读取字段创建
 *       具体协作者，但不能把 {@code NekoCoreContext} 继续转传给非 allowlist 类。</li>
 *   <li>{@code ScriptManager}、catalog provider、recipe handler、event bridge、bindings installer、
 *       module resolver、module cache 等子系统服务不直接接收 {@code NekoCoreContext}。</li>
 *   <li>新增字段必须先通过准入检查（core-wide？同生命周期？是否过宽？能否用 role interface 替代？）；
 *       字段数超过 6 默认不接受。</li>
 * </ul>
 *
 * <p>如果一个类只需要单个能力，直接注入具体字段：只需路径注入 {@link NekoJSPaths}，
 * 只需配置注入 {@link SandboxConfig}，只需错误记录注入 {@link ErrorTracker}。
 */
public record NekoCoreContext(
        NekoJSPaths paths,
        Engine engine,
        SandboxConfig sandboxConfig,
        ClassFilter classFilter,
        ErrorTracker errorTracker
) {
}
