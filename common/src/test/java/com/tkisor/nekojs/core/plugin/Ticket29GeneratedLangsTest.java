package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic language set for plugin-only language declarations.
 *
 * <p>Covers four behaviors of {@link PluginGenerationHooks#resolveGeneratedLangs}: the default
 * {@code en_us} keeps plugin callbacks from being silently skipped when no script listener exists,
 * the sorted union of plugin declarations with the script keyed listener languages, whole-batch
 * rejection of an invalid language before any file is written, and a single plugin's failed
 * declaration invalidating only that plugin without touching the others.
 *
 * <p>Plugins are injected the same way as in {@link PluginGenerationHooksTest}: reflection backs up
 * and clears the private static {@code ENTRIES} of {@link NekoJSBasePluginManager}; production code
 * documents the field as non-final only so tests can swap it. No production API is added or
 * changed.
 */
class Ticket29GeneratedLangsTest {

    private static final Field ENTRIES_FIELD = field("ENTRIES");
    private static final Field SORTED_VIEW_FIELD = field("sortedView");
    private static final Field OWNED_VIEW_FIELD = field("ownedView");

    private Object previousEntries;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void isolatePluginManager() throws Exception {
        previousEntries = ENTRIES_FIELD.get(null);
        ENTRIES_FIELD.set(null, new CopyOnWriteArrayList<>());
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
    }

    @AfterEach
    void restorePluginManager() throws Exception {
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
        ENTRIES_FIELD.set(null, previousEntries == null ? new CopyOnWriteArrayList<>() : previousEntries);
    }

    private static Field field(String name) {
        try {
            Field f = NekoJSBasePluginManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void defaultsToEnUsSoPluginCallbacksAreNotGatedByScriptListeners() {
        NekoJSBasePluginManager.registerClass(DefaultLangsPlugin.class);

        // No script-side ClientEvents.lang listener exists here, so with no default declaration
        // registeredKeys() is empty and the generateLang plugin callback never fires; the default
        // must keep en_us in the set
        List<String> langs = PluginGenerationHooks.resolveGeneratedLangs(Set.of());

        assertTrue(langs.contains("en_us"),
                "the default generatedLangs() must keep en_us present so the generateLang plugin"
                        + " callback is not silently skipped when no script lang listener exists;"
                        + " got " + langs);
    }

    @Test
    void unionOfPluginDeclarationsAndScriptKeysIsDeduplicatedAndSorted() {
        NekoJSBasePluginManager.registerClass(TwoLangPlugin.class);
        NekoJSBasePluginManager.registerClass(DefaultLangsPlugin.class);

        List<String> first = PluginGenerationHooks.resolveGeneratedLangs(
                Set.of("sv_se", "en_us", "de_de"));
        // The same three script keys in another input order: the result must be equal element by
        // element (TreeSet normalizes it, so input order does not matter).
        List<String> second = PluginGenerationHooks.resolveGeneratedLangs(
                Set.of("de_de", "en_us", "sv_se"));

        assertEquals(first, second,
                "the language list must not depend on the iteration order of the script keys");
        assertEquals(List.of("de_de", "en_us", "fr_fr", "ja_jp", "sv_se"), first,
                "plugin declarations union script keys, deduplicated by code and sorted lexicographically;"
                        + " got " + first);
    }

    @Test
    void invalidPluginLangRejectsTheWholeBatchWithOwnerAndCodeInTheMessage() {
        NekoJSBasePluginManager.registerClass(InvalidLangPlugin.class);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of("en_us")));

        assertTrue(error.getMessage().contains(InvalidLangPlugin.class.getName()),
                "the rejection must name the owning plugin: " + error.getMessage());
        assertTrue(error.getMessage().contains("zh cn"),
                "the rejection must name the offending language code: " + error.getMessage());
    }

    @Test
    void invalidScriptLangKeyIsRejectedBeforeAnyWrite() {
        NekoJSBasePluginManager.registerClass(DefaultLangsPlugin.class);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of("en_us", "../evil")));

        assertTrue(error.getMessage().contains("../evil"),
                "a bad script listener key must be rejected too: " + error.getMessage());
    }

    @Test
    void overlongAndEmptyLangCodesAreRejected() {
        NekoJSBasePluginManager.registerClass(DefaultLangsPlugin.class);

        assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of("x".repeat(65))),
                "codes longer than 64 characters must be rejected");
        assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of("")),
                "an empty code must be rejected");
    }

    @Test
    void oneFailingPluginDoesNotPoisonOtherPluginsDeclarations() {
        NekoJSBasePluginManager.registerClass(ThrowingLangsPlugin.class);
        NekoJSBasePluginManager.registerClass(TwoLangPlugin.class);

        // Returning instead of throwing is the first difference: a whole-batch failure throws IllegalStateException.
        List<String> langs = PluginGenerationHooks.resolveGeneratedLangs(Set.of());

        assertEquals(List.of("fr_fr", "ja_jp"), langs,
                "a plugin whose generatedLangs() throws loses only its own declarations: the batch must"
                        + " still succeed and carry exactly the other plugin's valid declarations;"
                        + " got " + langs);
    }

    @Test
    void nullDeclarationIsTreatedAsAViolation() {
        NekoJSBasePluginManager.registerClass(NullLangsPlugin.class);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of()));

        assertTrue(error.getMessage().contains(NullLangsPlugin.class.getName()),
                "a null declaration must be attributed to its plugin: " + error.getMessage());
        assertTrue(error.getMessage().contains("null"),
                "a null declaration must be reported as null: " + error.getMessage());
    }

    /* ================= Stub plugins (injected through NekoJSBasePluginManager.registerClass) ================= */

    /** Overrides no generatedLangs(): the default returns Set.of("en_us"). */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class DefaultLangsPlugin implements NekoJSPlugin {
    }

    /** Declares two non-English languages explicitly, showing that the declarations take effect. */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class TwoLangPlugin implements NekoJSPlugin {
        @Override
        public Set<String> generatedLangs() {
            return Set.of("ja_jp", "fr_fr");
        }
    }

    /** Declares an invalid code that contains a space. */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class InvalidLangPlugin implements NekoJSPlugin {
        @Override
        public Set<String> generatedLangs() {
            return Set.of("en_us", "zh cn");
        }
    }

    /** Throws while declaring: only this plugin's declarations are dropped. */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class ThrowingLangsPlugin implements NekoJSPlugin {
        @Override
        public Set<String> generatedLangs() {
            throw new IllegalStateException("boom-generatedLangs");
        }
    }

    /** Returns null, which is attributed to this plugin as a violation. */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class NullLangsPlugin implements NekoJSPlugin {
        @Override
        public Set<String> generatedLangs() {
            return null;
        }
    }
}
