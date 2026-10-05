//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModificationSyncWireTest {
    @Test
    void committedDeclarationsRoundTripThroughTheNetworkWire() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("maxStackSize", 16);
        properties.put("rarity", "epic");
        properties.put("nested", Map.of("enabled", true));
        List<ModificationDeclaration> expected = List.of(
                new ModificationDeclaration("item", "minecraft:diamond", properties, "server_scripts/main.js"),
                new ModificationDeclaration("block", "minecraft:redstone_lamp", Map.of("lightEmission", 7), null));

        List<ModificationDeclaration> actual = ModificationSyncWire.decode(
                ModificationSyncWire.encode(4L, expected).getStringOr("declarations", ""));

        assertEquals(expected.size(), actual.size());
        assertEquals(expected.get(0).kind(), actual.get(0).kind());
        assertEquals(expected.get(0).targetId(), actual.get(0).targetId());
        assertEquals(16, actual.get(0).properties().get("maxStackSize"));
        assertEquals("epic", actual.get(0).properties().get("rarity"));
        assertEquals(true, ((Map<?, ?>) actual.get(0).properties().get("nested")).get("enabled"));
        assertEquals(7, actual.get(1).properties().get("lightEmission"));
    }
}
//?}
