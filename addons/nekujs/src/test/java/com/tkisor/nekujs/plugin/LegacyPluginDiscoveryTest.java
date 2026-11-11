package com.tkisor.nekujs.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPluginDiscoveryTest {
    private static final String PREFIX = "fixture.discovery.";
    @TempDir Path temporary;

    @Test
    void gatesUnavailableClassesBeforeLoadingAndPreservesSourceAndLineOrder() throws Exception {
        var first = jar("first", """
                # A disabled class has an absent superclass in its binary.
                fixture.discovery.Unavailable missing_mod
                fixture.discovery.ClientOnly cLiEnT
                  fixture.discovery.First # trailing comment
                """, Map.of(
                "Unavailable", binary("Unavailable", true, "fixture/discovery/AbsentBase", Failure.NORMAL),
                "First", binary("First")));
        var second = jar("second", "fixture.discovery.Second present\nfixture.discovery.Third\n",
                Map.of("Second", binary("Second"), "Third", binary("Third")));
        try (var loader = loader(first, second)) {
            var loaded = LegacyPluginDiscovery.load(List.of(first, second), loader,
                    Set.of("present")::contains, false, Runnable.class);
            assertEquals(List.of(PREFIX + "First", PREFIX + "Second", PREFIX + "Third"),
                    loaded.stream().map(LegacyPluginDiscovery.LoadedPlugin::className).toList());
            assertEquals(List.of(PREFIX + "First", PREFIX + "Second", PREFIX + "Third"), loader.requests);
            assertEquals(List.of("first", "second", "second"),
                    loaded.stream().map(plugin -> plugin.source().owner()).toList());
            assertThrows(UnsupportedOperationException.class, () -> loaded.add(loaded.getFirst()));
        }
    }

    @Test
    void clientMarkerIsCaseInsensitiveAndOtherTokensRemainRequiredModGates() throws Exception {
        var source = jar("client", "fixture.discovery.ClientOnly ClIeNt present\n",
                Map.of("ClientOnly", binary("ClientOnly")));
        try (var loader = loader(source)) {
            var gates = new ArrayList<String>();
            var loaded = LegacyPluginDiscovery.load(List.of(source), loader, mod -> {
                gates.add(mod);
                return mod.equals("present");
            }, true, Runnable.class);
            assertEquals(1, loaded.size());
            assertEquals(List.of("present"), gates);
            var reloaded = LegacyPluginDiscovery.load(List.of(source), loader, mod -> true, true, Runnable.class);
            assertNotSame(loaded.getFirst().instance(), reloaded.getFirst().instance());
        }
    }

    @Test
    void rejectsDuplicateEligibleClassNamesBeforeAnyClassLoadAcrossAllSources() throws Exception {
        var first = jar("duplicate-first", "fixture.discovery.First\n", Map.of("First", binary("First")));
        var second = jar("duplicate-second", "# second owner\nfixture.discovery.First present\n", Map.of());
        try (var loader = loader(first, second)) {
            var failure = assertThrows(LegacyPluginDiscovery.DiscoveryException.class,
                    () -> LegacyPluginDiscovery.load(List.of(first, second), loader, mod -> true, false, Runnable.class));
            assertTrue(failure.getMessage().contains("NEKO-4191"));
            assertTrue(failure.getMessage().contains("duplicate-first"));
            assertTrue(failure.getMessage().contains("duplicate-second"));
            assertTrue(failure.getMessage().contains("kubejs.plugins.txt:2"));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertTrue(loader.requests.isEmpty());
        }
    }

    @Test
    void rejectsSameSourceDuplicatesButIgnoresIneligibleDuplicateDeclarations() throws Exception {
        var duplicate = jar("same-source", "fixture.discovery.First\nfixture.discovery.First\n",
                Map.of("First", binary("First")));
        try (var loader = loader(duplicate)) {
            var failure = assertThrows(LegacyPluginDiscovery.DiscoveryException.class,
                    () -> LegacyPluginDiscovery.load(List.of(duplicate), loader, mod -> true, false, Runnable.class));
            assertTrue(failure.getMessage().contains("NEKO-4191"));
            assertTrue(loader.requests.isEmpty());
        }
        var gated = jar("gated-duplicate", "fixture.discovery.First\nfixture.discovery.First missing\n",
                Map.of("First", binary("First")));
        try (var loader = loader(gated)) {
            assertEquals(1, LegacyPluginDiscovery.load(List.of(gated), loader, mod -> false, false, Runnable.class).size());
        }
    }

    @Test
    void preservesLiteralSpaceAndTabTokenizationFromTheLegacyDescriptor() throws Exception {
        var spaces = jar("spaces", "fixture.discovery.Disabled  present\n", Map.of());
        try (var loader = loader(spaces)) {
            var gates = new ArrayList<String>();
            assertTrue(LegacyPluginDiscovery.load(List.of(spaces), loader, mod -> {
                gates.add(mod);
                return mod.equals("present");
            }, false, Runnable.class).isEmpty());
            assertEquals(List.of(""), gates);
            assertTrue(loader.requests.isEmpty());
        }
        var tabs = jar("tabs", "fixture.discovery.Disabled\tpresent\n", Map.of());
        try (var loader = loader(tabs)) {
            var failure = assertThrows(LegacyPluginDiscovery.DiscoveryException.class,
                    () -> LegacyPluginDiscovery.load(List.of(tabs), loader, mod -> true, false, Runnable.class));
            assertTrue(failure.getMessage().contains("NEKO-4192"));
            assertInstanceOf(ClassNotFoundException.class, failure.getCause());
        }
    }

    @Test
    void readsDirectoryAndArchiveFileSystemRootsAndAllowsMissingDescriptors() throws Exception {
        var directory = temporary.resolve("development-resources");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("kubejs.plugins.txt"), "fixture.discovery.Directory\n");
        Path classFile = directory.resolve("fixture/discovery/Directory.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, binary("Directory"));
        var directorySource = new LegacyPluginDiscovery.Source("development", directory);
        var archive = jar("archive", "fixture.discovery.Archive\n", Map.of("Archive", binary("Archive")));
        var emptyArchive = jar("empty", null, Map.of());
        Path emptyDirectory = Files.createDirectory(temporary.resolve("empty-directory"));
        try (var fileSystem = FileSystems.newFileSystem(archive.root());
             var loader = loader(directorySource, archive, emptyArchive)) {
            var archiveRoot = new LegacyPluginDiscovery.Source("archive-root", fileSystem.getPath("/"));
            var loaded = LegacyPluginDiscovery.load(List.of(
                    new LegacyPluginDiscovery.Source("empty-directory", emptyDirectory), emptyArchive,
                    directorySource, archiveRoot), loader, mod -> false, false, Runnable.class);
            assertEquals(List.of(PREFIX + "Directory", PREFIX + "Archive"),
                    loaded.stream().map(LegacyPluginDiscovery.LoadedPlugin::className).toList());
            assertEquals("archive-root", loaded.getLast().source().owner());
        }
    }

    @Test
    void missingClassesAndLinkageFailuresRetainTheirCauses() throws Exception {
        var missing = rejected("Missing", Map.of(), "NEKO-4192");
        assertInstanceOf(ClassNotFoundException.class, missing.getCause());
        var linkage = rejected("Unavailable", Map.of("Unavailable",
                binary("Unavailable", true, "fixture/discovery/AbsentBase", Failure.NORMAL)), "NEKO-4192");
        assertInstanceOf(NoClassDefFoundError.class, linkage.getCause());
    }

    @Test
    void typeMismatchIsRejectedWithoutExecutingItsThrowingInitializer() throws Exception {
        var failure = rejected("WrongType", Map.of("WrongType",
                binary("WrongType", false, "java/lang/Object", Failure.INITIALIZER)), "NEKO-4194");
        assertInstanceOf(ClassCastException.class, failure.getCause());
    }

    @Test
    void constructorAndInitializerFailuresAreNeverSilentlySkipped() throws Exception {
        var constructor = rejected("BadConstructor", Map.of("BadConstructor",
                binary("BadConstructor", true, "java/lang/Object", Failure.CONSTRUCTOR)), "NEKO-4193");
        assertInstanceOf(UnsupportedOperationException.class, constructor.getCause());
        assertEquals("constructor rejected", constructor.getCause().getMessage());
        var initializer = rejected("BadInitializer", Map.of("BadInitializer",
                binary("BadInitializer", true, "java/lang/Object", Failure.INITIALIZER)), "NEKO-4193");
        var error = assertInstanceOf(ExceptionInInitializerError.class, initializer.getCause());
        assertInstanceOf(UnsupportedOperationException.class, error.getCause());
        var absentConstructor = rejected("NoZeroArgument", Map.of("NoZeroArgument",
                binary("NoZeroArgument", true, "java/lang/Object", Failure.NO_ZERO_ARGUMENT)), "NEKO-4193");
        assertInstanceOf(NoSuchMethodException.class, absentConstructor.getCause());
    }

    @Test
    void sourceReadAndGateFailuresRejectTheWholeScanBeforeLoading() throws Exception {
        var valid = jar("valid", "fixture.discovery.First\n", Map.of("First", binary("First")));
        Path brokenJar = temporary.resolve("broken.jar");
        Files.writeString(brokenJar, "not a jar");
        var broken = new LegacyPluginDiscovery.Source("broken-owner", brokenJar);
        try (var loader = loader(valid)) {
            var failure = assertThrows(LegacyPluginDiscovery.DiscoveryException.class,
                    () -> LegacyPluginDiscovery.load(List.of(valid, broken), loader, mod -> true, false, Runnable.class));
            assertTrue(failure.getMessage().contains("NEKO-4190"));
            assertTrue(failure.getMessage().contains("broken-owner"));
            assertInstanceOf(IOException.class, failure.getCause());
            assertTrue(loader.requests.isEmpty());
        }
        var gated = jar("gate-error", "fixture.discovery.First present\n", Map.of("First", binary("First")));
        var gateFailure = new IllegalStateException("loaded-mod index unavailable");
        try (var loader = loader(gated)) {
            var failure = assertThrows(LegacyPluginDiscovery.DiscoveryException.class,
                    () -> LegacyPluginDiscovery.load(List.of(gated), loader, mod -> {
                        throw gateFailure;
                    }, false, Runnable.class));
            assertTrue(failure.getMessage().contains("NEKO-4190"));
            assertSame(gateFailure, failure.getCause());
            assertTrue(loader.requests.isEmpty());
        }
    }

    @Test
    void malformedUtf8DescriptorIsRejectedInsteadOfReplacingCharacters() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("malformed"));
        Files.write(root.resolve("kubejs.plugins.txt"), new byte[] {(byte) 0xC3});
        var source = new LegacyPluginDiscovery.Source("malformed", root);
        try (var loader = loader(source)) {
            var failure = assertThrows(LegacyPluginDiscovery.DiscoveryException.class,
                    () -> LegacyPluginDiscovery.load(List.of(source), loader, mod -> true, false, Runnable.class));
            assertTrue(failure.getMessage().contains("NEKO-4190"));
            assertInstanceOf(IOException.class, failure.getCause());
            assertTrue(loader.requests.isEmpty());
        }
    }

    private LegacyPluginDiscovery.DiscoveryException rejected(String name, Map<String, byte[]> classes,
                                                              String code) throws Exception {
        var source = jar(name, "# descriptor\n" + PREFIX + name + "\n", classes);
        try (var loader = loader(source)) {
            var failure = assertThrows(LegacyPluginDiscovery.DiscoveryException.class,
                    () -> LegacyPluginDiscovery.load(List.of(source), loader, mod -> true, false, Runnable.class));
            assertTrue(failure.getMessage().contains(code), failure.getMessage());
            assertTrue(failure.getMessage().contains(source.root().toString()));
            assertTrue(failure.getMessage().contains("kubejs.plugins.txt:2"));
            return failure;
        }
    }

    private LegacyPluginDiscovery.Source jar(String name, String descriptor, Map<String, byte[]> classes)
            throws IOException {
        Path path = temporary.resolve(name + ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            if (descriptor != null) {
                output.putNextEntry(new JarEntry("kubejs.plugins.txt"));
                output.write(descriptor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
            for (var entry : classes.entrySet()) {
                output.putNextEntry(new JarEntry("fixture/discovery/" + entry.getKey() + ".class"));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return new LegacyPluginDiscovery.Source(name, path);
    }

    private TrackingLoader loader(LegacyPluginDiscovery.Source... sources) throws IOException {
        var urls = new ArrayList<URL>();
        for (var source : sources) urls.add(source.root().toUri().toURL());
        return new TrackingLoader(urls.toArray(URL[]::new));
    }

    private static final class TrackingLoader extends URLClassLoader {
        private final List<String> requests = new ArrayList<>();

        private TrackingLoader(URL[] urls) {
            super(urls, LegacyPluginDiscoveryTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(PREFIX)) requests.add(name);
            return super.loadClass(name, resolve);
        }
    }

    private enum Failure { NORMAL, CONSTRUCTOR, INITIALIZER, NO_ZERO_ARGUMENT }

    private static byte[] binary(String name) {
        return binary(name, true, "java/lang/Object", Failure.NORMAL);
    }

    private static byte[] binary(String name, boolean runnable, String superclass, Failure failure) {
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "fixture/discovery/" + name, null, superclass,
                runnable ? new String[] {"java/lang/Runnable"} : null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                failure == Failure.NO_ZERO_ARGUMENT ? "(I)V" : "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclass, "<init>", "()V", false);
        if (failure == Failure.CONSTRUCTOR) {
            throwFailure(constructor, "constructor rejected");
        } else {
            constructor.visitInsn(Opcodes.RETURN);
        }
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        if (runnable) {
            var run = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
            run.visitCode();
            run.visitInsn(Opcodes.RETURN);
            run.visitMaxs(0, 0);
            run.visitEnd();
        }
        if (failure == Failure.INITIALIZER) {
            var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            initializer.visitCode();
            throwFailure(initializer, "initializer rejected");
            initializer.visitMaxs(0, 0);
            initializer.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void throwFailure(MethodVisitor method, String message) {
        method.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn(message);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>",
                "(Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.ATHROW);
    }
}
