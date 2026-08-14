package com.tkisor.nekojs.api.spec.processor;

import com.tkisor.nekojs.api.spec.PlatformAvailability;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 编译期处理器：验证各平台 Extension 接口真正覆盖了其 extends 的 Spec 接口的所有 neko$ 方法。
 *
 * <p>工作原理：扫描编译单元中所有接口声明，对每个直接或间接 extends 某 Spec 接口（带
 * {@code @PlatformAvailability} 注解的接口）的接口，检查 Spec 的每个 {@code neko$} 方法
 * 是否被该接口<b>自己声明</b>（而非继承哨兵 default）。
 *
 * <p>未覆盖 → 编译 ERROR（IDE 红线）。
 *
 * <p><b>平台范围强制（可选）</b>：编译时传 {@code -Anekojs.platform=<nf|cr>}（nf = NeoForge
 * 系列，cr = Cleanroom）后，额外按 {@link PlatformAvailability.Scope} 语义校验：scope 要求
 * 当前平台实现、但本次编译中没有任何非 Spec 接口<b>直接 extends</b> 该 spec → 编译 ERROR。
 * 未传该选项时只做方法覆盖校验（历史行为），spec 接口完全缺失时处理器静默跳过。
 */
@SupportedAnnotationTypes("*")
@SupportedOptions(SpecCoverageProcessor.PLATFORM_OPTION)
public class SpecCoverageProcessor extends AbstractProcessor {

    /** 编译选项名：目标平台（nf = NeoForge 系列 / 26.x + 1.21.1，cr = Cleanroom 1.12.2）。 */
    static final String PLATFORM_OPTION = "nekojs.platform";

    /** spec 接口所在包；扫描这些包以枚举全部 spec（含仅存在于 classpath 的）。 */
    private static final String[] SPEC_PACKAGES = {
        "com.tkisor.nekojs.api.spec",
        "com.tkisor.nekojs.api.spec.inject"
    };

    /** 平台范围校验只关心 nekojs 自己的接口层级，其它包（如 net.minecraft.*）不可能是 spec 链的一环。 */
    private static final String NEKOJS_PACKAGE_PREFIX = "com.tkisor.nekojs.";

    private Types types;
    private Elements elements;
    private Messager messager;

