// fabric has no matching implementation yet; remove this whole-file guard once the port lands
//? if neoforge {
package com.tkisor.nekojs.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard: every {@code *Extension} host injection interface must actually be applied, either by a
 * mixin listed in nekojs.mixins.json or by an entry in nekojs.interface_injection.json
 * (two paths on 26.x: interface injection covers dev, mixins cover production).
 *
 * <p>Add an Extension interface without configuring a mixin or injection entry and calls from
 * scripts fail silently for the whole class. Compilation stays green, the probe keeps generating
 * declarations, and the failure only shows up as {@code NoSuchMethod} at runtime. The interface
 * list is enumerated from the classes directory on the test classpath, independent of the source
 * tree layout.
 */
class ExtensionCoverageTest {

    private static final String INJECT_PACKAGE = "com.tkisor.nekojs.api.inject";

    @Test
    void everyExtensionIsAppliedByMixinOrInterfaceInjection() throws Exception {
        Set<String> extensions = enumerateExtensions();
        assertFalse(extensions.isEmpty(), "no *Extension classes found - enumeration is broken");

        Set<String> covered = new HashSet<>();
        for (String config : List.of("nekojs.mixins.json")) {
            collectMixinInterfaces(config, covered);
        }
        collectInterfaceInjection(covered);

        Set<String> missing = new HashSet<>(extensions);
        missing.removeAll(covered);
        assertTrue(missing.isEmpty(),
                "Extension interfaces not applied by any mixin or interface_injection entry: " + missing
                        + " (covered: " + covered + ")");
    }

    /**
     * {@code *Extension.class} files in the inject package directory on the classpath (merged compile
     * output, not the source tree layout).
     */
    private static Set<String> enumerateExtensions() throws Exception {
        Set<String> out = new HashSet<>();
        ClassLoader loader = ExtensionCoverageTest.class.getClassLoader();
        for (var url : Collections.list(loader.getResources(INJECT_PACKAGE.replace('.', '/')))) {
            if (!"file".equalsIgnoreCase(url.getProtocol())) continue;
            Path dir = Path.of(url.toURI());
            try (var files = Files.list(dir)) {
                files.filter(f -> f.getFileName().toString().endsWith("Extension.class"))
                        .forEach(f -> out.add(INJECT_PACKAGE + "."
                                + f.getFileName().toString().replace(".class", "")));
            }
        }
        return out;
    }

    /**
     * Inject-package interfaces implemented by mixin classes count as mixin coverage (the agent only
     * rewrites annotations, the implements clause is untouched).
     */
    private static void collectMixinInterfaces(String configName, Set<String> covered) throws Exception {
        JsonObject root = readConfig(configName);
        String pkg = root.get("package").getAsString();
        ClassLoader loader = ExtensionCoverageTest.class.getClassLoader();
        for (String section : List.of("mixins", "client", "server")) {
            JsonElement sectionEl = root.get(section);
            if (sectionEl == null) continue;
            JsonArray entries = sectionEl.getAsJsonArray();
            for (JsonElement e : entries) {
                Class<?> mixin = Class.forName(pkg + "." + e.getAsString(), false, loader);
                for (Class<?> iface : mixin.getInterfaces()) {
                    if (iface.getName().startsWith(INJECT_PACKAGE + ".")) {
                        covered.add(iface.getName());
                    }
                }
            }
        }
    }

    /** The values of interface_injection.json count as injection coverage (only 26.x ships this resource). */
    private static void collectInterfaceInjection(Set<String> covered) throws Exception {
        InputStream in = ExtensionCoverageTest.class.getClassLoader()
                .getResourceAsStream("nekojs.interface_injection.json");
        if (in == null) return;
        JsonObject root;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (var entry : root.entrySet()) {
            for (JsonElement iface : entry.getValue().getAsJsonArray()) {
                covered.add(iface.getAsString().replace('/', '.'));
            }
        }
    }

    private static JsonObject readConfig(String resource) throws Exception {
        InputStream in = ExtensionCoverageTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(in, resource + " must be on the test classpath");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
//?}
