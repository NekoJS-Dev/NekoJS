package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * 共享类收集（包内组件，从 {@link ProbeCoordinator} 抽出）：从事件/绑定种子出发做轻量 BFS
 * （仅收集类，不生成声明），按 {@link ProbeConfig} 包过滤。适配器目标类、适配器别名引用的
 * host 类型等 backend 特定的后续增补留给各 backend 自行处理。无状态，全静态。
 */
final class ProbeClassCollector {

    private ProbeClassCollector() {
    }

    /**
     * 收集种子：事件类型、事件 dispatch key 类型、绑定 Java 类型、代理绑定的 extraDocTypes
     * （depth 0）；随后 BFS 可达闭包（superclass / interfaces / 公共构造器参数 / 公共方法返回值与参数 /
     * 公共字段 / ParameterizedType raw+实参 / GenericArrayType 组件），深度上限
     * {@code scan.maxDepth}（≤0 兜底 5）。
     *
     * <p>确定性：BFS 访问顺序依赖 getDeclaredMethods/getInterfaces 等反射顺序（JVM 规范不保证，
     * 跨 JDK 版本/平台可能不同）。返回前按全限定名字典序排序，保证 probe 产物可复现。
     */
    static LinkedHashSet<Class<?>> collect(NekoScriptCatalogSnapshot snapshot, ProbeConfig cfg) {
        List<String> platformPkgs = ProbeConfigLoader.platformDefaultPackages();
        Set<String> forcedPkgs = cfg.forcedPackages();
        LinkedHashSet<Class<?>> visited = new LinkedHashSet<>();
        Queue<Object[]> queue = new LinkedList<>();

        // 种子类：事件类型和绑定类型（depth 0）
        for (EventCatalogEntry event : snapshot.events()) {
            if (event.eventType() != null) queue.add(new Object[]{event.eventType(), 0});
            if (event.dispatchKeyType() != null) queue.add(new Object[]{event.dispatchKeyType(), 0});
        }
        for (BindingCatalogEntry binding : snapshot.bindings()) {
            if (binding.javaType() != null) queue.add(new Object[]{binding.javaType(), 0});
            // 代理绑定（如 Item）的 extraDocTypes（委托目标 MC 类）也作为种子
            for (Class<?> extra : binding.extraDocTypes()) {
                queue.add(new Object[]{extra, 0});
            }
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

            if (cls.getSuperclass() != null) queue.add(new Object[]{cls.getSuperclass(), nextDepth});
            for (Class<?> iface : cls.getInterfaces()) queue.add(new Object[]{iface, nextDepth});

            for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
                if (Modifier.isPublic(ctor.getModifiers())) {
                    for (Type p : ctor.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                }
            }
            for (Method method : cls.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    collectTypeToQueue(method.getGenericReturnType(), queue, nextDepth);
                    for (Type p : method.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                }
            }
            for (Field field : cls.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers())) collectTypeToQueue(field.getGenericType(), queue, nextDepth);
            }
        }

        List<Class<?>> sorted = new ArrayList<>(visited);
        sorted.sort(Comparator.comparing(Class::getName));
        return new LinkedHashSet<>(sorted);
    }

    /**
     * collect 的过滤判定（exclude 始终生效；FULL 直通 include；SMART 走白名单 + forceScanMods 补充）：
     * <ul>
     *   <li>命中 {@code excludePackages} → 跳过（FULL 模式同样生效）</li>
     *   <li>mode == FULL → 收（跳过 include 白名单；闭包体积由 maxDepth 护栏）</li>
     *   <li>其余取值（SMART 等）→ {@link ProbeConfig#isRelevantClass}；另命中 forceScanMods 前缀也收</li>
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

    private static void collectTypeToQueue(Type type, Queue<Object[]> queue, int depth) {
        if (type instanceof Class<?> cls) {
            queue.add(new Object[]{cls, depth});
        } else if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> rawCls) queue.add(new Object[]{rawCls, depth});
            for (Type arg : pt.getActualTypeArguments()) collectTypeToQueue(arg, queue, depth);
        } else if (type instanceof GenericArrayType gat) {
            collectTypeToQueue(gat.getGenericComponentType(), queue, depth);
        }
        // 刻意不跟随 TypeVariable 上界与 WildcardType 上/下界：跟随它们（尤其在 java.* 内）会触发
        // 级联爆炸——例如 File 的签名拉入 URI/URL/Path/Charset/Locale…，5 层 BFS 穿过 java.io/java.util
        // 产出海量类。原行为（不跟随）是有意的范围控制，保持 probe 输出有界。
    }
}
