package com.tkisor.nekojs.mixin;

import net.minecraft.client.resources.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 1.12.2 Locale 访问器：暴露 {@code Locale.properties}（当前语言的翻译条目表）。
 *
 * <p>配合 {@link LanguageManagerMixin} 在资源 reload 后把脚本生成的翻译条目
 * 直接并入当前语言的 {@code Locale}，再经 {@code LanguageMap.replaceWith}
 * 同步到 I18n（Cleanroom 新版 I18n 走 LanguageMap，旧路径 StatCollector 等
 * 走 Locale.properties —— 两者必须同时更新）。
 */
@Mixin(Locale.class)
public interface LocaleAccessor {

    @Accessor("properties")
    Map<String, String> nekojs$getProperties();
}
