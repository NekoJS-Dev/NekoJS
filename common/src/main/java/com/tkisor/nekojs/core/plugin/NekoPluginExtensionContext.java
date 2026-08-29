package com.tkisor.nekojs.core.plugin;

/**
 * 扩展点收集期的 bootstrap 上下文：环境信息 + 先序扩展点的已完成产物。
 *
 * <p><b>访问两档（ADR-0001 §7）：</b>
 * <ul>
 *   <li>{@link #result} —— 可选依赖：对方未注册或被环境跳过时返回 {@code null}，调用方自行处理；</li>
 *   <li>{@link #resultOrThrow} —— 必需依赖：对方不存在（未注册 / 环境跳过 / 尚未 finish）
 *       一律抛 {@link IllegalStateException}。</li>
 * </ul>
 *
 * <p><b>数据依赖违序（ADR-0002 ②）：</b>拓扑序保证被 {@code dependsOn} 声明的点先完成；
 * 若读取了一个已注册但尚未 finish 的点（漏声明 dependsOn 或图被破坏），{@link #result}
 * 也会立即抛 {@link IllegalStateException}，报错附"declare dependsOn"修复指引——
 * 漏声明不可能静默出错。
 */
public interface NekoPluginExtensionContext {

    /** 当前进程是否为客户端（专用服务器为 {@code false}）。 */
    boolean client();

    /**
     * 按扩展点实例查询先序点的产物（可选依赖档）。
     *
     * @return 产物；该点未注册或被环境跳过时为 {@code null}；
     *         该点已注册但尚未 finish 时抛 {@link IllegalStateException}（违序，附修复指引）
     */
    <R> R result(NekoPluginExtensionPoint<?, ?, R> point);

    /**
     * 按扩展点 id 查询先序点的产物（可选依赖档）。
     *
     * @return 产物；该点未注册、被环境跳过或 finisher 返回 null 时为 {@code null}；
     *         产物类型不匹配 / 已注册但尚未 finish 时抛 {@link IllegalStateException}
     */
    <R> R result(String pointId, Class<R> type);

    /**
     * 按扩展点实例查询先序点的产物（必需依赖档）。
     *
     * @return 产物
     * @throws IllegalStateException 该点未注册、被环境跳过或尚未 finish
     */
    <R> R resultOrThrow(NekoPluginExtensionPoint<?, ?, R> point);

    /**
     * 按扩展点 id 查询先序点的产物（必需依赖档）。
     *
     * @return 产物
     * @throws IllegalStateException 该点未注册、被环境跳过或尚未 finish，或产物类型不匹配
     */
    <R> R resultOrThrow(String pointId, Class<R> type);
}
