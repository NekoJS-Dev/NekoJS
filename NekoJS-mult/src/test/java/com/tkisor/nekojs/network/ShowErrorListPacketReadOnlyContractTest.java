//? if neoforge {
package com.tkisor.nekojs.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 错误面板只读契约：fullDetails 快照与被动刷新标志必须原样穿过 wire。 */
class ShowErrorListPacketReadOnlyContractTest {

    @Test
    void longIdentityPathAndSummaryRoundTripWithoutImplicitUtfLimit() {
        String longSuffix = "x".repeat(600);
        ErrorSummaryDTO error = new ErrorSummaryDTO(
                "nekojs/rt/server_scripts/" + longSuffix,
                "server_scripts/" + longSuffix,
                1,
                1,
                "TypeError: " + longSuffix,
                "frozen details"
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ShowErrorListPacket(java.util.List.of(error), false).write(buf);
            ShowErrorListPacket decoded = new ShowErrorListPacket(buf);

            assertEquals(error, decoded.errors().get(0), "long display fields must not fall into FriendlyByteBuf UTF default limits");
        } finally {
            buf.release();
        }
    }
    @Test
    void fullDetailsSnapshotAndPassiveRefreshFlagRoundTrip() {
        String details = "脚本: server_scripts/demo.js\n    1 | throw new Error(\"demo\")\n位置: 1";
        ErrorSummaryDTO error = new ErrorSummaryDTO(
                "nekojs/rt/server_scripts/demo.js",
                "server_scripts/demo.js",
                1,
                3,
                "demo error",
                details
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ShowErrorListPacket(java.util.List.of(error), false).write(buf);
            ShowErrorListPacket decoded = new ShowErrorListPacket(buf);

            assertEquals(false, decoded.openIfMissing(), "passive dashboard updates must not force a closed screen open");
            assertEquals(details, decoded.errors().get(0).fullDetails(), "read-only details must stay a frozen snapshot");
            assertEquals(3, decoded.errors().get(0).count());
        } finally {
            buf.release();
        }
    }
}
//?}



