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
 * Compile-time processor that checks that each platform Extension interface really covers every
 * {@code neko$} method of the Spec interfaces it extends.
 *
 * <p>How it works: it scans every interface declared in the compilation unit. For each interface
 * that directly or indirectly extends a Spec interface (an interface annotated with
 * {@code @PlatformAvailability}), it checks whether each {@code neko$} method of the Spec is
 * <b>declared by that interface itself</b> rather than inherited from the sentinel default.
 *
 * <p>Missing coverage becomes a compile ERROR (a red line in the IDE).
 *
 * <p><b>Platform scope enforcement (optional)</b>: when the compiler receives
 * {@code -Anekojs.platform=<nf|cr>} (nf = the NeoForge family, cr = Cleanroom), the processor also
 * checks the {@link PlatformAvailability.Scope} semantics: if the scope requires the current
 * platform to implement a spec but no non-Spec interface in this compilation <b>directly extends</b>
 * that spec, it reports a compile ERROR. Without the option it only checks method coverage; when no
 * spec interface exists at all, the processor skips silently.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions(SpecCoverageProcessor.PLATFORM_OPTION)
public class SpecCoverageProcessor extends AbstractProcessor {

    /** Compile option name for the target platform (nf = the NeoForge family / 26.x + 1.21.1,
    cr = Cleanroom 1.12.2). */
    static final String PLATFORM_OPTION = "nekojs.platform";

    /** Packages that hold the spec interfaces; they are scanned to enumerate every spec, including
    the ones that exist only on the classpath. */
    private static final String[] SPEC_PACKAGES = {
        "com.tkisor.nekojs.api.spec",
        "com.tkisor.nekojs.api.spec.inject"
    };

    /** Platform scope checks only care about the nekojs interface hierarchy; other packages such as
    net.minecraft.* can never be part of a spec chain. */
    private static final String NEKOJS_PACKAGE_PREFIX = "com.tkisor.nekojs.";

    private Types types;
    private Elements elements;
    private Messager messager;

    /** Parsed target platform ("nf" / "cr"); null means the option was absent or its value was
    invalid (an invalid value is already reported as an error). */
    private String platform;
    /** Makes platform option parsing and scope checking run once, in the first round only. */
    private boolean scopeCheckDone;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.messager = env.getMessager();
    }

    /** Does not pin a version: follows the latest SourceVersion of the running compiler, so new JDKs
    do not report outdated warnings. */
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
     * Parses the -Anekojs.platform option; an invalid value is reported as an ERROR (fail fast, so
     * a typo cannot silently disable the check).
     */
    private void resolvePlatformOption() {
        String raw = processingEnv.getOptions().get(PLATFORM_OPTION);
        if (raw == null) return; // Platform scope checking is not enabled.
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("nf26".equals(value) || "nf121".equals(value) || "cr".equals(value)) {
            platform = value;
        } else if ("nf".equals(value)) {
            // "nf" cannot tell 26.x from 1.21.1, so fail fast and force the caller to name the platform.
            messager.printMessage(Diagnostic.Kind.ERROR,
                "-A" + PLATFORM_OPTION + "=nf 已废弃：请按平台传 nf26（NeoForge 26.x）/ nf121（1.21.1）/ cr（Cleanroom）");
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "-A" + PLATFORM_OPTION + "='" + raw + "' 非法：只支持 nf26（NeoForge 26.x）/ nf121（1.21.1）/ cr（Cleanroom）");
        }
    }

    /**
     * Enforces platform scope: enumerates every spec interface and uses its scope to decide which
     * ones this platform must implement. A required spec that no non-Spec interface directly
     * extends is an ERROR.
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

        // Collect the specs that non-Spec interfaces in this compilation extend directly, deduplicated by
        // qualified name.
        Set<String> covered = new HashSet<>();
        for (Element root : round.getRootElements()) {
            if (!(root instanceof TypeElement type) || !type.getKind().isInterface()) continue;
            if (isSpec(type)) continue; // A spec, or a spec extending another spec, does not count as an implementation.
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

    /** Enumerates every spec interface in the spec packages, including the ones that exist only on the
    classpath or in the common-api jar. */
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

    /** Fallback: finds more specs in the parent-interface closure of the compilation unit interfaces.
    It only descends into nekojs packages, so Minecraft types are never expanded. */
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

    /** Reads the scope of a spec; a missing annotation is treated as the default value ALL. */
    private PlatformAvailability.Scope scopeOf(TypeElement spec) {
        PlatformAvailability availability = spec.getAnnotation(PlatformAvailability.class);
        return availability != null ? availability.value() : PlatformAvailability.Scope.ALL;
    }

    /** Scope meaning: ALL = every platform; NF_ONLY = nf only; CR_ONLY = cr only. */
    private boolean isRequiredOn(PlatformAvailability.Scope scope) {
        return scope == PlatformAvailability.Scope.ALL
            || (scope == PlatformAvailability.Scope.NF_ONLY && ("nf26".equals(platform) || "nf121".equals(platform)))
            || (scope == PlatformAvailability.Scope.NF26_ONLY && "nf26".equals(platform))
            || (scope == PlatformAvailability.Scope.CR_ONLY && "cr".equals(platform));
    }

    /**
     * Checks whether the given interface extends a Spec interface; if it does, verifies that the
     * interface covers every neko$ method of that Spec.
     */
    private void checkSpecCoverage(TypeElement implInterface) {
        for (TypeMirror superType : implInterface.getInterfaces()) {
            Element superElem = types.asElement(superType);
            if (!(superElem instanceof TypeElement specInterface)) continue;
            if (!isSpec(specInterface)) continue;

            // Find every neko$ method of the Spec and check whether implInterface (or one of its non-Spec
            // parent interfaces) declares it.
            for (Element member : specInterface.getEnclosedElements()) {
                if (!(member instanceof ExecutableElement specMethod)) continue;
                String name = specMethod.getSimpleName().toString();
                if (!name.startsWith("neko$")) continue;
                if (specMethod.getKind() != javax.lang.model.element.ElementKind.METHOD) continue;

                // Recursive check: did implInterface declare it, or did one of its non-Spec parent interfaces?
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
     * Recursively checks whether implInterface or its chain of non-Spec parent interfaces declares a
     * method with the same name and signature as specMethod.
     *
     * <p>The key rule: a default method of the Spec interface itself (the sentinel) does not count
     * as coverage. Only a method with the same name and signature declared by a <b>non-Spec</b>
     * interface counts.
     */
    private boolean isCovered(TypeElement iface, ExecutableElement specMethod) {
        // A sentinel default on the Spec interface itself does not count as coverage.
        if (isSpec(iface)) {
            // Still check the non-Spec parents of the Spec recursively, in case intermediate interfaces exist.
            return checkNonSpecParents(iface, specMethod);
        }
        String name = specMethod.getSimpleName().toString();
        // Check the members declared by iface itself.
        for (Element member : iface.getEnclosedElements()) {
            if (member instanceof ExecutableElement m
                && m.getSimpleName().toString().equals(name)
                && sameSignature(m, specMethod)) {
                return true;
            }
        }
        // Check the parent interfaces recursively.
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
     * A sentinel default on the Spec interface does not count as coverage; checks its non-Spec
     * parent interfaces recursively.
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
     * Tells whether an interface is a Spec interface, that is, one annotated with
     * @PlatformAvailability.
     */
    private boolean isSpec(TypeElement iface) {
        return iface.getAnnotation(PlatformAvailability.class) != null;
    }

    /**
     * Matches signatures: the same parameter count and the same type for every parameter.
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
