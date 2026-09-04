package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * 玩家与实体交互事件（{@code PlayerEvents.entityInteract}）的加载器中立载荷。
 *
 * <p>成员对齐 NeoForge {@code PlayerInteractEvent.EntityInteract} 的 getter 形态：
 * {@code event.player} / {@code event.target}（getTarget，右击的对象实体）/
 * {@code event.hand}（getHand）。
 *
 * <p>可取消：监听器返回 {@code true} 时，fabric 侧取消整个 {@code ServerboundInteractPacket}
 * 的处理（不执行 {@code ServerPlayer#interactOn}）——语义与 NeoForge 的 ICancellableEvent
 * 一致。仅服务端触发，与攻击实体（handleAttack）无涉。
 */
@Doc("Fired when a player interacts (right-clicks) an entity (PlayerEvents.entityInteract).")
@Doc("Return true to cancel the interaction.")
@Getter
public class PlayerEntityInteractEventJS {

    @Doc("The interacting player.")
    private final ServerPlayer player;

    @Doc("The entity being interacted with.")
    private final Entity target;

    @Doc("The hand used (main hand or off hand).")
    private final InteractionHand hand;

    public PlayerEntityInteractEventJS(ServerPlayer player, Entity target, InteractionHand hand) {
        this.player = player;
        this.target = target;
        this.hand = hand;
    }
}
