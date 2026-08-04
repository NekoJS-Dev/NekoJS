package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;

/**
 * 1.12.2 BlockEvent 统一扩展：注入 {@code neko$getLevel()} 作为 {@code getWorld()} 的 alias，
 * 使脚本侧 {@code event.level} 在 NeoForge（原生 {@code getLevel()}）与 Cleanroom 上一致。
 */
@RemapByPrefix("neko$")
public interface BlockEventExtension {

    /**
     * 返回事件发生的 World（NeoForge 的 Level 在 1.12.2 等价于 World）。
     * 脚本侧 {@code event.level} 跨平台统一。
     *
     * @return 事件发生的 World
     */
    default World neko$getLevel() {
        return ((BlockEvent) this).getWorld();
    }
}
