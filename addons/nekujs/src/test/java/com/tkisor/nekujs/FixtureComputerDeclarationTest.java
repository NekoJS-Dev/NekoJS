package com.tkisor.nekujs;

import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Tests the unchanged Create owner with absent and test-resolved optional signature types. */
@Tag("fixtures")
class FixtureComputerDeclarationTest {
    private static final String OWNER = "com.simibubi.create.compat.computercraft.AbstractComputerBehaviour";
    private static final String PERIPHERAL = "dan200/computercraft/api/peripheral/IPeripheral";

    @Test
    void absentOptionalPublicSignatureRetainsIndependentMembersHonestly(@TempDir Path directory) throws Exception {
        try (var loader = loader(directory, false)) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(PERIPHERAL.replace('/', '.'), false, loader));
            Class<?> owner = owner(loader);
            assertThrows(NoClassDefFoundError.class, owner::getMethods);
            var declaration = new TypeReflector().reflect(owner);
            assertFalse(declaration.memberOmissions.isEmpty());
            assertTrue(declaration.memberOmissions.getFirst().contains("Public method set"));
            assertTrue(declaration.memberOmissions.getFirst().contains("IPeripheral"));
            assertTrue(declaration.methods.isEmpty());
            assertNull(declaration.superType, "Unknown overrides must not be restored through heritage");
            assertTrue(declaration.interfaces.isEmpty());
            assertEquals(1, declaration.constructors.size());
            assertTrue(declaration.fields.stream().anyMatch(field -> field.name.equals("TYPE")));
            String typescript = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
            assertTrue(typescript.contains("export class $AbstractComputerBehaviour"), typescript);
            assertTrue(typescript.contains("TYPE"), typescript);
            assertFalse(typescript.contains("getPeripheralCapability"), typescript);
            assertFalse(typescript.contains("IPeripheral"), typescript);
        }
    }

    @Test
    void resolvedOptionalSignatureUsesUnchangedNativeMethodIdentity(@TempDir Path directory) throws Exception {
        try (var loader = loader(directory, true)) {
            Class<?> owner = owner(loader);
            var nativeMethod = owner.getMethod("getPeripheralCapability");
            assertEquals(PERIPHERAL.replace('/', '.'), nativeMethod.getReturnType().getName());
            var declaration = new TypeReflector().reflect(owner);
            assertTrue(declaration.memberOmissions.isEmpty());
            assertNotNull(declaration.superType);
            var method = declaration.methods.stream().filter(candidate -> candidate.name.equals("getPeripheralCapability"))
                    .findFirst().orElseThrow();
            assertEquals(nativeMethod, method.sourceMethod);
            assertTrue(declaration.methods.stream().anyMatch(candidate -> candidate.name.equals("prepareComputerEvent")));
            String typescript = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
            assertTrue(typescript.contains("getPeripheralCapability()"), typescript);
            assertTrue(typescript.contains("$IPeripheral"), typescript);
        }
    }

    private Class<?> owner(ClassLoader loader) throws Exception {
        Class<?> owner = Class.forName(OWNER, false, loader);
        assertSame(loader, owner.getClassLoader());
        Path create = Path.of(System.getProperty("nekujs.fixtures.dir"), "create-1.21.1-6.0.10.jar");
        assertEquals(create.toUri().toURL(), owner.getProtectionDomain().getCodeSource().getLocation());
        return owner;
    }

    private URLClassLoader loader(Path directory, boolean resolvePeripheral) throws Exception {
        Path fixtures = Path.of(System.getProperty("nekujs.fixtures.dir"));
        Path create = fixtures.resolve("create-1.21.1-6.0.10.jar");
        var urls = new ArrayList<URL>();
        try (var files = Files.list(fixtures)) {
            for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) {
                urls.add(jar.toUri().toURL());
            }
        }
        try (var jar = new JarFile(create.toFile())) {
            for (var entry : jar.stream().filter(entry -> entry.getName().startsWith("META-INF/jarjar/")
                    && entry.getName().endsWith(".jar")).toList()) {
                Path library = directory.resolve(Path.of(entry.getName()).getFileName().toString());
                try (var input = jar.getInputStream(entry)) { Files.copy(input, library); }
                urls.add(library.toUri().toURL());
            }
        }
        if (resolvePeripheral) {
            // Test-only linkage fixture, not an implementation or installation of ComputerCraft.
            ClassWriter writer = new ClassWriter(0);
            writer.visit(V21, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, PERIPHERAL, null, "java/lang/Object", null);
            writer.visitEnd();
            Path type = directory.resolve(PERIPHERAL + ".class");
            Files.createDirectories(type.getParent());
            Files.write(type, writer.toByteArray());
            urls.add(directory.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
    }
}
