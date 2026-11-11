package com.tkisor.nekujs.bridge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.tkisor.nekujs.plugin.LegacyPluginDiscovery;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.DataComponentTypeInfoRegistry;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRuntimeLifecycleTest {
    @Test
    void descriptionsReadNativeDeferredValuesOnlyAfterRegistryBinding() {
        var registry = new MappedRegistry<DataComponentType<?>>(Registries.DATA_COMPONENT_TYPE, Lifecycle.stable(), false);
        var id = ResourceLocation.fromNamespaceAndPath("mekanism", "module_container");
        var holder = new LocalDeferredHolder(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, id), registry);
        var unavailable = assertThrows(NullPointerException.class, holder::get);
        assertEquals("Trying to access unbound value: ResourceKey[minecraft:data_component_type / mekanism:module_container]", unavailable.getMessage());
        var phases = new ArrayList<String>();
        KubeJSPlugin plugin = new KubeJSPlugin() {
            @Override public void registerEvents(dev.latvian.mods.kubejs.event.EventGroupRegistry ignored) { phases.add("events"); }
            @Override public void registerBuilderTypes(dev.latvian.mods.kubejs.registry.BuilderTypeRegistry ignored) { phases.add("builders"); }
            @Override public void registerBindings(dev.latvian.mods.kubejs.script.BindingRegistry registry) {
                registry.add("LifecycleBinding", "early");
                phases.add("bindings");
            }
            @Override public void init() { phases.add("init"); }
            @Override public void initStartup() { phases.add("startup"); }
            @Override public void registerDataComponentTypeDescriptions(DataComponentTypeInfoRegistry descriptions) {
                descriptions.register(holder.get(), TypeInfo.of(String.class));
                phases.add("descriptions");
            }
            @Override public void afterInit() { phases.add("afterInit"); }
        };
        var bridge = bridge(plugin);
        var bindings = new com.tkisor.nekojs.api.data.BindingRegistry.BindingRegistryImpl(com.tkisor.nekojs.api.ScriptType.STARTUP);
        bridge.registerBindings(bindings);
        bridge.init();
        bridge.initStartup();
        assertEquals("early", bindings.viewRegistered().get("LifecycleBinding").value());
        assertEquals(List.of("events", "builders", "bindings", "init", "startup"), phases);
        assertFalse(holder.isBound());
        var component = DataComponentType.<String>builder().persistent(Codec.STRING).build();
        Registry.register(registry, id, component);
        assertSame(component, holder.get());
        bridge.afterInit();
        assertEquals(List.of("events", "builders", "bindings", "init", "startup", "descriptions", "afterInit"), phases);
    }

    @Test
    void everyDescriptionHookPrecedesEveryLegacyAfterInitHook() {
        var phases = new ArrayList<String>();
        KubeJSPlugin first = new KubeJSPlugin() {
            @Override public void registerDataComponentTypeDescriptions(DataComponentTypeInfoRegistry registry) { phases.add("firstDescriptions"); }
            @Override public void afterInit() { phases.add("firstAfterInit"); }
        };
        KubeJSPlugin second = new KubeJSPlugin() {
            @Override public void registerDataComponentTypeDescriptions(DataComponentTypeInfoRegistry registry) { phases.add("secondDescriptions"); }
            @Override public void afterInit() { phases.add("secondAfterInit"); }
        };
        var bridge = new LegacyRuntimeBridge(List.of(owned(first), owned(second)));
        assertTrue(phases.isEmpty());
        bridge.afterInit();
        assertEquals(List.of("firstDescriptions", "secondDescriptions", "firstAfterInit", "secondAfterInit"), phases);
    }

    @Test
    void genuineDescriptionFailurePreservesCauseAndDoesNotAdvanceAfterInit() {
        var failure = new IllegalStateException("fixture registration failure");
        var phases = new ArrayList<String>();
        var bridge = bridge(new KubeJSPlugin() {
            @Override public void registerDataComponentTypeDescriptions(DataComponentTypeInfoRegistry registry) { throw failure; }
            @Override public void afterInit() { phases.add("afterInit"); }
        });
        var reported = assertThrows(IllegalStateException.class, bridge::afterInit);
        assertSame(failure, reported.getCause());
        assertTrue(reported.getMessage().contains("[NEKO-4200]"));
        assertTrue(reported.getMessage().contains("hook=registerDataComponentTypeDescriptions"));
        assertTrue(phases.isEmpty());
    }

    @Test
    void duplicateDescriptionsRemainAnExplicitRegistrationFailure() {
        var delegate = DataComponentType.<String>builder().persistent(Codec.STRING).build();
        DataComponentType<String> component = new DataComponentType<>() {
            @Override public Codec<String> codec() { return delegate.codec(); }
            @Override public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, String> streamCodec() {
                return delegate.streamCodec();
            }
            // A local type has no global registry name; keep this diagnostic independent of global bootstrap.
            @Override public String toString() { return "lifecycle_fixture:duplicate"; }
        };
        var bridge = bridge(new KubeJSPlugin() {
            @Override public void registerDataComponentTypeDescriptions(DataComponentTypeInfoRegistry registry) {
                registry.register(component, TypeInfo.of(String.class));
                registry.register(component, TypeInfo.of(Integer.class));
            }
        });
        var reported = assertThrows(IllegalStateException.class, bridge::afterInit);
        assertInstanceOf(IllegalArgumentException.class, reported.getCause(), () -> {
            var trace = new java.io.StringWriter();
            reported.printStackTrace(new java.io.PrintWriter(trace));
            return trace.toString();
        });
        assertTrue(reported.getCause().getMessage().contains("[NEKO-4265]"));
    }

    private static LegacyPluginDiscovery.LoadedPlugin<KubeJSPlugin> owned(KubeJSPlugin plugin) {
        return new LegacyPluginDiscovery.LoadedPlugin<>(new LegacyPluginDiscovery.Source(
                "lifecycle_fixture", Path.of(".")), plugin.getClass().getName(), plugin);
    }

    private static LegacyRuntimeBridge bridge(KubeJSPlugin plugin) {
        return new LegacyRuntimeBridge(List.of(owned(plugin)));
    }

    /** Borrows a local real registry; all holder binding and value access remain NeoForge's implementation. */
    private static final class LocalDeferredHolder extends DeferredHolder<DataComponentType<?>, DataComponentType<String>> {
        private final Registry<DataComponentType<?>> registry;
        private LocalDeferredHolder(ResourceKey<DataComponentType<?>> key, Registry<DataComponentType<?>> registry) {
            super(key);
            this.registry = registry;
        }
        @Override protected Registry<DataComponentType<?>> getRegistry() { return registry; }
    }
}
