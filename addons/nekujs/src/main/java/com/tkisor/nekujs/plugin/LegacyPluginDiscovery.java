package com.tkisor.nekujs.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.jar.JarFile;

/** Reads owned plugin descriptors, applies their gates, and constructs only eligible plugin classes. */
public final class LegacyPluginDiscovery {
    private static final String DESCRIPTOR = "kubejs.plugins.txt";

    private LegacyPluginDiscovery() {}

    /** The caller owns this directory/union root or physical JAR and its classloader. */
    public record Source(String owner, Path root) {
        public Source {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(root, "root");
            if (owner.isBlank()) {
                throw new IllegalArgumentException("[NEKO-4190] Plugin source owner is blank");
            }
            root = root.toAbsolutePath().normalize();
        }
    }

    public record LoadedPlugin<T>(Source source, String className, T instance) {}

    /** A descriptor, linkage, type, or constructor failure rejects discovery instead of skipping a plugin. */
    public static final class DiscoveryException extends IllegalStateException {
        private DiscoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record Declaration(Source source, int line, String className) {
        private String location() {
            return source.owner() + " (" + source.root() + "!/" + DESCRIPTOR + ":" + line + ") " + className;
        }
    }

    /**
     * Preserves source order and descriptor line order. All gates and active-name duplicate checks finish
     * before class loading starts. Missing descriptors are allowed; malformed/unreadable sources fail.
     * Returned instances belong to the caller's plugin runtime, not a static discovery cache.
     */
    public static <T> List<LoadedPlugin<T>> load(List<Source> sources, ClassLoader loader,
                                                Predicate<String> isModLoaded, boolean loadClientPlugins,
                                                Class<T> pluginType) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(isModLoaded, "isModLoaded");
        Objects.requireNonNull(pluginType, "pluginType");
        var declarations = new LinkedHashMap<String, Declaration>();
        for (var source : List.copyOf(sources)) {
            List<String> lines;
            try {
                lines = readDescriptor(source);
            } catch (IOException | RuntimeException failure) {
                throw new DiscoveryException("[NEKO-4190] Plugin source read rejected: "
                        + source.owner() + " (" + source.root() + ")", failure);
            }
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).split("#", 2)[0].trim();
                if (line.isBlank()) continue;
                // Preserve KubeJS's literal-space token semantics, including empty tokens between spaces.
                String[] tokens = line.split(" ");
                var declaration = new Declaration(source, index + 1, tokens[0]);
                boolean eligible;
                try {
                    eligible = eligible(tokens, isModLoaded, loadClientPlugins);
                } catch (RuntimeException failure) {
                    throw new DiscoveryException("[NEKO-4190] Plugin declaration gates rejected: "
                            + declaration.location(), failure);
                }
                if (!eligible) continue;
                var previous = declarations.putIfAbsent(declaration.className(), declaration);
                if (previous != null) {
                    throw new DiscoveryException("[NEKO-4191] Duplicate eligible plugin declaration: "
                            + declaration.location() + "; first declared by " + previous.location(),
                            new IllegalArgumentException("Duplicate plugin class: " + declaration.className()));
                }
            }
        }
        var loaded = new ArrayList<LoadedPlugin<T>>();
        for (var declaration : declarations.values()) {
            Class<?> pluginClass;
            try {
                // Defer initialization until after the declared plugin type has been checked.
                pluginClass = Class.forName(declaration.className(), false, loader);
            } catch (ClassNotFoundException | LinkageError | RuntimeException failure) {
                throw new DiscoveryException("[NEKO-4192] Plugin class resolution rejected: "
                        + declaration.location(), failure);
            }
            if (!pluginType.isAssignableFrom(pluginClass)) {
                throw new DiscoveryException("[NEKO-4194] Plugin does not implement " + pluginType.getName()
                        + ": " + declaration.location(),
                        new ClassCastException(pluginClass.getName() + " is not a " + pluginType.getName()));
            }
            try {
                T instance = pluginType.cast(pluginClass.getDeclaredConstructor().newInstance());
                loaded.add(new LoadedPlugin<>(declaration.source(), declaration.className(), instance));
            } catch (InvocationTargetException failure) {
                throw new DiscoveryException("[NEKO-4193] Plugin construction rejected: "
                        + declaration.location(), failure.getCause());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                throw new DiscoveryException("[NEKO-4193] Plugin construction rejected: "
                        + declaration.location(), failure);
            }
        }
        return List.copyOf(loaded);
    }

    private static boolean eligible(String[] tokens, Predicate<String> isModLoaded, boolean loadClientPlugins) {
        for (int index = 1; index < tokens.length; index++) {
            if (tokens[index].equalsIgnoreCase("client")) {
                if (!loadClientPlugins) return false;
            } else if (!isModLoaded.test(tokens[index])) {
                return false;
            }
        }
        return true;
    }

    private static List<String> readDescriptor(Source source) throws IOException {
        if (Files.isDirectory(source.root())) {
            Path descriptor = source.root().resolve(DESCRIPTOR);
            if (Files.notExists(descriptor)) return List.of();
            try (var input = Files.newInputStream(descriptor)) {
                return readLines(input);
            }
        }
        try (var jar = new JarFile(source.root().toFile())) {
            var descriptor = jar.getJarEntry(DESCRIPTOR);
            if (descriptor == null) return List.of();
            try (var input = jar.getInputStream(descriptor)) {
                return readLines(input);
            }
        }
    }

    private static List<String> readLines(InputStream input) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()))) {
            var lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
            return List.copyOf(lines);
        }
    }
}
