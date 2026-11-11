package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Reproduces an optional capability return type used only by a private synthetic lambda. */
class ProbePublicSurfaceTest {
    private static final String PACKAGE = "com.simibubi.create.content.trains.station";
    private static final String OWNER = PACKAGE + ".StationBlockEntity";
    private static final String PARENT = PACKAGE + ".StationParent";
    private static final String CONTRACT = PACKAGE + ".StationContract";
    private static final String DIRECTION = "net.minecraft.core.Direction";
    private static final String MISSING = "dan200.computercraft.api.peripheral.IPeripheral";
    private static final String PRIVATE_LAMBDA = "lambda$registerCapabilities$1";
    private static final String INITIALIZATION_FAILURE = "Probe must not initialize the station fixture";

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void jdkPublicEnumerationIgnoresUnavailablePrivateSignatures() throws Exception {
        ClassLoader loader = fixtureLoader();
        Class<?> owner = loader.loadClass(OWNER);
        Class<?> contract = loader.loadClass(CONTRACT);
        assertPublicNativeBaseline(owner, contract);
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass(MISSING));
        assertNotInitialized(loader);
    }

    @Test
    void reflectorPreservesOwnerBeansAndInheritedOverloads() throws Exception {
        ClassLoader loader = fixtureLoader();
        Class<?> owner = loader.loadClass(OWNER);
        assertPublicNativeBaseline(owner, loader.loadClass(CONTRACT));
        TypeDecl declaration = assertDoesNotThrow(() -> new TypeReflector().reflect(owner));
        assertSame(owner, declaration.sourceClass);
        assertEquals(TypeDecl.Kind.CLASS, declaration.kind);
        assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("status")));
        var getter = declaration.methods.stream().filter(method -> method.name.equals("getTitle"))
                .findFirst().orElseThrow();
        assertTrue(getter.isGetter);
        assertEquals(Arrays.stream(owner.getMethods()).filter(method -> method.getName().equals("getTitle"))
                .findFirst().orElseThrow(), getter.sourceMethod);
        assertEquals("title", getter.property);
        assertNotNull(getter.setterParamType);
        assertEquals(Set.of(int.class, long.class, String.class), declaration.methods.stream()
                .filter(method -> method.name.equals("choose"))
                .map(method -> method.sourceMethod.getParameterTypes()[0]).collect(Collectors.toSet()));
        assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("interfaceStatus")));
        assertTrue(declaration.methods.stream().noneMatch(method -> method.name.equals(PRIVATE_LAMBDA)));
        assertNotInitialized(loader);
    }

    @Test
    void reflectorPreservesInterfaceWithUnavailablePrivateSignature() throws Exception {
        ClassLoader loader = fixtureLoader();
        Class<?> contract = loader.loadClass(CONTRACT);
        assertPublicNativeBaseline(loader.loadClass(OWNER), contract);
        TypeDecl declaration = assertDoesNotThrow(() -> new TypeReflector().reflect(contract));
        assertSame(contract, declaration.sourceClass);
        assertEquals(TypeDecl.Kind.INTERFACE, declaration.kind);
        assertEquals(Set.of("choose", "interfaceStatus"), declaration.methods.stream()
                .map(method -> method.name).collect(Collectors.toSet()));
        assertNotInitialized(loader);
    }

    @Test
    void bothProbeBackendsKeepRealPublicOwnerWithoutPublicUnavailableDiagnostics(@TempDir Path directory) throws Exception {
        ClassLoader loader = fixtureLoader();
        Class<?> owner = loader.loadClass(OWNER);
        Class<?> contract = loader.loadClass(CONTRACT);
        assertPublicNativeBaseline(owner, contract);
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(
                BindingCatalogEntry.of("Station", ScriptType.SERVER, owner, false),
                BindingCatalogEntry.of("StationContract", ScriptType.SERVER, contract, false)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        Files.createDirectories(paths.probeConfig().getParent());
        Files.writeString(paths.probeConfig(), """
                [scan]
                includePackages = ["%s"]
                maxDepth = 2
                mode = "SMART"
                """.formatted(PACKAGE));
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(loader);
            var coordinator = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
            var collected = ProbeCoordinator.collectClasses(snapshot, coordinator.readConfig());
            assertTrue(collected.containsAll(List.of(owner, contract, loader.loadClass(PARENT))), collected::toString);
            var results = coordinator.runProbe(snapshot, List.of(new TypeScriptProbeBackend(), new PythonProbeBackend()));
            assertEquals(2, results.size());
            for (var result : results) {
                assertTrue(result.success(), result::message);
                assertTrue(result.warnings().stream().noneMatch(warning -> warning.contains("[NEKO-4029]")
                        || warning.contains("[NEKO-4038]")), result.warnings()::toString);
            }
            var tsWarnings = results.getFirst().warnings();
            for (String target : List.of(OWNER, CONTRACT)) {
                assertTrue(tsWarnings.stream().anyMatch(warning -> warning.startsWith(
                        "MixinJS declaration completion unavailable for " + target + ": java.lang.NoClassDefFoundError: ")
                        && warning.replace('/', '.').endsWith(MISSING)), tsWarnings::toString);
            }
            var dependencies = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve(
                    ".neko_probe/typescript/@special/dependencies.json"))).getAsJsonObject();
            assertTrue(dependencies.get("complete").getAsBoolean(), dependencies::toString);
            assertTrue(dependencies.getAsJsonObject("unavailable").isEmpty(), dependencies::toString);
            String packagePath = PACKAGE.replace('.', '/');
            String ts = Files.readString(directory.resolve(".neko_probe/typescript/@package/" + packagePath + "/index.d.ts"));
            assertTrue(ts.contains("export class $StationBlockEntity"), ts);
            assertTrue(ts.contains("export interface $StationContract"), ts);
            assertTrue(ts.contains("status(): string;"), ts);
            assertTrue(ts.contains("get title(): string;"), ts);
            assertTrue(ts.contains("set title(value: string);"), ts);
            assertTrue(ts.contains("getTitle(): string;"), ts);
            assertTrue(ts.contains("setTitle(value: string): void;"), ts);
            assertTrue(ts.contains("choose(value: number): string;"), ts);
            assertTrue(ts.contains("choose(value: string): string;"), ts);
            assertTrue(ts.contains("interfaceStatus(): string;"), ts);
            String py = Files.readString(directory.resolve(".neko_probe/python/nekojs/_java/" + packagePath + "/__init__.pyi"));
            assertTrue(py.contains("class StationBlockEntity("), py);
            assertTrue(py.contains("class StationContract"), py);
            assertTrue(py.contains("def status(self) -> str"), py);
            assertTrue(py.contains("@property\n    def title(self) -> str"), py);
            assertTrue(py.contains("@title.setter\n    def title(self, value: str) -> None"), py);
            assertFalse(py.contains("def getTitle("), py);
            assertFalse(py.contains("def setTitle("), py);
            assertTrue(py.contains("@overload\n    def choose(self, value: int) -> str"), py);
            assertTrue(py.contains("@overload\n    def choose(self, value: str) -> str"), py);
            assertTrue(py.contains("def interfaceStatus(self) -> str"), py);
            assertFalse(ts.contains("IPeripheral"), ts);
            assertFalse(py.contains("IPeripheral"), py);
            assertFalse(ts.contains(PRIVATE_LAMBDA), ts);
            assertFalse(py.contains(PRIVATE_LAMBDA), py);
            try (var files = Files.walk(directory.resolve(".neko_probe"))) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".d.ts") || path.toString().endsWith(".pyi")).toList()) {
                    assertFalse(file.getFileName().toString().contains("IPeripheral"), file::toString);
                    assertFalse(Files.readString(file).contains("IPeripheral"), file::toString);
                }
            }
        } finally {
            thread.setContextClassLoader(previous);
        }
        assertNotInitialized(loader);
    }

    private static void assertPublicNativeBaseline(Class<?> owner, Class<?> contract) {
        List<Method> visible = Arrays.asList(assertDoesNotThrow(owner::getMethods));
        assertTrue(visible.stream().anyMatch(method -> method.getName().equals("getTitle")));
        assertTrue(visible.stream().anyMatch(method -> method.getName().equals("interfaceStatus")));
        assertEquals(Set.of(int.class, long.class, String.class), visible.stream()
                .filter(method -> method.getName().equals("choose"))
                .map(method -> method.getParameterTypes()[0]).collect(Collectors.toSet()));
        assertTrue(visible.stream().noneMatch(method -> method.getName().equals(PRIVATE_LAMBDA)));
        assertEquals(Set.of("choose", "interfaceStatus"), Arrays.stream(assertDoesNotThrow(contract::getMethods))
                .map(Method::getName).collect(Collectors.toSet()));
        assertThrows(NoClassDefFoundError.class, owner::getDeclaredMethods);
        assertThrows(NoClassDefFoundError.class, contract::getDeclaredMethods);
    }

    private static void assertNotInitialized(ClassLoader loader) {
        AssertionError failure = assertThrows(AssertionError.class, () -> Class.forName(OWNER, true, loader));
        assertEquals(INITIALIZATION_FAILURE, failure.getMessage());
    }

    private static ClassLoader fixtureLoader() {
        Map<String, byte[]> definitions = Map.of(
                OWNER, ownerBytes(), PARENT, parentBytes(), CONTRACT, contractBytes(), DIRECTION, directionBytes());
        return new ClassLoader(ProbePublicSurfaceTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(MISSING)) throw new ClassNotFoundException(name);
                byte[] bytes = definitions.get(name);
                if (bytes == null) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
    }

    private static byte[] ownerBytes() {
        ClassWriter writer = type(OWNER, ACC_PUBLIC | ACC_SUPER, PARENT, new String[]{internal(CONTRACT)});
        constructor(writer, PARENT);
        stringMethod(writer, "status", "()Ljava/lang/String;", null);
        stringMethod(writer, "getTitle", "()Ljava/lang/String;", null);
        stringMethod(writer, "choose", "(I)Ljava/lang/String;", "value");
        MethodVisitor setter = writer.visitMethod(ACC_PUBLIC, "setTitle", "(Ljava/lang/String;)V", null, null);
        setter.visitParameter("value", 0);
        setter.visitCode();
        setter.visitInsn(RETURN);
        setter.visitMaxs(0, 0);
        setter.visitEnd();
        privateLambda(writer);
        MethodVisitor initializer = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitTypeInsn(NEW, "java/lang/AssertionError");
        initializer.visitInsn(DUP);
        initializer.visitLdcInsn(INITIALIZATION_FAILURE);
        initializer.visitMethodInsn(INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V", false);
        initializer.visitInsn(ATHROW);
        initializer.visitMaxs(0, 0);
        initializer.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] parentBytes() {
        ClassWriter writer = type(PARENT, ACC_PUBLIC | ACC_SUPER, "java.lang.Object", null);
        constructor(writer, "java.lang.Object");
        stringMethod(writer, "choose", "(Ljava/lang/String;)Ljava/lang/String;", "value");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] contractBytes() {
        ClassWriter writer = type(CONTRACT, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, "java.lang.Object", null);
        stringMethod(writer, "choose", "(J)Ljava/lang/String;", "value");
        stringMethod(writer, "interfaceStatus", "()Ljava/lang/String;", null);
        privateLambda(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] directionBytes() {
        ClassWriter writer = type(DIRECTION, ACC_PUBLIC | ACC_SUPER, "java.lang.Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter type(String name, int access, String parent, String[] interfaces) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, access, internal(name), null, internal(parent), interfaces);
        return writer;
    }

    private static void constructor(ClassWriter writer, String parent) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, internal(parent), "<init>", "()V", false);
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void stringMethod(ClassWriter writer, String name, String descriptor, String parameter) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
        if (parameter != null) method.visitParameter(parameter, 0);
        method.visitCode();
        method.visitLdcInsn("available");
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void privateLambda(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, PRIVATE_LAMBDA,
                "(L" + internal(OWNER) + ";L" + internal(DIRECTION) + ";)L" + internal(MISSING) + ";", null, null);
        method.visitCode();
        method.visitInsn(ACONST_NULL);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static String internal(String name) {
        return name.replace('.', '/');
    }
}
