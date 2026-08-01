package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

/**
 * 1.12.2 声音注册器（{@code StartupEvents.registry('soundEvent')}）。
 *
 * <p>1.12.2 的 {@link SoundEvent} 直接构造（{@code new SoundEvent(ResourceLocation)}）。
 * 注册到 {@code ForgeRegistries.SOUND_EVENTS}。注意：可播放仍需资源包提供
 * {@code assets/<mod>/sounds.json} + {@code .ogg} 文件（仅注册 registry 条目是静音）。
 */
public class SoundEventBuilderJS {

    private final String registryName;

    public SoundEventBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    public String getRegistryName() {
        return registryName;
    }

    public SoundEvent build() {
        ResourceLocation rl = new ResourceLocation(registryName);
        SoundEvent sound = new SoundEvent(rl);
        sound.setRegistryName(rl);
        return sound;
    }
}
