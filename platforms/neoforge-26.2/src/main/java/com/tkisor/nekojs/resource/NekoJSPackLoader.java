package com.tkisor.nekojs.resource;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

@EventBusSubscriber
public class NekoJSPackLoader {

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES
                || event.getPackType() == PackType.SERVER_DATA) {

            event.addRepositorySource(consumer -> {

                Pack pack = Pack.readMetaAndCreate(
                        new PackLocationInfo(
                                NekoJS.MODID,
                                Component.literal("NekoJS"),
                                NekoJSPackSource.PACK_SOURCE_NEKO,
                                Optional.empty()
                        ),
                        new Pack.ResourcesSupplier() {
                            @Override
                            public @NonNull PackResources openPrimary(PackLocationInfo location) {
                                return new NekoJSPathPackResources(location.id(), NekoJSPaths.get().root(), event.getPackType());
                            }

                            @Override
                            public @NonNull PackResources openFull(@NonNull PackLocationInfo location, Pack.@NonNull Metadata metadata) {
                                return openPrimary(location);
                            }
                        },
                        event.getPackType(),
                        new PackSelectionConfig(
                                true,
                                Pack.Position.TOP,
                                false
                        )
                );

                if (pack != null) {
                    consumer.accept(pack);
                }
            });
        }
    }
}
