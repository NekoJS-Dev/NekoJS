package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.resources.Identifier;

/**
 * Fabric 侧 {@link NekoIdCompat.Adapter}：只碰原版 API，与 NeoForge 侧同源。
 *
 * <p>B3 note：26.1+ 原版类名是 {@code Identifier}（1.21.x 叫 {@code ResourceLocation}）。
 * fabric 分支目前只有 26.1.2 一个节点，故直接写 26.x 形态；将来加 1.21.1 节点时，
 * 这一处交给控制器的 `mc_renames` replacements 处理，与 NeoForge 侧同一套规则。
 */
public final class FabricIdCompat implements NekoIdCompat.Adapter {
    @Override
    public Identifier toPlatformId(NekoId id) {
        return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
    }
}
