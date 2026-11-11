package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class ProbeUnavailableEnumTest {

    private static final String OWNER = "fixture.probe.OptionalEnum";
    private static final String MISSING = "fixture.probe.OptionalEnumSignature";
    private static final String INTERNAL_OWNER = OWNER.replace('.', '/');
    private static final String ENUM_DESCRIPTOR = "L" + INTERNAL_OWNER + ";";

    @Test
    void unavailablePublicMethodLeavesTheWholeEnumUnavailable() throws Exception {
        Class<?> owner = fixtureLoader(false).loadClass(OWNER);
        assertTrue(owner.isEnum());
        assertTrue(owner.getDeclaredField("READY").isEnumConstant());
        NoClassDefFoundError nativeFailure = assertThrows(NoClassDefFoundError.class, owner::getMethods);

        NoClassDefFoundError reflectedFailure = assertThrows(NoClassDefFoundError.class,
                () -> new TypeReflector().reflect(owner));

        assertEquals(nativeFailure.getMessage(), reflectedFailure.getMessage());
        assertEquals(MISSING, reflectedFailure.getMessage().replace('/', '.'));
    }

    @Test
    void resolvingTheSameSignatureRestoresNativeEnumConstants() throws Exception {
        Class<?> owner = fixtureLoader(true).loadClass(OWNER);
        assertTrue(owner.isEnum());
        assertDoesNotThrow(owner::getMethods);
        assertEquals("READY", owner.getEnumConstants()[0].toString());

        assertConstantFlags(owner, "READY");
    }

    @Test
    void ordinaryHealthyEnumKeepsItsConstantFlags() throws NoSuchFieldException {
        assertDoesNotThrow(OrdinaryEnum.class::getMethods);
        assertConstantFlags(OrdinaryEnum.class, "READY");
    }

    private static void assertConstantFlags(Class<?> owner, String constantName) throws NoSuchFieldException {
        TypeDecl declaration = new TypeReflector().reflect(owner);
        assertEquals(TypeDecl.Kind.ENUM, declaration.kind);
        var constant = declaration.fields.stream().filter(field -> field.name.equals(constantName))
                .findFirst().orElseThrow();
        assertTrue(constant.isEnumConstant);
        assertTrue(constant.isStatic);
        var nativeConstant = owner.getDeclaredField(constantName);
        assertEquals(owner, nativeConstant.getDeclaringClass());
        assertTrue(nativeConstant.isEnumConstant());
        assertTrue(java.lang.reflect.Modifier.isStatic(nativeConstant.getModifiers()));
    }

    private static ClassLoader fixtureLoader(boolean resolveSignature) {
        Map<String, byte[]> definitions = resolveSignature
                ? Map.of(OWNER, enumBytes(), MISSING, signatureBytes())
                : Map.of(OWNER, enumBytes());
        return new ClassLoader(ProbeUnavailableEnumTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(MISSING) && !resolveSignature) throw new ClassNotFoundException(name);
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

    private static byte[] enumBytes() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_ENUM, INTERNAL_OWNER,
                "Ljava/lang/Enum<" + ENUM_DESCRIPTOR + ">;", "java/lang/Enum", null);
        writer.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_ENUM,
                "READY", ENUM_DESCRIPTOR, null, null).visitEnd();
        writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
                "$VALUES", "[" + ENUM_DESCRIPTOR, null, null).visitEnd();

        MethodVisitor constructor = writer.visitMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitVarInsn(ALOAD, 1);
        constructor.visitVarInsn(ILOAD, 2);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
        constructor.visitInsn(RETURN);
        finish(constructor);

        MethodVisitor initializer = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitTypeInsn(NEW, INTERNAL_OWNER);
        initializer.visitInsn(DUP);
        initializer.visitLdcInsn("READY");
        initializer.visitInsn(ICONST_0);
        initializer.visitMethodInsn(INVOKESPECIAL, INTERNAL_OWNER, "<init>", "(Ljava/lang/String;I)V", false);
        initializer.visitFieldInsn(PUTSTATIC, INTERNAL_OWNER, "READY", ENUM_DESCRIPTOR);
        initializer.visitInsn(ICONST_1);
        initializer.visitTypeInsn(ANEWARRAY, INTERNAL_OWNER);
        initializer.visitInsn(DUP);
        initializer.visitInsn(ICONST_0);
        initializer.visitFieldInsn(GETSTATIC, INTERNAL_OWNER, "READY", ENUM_DESCRIPTOR);
        initializer.visitInsn(AASTORE);
        initializer.visitFieldInsn(PUTSTATIC, INTERNAL_OWNER, "$VALUES", "[" + ENUM_DESCRIPTOR);
        initializer.visitInsn(RETURN);
        finish(initializer);

        MethodVisitor values = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "values", "()[" + ENUM_DESCRIPTOR, null, null);
        values.visitCode();
        values.visitFieldInsn(GETSTATIC, INTERNAL_OWNER, "$VALUES", "[" + ENUM_DESCRIPTOR);
        values.visitMethodInsn(INVOKEVIRTUAL, "[" + ENUM_DESCRIPTOR, "clone", "()Ljava/lang/Object;", false);
        values.visitTypeInsn(CHECKCAST, "[" + ENUM_DESCRIPTOR);
        values.visitInsn(ARETURN);
        finish(values);

        MethodVisitor valueOf = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "valueOf",
                "(Ljava/lang/String;)" + ENUM_DESCRIPTOR, null, null);
        valueOf.visitCode();
        valueOf.visitLdcInsn(org.objectweb.asm.Type.getObjectType(INTERNAL_OWNER));
        valueOf.visitVarInsn(ALOAD, 0);
        valueOf.visitMethodInsn(INVOKESTATIC, "java/lang/Enum", "valueOf",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        valueOf.visitTypeInsn(CHECKCAST, INTERNAL_OWNER);
        valueOf.visitInsn(ARETURN);
        finish(valueOf);

        MethodVisitor optional = writer.visitMethod(ACC_PUBLIC, "optional", "()L" + MISSING.replace('.', '/') + ";", null, null);
        optional.visitCode();
        optional.visitInsn(ACONST_NULL);
        optional.visitInsn(ARETURN);
        finish(optional);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] signatureBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V21, ACC_PUBLIC | ACC_SUPER, MISSING.replace('.', '/'), null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void finish(MethodVisitor method) {
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    public enum OrdinaryEnum { READY }
}
