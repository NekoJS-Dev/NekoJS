package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoModuleMode;

/**
 * prepared module 的<strong>语言身份</strong>：由模块解析阶段（path 的扩展名 → requested mode，
 * 编译器注册表 → language id）唯一决定，不需要运行编译管线即可计算。
 *
 * <p>工单 11：cache 命中判定不能只看文件内容指纹。同一路径、同一内容在以下情况下语义已变：
 * <ul>
 *   <li>插件用 {@code replaceLanguage} 换了该扩展名的语言（language id 变化）；</li>
 *   <li>扩展名/请求模式变化（{@link NekoModuleMode}：{@code .cjs} 强制 CJS、{@code .mjs} 强制 ESM）。</li>
 * </ul>
 * 因此 {@link NekoModulePreparation} 在 stamp 相等时还要比对本 identity，否则会返回旧语言编译的模块。
 *
 * @param languageId 解析到的语言 id（{@code javascript} / {@code typescript} / {@code legacy:<ext>} / 插件自定义）
 * @param mode       由扩展名请求的模块模式（{@link NekoModuleMode#AUTO} 表示待管线判定）
 */
public record NekoModuleIdentity(String languageId, NekoModuleMode mode) {

    public NekoModuleIdentity {
        languageId = languageId == null || languageId.isBlank() ? "unknown" : languageId;
        mode = mode == null ? NekoModuleMode.AUTO : mode;
    }
}
