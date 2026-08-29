package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.plugin.MergePolicy;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionPoint;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * 内置扩展点 {@code nekojs:registry_infos}（ADR-0004 贡献式发现）。
 *
 * <p>默认扫描根为原版 {@link Registries}（反射其 public static {@link ResourceKey} 字段，
 * 版本字段集差异天然自适应）；插件经 {@link Contributor} 补充扫描类或手动条目
 * （如模组自定义注册表）。merge 策略 {@link MergePolicy#append}（贡献的是
 * 扫描类/信息条目，无键冲突）。
 *
 * <p>本点与 {@link RegistryTypesPoint} 是 V2 扩展点系统上首批引擎级新消费者
 * （P2 / PR #37 主场），也是"新增内置扩展点 = 1 文件 + 清单 1 行"判据②的试做对象：
 * 它们经 {@code NekoRegistryPointsPlugin}（版本树 provider）注册。
 */
public final class RegistryInfosPoint {

    /** 扩展点 id。 */
    public static final String ID = "nekojs:registry_infos";

    private RegistryInfosPoint() {
    }

    /** 贡献面：实现本接口的插件补充注册表扫描根或手动条目。 */
    public interface Contributor extends NekoJSPlugin {

        /** 默认空实现：仅扫描内置根。 */
        default void registerRegistryInfos(RegistryInfosCollector collector) {
        }
    }

    /** 收集期累积器（append：类列表 + 手动条目列表）。 */
    public static final class RegistryInfosCollector {
        final List<Class<?>> classesToScan = new ArrayList<>();
        final List<RegistryInfo> additionalInfos = new ArrayList<>();

        /** 追加一个扫描根类（其 public static ResourceKey 字段将被反射登记）。 */
        public void addClassToScan(Class<?> root) {
            classesToScan.add(Objects.requireNonNull(root, "root"));
        }

        /** 手动登记一条注册表元信息（无法反射发现的场合）。 */
        public void addInfo(RegistryInfo info) {
            additionalInfos.add(Objects.requireNonNull(info, "info"));
        }
    }

    /** 产物：全量注册表元信息（扫描根反射 + 手动条目，键去重首胜）。 */
    record RegistryInfos(Map<ResourceKey<? extends Registry<?>>, RegistryInfo> infos) {
        RegistryInfos {
            infos = Map.copyOf(infos);
        }

        /** 按注册表键查询。 */
        public RegistryInfo get(ResourceKey<? extends Registry<?>> key) {
            return infos.get(key);
        }
    }

    static RegistryInfos scan(RegistryInfosCollector collector) {
        Map<ResourceKey<? extends Registry<?>>, RegistryInfo> map = new LinkedHashMap<>();
        List<Class<?>> roots = new ArrayList<>(collector.classesToScan);
        roots.add(Registries.class);
        for (Class<?> root : roots) {
            for (Field field : root.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !ResourceKey.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    @SuppressWarnings("unchecked")
                    ResourceKey<? extends Registry<?>> key = (ResourceKey<? extends Registry<?>>) field.get(null);
                    map.putIfAbsent(key, new RegistryInfo(key, Object.class));
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        for (RegistryInfo info : collector.additionalInfos) {
            map.putIfAbsent(info.key(), info);
        }
        return new RegistryInfos(map);
    }

    /** 扩展点定义（由 NekoRegistryPointsPlugin 注册）。 */
    public static final NekoPluginExtensionPoint<Contributor, RegistryInfosCollector, RegistryInfos> POINT =
            NekoPluginExtensionPoint.<Contributor, RegistryInfosCollector, RegistryInfos>builder(ID, Contributor.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new RegistryInfosCollector())
                    .collector(Contributor::registerRegistryInfos)
                    .finish(RegistryInfosPoint::scan)
                    .build();
}
