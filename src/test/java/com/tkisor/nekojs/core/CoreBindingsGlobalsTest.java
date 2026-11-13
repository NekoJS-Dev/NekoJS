//? if neoforge {
package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingRegistry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Globals added for wrapper parity: once registered into the {@code SERVER} binding set they must
 * appear in the known-globals set. The preflight undefined-identifier check uses that set as its
 * source of truth, so a missing registration reports "undefined" even when the script author spells
 * the name correctly.
 */
class CoreBindingsGlobalsTest {

    @Test
    void wrapperParityGlobalsAreRegistered() {
        BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER).scopedTo("nekojs");

        new NekoJSCorePlugin().registerBinding(registry);

        for (String name : List.of("KMath", "JavaMath", "TextIcons", "DamageSource", "ParticleOptions", "DataMap")) {
            assertTrue(registry.viewRegistered().containsKey(name), "'" + name + "' 应注册为脚本全局");
        }
    }

    @Test
    void parityGlobalsDoNotShadowExistingNames() {
        BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER).scopedTo("nekojs");

        new NekoJSCorePlugin().registerBinding(registry);

        Set<String> names = registry.viewRegistered().keySet();
        // The existing globals survive the parity work: with first-wins, a second registration under the
        // same name silently pushes the first one out. (Text, NBT and Registry are managed facade
        // globals registered outside registerBinding, so they are not asserted here.)
        for (String existing : List.of("Item", "Block", "Utils", "Color", "Direction")) {
            assertTrue(names.contains(existing), "'" + existing + "' 是既有全局，不应被 parity 工作破坏");
        }
    }
}
//?}
