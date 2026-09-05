package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;

import net.minecraft.core.Registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动注册表适配器扫描器：扫 holder 类里的 {@code public static final Registry<T>} 字段，
 * 对"没有专属适配器的注册表类型"动态实例化 {@link SimpleRegistryBasedAdapter} 并注册——
 * 脚本侧任何注册表类型参数都能直接传字符串 id，零 per-type 代码
 * （KubeJS {@code RegistryType.Scanner} 的对标；NekoJS 没有 Mixin 那条路，走静态字段反射）。
 *
 * <p>去重：目标类型已有适配器（手写或同轮扫描已注册）就跳过，手写适配器行为零变化；
 * 动态适配器走 {@code AbstractJSTypeAdapter} 默认 LOWEST 优先级，永远让位于手写。
 *
 * <p>失败语义：字段不可读、注册表对象为 null、泛型实参不可解析（裸泛型/嵌套通配符）、
 * 目标类型本身是注册表（根注册表 {@code Registry<Registry<?>>}）——全部静默跳过并记
 * debug 日志，绝不阻断启动。mod/插件的额外 holder 类经
 * {@code installInto(registry, List.of(MyRegistries.class))} 贡献，不需要新插件钩子。
 */
public final class RegistryAutoAdapterScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryAutoAdapterScanner.class);

    private RegistryAutoAdapterScanner() {}

    public static void installInto(JSTypeAdapterRegistry registry, List<Class<?>> holderClasses) {
        Set<Class<?>> covered = new HashSet<>();
        for (JSTypeAdapter<?> adapter : registry.view()) {
            covered.add(adapter.getTargetClass());
        }
        for (Class<?> holder : holderClasses) {
            for (Field field : holder.getDeclaredFields()) {
                installField(registry, covered, field);
            }
        }
    }

    private static void installField(JSTypeAdapterRegistry registry, Set<Class<?>> covered, Field field) {
        int mods = field.getModifiers();
        if (!Modifier.isStatic(mods) || !Modifier.isPublic(mods)) return;
        if (!Registry.class.isAssignableFrom(field.getType())) return;

        Class<?> valueType = registryValueType(field.getGenericType());
        if (valueType == null) return; // 裸泛型 / 类型实参不是 Class
        if (Registry.class.isAssignableFrom(valueType)) return; // 根注册表等
        if (!covered.add(valueType)) return; // 已有专属适配器（手写 adapter 优先）

        Registry<?> registryObject;
        try {
            registryObject = (Registry<?>) field.get(null);
        } catch (Throwable inaccessible) {
            LOGGER.debug("registry auto-adapter: skip field {}", field, inaccessible);
            return;
        }
        if (registryObject == null) return;

        register(registry, registryObject, valueType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void register(JSTypeAdapterRegistry registry, Registry<?> registryObject, Class<?> valueType) {
        try {
            registry.register(new SimpleRegistryBasedAdapter<>(
                    (Registry) registryObject, (Class) valueType, valueType.getSimpleName()));
            LOGGER.debug("registry auto-adapter: registered {} for {}", valueType.getName(),
                    valueType.getSimpleName());
        } catch (Throwable constructionFailure) {
            LOGGER.debug("registry auto-adapter: failed to construct adapter for {}", valueType, constructionFailure);
        }
    }

    /** 取 {@code Registry<T>} 的第一个类型实参；解析不了返回 null。 */
    private static Class<?> registryValueType(Type genericType) {
        if (genericType instanceof ParameterizedType parameterized) {
            Type argument = parameterized.getActualTypeArguments()[0];
            if (argument instanceof Class<?> valueClass) return valueClass;
        }
        return null;
    }
}
