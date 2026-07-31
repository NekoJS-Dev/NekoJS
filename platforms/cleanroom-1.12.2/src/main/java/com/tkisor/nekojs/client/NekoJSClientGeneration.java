package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.plugin.PluginGenerationHooks;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1.12.2 客户端生成管线（{@code LanguageManagerMixin} TAIL 调用）。
 *
 * <p>{@code LanguageManager} 是 Minecraft 构造中第一个注册的资源 reload listener，
 * 因此本管线在每次资源 reload（F3+T）中先于 ModelManager / TextureManager /
 * FontRenderer 执行：重载 CLIENT 脚本、把生成的 asset JSON 写入
 * {@code <gameDir>/nekojs/assets}（同一 reload 中 {@code FolderResourcePack}
 * 即可读到），并收集各语言的翻译条目供 mixin 注入。
 */
public final class NekoJSClientGeneration {
    private NekoJSClientGeneration() {}

    /**
     * 重载 CLIENT 脚本并触发资产生成，返回按语言代码聚合的翻译条目
     * （{@code langCode -> entries}；无条目时返回空 map）。
     */
    public static Map<String, Map<String, String>> generateAndCollect() {
        try {
            NekoJSMod.RUNTIME_ROOT.reload(ScriptType.CLIENT);
        } catch (Exception e) {
            ScriptType.CLIENT.logger().error("CLIENT script reload failed: ", e);
        }

        Map<String, Map<String, String>> langs = new LinkedHashMap<>();
        try {
            Path assets = NekoJSPaths.get().assets();
            DataGeneratorJS generator = new DataGeneratorJS(assets, "after_mods");
            PluginGenerationHooks.fireGenerateAssets(generator);
            ClientEvents.GENERATE_ASSETS.post(generator, "after_mods");
            // 1.12.2 语言为 .lang 文本格式：仅聚合条目（不写 lang/<lang>.json），
            // 由 mixin 注入当前语言的 Locale。
            for (String lang : ClientEvents.LANG.registeredKeys()) {
                LangGeneratorJS langGenerator = new LangGeneratorJS(lang);
                PluginGenerationHooks.fireGenerateLang(langGenerator);
                ClientEvents.LANG.post(langGenerator, lang);
                langs.put(lang, langGenerator.entries());
            }
        } catch (Exception e) {
            ScriptType.CLIENT.logger().error("Client generation event failed", e);
        }
        return langs;
    }
}
