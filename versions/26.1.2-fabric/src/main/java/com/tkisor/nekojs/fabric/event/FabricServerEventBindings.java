package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.player.PlayerLifecycleEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerLifecycleEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 服务端事件面的 fabric 桥 v1：生命周期（aboutToStart/starting/started/stopping/stopped）、
 * 服务端 tick（tickPre/tickPost）、玩家进出服（loggedIn/loggedOut）。
 *
 * <p>组名与总线名和 NeoForge 侧一致（同名组经 EventGroupRegistry 合并，fabric 节点上
 * NeoForge 核心插件不加载，无重复）；payload 为中立类，成员名对齐契约 getter 约定。
 * SERVER 脚本首次加载挂在 SERVER_STARTING（NeoForge 侧在 datapack reload listener
 * 注册期做，时机等价：都在世界装载完成后、STARTED 之前）。
 *
 * <p>NeoForge 侧这些总线直传原生事件——两类 payload 成员同形（getServer/getPlayer），
 * 脚本无感；后续批次把 NeoForge 侧也切到中立 payload 后即完全同型。
 *
 * <p><b>时机差异（fabric 已知）</b>：fabric 没有 NeoForge「Starting」（世界装载后、
 * Done 前）的等价事件，{@code starting} 与 {@code aboutToStart} 都在 fabric
 * SERVER_STARTING（世界装载<b>前</b>）触发——此时 {@code server.getPlayerList()}
 * 尚为 null，脚本请到 {@code started} 再访问玩家列表。
 */
public final class FabricServerEventBindings {

    /** 与 NeoForge 侧 bindings/event/ServerEvents 同名（fabric 子集）。 */
    public static final EventGroup SERVER_EVENTS = EventGroup.of("ServerEvents");

    public static final EventBusJS<ServerLifecycleEventJS, Void> ABOUT_TO_START =
            SERVER_EVENTS.server("aboutToStart", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerLifecycleEventJS, Void> STARTING =
            SERVER_EVENTS.server("starting", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerLifecycleEventJS, Void> STARTED =
            SERVER_EVENTS.server("started", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerLifecycleEventJS, Void> STOPPING =
            SERVER_EVENTS.server("stopping", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerLifecycleEventJS, Void> STOPPED =
            SERVER_EVENTS.server("stopped", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerTickEventJS, Void> TICK_PRE =
            SERVER_EVENTS.server("tickPre", ServerTickEventJS.class);
    public static final EventBusJS<ServerTickEventJS, Void> TICK_POST =
            SERVER_EVENTS.server("tickPost", ServerTickEventJS.class);

    /** 与 NeoForge 侧 bindings/event/PlayerEvents 同名（fabric 子集）。 */
    public static final EventGroup PLAYER_EVENTS = EventGroup.of("PlayerEvents");

    public static final EventBusJS<PlayerLifecycleEventJS, Void> LOGGED_IN =
            PLAYER_EVENTS.server("loggedIn", PlayerLifecycleEventJS.class);
    public static final EventBusJS<PlayerLifecycleEventJS, Void> LOGGED_OUT =
            PLAYER_EVENTS.server("loggedOut", PlayerLifecycleEventJS.class);

    /** chat：中立 payload（player/username/message 字符串，契约可移植成员）。 */
    public static final EventBusJS<com.tkisor.nekojs.wrapper.event.player.ServerChatEventJS, Void> CHAT =
            PLAYER_EVENTS.server("chat", com.tkisor.nekojs.wrapper.event.player.ServerChatEventJS.class);

    /** 等待下一个 tick 末 post loggedIn 的玩家（见 {@link #register} 中的时机说明）。 */
    private static final Queue<ServerPlayer> PENDING_LOGINS = new ConcurrentLinkedQueue<>();

    /**
     * 当前运行中的服务器实例（SERVER_STARTING 设、SERVER_STOPPED 清）。
     * fabric-api 无 {@code ServerLifecycleHooks#getCurrentServer} 等价物，配方 mixin 的
     * 广播段（RecipeManagerMixin 孪生）从这里取 server 找 gamemaster 玩家。
     */
    private static volatile MinecraftServer currentServer;

    /** @return 当前服务器实例；不在服务器生命周期内（纯客户端未开世界等）为 {@code null}。 */
    public static MinecraftServer currentServer() {
        return currentServer;
    }

    private FabricServerEventBindings() {}

    private static void drainPendingLogins() {
        ServerPlayer player;
        while ((player = PENDING_LOGINS.poll()) != null) {
            LOGGED_IN.post(new PlayerLifecycleEventJS(player));
        }
    }

    /**
     * @param loadServerScripts SERVER 脚本首次加载动作（{@code NekoRuntimeRoot.reload(SERVER)}）
     */
    public static void register(Runnable loadServerScripts) {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer = server;
            loadServerScripts.run();
            ABOUT_TO_START.post(new ServerLifecycleEventJS(server));
            STARTING.post(new ServerLifecycleEventJS(server));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                STARTED.post(new ServerLifecycleEventJS(server)));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                STOPPING.post(new ServerLifecycleEventJS(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            // 未来得及 post 的登录不带进下一个服务器实例（单人退出世界再进）
            PENDING_LOGINS.clear();
            currentServer = null;
            STOPPED.post(new ServerLifecycleEventJS(server));
        });
        ServerTickEvents.START_SERVER_TICK.register(server ->
                TICK_PRE.post(new ServerTickEventJS(server)));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            drainPendingLogins();
            TICK_POST.post(new ServerTickEventJS(server));
        });
        // fabric 的 JOIN 在 PlayerList#placeNewPlayer 中途触发（语义是"可以给这个连接发包了"），
        // 此刻玩家还没进 server.getPlayerList()——NeoForge 的 PlayerLoggedInEvent 是进列表之后。
        // 因此排到下一个 tick 末再 post：否则脚本在 loggedIn 里做的全服广播（ClientData.sync 等）
        // 会静默漏掉刚进来的这个人。server.execute 不行——同线程会内联执行。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PENDING_LOGINS.add(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // 同一 tick 内进又出：撤掉排队中的 loggedIn，避免 loggedOut 先于 loggedIn
            if (PENDING_LOGINS.remove(handler.player)) return;
            LOGGED_OUT.post(new PlayerLifecycleEventJS(handler.player));
        });
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                CHAT.post(new com.tkisor.nekojs.wrapper.event.player.ServerChatEventJS(sender, message.signedContent())));
    }
}
