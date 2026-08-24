package com.tkisor.nekojs.network;

import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * S3 regression test: the script batch packets must enforce entry-count,
 * per-file and total-size limits on both encode and decode. The test file
 * lives in the shared 26.x test tree and is also compiled by the 1.21.1
 * platform, so it only uses the version-independent {@link FriendlyByteBuf}
 * constructor and the shared packet record API.
 */
class ScriptBatchPacketLimitTest {

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void uploadPacketSmallMapRoundTrips() {
        Map<String, String> files = new HashMap<>();
        files.put("scripts/startup.js", "console.log('hi')");
        files.put("assets/data.json", "{\"a\":1}");

        FriendlyByteBuf buf = buffer();
        try {
            new UploadAllScriptsPacket(files).write(buf);
            UploadAllScriptsPacket decoded = new UploadAllScriptsPacket(buf);

            assertEquals(files, decoded.files());
        } finally {
            ReferenceCountUtil.safeRelease(buf);
        }
    }

    @Test
    void downloadPacketSmallMapRoundTrips() {
        Map<String, String> files = new HashMap<>();
        files.put("scripts/startup.js", "console.log('hi')");
        files.put("assets/data.json", "{\"a\":1}");

        FriendlyByteBuf buf = buffer();
        try {
            new DownloadAllScriptsPacket(files).write(buf);
            DownloadAllScriptsPacket decoded = new DownloadAllScriptsPacket(buf);

            assertEquals(files, decoded.files());
        } finally {
            ReferenceCountUtil.safeRelease(buf);
        }
    }

    @Test
    void uploadPacketDecodeRejectsEntryCountAboveMax() {
        FriendlyByteBuf buf = buffer();
        try {
            buf.writeVarInt(ScriptSyncService.MAX_SYNC_FILES + 1);

            assertThrows(IllegalStateException.class, () -> new UploadAllScriptsPacket(buf));
        } finally {
            ReferenceCountUtil.safeRelease(buf);
        }
    }

    @Test
    void downloadPacketDecodeRejectsEntryCountAboveMax() {
        FriendlyByteBuf buf = buffer();
        try {
            buf.writeVarInt(ScriptSyncService.MAX_SYNC_FILES + 1);

            assertThrows(IllegalStateException.class, () -> new DownloadAllScriptsPacket(buf));
        } finally {
            ReferenceCountUtil.safeRelease(buf);
        }
    }

    @Test
    void uploadPacketDecodeRejectsSingleContentAboveMaxBatchScriptSize() {
        // 2,796,203 个中文字符（每个 3 字节）：字符数未超过 MAX_BATCH_SCRIPT_SIZE，
        // 但 UTF-8 字节数 = 8,388,609，刚好超过 MAX_BATCH_SCRIPT_SIZE，用于验证显式
        // 的 UTF-8 字节数校验（而 readUtf 的字符数上限不会拒绝它）。
        String bigContent = "脚".repeat(ScriptSyncService.MAX_BATCH_SCRIPT_SIZE / 3 + 1);
        byte[] bigBytes = bigContent.getBytes(StandardCharsets.UTF_8);

        FriendlyByteBuf buf = buffer();
        try {
            buf.writeVarInt(1);
            buf.writeUtf("a.js");
            buf.writeVarInt(bigBytes.length);
            buf.writeBytes(bigBytes);

            assertThrows(IllegalStateException.class, () -> new UploadAllScriptsPacket(buf));
        } finally {
            ReferenceCountUtil.safeRelease(buf);
        }
    }

    @Test
    void uploadPacketEncodeRejectsMapWithTooManyEntries() {
        Map<String, String> files = new HashMap<>();
        for (int i = 0; i <= ScriptSyncService.MAX_SYNC_FILES; i++) {
            files.put("f" + i + ".js", "// empty");
        }

        FriendlyByteBuf buf = buffer();
        try {
            assertThrows(IllegalArgumentException.class, () -> new UploadAllScriptsPacket(files).write(buf));
        } finally {
            ReferenceCountUtil.safeRelease(buf);
        }
    }
}
