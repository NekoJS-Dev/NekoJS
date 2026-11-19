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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
 * {@code -Anekojs.platform=<nf26|nf121|cr>}, the processor also checks the
 * {@link PlatformAvailability.Scope} semantics: if the scope requires the current platform to
 * implement a spec but no non-Spec interface in this compilation <b>directly extends</b> that spec,
 * it reports a compile ERROR. Without the option it only checks method coverage; when no spec
 * interface exists at all, the processor skips silently.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions(SpecCoverageProcessor.PLATFORM_OPTION)
public class SpecCoverageProcessor extends AbstractProcessor {

    /** Compile option name for the target platform (nf26 / nf121 / cr). */
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

    /** Parsed target platform ("nf26" / "nf121" / "cr"); null means the option was absent or its
    value was invalid (an invalid value is already reported as an error). */
    private String platform;
    /** Ensures -Anekojs.platform is parsed and validated once, in the first round. */
    private boolean platformOptionResolved;
    /** Ensures classpath spec packages are scanned once. */
    private boolean packageSpecsCollected;
    /** Specs accumulated across all rounds, keyed by qualified name. */
    private final Map<String, TypeElement> allSpecs = new LinkedHashMap<>();
    /** Qualified names of specs directly extended by non-Spec interfaces across all rounds. */
    private final Set<String> coveredSpecs = new HashSet<>();

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
        if (!platformOptionResolved) {
            platformOptionResolved = true;
            resolvePlatformOption();
        }

        if (round.processingOver()) {
            if (platform != null) {
                finalizePlatformScope();
            }
            return false;
        }

        if (platform != null) {
            collectPlatformScopeRound(round);
        }

