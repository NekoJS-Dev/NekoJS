package com.tkisor.nekujs;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Lifecycle;
import dev.latvian.mods.kubejs.holder.NamespaceHolderSet;
import dev.latvian.mods.kubejs.holder.RegExHolderSet;
import dev.latvian.mods.kubejs.util.RegExpKJS;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class HolderSetNativeBridgeTest {
    private static final ResourceKey<Registry<String>> KEY = ResourceKey.createRegistryKey(ResourceLocation.parse("test:holders"));

    private static RegistryAccess.Frozen access() {
        var registry = new MappedRegistry<String>(KEY, Lifecycle.stable(), false);
        Registry.register(registry, ResourceLocation.parse("alpha:iron_ingot"), "iron");
        Registry.register(registry, ResourceLocation.parse("alpha:gold_ingot"), "gold");
        Registry.register(registry, ResourceLocation.parse("beta:stone"), "stone");
        registry.freeze();
        return new RegistryAccess.ImmutableRegistryAccess(List.of(registry)).freeze();
    }

    @Test void exactPublicConstructorDescriptorsAndNativeNamespaceMatching() throws Exception {
        assertNotNull(NamespaceHolderSet.class.getConstructor(HolderLookup.RegistryLookup.class, String.class));
        assertNotNull(RegExHolderSet.class.getConstructor(HolderLookup.RegistryLookup.class, Pattern.class));
        var lookup = access().lookupOrThrow(KEY);
        var nativeSet = new com.tkisor.nekojs.holder.NamespaceHolderSet<>(lookup, "alpha");
        var bridged = new NamespaceHolderSet<>(lookup, "alpha");
        assertEquals(nativeSet.stream().toList(), bridged.stream().toList());
        assertEquals(2, bridged.size());
        Holder<String> iron = lookup.getOrThrow(ResourceKey.create(KEY, ResourceLocation.parse("alpha:iron_ingot")));
        Holder<String> stone = lookup.getOrThrow(ResourceKey.create(KEY, ResourceLocation.parse("beta:stone")));
        assertTrue(bridged.contains(iron));
        assertFalse(bridged.contains(stone));
        assertEquals(nativeSet.unwrap(), bridged.unwrap());
        assertSame(bridged.unwrap().right().orElseThrow(), bridged.unwrap().right().orElseThrow());
        assertTrue(bridged.unwrapKey().isEmpty());
        assertEquals(0, new NamespaceHolderSet<>(lookup, "absent").size());
    }

    @Test void regexBridgeUsesNativeFindSemanticsAndDirectUnwrap() {
        var lookup = access().lookupOrThrow(KEY);
        Pattern pattern = Pattern.compile("INGOT", Pattern.CASE_INSENSITIVE);
        var nativeSet = new com.tkisor.nekojs.holder.RegexHolderSet<>(lookup, pattern);
        var bridged = new RegExHolderSet<>(lookup, pattern);
        assertEquals(nativeSet.stream().toList(), bridged.stream().toList());
        assertEquals(2, bridged.size());
        for (var holder : lookup.listElements().toList()) assertEquals(nativeSet.contains(holder), bridged.contains(holder));
        assertEquals(nativeSet.unwrap(), bridged.unwrap());
        assertTrue(bridged.unwrapKey().isEmpty());
        assertEquals(0, new RegExHolderSet<>(lookup, Pattern.compile("missing")).size());
    }

    @Test void regexWrapperPreservesPatternsFlagsEscapesAndSyntaxFailures() {
        Pattern nativePattern = Pattern.compile("ingot");
        assertSame(nativePattern, RegExpKJS.wrap(nativePattern));
        assertNull(RegExpKJS.wrap("alpha:iron_ingot"));
        assertNull(RegExpKJS.wrap(new Object()));
        assertNull(RegExpKJS.wrap(null));
        Pattern literal = RegExpKJS.wrap("/alpha\\/ore/im");
        assertEquals("alpha/ore", literal.pattern());
        assertEquals(Pattern.CASE_INSENSITIVE | Pattern.MULTILINE, literal.flags());
        assertTrue(literal.matcher("ALPHA/ORE").find());
        assertThrows(PatternSyntaxException.class, () -> RegExpKJS.wrap("/[a-/"));
    }

    @Test @Tag("expanded-profile")
    void unchangedLootJsMixinConstructsBothNativeBridges() throws Exception {
        // RegistryAccessContainer also exposes built-in registry ops, so initialize the real Minecraft registries first.
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Path fixture = Path.of(System.getProperty("nekujs.fixtures.dir"), "lootjs-neoforge-1.21.1-3.7.0.jar");
        try (var loader = new URLClassLoader(new java.net.URL[]{fixture.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> mixin = Class.forName("com.almostreliable.lootjs.mixin.HolderSetCodecMixin", true, loader);
            assertSame(loader, mixin.getClassLoader());
            Object instance = mixin.getConstructor().newInstance();
            var registryKey = mixin.getDeclaredField("registryKey");
            registryKey.setAccessible(true);
            registryKey.set(instance, KEY);
            Class<?> opsClass = Class.forName("com.almostreliable.lootjs.kube.KubeOps", true, loader);
            Object ops = opsClass.getMethod("create", RegistryAccessContainer.class).invoke(null, new RegistryAccessContainer(access()));
            var namespace = mixin.getDeclaredMethod("lootjs$tryByNamespace", opsClass, Object.class);
            namespace.setAccessible(true);
            HolderSet<?> namespaced = (HolderSet<?>) namespace.invoke(instance, ops, "@alpha");
            assertInstanceOf(NamespaceHolderSet.class, namespaced);
            assertEquals(2, namespaced.size());
            var regex = mixin.getDeclaredMethod("lootjs$tryByRegex", opsClass, Object.class);
            regex.setAccessible(true);
            HolderSet<?> matched = (HolderSet<?>) regex.invoke(instance, ops, "/stone$/");
            assertInstanceOf(RegExHolderSet.class, matched);
            assertEquals(1, matched.size());
            assertNull(regex.invoke(instance, ops, "not_a_regex"));
        }
    }
}
