package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;

/** Resolves plain text using the current client language. */
public interface I18nFacade {
    @Doc("Translates a key using the current client language. Missing keys remain visible. Available only to CLIENT scripts.")
    @Param(name = "key", value = "full translation key")
    @Param(name = "args", value = "string, number, boolean or null format arguments")
    String translate(String key, Object... args);

    @Doc("Checks the active client translation table, including language fallbacks. A translation equal to its key still exists.")
    @Param(name = "key", value = "full translation key")
    boolean has(String key);

    @Doc("Translates an existing key or formats a literal fallback with Locale.ROOT when missing. Malformed fallback formats return Format error: followed by the template.")
    @Param(name = "key", value = "full translation key")
    @Param(name = "fallback", value = "fallback format string, used only when the key is absent")
    @Param(name = "args", value = "string, number, boolean or null format arguments")
    String translateWithFallback(String key, String fallback, Object... args);

    @Doc("Current selected client language code, such as zh_cn. Read-only Bean property.")
    String getLanguageCode();

    @Doc("Whether the active client language uses right-to-left layout. Read-only Bean property.")
    boolean isRightToLeft();
}
