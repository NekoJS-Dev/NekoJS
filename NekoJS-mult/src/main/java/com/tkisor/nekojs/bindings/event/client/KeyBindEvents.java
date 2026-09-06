//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.bindings.event.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import graal.graalvm.polyglot.Value;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Script-friendly key bindings (KubeJS-style): {@code register(id, key[, category])} creates a
 * {@link KeyMapping}, and the {@code pressed} / {@code released} / {@code tick} buses fire on
 * state transitions detected from {@code ClientTickEvent.Post}. Fires on the client thread;
 * only visible in client_scripts.
 *
 * <p>Script usage:
 * <ul>
 *   <li>{@code KeyBindEvents.register('mymod:my_key', 'key.keyboard.g', 'key.categories.misc')}
 *       — returns the {@link KeyMapping} handle (query {@code isDown()} / {@code consumeClick()}
 *       yourself if you prefer polling). {@code id} without a namespace defaults to
 *       {@code nekojs:}; {@code key} uses names like {@code key.keyboard.g} or
 *       {@code key.mouse.left}; omit {@code category} for {@code key.categories.misc}.
 *       Re-registering the same id returns the existing mapping (bindings survive CLIENT
 *       script reloads).</li>
 *   <li>{@code KeyBindEvents.pressed(event => { })} — a registered binding transitions to down
 *       this tick. Fires on the client thread; only visible in client_scripts.</li>
 *   <li>{@code KeyBindEvents.pressed('mymod:my_key', event => { })} — dispatch form: only for
 *       the binding with that full id.</li>
 *   <li>{@code KeyBindEvents.released(...)} — transition down → up (same shapes).</li>
 *   <li>{@code KeyBindEvents.tick(...)} — once per client tick while the binding is held down
 *       (KubeJS fires only for pressed keys).</li>
 * </ul>
 *
 * <p>Registration timing: {@link RegisterKeyMappingsEvent} fires on the MOD bus before client
 * scripts load, so {@code register(...)} invoked at script load is queued and flushed through
 * that event (bindings then participate in {@code Options.load()}, i.e. user-rebound keys
 * persist in options.txt). Calls made afterwards — including CLIENT reloads — append to
 * {@code Minecraft.getInstance().options.keyMappings} directly, exactly what the event itself
 * does. This group is registered through {@code NekoJSCorePlugin.registerClientEvents}, which
 * the plugin bootstrap skips on dedicated servers, so the client-only classes referenced here
 * are never loaded there.
 */
public final class KeyBindEvents {

    /** Event group exposed to scripts as the {@code KeyBindEvents} global. */
    public static final EventGroup GROUP = EventGroup.of("KeyBindEvents");

    /** Dispatch key of the trigger buses: the full binding id, e.g. {@code 'nekojs:my_key'}. */
    private static final DispatchKey<KeyBindEventJS, String> KEY_DISPATCH =
            DispatchKey.of(String.class, KeyBindEventJS::getId);

    /**
     * Fired when a registered binding transitions to down this tick (edge detection on
     * {@code KeyMapping.isDown()}). Fires on the client thread; only visible in client_scripts.
     */
    public static final EventBusJS<KeyBindEventJS, String> PRESSED =
            GROUP.client("pressed", KeyBindEventJS.class, KEY_DISPATCH);

    /**
     * Fired when a registered binding transitions from down to up. Fires on the client thread;
     * only visible in client_scripts.
     */
    public static final EventBusJS<KeyBindEventJS, String> RELEASED =
            GROUP.client("released", KeyBindEventJS.class, KEY_DISPATCH);

    /**
     * Fired once per client tick while a registered binding is held down (not fired for
     * released keys, mirroring KubeJS). Fires on the client thread; only visible in
     * client_scripts.
     */
    public static final EventBusJS<KeyBindEventJS, String> TICK =
            GROUP.client("tick", KeyBindEventJS.class, KEY_DISPATCH);

    /**
     * Key binding registration entry (unconventional bus, same shape as
     * {@code ClientEvents.hudRender}): calling it registers a binding instead of a listener.
     * Form: {@code register(id, key[, category])}; returns the {@link KeyMapping} handle.
     */
    public static final RegisterBus REGISTER =
            GROUP.add("register", ScriptType.CLIENT, new RegisterBus());

