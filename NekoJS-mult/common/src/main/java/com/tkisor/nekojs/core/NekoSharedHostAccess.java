package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Objects;

public final class NekoSharedHostAccess {
    private final HostAccess hostAccess;

    public NekoSharedHostAccess(List<JSTypeAdapter<?>> adapters) {
        this.hostAccess = create(List.copyOf(Objects.requireNonNull(adapters, "adapters")));
    }

    public HostAccess get() {
        return hostAccess;
    }

    private static HostAccess create(List<JSTypeAdapter<?>> adapters) {
        // 设计决策（最终）：保持 HostAccess.ALL，永不迁移 HostAccess.EXPLICIT + @Export。
        // 理由：NekoJS 的脚本绑定面（Utils/Platform/Items/事件回调等）面向整合包作者开放，
        // EXPLICIT 模式需要给数百个绑定类逐一标注注解，会大面积破坏现有脚本兼容性，
        // 且安全收益有限——真正的危险面（文件系统/进程/网络/线程/反射/ASM）已由
        // ClassFilter 黑名单 + VFS 沙盒 + allowCreateProcess(false) 等层拦截。
        // 脚本仍应视为可信代码（README 已声明）。
        HostAccess.Builder hostBuilder = HostAccess.newBuilder(HostAccess.ALL)
                .allowPublicAccess(true)

                .allowArrayAccess(true)
                .allowListAccess(true)
                .allowMapAccess(true)

                .allowIterableAccess(true)
                .allowIteratorAccess(true)
                .allowBufferAccess(true)

                .allowAllClassImplementations(true)
                .allowAllImplementations(true);

        adapters.forEach(adapter -> registerTypeAdapter(hostBuilder, adapter));
        hostBuilder.targetTypeMapping(Number.class, Float.class, n -> true, Number::floatValue);
        hostBuilder.targetTypeMapping(Number.class, Integer.class, n -> true, Number::intValue);
        return hostBuilder.build();
    }

    private static <T> void registerTypeAdapter(HostAccess.Builder builder, JSTypeAdapter<T> adapter) {
        builder.targetTypeMapping(Value.class, adapter.getTargetClass(), adapter, adapter,
                toHostAccessPrecedence(adapter.getPrecedence()));
    }

    private static HostAccess.TargetMappingPrecedence toHostAccessPrecedence(ConversionPrecedence p) {
        return switch (p) {
            case LOWEST -> HostAccess.TargetMappingPrecedence.LOWEST;
            case LOW -> HostAccess.TargetMappingPrecedence.LOW;
            case HIGH -> HostAccess.TargetMappingPrecedence.HIGH;
            case HIGHEST -> HostAccess.TargetMappingPrecedence.HIGHEST;
        };
    }
}
