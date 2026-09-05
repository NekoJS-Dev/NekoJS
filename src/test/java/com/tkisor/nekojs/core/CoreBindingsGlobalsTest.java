//? if neoforge {
package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingRegistry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * kjs-wrapper-parity（spec docs/kjs-wrapper-parity.md）补的全局：注册进 {@code SERVER}
 * 绑定集后必须出现在已知全局集合里——preflight 的"未定义标识符"检查以该集合为事实源，
 * 漏注册会让脚本作者写对名字也报"未定义"。
 */
class CoreBindingsGlobalsTest {

    @Test
    void wrapperParityGlobalsAreRegistered() {
        BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER);

        new NekoJSCorePlugin().registerBinding(registry);

        for (String name : List.of("KMath", "JavaMath", "TextIcons", "DamageSource", "ParticleOptions", "DataMap")) {
            assertTrue(registry.viewRegistered().containsKey(name), "'" + name + "' 应注册为脚本全局");
        }
    }

    @Test
    void parityGlobalsDoNotShadowExistingNames() {
        BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER);

        new NekoJSCorePlugin().registerBinding(registry);

        Set<String> names = registry.viewRegistered().keySet();
        // 既有全局不被 parity 工作破坏——首胜语义下同名注册会静默挤掉后来者。
        // （Text/NBT/Registry 是 managed facade 全局，不经 registerBinding，不在断言之列）
        for (String existing : List.of("Item", "Block", "Utils", "Color", "Direction")) {
            assertTrue(names.contains(existing), "'" + existing + "' 是既有全局，不应被 parity 工作破坏");
        }
    }
}
//?}
