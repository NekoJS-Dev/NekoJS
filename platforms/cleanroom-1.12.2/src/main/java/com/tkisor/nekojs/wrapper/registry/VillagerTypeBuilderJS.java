package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.VillagerRegistry;

/**
 * 1.12.2 村民职业注册器（{@code StartupEvents.registry('villagerType')}）。
 *
 * <p>1.12.2 的村民职业是 {@link VillagerRegistry.VillagerProfession}
 * （注册到 {@code ForgeRegistries.VILLAGER_PROFESSIONS}）。纹理为
 * {@code assets/<mod>/textures/entity/villager/<name>.png}（含僵尸村民纹理）。
 */
@Doc("Builder for registering a new villager profession; obtain it from RegistryEvents.villagerType.create(id).")
public class VillagerTypeBuilderJS {

    private final String registryName;
    private String texture;
    private String zombieTexture;

    public VillagerTypeBuilderJS(String registryName) {
        this.registryName = registryName;
        // 默认纹理与 id 同名（资源包路径）
        ResourceLocation rl = new ResourceLocation(registryName);
        this.texture = rl.getNamespace() + ":textures/entity/villager/" + rl.getPath() + ".png";
        this.zombieTexture = rl.getNamespace() + ":textures/entity/zombie_villager/" + rl.getPath() + ".png";
    }

    /** 村民纹理资源路径（默认 {@code <ns>:textures/entity/villager/<path>.png}）。 */
    @Doc("Sets the villager texture.")
    @Param(name = "texture", value = "texture path like 'mymod:textures/entity/villager/my_profession.png'")
    @Return("this builder, for chaining")
    public VillagerTypeBuilderJS texture(String texture) {
        this.texture = texture;
        return this;
    }

    /** 僵尸村民纹理资源路径。 */
    @Doc("Sets the zombie villager texture.")
    @Param(name = "zombieTexture", value = "texture path like 'mymod:textures/entity/zombie_villager/my_profession.png'")
    @Return("this builder, for chaining")
    public VillagerTypeBuilderJS zombieTexture(String zombieTexture) {
        this.zombieTexture = zombieTexture;
        return this;
    }

    /** 注册名。 */
    @Doc("Gets the registry name of the profession being built.")
    @Return("the registry name string")
    public String getRegistryName() {
        return registryName;
    }

    /** 构建职业实例（不注册）。 */
    @Doc("Builds the VillagerProfession; registration happens when the event completes.")
    @Return("the profession with its textures")
    public VillagerRegistry.VillagerProfession build() {
        VillagerRegistry.VillagerProfession profession =
                new VillagerRegistry.VillagerProfession(registryName, texture, zombieTexture);
        profession.setRegistryName(new ResourceLocation(registryName));
        return profession;
    }
}
