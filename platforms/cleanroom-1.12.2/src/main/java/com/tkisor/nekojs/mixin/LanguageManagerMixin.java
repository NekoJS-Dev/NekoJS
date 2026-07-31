package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.client.NekoJSClient;
import com.tkisor.nekojs.client.NekoJSClientGeneration;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.resources.Locale;
import net.minecraft.util.text.translation.LanguageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 1.12.2 LanguageManagerMixin - 客户端生成管线的单一 chokepoint。
 *
 * <p>{@link LanguageManager} 是 Minecraft 构造中第一个注册的资源 reload listener，
 * 因此本 TAIL 注入在每次资源 reload（F3+T）时先于 ModelManager / TextureManager /
 * FontRenderer 执行：重载 CLIENT 脚本、把生成的 asset JSON 写入磁盘
 * （{@code FolderResourcePack} 在同一 reload 中即可读到），并把生成的翻译条目
 * 并入当前语言的 {@code Locale}。语言注入无需反射 —— vanilla 的
 * {@code onResourceManagerReload} 本体已把 {@code Locale.properties} 填好并
 * {@code LanguageMap.replaceWith} 同步，这里补一条 putAll 后重新同步即可。
 */
@Mixin(LanguageManager.class)
public abstract class LanguageManagerMixin {

    @Shadow
    protected static Locale CURRENT_LOCALE;

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void nekojs$injectGeneratedLang(IResourceManager manager, CallbackInfo ci) {
        // 首 tick 完成前的 reload（Minecraft 构造期间 / 启动加载）不触发脚本。
        if (!NekoJSClient.isReady()) {
            return;
        }

        Map<String, Map<String, String>> generated = NekoJSClientGeneration.generateAndCollect();
        if (generated.isEmpty()) {
            return;
        }

        Map<String, String> properties = ((LocaleAccessor) CURRENT_LOCALE).nekojs$getProperties();
        for (Map<String, String> entries : generated.values()) {
            properties.putAll(entries);
        }
        // 重新同步 I18n（Cleanroom 新版 I18n 直接读取 LanguageMap 实例）。
        LanguageMap.replaceWith(properties);
    }
}
