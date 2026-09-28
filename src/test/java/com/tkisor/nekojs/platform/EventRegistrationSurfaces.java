package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared driver for the node platform-gate tests: discovers the node's real event-registration
 * entry points and invokes their {@code registerEvents}/{@code registerClientEvents} hooks into
 * a fresh {@link EventGroupRegistry}.
 *
 * <p>Extracted from {@code EventSurfaceDomainGateTest} (ticket 33) so the declared-event-surface
 * golden test drives the exact same discovery input instead of a second, drifting copy. The
 * discovery contract is frozen there: neoforge = every {@link RegisterNekoJSPlugin} class on the
 * test classpath (the {@code NeoForgePluginLoader} scan scope), fabric = those classes plus the
 * {@code FabricPluginLoader.BUILTIN_PLUGINS} built-in list. No runtime gating ({@code clientOnly},
 * {@code requiredMods}) is applied — this proves the registration surface, not runtime activation.
 */
final class EventRegistrationSurfaces {

    private EventRegistrationSurfaces() {}

    /** Node identity injected by Gradle ({@code -Dnekojs.node}); baselines are keyed by node, not loader. */
    static String nodeId() {
        return System.getProperty("nekojs.node");
    }

    static String loaderId() {
        if (loadQuiet("com.tkisor.nekojs.platform.FabricPlatform") != null) return "fabric";
        if (loadQuiet("com.tkisor.nekojs.platform.NeoForgePlatform") != null) return "neoforge";
        return "unknown";
    }

    /**
     * Event-registration entry points: the union of the node's real plugin-discovery inputs.
     */
    @SuppressWarnings("unchecked")
    static List<String> pluginContributors(String loader) throws IOException {
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (String className : classNamesInPackage("com.tkisor.nekojs")) {
            Class<?> type = loadQuiet(className);
            if (type == null) continue;
            if (type.getAnnotation(RegisterNekoJSPlugin.class) != null) {
                names.add(className);
            }
        }
        if ("fabric".equals(loader)) {
            Class<?> fabricLoader = loadQuiet("com.tkisor.nekojs.fabric.FabricPluginLoader");
            if (fabricLoader != null) {
                try {
                    var field = fabricLoader.getDeclaredField("BUILTIN_PLUGINS");
                    field.setAccessible(true);
                    for (Class<?> type : (List<Class<?>>) field.get(null)) names.add(type.getName());
                } catch (ReflectiveOperationException | RuntimeException error) {
                    throw new IllegalStateException("无法读取 FabricPluginLoader.BUILTIN_PLUGINS："
                            + describe(error));
                }
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Result of driving one registration hook: the registered groups, or a {@link #failed}
     * marker when the holder class cannot initialize or the hook throws.
     */
    record Registration(Map<String, EventGroup> groups, String error) {
        static Registration failure(String detail) {
            return new Registration(Map.of(), detail);
        }
    }

    /**
     * Invokes one registration hook on a fresh registry and reads back the registered groups.
     * A missing hook (the holder is not a Contributor) yields empty groups and no error.
     */
    static Registration invokeRegistration(String className, String hookName) {
        Map<String, EventGroup> out = new java.util.LinkedHashMap<>();
        Method method;
        Object instance;
        Class<?> type;
        try {
            type = Class.forName(className, true, EventRegistrationSurfaces.class.getClassLoader());
            instance = type.getDeclaredConstructor().newInstance();
            method = findMethod(type, hookName);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return Registration.failure(describe(error));
        }
        if (method == null) return new Registration(out, null);
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        try {
            method.invoke(instance, registry);
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException ite && ite.getCause() != null
                    ? ite.getCause() : error;
            return Registration.failure(cause.getClass().getName() + ": " + cause.getMessage());
        }
        out.putAll(registry.view());
        return new Registration(out, null);
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name)) continue;
            if (method.getParameterCount() != 1) continue;
            if (!EventGroupRegistry.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
            return method;
        }
        return null;
    }

    static String describe(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return error.getClass().getName() + ": " + error.getMessage()
                + (cause != error ? " (root: " + cause + ")" : "");
    }

    // ---- classpath scan (directory and jar forms) ----

    private static final Map<String, List<String>> PACKAGE_CACHE = new HashMap<>();

    /** Recursively enumerates every class under a package prefix on the classpath. */
    private static List<String> classNamesInPackage(String packagePrefix) throws IOException {
        List<String> cached = PACKAGE_CACHE.get(packagePrefix);
        if (cached != null) return cached;
        String prefix = packagePrefix.replace('.', '/') + "/";
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) continue;
            java.io.File path = new java.io.File(entry);
            if (!path.exists()) continue;
            if (path.isDirectory()) {
                Path base = path.toPath().resolve(packagePrefix.replace('.', '/'));
                if (!Files.isDirectory(base)) continue;
                try (var stream = Files.walk(base)) {
                    stream.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                        String relative = path.toPath().relativize(p).toString()
                                .replace(java.io.File.separatorChar, '/');
                        names.add(relative.substring(0, relative.length() - ".class".length())
                                .replace('/', '.'));
                    });
                }
            } else if (entry.endsWith(".jar")) {
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(path)) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (!name.startsWith(prefix) || !name.endsWith(".class")) continue;
                        names.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
                    }
                }
            }
        }
        List<String> result = new ArrayList<>(names);
        PACKAGE_CACHE.put(packagePrefix, result);
        return result;
    }

    private static Class<?> loadQuiet(String name) {
        try {
            return Class.forName(name, false, EventRegistrationSurfaces.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError error) {
            return null;
        }
    }
}
