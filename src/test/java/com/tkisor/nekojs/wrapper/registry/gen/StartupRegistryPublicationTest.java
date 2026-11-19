package com.tkisor.nekojs.wrapper.registry.gen;

import com.mojang.serialization.Lifecycle;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.api.ModuleEventGroupProxy;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupRegistryPublicationTest {
    private static final ResourceKey<Registry<String>> MACHINES = key("fixture:machines");
    private static final ResourceKey<Registry<String>> OTHER_MACHINES = key("other:machines");
    private static final ResourceKey<Registry<String>> CLOSED_MACHINES = key("closed:machines");

    @Test
    void versionedStartupPublicationRequiresOneOpenSelectorAndOrdersAliasesByPriority() {
        var plugins = StartupRegistryScriptFixture.bootstrap(new NekoRegistryPointsPlugin());
        assertFalse(plugins.moduleEventGroups().stream().anyMatch(group -> group.group().name().equals("RegistryEvents")));
        assertEquals(List.of("nekojs:event/versioned"), plugins.moduleEventGroups().stream()
                .filter(group -> group.group().name().equals("StartupEvents"))
                .map(group -> group.moduleId()).toList());

        try (var script = new StartupRegistryScriptFixture(plugins)) {
            assertEquals(Set.of("registry"), script.publication.events());
            for (ScriptType type : List.of(ScriptType.SERVER, ScriptType.CLIENT, ScriptType.TEST)) {
                assertFalse(script.publication.available(type));
                assertFalse(new ModuleEventGroupProxy(script.context, script.publication, type).hasMember("registry"));
            }
            assertSubscriptionRejected(script, "event => {}", "requires a selector");
            assertSubscriptionRejected(script, "['item', 'block'], event => {}", "Invalid event selector");
            assertSubscriptionRejected(script, "null, event => {}", "requires a selector");
            assertSubscriptionRejected(script, "'item', event => {}, 'extra'", "Invalid event selector");
            assertSubscriptionRejected(script, "'BAD space', event => {}", "Invalid STARTUP registry selector");
            assertSubscriptionRejected(script, "'minecraft:missing_registry', event => {}", "is not open for STARTUP registration");

            var runtime = new StartupRegistryRuntime("startup-publication");
            script.context.eval("js", """
                    let itemOrder = [];
                    let blockCallbacks = 0;
                    StartupEvents.registry('LOW', 'item', event => { itemOrder.push('short'); });
                    StartupEvents.registry('minecraft:item', event => { itemOrder.push('full'); });
                    StartupEvents.registry('HIGH', 'item', event => {
                        itemOrder.push('high');
                        event.create('fixture:bound_to_item', build => {});
                    });
                    StartupEvents.registry('block', event => { blockCallbacks++; });
                    """);
            runtime.collectOnce();
            runtime.collectOnce();
            assertEquals("[\"high\",\"full\",\"short\"]", script.context.eval("js", "JSON.stringify(itemOrder)").asString());
            assertEquals(1, script.context.eval("js", "blockCallbacks").asInt());
            assertEquals(List.of(Identifier.parse("fixture:bound_to_item")), runtime.undrainedLiveView().get(Registries.ITEM));
            assertFalse(runtime.undrainedLiveView().containsKey(Registries.BLOCK));
        }
    }

    @Test
    void pluginMustOpenAModRegistryAndItsOwnFactoriesDoNotLeakAcrossNamespaces() {
        var machines = new MappedRegistry<>(MACHINES, Lifecycle.stable(), false);
        var other = new MappedRegistry<>(OTHER_MACHINES, Lifecycle.stable(), false);
        var closed = new MappedRegistry<>(CLOSED_MACHINES, Lifecycle.stable(), false);
        var preexisting = Identifier.parse("fixture:preexisting");
        Registry.register(closed, preexisting, "native");
        assertEquals(Set.of(preexisting), closed.keySet());
        assertEquals(List.of("native"), closed.stream().toList());
        try (var script = new StartupRegistryScriptFixture(new NekoRegistryPointsPlugin(), new MachineRegistries())) {
            assertSubscriptionRejected(script, "'closed:machines', event => {}", "is not open for STARTUP registration");
            assertSubscriptionRejected(script, "'machines', event => {}", "is not open for STARTUP registration");
            script.context.eval("js", """
                    let failure;
                    let builderless;
                    StartupEvents.registry('fixture:machines', event => {
                        try { event.create('entries:missing_default', build => {}); }
                        catch (error) { failure = String(error); }
                        event.create('entries:shared', 'named', build => {});
                    });
                    StartupEvents.registry('other:machines', event => {
                        builderless = typeof event.create === 'undefined' && typeof event.createCustom === 'function';
                        event.createCustom('entries:shared', () => 'from supplier');
                    });
                    """);
            var runtime = new StartupRegistryRuntime("startup-publication");
            runtime.collectOnce();
            assertTrue(script.context.eval("js", "builderless").asBoolean());
            assertTrue(script.context.eval("js", "failure.includes('has no default type')").asBoolean());
            assertEquals(List.of(Identifier.parse("entries:shared")), runtime.undrainedLiveView().get(MACHINES));
            assertEquals(List.of(Identifier.parse("entries:shared")), runtime.undrainedLiveView().get(OTHER_MACHINES));

            assertTrue(runtime.drainFor(MACHINES, (target, id, supplier) ->
                    Registry.register(machines, id, (String) supplier.get())).errors().isEmpty());
            assertTrue(runtime.drainFor(OTHER_MACHINES, (target, id, supplier) ->
                    Registry.register(other, id, (String) supplier.get())).errors().isEmpty());
            assertEquals(Set.of(Identifier.parse("entries:shared")), machines.keySet());
            assertEquals(Set.of(Identifier.parse("entries:shared")), other.keySet());
            assertEquals(List.of("from builder"), machines.stream().toList());
            assertEquals(List.of("from supplier"), other.stream().toList());
            assertTrue(runtime.isFullyDrained());
        }
    }

    private static void assertSubscriptionRejected(StartupRegistryScriptFixture script, String args, String message) {
        var failure = assertThrows(PolyglotException.class,
                () -> script.context.eval("js", "StartupEvents.registry(" + args + ");"));
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }

    private static ResourceKey<Registry<String>> key(String id) {
        return ResourceKey.createRegistryKey(Identifier.parse(id));
    }

    private static final class MachineRegistries implements RegistryInfosPoint.Contributor, RegistryTypesPoint.Contributor {
        @Override
        public void registerRegistryInfos(RegistryInfosPoint.RegistryInfosCollector collector) {
            collector.openStartupRegistry(MACHINES);
            collector.openStartupRegistry(OTHER_MACHINES);
            collector.addInfo(new RegistryInfo(CLOSED_MACHINES, String.class));
        }

        @Override
        public void registerRegistryTypes(RegistryTypesPoint.RegistryTypesCollector collector) {
            collector.registerType(MACHINES, "named", MachineBuilder.class, MachineBuilder::new);
        }
    }

    public static final class MachineBuilder extends RegistryObjectBuilder<String> {
        MachineBuilder(Identifier id) { super(id); }
        @Override public String build() { return "from builder"; }
    }
}
