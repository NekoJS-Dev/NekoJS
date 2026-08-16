package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

/**
 * 1.12.2 声音注册器（{@code StartupEvents.registry('soundEvent')}）。
 *
 * <p>1.12.2 的 {@link SoundEvent} 直接构造（{@code new SoundEvent(ResourceLocation)}）。
 * 注册到 {@code ForgeRegistries.SOUND_EVENTS}。注意：可播放仍需资源包提供
 * {@code assets/<mod>/sounds.json} + {@code .ogg} 文件（仅注册 registry 条目是静音）。
 */
@Doc("Builder for registering a new sound event; obtain it from RegistryEvents.soundEvent.create(id).")
@Doc("The id must point at a sounds.json entry in a resource pack, otherwise the sound is silent.")
public class SoundEventBuilderJS {

    private final String registryName;

    /** Creates a builder for the given sound id. */
    public SoundEventBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    /** 注册名。 */
    @Doc("Gets the sound id of the event being built.")
    @Return("the sound id string")
    public String getRegistryName() {
        return registryName;
    }

    /** 构建声音事件实例（不注册）。 */
    @Doc("Builds the SoundEvent instance; registration happens when the event completes.")
    @Return("the sound event bound to its id")
    public SoundEvent build() {
        ResourceLocation rl = new ResourceLocation(registryName);
        SoundEvent sound = new SoundEvent(rl);
        sound.setRegistryName(rl);
        return sound;
    }
}
