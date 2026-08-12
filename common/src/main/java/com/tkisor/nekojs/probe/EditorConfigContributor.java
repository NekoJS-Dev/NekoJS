package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.events.Snippet;

import java.util.List;
import java.util.Map;

/**
 * 编辑器配置贡献槽：每个 {@link ProbeBackend} 在 {@link ProbeBackend#contributeEditorConfig} 中
 * 把自己语言工具器所需的配置片段贡献进来，由实现负责合并到对应的编辑器配置文件。
 *
 * <p>解耦「backend 声明贡献什么」与「配置写到哪里/如何合并」：
 * <ul>
 *   <li>{@link #mergeJsConfigPaths}：把 TS 路径别名（{@code java:*}、{@code @side-only/*}、{@code @special/*}）
 *       合并进某个脚本目录的 {@code jsconfig.json}（probe 拥有的键被替换，用户键保留）。</li>
 *   <li>{@link #mergeJsConfigIncludes} / {@link #mergeJsConfigTypeRoots}：把 include 与 typeRoots
 *       合并进同一个 jsconfig.json（数组整体替换为 probe 贡献值）。</li>
 *   <li>{@link #mergePyrightExtraPaths}：把路径合并进 {@code pyrightconfig.json} 的 {@code extraPaths}（去重）。</li>
 * </ul>
 *
 * <p>用接口（而非直接写文件）便于单测：测试可传入记录式实现，断言 backend 贡献了什么，无需触碰磁盘。
 */
public interface EditorConfigContributor {

    /**
     * 把路径别名合并进指定 {@code jsconfig.json} 的 {@code compilerOptions.paths}。
     *
     * @param jsconfigFile 目标 jsconfig.json（不存在则创建）
     * @param pathAliases  别名 → 候选路径列表（probe 拥有的键替换旧值，用户自定义键保留）
     */
    void mergeJsConfigPaths(java.nio.file.Path jsconfigFile, Map<String, List<String>> pathAliases);

    /**
     * 把 include globs 合并进指定 {@code jsconfig.json} 顶层的 {@code include} 数组。
     *
     * <p>include 是 probe 拥有的数组：整体替换为贡献值（用户若自定义 include 大概率就是要覆盖，
     * 语义可接受）。文件不存在则创建；其余键保留。
     *
     * @param jsconfigFile 目标 jsconfig.json（不存在则创建）
     * @param includeGlobs 相对脚本目录的 include glob 列表（probe 贡献值）
     */
    void mergeJsConfigIncludes(java.nio.file.Path jsconfigFile, List<String> includeGlobs);

    /**
     * 把 typeRoots 合并进指定 {@code jsconfig.json} 的 {@code compilerOptions.typeRoots} 数组。
     *
     * <p>typeRoots 是 probe 拥有的数组：整体替换为贡献值（语义同 {@link #mergeJsConfigIncludes}）。
     * 文件不存在则创建；其余键保留。
     *
     * @param jsconfigFile 目标 jsconfig.json（不存在则创建）
     * @param typeRoots    类型根目录列表（probe 贡献值）
     */
    void mergeJsConfigTypeRoots(java.nio.file.Path jsconfigFile, List<String> typeRoots);

    /**
     * 把 extraPaths 合并进指定 {@code pyrightconfig.json}（去重；fresh 文件附带默认 typeCheckingMode）。
     *
     * @param pyrightFile 目标 pyrightconfig.json（不存在则创建）
     * @param extraPaths  要确保存在的相对路径列表
     */
    void mergePyrightExtraPaths(java.nio.file.Path pyrightFile, List<String> extraPaths);

    /**
     * 把编辑器片段合并进 VSCode {@code .code-snippets} 文件（probe 拥有的片段名替换，用户片段保留）。
     *
     * @param snippetsFile 目标 .code-snippets（如 {@code nekojs/.vscode/nekojs.code-snippets}，不存在则创建）
     * @param snippets     要写入的片段列表
     */
    void mergeVscodeSnippets(java.nio.file.Path snippetsFile, List<Snippet> snippets);
}
