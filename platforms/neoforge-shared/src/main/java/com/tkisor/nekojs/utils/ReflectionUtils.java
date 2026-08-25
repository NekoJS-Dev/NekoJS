package com.tkisor.nekojs.utils;

import com.tkisor.nekojs.NekoJS;
import lombok.experimental.UtilityClass;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.*;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * This file is derived from LDLib2.
 *
 * <p><b>Source repository:</b><br>
 * <a href="https://github.com/Low-Drag-MC/LDLib2">
 * https://github.com/Low-Drag-MC/LDLib2
 * </a>
 *
 * <p><b>Original work copyright:</b><br>
 * Copyright (c) 2021-2025 Low-Drag MC and contributors
 *
 * <p><b>License:</b><br>
 * GNU General Public License v3.0 (GPL-3.0)
 *
 * <p><b>License text:</b><br>
 * <a href="https://github.com/Low-Drag-MC/LDLib2/blob/1.21/LICENSE">
 * https://github.com/Low-Drag-MC/LDLib2/blob/1.21/LICENSE
 * </a>
 *
 * <p><b>Modifications copyright:</b><br>
 * Copyright (c) 2025 Tki_sor
 *
 * <p>
 * This file is licensed under the same GPL-3.0 terms.
 * See the LICENSE file in the project root for details.
 */
@UtilityClass
public final class ReflectionUtils {

    public static Class<?> getRawType(Type type, Class<?> fallback) {
        var rawType = getRawType(type);
        return rawType != null ? rawType : fallback;
    }

    public static Class<?> getRawType(Type type) {
        return switch (type) {
            case Class<?> aClass -> aClass;
            case GenericArrayType genericArrayType -> getRawType(genericArrayType.getGenericComponentType());
            case ParameterizedType parameterizedType -> getRawType(parameterizedType.getRawType());
            case null, default -> null;
        };
    }

    public static <A extends Annotation> void findAnnotationClasses(Class<A> annotationClass,
                                                                    @Nullable Predicate<Map<String, Object>> annotationPredicate,
                                                                    Consumer<Class<?>> consumer,
                                                                    Runnable onFinished) {
        org.objectweb.asm.Type annotationType = org.objectweb.asm.Type.getType(annotationClass);
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (annotationType.equals(annotation.annotationType()) && annotation.targetType() == ElementType.TYPE) {
                    if (annotationPredicate == null || annotationPredicate.test(annotation.annotationData())) {
                        try {
                            consumer.accept(Class.forName(annotation.memberName(), false, ReflectionUtils.class.getClassLoader()));
                        } catch (Throwable throwable) {
                            NekoJS.LOGGER.error("Failed to load class for notation: {}", annotation.memberName(), throwable);
                        }
                    }
                }
            }
        }
        onFinished.run();
    }

    public static <A extends Annotation> void findAnnotationStaticField(Class<A> annotationClass,
                                                                        @Nullable Predicate<Map<String, Object>> annotationPredicate,
                                                                        BiConsumer<Field, Object> consumer,
                                                                        Runnable onFinished) {
        org.objectweb.asm.Type annotationType = org.objectweb.asm.Type.getType(annotationClass);
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (annotationType.equals(annotation.annotationType()) && annotation.targetType() == ElementType.FIELD) {
                    if (annotationPredicate == null || annotationPredicate.test(annotation.annotationData())) {
                        var clazz = annotation.clazz();
                        var fieldName = annotation.memberName();
                        try {
                            var field = Class.forName(annotation.clazz().getClassName()).getDeclaredField(fieldName);
                            if (Modifier.isStatic(field.getModifiers())) {
                                consumer.accept(field, field.get(null));
                            } else {
                                NekoJS.LOGGER.error("Field is not static for notation: {} in {}", fieldName, clazz);
                            }
                        } catch (Throwable throwable) {
                            NekoJS.LOGGER.error("Failed to load static field for notation: {} in {}", fieldName, clazz, throwable);
                        }
                    }
                }
            }
        }
        onFinished.run();
    }

    public static <A extends Annotation> void findAnnotationStaticMethod(Class<A> annotationClass,
                                                                         @Nullable Predicate<Map<String, Object>> annotationPredicate,
                                                                         Consumer<Method> consumer,
                                                                         Runnable onFinished) {
        org.objectweb.asm.Type annotationType = org.objectweb.asm.Type.getType(annotationClass);
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (annotationType.equals(annotation.annotationType()) && annotation.targetType() == ElementType.METHOD) {
                    if (annotationPredicate == null || annotationPredicate.test(annotation.annotationData())) {
                        var clazz = annotation.clazz();
                        var methodFullDesc = annotation.memberName();
                        var methodName = methodFullDesc.substring(0, methodFullDesc.indexOf('('));
                        var methodDesc = methodFullDesc.substring(methodFullDesc.indexOf('('));
                        try {
                            for (var method : Class.forName(annotation.clazz().getClassName()).getDeclaredMethods()) {
                                if (method.getName().equals(methodName) &&
                                        methodDesc.equals(org.objectweb.asm.Type.getMethodDescriptor(method))) {
                                    if (Modifier.isStatic(method.getModifiers())) {
                                        consumer.accept(method);
                                    } else {
                                        NekoJS.LOGGER.error("Method is not static for notation: {} in {}", methodDesc, clazz);
                                    }
                                }
                            }
                        } catch (Throwable throwable) {
                            NekoJS.LOGGER.error("Failed to load static method for notation: {} in {}", methodDesc, clazz, throwable);
                        }
                    }
                }
            }
        }
        onFinished.run();
    }
}
