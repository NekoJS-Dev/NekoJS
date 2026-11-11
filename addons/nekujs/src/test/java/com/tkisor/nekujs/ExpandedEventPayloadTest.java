package com.tkisor.nekujs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/** Resolves original payload class contracts without initializing addons or creating game state. */
@Tag("expanded-profile")
class ExpandedEventPayloadTest {
    @Test
    void originalModifyJsStartupPayloadRetainsItsStartupEventSuperinterface() throws Exception {
        Path fixtures = Path.of(System.getProperty("nekujs.fixtures.dir"));
        try (var loader = fixtureLoader(fixtures)) {
            Class<?> payload = originalPayload(loader, fixtures.resolve("modifyjs-1.1.0.3-neoforge.jar"),
                    "com.anningui.modifyjs.event.register.ItemRenderRegister");
            Class<?> startup = Class.forName("dev.latvian.mods.kubejs.event.KubeStartupEvent", false, loader);
            assertTrue(startup.isInterface());
            assertTrue(KubeEvent.class.isAssignableFrom(startup));
            assertTrue(startup.isAssignableFrom(payload));
            var basic = startup.getField("BASIC");
            assertEquals(startup, basic.getType());
            assertTrue(Modifier.isStatic(basic.getModifiers()));
            assertTrue(Modifier.isFinal(basic.getModifiers()));
        }
    }

    @Test
    void originalKeyBindJsModifyJsAndJeiPayloadsRetainTheirClientEventSuperinterface() throws Exception {
        Path fixtures = Path.of(System.getProperty("nekujs.fixtures.dir"));
        try (var loader = fixtureLoader(fixtures)) {
            Class<?> keyBind = originalPayload(loader, fixtures.resolve("KeyBindJS-2101-1.1.0.jar"),
                    "com.common.keybindjs.kubejs.KeyBindJSRegisterEvent");
            Class<?> client = Class.forName("dev.latvian.mods.kubejs.client.ClientKubeEvent", false, loader);
            assertTrue(client.isInterface());
            assertTrue(KubeEvent.class.isAssignableFrom(client));
            assertTrue(client.isAssignableFrom(keyBind));
            assertTrue(client.isAssignableFrom(originalPayload(loader,
                    fixtures.resolve("modifyjs-1.1.0.3-neoforge.jar"),
                    "com.anningui.modifyjs.event.modify.ItemRenderModify")));
            assertTrue(client.isAssignableFrom(originalPayload(loader,
                    fixtures.resolve("jei_kubejs-1.21.1-neoforge-1.1.0.jar"),
                    "com.hp.jei_kubejs.kubejs.RegisterCategoriesKubeEvent")));
            assertTrue(client.isAssignableFrom(originalPayload(loader,
                    fixtures.resolve("jei_kubejs-1.21.1-neoforge-1.1.0.jar"),
                    "com.hp.jei_kubejs.kubejs.RegisterRecipesKubeEvent")));
            var getClient = client.getMethod("getClient");
            assertTrue(getClient.isDefault());
            assertEquals("net.minecraft.client.Minecraft", getClient.getReturnType().getName());
        }
    }

    private static URLClassLoader fixtureLoader(Path fixtures) throws Exception {
        assertTrue(Files.isDirectory(fixtures), "Missing expanded fixture directory: " + fixtures);
        var urls = new ArrayList<URL>();
        try (var files = Files.list(fixtures)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                urls.add(file.toUri().toURL());
            }
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ExpandedEventPayloadTest.class.getClassLoader());
    }

    private static Class<?> originalPayload(URLClassLoader loader, Path source, String name) throws Exception {
        assertTrue(Files.isRegularFile(source), "Missing original payload fixture: " + source);
        try (var jar = new JarFile(source.toFile())) {
            assertNotNull(jar.getJarEntry(name.replace('.', '/') + ".class"),
                    "Original payload class is absent from fixture: " + name);
        }
        Class<?> payload = Class.forName(name, false, loader);
        assertSame(loader, payload.getClassLoader(), "Payload must be defined by the actual fixture loader");
        assertEquals(source.toAbsolutePath().normalize().toUri(),
                payload.getProtectionDomain().getCodeSource().getLocation().toURI());
        return payload;
    }
}
