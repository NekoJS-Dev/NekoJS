package com.tkisor.nekojs.api.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Common {@link UiResourceResolver} over the NekoJS disk pack root
 * ({@code <gameDir>/nekojs/assets}, the same root ticket 29's {@code Assets}
 * binding writes to). The platform side layers the vanilla resource manager on top
 * of this; pass the game dir's pack root in here.
 */
public final class DiskPackUiResourceResolver implements UiResourceResolver {
    private final Path assetsRoot;

    /**
     * @param assetsRoot the NekoJS disk pack root ({@code nekojs/assets})
     */
    public DiskPackUiResourceResolver(Path assetsRoot) {
        this.assetsRoot = assetsRoot;
    }

    @Override
    public ResourceStatus resolveTexture(String id) {
        Optional<UiResourceId> parsed = UiResourceId.parse(id);
        return parsed.isPresent()
                ? resolveParsed(parsed.get(), "textures", ".png")
                : ResourceStatus.invalid(String.valueOf(id));
    }

    @Override
    public ResourceStatus resolveFont(String id) {
        Optional<UiResourceId> parsed = UiResourceId.parse(id);
        if (parsed.isEmpty()) return ResourceStatus.invalid(String.valueOf(id));
        // Vanilla renamed the directory between versions; accept both spellings,
        // preferring the modern one when both exist.
        ResourceStatus modern = resolveParsed(parsed.get(), "fonts", ".json");
        if (modern.state() == ResourceStatus.State.RESOLVED) return modern;
        return resolveParsed(parsed.get(), "font", ".json");
    }

    private ResourceStatus resolveParsed(UiResourceId id, String directory, String suffix) {
        String relative = id.path().endsWith(suffix) ? id.path() : id.path() + suffix;
        Path candidate = assetsRoot.resolve("assets").resolve(id.namespace())
                .resolve(directory).resolve(relative).normalize();
        // Id grammar already excludes traversal; the containment check keeps that
        // true even if the grammar rules ever drift.
        if (!candidate.startsWith(assetsRoot)) return ResourceStatus.invalid(id.toString());
        return Files.isRegularFile(candidate)
                ? ResourceStatus.resolved(id.toString(), candidate.toString())
                : ResourceStatus.missing(id);
    }
}
