package com.tkisor.nekojs.wrapper.event.level;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;

/**
 * 维度保存事件（{@code LevelEvents.saved}）的**加载器中立**载荷。
 *
 * <p>NeoForge 侧直传原生 {@code LevelEvent.Save}（成员仅 {@code getLevel()}）；
 * fabric 侧由 {@code MixinServerLevelSave} 在
 * {@code ServerLevel#save(ProgressListener, boolean, boolean)} 的 TAIL 转换并投递。
 *
 * <p>**刻意不带保存标志**：26.x 的 {@code ServerLevel#save} 带两个 boolean 参数
 * （本批次 javap 实证 {@code public void save(net.minecraft.util.ProgressListener, boolean, boolean)}），
 * 其语义未在 Mojang 原始字节码层面直接可读；且 NeoForge 的原生 {@code LevelEvent.Save} 也
 * 不暴露任何标志——设计中立载荷时与之对齐，不硬造字段。
 *
 * <p>不可取消（NeoForge 的 {@code LevelEvent.Save} 不可取消；fabric 侧在保存完成后
 * 投递，取消没有时机）。
 */
@Doc("Fired when a server level is saved (LevelEvents.saved).")
@Getter
public class LevelSavedEventJS extends LevelEventJS {

    public LevelSavedEventJS(ServerLevel level) {
        super(level);
    }
}
