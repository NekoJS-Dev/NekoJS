package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;
import com.tkisor.nekojs.core.reflect.JavaGenericTypes;
import com.tkisor.nekojs.probe.ir.TypeReflector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Collects a shared set of classes, split out of {@link ProbeCoordinator}. It walks a light BFS from
 * the event and binding seeds and filters classes by the {@link ProbeConfig} package rules. It
 * collects classes only and never emits declarations. A backend that needs more, such as adapter
 * target classes or host types referenced through adapter aliases, adds them itself. Stateless and
 * all static.
 */
final class ProbeClassCollector {

    private ProbeClassCollector() {
    }

    /**
     * Collects the seeds at depth 0: event types, event dispatch key types, binding Java types and the
     * extraDocTypes of proxy bindings. It then walks the reachable closure with BFS over superclasses,
     * interfaces, public constructor parameters, public method return types and parameters, public
     * fields, ParameterizedType raw types plus type arguments, and GenericArrayType components. Depth is
     * capped by {@code scan.maxDepth}; a value of 0 or less falls back to 5.
     *
     * <p>The BFS visit order depends on reflection order (getDeclaredMethods, getInterfaces and friends),
     * which the JVM does not guarantee and which can differ across JDK versions and platforms. The result
     * is sorted by fully qualified name before it is returned, so probe output is reproducible.
     *
     * <p>Unavailable signature metadata stops traversal of that type, while other queued types remain
     * collectable. The coordinator reports the incomplete closure to each backend.
     */
    static LinkedHashSet<Class<?>> collect(NekoScriptCatalogSnapshot snapshot, ProbeConfig cfg) {
        return collect(snapshot, cfg, new ArrayList<>());
    }

