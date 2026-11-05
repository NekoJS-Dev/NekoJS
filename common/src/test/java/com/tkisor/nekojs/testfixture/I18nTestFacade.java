package com.tkisor.nekojs.testfixture;

import com.tkisor.nekojs.api.facade.I18nFacade;

import java.util.Locale;
import java.util.function.BiFunction;

/** Empty English translation table for tests without a Minecraft client. */
public class I18nTestFacade implements I18nFacade {
    private final BiFunction<String, Object[], String> translator;

    public I18nTestFacade(BiFunction<String, Object[], String> translator) { this.translator = translator; }
    @Override public String translate(String key, Object... args) { return translator.apply(key, args); }
    @Override public boolean has(String key) { return false; }
    @Override public String translateWithFallback(String key, String fallback, Object... args) {
        return has(key) ? translate(key, args) : String.format(Locale.ROOT, fallback, args);
    }
    @Override public String getLanguageCode() { return "en_us"; }
    @Override public boolean isRightToLeft() { return false; }
}
