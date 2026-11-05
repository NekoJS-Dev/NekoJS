package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.testfixture.I18nTestFacade;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class I18nInteropTest {
    private static Context context(I18nTestFacade facade) {
        Context context = Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false)
                .option("js.nashorn-compat", "true").option("js.strict", "true")
                .option("js.ecmascript-version", "latest").build();
        context.getBindings("js").putMember("I18n", facade);
        return context;
    }

    @Test
    void directFacadeUsesVariadicArgumentsAndReadsCurrentTranslations() {
        AtomicInteger revision = new AtomicInteger();
        try (Context context = context(new I18nTestFacade((key, arguments) ->
                revision.get() + ":" + key + ":" + Arrays.toString(arguments)))) {
            String call = "I18n.translate('example.value', 'text', 3, 1.5, true, null)";
            assertEquals("0:example.value:[text, 3, 1.5, true, null]", context.eval("js", call).asString());
            revision.incrementAndGet();
            assertEquals("1:example.value:[text, 3, 1.5, true, null]", context.eval("js", call).asString());
            assertThrows(RuntimeException.class, () -> context.eval("js", "I18n.translate = 12"));
        }
    }

    @Test
    void beanPropertiesStayReadOnlyAndReflectLanguageChanges() {
        AtomicInteger revision = new AtomicInteger();
        var facade = new I18nTestFacade((key, arguments) -> key) {
            @Override public boolean has(String key) { return key.equals("same.key"); }
            @Override public String getLanguageCode() { return revision.get() == 0 ? "en_us" : "ar_sa"; }
            @Override public boolean isRightToLeft() { return revision.get() != 0; }
        };
        try (Context context = context(facade)) {
            assertTrue(context.eval("js", "I18n.has('same.key')").asBoolean());
            assertFalse(context.eval("js", "I18n.has('missing')").asBoolean());
            assertEquals("same.key", context.eval("js", "I18n.translateWithFallback('same.key', 'Wrong')").asString());
            assertEquals("en_us", context.eval("js", "I18n.languageCode").asString());
            assertFalse(context.eval("js", "I18n.rightToLeft").asBoolean());
            for (String statement : List.of("I18n.languageCode = 'fr_fr'", "I18n.rightToLeft = true")) {
                assertThrows(RuntimeException.class, () -> context.eval("js", statement));
            }
            revision.incrementAndGet();
            assertEquals("ar_sa", context.eval("js", "I18n.languageCode").asString());
            assertTrue(context.eval("js", "I18n.rightToLeft").asBoolean());
        }
    }
}
