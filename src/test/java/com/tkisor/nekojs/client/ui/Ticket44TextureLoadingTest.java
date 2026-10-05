//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiErrorCodes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class Ticket44TextureLoadingTest {
    @Test
    void resourceStackLoadingCachesUploadsAndCommitReleasesRetiredSlots() throws Exception {
        MemoryResources resources = new MemoryResources();
        resources.add("demo:textures/gui/panel.png", pngHeader(32, 16));
        RecordingBackend backend = new RecordingBackend();
        AtomicInteger revision = new AtomicInteger();
        MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, revision::get);
        var first = resolver.texture("demo:gui/panel");
        assertEquals(32, first.width());
        assertEquals(16, first.height());
        assertEquals(first, resolver.texture("demo:gui/panel"));
        assertEquals(1, backend.uploads);
        revision.incrementAndGet();
        var second = resolver.texture("demo:gui/panel");
        assertNotEquals(first.slot(), second.slot());
        assertTrue(backend.released.isEmpty());
        assertEquals(2, backend.uploads);
        resolver.commitPrepared();
        assertEquals(List.of(first.slot()), backend.released);
        resolver.commitPrepared();
        assertEquals(List.of(first.slot()), backend.released);
        resolver.close();
        resolver.close();
        assertEquals(List.of(first.slot(), second.slot()), backend.released);
    }

    @Test
    void failingPreparationAfterARevisionChangeKeepsThePriorSlotUntilClose() throws Exception {
        MemoryResources resources = new MemoryResources();
        resources.add("demo:textures/panel.png", pngHeader(8, 8));
        RecordingBackend backend = new RecordingBackend();
        AtomicInteger revision = new AtomicInteger();
        MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, revision::get);
        var active = resolver.texture("demo:panel");
        resources.add("demo:textures/panel.png", new byte[]{1, 2, 3});
        revision.incrementAndGet();
        assertTrue(resolver.refresh());
        var failed = resolver.texture("demo:panel");
        assertEquals(UiErrorCodes.RESOURCE_DECODE_FAILED, failed.status().code());
        assertTrue(backend.released.isEmpty());
        assertEquals(1, backend.uploads);
        assertFalse(resolver.refresh());
        resolver.close();
        assertEquals(List.of(active.slot()), backend.released);
    }

    @Test
    void failedHostPreparationKeepsThePriorSlotAliveUntilASuccessfulPublication() throws Exception {
        MemoryResources resources = new MemoryResources();
        resources.add("demo:textures/panel.png", pngHeader(8, 8));
        RecordingBackend backend = new RecordingBackend();
        AtomicInteger revision = new AtomicInteger();
        try (MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, revision::get)) {
            var active = resolver.texture("demo:panel");
            revision.incrementAndGet();
            assertThrows(IllegalStateException.class, () -> {
                resolver.texture("demo:panel");
                throw new IllegalStateException("host preparation failed after texture upload");
            });
            assertEquals(2, backend.uploads);
            assertTrue(backend.released.isEmpty());
            var prepared = resolver.texture("demo:panel");
            assertNotEquals(active.slot(), prepared.slot());
            resolver.commitPrepared();
            assertEquals(List.of(active.slot()), backend.released);
        }
        assertEquals(2, backend.released.size());
    }

    @Test
    void inspectLikeLookupAndExplicitInvalidationDoNotReleaseActiveSlots() throws Exception {
        MemoryResources resources = new MemoryResources();
        resources.add("demo:textures/panel.png", pngHeader(8, 8));
        RecordingBackend backend = new RecordingBackend();
        AtomicInteger revision = new AtomicInteger();
        try (MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, revision::get)) {
            var active = resolver.texture("demo:panel");
            revision.incrementAndGet();
            assertTrue(resolver.refresh());
            assertEquals(null, resolver.resolveTexture("demo:panel").code());
            var prepared = resolver.texture("demo:panel");
            int lookups = resources.lookups;
            assertEquals(prepared, resolver.texture("demo:panel"));
            assertEquals(lookups, resources.lookups);
            assertTrue(backend.released.isEmpty());
            resolver.invalidate();
            assertTrue(backend.released.isEmpty());
            var latest = resolver.texture("demo:panel");
            resolver.commitPrepared();
            assertEquals(List.of(active.slot(), prepared.slot()), backend.released);
            assertFalse(backend.released.contains(latest.slot()));
        }
        assertEquals(3, backend.released.size());
    }

    @Test
    void closeAttemptsCurrentAndRetiredSlotsDespiteReleaseFailuresAndRejectsFurtherUse() throws Exception {
        MemoryResources resources = new MemoryResources();
        resources.add("demo:textures/panel.png", pngHeader(8, 8));
        resources.add("demo:textures/icon.png", pngHeader(4, 4));
        RecordingBackend backend = new RecordingBackend();
        AtomicInteger revision = new AtomicInteger();
        MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, revision::get);
        var retired = resolver.texture("demo:panel");
        revision.incrementAndGet();
        var current = resolver.texture("demo:panel");
        var icon = resolver.texture("demo:icon");
        RuntimeException retiredFailure = new IllegalStateException("retired release failed");
        RuntimeException currentFailure = new IllegalStateException("current release failed");
        backend.releaseFailures.put(retired.slot(), retiredFailure);
        backend.releaseFailures.put(current.slot(), currentFailure);
        RuntimeException failure = assertThrows(RuntimeException.class, resolver::close);
        assertSame(retiredFailure, failure);
        assertArrayEquals(new Throwable[]{currentFailure}, failure.getSuppressed());
        assertEquals(Set.of(retired.slot(), current.slot(), icon.slot()), Set.copyOf(backend.released));
        assertEquals(3, backend.released.size());
        assertThrows(IllegalStateException.class, () -> resolver.texture("demo:panel"));
        assertThrows(IllegalStateException.class, resolver::refresh);
        assertThrows(IllegalStateException.class, resolver::invalidate);
        assertThrows(IllegalStateException.class, resolver::commitPrepared);
        assertThrows(IllegalStateException.class, () -> resolver.failureCause("demo:panel"));
        resolver.close();
        assertEquals(3, backend.released.size());
    }

    @Test
    void commitAttemptsAllRetiredSlotsAndKeepsCurrentSlotsAfterCleanupFailure() throws Exception {
        MemoryResources resources = new MemoryResources();
        resources.add("demo:textures/panel.png", pngHeader(8, 8));
        RecordingBackend backend = new RecordingBackend();
        AtomicInteger revision = new AtomicInteger();
        try (MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, revision::get)) {
            var first = resolver.texture("demo:panel");
            revision.incrementAndGet();
            var second = resolver.texture("demo:panel");
            revision.incrementAndGet();
            var current = resolver.texture("demo:panel");
            RuntimeException firstFailure = new IllegalStateException("first release failed");
            backend.releaseFailures.put(first.slot(), firstFailure);
            assertSame(firstFailure, assertThrows(RuntimeException.class, resolver::commitPrepared));
            assertEquals(List.of(first.slot(), second.slot()), backend.released);
            assertEquals(current, resolver.texture("demo:panel"));
            resolver.commitPrepared();
            assertEquals(List.of(first.slot(), second.slot()), backend.released);
        }
        assertEquals(3, backend.released.size());
    }

    @Test
    void missingReadDecodeAndUploadFailuresCarryDistinctLocatableCodes() throws Exception {
        MemoryResources resources = new MemoryResources();
        IOException readFailure = new IOException("read fixture");
        resources.add("demo:textures/read.png", new Resource(null, () -> { throw readFailure; }));
        resources.add("demo:textures/header.png", new byte[]{1, 2, 3});
        resources.add("demo:textures/decode.png", pngHeader(8, 8));
        resources.add("demo:textures/upload.png", pngHeader(8, 8));
        RecordingBackend backend = new RecordingBackend();
        try (MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, () -> 1)) {
            assertEquals(UiErrorCodes.INVALID_RESOURCE_ID, resolver.resolveTexture("https://bad").code());
            assertEquals(UiErrorCodes.MISSING_RESOURCE, resolver.resolveTexture("demo:missing").code());
            assertEquals(UiErrorCodes.RESOURCE_LOAD_FAILED, resolver.resolveTexture("demo:read").code());
            assertSame(readFailure, resolver.texture("demo:read").cause());
            assertSame(readFailure, resolver.failureCause("demo:read"));
            assertEquals(UiErrorCodes.RESOURCE_DECODE_FAILED, resolver.resolveTexture("demo:header").code());
            assertNotNull(resolver.texture("demo:header").cause());
            backend.decodingFailure = true;
            var decode = resolver.texture("demo:decode");
            assertEquals(UiErrorCodes.RESOURCE_DECODE_FAILED, decode.status().code());
            assertSame(backend.uploadCause, decode.cause().getCause());
            assertSame(decode.cause(), resolver.failureCause("demo:decode"));
            UiDiagnostic diagnostic = decode.diagnostic(new UiDiagnostic.Location("root", "image", "hero", 4));
            assertEquals("root", diagnostic.rootId());
            assertEquals("hero", diagnostic.nodeKey());
            assertEquals("demo:decode", diagnostic.resourceId());
            assertEquals(4, diagnostic.generation());
            backend.decodingFailure = false;
            backend.uploadFailure = true;
            assertEquals(UiErrorCodes.RESOURCE_LOAD_FAILED, resolver.resolveTexture("demo:upload").code());
            assertSame(backend.uploadCause, resolver.failureCause("demo:upload").getCause());
            int lookups = resources.lookups;
            assertEquals(null, resolver.failureCause("demo:not-prepared"));
            assertEquals(null, resolver.failureCause("https://bad"));
            assertEquals(null, resolver.failureCause("demo:missing"));
            assertSame(readFailure, resolver.failureCause("demo:read"));
            assertEquals(lookups, resources.lookups);
        }
    }

    @Test
    void lookupFailureCausesAreCachedWithoutRefreshingDuringDiagnosticLookup() {
        MemoryResources resources = new MemoryResources();
        RuntimeException lookupFailure = new IllegalStateException("lookup fixture");
        resources.lookupFailure = lookupFailure;
        RecordingBackend backend = new RecordingBackend();
        AtomicInteger revision = new AtomicInteger();
        try (MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, revision::get)) {
            assertEquals(UiErrorCodes.RESOURCE_LOAD_FAILED, resolver.resolveTexture("demo:panel").code());
            assertEquals(UiErrorCodes.RESOURCE_LOAD_FAILED, resolver.resolveFont("demo:font").code());
            int lookups = resources.lookups;
            revision.incrementAndGet();
            assertSame(lookupFailure, resolver.failureCause("demo:panel"));
            assertSame(lookupFailure, resolver.failureCause("demo:font"));
            assertEquals(lookups, resources.lookups);
            assertTrue(backend.released.isEmpty());
        }
    }

    @Test
    void oversizedDimensionsAreRejectedBeforeBackendAllocationAndInvalidationRetriesMissing() throws Exception {
        MemoryResources resources = new MemoryResources();
        resources.add("demo:textures/huge.png", pngHeader(100000, 100000));
        RecordingBackend backend = new RecordingBackend();
        try (MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, () -> 1)) {
            assertEquals(UiErrorCodes.INVALID_RESOURCE_SIZE, resolver.resolveTexture("demo:huge").code());
            assertEquals(0, backend.uploads);
            assertEquals(UiErrorCodes.MISSING_RESOURCE, resolver.resolveTexture("demo:late").code());
            resources.add("demo:textures/late.png", pngHeader(8, 8));
            assertEquals(UiErrorCodes.MISSING_RESOURCE, resolver.resolveTexture("demo:late").code());
            resolver.invalidate();
            assertEquals(null, resolver.resolveTexture("demo:late").code());
        }
    }

    @Test
    void explicitTexturePrefixAndFontDefinitionsUseTheSameResourceManager() {
        MemoryResources resources = new MemoryResources();
        resources.add("demo:font/custom.json", "{\"providers\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RecordingBackend backend = new RecordingBackend();
        try (MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, () -> 1)) {
            assertEquals("demo:font/custom.json", resolver.resolveFont("demo:custom").resolvedPath());
            assertEquals("demo:textures/gui/panel.png", MinecraftUiResourceResolver.textureLocation(
                    com.tkisor.nekojs.api.ui.UiResourceId.parse("demo:textures/gui/panel.png").orElseThrow()).toString());
        }
    }

    @Test
    void fontProviderLoadingFailureIsCachedAndReloadCanRecover() {
        MemoryResources resources = new MemoryResources();
        byte[] font = "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"demo:font/test.png\",\"height\":8,\"ascent\":7,\"chars\":[\"A\"]}]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        resources.add("demo:font/test.json", font);
        resources.add("demo:textures/font/test.png", new byte[]{1, 2, 3});
        RecordingBackend backend = new RecordingBackend();
        try (MinecraftUiResourceResolver resolver = new MinecraftUiResourceResolver(resources, backend, () -> 1)) {
            var failed = resolver.resolveFont("demo:test");
            assertEquals(UiErrorCodes.RESOURCE_LOAD_FAILED, failed.code());
            assertNotNull(resolver.failureCause("demo:test"));
            assertEquals(UiErrorCodes.RESOURCE_LOAD_FAILED, resolver.resolveFont("demo:test").code());

            resources.add("demo:textures/font/test.png", java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
            resolver.invalidate();
            assertEquals(null, resolver.resolveFont("demo:test").code());
        }
    }

    private static byte[] pngHeader(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeLong(-8552249625308161526L);
        output.writeInt(13);
        output.writeInt(1229472850);
        output.writeInt(width);
        output.writeInt(height);
        return bytes.toByteArray();
    }

    private static final class RecordingBackend implements UiTextureBackend {
        int uploads;
        boolean decodingFailure;
        boolean uploadFailure;
        final IOException uploadCause = new IOException("upload fixture");
        final List<Identifier> released = new ArrayList<>();
        final Map<Identifier, RuntimeException> releaseFailures = new HashMap<>();

        @Override
        public Size upload(Identifier slot, Identifier resource, byte[] bytes) throws Failure {
            uploads++;
            if (decodingFailure || uploadFailure) throw new Failure(decodingFailure, uploadCause);
            try {
                var info = net.minecraft.util.PngInfo.fromBytes(bytes);
                return new Size(info.width(), info.height());
            } catch (IOException failure) {
                throw new Failure(true, failure);
            }
        }

        @Override
        public void release(Identifier slot) {
            released.add(slot);
            RuntimeException failure = releaseFailures.get(slot);
            if (failure != null) throw failure;
        }
    }

    private static final class MemoryResources implements ResourceManager {
        final Map<Identifier, Resource> entries = new HashMap<>();
        int lookups;
        RuntimeException lookupFailure;

        void add(String id, byte[] bytes) { add(id, new Resource(null, () -> new ByteArrayInputStream(bytes))); }
        void add(String id, Resource resource) { entries.put(Identifier.parse(id), resource); }
        public Optional<Resource> getResource(Identifier id) {
            lookups++;
            if (lookupFailure != null) throw lookupFailure;
            return Optional.ofNullable(entries.get(id));
        }
        public Set<String> getNamespaces() { return Set.of("demo"); }
        public List<Resource> getResourceStack(Identifier id) { return getResource(id).stream().toList(); }
        public Map<Identifier, Resource> listResources(String directory, Predicate<Identifier> filter) { return Map.copyOf(entries); }
        public Map<Identifier, List<Resource>> listResourceStacks(String directory, Predicate<Identifier> filter) { return Map.of(); }
        public Stream<PackResources> listPacks() { return Stream.empty(); }
    }
}
//?}
