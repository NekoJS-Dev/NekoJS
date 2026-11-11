package com.tkisor.nekujs;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/** Checks selected loader compatibility against required ranges in the unchanged fixture metadata. */
@Tag("expanded-profile")
class RuntimeDependencyVersionTest {
    @Test
    void selectedNeoForgeSatisfiesOriginalKeyBindJSRequirement() throws Exception {
        assertSelectedNeoForge("KeyBindJS-2101-1.1.0.jar", "keybindjs", "[21.1.233,)");
    }

    @Test
    void selectedNeoForgeSatisfiesOriginalJeiRequirement() throws Exception {
        assertSelectedNeoForge("jei-1.21.1-neoforge-19.70.0.469.jar", "jei", "[21.1.238,)");
    }

    @Test
    void packagedProviderSatisfiesOriginalEnderIORequirement() throws Exception {
        Path source = Path.of(System.getProperty("nekujs.fixtures.dir")).resolve("kubejs_enderio-neoforge-1.21.1-0.13.0.jar");
        String required;
        try (var jar = new JarFile(source.toFile()); var input = jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml"))) {
            var config = new TomlParser().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            List<UnmodifiableConfig> dependencies = config.get(List.of("dependencies", "kubejs_enderio"));
            required = dependencies.stream().filter(dependency -> "kubejs".equals(dependency.get("modId")))
                    .map(dependency -> (String) dependency.get("versionRange")).findFirst().orElseThrow();
        }
        assertEquals("[2101.7.2-build.309,)", required);
        String provided;
        try (var jar = new JarFile(System.getProperty("nekujs.test.jar")); var input = jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml"))) {
            var config = new TomlParser().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            List<UnmodifiableConfig> mods = config.get("mods");
            provided = mods.stream().filter(mod -> "kubejs".equals(mod.get("modId")))
                    .map(mod -> (String) mod.get("version")).findFirst().orElseThrow();
        }
        assertTrue(VersionRange.createFromVersionSpec(required).containsVersion(new DefaultArtifactVersion(provided)),
                "Packaged provider " + provided + " does not satisfy original EnderIO " + required);
    }

    private static void assertSelectedNeoForge(String file, String modId, String expectedRange) throws Exception {
        Path source = Path.of(System.getProperty("nekujs.fixtures.dir")).resolve(file);
        assertTrue(Files.isRegularFile(source), "Missing dependency fixture: " + source);
        try (var jar = new JarFile(source.toFile())) {
            var metadata = jar.getJarEntry("META-INF/neoforge.mods.toml");
            assertNotNull(metadata, "Missing native mod metadata: " + file);
            String text;
            try (var input = jar.getInputStream(metadata)) {
                text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            var config = new TomlParser().parse(text);
            List<UnmodifiableConfig> dependencies = config.get(List.of("dependencies", modId));
            assertNotNull(dependencies, "Missing dependency declarations: " + file);
            var loaderDependencies = dependencies.stream()
                    .filter(dependency -> "neoforge".equals(dependency.get("modId"))).toList();
            assertEquals(1, loaderDependencies.size(), "Expected one NeoForge dependency: " + file);
            var dependency = loaderDependencies.getFirst();
            assertEquals("required", dependency.get("type"), file);
            String range = dependency.get("versionRange");
            assertEquals(expectedRange, range, "Original fixture dependency range changed: " + file);
            String selected = System.getProperty("nekujs.neo.version");
            assertNotNull(selected, "Fixture task must supply the selected NeoForge version as nekujs.neo.version");
            assertFalse(selected.isBlank(), "Selected NeoForge version must not be blank");
            assertTrue(VersionRange.createFromVersionSpec(range).containsVersion(new DefaultArtifactVersion(selected)),
                    () -> file + " requires NeoForge " + range + "; selected NeoForge is " + selected);
        }
    }
}