        for (Element root : round.getRootElements()) {
            visitInterfaces(root, this::checkSpecCoverage);
        }
        return false;
    }

    /**
     * Parses the -Anekojs.platform option; an invalid or missing-value option is reported as an
     * ERROR (fail fast, so a typo cannot silently disable the check).
     */
    private void resolvePlatformOption() {
        Map<String, String> options = processingEnv.getOptions();
        if (!options.containsKey(PLATFORM_OPTION)) return; // Platform scope checking is not enabled.
        String raw = options.get(PLATFORM_OPTION);
        if (raw == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "-A" + PLATFORM_OPTION + " 缺少平台值：只支持 nf26（NeoForge 26.x）/ nf121（1.21.1）/ cr（Cleanroom）");
            return;
        }
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
     * Collects spec interfaces and direct non-Spec implementations from the current round so
     * types generated in Round 2+ are included before final scope verification.
     */
    private void collectPlatformScopeRound(RoundEnvironment round) {
        if (!packageSpecsCollected) {
            packageSpecsCollected = true;
            collectSpecsFromPackages(allSpecs);
        }
        collectSpecsFromRoots(round, allSpecs);

        for (Element root : round.getRootElements()) {
            visitInterfaces(root, type -> {
                if (isSpec(type)) return; // A spec, or a spec extending another spec, does not count as an implementation.
                for (TypeMirror superType : type.getInterfaces()) {
                    Element superElem = types.asElement(superType);
                    if (superElem instanceof TypeElement specIface && isSpec(specIface)) {
                        coveredSpecs.add(specIface.getQualifiedName().toString());
                    }
                }
            });
        }
    }

    /**
     * Enforces platform scope once all processing rounds have completed: enumerates every spec
     * interface and uses its scope to decide which ones this platform must implement.
     */
    private void finalizePlatformScope() {
        if (allSpecs.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                "已启用平台范围校验（-A" + PLATFORM_OPTION + "=" + platform
                + "），但未发现任何 @PlatformAvailability spec 接口，跳过范围校验");
            return;
        }

        for (TypeElement spec : allSpecs.values()) {
            PlatformAvailability.Scope scope = scopeOf(spec);
            if (!isRequiredOn(scope)) continue;
            if (coveredSpecs.contains(spec.getQualifiedName().toString())) continue;
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
                visitInterfaces(enclosed, type -> {
                    if (isSpec(type)) {
                        specs.put(type.getQualifiedName().toString(), type);
                    }
                });
            }
        }
    }

    /** Fallback: finds more specs in the parent-interface closure of the compilation unit interfaces.
    It only descends into nekojs packages, so Minecraft types are never expanded. */
    private void collectSpecsFromRoots(RoundEnvironment round, Map<String, TypeElement> specs) {
        Set<String> visited = new HashSet<>();
        for (Element root : round.getRootElements()) {
            visitInterfaces(root, type -> collectSpecsFromClosure(type, specs, visited));
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

    /** Visits an interface element and recursively visits any nested interfaces inside a type. */
    private void visitInterfaces(Element element, Consumer<TypeElement> visitor) {
        if (!(element instanceof TypeElement type)) return;
        if (type.getKind().isInterface()) {
            visitor.accept(type);
        }
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed instanceof TypeElement nested) {
                visitInterfaces(nested, visitor);
            }
        }
    }

    /** Reads the scope of a spec (all collected specs carry {@code @PlatformAvailability}). */
    private PlatformAvailability.Scope scopeOf(TypeElement spec) {
        return spec.getAnnotation(PlatformAvailability.class).value();
    }

    /** Scope meaning: ALL = every platform; NF_ONLY = nf only; CR_ONLY = cr only. */
    private boolean isRequiredOn(PlatformAvailability.Scope scope) {
        return scope == PlatformAvailability.Scope.ALL
            || (scope == PlatformAvailability.Scope.NF_ONLY && ("nf26".equals(platform) || "nf121".equals(platform)))
            || (scope == PlatformAvailability.Scope.NF26_ONLY && "nf26".equals(platform))
            || (scope == PlatformAvailability.Scope.CR_ONLY && "cr".equals(platform));
    }

    /**
     * Checks whether a non-Spec interface directly or indirectly extends any Spec interface; if it
     * does, verifies that it covers every {@code neko$} method of those Specs.
     */
    private void checkSpecCoverage(TypeElement implInterface) {
        if (isSpec(implInterface)) return;

        Map<String, TypeElement> ancestorSpecs = new LinkedHashMap<>();
        collectAncestorSpecs(implInterface, ancestorSpecs, new HashSet<>());

        for (TypeElement specInterface : ancestorSpecs.values()) {
            // Find every neko$ method of the Spec and check whether implInterface (or one of its non-Spec
            // parent interfaces) declares it.
            for (Element member : specInterface.getEnclosedElements()) {
                if (!(member instanceof ExecutableElement specMethod)) continue;
                if (specMethod.getKind() != ElementKind.METHOD) continue;
                Set<Modifier> modifiers = specMethod.getModifiers();
                if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.PRIVATE)) continue;
                String name = specMethod.getSimpleName().toString();
                if (!name.startsWith("neko$")) continue;

                // Recursive check: did implInterface declare it, or did one of its non-Spec parent interfaces?
                if (!isCovered(implInterface, implInterface, specMethod, new HashSet<>())) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        implInterface.getQualifiedName() + " 未覆盖 " +
                        specInterface.getQualifiedName() + "." + name +
                        " —— Spec 方法必须被平台实现覆盖（不能继承哨兵 default）",
                        implInterface);
                }
            }
        }
    }

    /** Collects all Spec interfaces in the transitive superinterface hierarchy of {@code iface}. */
    private void collectAncestorSpecs(TypeElement iface, Map<String, TypeElement> specs, Set<String> visited) {
        for (TypeMirror superType : iface.getInterfaces()) {
            Element superElem = types.asElement(superType);
            if (!(superElem instanceof TypeElement superIface)) continue;
            if (!visited.add(superIface.getQualifiedName().toString())) continue;
            if (isSpec(superIface)) {
                specs.put(superIface.getQualifiedName().toString(), superIface);
            }
            collectAncestorSpecs(superIface, specs, visited);
        }
    }

    /**
     * Recursively checks whether implInterface or its chain of non-Spec parent interfaces declares a
     * method with the same name and signature as specMethod.
     *
     * <p>The key rule: a default method of the Spec interface itself (the sentinel) does not count
     * as coverage. Only a non-static, non-private method with the same name and signature declared
     * by a <b>non-Spec</b> interface counts.
     */
    private boolean isCovered(TypeElement rootImpl, TypeElement iface, ExecutableElement specMethod, Set<String> visited) {
        if (!visited.add(iface.getQualifiedName().toString())) {
            return false;
        }
        // A sentinel default on the Spec interface itself does not count as coverage.
        if (isSpec(iface)) {
            // Still check the non-Spec parents of the Spec recursively, in case intermediate interfaces exist.
            return checkNonSpecParents(rootImpl, iface, specMethod, visited);
        }
        String name = specMethod.getSimpleName().toString();
        // Check the members declared by iface itself.
        for (Element member : iface.getEnclosedElements()) {
            if (!(member instanceof ExecutableElement m)) continue;
            if (m.getKind() != ElementKind.METHOD) continue;
            Set<Modifier> modifiers = m.getModifiers();
            if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.PRIVATE)) continue;
            if (m.getSimpleName().toString().equals(name)
                && sameSignature(rootImpl, m, specMethod)) {
                return true;
            }
        }
        // Check the parent interfaces recursively.
        for (TypeMirror parentType : iface.getInterfaces()) {
            Element parentElem = types.asElement(parentType);
            if (parentElem instanceof TypeElement parentIface) {
                if (isCovered(rootImpl, parentIface, specMethod, visited)) {
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
    private boolean checkNonSpecParents(TypeElement rootImpl, TypeElement specIface, ExecutableElement specMethod, Set<String> visited) {
        for (TypeMirror parentType : specIface.getInterfaces()) {
            Element parentElem = types.asElement(parentType);
            if (parentElem instanceof TypeElement parentIface) {
                if (isCovered(rootImpl, parentIface, specMethod, visited)) {
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
     * Matches signatures in the context of {@code rootImpl}, supporting generic substitution and
     * raw/erased overrides via {@link Types#isSubsignature}.
     */
    private boolean sameSignature(TypeElement rootImpl, ExecutableElement a, ExecutableElement b) {
        ExecutableType typeA = resolveExecutableType(rootImpl, a);
        ExecutableType typeB = resolveExecutableType(rootImpl, b);
        if (typeA != null && typeB != null) {
            return types.isSubsignature(typeA, typeB);
        }
        if (a.getParameters().size() != b.getParameters().size()) return false;
        for (int i = 0; i < a.getParameters().size(); i++) {
            if (!types.isSameType(types.erasure(a.getParameters().get(i).asType()),
                                  types.erasure(b.getParameters().get(i).asType()))) {
                return false;
            }
        }
        return true;
    }

    private ExecutableType resolveExecutableType(TypeElement rootImpl, ExecutableElement method) {
        if (rootImpl.asType() instanceof DeclaredType containing) {
            try {
                TypeMirror memberType = types.asMemberOf(containing, method);
                if (memberType instanceof ExecutableType execType) {
                    return execType;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall back to the method's own declared ExecutableType below.
            }
        }
        return method.asType() instanceof ExecutableType execType ? execType : null;
    }
}
