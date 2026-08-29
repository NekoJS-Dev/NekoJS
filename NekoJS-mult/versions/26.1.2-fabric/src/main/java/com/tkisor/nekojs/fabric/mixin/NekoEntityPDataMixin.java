package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.api.inject.NekoEntityPData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fabric 侧实体持久化数据（NeoForge {@code Entity#getPersistentData()} 的等价物）：
 * mixin 加 {@code neko$pdataTag} 字段随实体存档读写（{@code addAdditionalSaveData} /
 * {@code readAdditionalSaveData} 钩子），访问经 {@link NekoEntityPData} duck 接口。
 * {@code EntityExtension} 的 {@code neko$pdata()} 读写它——两平台脚本侧同形。
 *
 * <p>存档格式与 NeoForge 侧兼容：外层 {@code NeoForgeData}（NeoForge 容器键，
 * 见 minecraft-patched Entity#addAdditionalSaveData 的 storeNullable("NeoForgeData", ...)）
 * 下挂 NekoJS 的 {@code NekoJSPersistentData} 子键——同一存档在两个加载器间迁移时 pdata 不丢。
 */
@Mixin(Entity.class)
public abstract class NekoEntityPDataMixin implements NekoEntityPData {

    @Unique
    private static final String NEKO$CONTAINER_KEY = "NeoForgeData";

    @Unique
    private CompoundTag neko$pdataTag;

    @Unique
    private CompoundTag neko$ensureTag() {
        if (neko$pdataTag == null) {
            neko$pdataTag = new CompoundTag();
        }
        return neko$pdataTag;
    }

    @Override
    public CompoundTag neko$getPDataRoot() {
        return neko$ensureTag();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void neko$savePData(ValueOutput output, CallbackInfo ci) {
        if (neko$pdataTag != null && !neko$pdataTag.isEmpty()) {
            CompoundTag container = new CompoundTag();
            container.put(EntityExtension.NEKO_PDATA_KEY, neko$pdataTag.copy());
            output.store(NEKO$CONTAINER_KEY, CompoundTag.CODEC, container);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void neko$loadPData(ValueInput input, CallbackInfo ci) {
        neko$pdataTag = input.read(NEKO$CONTAINER_KEY, CompoundTag.CODEC)
                .map(container -> container.getCompound(EntityExtension.NEKO_PDATA_KEY).orElseGet(CompoundTag::new).copy())
                .orElse(null);
    }
}
