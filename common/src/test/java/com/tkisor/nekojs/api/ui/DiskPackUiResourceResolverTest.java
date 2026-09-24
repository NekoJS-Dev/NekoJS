package com.tkisor.nekojs.api.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolution against a temp NekoJS disk pack root, mirroring the layout ticket
 * 29's {@code Assets} binding writes ({@code assets/<ns>/textures/...}).
 */
class DiskPackUiResourceResolverTest {
    @TempDir
    Path packRoot;

    @Test
    void resolvesTextureFromDiskPack() throws IOException {
        Path texture = packRoot.resolve("assets/mymod/textures/gui/panel.png");
        Files.createDirectories(texture.getParent());
        Files.writeString(texture, "png");

        DiskPackUiResourceResolver resolver = new DiskPackUiResourceResolver(packRoot);
        ResourceStatus status = resolver.resolveTexture("mymod:gui/panel");
        assertEquals(ResourceStatus.State.RESOLVED, status.state());
        assertEquals(texture.toString(), status.resolvedPath());
        assertEquals("mymod:gui/panel", status.id());
    }

    @Test
    void textureWithExplicitSuffixIsNotDoubled() throws IOException {
        Path texture = packRoot.resolve("assets/mymod/textures/gui/panel.png");
        Files.createDirectories(texture.getParent());
        Files.writeString(texture, "png");

        ResourceStatus status = new DiskPackUiResourceResolver(packRoot).resolveTexture("mymod:gui/panel.png");
        assertEquals(ResourceStatus.State.RESOLVED, status.state());
    }

    @Test
    void missingTextureReportsLocatableDiagnostic() {
        ResourceStatus status = new DiskPackUiResourceResolver(packRoot).resolveTexture("mymod:gui/absent");
        assertEquals(ResourceStatus.State.MISSING, status.state());
        assertEquals("NEKO-6004", status.code());
        assertTrue(status.message().contains("mymod:gui/absent"));
    }

    @Test
    void invalidIdIsRejectedBeforeTouchingTheDisk() {
        ResourceStatus status = new DiskPackUiResourceResolver(packRoot).resolveTexture("../escape");
        assertEquals(ResourceStatus.State.INVALID, status.state());
        assertEquals("NEKO-6003", status.code());
    }

    @Test
    void resolvesFontInBothVanillaSpellings() throws IOException {
        Path legacy = packRoot.resolve("assets/mymod/font/custom.json");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "{}");
        assertEquals(ResourceStatus.State.RESOLVED,
                new DiskPackUiResourceResolver(packRoot).resolveFont("mymod:custom").state());

        Path modern = packRoot.resolve("assets/other/fonts/custom.json");
        Files.createDirectories(modern.getParent());
        Files.writeString(modern, "{}");
        assertEquals(ResourceStatus.State.RESOLVED,
                new DiskPackUiResourceResolver(packRoot).resolveFont("other:custom").state());
    }

    @Test
    void missingFontReportsMissing() {
        assertEquals(ResourceStatus.State.MISSING,
                new DiskPackUiResourceResolver(packRoot).resolveFont("mymod:none").state());
    }
}
