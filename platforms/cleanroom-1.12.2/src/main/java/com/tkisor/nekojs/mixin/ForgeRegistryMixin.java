package com.tkisor.nekojs.mixin;

import com.google.common.collect.BiMap;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 1.12.2 ForgeRegistryMixin - provides safe registry entry removal by
 * directly manipulating the ForgeRegistry BiMaps.
 *
 * <p>Pattern adapted from GroovyScript's ForgeRegistryMixin.</p>
 *
 * <p>已知代价（1.12.2 无官方运行时反注册 API）：只摘除 names/ids/owners 三张 BiMap，
 * 不清理 RegistryDelegate / slave registry 中的旧引用，也不触发 registry 事件；持有旧
 * RegistryDelegate 的调用方在下次 delegate 解析前可能读到陈旧值。同步在 registry 实例
 * 上进行，与 ForgeRegistry 自身的 add/remove 锁保持一致，避免与其他 mod 的注册表读取竞争。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryMixin {

    @Shadow(remap = false)
    @Final
    private BiMap names;

    @Shadow(remap = false)
    @Final
    private BiMap ids;

    @Shadow(remap = false)
    @Final
    private BiMap owners;

    /**
     * Remove an entry from the ForgeRegistry's internal BiMaps.
     * Called by NekoJS recipe system to remove recipes at runtime.
     */
    public void nekojs$removeEntry(Object name) {
        if (name == null) return;
        synchronized (this) {
            Object entry = this.names.remove(name);
            if (entry == null) return;
            this.ids.inverse().remove(entry);
            this.owners.inverse().remove(entry);
        }
    }
}
