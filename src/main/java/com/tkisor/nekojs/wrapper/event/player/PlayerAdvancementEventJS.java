package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家获得进度事件（{@code PlayerEvents.advancement}）的加载器中立载荷。
 *
 * <p>对齐 NeoForge {@code AdvancementEvent.AdvancementEarnEvent} 的 getter 形态：
 * {@code event.advancement}（AdvancementHolder）；另附 {@code id}（注册名 Identifier）
 * 与 {@code displayName}（展示标题，无 display 的进度为 null）两个便捷成员。
 *
 * <p>触发语义与 NeoForge 一致：{@code PlayerAdvancements#award} 中某进度从「未完成」
 * 变为「完成」（{@code !wasDone && progress.isDone()}）且该进度带 display 时触发一次
 * （内部无 display 的进度不触发，与 NeoForge 的 ifPresent 语义相同）。
 */
@Doc("Fired when a player earns an advancement (PlayerEvents.advancement).")
@Doc("event.advancement is the AdvancementHolder; event.id its Identifier; event.displayName the title or null.")
@Getter
public class PlayerAdvancementEventJS {

    @Doc("The player who earned the advancement.")
    private final ServerPlayer player;

    @Doc("The earned advancement (AdvancementHolder).")
    private final AdvancementHolder advancement;

    @Doc("Registry identifier of the advancement (advancement.id()).")
    private final Identifier id;

    @Doc("Display title of the advancement (Component), or null when the advancement has no display.")
    private final Component displayName;

    public PlayerAdvancementEventJS(ServerPlayer player, AdvancementHolder advancement) {
        this.player = player;
        this.advancement = advancement;
        this.id = advancement.id();
        this.displayName = advancement.value().display().map(DisplayInfo::getTitle).orElse(null);
    }
}
