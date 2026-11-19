package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a client interaction key input event.
 */
@Doc("Immutable portable snapshot of a client interaction key input event.")
public final class StableClientInputEvent {
    private final boolean attack;
    private final boolean useItem;
    private final boolean pickBlock;
    private final String hand;

    /**
     * Creates an immutable client input event snapshot.
     *
     * @param attack true when the attack input triggered the event
     * @param useItem true when the use-item input triggered the event
     * @param pickBlock true when the pick-block input triggered the event
     * @param hand interaction hand name; null is normalized to an empty string
     */
    @Doc("Creates an immutable client input event snapshot.")
    @Param(name = "attack", value = "true when the attack input triggered the event")
    @Param(name = "useItem", value = "true when the use-item input triggered the event")
    @Param(name = "pickBlock", value = "true when the pick-block input triggered the event")
    @Param(name = "hand", value = "interaction hand name; null is normalized to an empty string")
    public StableClientInputEvent(boolean attack, boolean useItem, boolean pickBlock, String hand) {
        this.attack = attack;
        this.useItem = useItem;
        this.pickBlock = pickBlock;
        this.hand = Objects.requireNonNullElse(hand, "");
    }

    /**
     * Returns whether the attack input is active.
     *
     * @return true when attack input is active
     */
    @Doc("Returns whether the attack input is active.")
    @Return("true when attack input is active")
    public boolean isAttack() {
        return attack;
    }

    /**
     * Returns whether the use-item input is active.
     *
     * @return true when use-item input is active
     */
    @Doc("Returns whether the use-item input is active.")
    @Return("true when use-item input is active")
    public boolean isUseItem() {
        return useItem;
    }

    /**
     * Returns whether the pick-block input is active.
     *
     * @return true when pick-block input is active
     */
    @Doc("Returns whether the pick-block input is active.")
    @Return("true when pick-block input is active")
    public boolean isPickBlock() {
        return pickBlock;
    }

    /**
     * Returns the interaction hand name.
     *
     * @return non-null interaction hand name
     */
    @Doc("Returns the interaction hand name.")
    @Return("non-null interaction hand name")
    public String getHand() {
        return hand;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableClientInputEvent that)) return false;
        return this.attack == that.attack
                && this.useItem == that.useItem
                && this.pickBlock == that.pickBlock
                && Objects.equals(this.hand, that.hand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attack, useItem, pickBlock, hand);
    }

    @Override
    public String toString() {
        return "StableClientInputEvent{"
                + "attack=" + attack
                + ", useItem=" + useItem
                + ", pickBlock=" + pickBlock
                + ", hand='" + hand + "'"
                + "}";
    }
}
