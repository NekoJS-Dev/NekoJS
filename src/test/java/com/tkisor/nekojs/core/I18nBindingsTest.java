package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.facade.I18nFacade;
import com.tkisor.nekojs.bindings.static_access.I18nJS;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class I18nBindingsTest {
    @BeforeAll
    static void initializePlatform() {
        try {
            com.tkisor.nekojs.platform.Platform.instance();
            return;
        } catch (IllegalStateException uninitialized) {
            // This test also runs alone, without another suite installing the platform fixture.
        }
        com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
            public boolean isClient() { return false; }
            public boolean isDevelopment() { return true; }
            public String getMcVersion() { return "0.0.0"; }
            public String getLoaderId() { return "test"; }
            public String getLoaderVersion() { return "0"; }
            public java.nio.file.Path getGameDir() { return com.tkisor.nekojs.TestGameDirs.unique("nekojs-i18n"); }
            public java.util.Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return java.util.Map.of(); }
            public com.tkisor.nekojs.platform.IModInfo getInfo(String modId) { return null; }
        });
    }

    private static Context scriptContext() {
        return Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(java.util.List.of()).get())
                .allowHostClassLookup(name -> false)
                .option("js.nashorn-compat", "true").option("js.strict", "true")
                .option("js.ecmascript-version", "latest").build();
    }

    @Test
    void nativeLocalizationIsRegisteredOnlyForClientScripts() {
        for (ScriptType type : ScriptType.values()) {
            var registry = new BindingRegistry.BindingRegistryImpl(type).scopedTo("nekojs");
//? if neoforge {
            new NekoJSCorePlugin().registerBinding(registry);
//?} else {
            /*new com.tkisor.nekojs.fabric.FabricCorePlugin().registerBinding(registry);
*///?}
            var binding = registry.viewRegistered().get("I18n");
            if (type != ScriptType.CLIENT) {
                assertNull(binding, type.name());
                continue;
            }
            assertNotNull(binding);
            assertEquals(I18nFacade.class, binding.valueType());
            assertInstanceOf(I18nJS.class, binding.value());
            try (Context context = scriptContext()) {
                context.getBindings("js").putMember("I18n", binding.value());
                assertEquals("nekojs.test.missing", context.eval("js", "I18n.translate('nekojs.test.missing')").asString());
                assertEquals("nekojs.test.text-3-true", context.eval("js",
                        "I18n.translate('nekojs.test.%s-%s-%s', 'text', 3, true)").asString());
                assertFalse(context.eval("js", "I18n.has('nekojs.test.missing')").asBoolean());
                assertEquals("Fallback 3", context.eval("js",
                        "I18n.translateWithFallback('nekojs.test.missing', 'Fallback %s', 3)").asString());
            }
        }
    }

    @Test
    void nativeTableMembershipFallbackFormattingAndLanguageMetadataStayCurrent() throws Exception {
        var instanceField = net.minecraft.client.Minecraft.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        Object originalClient = instanceField.get(null);
        java.lang.reflect.Field tableField;
        try {
            tableField = net.minecraft.client.resources.language.I18n.class.getDeclaredField("language");
            tableField.setAccessible(true);
        } catch (NoSuchFieldException directLanguageLookup) {
            // Minecraft 26.2 reads Language directly instead of retaining an I18n table field.
            tableField = null;
        }
        Object originalTable = tableField == null ? null : tableField.get(null);
        var originalLanguage = net.minecraft.locale.Language.getInstance();
        var rtl = new java.util.concurrent.atomic.AtomicBoolean(true);
        var translations = java.util.Map.of("same.key", "same.key", "known", "Known %s", "fallback.key", "Wrong translation");
        var language = new net.minecraft.locale.Language() {
            @Override public String getOrDefault(String key, String fallback) { return translations.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return translations.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return rtl.get(); }
            @Override public net.minecraft.util.FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text) {
                return originalLanguage.getVisualOrder(text);
            }
        };
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var client = (net.minecraft.client.Minecraft) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(net.minecraft.client.Minecraft.class);
        var manager = new net.minecraft.client.resources.language.LanguageManager("ar_sa", ignored -> {});
        var managerField = net.minecraft.client.Minecraft.class.getDeclaredField("languageManager");
        managerField.setAccessible(true);
        managerField.set(client, manager);
        try (Context context = scriptContext()) {
            instanceField.set(null, client);
            if (tableField != null) tableField.set(null, language);
            net.minecraft.locale.Language.inject(language);
            context.getBindings("js").putMember("I18n", new I18nJS());
            assertTrue(context.eval("js", "I18n.has('same.key')").asBoolean());
            assertEquals("same.key", context.eval("js", "I18n.translateWithFallback('same.key', 'Wrong')").asString());
            assertEquals("Known 3", context.eval("js", "I18n.translateWithFallback('known', 'Wrong', 3)").asString());
            assertEquals("fallback.key", context.eval("js", "I18n.translateWithFallback('missing', 'fallback.key')").asString());
            assertEquals("true/text/3/null/%", context.eval("js",
                    "I18n.translateWithFallback('missing', '%2$s/%1$s/%3$s/%4$s/%%', 'text', true, 3, null)").asString());
            assertEquals("1.50", context.eval("js", "I18n.translateWithFallback('missing', '%.2f', 1.5)").asString());
            assertEquals("Format error: %q", context.eval("js", "I18n.translateWithFallback('missing', '%q')").asString());
            assertEquals("ar_sa", context.eval("js", "I18n.languageCode").asString());
            assertTrue(context.eval("js", "I18n.rightToLeft").asBoolean());
            assertThrows(RuntimeException.class, () -> context.eval("js", "I18n.languageCode = 'invalid'"));
            assertThrows(RuntimeException.class, () -> context.eval("js", "I18n.rightToLeft = false"));
            manager.setSelected("en_us");
            rtl.set(false);
            assertEquals("en_us", context.eval("js", "I18n.languageCode").asString());
            assertFalse(context.eval("js", "I18n.rightToLeft").asBoolean());
        } finally {
            instanceField.set(null, originalClient);
            if (tableField != null) tableField.set(null, originalTable);
            net.minecraft.locale.Language.inject(originalLanguage);
        }
    }
    @Test
    void directJavaBindingRejectsInvalidArgumentsAndUsesOrdinaryVarargs() {
        try (Context context = scriptContext()) {
            context.getBindings("js").putMember("I18n", new I18nJS());
            for (String call : new String[] { "I18n.translate()", "I18n.translate(12)",
                    "I18n.translate(null)", "I18n.translate('key', {})", "I18n.translate = 12",
                    "I18n.has()", "I18n.has(3)", "I18n.has(null)", "I18n.has('key', true)",
                    "I18n.translateWithFallback('key')", "I18n.translateWithFallback(3, 'fallback')",
                    "I18n.translateWithFallback(null, 'fallback')", "I18n.translateWithFallback('key', null)",
                    "I18n.translateWithFallback('key', '%s', {})", "I18n.has = 3", "I18n.translateWithFallback = 3" }) {
                assertThrows(RuntimeException.class, () -> context.eval("js", call), call);
            }
            assertEquals("text/3/1.5/true/null", context.eval("js",
                    "I18n.translateWithFallback('nekojs.test.missing', '%s/%s/%s/%s/%s', 'text', 3.0, 1.5, true, null)").asString());
            assertEquals("3/4", context.eval("js",
                    "I18n.translateWithFallback('nekojs.test.missing', '%s/%s', [3, 4])").asString());
            assertEquals("-0.0", context.eval("js",
                    "I18n.translateWithFallback('nekojs.test.missing', '%s', -0)").asString());
            assertEquals("9223372036854775807", context.eval("js",
                    "I18n.translateWithFallback('nekojs.test.missing', '%s', 9223372036854775807n)").asString());
            assertThrows(RuntimeException.class, () -> context.eval("js",
                    "I18n.translateWithFallback('nekojs.test.missing', '%s', 9223372036854775809n)"));
            assertEquals("null", context.eval("js",
                    "I18n.translateWithFallback('nekojs.test.missing', '%s', null)").asString());
            assertEquals("Format error: %s/%s", context.eval("js",
                    "I18n.translateWithFallback('nekojs.test.missing', '%s/%s', null)").asString());
            assertThrows(RuntimeException.class, () -> context.eval("js",
                    "I18n.translateWithFallback('nekojs.test.missing', '%s', [[3]])"));
            assertEquals("undefined", context.eval("js", "typeof I18n.formatArguments").asString());
        }
    }

}