    private static final class Binding {
        final Identifier id;
        final KeyMapping mapping;
        /** Whether the mapping has been handed to the game (event or options array) yet. */
        volatile boolean installed;
        /** Last observed isDown(); only mutated on the client tick thread. */
        boolean lastDown;

        Binding(Identifier id, KeyMapping mapping) {
            this.id = id;
            this.mapping = mapping;
        }
    }

    /** All bindings of this session, keyed by full id ({@code 'namespace:path'}). */
    private static final Map<String, Binding> BINDINGS = new ConcurrentHashMap<>();
    // 解析/分类逻辑与状态在 KeyBindIds（裸 JUnit 可测）；本类静态初始化需要 FML 运行时

    /**
     * Non-null only while the {@link RegisterKeyMappingsEvent} dispatch is in progress: script
     * listeners of {@code ClientEvents.registerKeyMappings} may call {@code register(...)} in
     * that window, when the event's {@code Options} instance is the only reachable one.
     * Cleared on the first client tick.
     */
    private static volatile RegisterKeyMappingsEvent activeKeyMappingsEvent;
    private static volatile boolean gameBusSubscribed;
    private static volatile boolean modBusSubscribed;

    static {
        install();
    }

    private KeyBindEvents() {}

    /**
     * Subscribes the {@link ClientTickEvent.Post} handler to the game bus and the
     * {@link RegisterKeyMappingsEvent} handler to the mod bus. Idempotent and defensive: each
     * subscription is wrapped in try/catch so a failure (or a bare-JVM test environment)
     * degrades to pending-registration-only instead of breaking class initialization. Re-run
     * lazily by {@link #registerBinding(Identifier, InputConstants.Key, KeyMapping.Category)}.
     */
    public static void install() {
        if (!gameBusSubscribed) {
            try {
                NeoForge.EVENT_BUS.addListener(KeyBindEvents::onClientTickPost);
                gameBusSubscribed = true;
            } catch (Throwable t) {
                NekoJS.LOGGER.warn("KeyBindEvents: failed to subscribe ClientTickEvent.Post", t);
            }
        }
        if (!modBusSubscribed) {
            IEventBus modEventBus = modEventBusOrNull();
            if (modEventBus != null) {
                try {
                    modEventBus.addListener(KeyBindEvents::onRegisterKeyMappings);
                    modBusSubscribed = true;
                } catch (Throwable t) {
                    NekoJS.LOGGER.warn("KeyBindEvents: failed to subscribe RegisterKeyMappingsEvent", t);
                }
            }
        }
    }

    private static IEventBus modEventBusOrNull() {
        try {
            return NekoJSMod.modEventBus;
        } catch (Throwable t) {
            // NekoJSMod failed to initialize (e.g. bare JUnit): treat as unavailable
            return null;
        }
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        activeKeyMappingsEvent = event;
        flushPending();
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        // By the first client tick the RegisterKeyMappingsEvent dispatch is long finished
        activeKeyMappingsEvent = null;
        flushPending();
        dispatchStateTransitions();
    }

    /** Tries to hand every not-yet-installed binding to the game. */
    private static void flushPending() {
        for (Binding binding : BINDINGS.values()) {
            if (!binding.installed) {
                installIntoGame(binding);
            }
        }
    }

    /**
     * Routes one binding into the game: through {@link RegisterKeyMappingsEvent} while it is
     * dispatching (or has not fired yet), otherwise appended to
     * {@code Minecraft.getInstance().options.keyMappings} — the very same array-replace the
     * event performs. Before the event fires (client scripts load earlier, at
     * FMLConstructModEvent) the binding simply stays pending and is flushed by
     * {@link #onRegisterKeyMappings}.
     */
    private static void installIntoGame(Binding binding) {
        RegisterKeyMappingsEvent active = activeKeyMappingsEvent;
        if (active != null) {
            try {
                installCustomCategories(active);
                active.register(binding.mapping);
                binding.installed = true;
                return;
            } catch (Throwable t) {
                NekoJS.LOGGER.error(
                        "KeyBindEvents: RegisterKeyMappingsEvent.register failed for '{}'; falling back",
                        binding.id, t);
            }
        }
        appendToOptions(binding);
    }

