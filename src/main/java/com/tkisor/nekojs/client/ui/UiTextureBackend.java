//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.IOException;

interface UiTextureBackend {
    Size upload(Identifier slot, Identifier resource, byte[] bytes) throws Failure;

    void release(Identifier slot);

    record Size(int width, int height) { }

    final class Failure extends Exception {
        private static final long serialVersionUID = 1L;
        private final boolean decoding;

        Failure(boolean decoding, Throwable cause) {
            super(cause);
            this.decoding = decoding;
        }

        boolean decoding() { return decoding; }
    }

    static UiTextureBackend minecraft(TextureManager textures) {
        return new UiTextureBackend() {
            @Override
            public Size upload(Identifier slot, Identifier resource, byte[] bytes) throws Failure {
                NativeImage image;
                try {
                    image = NativeImage.read(bytes);
                } catch (IOException | RuntimeException failure) {
                    throw new Failure(true, failure);
                }
                UiTextureUploaded texture = null;
                try (image) {
                    Size size = new Size(image.getWidth(), image.getHeight());
                    texture = new UiTextureUploaded();
                    texture.upload(resource, image);
                    textures.register(slot, texture);
                    return size;
                } catch (RuntimeException | Error failure) {
                    if (texture != null) {
                        try {
                            texture.close();
                        } catch (RuntimeException | Error cleanupFailure) {
                            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
                        }
                    }
                    if (failure instanceof Error fatal) throw fatal;
                    throw new Failure(false, failure);
                }
            }

            @Override
            public void release(Identifier slot) {
                textures.release(slot);
            }
        };
    }
}
//?}
