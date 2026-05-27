package com.tkisor.nekojs.wrapper;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Composite: handler's typed methods take priority; unknown types fall through to schema proxy.
 * <p>
 * Supports {@code Optional<T>} parameters: missing JS args are filled with {@link Optional#empty()}.
 * A handler method matches when JS args count is between the number of non-Optional params (required)
 * and total params (all). Only trailing params may be Optional.
 */
final class HandlerWithSchemaProxy implements ProxyObject {
    private final Object handler;
    private final Map<String, List<Method>> handlerMethods;
    private final ProxyObject schemaFallback;

    HandlerWithSchemaProxy(Object handler, ProxyObject schemaFallback) {
        this.handler = handler;
        this.schemaFallback = schemaFallback;
        this.handlerMethods = reflectMethods(handler);
    }

    private static Map<String, List<Method>> reflectMethods(Object handler) {
        Map<String, List<Method>> methods = new LinkedHashMap<>();
        for (Method method : handler.getClass().getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            methods.computeIfAbsent(method.getName(), k -> new ArrayList<>()).add(method);
        }
        return methods;
    }

    private static int requiredParamCount(Method method) {
        int count = 0;
        for (var param : method.getParameters()) {
            if (param.getType() == Optional.class) break;
            count++;
        }
        return count;
    }

    @Override
    public Object getMember(String key) {
        List<Method> methods = handlerMethods.get(key);
        if (methods != null && !methods.isEmpty()) {
            return (ProxyExecutable) args -> {
                // 按 (必填参数数 ≤ args数 ≤ 总参数数) 匹配 Optional<T> 方法
                for (Method method : methods) {
                    int total = method.getParameterCount();
                    int required = requiredParamCount(method);
                    if (args.length >= required && args.length <= total) {
                        try {
                            return method.invoke(handler, convertWithOptional(method, args, total));
                        } catch (Exception e) {
                            break; // conversion failed → fall through to schema
                        }
                    }
                }
                // 不匹配：fallback 到 schema
                Object fallback = schemaFallback.getMember(key);
                if (fallback instanceof ProxyExecutable exec) {
                    return exec.execute(args);
                }
                throw new IllegalArgumentException("No matching overload for " + key + " with " + args.length + " args");
            };
        }

        return schemaFallback.getMember(key);
    }

    private static Object[] convertWithOptional(Method method, Value[] args, int totalParams) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] converted = new Object[totalParams];
        for (int i = 0; i < totalParams; i++) {
            if (i < args.length) {
                converted[i] = args[i].as(paramTypes[i]);
            } else {
                // 缺失的参数必须是 Optional<T>
                converted[i] = Optional.empty();
            }
        }
        return converted;
    }

    @Override
    public Object getMemberKeys() {
        Set<String> keys = new LinkedHashSet<>(handlerMethods.keySet());
        Object schemaKeys = schemaFallback.getMemberKeys();
        if (schemaKeys instanceof String[] arr) keys.addAll(Arrays.asList(arr));
        else if (schemaKeys instanceof List<?> list) list.forEach(k -> keys.add(String.valueOf(k)));
        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return true;
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Recipe namespace is read-only");
    }
}
