//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ui.ResourceStatus;
import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiErrorCodes;
import com.tkisor.nekojs.api.ui.UiResourceId;
import com.tkisor.nekojs.api.ui.UiResourceResolver;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.PngInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resolves the existing Minecraft resource stack and owns one generation's uploaded UI textures.
 * Prepare resources on the client owner thread before painting; close releases only this owner's
 * texture slots. Resource-pack revision changes or explicit invalidation retire cached entries
 * without releasing slots still used by the active host plan. Commit prepared plans before calling
 * {@link #commitPrepared()}; close drains every owned slot. Successes and failures are cached so
 * reload can retry a missing or repaired image without reading or decoding each frame.
 */
public final class MinecraftUiResourceResolver implements UiResourceResolver, AutoCloseable {
    private static final int MAX_TEXTURE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_FONT_BYTES = 1024 * 1024;
    private final ResourceManager resources;
    private final UiTextureBackend backend;
    private final Supplier<?> revision;
    private final Thread ownerThread;
    private final String slotPrefix = "ui/" + UUID.randomUUID().toString().replace("-", "") + "/";
    private final Map<String, Texture> textures = new HashMap<>();
    private final List<Texture> retiredTextures = new ArrayList<>();
    private final Map<String, ResourceStatus> fonts = new HashMap<>();
    private final Map<String, Throwable> fontFailures = new HashMap<>();
    private Object currentRevision;
    private long nextSlot;
    private boolean closed;

    public MinecraftUiResourceResolver(ResourceManager resources, TextureManager textures) {
        this(resources, UiTextureBackend.minecraft(textures), () -> resources.listPacks().toList());
    }

    MinecraftUiResourceResolver(ResourceManager resources, UiTextureBackend backend, Supplier<?> revision) {
        this.resources = resources;
        this.backend = backend;
        this.revision = revision;
        this.ownerThread = Thread.currentThread();
        this.currentRevision = revision.get();
    }

    @Override
    public ResourceStatus resolveTexture(String rawId) {
        return texture(rawId).status();
    }

    public Texture texture(String rawId) {
        requireOpen();
        refresh();
        Optional<UiResourceId> parsed = UiResourceId.parse(rawId);
        if (parsed.isEmpty()) return new Texture(ResourceStatus.invalid(rawId), null, 0, 0);
        UiResourceId id = parsed.get();
        return textures.computeIfAbsent(id.toString(), ignored -> load(id));
    }

    @Override
    public ResourceStatus resolveFont(String rawId) {
        requireOpen();
        refresh();
        Optional<UiResourceId> parsed = UiResourceId.parse(rawId);
        if (parsed.isEmpty()) return ResourceStatus.invalid(rawId);
        UiResourceId id = parsed.get();
        return fonts.computeIfAbsent(id.toString(), ignored -> {
            String path = id.path().endsWith(".json") ? id.path() : id.path() + ".json";
            Identifier location = Identifier.fromNamespaceAndPath(id.namespace(), "font/" + path);
            byte[] bytes;
            try {
                Optional<Resource> definition = resources.getResource(location);
                if (definition.isEmpty()) return ResourceStatus.missing(id);
                try (InputStream input = definition.get().open()) {
                    bytes = input.readNBytes(MAX_FONT_BYTES + 1);
                }
            } catch (IOException | RuntimeException failure) {
                fontFailures.put(id.toString(), failure);
                return new ResourceStatus(ResourceStatus.State.INVALID, id.toString(), null,
                        UiErrorCodes.RESOURCE_LOAD_FAILED, "UI font definition read failed: " + id);
            }
            if (bytes.length > MAX_FONT_BYTES) {
                return new ResourceStatus(ResourceStatus.State.INVALID, id.toString(), null,
                        UiErrorCodes.INVALID_RESOURCE_SIZE, "UI font definition exceeds the encoded byte limit: " + id);
            }
            try {
                var json = com.google.gson.JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                if (!json.isJsonObject() || !json.getAsJsonObject().has("providers")) {
                    throw new IllegalArgumentException("Font definition requires a providers array");
                }
                net.minecraft.client.gui.font.providers.GlyphProviderDefinition.Conditional.CODEC.listOf()
                        .parse(com.mojang.serialization.JsonOps.INSTANCE, json.getAsJsonObject().get("providers"))
                        .getOrThrow();
                return ResourceStatus.resolved(id.toString(), location.toString());
            } catch (RuntimeException failure) {
                fontFailures.put(id.toString(), failure);
                return new ResourceStatus(ResourceStatus.State.INVALID, id.toString(), null,
                        UiErrorCodes.RESOURCE_DECODE_FAILED, "UI font definition decode failed: " + id);
            }
        });
    }

    /** Returns whether cached texture plans must be prepared again after a pack reload. */
    public boolean refresh() {
        requireOpen();
        Object nextRevision = revision.get();
        if (java.util.Objects.equals(currentRevision, nextRevision)) return false;
        invalidate();
        currentRevision = nextRevision;
        return true;
    }

    public void invalidate() {
        requireOpen();
        retiredTextures.addAll(textures.values());
        textures.clear();
        fonts.clear();
        fontFailures.clear();
    }

    /** Releases retired slots after the caller successfully publishes the prepared host plan. */
    public void commitPrepared() {
        requireOpen();
        List<Texture> retired = List.copyOf(retiredTextures);
        retiredTextures.clear();
        releaseTextures(retired);
    }

    /** Returns a cached failure cause without looking up resources or changing their revision. */
    public Throwable failureCause(String rawId) {
        requireOpen();
        Optional<UiResourceId> parsed = UiResourceId.parse(rawId);
        if (parsed.isEmpty()) return null;
        String id = parsed.get().toString();
        Texture texture = textures.get(id);
        return texture != null && texture.cause() != null ? texture.cause() : fontFailures.get(id);
    }

    @Override
    public void close() {
        if (closed) return;
        requireOwner();
        List<Texture> owned = new ArrayList<>(retiredTextures);
        owned.addAll(textures.values());
        retiredTextures.clear();
        textures.clear();
        fonts.clear();
        fontFailures.clear();
        try {
            releaseTextures(owned);
        } finally {
            closed = true;
        }
    }

    private Texture load(UiResourceId id) {
        Identifier resourceId = textureLocation(id);
        Optional<Resource> resource;
        try {
            resource = resources.getResource(resourceId);
        } catch (RuntimeException failure) {
            return failed(id, UiErrorCodes.RESOURCE_LOAD_FAILED, "UI texture lookup failed", failure);
        }
        if (resource.isEmpty()) return new Texture(ResourceStatus.missing(id), null, 0, 0);
        byte[] bytes;
        try (InputStream stream = resource.get().open()) {
            bytes = stream.readNBytes(MAX_TEXTURE_BYTES + 1);
            if (bytes.length > MAX_TEXTURE_BYTES) {
                return failed(id, UiErrorCodes.RESOURCE_LOAD_FAILED, "UI texture exceeds the 16 MiB input limit");
            }
        } catch (IOException | RuntimeException failure) {
            return failed(id, UiErrorCodes.RESOURCE_LOAD_FAILED, "UI texture read failed", failure);
        }
        PngInfo dimensions;
        try {
            dimensions = PngInfo.fromBytes(bytes);
        } catch (IOException | RuntimeException failure) {
            return failed(id, UiErrorCodes.RESOURCE_DECODE_FAILED, "UI texture PNG header decode failed", failure);
        }
        if (dimensions.width() <= 0 || dimensions.height() <= 0
                || dimensions.width() > 8192 || dimensions.height() > 8192
                || (long) dimensions.width() * dimensions.height() > 16 * 1024 * 1024) {
            return failed(id, UiErrorCodes.INVALID_RESOURCE_SIZE, "UI texture dimensions exceed the supported image limits");
        }
        Identifier slot = Identifier.fromNamespaceAndPath("nekojs", slotPrefix + nextSlot++);
        try {
            UiTextureBackend.Size size = backend.upload(slot, resourceId, bytes);
            if (size.width() <= 0 || size.height() <= 0) {
                backend.release(slot);
                return failed(id, UiErrorCodes.INVALID_RESOURCE_SIZE, "UI texture dimensions must be positive");
            }
            return new Texture(ResourceStatus.resolved(id.toString(), resourceId.toString()), slot, size.width(), size.height());
        } catch (UiTextureBackend.Failure failure) {
            return failed(id, failure.decoding() ? UiErrorCodes.RESOURCE_DECODE_FAILED : UiErrorCodes.RESOURCE_LOAD_FAILED,
                    failure.decoding() ? "UI texture decode failed" : "UI texture upload failed", failure);
        }
    }

    static Identifier textureLocation(UiResourceId id) {
        String path = id.path();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        return Identifier.fromNamespaceAndPath(id.namespace(), path);
    }

    private static Texture failed(UiResourceId id, String code, String message) {
        return failed(id, code, message, null);
    }

    private static Texture failed(UiResourceId id, String code, String message, Throwable cause) {
        return new Texture(new ResourceStatus(ResourceStatus.State.INVALID, id.toString(), null, code,
                message + ": " + id), null, 0, 0, cause);
    }

    private void releaseTextures(Collection<Texture> owned) {
        Throwable firstFailure = null;
        for (Texture texture : owned) {
            if (texture.slot() == null) continue;
            try {
                backend.release(texture.slot());
            } catch (RuntimeException | Error failure) {
                if (firstFailure == null) firstFailure = failure;
                else if (firstFailure != failure) firstFailure.addSuppressed(failure);
            }
        }
        if (firstFailure instanceof RuntimeException failure) throw failure;
        if (firstFailure instanceof Error failure) throw failure;
    }

    private void requireOpen() {
        requireOwner();
        if (closed) throw new IllegalStateException("[NEKO-7001] UI texture owner is closed");
    }

    private void requireOwner() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("[NEKO-7004] UI textures must be prepared on the client owner thread");
        }
    }

    public record Texture(ResourceStatus status, Identifier slot, int width, int height, Throwable cause) {
        public Texture(ResourceStatus status, Identifier slot, int width, int height) {
            this(status, slot, width, height, null);
        }

        public UiDiagnostic diagnostic(UiDiagnostic.Location location) {
            if (status.state() == ResourceStatus.State.RESOLVED) return null;
            return new UiDiagnostic(status.code(), status.message(), location.rootId(), location.nodeType(),
                    location.nodeKey(), status.id(), location.generation());
        }
    }
}
//?}
