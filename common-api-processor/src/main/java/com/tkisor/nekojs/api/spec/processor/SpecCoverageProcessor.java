package com.tkisor.nekojs.api.spec.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import java.util.HashSet;
import java.util.Set;

/**
 * 编译期处理器：验证各平台 Extension 接口真正覆盖了其 extends 的 Spec 接口的所有 neko$ 方法。
 *
 * <p>工作原理：扫描编译单元中所有接口声明，对每个直接或间接 extends 某 Spec 接口（带
 * {@code @PlatformAvailability} 注解的接口）的接口，检查 Spec 的每个 {@code neko$} 方法
 * 是否被该接口<b>自己声明</b>（而非继承哨兵 default）。
 *
 * <p>未覆盖 → 编译 ERROR（IDE 红线）。
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class SpecCoverageProcessor extends AbstractProcessor {

    private Types types;
    private Elements elements;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.messager = env.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver()) return false;

        for (Element root : round.getRootElements()) {
            if (!(root instanceof TypeElement type)) continue;
            if (type.getKind().isInterface()) {
                checkSpecCoverage(type);
            }
        }
        return false;
    }

    /**
     * 检查给定接口是否 extends 某 Spec 接口；若是，验证 Spec 的每个 neko$ 方法都被覆盖。
     */
    private void checkSpecCoverage(TypeElement implInterface) {
        for (TypeMirror superType : implInterface.getInterfaces()) {
            Element superElem = types.asElement(superType);
            if (!(superElem instanceof TypeElement specInterface)) continue;
            if (!isSpec(specInterface)) continue;

            // 找出 Spec 的所有 neko$ 方法，逐个检查是否被 implInterface（或其非 Spec 父接口）声明
            for (Element member : specInterface.getEnclosedElements()) {
                if (!(member instanceof ExecutableElement specMethod)) continue;
                String name = specMethod.getSimpleName().toString();
                if (!name.startsWith("neko$")) continue;
                if (specMethod.getKind() != javax.lang.model.element.ElementKind.METHOD) continue;

                // 递归检查：implInterface 自己声明了？或其非 Spec 父接口声明了？
                if (!isCovered(implInterface, specMethod)) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        implInterface.getQualifiedName() + " 未覆盖 " +
                        specInterface.getQualifiedName() + "." + name +
                        " —— Spec 方法必须被平台实现覆盖（不能继承哨兵 default）",
                        implInterface);
                }
            }
        }
    }

    /**
     * 递归检查：implInterface 或其非 Spec 父接口链中，是否声明了与 specMethod 同名同签名的方法。
     *
     * <p>关键：Spec 接口自己的 default 方法（哨兵）不算覆盖——只有<b>非 Spec</b> 接口声明了
     * 同名同签名方法才算覆盖。
     */
    private boolean isCovered(TypeElement iface, ExecutableElement specMethod) {
        // Spec 接口自己的哨兵 default 不算覆盖
        if (isSpec(iface)) {
            // 但仍需递归检查 Spec 的非 Spec 父接口（如果有中间接口的话）
            return checkNonSpecParents(iface, specMethod);
        }
        String name = specMethod.getSimpleName().toString();
        // 检查 iface 自己声明的成员
        for (Element member : iface.getEnclosedElements()) {
            if (member instanceof ExecutableElement m
                && m.getSimpleName().toString().equals(name)
                && sameSignature(m, specMethod)) {
                return true;
            }
        }
        // 递归检查父接口
        for (TypeMirror parentType : iface.getInterfaces()) {
            Element parentElem = types.asElement(parentType);
            if (parentElem instanceof TypeElement parentIface) {
                if (isCovered(parentIface, specMethod)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Spec 接口的哨兵 default 不算覆盖；递归检查其非 Spec 父接口。
     */
    private boolean checkNonSpecParents(TypeElement specIface, ExecutableElement specMethod) {
        for (TypeMirror parentType : specIface.getInterfaces()) {
            Element parentElem = types.asElement(parentType);
            if (parentElem instanceof TypeElement parentIface) {
                if (isCovered(parentIface, specMethod)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断一个接口是否为 Spec 接口（带 @PlatformAvailability 注解）。
     */
    private boolean isSpec(TypeElement iface) {
        return iface.getAnnotation(com.tkisor.nekojs.api.spec.PlatformAvailability.class) != null;
    }

    /**
     * 签名匹配：参数个数 + 每个参数类型相同。
     */
    private boolean sameSignature(ExecutableElement a, ExecutableElement b) {
        if (a.getParameters().size() != b.getParameters().size()) return false;
        for (int i = 0; i < a.getParameters().size(); i++) {
            if (!types.isSameType(a.getParameters().get(i).asType(),
                                   b.getParameters().get(i).asType())) {
                return false;
            }
        }
        return true;
    }
}
