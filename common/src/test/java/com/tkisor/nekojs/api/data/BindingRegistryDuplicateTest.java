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
 * 重复注册行为回归：同名 binding 二次注册必须被拒绝（返回 false、保留首个，首胜语义），
 * 不同名正常注册返回 true。被拒时会打 warn 日志（logger "nekojs.bootstrap"），
 * 但本测试不捕获日志，只断言注册行为本身。
 */
class BindingRegistryDuplicateTest {

    @BeforeAll
    static void initPlatform() {
        // ScriptType 枚举常量初始化依赖 NekoJSPaths -> Platform，必须先初始化测试平台
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
        // TypedBinding 显式携带 valueType，同样按名字去重
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