    private static void appendToOptions(Binding binding) {
        Options options = clientOptions();
        if (options == null) {
            // Options does not exist yet: keep pending, retried at the event / next client tick
            return;
        }
        try {
            installCustomCategories(null);
            // options.keyMappings is a public non-final array in 26.x; the RegisterKeyMappings
            // event itself does exactly this copy-and-replace
            KeyMapping[] current = options.keyMappings;
            KeyMapping[] grown = Arrays.copyOf(current, current.length + 1);
            grown[current.length] = binding.mapping;
            options.keyMappings = grown;
            binding.installed = true;
        } catch (Throwable t) {
            // mark installed anyway: retrying every tick would only spam the same error
            binding.installed = true;
            NekoJS.LOGGER.error(
                    "KeyBindEvents: failed to append '{}' to options.keyMappings; it stays script-pollable"
                            + " but will not appear in the controls screen",
                    binding.id, t);
        }
    }

    /**
     * Hands custom categories to the game: via the event when available, else sort order.
     * The deprecated {@link KeyMapping.Category#register(Identifier)} is the only way to add a
     * category after {@link RegisterKeyMappingsEvent} has already fired (script reloads at
     * runtime); during the event window the non-deprecated
     * {@link RegisterKeyMappingsEvent#registerCategory(KeyMapping.Category)} is used instead.
     */
    @SuppressWarnings("deprecation")
    private static void installCustomCategories(RegisterKeyMappingsEvent event) {
        for (KeyMapping.Category category : KeyBindIds.CUSTOM_CATEGORIES.values()) {
            if (!KeyBindIds.INSTALLED_CATEGORIES.add(category.id())) {
                continue;
            }
            try {
                if (event != null) {
                    event.registerCategory(category);
                } else {
                    KeyMapping.Category.register(category.id());
                }
            } catch (IllegalArgumentException alreadyRegistered) {
                // a value-equal category is already known to the game — nothing to do
            } catch (Throwable t) {
                NekoJS.LOGGER.warn("KeyBindEvents: failed to register key category '{}'", category.id(), t);
            }
        }
    }

    private static void dispatchStateTransitions() {
        if (BINDINGS.isEmpty()) {
            return;
        }
        boolean transitions = PRESSED.hasListeners() || RELEASED.hasListeners();
        boolean whileDown = TICK.hasListeners();
        if (!transitions && !whileDown) {
            return;
        }
        for (Binding binding : BINDINGS.values()) {
            boolean down = binding.mapping.isDown();
            if (transitions) {
                if (down && !binding.lastDown) {
                    PRESSED.post(new KeyBindEventJS(binding.id.toString(), binding.mapping, true));
                } else if (!down && binding.lastDown) {
                    RELEASED.post(new KeyBindEventJS(binding.id.toString(), binding.mapping, false));
                }
            }
            if (whileDown && down) {
                TICK.post(new KeyBindEventJS(binding.id.toString(), binding.mapping, true));
            }
            binding.lastDown = down;
        }
    }

