package com.tkisor.nekojs.bindings.event.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.eventbus.EventBusFactory;
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
            EventBusFactory.createDispatchKey(String.class, KeyBindEventJS::getId);

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
            super(EventBusFactory.createEventBus(Object.class));
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
