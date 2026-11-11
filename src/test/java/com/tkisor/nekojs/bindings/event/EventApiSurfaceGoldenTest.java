// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tkisor.nekojs.api.event.EventGroup;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Pins the script-visible block event members independently of platform source placement.
 * Reading BlockEvents.GROUP initializes the complete native group and its bridge before freezing.
 * Node-local goldens retain the 26.x-only modification event and the legacy absence of that event.
 */
class EventApiSurfaceGoldenTest {

    private static final String GOLDEN = "/golden/block-events-api.txt";

    @Test
    void blockEventsSurfaceMatchesGolden() throws IOException {
        // Install the platform predicate before the complete event family initializes.
        // ticket 24: cancellability predicate must also precede the first family class-init
        // (mirrors NekoJSMod's constructor calling NeoForgeRuntimeBootstrap.setup() first);
        // without it this test would freeze every cancellable family bus as non-cancellable
        // for the whole shared test JVM.
        com.tkisor.nekojs.api.event.EventBusJS.setExternalCancellabilityPredicate(
                net.neoforged.bus.api.ICancellableEvent.class::isAssignableFrom);
        EventGroup group = BlockEvents.GROUP;
        group.freeze();

        String actual = group.viewBuses().keySet().stream().sorted().collect(Collectors.joining("\n"));
        assertEquals(readGolden(), actual,
                "BlockEvents 的脚本可见成员集合变了。确认是刻意变更后，更新 " + GOLDEN);
    }

    private static String readGolden() throws IOException {
        try (InputStream stream = EventApiSurfaceGoldenTest.class.getResourceAsStream(GOLDEN)) {
            if (stream == null) {
                throw new IllegalStateException("缺少 golden 资源 " + GOLDEN);
            }
            List<String> lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .sorted()
                    .toList();
            return String.join("\n", lines);
        }
    }
}
//?}