    private static Options clientOptions() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null ? null : minecraft.options;
        } catch (Throwable t) {
            // dedicated server / bare JVM: KeyBindEvents is client-only, this is unreachable there
            return null;
        }
    }

    /**
     * Registers (or looks up) a binding. Idempotent per full id: the second call returns the
     * existing {@link KeyMapping} unchanged, so CLIENT script reloads do not duplicate entries.
     */
    static KeyMapping registerBinding(Identifier id, InputConstants.Key key, KeyMapping.Category category) {
        install();
        String fullId = id.toString();
        Binding existing = BINDINGS.get(fullId);
        if (existing != null) {
            return existing.mapping;
        }
        KeyMapping mapping = new KeyMapping(bindingName(id), key.getType(), key.getValue(), category);
        Binding binding = new Binding(id, mapping);
        BINDINGS.put(fullId, binding);
        installIntoGame(binding);
        return mapping;
    }

    /** Translation name of a binding: {@code 'key.<namespace>.<path>'}. */
    static String bindingName(Identifier id) {
        return KeyBindIds.bindingName(id);
    }

    /** See {@link KeyBindIds#parseIdentifier}. */
    static Identifier parseIdentifier(String raw) {
        return KeyBindIds.parseIdentifier(raw);
    }

    /** See {@link KeyBindIds#parseKey}. */
    static InputConstants.Key parseKey(String keyName) {
        return KeyBindIds.parseKey(keyName);
    }

    /** See {@link KeyBindIds#resolveCategory}. */
    static KeyMapping.Category resolveCategory(String category) {
        return KeyBindIds.resolveCategory(category);
    }

    /**
     * The {@code KeyBindEvents.register(...)} entry point: an {@link EventBusJS} subclass whose
     * {@code execute} registers a binding instead of a listener (the underlying bus is only a
     * placeholder, see {@code RenderRegistrationBusJS}).
     */
    public static final class RegisterBus extends EventBusJS<Object, Void> {

        RegisterBus() {
            super(EventBus.create(Object.class));
        }

        /**
         * Script forms: {@code register(id, key)} / {@code register(id, key, category)}.
         * Returns the {@link KeyMapping} handle. Fires on the client thread; only visible in
         * client_scripts.
         */
        @Override
        public Object execute(Value... args) {
            if (args.length < 1 || args.length > 3) {
                throw new IllegalArgumentException("register requires (id, key[, category])");
            }
            if (!args[0].isString() || args[0].asString().isBlank()) {
                throw new IllegalArgumentException("binding id must be a non-blank string like 'mymod:my_key'");
            }
            Identifier id = parseIdentifier(args[0].asString());
            InputConstants.Key key = parseKey(optionalString(args, 1, "key"));
            KeyMapping.Category category = resolveCategory(optionalString(args, 2, "category"));
            return registerBinding(id, key, category);
        }

        private static String optionalString(Value[] args, int index, String what) {
            if (index >= args.length) {
                return null;
            }
            Value value = args[index];
            if (value.isNull()) {
                return null;
            }
            if (!value.isString()) {
                throw new IllegalArgumentException(what + " must be a string (or null)");
            }
            return value.asString();
        }
    }

    /** Event object posted to {@code pressed} / {@code released} / {@code tick}. */
    @Doc("Key binding trigger event (KeyBindEvents.pressed / released / tick).")
    @Doc("Fires on the client thread; only visible in client_scripts.")
    public static final class KeyBindEventJS {

        private final String id;
        private final KeyMapping keyMapping;
        private final boolean down;

        KeyBindEventJS(String id, KeyMapping keyMapping, boolean down) {
            this.id = id;
            this.keyMapping = keyMapping;
            this.down = down;
        }

        /** Full binding id ({@code 'namespace:path'}), also the dispatch key. */
        @Doc("Gets the full binding id, e.g. 'mymod:my_key'.")
        @Return("the binding id; also the dispatch key of these buses")
        public String getId() {
            return id;
        }

        /** The underlying {@link KeyMapping} ({@code isDown()} / {@code consumeClick()} / {@code getName()}). */
        @Doc("Gets the underlying vanilla KeyMapping handle.")
        @Return("the KeyMapping; poll isDown()/consumeClick() or read getName() from it")
        public KeyMapping getKeyMapping() {
            return keyMapping;
        }

        /** Whether the binding is down at the time of the post ({@code true} for tick posts). */
        @Doc("Gets whether the binding is currently down.")
        @Return("true while held; pressed/released posts reflect the transition direction")
        public boolean isDown() {
            return down;
        }
    }
}
//?}
//?} else {
// fabric 节点孪生：版本树同名类整文件 `//? if neoforge && >=26` 守卫（重 NeoForge 面：
// RegisterKeyMappingsEvent 事件窗口、mod-bus 订阅、FML 运行时探测）。fabric 侧等价物更简——
// KeyMappingHelper.registerKeyMapping 任意时机可调（无事件窗口/pending 重试），状态轮询挂
// ClientTickEvents.END_CLIENT_TICK（edge-detect 逻辑与 NeoForge 版逐行同构）。
// 解析/分类逻辑复用版本树 KeyBindIds（>=26 版本守卫，fabric 26 存活）。版本树版去 NeoForge
// 化后（payload/RegisterBus 提为中立形态）合并回单副本。
package com.tkisor.nekojs.bindings.event.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import graal.graalvm.polyglot.Value;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Script-friendly key bindings（fabric 形态，脚本面与 NeoForge 版一致）：
 * {@code register(id, key[, category])} 经 KeyMappingHelper 注册绑定，
 * {@code pressed} / {@code released} / {@code tick} 总线在客户端 tick 上做
 * {@code KeyMapping.isDown()} 的边沿检测。仅 client_scripts 可见。
 */
