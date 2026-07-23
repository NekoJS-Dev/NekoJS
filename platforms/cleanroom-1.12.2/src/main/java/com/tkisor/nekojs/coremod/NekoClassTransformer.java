package com.tkisor.nekojs.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * cleanroom 1.12 coremod transformer：把 {@code @StaticInjector} 标注的 public static 方法
 * （来自普通载体类 {@code com.tkisor.nekojs.inject.*}）在目标 MC 类加载时复制进去，作为该类的
 * public static 方法。
 *
 * <p>用途：给 {@code net.minecraft.item.Item} 真正注入 {@code of(String)} / {@code of(String,int)}，
 * 让 {@code Java.type('net.minecraft.item.Item').of('stone')} 在 GraalJS / 反射层面都可用。
 *
 * <p>与 neoforge 那次失败的 mixin plugin {@code postApply} PoC 的本质区别：这里走 launchwrapper 的
 * {@link IClassTransformer}，自己用 {@code ClassWriter(COMPUTE_FRAMES)} 写出字节码，<b>不经 Mixin 框架</b>，
 * 从而避开 Mixin ClassNode 处理流程导致的 bootstrap NPE（见 memory: mixin-static-inject-bootstrap-npe）。
 *
 * <p>方法体里的类引用由载体类的 javac 编译保证正确；复制时 {@code src.accept(dst)} 原样保留指令。
 * 载体类 {@code of} 方法体只引用 nekojs 自身类（{@code ItemStackAdapter}），不引用 MC API，故无 mapping 问题。
 */
public class NekoClassTransformer implements IClassTransformer {

    private static final String INJECTOR_DESC = "Lcom/tkisor/nekojs/api/annotation/StaticInjector;";

    /** 载体类全限定名（PoC 阶段硬编码；后续可改为注解扫描）。 */
    private static final String[] CARRIERS = {
            "com.tkisor.nekojs.inject.ItemStaticExtensions"
    };

    /** target internal name → 要注入的方法列表。 */
    private static final Map<String, List<MethodNode>> INJECTIONS = new HashMap<>();

    static {
        for (String carrier : CARRIERS) {
            registerCarrier(carrier);
        }
    }

    private static void registerCarrier(String carrierFqn) {
        String internal = carrierFqn.replace('.', '/');
        String path = "/" + internal + ".class";
        ClassNode node;
        try (InputStream in = NekoClassTransformer.class.getResourceAsStream(path)) {
            if (in == null) return;
            ClassReader reader = new ClassReader(in);
            node = new ClassNode();
            reader.accept(node, 0);
        } catch (IOException e) {
            return;
        }
        for (MethodNode m : node.methods) {
            String target = findInjectorTarget(m);
            if (target == null) continue;
            INJECTIONS.computeIfAbsent(target.replace('.', '/'), k -> new ArrayList<>()).add(m);
        }
    }

    private static String findInjectorTarget(MethodNode method) {
        if (method.visibleAnnotations == null) return null;
        for (AnnotationNode a : method.visibleAnnotations) {
            if (!INJECTOR_DESC.equals(a.desc)) continue;
            if (a.values == null) return null;
            for (int i = 0; i < a.values.size(); i += 2) {
                if ("value".equals(a.values.get(i))) {
                    Object v = a.values.get(i + 1);
                    return v == null ? null : v.toString();
                }
            }
        }
        return null;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        List<MethodNode> methods = INJECTIONS.get(transformedName.replace('.', '/'));
        if (methods == null || methods.isEmpty()) return basicClass;

        ClassReader reader = new ClassReader(basicClass);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        boolean changed = false;
        for (MethodNode src : methods) {
            boolean exists = node.methods.stream()
                    .anyMatch(m -> src.name.equals(m.name) && src.desc.equals(m.desc));
            if (exists) continue;
            MethodNode dst = new MethodNode(Opcodes.ASM9, src.access, src.name, src.desc, src.signature, null);
            src.accept(dst);
            node.methods.add(dst);
            changed = true;
        }
        if (!changed) return basicClass;

        // COMPUTE_FRAMES 自行重算栈帧/maxs；override getCommonSuperClass 避免在 coremod 早期阶段
        // 触发 MC 类的 Class.forName（注入的方法体不依赖真实共同父类）。
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
        node.accept(writer);
        return writer.toByteArray();
    }
}
