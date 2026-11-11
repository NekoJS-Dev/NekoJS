package com.tkisor.nekojs.core.mixin.tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.core.mixin.MixinPlan;
import org.objectweb.asm.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

import static org.objectweb.asm.Opcodes.*;

/** Writes internal game libraries or dependency-bearing carrier mods from exact target bytes. */
final class MixinCarrierWriter {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String BRIDGE = "com/tkisor/nekojs/core/mixin/MixinNativeBridge";
    private static final String DECISION = BRIDGE + "$Decision";
    private static final String CI = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CIR = CI + "Returnable";
    private MixinCarrierWriter() {}

    static void validate(MixinPlan.Hook hook, byte[] bytes) {
        var reader = new ClassReader(bytes);
        if (!reader.getClassName().equals(hook.target().replace('.', '/'))) throw MixinPlan.invalid("Target class entry mismatch");
        boolean[] found = {false};
        reader.accept(new ClassVisitor(ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.equals(hook.method()) && descriptor.equals(hook.descriptor())) {
                    if (found[0] || (access & (ACC_ABSTRACT | ACC_NATIVE | ACC_SYNTHETIC | ACC_BRIDGE)) != 0
                            || ((access & ACC_STATIC) != 0) != hook.staticMethod()) throw MixinPlan.invalid("Unsupported target method: " + hook.id());
                    found[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!found[0]) throw MixinPlan.invalid("Exact target method not found: " + hook.target() + "." + hook.method() + hook.descriptor());
    }

    static byte[] mixin(MixinPlan plan, MixinPlan.Hook hook, String name) {
        // Runtime-namespace inputs are mandatory. No development-name remapping is promised.
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC | ACC_ABSTRACT, name, null, "java/lang/Object", null);
        var mixin = writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
        var targets = mixin.visitArray("targets"); targets.visit(null, hook.target()); targets.visitEnd();
        mixin.visit("remap", false); mixin.visitEnd();
        Type[] arguments = Type.getArgumentTypes(hook.descriptor());
        Type result = Type.getReturnType(hook.descriptor());
        String callback = result.getSort() == Type.VOID ? CI : CIR;
        Type[] handlerArgs = Arrays.copyOf(arguments, arguments.length + 1);
        handlerArgs[arguments.length] = Type.getObjectType(callback);
        var method = writer.visitMethod(ACC_PRIVATE | (hook.staticMethod() ? ACC_STATIC : 0),
                "nekojs$hook", Type.getMethodDescriptor(Type.VOID_TYPE, handlerArgs), null, null);
        var inject = method.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
        var selectors = inject.visitArray("method"); selectors.visit(null, hook.method() + hook.descriptor()); selectors.visitEnd();
        var points = inject.visitArray("at"); var at = points.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
        at.visit("value", hook.at()); at.visitEnd(); points.visitEnd();
        inject.visit("cancellable", hook.cancellable()); inject.visit("require", hook.require()); inject.visit("remap", false); inject.visitEnd();
        method.visitCode();
        var done = new Label();
        method.visitLdcInsn(plan.hash()); method.visitLdcInsn(hook.id());
        method.visitMethodInsn(INVOKESTATIC, BRIDGE, "enabled", "(Ljava/lang/String;Ljava/lang/String;)Z", false);
        method.visitJumpInsn(IFEQ, done);
        method.visitLdcInsn(plan.hash()); method.visitLdcInsn(hook.id());
        if (hook.staticMethod()) method.visitInsn(ACONST_NULL); else method.visitVarInsn(ALOAD, 0);
        method.visitLdcInsn(arguments.length); method.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        int local = hook.staticMethod() ? 0 : 1;
        for (int index = 0; index < arguments.length; index++) {
            Type argument = arguments[index];
            method.visitInsn(DUP); method.visitLdcInsn(index); method.visitVarInsn(argument.getOpcode(ILOAD), local);
            box(method, argument); method.visitInsn(AASTORE); local += argument.getSize();
        }
        int callbackLocal = local;
        if (hook.at().equals("RETURN") && result.getSort() != Type.VOID) {
            method.visitVarInsn(ALOAD, callbackLocal);
            method.visitMethodInsn(INVOKEVIRTUAL, CIR, "getReturnValue", "()Ljava/lang/Object;", false);
        } else method.visitInsn(ACONST_NULL);
        method.visitMethodInsn(INVOKESTATIC, BRIDGE, "invoke", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/Object;)L" + DECISION + ";", false);
        int decisionLocal = callbackLocal + 1;
        method.visitVarInsn(ASTORE, decisionLocal);
        if (hook.cancellable()) {
            method.visitVarInsn(ALOAD, decisionLocal);
            method.visitMethodInsn(INVOKEVIRTUAL, DECISION, "changed", "()Z", false);
            method.visitJumpInsn(IFEQ, done);
            method.visitVarInsn(ALOAD, callbackLocal);
            if (result.getSort() == Type.VOID) method.visitMethodInsn(INVOKEVIRTUAL, CI, "cancel", "()V", false);
            else {
                method.visitVarInsn(ALOAD, decisionLocal);
                method.visitMethodInsn(INVOKEVIRTUAL, DECISION, "value", "()Ljava/lang/Object;", false);
                method.visitMethodInsn(INVOKEVIRTUAL, CIR, "setReturnValue", "(Ljava/lang/Object;)V", false);
            }
        }
        method.visitLabel(done); method.visitInsn(RETURN); method.visitMaxs(0, 0); method.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }

    private static void box(MethodVisitor method, Type type) {
        String owner = switch (type.getSort()) {
            case Type.BOOLEAN -> "java/lang/Boolean"; case Type.BYTE -> "java/lang/Byte";
            case Type.CHAR -> "java/lang/Character"; case Type.SHORT -> "java/lang/Short";
            case Type.INT -> "java/lang/Integer"; case Type.FLOAT -> "java/lang/Float";
            case Type.DOUBLE -> "java/lang/Double"; default -> null;
        };
        if (owner != null) method.visitMethodInsn(INVOKESTATIC, owner, "valueOf", "(" + type.getDescriptor() + ")L" + owner + ";", false);
    }

    static final String FORMAT = "2";

    static boolean gameLibrary(MixinPlan.Profile profile) {
        return profile.loader().equals("neoforge") && Set.of("26.1.2", "26.2", "26.2.0").contains(profile.minecraft());
    }

    static Path write(Path output, MixinPlan plan, Map<String, byte[]> sources) throws IOException {
        return write(output, plan, sources, false);
    }

    static Path write(Path output, MixinPlan plan, Map<String, byte[]> sources, boolean managed) throws IOException {
        String id = "nekojs_mixins_" + plan.hash().substring(0, 16);
        String pkg = "nekojs.generated.mixins.p" + plan.hash();
        var files = new TreeMap<String, byte[]>();
        var classes = new ArrayList<String>();
        int index = 0;
        for (var hook : plan.hooks()) {
            String simple = "Hook" + index++;
            classes.add(simple);
            files.put(pkg.replace('.', '/') + "/" + simple + ".class", mixin(plan, hook, pkg.replace('.', '/') + "/" + simple));
        }
        String config = id + ".mixins.json";
        var configData = new LinkedHashMap<String, Object>();
        String plugin = pkg + ".BootstrapPlugin";
        files.put(plugin.replace('.', '/') + ".class", MixinPluginWriter.write(plugin.replace('.', '/'), plan, managed));
        configData.put("plugin", plugin);
        configData.put("required", true); configData.put("minVersion", "0.8"); configData.put("package", pkg);
        configData.put("compatibilityLevel", "JAVA_21"); configData.put("injectors", Map.of("defaultRequire", 1));
        configData.put(plan.profile().side().equals("client") ? "client" : "mixins", classes);
        put(files, config, JSON.toJson(configData));
        put(files, "META-INF/nekojs/mixin-plan.json", JSON.toJson(plan));
        sources.forEach((name, data) -> files.put("META-INF/nekojs/sources/" + name, data));
        var manifest = new Manifest();
        var attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue("NekoJS-Mixin-Format", FORMAT);
        attributes.putValue("NekoJS-Mixin-Owner", id);
        if (managed) attributes.putValue("NekoJS-Mixin-Managed", plan.hash());
        if (gameLibrary(plan.profile())) {
            attributes.putValue("FMLModType", "GAMELIBRARY");
            attributes.putValue("MixinConfigs", config);
            attributes.putValue("Automatic-Module-Name", "nekojs.generated.mixins.p" + plan.hash());
        } else if (plan.profile().loader().equals("neoforge")) {
            put(files, "META-INF/neoforge.mods.toml", """
                    modLoader="javafml"
                    loaderVersion="[1,)"
                    license="All Rights Reserved"
                    [[mods]]
                    modId="%s"
                    version="1.0.0"
                    displayName="NekoJS experimental mixin carrier"
                    [[mixins]]
                    config="%s"
                    [[dependencies.%s]]
                    modId="nekojs"
                    type="required"
                    versionRange="[0,)"
                    ordering="AFTER"
                    side="BOTH"
                    [[dependencies.%s]]
                    modId="minecraft"
                    type="required"
                    versionRange="[%s]"
                    [[dependencies.%s]]
                    modId="neoforge"
                    type="required"
                    versionRange="[%s]"
                    """.formatted(id, config, id, id, plan.profile().minecraft(), id, plan.profile().loaderVersion()));
        } else {
            put(files, "fabric.mod.json", JSON.toJson(Map.of("schemaVersion", 1, "id", id, "version", "1.0.0", "environment", plan.profile().side().equals("server") ? "server" : "client", "mixins", List.of(config), "depends", Map.of("nekojs", "*", "minecraft", plan.profile().minecraft(), "fabricloader", plan.profile().loaderVersion()))));
        }
        Path target = output.toAbsolutePath().normalize(); Files.createDirectories(target.getParent());
        // Never silently replace an installed carrier; structural changes require explicit removal and restart.
        try (var stream = new JarOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW), manifest)) {
            for (var entry : files.entrySet()) { var item = new JarEntry(entry.getKey()); item.setTime(0); stream.putNextEntry(item); stream.write(entry.getValue()); stream.closeEntry(); }
        }
        return target;
    }
    private static void put(Map<String, byte[]> files, String name, String value) { files.put(name, value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