public final class KeyBindEvents {

    public static final EventGroup GROUP = EventGroup.of("KeyBindEvents");

    private static final DispatchKey<KeyBindEventJS, String> KEY_DISPATCH =
            DispatchKey.of(String.class, KeyBindEventJS::getId);

    public static final EventBusJS<KeyBindEventJS, String> PRESSED =
            GROUP.client("pressed", KeyBindEventJS.class, KEY_DISPATCH);

    public static final EventBusJS<KeyBindEventJS, String> RELEASED =
            GROUP.client("released", KeyBindEventJS.class, KEY_DISPATCH);

    public static final EventBusJS<KeyBindEventJS, String> TICK =
            GROUP.client("tick", KeyBindEventJS.class, KEY_DISPATCH);

    /** {@code KeyBindEvents.register(id, key[, category])}：注册绑定，返回 KeyMapping 句柄。 */
    public static final RegisterBus REGISTER =
            GROUP.add("register", ScriptType.CLIENT, new RegisterBus());

    private static final class Binding {
        final Identifier id;
        final KeyMapping mapping;
        boolean lastDown;

        Binding(Identifier id, KeyMapping mapping) {
            this.id = id;
            this.mapping = mapping;
        }
    }

    private static final Map<String, Binding> BINDINGS = new ConcurrentHashMap<>();

    private KeyBindEvents() {}

