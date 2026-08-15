package com.tkisor.nekojs.core.node;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoNodeOSTest {

    @Test
    void ipVersionDetectsIpv6ByColon() {
        assertEquals("IPv6", NekoNodeOS.ipVersion("::1"));
        assertEquals("IPv6", NekoNodeOS.ipVersion("fe80::1%lo0"));
        assertEquals("IPv4", NekoNodeOS.ipVersion("127.0.0.1"));
        assertEquals("IPv4", NekoNodeOS.ipVersion("192.168.1.1"));
    }

    @Test
    void networkInterfacesTypeMatchesFamilyForEveryEntry() {
        NekoNodeOS os = new NekoNodeOS();
        Map<String, List<NekoNodeOS.NetworkAddress>> interfaces = os.networkInterfaces();

        assertFalse(interfaces.isEmpty(), "test environment should expose at least one network interface");
        for (Map.Entry<String, List<NekoNodeOS.NetworkAddress>> entry : interfaces.entrySet()) {
            for (NekoNodeOS.NetworkAddress address : entry.getValue()) {
                assertEquals(address.family(), address.type(),
                        "type must match family for " + address.address() + " on " + entry.getKey());
            }
        }
    }
}