    static LinkedHashSet<Class<?>> collect(NekoScriptCatalogSnapshot snapshot, ProbeConfig cfg, List<String> warnings) {
        List<String> platformPkgs = ProbeConfigLoader.platformDefaultPackages();
        Set<String> forcedPkgs = cfg.forcedPackages();
        LinkedHashSet<Class<?>> visited = new LinkedHashSet<>();
        Queue<Object[]> queue = new LinkedList<>();

        // Seed classes: event types and binding types, at depth 0
        for (EventCatalogEntry event : java.util.stream.Stream.concat(snapshot.events().stream(),
                snapshot.moduleEvents().stream().map(com.tkisor.nekojs.api.catalog.ModuleEventCatalogEntry::event)).toList()) {
            if (event.eventType() != null) queue.add(new Object[]{event.eventType(), 0});
            if (event.dispatchKeyType() != null) queue.add(new Object[]{event.dispatchKeyType(), 0});
        }
        for (BindingCatalogEntry binding : java.util.stream.Stream.concat(snapshot.bindings().stream(),
                snapshot.moduleBindings().stream().map(com.tkisor.nekojs.api.catalog.ModuleBindingCatalogEntry::binding)).toList()) {
            if (binding.javaType() != null) queue.add(new Object[]{binding.javaType(), 0});
            // The extraDocTypes of proxy bindings (the delegate-target MC classes) are seeds too
            for (Class<?> extra : binding.extraDocTypes()) {
                queue.add(new Object[]{extra, 0});
            }
        }

        // Whole-field recipe inputs can introduce host types absent from event and binding signatures.
        for (var namespace : snapshot.recipeNamespaces()) {
            for (var schema : namespace.schemaTypes()) {
                if (schema.unsupportedReason() != null) continue;
                for (var field : schema.fields()) collectShapeToQueue(field.inputShape(), queue);
            }
        }

        // Authored declarations can reference types absent from every reflected signature.
        // Treat those dependencies as seeds so they share scan filters, IR edits, and depth limits.
        for (var declaration : snapshot.classDeclarations()) {
            if (!passesScanFilter(cfg, declaration.targetType().getName(), platformPkgs, forcedPkgs)) continue;
            queue.add(new Object[]{declaration.targetType(), 0});
            declaration.imports().stream().sorted(Comparator.comparing(Class::getName))
                    .forEach(imported -> queue.add(new Object[]{imported, 0}));
        }

        for (String name : com.tkisor.nekojs.probe.backend.typescript.MixinHookDeclarations.referencedClasses(
                com.tkisor.nekojs.core.mixin.MixinCarriers.discover())) {
            try { queue.add(new Object[]{Class.forName(name, false, ProbeClassCollector.class.getClassLoader()), 0}); }
            catch (ClassNotFoundException | LinkageError failure) { warnings.add("Installed mixin hook type unavailable: " + name); }
        }

        int maxDepth = cfg.scan().maxDepth() <= 0 ? 5 : cfg.scan().maxDepth();

        while (!queue.isEmpty()) {
            Object[] entry = queue.poll();
            Class<?> cls = (Class<?>) entry[0];
            int depth = (int) entry[1];

            if (depth > maxDepth) continue;
            if (cls == null || cls.isPrimitive() || cls == Object.class) continue;
            if (visited.contains(cls)) continue;
            if (!passesScanFilter(cfg, cls.getName(), platformPkgs, forcedPkgs)) continue;

            visited.add(cls);

            int nextDepth = depth + 1;
            if (nextDepth > maxDepth) continue;

            try {
                if (cls.getGenericSuperclass() != null) collectTypeToQueue(cls.getGenericSuperclass(), queue, nextDepth);
                for (Type iface : cls.getGenericInterfaces()) collectTypeToQueue(iface, queue, nextDepth);
                for (TypeVariable<?> variable : JavaGenericTypes.parameters(cls)) {
                    for (Type bound : variable.getBounds()) collectTypeToQueue(bound, queue, nextDepth);
                }

                for (Constructor<?> ctor : TypeReflector.declarationConstructors(cls)) {
                    for (Type p : ctor.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                    for (TypeVariable<?> variable : ctor.getTypeParameters()) {
                        for (Type bound : variable.getBounds()) collectTypeToQueue(bound, queue, nextDepth);
                    }
                }
                for (Method method : TypeReflector.scanDeclarationMethods(cls).methods()) {
                    if (Modifier.isPublic(method.getModifiers())) {
                        collectTypeToQueue(method.getGenericReturnType(), queue, nextDepth, true);
                        for (Type p : method.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                        for (TypeVariable<?> variable : method.getTypeParameters()) {
                            for (Type bound : variable.getBounds()) collectTypeToQueue(bound, queue, nextDepth);
                        }
                    }
                }
                for (Field field : cls.getDeclaredFields()) {
                    if (Modifier.isPublic(field.getModifiers())) collectTypeToQueue(field.getGenericType(), queue, nextDepth);
                }
            } catch (RuntimeException | LinkageError unavailable) {
                // A loader can reject a signature dependency even after its declaring class loaded.
                // Keep other queued types and report the incomplete closure to every backend.
                String warning = "[NEKO-4029] Probe type scan incomplete for " + cls.getName()
                        + "; reachable declarations may be missing (" + unavailable + ")";
                warnings.add(warning);
                NekoJS.LOGGER.warn(warning, unavailable);
            }
        }

        List<Class<?>> sorted = new ArrayList<>(visited);
        sorted.sort(Comparator.comparing(Class::getName));
        return new LinkedHashSet<>(sorted);
    }

    /**
     * Filter decision used by collect. Excludes always apply, FULL passes the include check, and SMART
     * uses the whitelist plus the forceScanMods additions:
     * <ul>
     *   <li>hits {@code excludePackages} -> skipped, in FULL mode too</li>
     *   <li>mode == FULL -> collected (the include whitelist is skipped; maxDepth still caps the closure)</li>
     *   <li>any other mode (SMART and so on) -> {@link ProbeConfig#isRelevantClass}; a forceScanMods
     *       prefix hit is collected as well</li>
     * </ul>
     */
    private static boolean passesScanFilter(ProbeConfig cfg, String fqn, List<String> platformPkgs, Set<String> forcedPkgs) {
        if (cfg.isExcluded(fqn)) return false;
        if (cfg.scan().mode() == ProbeConfig.ScanConfig.ScanMode.FULL) return true;
        if (cfg.isRelevantClass(fqn, platformPkgs)) return true;
        for (String pkg : forcedPkgs) {
            if (ProbeConfig.matchesPackageRule(pkg, fqn)) return true;
        }
        return false;
    }

    private static void collectShapeToQueue(com.tkisor.nekojs.api.AdapterInputShape shape, Queue<Object[]> queue) {
        if (shape instanceof com.tkisor.nekojs.api.AdapterInputShape.HostValue host) {
            queue.add(new Object[]{host.cls(), 0});
        } else if (shape instanceof com.tkisor.nekojs.api.AdapterInputShape.ArrayOfValue array) {
            collectShapeToQueue(array.element(), queue);
        } else if (shape instanceof com.tkisor.nekojs.api.AdapterInputShape.UnionValue union) {
            union.members().forEach(member -> collectShapeToQueue(member, queue));
        } else if (shape instanceof com.tkisor.nekojs.api.AdapterInputShape.ObjectValue object) {
            object.slots().forEach(slot -> collectShapeToQueue(slot.shape(), queue));
        } else if (shape instanceof com.tkisor.nekojs.api.AdapterInputShape.TemplateValue template) {
            collectShapeToQueue(template.hole(), queue);
        } else if (shape instanceof com.tkisor.nekojs.api.AdapterInputShape.NamedValue named) {
            collectShapeToQueue(named.shape(), queue);
        } else if (shape instanceof com.tkisor.nekojs.api.AdapterInputShape.CallableValue callable) {
            callable.parameters().forEach(parameter -> collectShapeToQueue(parameter.shape(), queue));
            collectShapeToQueue(callable.result(), queue);
        }
    }

    private static void collectTypeToQueue(Type type, Queue<Object[]> queue, int depth) {
        collectTypeToQueue(type, queue, depth, false);
    }

    private static void collectTypeToQueue(Type type, Queue<Object[]> queue, int depth, boolean output) {
        if (type instanceof Class<?> cls) {
            if (cls.isArray()) {
                // Array dimensions do not add signature edges; scan the actual component at this depth.
                collectTypeToQueue(cls.getComponentType(), queue, depth, false);
            } else {
                queue.add(new Object[]{output ? com.tkisor.nekojs.api.ScriptTypeViews.outputClass(cls) : cls, depth});
            }
        } else if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> rawCls) {
                queue.add(new Object[]{rawCls, depth});
            }
            boolean functional = FunctionalInterfaceResolver.resolve(pt) != null;
            for (Type arg : JavaGenericTypes.arguments(pt)) {
                if (functional && arg instanceof WildcardType wildcard) {
                    // Callback signatures project wildcard bounds into concrete host parameter types.
                    // Follow only these bounds, through the existing depth and package filters.
                    for (Type bound : wildcard.getLowerBounds()) collectTypeToQueue(bound, queue, depth, false);
                    for (Type bound : wildcard.getUpperBounds()) collectTypeToQueue(bound, queue, depth, false);
                } else {
                    collectTypeToQueue(arg, queue, depth, false);
                }
            }
        } else if (type instanceof GenericArrayType gat) {
            collectTypeToQueue(gat.getGenericComponentType(), queue, depth, false);
        } else if (type instanceof WildcardType wildcard) {
            // Ordinary wildcard slots render their upper bound, even inside non-functional containers.
            // The bound is part of this signature edge; its members still use the next BFS depth.
            for (Type bound : wildcard.getUpperBounds()) collectTypeToQueue(bound, queue, depth, false);
        }
        // Declaration parameter bounds are queued explicitly at their owner's next depth.
        // Other nested type variables do not expand the scan.
    }
}
