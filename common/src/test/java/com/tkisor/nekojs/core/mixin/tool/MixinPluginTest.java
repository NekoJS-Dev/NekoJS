package com.tkisor.nekojs.core.mixin.tool;

import com.tkisor.nekojs.core.mixin.MixinPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class MixinPluginTest {
    @TempDir Path root;
    private static final String PLUGIN = "demo/BootstrapPlugin";
    private static final String GUARD = "com/tkisor/nekojs/mixinjs/MixinBootstrapGuard";
    private static final String INTERFACE = "org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin";
    private static final MixinPlan.Profile PROFILE = new MixinPlan.Profile("26.1.2", "neoforge", "26.1.2.71", "runtime", "client");

    @Test void missingNekoJSDisablesThePluginWithoutResolvingTheRuntimeBridge() throws Exception {
        var plugin = plugin(null);
        assertFalse(applies(plugin));
        plugin.getClass().getMethod("onLoad", String.class).invoke(plugin, "demo");
        assertFalse(applies(plugin));
    }

    @Test void nativeGuardResultControlsEveryInjection() throws Exception {
        for (boolean enabled : List.of(false, true)) {
            var plugin = plugin(guard(enabled));
            assertFalse(applies(plugin));
            plugin.getClass().getMethod("onLoad", String.class).invoke(plugin, "demo");
            assertEquals(enabled, applies(plugin));
        }
    }

    @Test void olderNeoForgeAndFabricArtifactsRequireNekoJS() throws Exception {
        for (var profile : List.of(
                new MixinPlan.Profile("1.21.1", "neoforge", "21.1.227", "runtime", "client"),
                new MixinPlan.Profile("26.1.2", "fabric", "0.19.3", "runtime", "client"))) {
            var plan = new MixinPlan(1, profile, "b".repeat(64), List.of());
            Path output = root.resolve(profile.loader() + ".jar");
            MixinCarrierWriter.write(output, plan, Map.of());
            try (var jar = new JarFile(output.toFile())) {
                assertNull(jar.getManifest().getMainAttributes().getValue("FMLModType"));
                assertNotNull(jar.getEntry("nekojs/generated/mixins/p" + plan.hash() + "/BootstrapPlugin.class"));
                if (profile.loader().equals("neoforge")) {
                    String toml = text(jar, "META-INF/neoforge.mods.toml");
                    assertTrue(toml.contains("modId=\"nekojs\"\ntype=\"required\""));
                    assertTrue(toml.contains("versionRange=\"[1.21.1]\""));
                } else {
                    var metadata = com.google.gson.JsonParser.parseString(text(jar, "fabric.mod.json")).getAsJsonObject();
                    assertEquals("*", metadata.getAsJsonObject("depends").get("nekojs").getAsString());
                }
            }
        }
    }

    private static String text(JarFile jar, String name) throws Exception {
        try (var input = jar.getInputStream(jar.getEntry(name))) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private static boolean applies(Object plugin) throws Exception {
        return (boolean) plugin.getClass().getMethod("shouldApplyMixin", String.class, String.class).invoke(plugin, "demo.Target", "demo.Hook");
    }
    private static Object plugin(byte[] guard) throws Exception {
        var contract = new ClassWriter(0);
        contract.visit(V21, ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE, INTERFACE, null, "java/lang/Object", null);
        contract.visitEnd();
        var classes = new HashMap<String, byte[]>();
        classes.put(INTERFACE.replace('/', '.'), contract.toByteArray());
        // Reflection resolves all method descriptors, including the unused transformation arguments.
        for (String name : List.of("org/objectweb/asm/tree/ClassNode", "org/spongepowered/asm/mixin/extensibility/IMixinInfo")) {
            var parameter = new ClassWriter(0);
            parameter.visit(V21, ACC_PUBLIC, name, null, "java/lang/Object", null);
            parameter.visitEnd(); classes.put(name.replace('/', '.'), parameter.toByteArray());
        }
        classes.put(PLUGIN.replace('/', '.'), MixinPluginWriter.write(PLUGIN, new MixinPlan(1, PROFILE, "b".repeat(64), List.of()), false));
        if (guard != null) classes.put(GUARD.replace('/', '.'), guard);
        var loader = new ClassLoader(null) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return loader.loadClass(PLUGIN.replace('/', '.')).getConstructor().newInstance();
    }
    private static byte[] guard(boolean enabled) {
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC, GUARD, null, "java/lang/Object", null);
        var method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "active", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Z", null, null);
        method.visitCode(); method.visitInsn(enabled ? ICONST_1 : ICONST_0); method.visitInsn(IRETURN);
        method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd(); return writer.toByteArray();
    }
}
