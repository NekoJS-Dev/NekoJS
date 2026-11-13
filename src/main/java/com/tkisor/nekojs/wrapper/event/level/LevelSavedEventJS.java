package com.tkisor.nekojs.wrapper.event.level;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;

/**
 * Loader-neutral payload for the level saved event ({@code LevelEvents.saved}).
 *
 * <p>NeoForge passes its native {@code LevelEvent.Save} straight through (its only member is
 * {@code getLevel()}); on fabric, {@code MixinServerLevelSave} converts the TAIL of
 * {@code ServerLevel#save(ProgressListener, boolean, boolean)} and posts from there.
 *
 * <p><b>Deliberately no save flags</b>: the 26.x {@code ServerLevel#save} takes two boolean
 * parameters (javap shows
 * {@code public void save(net.minecraft.util.ProgressListener, boolean, boolean)}), and their
 * meaning is not directly readable from the original Mojang bytecode. NeoForge's native
 * {@code LevelEvent.Save} exposes no flags either, so this loader-neutral payload aligns with
 * it instead of inventing fields.
 *
 * <p>Not cancellable: NeoForge's {@code LevelEvent.Save} cannot be cancelled, and on fabric it
 * posts after the save has finished, so there is no point at which a cancel could act.
 */
@Doc("Fired when a server level is saved (LevelEvents.saved).")
@Getter
public class LevelSavedEventJS extends LevelEventJS {

    public LevelSavedEventJS(ServerLevel level) {
        super(level);
    }
}
