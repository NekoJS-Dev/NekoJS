package com.tkisor.nekojs.api.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * {@link UiResourceResolver} over the NekoJS disk pack root ({@code <gameDir>/nekojs}).
 * Ids resolve under its {@code assets/<namespace>/...} tree, the same tree the
 * {@code Assets} binding writes to. Resolution reads the disk only: an in-memory pack
 * reload is not observed here, and the vanilla resource stack decides whether a texture
 * is available once the version owner wires texture blitting.
 */
public final class DiskPackUiResourceResolver implements UiResourceResolver {
    private final Path packRoot;

    /**
     * @param packRoot the NekoJS disk pack root ({@code <gameDir>/nekojs})
     */
    public DiskPackUiResourceResolver(Path packRoot) {
        this.packRoot = packRoot;
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
        return resolveParsed(parsed.get(), "font", ".json");
    }

    private ResourceStatus resolveParsed(UiResourceId id, String directory, String suffix) {
        String relative = id.path().endsWith(suffix) ? id.path() : id.path() + suffix;
        Path candidate = packRoot.resolve("assets").resolve(id.namespace())
                .resolve(directory).resolve(relative).normalize();
        // Id grammar already excludes traversal; the containment check keeps that
        // true even if the grammar rules ever drift.
        if (!candidate.startsWith(packRoot)) return ResourceStatus.invalid(id.toString());
        return Files.isRegularFile(candidate)
                ? ResourceStatus.resolved(id.toString(), candidate.toString())
                : ResourceStatus.missing(id);
    }
}
