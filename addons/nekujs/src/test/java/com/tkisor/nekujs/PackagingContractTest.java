package com.tkisor.nekujs;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class PackagingContractTest {
    @Test
    void packagesAnIndependentAddonWithoutEngineAuditOrExperimentalAbiClasses() throws Exception {
        try (var jar = new JarFile(System.getProperty("nekujs.test.jar"))) {
            assertNotNull(jar.getJarEntry("com/tkisor/nekujs/NekuJSMod.class"));
            assertNotNull(jar.getJarEntry("com/tkisor/nekujs/NekuJSPlugin.class"));
            assertNotNull(jar.getJarEntry("com/tkisor/nekujs/KubeJSProvider.class"));
            assertNotNull(jar.getJarEntry("com/tkisor/nekujs/RhinoProvider.class"));
            assertNotNull(jar.getJarEntry("dev/latvian/mods/kubejs/plugin/KubeJSPlugin.class"));
            assertNotNull(jar.getJarEntry("dev/latvian/mods/rhino/Context.class"));
            assertNull(jar.getJarEntry("dev/latvian/mods/rhino/ScriptRuntime.class"));
            var entries = jar.stream().map(entry -> entry.getName()).toList();
            for (String prefix : new String[]{"com/tkisor/nekojs/", "graal/", "org/junit/",
                    "com/tkisor/nekujs/audit/", "com/tkisor/nekujs/proof/",
                    "net/neoforged/"}) {
                assertFalse(entries.stream().anyMatch(name -> name.startsWith(prefix)), prefix);
            }
            try (var input = jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml"))) {
                var metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(metadata.contains("$" + "{"));
                assertTrue(metadata.contains("""
                        modId = "kubejs"
                        """.strip()));
                assertTrue(metadata.contains("""
                        version = "2101.7.2-build.309"
                        """.strip()));
                assertTrue(metadata.contains("""
                        modId = "rhino"
                        """.strip()));
                assertTrue(metadata.contains("""
                        modId = "nekujs"
                        """.strip()));
                assertTrue(metadata.contains("""
                        modId = "nekojs"
                        """.strip()));
                assertTrue(metadata.contains("""
                        versionRange = "[1.21.1,1.21.2)"
                        """.strip()));
            }
        }
    }
}
