package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.ScriptTypePredicate;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingSideSelectionTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void clientProcessRegistersClientBindings() {
        ScriptTypePredicate predicate = NekoPluginBootstrap.bindingPredicate(true);
        Set<ScriptType> matched = predicate.streamMatched().collect(Collectors.toSet());
        assertTrue(matched.contains(ScriptType.CLIENT),
                "client process must register CLIENT bindings");
        assertTrue(matched.contains(ScriptType.STARTUP),
                "client process must register STARTUP bindings");
        assertTrue(matched.contains(ScriptType.SERVER),
                "client process must register SERVER bindings (integrated server)");
    }

    @Test
    void dedicatedServerExcludesClientBindings() {
        ScriptTypePredicate predicate = NekoPluginBootstrap.bindingPredicate(false);
        Set<ScriptType> matched = predicate.streamMatched().collect(Collectors.toSet());
        assertFalse(matched.contains(ScriptType.CLIENT),
                "dedicated server must NOT register CLIENT bindings");
        assertTrue(matched.contains(ScriptType.SERVER),
                "dedicated server must register SERVER bindings");
        assertTrue(matched.contains(ScriptType.STARTUP),
                "dedicated server must register STARTUP bindings");
    }
}
