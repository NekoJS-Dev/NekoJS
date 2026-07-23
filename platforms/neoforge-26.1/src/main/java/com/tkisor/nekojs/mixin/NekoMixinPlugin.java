package com.tkisor.nekojs.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NekoJS mixin config plugin：负责把 {@code @StaticInjector} 标注的静态方法（来自普通载体类）
 * 在 mixin {@code postApply} 阶段复制到 target 类，从而给 MC 原生类注入 public static 方法
 * （SpongePowered Mixin 框架本身在 {@code checkMethodVisibility} 硬拒绝注入 public static 方法）。
 *
 * <p>流程：
 * <ol>
 *   <li>{@code onLoad}：用 ASM 读载体类字节码（{@code getResourceAsStream}，不加载载体类，
 *       避免 mixin 阶段触发 MC 类初始化），收集每个 {@code @StaticInjector} 方法的 target 与 MethodNode。</li>
 *   <li>{@code postApply}：target 类的 mixin 应用完后，把对应的 MethodNode（javac 已编译字节码）
 *       整体复制到 target 的 {@code methods}。方法体里的类引用由载体类编译流程保证正确 mapping。</li>
 * </ol>
 *
 * <p>注：postApply 每应用到 target 的每个 mixin 都会 fire 一次，所以用方法名+desc 去重。
 */
public class NekoMixinPlugin implements IMixinConfigPlugin {
    private static final String INJECTOR_DESC = "Lcom/tkisor/nekojs/api/annotation/StaticInjector;";

    /** target internal name → 要注入的方法列表。 */
    private static final Map<String, List<MethodNode>> INJECTIONS = new HashMap<>();

    /** 载体类全限定名（硬编码列表；PoC 阶段，后续可改为注解扫描）。 */
    private static final String[] CARRIERS = {
            "com.tkisor.nekojs.inject.ItemStaticExtensions"
    };

    @Override
    public void onLoad(String mixinPackage) {
        for (String carrier : CARRIERS) {
            registerCarrier(carrier);
        }
    }

    private static void registerCarrier(String carrierFqn) {
        String internal = carrierFqn.replace('.', '/');
        String path = "/" + internal + ".class";
        ClassNode node;
        try (InputStream in = NekoMixinPlugin.class.getResourceAsStream(path)) {
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
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        List<MethodNode> methods = INJECTIONS.get(targetClassName.replace('.', '/'));
        if (methods == null || methods.isEmpty()) return;
        for (MethodNode src : methods) {
            boolean exists = targetClass.methods.stream()
                    .anyMatch(m -> src.name.equals(m.name) && src.desc.equals(m.desc));
            if (exists) continue;
            targetClass.methods.add(cloneMethod(src));
        }
    }

    /** 把载体类已编译的 MethodNode 复制一份；instructions 原样保留（含正确的 owner 引用与 maxs）。 */
    private static MethodNode cloneMethod(MethodNode src) {
        MethodNode dst = new MethodNode();
        src.accept(dst);
        return dst;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> set, Set<String> set1) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {}
}
