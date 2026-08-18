package com.tkisor.nekojs.resource;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

public class NekoJSPathPackResources extends PathPackResources {

    private final byte[] meta;

    public NekoJSPathPackResources(String name, Path root, PackType type) {
        super(
                new PackLocationInfo(
                        name,
                        Component.literal(name),
                        NekoJSPackSource.PACK_SOURCE_NEKO,
                        Optional.empty()
                ),
                root
        );

        // SharedConstants.RESOURCE_PACK_FORMAT / DATA_PACK_FORMAT 已废弃：按 PackType 从
        // 当前 WorldVersion 取 pack version（getPackVersion(PackType)）
        int format = SharedConstants.getCurrentVersion().getPackVersion(type);

        String json = """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "NekoJS Resources"
                  }
                }
                """.formatted(format);

        this.meta = json.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && path[0].equals("pack.mcmeta")) {
            return () -> new ByteArrayInputStream(meta);
        }
        return super.getRootResource(path);
    }
}