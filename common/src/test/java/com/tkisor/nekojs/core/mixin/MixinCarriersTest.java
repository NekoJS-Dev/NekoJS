package com.tkisor.nekojs.core.mixin;

import com.google.gson.Gson;
import com.tkisor.nekojs.platform.IPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class MixinCarriersTest {
    @TempDir Path root;
    private static final MixinPlan.Profile PROFILE = new MixinPlan.Profile("26.1.2", "neoforge", "26.1.2.71", "runtime", "client");
    private static MixinPlan plan(String hash) {
        return new MixinPlan(1, PROFILE, hash.repeat(64), List.of(new MixinPlan.Hook("demo:health", "demo.Target", "health", "()F", "RETURN", true, "SERVER", "SERVER_MAIN", 1, false)));
    }
    private URI write(MixinPlan plan, String name) throws Exception {
        Path file = root.resolve(name); Files.writeString(file, new Gson().toJson(plan)); return file.toUri();
    }
    private static String owner(MixinPlan plan) { return "nekojs_mixins_" + plan.hash().substring(0, 16); }
    private static IPlatform platform(boolean development, String minecraft, Map<String, URI> admitted) {
        return (IPlatform) Proxy.newProxyInstance(IPlatform.class.getClassLoader(), new Class<?>[]{IPlatform.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isDevelopment" -> development;
            case "isClient" -> true;
            case "getMcVersion" -> minecraft;
            case "getLoaderId" -> "neoforge";
            case "getLoaderVersion" -> "26.1.2.71";
            case "visitLoadedMixinPlans" -> {
                @SuppressWarnings("unchecked") var visitor = (BiConsumer<String, URI>) args[0];
                admitted.forEach(visitor); yield null;
            }
            default -> throw new AssertionError("Unexpected platform access: " + method.getName());
        });
    }
    @Test void onlyAdmittedExactDevelopmentPlansBecomeVisible() throws Exception {
        var plan = plan("a"); URI uri = write(plan, "admitted.json"); var admitted = Map.of(owner(plan), uri);
        assertEquals(List.of(plan), MixinCarriers.discover(platform(true, "26.1.2", admitted)));
        assertThrows(IllegalArgumentException.class, () -> MixinCarriers.discover(platform(false, "26.1.2", admitted)));
        assertThrows(IllegalArgumentException.class, () -> MixinCarriers.discover(platform(true, "26.2.0", admitted)));
        assertThrows(IllegalArgumentException.class, () -> MixinCarriers.discover(platform(true, "26.1.2", Map.of("unrelated", uri))));
        assertTrue(MixinCarriers.discover(platform(false, "26.1.2", Map.of())).isEmpty(), "A pending file is not a loader admission");
        var other = plan("b"); URI duplicate = write(other, "duplicate.json");
        assertThrows(IllegalArgumentException.class, () -> MixinCarriers.discover(platform(true, "26.1.2", Map.of(owner(plan), uri, owner(other), duplicate))));
    }
    @Test void archiveDiscoveryClosesOnlyItsOwnFilesystem() throws Exception {
        var plan = plan("c"); Path jar = root.resolve("carrier.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("META-INF/nekojs/mixin-plan.json"));
            output.write(new Gson().toJson(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.closeEntry();
        }
        URI filesystem = URI.create("jar:" + jar.toUri());
        URI uri = URI.create(filesystem + "!/META-INF/nekojs/mixin-plan.json");
        var platform = platform(true, "26.1.2", Map.of(owner(plan), uri));
        assertEquals(List.of(plan), MixinCarriers.discover(platform));
        assertThrows(FileSystemNotFoundException.class, () -> FileSystems.getFileSystem(filesystem));
        try (var borrowed = FileSystems.newFileSystem(filesystem, Map.of())) {
            assertEquals(List.of(plan), MixinCarriers.discover(platform)); assertTrue(borrowed.isOpen());
        }
    }
}
