package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-neutral payload for the player advancement event ({@code PlayerEvents.advancement}).
 *
 * <p>The getters follow NeoForge {@code AdvancementEvent.AdvancementEarnEvent}:
 * {@code event.advancement} (AdvancementHolder), plus two convenience members, {@code id} (the
 * registration name as an Identifier) and {@code displayName} (the display title, null for an
 * advancement without a display).
 *
 * <p>The trigger follows NeoForge: in {@code PlayerAdvancements#award} it fires once when an
 * advancement turns from not done to done ({@code !wasDone && progress.isDone()}) and that
 * advancement has a display. Advancements without a display never fire, the same as NeoForge's
 * ifPresent behavior.
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
