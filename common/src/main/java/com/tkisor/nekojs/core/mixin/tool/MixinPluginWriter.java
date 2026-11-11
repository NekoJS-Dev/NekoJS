package com.tkisor.nekojs.core.mixin.tool;

import com.tkisor.nekojs.core.mixin.MixinPlan;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** Self-contained Mixin gate: an orphaned artifact never links the runtime bridge. */
final class MixinPluginWriter {
    private MixinPluginWriter() {}

    static byte[] write(String name, MixinPlan plan, boolean managed) {
        var profile = plan.profile();
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC | ACC_FINAL, name, null, "java/lang/Object",
                new String[]{"org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin"});
        writer.visitField(ACC_PRIVATE, "enabled", "Z", null, null).visitEnd();
        var init = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(RETURN); init.visitMaxs(0, 0); init.visitEnd();

        var load = writer.visitMethod(ACC_PUBLIC, "onLoad", "(Ljava/lang/String;)V", null, null);
        var start = new Label(); var end = new Label(); var missing = new Label(); var done = new Label();
        load.visitTryCatchBlock(start, end, missing, "java/lang/LinkageError");
        load.visitCode(); load.visitLabel(start); load.visitVarInsn(ALOAD, 0);
        load.visitLdcInsn(profile.loader()); load.visitLdcInsn(profile.minecraft());
        load.visitLdcInsn(profile.loaderVersion()); load.visitLdcInsn(profile.side());
        load.visitLdcInsn(plan.hash()); load.visitInsn(managed ? ICONST_1 : ICONST_0);
        // The gate lives in NekoJS, not the artifact, and never initializes a runtime root.
        load.visitMethodInsn(INVOKESTATIC, "com/tkisor/nekojs/mixinjs/MixinBootstrapGuard", "active",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Z", false);
        load.visitFieldInsn(PUTFIELD, name, "enabled", "Z");
        load.visitLabel(end); load.visitJumpInsn(GOTO, done);
        load.visitLabel(missing); load.visitInsn(POP);
        load.visitVarInsn(ALOAD, 0); load.visitInsn(ICONST_0); load.visitFieldInsn(PUTFIELD, name, "enabled", "Z");
        load.visitLabel(done); load.visitInsn(RETURN); load.visitMaxs(0, 0); load.visitEnd();

        var apply = writer.visitMethod(ACC_PUBLIC, "shouldApplyMixin", "(Ljava/lang/String;Ljava/lang/String;)Z", null, null);
        apply.visitCode(); apply.visitVarInsn(ALOAD, 0); apply.visitFieldInsn(GETFIELD, name, "enabled", "Z");
        apply.visitInsn(IRETURN); apply.visitMaxs(0, 0); apply.visitEnd();
        empty(writer, "getRefMapperConfig", "()Ljava/lang/String;", true);
        empty(writer, "getMixins", "()Ljava/util/List;", true);
        empty(writer, "acceptTargets", "(Ljava/util/Set;Ljava/util/Set;)V", false);
        String transformation = "(Ljava/lang/String;Lorg/objectweb/asm/tree/ClassNode;Ljava/lang/String;Lorg/spongepowered/asm/mixin/extensibility/IMixinInfo;)V";
        empty(writer, "preApply", transformation, false);
        empty(writer, "postApply", transformation, false);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void empty(ClassWriter writer, String name, String descriptor, boolean nullableResult) {
        var method = writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode();
        if (nullableResult) method.visitInsn(ACONST_NULL);
        method.visitInsn(nullableResult ? ARETURN : RETURN);
        method.visitMaxs(0, 0); method.visitEnd();
    }
}
