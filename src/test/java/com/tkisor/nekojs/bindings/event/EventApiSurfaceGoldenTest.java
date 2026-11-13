// TODO(fabric): the fabric side has no counterpart yet; drop this whole-file guard once the port lands
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
        // Installs the cancellability predicate before any event family class-initializes. This
        // mirrors the NekoJSMod constructor, which calls NeoForgeRuntimeBootstrap.setup() first.
        // Without it, the shared test JVM would freeze every cancellable family bus as
        // non-cancellable.
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
