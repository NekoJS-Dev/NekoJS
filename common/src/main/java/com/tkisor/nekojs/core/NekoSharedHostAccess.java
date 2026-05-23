package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoJSTypeAdapters;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;

public final class NekoSharedHostAccess {
    private static final HostAccess SHARED_HOST_ACCESS = create();

    private NekoSharedHostAccess() {}

    public static HostAccess get() {
        return SHARED_HOST_ACCESS;
    }

    private static HostAccess create() {
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

        NekoJSTypeAdapters.all().forEach(adapter -> registerTypeAdapter(hostBuilder, adapter));
        hostBuilder.targetTypeMapping(Number.class, Float.class, n -> true, Number::floatValue);
        hostBuilder.targetTypeMapping(Number.class, Integer.class, n -> true, Number::intValue);
        return hostBuilder.build();
    }

    private static <T> void registerTypeAdapter(HostAccess.Builder builder, JSTypeAdapter<T> adapter) {
        builder.targetTypeMapping(
                        Value.class,
                        adapter.getTargetClass(),
                        adapter::canConvert,
                        adapter::convert,
                        adapter.getPrecedence()
                );
    }
}
