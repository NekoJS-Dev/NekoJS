package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for duplicate registration: registering a second binding with the same name is
 * rejected (returns false, the first binding stays, first one wins), while a differently named
 * binding is registered and returns true. A rejection logs a warning under the logger
 * "nekojs.bootstrap", but this test does not capture logs and asserts only the registration result.
 */
class BindingRegistryDuplicateTest {

    @BeforeAll
    static void initPlatform() {
        // Initializing the ScriptType enum constants needs NekoJSPaths -> Platform, so the test platform
        // must be set up first
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void duplicateNameSecondRegistrationRejectedAndFirstWins() {
        var reg = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER);
        var first = Binding.of("dup", "first");
        var second = Binding.of("dup", 42);

        assertTrue(reg.register(first), "首次注册必须成功");
        assertFalse(reg.register(second), "同名二次注册必须被拒绝（首胜）");

        var view = reg.viewRegistered();
        assertEquals(1, view.size(), "被拒的重复注册不应新增条目");
        assertSame(first, view.get("dup"), "必须保留首个 binding，不得被后者覆盖");
        assertEquals("first", view.get("dup").value());
        assertEquals(String.class, view.get("dup").valueType());
    }

    @Test
    void duplicateTypedBindingAlsoRejectedByName() {
        var reg = new BindingRegistry.BindingRegistryImpl(ScriptType.TEST);
        var first = Binding.of("probe", new Object());
        // TypedBinding carries valueType explicitly, but is still deduplicated by name
        var second = Binding.of("probe", new Object(), Runnable.class);

        assertTrue(reg.register(first));
        assertFalse(reg.register(second), "TypedBinding 同名也必须被拒绝");
        assertEquals(Object.class, reg.viewRegistered().get("probe").valueType(),
                "显式 valueType 的后者不得生效");
    }

    @Test
    void distinctNamesAllRegister() {
        var reg = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER);

        assertTrue(reg.register(Binding.of("a", 1)), "不同名注册应成功");
        assertTrue(reg.register(Binding.of("b", 2)), "不同名注册应成功");
        assertEquals(2, reg.viewRegistered().size());
    }
}
