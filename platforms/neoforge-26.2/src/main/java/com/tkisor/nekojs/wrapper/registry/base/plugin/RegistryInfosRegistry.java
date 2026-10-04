package com.tkisor.nekojs.wrapper.registry.base.plugin;

import com.tkisor.nekojs.wrapper.registry.base.RegistryInfo;
import com.tkisor.nekojs.wrapper.registry.base.RegistryInfos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;

/**
 * @author ZZZank
 */
public interface RegistryInfosRegistry {

    /**
     * 添加需要扫描的类。这些类的 {@code public static final ResourceKey<Registry<XXXX>>} 字段将被扫描以发现 Registry。
     *
     * @param classes 需要扫描的类
     */
    void addClassesToScan(Collection<Class<?>> classes);

    void addClassesToScan(Class<?>... classes);

    /**
     * 添加额外的 RegistryInfo
     *
     * @param infos 额外的 RegistryInfo
     */
    void addAdditionalInfos(Collection<RegistryInfo<?>> infos);

    void addAdditionalInfos(RegistryInfo<?>... infos);

    <T> void addAdditionalInfo(Class<T> clazz, ResourceKey<Registry<T>> key);

    /**
     * RegistryInfosRegister 的实现，收集 plugin 提供的 classesToScan 和 additionalInfos，
     * 并在构建时创建 RegistryInfos 实例。
     */
    @ApiStatus.Internal
    final class Impl implements RegistryInfosRegistry {
        private final Set<Class<?>> classesToScan = new LinkedHashSet<>();
        private final List<RegistryInfo<?>> additionalInfos = new ArrayList<>();

        @Override
        public void addClassesToScan(Collection<Class<?>> classes) {
            classes.forEach(Objects::requireNonNull);
            classesToScan.addAll(classes);
        }

        @Override
        public void addClassesToScan(Class<?>... classes) {
            addClassesToScan(Arrays.asList(classes));
        }

        @Override
        public void addAdditionalInfos(Collection<RegistryInfo<?>> infos) {
            infos.forEach(Objects::requireNonNull);
            additionalInfos.addAll(infos);
        }

        @Override
        public void addAdditionalInfos(RegistryInfo<?>... infos) {
            addAdditionalInfos(Arrays.asList(infos));
        }

        @Override
        public <T> void addAdditionalInfo(Class<T> clazz, ResourceKey<Registry<T>> key) {
            addAdditionalInfos(new RegistryInfo<>(clazz, clazz, key));
        }

        /**
         * 构建 RegistryInfos 实例。
         *
         * @return 新的 RegistryInfos 实例
         */
        public RegistryInfos build() {
            return new RegistryInfos(classesToScan, additionalInfos);
        }
    }
}
