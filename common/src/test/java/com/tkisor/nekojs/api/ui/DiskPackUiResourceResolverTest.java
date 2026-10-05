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
    void pluralFontDirectoryDoesNotBecomeANativeFontDefinition() throws IOException {
        Path plural = packRoot.resolve("assets/mymod/fonts/custom.json");
        Files.createDirectories(plural.getParent());
        Files.writeString(plural, "{}");
        DiskPackUiResourceResolver resolver = new DiskPackUiResourceResolver(packRoot);
        assertEquals(ResourceStatus.State.MISSING, resolver.resolveFont("mymod:custom").state());
        Path definition = packRoot.resolve("assets/mymod/font/custom.json");
        Files.createDirectories(definition.getParent());
        Files.writeString(definition, "{}");
        ResourceStatus status = resolver.resolveFont("mymod:custom");
        assertEquals(ResourceStatus.State.RESOLVED, status.state());
        assertEquals(definition.toString(), status.resolvedPath());
        assertEquals(ResourceStatus.State.RESOLVED, resolver.resolveFont("mymod:custom.json").state());
    }

    @Test
    void explicitDefinitionSuffixPreservesALogicalFontPathEndingInJson() throws IOException {
        Path definition = packRoot.resolve("assets/mymod/font/custom.json.json");
        Files.createDirectories(definition.getParent());
        Files.writeString(definition, "{}");
        DiskPackUiResourceResolver resolver = new DiskPackUiResourceResolver(packRoot);
        ResourceStatus status = resolver.resolveFont("mymod:custom.json.json");
        assertEquals(ResourceStatus.State.RESOLVED, status.state());
        assertEquals(definition.toString(), status.resolvedPath());
        VisualSpec spec = VisualStyleResolver.resolve(java.util.Map.of("font", "mymod:custom.json.json"),
                new UiDiagnostic.Location("font-root", "label", "heading", 1), resolver);
        assertEquals("mymod:custom.json", spec.font().toString());
        assertTrue(spec.diagnostics().isEmpty());
    }

    @Test
    void missingFontReportsMissing() {
        assertEquals(ResourceStatus.State.MISSING,
                new DiskPackUiResourceResolver(packRoot).resolveFont("mymod:none").state());
    }
}
