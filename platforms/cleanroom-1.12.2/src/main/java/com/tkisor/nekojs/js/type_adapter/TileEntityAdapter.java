package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 TileEntity 适配器。
 *
 * <p><b>1.12.2 关键差异</b>：TileEntity 不是 IForgeRegistry，注册表是 TileEntity 类内部一个
 * {@code RegistryNamespaced<ResourceLocation, Class<? extends TileEntity>>} 静态字段
 * （MCP 映射名为 {@code REGISTRY}，cleanroom 实测即此名）。id→class 查询必须反射访问该字段；
 * class→id 反查用公开静态 {@link TileEntity#getKey(Class)}。
 *
 * <p>本适配器目标类型是 {@code Class<? extends TileEntity>}（脚本侧用 class 表示一个 TE 类型）。
 * 支持 string id / {@link NekoId} / {@link ResourceLocation} / {@code Class} / {@link TileEntity} 实例 host。
 *
 * <p><b>反射健壮性</b>：先按已知字段名 {@code REGISTRY}（MCP）查找，失败则启发式扫描 TileEntity
 * 所有 declared field，找 RegistryNamespaced/Map 类型且符合 (RL→Class) 签名的字段。
 */
public class TileEntityAdapter extends AbstractJSTypeAdapter<Class<? extends TileEntity>> {

    /** 缓存的 id→class 注册表（RegistryNamespaced），反射拿到一次后续复用。 */
    private static volatile Object idToClassRegistry;

    @Override
    @SuppressWarnings("unchecked")
    public Class<Class<? extends TileEntity>> getTargetClass() {
        return (Class<Class<? extends TileEntity>>) (Class<?>) Class.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("TileEntity"),
                host(NekoId.class),
                host(ResourceLocation.class),
                host(Class.class),
                string()
        );
    }

    @Override
    protected Class<? extends TileEntity> fromString(String rawId) {
        String id = rawId.trim();
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation rl;
        try {
            rl = new ResourceLocation(id);
        } catch (Exception e) {
            throw new ValueConversionException(getTargetClass(), "valid TileEntity id", rawId,
                    "invalid id syntax: " + rawId);
        }
        Class<? extends TileEntity> clazz = lookupById(rl);
        if (clazz == null) {
            throw new ValueConversionException(getTargetClass(), "registered TileEntity id", rawId,
                    "TileEntity not found: " + id);
        }
        return clazz;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<? extends TileEntity> fromHostObject(Object host) {
        if (host instanceof Class<?> c) {
            if (TileEntity.class.isAssignableFrom(c)) {
                return (Class<? extends TileEntity>) c;
            }
            return null;
        }
        if (host instanceof TileEntity te) {
            return te.getClass();
        }
        if (host instanceof NekoId(String namespace, String path)) {
            return lookupById(new ResourceLocation(namespace, path));
        }
        if (host instanceof ResourceLocation rl) {
            return lookupById(rl);
        }
        return null;
    }

    // ===================== 注册表反射查找 =====================

    /**
     * 按 ResourceLocation 查 TileEntity class。反射失败或未注册返回 null。
     * 先尝试 {@code getObject(rl)}（RegistryNamespaced API），若注册表被映射成裸 Map，
     * fallback 到 {@code Map.get(rl)} / {@code Map.get(rl.toString())} 兼容 String/RL key。
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends TileEntity> lookupById(ResourceLocation rl) {
        Object registry = getIdToClassRegistry();
        if (registry == null) return null;

        // RegistryNamespaced 路径
        if (registry instanceof RegistryNamespaced) {
            try {
                Object v = ((RegistryNamespaced<ResourceLocation, ?>) registry).getObject(rl);
                Class<? extends TileEntity> te = asTileEntityClass(v);
                if (te != null) return te;
            } catch (Throwable ignored) {}
        }

        // Map 路径（key 可能是 RL 或 String）
        if (registry instanceof Map) {
            try {
                Map<Object, Object> m = (Map<Object, Object>) registry;
                Object v = m.get(rl);
                if (v == null) v = m.get(rl.toString());
                Class<? extends TileEntity> te = asTileEntityClass(v);
                if (te != null) return te;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * 列出所有已注册 TileEntity 的 id（{@code namespace:path} 形式）。供 probe 的
     * {@code @special} {@code RegistryTypes.TileEntity} 字面量联合生成使用。反射失败返回空列表。
     *
     * <p>复用 {@link #getIdToClassRegistry()} 拿到的注册表句柄：{@link RegistryNamespaced} 走
     * {@code getKeys()}，裸 {@link Map}（如 {@code nameToClassMap} 兜底）走 {@code keySet()}。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<String> allRegisteredIds() {
        Object registry = getIdToClassRegistry();
        if (registry == null) return List.of();
        List<String> ids = new ArrayList<>();
        if (registry instanceof RegistryNamespaced reg) {
            try {
                Set keys = reg.getKeys();
                for (Object key : keys) {
                    if (key != null) ids.add(key.toString());
                }
            } catch (Throwable ignored) {
                // 映射名/结构异常 → fallback 到 Map 路径
            }
        }
        if (ids.isEmpty() && registry instanceof Map m) {
            for (Object key : m.keySet()) {
                if (key != null) ids.add(key.toString());
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    /**
     * 安全把任意对象转成 {@code Class<? extends TileEntity>}。instanceof 不支持泛型通配符，
     * 改为先 {@code Class<?>} 再 {@code isAssignableFrom} 校验。
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends TileEntity> asTileEntityClass(Object v) {
        if (v instanceof Class<?> c && TileEntity.class.isAssignableFrom(c)) {
            return (Class<? extends TileEntity>) c;
        }
        return null;
    }

    /**
     * 懒加载 + 缓存 id→class 注册表反射句柄。双重检查锁定保证只反射一次。
     * 字段名优先 {@code REGISTRY}（MCP），失败时遍历 declared fields 启发式匹配
     * （RegistryNamespaced 或 Map 类型，且能找到至少一个 Class 值的）。
     */
    private static Object getIdToClassRegistry() {
        Object cached = idToClassRegistry;
        if (cached != null) return cached;

        synchronized (TileEntityAdapter.class) {
            if (idToClassRegistry != null) return idToClassRegistry;

            Object found = findByName("REGISTRY");
            if (found == null) {
                // MCP 历史/其它映射候选名（1.12.2 vanilla 是 nameToClassMap/classToNameMap 双 Map，
                // Forge/CPW 改造后是 RegistryNamespaced REGISTRY —— 这里全部尝试一遍）
                found = findByName("nameToClassMap");
            }
            if (found == null) {
                found = heuristicScan();
            }
            idToClassRegistry = found;
            return found;
        }
    }

    private static Object findByName(String name) {
        try {
            Field f = TileEntity.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }

    /**
     * 启发式：扫描 TileEntity 所有 static field，找 RegistryNamespaced 或 Map 类型且
     * 至少存在一个 Class value 的字段。用于未知映射名兜底。
     */
    private static Object heuristicScan() {
        for (Field f : TileEntity.class.getDeclaredFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof RegistryNamespaced || val instanceof Map) {
                        if (looksLikeIdToClassMap(val)) return val;
                    }
                }
            } catch (Throwable ignored) {
                // 试下一个
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean looksLikeIdToClassMap(Object candidate) {
        if (candidate instanceof Map<?, ?> m) {
            for (Object v : m.values()) {
                if (v instanceof Class<?> c && TileEntity.class.isAssignableFrom(c)) return true;
            }
            return false;
        }
        if (candidate instanceof RegistryNamespaced<?, ?> reg) {
            for (Object v : reg) {
                if (v instanceof Class<?> c && TileEntity.class.isAssignableFrom(c)) return true;
            }
            return false;
        }
        return false;
    }
}