    /** client init 调用：挂状态轮询（END_CLIENT_TICK，与 NeoForge 版 ClientTickEvent.Post 同位）。 */
    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(client -> dispatchStateTransitions());
    }

    /** 注册（或查回）绑定：同 id 幂等，CLIENT 脚本 reload 不重复注册。 */
    static KeyMapping registerBinding(Identifier id, InputConstants.Key key, KeyMapping.Category category) {
        String fullId = id.toString();
        Binding existing = BINDINGS.get(fullId);
        if (existing != null) {
            return existing.mapping;
        }
        installCategory(category);
        KeyMapping mapping = new KeyMapping(KeyBindIds.bindingName(id), key.getType(), key.getValue(), category);
        BINDINGS.put(fullId, new Binding(id, mapping));
        installIntoGame(mapping, id);
        return mapping;
    }

    /**
     * 把绑定交给游戏：CLIENT 脚本加载在 CLIENT_STARTED（晚于 Options 构建），此时
     * fabric-api 的 KeyMappingHelper 会撞 26.x GameOptions 的二次初始化守卫
     * （"GameOptions has already been initialised"）——改为直接扩 options.keyMappings
     * 数组（AW mutable；RegisterKeyMappingsEvent 本身做的就是这份替换）。失败时绑定仍
     * 记录在 BINDINGS（脚本可持句柄），只是不进控制设置界面。
     */
    private static void installIntoGame(KeyMapping mapping, Identifier id) {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft == null || minecraft.options == null) {
                return;   // 保持 pending，下一 tick 重试（dispatchStateTransitions 顺带 flush）
            }
            net.minecraft.client.KeyMapping[] current = minecraft.options.keyMappings;
            net.minecraft.client.KeyMapping[] grown = java.util.Arrays.copyOf(current, current.length + 1);
            grown[current.length] = mapping;
            minecraft.options.keyMappings = grown;
        } catch (Throwable t) {
            NekoJS.LOGGER.error(
                    "KeyBindEvents: failed to append '{}' to options.keyMappings; it stays script-pollable"
                            + " but will not appear in the controls screen", id, t);
        }
    }

    /** 自定义分类：26.x 唯一注册点是 Category.register（与 NeoForge 版事件窗口外的回退同款）。 */
    @SuppressWarnings("deprecation")
    private static void installCategory(KeyMapping.Category category) {
        if (!KeyBindIds.INSTALLED_CATEGORIES.add(category.id())) {
            return;
        }
        try {
            KeyMapping.Category.register(category.id());
        } catch (IllegalArgumentException alreadyRegistered) {
            // 值相等的分类已注册——无事可做
        } catch (Throwable t) {
            NekoJS.LOGGER.warn("KeyBindEvents: failed to register key category '{}'", category.id(), t);
        }
    }

    private static void dispatchStateTransitions() {
        if (BINDINGS.isEmpty()) {
            return;
        }
        boolean transitions = PRESSED.hasListeners() || RELEASED.hasListeners();
        boolean whileDown = TICK.hasListeners();
        if (!transitions && !whileDown) {
            return;
        }
        for (Binding binding : BINDINGS.values()) {
            boolean down = binding.mapping.isDown();
            if (transitions) {
                if (down && !binding.lastDown) {
                    PRESSED.post(new KeyBindEventJS(binding.id.toString(), binding.mapping, true));
                } else if (!down && binding.lastDown) {
                    RELEASED.post(new KeyBindEventJS(binding.id.toString(), binding.mapping, false));
                }
            }
            if (whileDown && down) {
                TICK.post(new KeyBindEventJS(binding.id.toString(), binding.mapping, true));
            }
            binding.lastDown = down;
        }
    }

    /** {@code KeyBindEvents.register(...)} 入口：注册绑定而非挂监听（与 NeoForge 版同形）。 */
    public static final class RegisterBus extends EventBusJS<Object, Void> {

        RegisterBus() {
            super(EventBus.create(Object.class));
        }

        @Override
        public Object execute(Value... args) {
            if (args.length < 1 || args.length > 3) {
                throw new IllegalArgumentException("register requires (id, key[, category])");
            }
            if (!args[0].isString() || args[0].asString().isBlank()) {
                throw new IllegalArgumentException("binding id must be a non-blank string like 'mymod:my_key'");
            }
            Identifier id = KeyBindIds.parseIdentifier(args[0].asString());
            InputConstants.Key key = KeyBindIds.parseKey(optionalString(args, 1, "key"));
            KeyMapping.Category category = KeyBindIds.resolveCategory(optionalString(args, 2, "category"));
            return registerBinding(id, key, category);
        }

        private static String optionalString(Value[] args, int index, String what) {
            if (index >= args.length) {
                return null;
            }
            Value value = args[index];
            if (value.isNull()) {
                return null;
            }
            if (!value.isString()) {
                throw new IllegalArgumentException(what + " must be a string (or null)");
            }
            return value.asString();
        }
    }

    /** pressed / released / tick 的事件对象（与 NeoForge 版同形）。 */
    @Doc("Key binding trigger event (KeyBindEvents.pressed / released / tick).")
    @Doc("Fires on the client thread; only visible in client_scripts.")
    public static final class KeyBindEventJS {

        private final String id;
        private final KeyMapping keyMapping;
        private final boolean down;

        KeyBindEventJS(String id, KeyMapping keyMapping, boolean down) {
            this.id = id;
            this.keyMapping = keyMapping;
            this.down = down;
        }

        @Doc("Gets the full binding id, e.g. 'mymod:my_key'.")
        @Return("the binding id; also the dispatch key of these buses")
        public String getId() {
            return id;
        }

        @Doc("Gets the underlying vanilla KeyMapping handle.")
        @Return("the KeyMapping; poll isDown()/consumeClick() or read getName() from it")
        public KeyMapping getKeyMapping() {
            return keyMapping;
        }

        @Doc("Gets whether the binding is currently down.")
        @Return("true while held; pressed/released posts reflect the transition direction")
        public boolean isDown() {
            return down;
        }
    }
}
//?}
