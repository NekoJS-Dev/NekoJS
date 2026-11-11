package com.tkisor.nekujs;

import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the unchanged Create class without ComputerCraft or class initialization. */
@Tag("fixtures")
class FixtureStationDeclarationTest {
    @Test
    void privatePeripheralLambdaDoesNotRemoveThePublicStationDeclaration(@TempDir Path directory) throws Exception {
        Path fixtures = Path.of(System.getProperty("nekujs.fixtures.dir"));
        Path create = fixtures.resolve("create-1.21.1-6.0.10.jar");
        var urls = new ArrayList<URL>();
        try (var files = Files.list(fixtures)) {
            for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) {
                urls.add(jar.toUri().toURL());
            }
        }
        // Nested runtime libraries provide Create's real public supertypes.
        try (var jar = new JarFile(create.toFile())) {
            for (var entry : jar.stream().filter(entry -> entry.getName().startsWith("META-INF/jarjar/")
                    && entry.getName().endsWith(".jar")).toList()) {
                Path library = directory.resolve(Path.of(entry.getName()).getFileName().toString());
                try (var input = jar.getInputStream(entry)) {
                    Files.copy(input, library);
                }
                urls.add(library.toUri().toURL());
            }
        }
        try (var loader = new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader())) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("dan200.computercraft.api.peripheral.IPeripheral", false, loader));
            Class<?> station = Class.forName("com.simibubi.create.content.trains.station.StationBlockEntity", false, loader);
            assertSame(loader, station.getClassLoader());
            assertEquals(create.toUri().toURL(), station.getProtectionDomain().getCodeSource().getLocation());
            assertThrows(NoClassDefFoundError.class, station::getDeclaredMethods);
            assertDoesNotThrow(station::getMethods);
            var declaration = new TypeReflector().reflect(station);
            assertEquals(station.getName(), declaration.fqn);
            assertFalse(declaration.methods.isEmpty());
            assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("getStation")));
            assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("registerCapabilities")));
            assertFalse(declaration.methods.stream().anyMatch(method -> method.name.startsWith("lambda$")));
            String typescript = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
            assertTrue(typescript.contains("export class $StationBlockEntity"), typescript);
            assertTrue(typescript.contains("getStation("), typescript);
            assertTrue(typescript.contains("$GlobalStation"), typescript);
            assertTrue(typescript.contains("registerCapabilities("), typescript);
            assertFalse(typescript.contains("computercraft"), typescript);
        }
    }
}
