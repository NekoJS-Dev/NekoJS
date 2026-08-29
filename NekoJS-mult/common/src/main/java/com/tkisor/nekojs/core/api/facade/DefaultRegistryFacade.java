package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.facade.RegistryFacade;
import com.tkisor.nekojs.api.facade.RegistryView;
import com.tkisor.nekojs.api.registry.RegistryQueryService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DefaultRegistryFacade implements RegistryFacade {
    /**
     * 视图缓存封顶。registryId 由脚本任意提供（含用户自定义注册表），空间不可预知；
     * {@link DefaultRegistryView} 无状态，简单按访问顺序做 LRU 封顶即可同时兼顾
     * 热路径（脚本按 tick 调 {@code Registry.get(...)} 不再重复分配视图）与内存上限。
     */
    private static final int MAX_CACHED_VIEWS = 256;

    private final RegistryQueryService service;
    // DefaultRegistryView 只有 service + registryId 两个 final 字段、所有方法委托 service，
    // 同一 (service, registryId) 的视图行为完全一致，可安全复用。缓存按实例持有（service 绑死）。
    private final Map<String, RegistryView> viewCache =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, RegistryView> eldest) {
                    return size() > MAX_CACHED_VIEWS;
                }
            });

    public DefaultRegistryFacade(RegistryQueryService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public RegistryView get(String registryId) {
        String id = requireRegistryId(registryId);
        return viewCache.computeIfAbsent(id, key -> new DefaultRegistryView(service, key));
    }

    private static String requireRegistryId(String registryId) {
        Objects.requireNonNull(registryId, "registryId");
        if (registryId.isBlank()) {
            throw new ApiInvocationException(ApiErrorCodes.TYPE_MISMATCH, "Registry id cannot be blank");
        }
        return registryId;
    }
}
