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
     * 把 {@code typeAcquisition.enable} 写进指定 {@code jsconfig.json} 的顶层 {@code typeAcquisition}。
     *
     * <p>脚本工程自带 probe 全量声明，VS Code 的 Automatic Type Acquisition 只会按 JS 依赖名
     * 猜测并联网拉取 @types 包——离线/代理环境下会拖慢甚至卡住语言服务，并混入无关全局。
     * typeAcquisition 是 probe 拥有的对象：整体替换（语义同 {@link #mergeJsConfigIncludes}）。
     *
     * @param jsconfigFile 目标 jsconfig.json（不存在则创建）
     * @param enable       是否启用 ATA（probe 一律贡献 false）
     */
    default void mergeJsConfigTypeAcquisition(java.nio.file.Path jsconfigFile, boolean enable) {}

    /**
     * 把 extraPaths 合并进指定 {@code pyrightconfig.json}（去重；fresh 文件附带默认 typeCheckingMode）。
     *
     * @param pyrightFile 目标 pyrightconfig.json（不存在则创建）
     * @param extraPaths  要确保存在的相对路径列表
     */
    void mergePyrightExtraPaths(java.nio.file.Path pyrightFile, List<String> extraPaths);

    /**
     * 把 {@code python.analysis.extraPaths} 合并进 VSCode 工作区设置
     * （{@code <workspaceRoot>/.vscode/settings.json}，去重、保留未知键）。
     *
     * <p>Pylance 对 pyrightconfig.json 的发现策略在不同版本/打开方式下不稳定，而
     * {@code .vscode/settings.json} 由 VSCode 原生加载，是 Pylance 最可靠的路径注入方式；
     * pyright CLI 则继续使用 pyrightconfig.json。两者同步写入，互为兜底。
     */
    default void mergeVscodePythonExtraPaths(java.nio.file.Path settingsFile, List<String> extraPaths) {}

    /**
     * 通用 VSCode {@code .vscode/settings.json} 注入：把任意 backend 声明的配置片段合并进工作区设置。
     *
     * <p>合并策略由每条 {@link VscodeSetting#mode()} 决定；除声明为「替换」的叶子键外，
     * 既有文件的其它键（含用户自定义键与其它 backend 注入的键）全部保留，probe 只增删自己拥有的键。
     * 对象贡献（{@link VscodeSettingMerge#MERGE_OBJECT}）递归合并，使多个 backend 可以向同一个
     * 设置对象（如 {@code files.exclude}）各注入自己的条目而不互相覆盖。
     *
     * @param settingsFile 目标 {@code <workspaceRoot>/.vscode/settings.json}（不存在则创建）
     * @param settings     要合并的贡献列表（按顺序应用）
     */
    default void mergeVscodeSettings(java.nio.file.Path settingsFile, List<VscodeSetting> settings) {}

    /** {@link #mergeVscodeSettings} 中一条叶子键的合并策略。 */
    enum VscodeSettingMerge {
        /** probe 拥有该键：总用贡献值替换（如 TS 的语言服务开关）。 */
        SET,
        /** 仅当键不存在时写入：用户已显式设置时保留用户值（如 {@code python.languageServer}）。 */
        SET_IF_ABSENT,
        /** 贡献值必须是 JSON 对象：与既有对象递归合并（贡献叶子替换，用户/其它 backend 叶子保留）。 */
        MERGE_OBJECT,
        /** 贡献值必须是 JSON 字符串数组：与既有数组按字符串去重追加（用户路径保留）。 */
        EXTEND_STRING_ARRAY,
    }

    /**
     * 一条 VSCode settings 注入：点分路径（如 {@code python.analysis.extraPaths}）+ 值 + 合并策略。
     * {@code value} 接受 Gson 可直接序列化的普通值（String/Number/Boolean/List/Map）。
     */
    record VscodeSetting(String key, Object value, VscodeSettingMerge mode) {
        public VscodeSetting {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("vscode setting key must not be blank");
            if (mode == null) throw new IllegalArgumentException("vscode setting merge mode must not be null");
        }
    }

    /**
     * 把编辑器片段合并进 VSCode {@code .code-snippets} 文件（probe 拥有的片段名替换，用户片段保留）。
     *
     * @param snippetsFile 目标 .code-snippets（如 {@code nekojs/.vscode/nekojs.code-snippets}，不存在则创建）
     * @param snippets     要写入的片段列表
     */
    void mergeVscodeSnippets(java.nio.file.Path snippetsFile, List<Snippet> snippets);
}
