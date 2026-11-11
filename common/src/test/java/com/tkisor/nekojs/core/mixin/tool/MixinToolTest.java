package com.tkisor.nekojs.core.mixin.tool;

import com.tkisor.nekojs.core.mixin.MixinPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class MixinToolTest {
    @TempDir Path root;
    private static final String VALID = "import {Mixin} from 'nekojs/mixin'; Mixin.target('demo.Target', target => { target.inject('health()F', 'demo:health', build => { build.at = 'RETURN'; build.cancellable = true; }); });";
    @Test void restrictedDeclarationsRejectTyposHostAccessAndAsyncBuilders() throws Exception {
        Path source = root.resolve("demo.mixin.js"); Files.writeString(source, VALID);
        try (var declarations = new MixinDeclarations(root)) {
            declarations.loadAll(); assertEquals(1, declarations.hooks.size()); assertEquals("()F", declarations.hooks.getFirst().descriptor());
        }
        for (String code : List.of(VALID.replace("build.at", "build.typo"), VALID.replace("'RETURN'", "'INVOKE'"),
                "Java.type('java.lang.System');", "import 'https://example.invalid/module.js';", "while (true) {}",
                VALID.replace("build =>", "async build =>"), VALID.replace("build.cancellable = true;", "build.cancellable = true; return null;"))) {
            Files.writeString(source, code);
            try (var declarations = new MixinDeclarations(root)) { assertThrows(Exception.class, declarations::loadAll, code); }
        }
        Path outside = root.getParent().resolve(root.getFileName() + "-outside.js");
        try { Files.writeString(outside, VALID); Files.writeString(source, "import '../" + outside.getFileName() + "';");
            try (var declarations = new MixinDeclarations(root)) { assertThrows(Exception.class, declarations::loadAll); }
        } finally { Files.deleteIfExists(outside); }
    }
    @Test void carrierBytecodeIsExactLazyAndModernArtifactIsAGameLibrary() throws Exception {
        var profile = new MixinPlan.Profile("26.1.2", "neoforge", "26.1.2.71", "runtime", "client");
        var hook = new MixinPlan.Hook("demo:health", "demo.Target", "health", "()F", "RETURN", true, "SERVER", "SERVER_MAIN", 1, false);
        var plan = new MixinPlan(1, profile, "b".repeat(64), List.of(hook));
        var target = new ClassWriter(0); target.visit(V21, ACC_PUBLIC, "demo/Target", null, "java/lang/Object", null);
        target.visitMethod(ACC_PUBLIC, "health", "()F", null, null).visitEnd(); target.visitEnd();
        MixinCarrierWriter.validate(hook, target.toByteArray());
        Path classes = root.resolve("classes/demo"); Files.createDirectories(classes);
        Files.write(classes.resolve("Target.class"), target.toByteArray());
        Path declarations = root.resolve("declarations"); Files.createDirectories(declarations);
        Files.writeString(declarations.resolve("health.mixin.js"), VALID);
        MixinToolMain.main(new String[] {"build", "--source", declarations.toString(), "--classes", root.resolve("classes").toString(),
                "--minecraft", "26.1.2", "--loader", "neoforge", "--loader-version", "26.1.2.71", "--side", "client", "--output", root.resolve("cli-carrier.jar").toString()});
        assertTrue(Files.isRegularFile(root.resolve("cli-carrier.jar")));
        assertThrows(IllegalArgumentException.class, () -> MixinCarrierWriter.validate(new MixinPlan.Hook("demo:missing", "demo.Target", "missing", "()F", "HEAD", false, "SERVER", "SERVER_MAIN", 1, false), target.toByteArray()));
        var instructions = new ArrayList<String>();
        new ClassReader(MixinCarrierWriter.mixin(plan, hook, "demo/generated/Hook")).accept(new ClassVisitor(ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM9) {
                    public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) { instructions.add(method); }
                    public void visitTypeInsn(int opcode, String type) { if (opcode == ANEWARRAY) instructions.add("allocate-args"); }
                };
            }
        }, 0);
        assertTrue(instructions.indexOf("enabled") < instructions.indexOf("allocate-args"));
        assertTrue(instructions.contains("getReturnValue")); assertTrue(instructions.contains("setReturnValue"));
        Path output = root.resolve("carrier.jar"); MixinCarrierWriter.write(output, plan, Map.of("demo.mixin.js", VALID.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try (var jar = new JarFile(output.toFile())) {
            assertNull(jar.getEntry("META-INF/neoforge.mods.toml"));
            assertNull(jar.getEntry("fabric.mod.json"));
            assertNotNull(jar.getEntry("META-INF/nekojs/mixin-plan.json"));
            assertEquals("GAMELIBRARY", jar.getManifest().getMainAttributes().getValue("FMLModType"));
            String config = jar.getManifest().getMainAttributes().getValue("MixinConfigs");
            assertNotNull(jar.getEntry(config));
            assertNotNull(jar.getEntry("nekojs/generated/mixins/p" + plan.hash() + "/BootstrapPlugin.class"));
        }
        assertThrows(FileAlreadyExistsException.class, () -> MixinCarrierWriter.write(output, plan, Map.of()));
    }
}