    /** 解析后的目标平台（"nf" / "cr"）；null 表示未传选项或值非法（非法时已报错）。 */
    private String platform;
    /** 保证平台选项解析 + 范围校验只在首个 round 执行一次。 */
    private boolean scopeCheckDone;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.messager = env.getMessager();
    }

    /** 不锁死具体版本：跟随运行编译器的最新 SourceVersion，避免新 JDK 上的过时警告。 */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver()) return false;

        if (!scopeCheckDone) {
            scopeCheckDone = true;
            resolvePlatformOption();
            if (platform != null) {
                enforcePlatformScope(round);
            }
        }

        for (Element root : round.getRootElements()) {
            if (!(root instanceof TypeElement type)) continue;
            if (type.getKind().isInterface()) {
                checkSpecCoverage(type);
            }
        }
        return false;
    }

    /**
     * 解析 -Anekojs.platform 选项；值非法时报 ERROR（快速失败，防止拼错后校验静默失效）。
     */
    private void resolvePlatformOption() {
        String raw = processingEnv.getOptions().get(PLATFORM_OPTION);
        if (raw == null) return; // 未启用平台范围校验
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("nf".equals(value) || "cr".equals(value)) {
            platform = value;
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "-A" + PLATFORM_OPTION + "='" + raw + "' 非法：只支持 nf（NeoForge 系列）或 cr（Cleanroom）");
        }
    }

    /**
     * 平台范围强制：枚举全部 spec 接口，按 scope 判定本平台必须实现哪些；
     * 必须 implement 却没有任何非 Spec 接口直接 extends → ERROR。
     */
    private void enforcePlatformScope(RoundEnvironment round) {
        Map<String, TypeElement> specs = new HashMap<>();
        collectSpecsFromPackages(specs);
        collectSpecsFromRoots(round, specs);

        if (specs.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                "已启用平台范围校验（-A" + PLATFORM_OPTION + "=" + platform
                + "），但未发现任何 @PlatformAvailability spec 接口，跳过范围校验");
            return;
        }

        // 收集被本次编译中非 Spec 接口直接 extends 的 spec（qualified name 去重）
        Set<String> covered = new HashSet<>();
        for (Element root : round.getRootElements()) {
            if (!(root instanceof TypeElement type) || !type.getKind().isInterface()) continue;
            if (isSpec(type)) continue; // Spec（或 Spec 间 extends）不算实现
            for (TypeMirror superType : type.getInterfaces()) {
                Element superElem = types.asElement(superType);
                if (superElem instanceof TypeElement specIface && isSpec(specIface)) {
                    covered.add(specIface.getQualifiedName().toString());
                }
            }
        }

        for (TypeElement spec : specs.values()) {
            PlatformAvailability.Scope scope = scopeOf(spec);
            if (!isRequiredOn(scope)) continue;
            if (covered.contains(spec.getQualifiedName().toString())) continue;
            messager.printMessage(Diagnostic.Kind.ERROR,
                spec.getQualifiedName() + " 声明 scope=" + scope
                + "，要求平台 [" + platform + "] 实现，但本次编译没有任何非 Spec 接口直接 extends 它",
                spec);
        }
    }

    /** 枚举 spec 包里的全部 spec 接口（含仅存在于 classpath / common-api jar 的）。 */
    private void collectSpecsFromPackages(Map<String, TypeElement> specs) {
        for (String packageName : SPEC_PACKAGES) {
            PackageElement pkg = elements.getPackageElement(packageName);
            if (pkg == null) continue;
            for (Element enclosed : pkg.getEnclosedElements()) {
                if (enclosed instanceof TypeElement type
                    && type.getKind() == ElementKind.INTERFACE
                    && isSpec(type)) {
                    specs.put(type.getQualifiedName().toString(), type);
                }
            }
        }
    }

    /** 兜底：从编译单元接口的父接口闭包中补充发现 spec（只下钻 nekojs 包，避免展开 MC 类型）。 */
    private void collectSpecsFromRoots(RoundEnvironment round, Map<String, TypeElement> specs) {
        for (Element root : round.getRootElements()) {
            if (!(root instanceof TypeElement type) || !type.getKind().isInterface()) continue;
            collectSpecsFromClosure(type, specs, new HashSet<>());
        }
    }

    private void collectSpecsFromClosure(TypeElement iface, Map<String, TypeElement> specs, Set<String> visited) {
        if (!visited.add(iface.getQualifiedName().toString())) return;
        if (isSpec(iface)) {
            specs.put(iface.getQualifiedName().toString(), iface);
        }
        for (TypeMirror superType : iface.getInterfaces()) {
            Element superElem = types.asElement(superType);
            if (superElem instanceof TypeElement superIface
                && superIface.getQualifiedName().toString().startsWith(NEKOJS_PACKAGE_PREFIX)) {
                collectSpecsFromClosure(superIface, specs, visited);
            }
        }
    }

    /** 读取 spec 的 scope；注解缺失时按默认值 ALL 对待。 */
    private PlatformAvailability.Scope scopeOf(TypeElement spec) {
        PlatformAvailability availability = spec.getAnnotation(PlatformAvailability.class);
        return availability != null ? availability.value() : PlatformAvailability.Scope.ALL;
    }

    /** scope 语义：ALL = 所有平台；NF_ONLY = 仅 nf；CR_ONLY = 仅 cr。 */
    private boolean isRequiredOn(PlatformAvailability.Scope scope) {
        return scope == PlatformAvailability.Scope.ALL
            || (scope == PlatformAvailability.Scope.NF_ONLY && "nf".equals(platform))
            || (scope == PlatformAvailability.Scope.CR_ONLY && "cr".equals(platform));
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
        return iface.getAnnotation(PlatformAvailability.class) != null;
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
