package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoNodeFSTest {
    private final NekoNodeFS fs = new NekoNodeFS(SandboxConfig.defaultConfig());
    private Path oversizedFile;

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    @AfterEach
    void cleanup() throws IOException {
        if (oversizedFile != null) {
            Files.deleteIfExists(oversizedFile);
        }
    }

    @Test
    void readFileBufferRejectsFilesLargerThanAllocationCap() throws IOException {
        Path root = NekoJSPaths.get().root();
        Files.createDirectories(root);
        oversizedFile = root.resolve("neko-node-fs-oversized-" + System.nanoTime() + ".bin");
        // RandomAccessFile.setLength extends the file without eagerly writing 256 MiB of zeros on NTFS.
        try (RandomAccessFile raf = new RandomAccessFile(oversizedFile.toFile(), "rw")) {
            raf.setLength(NekoNodeBuffer.MAX_ALLOC_BYTES + 1L);
        }

        IOException error = assertThrows(IOException.class,
                () -> fs.readFileBuffer(oversizedFile.toString()));
        assertTrue(error.getMessage().contains("too large"),
                "message should explain the allocation cap, got: " + error.getMessage());
    }

    @Test
    void readFileStringRejectsFilesLargerThanAllocationCap() throws IOException {
        Path root = NekoJSPaths.get().root();
        Files.createDirectories(root);
        Path file = root.resolve("neko-node-fs-oversized-string-" + System.nanoTime() + ".bin");
        try {
            // RandomAccessFile.setLength extends the file without eagerly writing 256 MiB of zeros on NTFS.
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                raf.setLength(NekoNodeBuffer.MAX_ALLOC_BYTES + 1L);
            }

            IOException error = assertThrows(IOException.class,
                    () -> fs.readFileString(file.toString(), "utf8"));
            assertTrue(error.getMessage().contains("too large"),
                    "message should explain the allocation cap, got: " + error.getMessage());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void readFileBufferAndStringReadSmallFiles() throws IOException {
        Path root = NekoJSPaths.get().root();
        Files.createDirectories(root);
        Path small = root.resolve("neko-node-fs-small-" + System.nanoTime() + ".bin");
        try {
            Files.writeString(small, "hello");
            assertEquals("hello", fs.readFileString(small.toString(), "utf8"));
            assertEquals('h', fs.readFileBuffer(small.toString()).get(0));
        } finally {
            Files.deleteIfExists(small);
        }
    }
}
