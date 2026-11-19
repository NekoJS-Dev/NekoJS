package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Portable player reference for resolving player stage handles without exposing loader or Minecraft player types.
 *
 * @param uuid       canonical player UUID string (normalized; non-blank unless {@code fake} is true)
 * @param name       player profile name (normalized to empty string when null)
 * @param fake       whether the referenced player is a simulated/fake player
 * @param clientSide whether the player reference originated on the logical client
 */
@Doc("Portable player reference for resolving player stage handles.")
@ContractReceiver
public record StagePlayerRef(String uuid, String name, boolean fake, boolean clientSide) {

    /** Shared fake-player sentinel that resolves to {@link StageHandle#EMPTY}. */
    public static final StagePlayerRef FAKE = new StagePlayerRef("", "", true, false);

    /**
     * Normalizes fields and validates that non-fake player references carry a non-blank UUID.
     */
    public StagePlayerRef {
        uuid = uuid == null ? "" : uuid.trim();
        name = Objects.requireNonNullElse(name, "");
        if (!fake && uuid.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4070] Player stage reference requires a non-blank UUID");
        }
    }

    /**
     * Creates a server-side real player reference from UUID and name.
     *
     * @param uuid canonical player UUID string
     * @param name player name; null is normalized to an empty string
     * @return player stage reference
     */
    @Doc("Creates a server-side real player reference from UUID and name.")
    @Param(name = "uuid", value = "canonical player UUID string")
    @Param(name = "name", value = "player name; null is normalized to an empty string")
    @Return("player stage reference")
    public static StagePlayerRef of(String uuid, @Nullable String name) {
        return new StagePlayerRef(uuid, name, false, false);
    }

    /**
     * Creates a server-side real player reference from UUID only.
     *
     * @param uuid canonical player UUID string
     * @return player stage reference
     */
    @Doc("Creates a server-side real player reference from UUID only.")
    @Param(name = "uuid", value = "canonical player UUID string")
    @Return("player stage reference")
    public static StagePlayerRef ofUuid(String uuid) {
        return new StagePlayerRef(uuid, "", false, false);
    }

    /**
     * Returns a fake-player reference that resolves to a no-op {@link StageHandle#EMPTY}.
     *
     * @return fake player stage reference
     */
    @Doc("Returns a fake-player reference that resolves to a no-op StageHandle.")
    @Return("fake player stage reference")
    public static StagePlayerRef ofFake() {
        return FAKE;
    }

    /**
     * Returns a fake-player reference that resolves to a no-op {@link StageHandle#EMPTY}.
     *
     * @return fake player stage reference
     */
    @Doc("Returns a fake-player reference that resolves to a no-op StageHandle.")
    @Return("fake player stage reference")
    public static StagePlayerRef fakePlayer() {
        return FAKE;
    }

    /**
     * Creates a named fake-player reference that resolves to a no-op {@link StageHandle#EMPTY}.
     *
     * @param name optional fake player name
     * @return fake player stage reference
     */
    @Doc("Creates a named fake-player reference that resolves to a no-op StageHandle.")
    @Param(name = "name", value = "optional fake player name")
    @Return("fake player stage reference")
    public static StagePlayerRef fake(@Nullable String name) {
        return new StagePlayerRef("", name, true, false);
    }

    /**
     * Creates a logical-client player reference.
     *
     * @param uuid canonical player UUID string
     * @param name player name; null is normalized to an empty string
     * @return client-side player stage reference
     */
    @Doc("Creates a logical-client player reference.")
    @Param(name = "uuid", value = "canonical player UUID string")
    @Param(name = "name", value = "player name; null is normalized to an empty string")
    @Return("client-side player stage reference")
    public static StagePlayerRef client(String uuid, @Nullable String name) {
        return new StagePlayerRef(uuid, name, false, true);
    }
}
