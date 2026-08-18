package com.tkisor.nekojs.utils;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ReflectionUtils {
    private ReflectionUtils() {}

    /**
     * Scans classpath for classes annotated with the given annotation.
     * For 1.12.2, scans the mod's own package first, then classpath.
     */
    public static void findAnnotationClasses(
            Class<? extends Annotation> annotationClass,
            String basePackage,
            Consumer<Class<?>> onFound,
            Runnable onComplete
    ) {
        String pkg = basePackage != null ? basePackage : "com.tkisor.nekojs";
        String pkgPath = pkg.replace('.', '/');

        // dev classpath 可能重复列出同一 jar（cleanroom run 配置里 common/common-api 各出现两次），
        // 同一 FQCN 会被 getResources 上报多次——按类名去重，保证每个插件类只回调一次
        Set<String> seen = new HashSet<>();

        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(pkgPath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    scanDirectory(new File(resource.getFile()), pkg, annotationClass, seen, onFound);
                } else if ("jar".equals(protocol)) {
                    scanJar(resource, pkgPath, annotationClass, seen, onFound);
                }
            }
        } catch (IOException e) {
            // Silently fail - classes will be discovered by Java's ServiceLoader or manual registration
        }

        if (onComplete != null) {
            onComplete.run();
        }
    }

    private static void scanDirectory(File directory, String pkg, Class<? extends Annotation> annotationClass,
                                       Set<String> seen, Consumer<Class<?>> onFound) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory()) {
                scanDirectory(file, pkg + "." + name, annotationClass, seen, onFound);
            } else if (name.endsWith(".class")) {
                String className = pkg + "." + name.substring(0, name.length() - 6);
                tryLoad(className, annotationClass, seen, onFound);
            }
        }
    }

    private static void scanJar(URL jarUrl, String pkgPath, Class<? extends Annotation> annotationClass,
                                 Set<String> seen, Consumer<Class<?>> onFound) {
        String jarPath = jarUrl.getPath();
        int sepIndex = jarPath.indexOf("!/");
        if (sepIndex > 0) jarPath = jarPath.substring(0, sepIndex);
        jarPath = jarPath.replaceFirst("^file:", "");

        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(pkgPath) && name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    tryLoad(className, annotationClass, seen, onFound);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void tryLoad(String className, Class<? extends Annotation> annotationClass,
                                 Set<String> seen, Consumer<Class<?>> onFound) {
        if (!seen.add(className)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            if (clazz.isAnnotationPresent(annotationClass) && !Modifier.isAbstract(clazz.getModifiers())) {
                onFound.accept(clazz);
            }
        } catch (Throwable ignored) {
        }
    }
}
