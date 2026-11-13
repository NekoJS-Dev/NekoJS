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
        // Design decision: keep HostAccess.ALL and never migrate to HostAccess.EXPLICIT with @Export.
        // The NekoJS script bindings (Utils/Platform/Items, event callbacks and so on) are open to
        // modpack authors, and EXPLICIT mode would need an annotation on each of the hundreds of
        // binding classes. That would break existing scripts on a large scale for little security
        // gain: the dangerous operations (file system, process, network, threading, reflection, ASM)
        // are already blocked by the ClassFilter blacklist, the VFS sandbox and
        // allowCreateProcess(false). Scripts are still treated as trusted code, as the README says.
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
        Class<T> target = adapter.getTargetClass();
        // Graal turns an adapter ClassCastException into the same signal as a target mismatch. Catch
        // execution failures before that so overload dispatch cannot suppress them.
        builder.targetTypeMapping(Value.class, target, value -> {
            try {
                return adapter.test(value);
            } catch (ClassCastException failure) {
                throw new AdapterExecutionException(target, "acceptance", failure);
            }
        }, value -> {
            try {
                return adapter.apply(value);
            } catch (ClassCastException failure) {
                throw new AdapterExecutionException(target, "conversion", failure);
            }
        }, toHostAccessPrecedence(adapter.getPrecedence()));
    }

    private static final class AdapterExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private AdapterExecutionException(Class<?> target, String phase, ClassCastException cause) {
            super("[NEKO-5202] Type adapter failed during " + phase + ": target=" + target.getName(), cause);
        }
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
