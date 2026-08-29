package com.tkisor.nekojs.api.recipe;

/**
 * 配方创建上下文（阶段 5：错误上下文）。
 *
 * <p>{@code scriptId} 为创建配方的脚本文件 id（可能为 null，如插件创建），
 * 由平台侧在脚本回调执行期间从 {@code ScriptContextRegistry} 捕获；
 * 出错时与 {@code api/type/prefix} 一起输出，便于定位脚本位置。
 */
public record RecipeCreationContext(String api, String type, String prefix, String scriptId) {
    public static RecipeCreationContext of(String api, String type, String prefix) {
        return new RecipeCreationContext(api, type, prefix, null);
    }

    public static RecipeCreationContext of(String api, String type, String prefix, String scriptId) {
        return new RecipeCreationContext(api, type, prefix, scriptId);
    }

    public RecipeCreationContext withScriptId(String scriptId) {
        return new RecipeCreationContext(api, type, prefix, scriptId);
    }

    public String describe(String id) {
        String base = "id=" + id + ", type=" + type + ", api=" + api + ", prefix=" + prefix;
        return scriptId == null ? base : base + ", script=" + scriptId;
    }
}
