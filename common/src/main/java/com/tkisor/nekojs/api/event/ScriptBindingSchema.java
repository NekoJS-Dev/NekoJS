package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态 accessor：暴露每个 {@link ScriptType} 的全局绑定名→Java 类型映射，供加载时成员校验器
 * （{@code GlobalBindingMemberValidator}）查询脚本对全局绑定（{@code Utils}、{@code Platform}、
 * {@code Items} 等）的成员访问是否合法。
 *
 * <p>仿 {@link ScriptErrorReporter} 的静态 accessor 模式：在 {@code ScriptEnvironmentFactory}
 * 创建 Context 时从 {@code pluginRuntime.bindings}（{@code Binding.valueType()}）填充；
 * core 编译管线通过本类读取映射，避免 core→运行时依赖。
 *
 * <p>映射在 Context 生命周期内稳定，按 {@link ScriptType} 分桶；脚本文件路径通过所属脚本目录
 * （{@link ScriptType#path}）推断 {@link ScriptType}。
 */
public final class ScriptBindingSchema {
    private static final Map<ScriptType, Map<String, Class<?>>> SCHEMAS = new ConcurrentHashMap<>();

    private ScriptBindingSchema() {}

    /** 注册某 {@link ScriptType} 的全局绑定名→Java 类型映射（拷贝快照）。 */
    public static void register(ScriptType type, Map<String, Class<?>> nameToType) {
        if (type == null) {
            return;
        }
        SCHEMAS.put(type, nameToType == null ? Map.of() : Map.copyOf(nameToType));
    }

    public static void clear(ScriptType type) {
        if (type != null) {
            SCHEMAS.remove(type);
        }
    }

    public static void clearAll() {
        SCHEMAS.clear();
    }

    public static Map<String, Class<?>> lookup(ScriptType type) {
        return type == null ? Map.of() : SCHEMAS.getOrDefault(type, Map.of());
    }

    /** 从脚本文件路径推断所属 {@link ScriptType}（依据 {@link ScriptType#path} 脚本目录）。 */
    public static ScriptType inferType(Path path) {
        if (path == null) {
            return null;
        }
        Path norm = path.toAbsolutePath().normalize();
        for (ScriptType type : ScriptType.values()) {
            if (type.path == null) {
                continue;
            }
            Path dir = type.path.toAbsolutePath().normalize();
            if (norm.startsWith(dir)) {
                return type;
            }
        }
        return null;
    }

    /** 路径对应的绑定映射；路径不属于任何脚本目录时返回空映射。 */
    public static Map<String, Class<?>> schemaForPath(Path path) {
        return lookup(inferType(path));
    }
}
